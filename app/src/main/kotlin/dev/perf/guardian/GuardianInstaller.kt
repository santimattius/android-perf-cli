package dev.perf.guardian

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.security.MessageDigest
import java.util.Comparator

private const val receiptFileName = ".guardian-manifest.json"
private val receiptJson =
    Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = false
    }
private val itemIdPattern = Regex("[a-z0-9][a-z0-9-]*")

/** A repository-local record of the catalog files Guardian owns. */
@Serializable
data class GuardianInstallReceipt(
    val guardianVersion: String,
    val target: String,
    val items: List<GuardianInstalledItem>,
) {
    companion object {
        fun read(path: Path): GuardianInstallReceipt =
            try {
                receiptJson.decodeFromString(serializer(), Files.readString(path))
            } catch (error: Exception) {
                throw GuardianInstallException("Invalid managed receipt: $path", error)
            }
    }

    fun bytes(): ByteArray = receiptJson.encodeToString(serializer(), this).encodeToByteArray()
}

/** One managed artifact and its installed-content checksum. */
@Serializable
data class GuardianInstalledItem(
    val kind: String,
    val id: String,
    val version: String,
    val path: String,
    val sha256: String,
)

/** The paths an installation published or verified as already current. */
data class GuardianInstallResult(
    val installedPaths: List<String>,
    val unchangedPaths: List<String>,
)

/** A catalog, collision, or filesystem error while preparing a Guardian installation. */
class GuardianInstallException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/**
 * Installs the Claude artifacts represented by an embedded [GuardianManifest].
 *
 * All catalog and collision validation, including payload staging, completes before any destination
 * file is changed. Publication errors attempt to restore already-published files and never publish a
 * successful receipt. A process crash can still interrupt the multi-path publication sequence.
 */
class GuardianInstaller(
    private val payloadReader: (String) -> ByteArray = GuardianAssets::readResource,
) {
    fun install(
        repository: Path,
        target: String,
        catalog: GuardianManifest = GuardianAssets.loadCatalog(),
    ): GuardianInstallResult {
        val root = validateRepository(repository)
        val plan = createPlan(root, target, catalog)
        val receiptPath = root.resolve(receiptFileName)
        val existingReceipt = readExistingReceipt(receiptPath)

        validateNoSymlinkAncestors(root, receiptPath)
        plan.forEach { validateNoSymlinkAncestors(root, it.destination) }
        validateCollisions(plan, existingReceipt)

        val staged = stagePayloads(root, plan)
        val desiredReceipt =
            GuardianInstallReceipt(
                guardianVersion = catalog.guardianVersion,
                target = target,
                items =
                    plan.map { item ->
                        GuardianInstalledItem(
                            kind = item.kind,
                            id = item.id,
                            version = item.version,
                            path = item.relativePath,
                            sha256 = sha256(staged.files.getValue(item).content),
                        )
                    },
            )
        val desiredReceiptBytes = desiredReceipt.bytes()
        val existingReceiptBytes = if (Files.exists(receiptPath, NOFOLLOW_LINKS)) Files.readAllBytes(receiptPath) else null
        val writes =
            plan.filter { item ->
                !Files.exists(item.destination, NOFOLLOW_LINKS) ||
                    sha256(Files.readAllBytes(item.destination)) != sha256(staged.files.getValue(item).content)
            }

        if (writes.isEmpty() && existingReceiptBytes?.contentEquals(desiredReceiptBytes) == true) {
            deleteTree(staged.directory)
            return GuardianInstallResult(emptyList(), plan.map { it.relativePath })
        }

        val published = mutableListOf<PublishedFile>()
        var receiptPublished: PublishedFile? = null
        try {
            writes.forEach { item ->
                validateNoSymlinkAncestors(root, item.destination)
                item.destination.parent.createDirectoriesSafely(root)
                val stagedFile = staged.files.getValue(item)
                published += publish(stagedFile.path, item.destination, staged.directory)
            }

            if (existingReceiptBytes?.contentEquals(desiredReceiptBytes) != true) {
                validateNoSymlinkAncestors(root, receiptPath)
                receiptPublished = publishBytes(desiredReceiptBytes, receiptPath, staged.directory)
            }

            return GuardianInstallResult(writes.map { it.relativePath }, plan.filterNot(writes::contains).map { it.relativePath })
        } catch (error: Exception) {
            receiptPublished?.restore()
            published.asReversed().forEach { it.restore() }
            throw if (error is GuardianInstallException) {
                error
            } else {
                GuardianInstallException(
                    "Guardian installation failed; rollback was attempted",
                    error,
                )
            }
        } finally {
            deleteTree(staged.directory)
        }
    }

    private fun validateRepository(repository: Path): Path {
        val root = repository.toAbsolutePath().normalize()
        if (!Files.isDirectory(root, NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw GuardianInstallException("Repository must be an existing non-symlink directory: $repository")
        }
        return root
    }

    private fun createPlan(
        root: Path,
        target: String,
        catalog: GuardianManifest,
    ): List<PlanItem> {
        if (target != "claude") throw GuardianInstallException("Unsupported Guardian target: $target")
        if (catalog.guardianVersion.isBlank()) throw GuardianInstallException("Guardian catalog version is required")

        val skillsById = catalog.skills.associateBy { it.id }
        if (skillsById.size != catalog.skills.size) throw GuardianInstallException("Duplicate Guardian skill identifiers")
        catalog.skills.forEach { skill ->
            validateItemId(skill.id, "skill")
            validateResource(skill.resource)
            if (skill.version.isBlank()) throw GuardianInstallException("Guardian skill version is required: ${skill.id}")
            if (skill.extraResources.isNotEmpty()) {
                throw GuardianInstallException("Guardian skill extra resources are not supported by the Claude installer: ${skill.id}")
            }
        }

        val targetPersonas = catalog.personas.filter { target in it.artifacts }
        targetPersonas.forEach { persona ->
            validateItemId(persona.id, "persona")
            validateResource(persona.artifacts.getValue(target))
            if (persona.version.isBlank()) throw GuardianInstallException("Guardian persona version is required: ${persona.id}")
            persona.requiresSkills.forEach { skillId ->
                if (skillId !in
                    skillsById
                ) {
                    throw GuardianInstallException("Guardian persona ${persona.id} requires missing skill: $skillId")
                }
            }
        }

        val plan =
            buildList {
                catalog.skills.forEach { skill ->
                    add(
                        PlanItem(
                            kind = "skill",
                            id = skill.id,
                            version = skill.version,
                            resource = skill.resource,
                            relativePath = ".claude/skills/${skill.id}/SKILL.md",
                            destination = resolveRelative(root, ".claude/skills/${skill.id}/SKILL.md"),
                        ),
                    )
                }
                targetPersonas.forEach { persona ->
                    add(
                        PlanItem(
                            kind = "persona",
                            id = persona.id,
                            version = persona.version,
                            resource = persona.artifacts.getValue(target),
                            relativePath = ".claude/agents/${persona.id}.md",
                            destination = resolveRelative(root, ".claude/agents/${persona.id}.md"),
                        ),
                    )
                }
            }
        if (plan.map { it.relativePath }.toSet().size != plan.size) {
            throw GuardianInstallException("Duplicate Guardian installation destinations")
        }
        return plan
    }

    private fun readExistingReceipt(path: Path): GuardianInstallReceipt? {
        if (!Files.exists(path, NOFOLLOW_LINKS)) return null
        if (!Files.isRegularFile(path, NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw GuardianInstallException("Guardian receipt is not a regular managed file: $path")
        }
        return GuardianInstallReceipt.read(path)
    }

    private fun validateCollisions(
        plan: List<PlanItem>,
        receipt: GuardianInstallReceipt?,
    ) {
        plan.forEach { item ->
            if (!Files.exists(item.destination, NOFOLLOW_LINKS)) return@forEach
            if (!Files.isRegularFile(item.destination, NOFOLLOW_LINKS) || Files.isSymbolicLink(item.destination)) {
                throw GuardianInstallException("Guardian destination is not a regular file: ${item.relativePath}")
            }
            val managed =
                receipt?.items?.singleOrNull { it.path == item.relativePath }
                    ?: throw GuardianInstallException("Refusing to overwrite unowned Guardian destination: ${item.relativePath}")
            if (sha256(Files.readAllBytes(item.destination)) != managed.sha256) {
                throw GuardianInstallException("Managed Guardian destination was modified: ${item.relativePath}")
            }
        }
    }

    private fun stagePayloads(
        root: Path,
        plan: List<PlanItem>,
    ): StagedPayloads {
        val directory =
            try {
                Files.createTempDirectory(root, ".guardian-stage-")
            } catch (error: IOException) {
                throw GuardianInstallException("Could not stage Guardian payloads", error)
            }
        try {
            val files =
                plan.associateWith { item ->
                    val content =
                        try {
                            payloadReader(item.resource).copyOf()
                        } catch (error: Exception) {
                            throw GuardianInstallException("Could not read Guardian payload: ${item.resource}", error)
                        }
                    val staged = directory.resolve("payload-${filesafe(item.relativePath)}")
                    Files.write(staged, content, CREATE_NEW)
                    StagedFile(staged, content)
                }
            return StagedPayloads(directory, files)
        } catch (error: Exception) {
            deleteTree(directory)
            throw if (error is GuardianInstallException) error else GuardianInstallException("Could not stage Guardian payloads", error)
        }
    }

    private fun publish(
        staged: Path,
        destination: Path,
        stageDirectory: Path,
    ): PublishedFile = publishFile(staged, destination, stageDirectory)

    private fun publishBytes(
        bytes: ByteArray,
        destination: Path,
        stageDirectory: Path,
    ): PublishedFile {
        val staged = Files.createTempFile(stageDirectory, "receipt-", ".json")
        Files.write(staged, bytes)
        return publishFile(staged, destination, stageDirectory)
    }

    private fun publishFile(
        staged: Path,
        destination: Path,
        stageDirectory: Path,
    ): PublishedFile {
        val backup =
            if (Files.exists(destination, NOFOLLOW_LINKS)) {
                val backupPath = Files.createTempFile(stageDirectory, "backup-", ".tmp")
                Files.copy(destination, backupPath, REPLACE_EXISTING)
                backupPath
            } else {
                null
            }
        Files.move(staged, destination, REPLACE_EXISTING, ATOMIC_MOVE)
        return PublishedFile(destination, backup)
    }

    private fun validateItemId(
        id: String,
        kind: String,
    ) {
        if (!itemIdPattern.matches(id)) throw GuardianInstallException("Invalid Guardian $kind identifier: $id")
    }

    private fun validateResource(resource: String) {
        val path = Path.of(resource)
        if (resource.isBlank() || resource.contains('\\') || path.isAbsolute ||
            resource.split('/').any { it.isBlank() || it == "." || it == ".." }
        ) {
            throw GuardianInstallException("Invalid Guardian resource path: $resource")
        }
    }

    private fun resolveRelative(
        root: Path,
        relative: String,
    ): Path {
        val destination = root.resolve(relative).normalize()
        if (!destination.startsWith(root)) throw GuardianInstallException("Guardian destination escapes repository: $relative")
        return destination
    }

    private fun validateNoSymlinkAncestors(
        root: Path,
        path: Path,
    ) {
        val relative = root.relativize(path)
        var current = root
        for (part in relative) {
            current = current.resolve(part)
            if (Files.isSymbolicLink(current)) throw GuardianInstallException("Guardian destination crosses a symbolic link: $path")
        }
    }

    private fun Path.createDirectoriesSafely(root: Path) {
        val relative = root.relativize(this)
        var current = root
        for (part in relative) {
            current = current.resolve(part)
            if (Files.exists(current, NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current) || !Files.isDirectory(current, NOFOLLOW_LINKS)) {
                    throw GuardianInstallException("Guardian destination parent is not a directory: $current")
                }
            } else {
                Files.createDirectory(current)
            }
        }
    }

    private data class PlanItem(
        val kind: String,
        val id: String,
        val version: String,
        val resource: String,
        val relativePath: String,
        val destination: Path,
    )

    private data class StagedPayloads(
        val directory: Path,
        val files: Map<PlanItem, StagedFile>,
    )

    private data class StagedFile(
        val path: Path,
        val content: ByteArray,
    )

    private data class PublishedFile(
        val destination: Path,
        val backup: Path?,
    ) {
        fun restore() {
            try {
                if (backup == null) {
                    Files.deleteIfExists(destination)
                } else {
                    Files.move(backup, destination, REPLACE_EXISTING, ATOMIC_MOVE)
                }
            } catch (_: IOException) {
                // Best-effort rollback is the documented boundary for unexpected filesystem failures.
            }
        }
    }

    private companion object {
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        fun filesafe(path: String): String = path.replace(Regex("[^a-zA-Z0-9]"), "-")

        fun deleteTree(path: Path) {
            if (!Files.exists(path, NOFOLLOW_LINKS)) return
            try {
                Files.walk(path).use { entries ->
                    entries.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                }
            } catch (_: IOException) {
                // Staging cleanup cannot compromise a completed receipt or successful payload publication.
            }
        }
    }
}
