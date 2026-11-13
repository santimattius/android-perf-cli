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
    val targets: List<String>,
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

/**
 * One managed artifact and its installed-content checksum. For a doc snippet the checksum covers
 * only the marked region, never the rest of the user's file.
 */
@Serializable
data class GuardianInstalledItem(
    val kind: String,
    val target: String,
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
 * Installs the artifacts of one [CliTarget] represented by an embedded [GuardianManifest].
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
        val cliTarget = CliTarget.byId(target) ?: throw GuardianInstallException("Unsupported Guardian target: $target")
        val plan = createPlan(root, cliTarget, catalog)
        val receiptPath = root.resolve(receiptFileName)
        val existingReceipt = readExistingReceipt(receiptPath)

        validateNoSymlinkAncestors(root, receiptPath)
        plan.forEach { validateNoSymlinkAncestors(root, it.destination) }
        validateCollisions(plan.filter { it.region == null }, existingReceipt)

        val staged = stagePayloads(root, plan, existingReceipt)
        val desiredReceipt =
            GuardianInstallReceipt(
                guardianVersion = catalog.guardianVersion,
                targets = ((existingReceipt?.targets ?: emptyList()) + cliTarget.id).distinct().sorted(),
                items =
                    (existingReceipt?.items?.filter { it.target != cliTarget.id } ?: emptyList()) +
                        plan.map { item ->
                            GuardianInstalledItem(
                                kind = item.kind,
                                target = item.target,
                                id = item.id,
                                version = item.version,
                                path = item.relativePath,
                                sha256 = staged.files.getValue(item).ownedSha,
                            )
                        },
            )
        val desiredReceiptBytes = desiredReceipt.bytes()
        val existingReceiptBytes = if (Files.exists(receiptPath, NOFOLLOW_LINKS)) Files.readAllBytes(receiptPath) else null
        val writes =
            plan.filter { item ->
                !Files.exists(item.destination, NOFOLLOW_LINKS) ||
                    !Files.readAllBytes(item.destination).contentEquals(staged.files.getValue(item).content)
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
                if (item.destination.parent != root) item.destination.parent.createDirectoriesSafely(root)
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
        target: CliTarget,
        catalog: GuardianManifest,
    ): List<PlanItem> {
        if (catalog.guardianVersion.isBlank()) throw GuardianInstallException("Guardian catalog version is required")

        val skillsById = catalog.skills.associateBy { it.id }
        if (skillsById.size != catalog.skills.size) throw GuardianInstallException("Duplicate Guardian skill identifiers")
        catalog.skills.forEach { skill ->
            validateItemId(skill.id, "skill")
            validateResource(skill.resource)
            if (skill.version.isBlank()) throw GuardianInstallException("Guardian skill version is required: ${skill.id}")
            if (skill.extraResources.isNotEmpty()) {
                throw GuardianInstallException("Guardian skill extra resources are not supported by the installer: ${skill.id}")
            }
        }

        val targetPersonas = catalog.personas.filter { target.id in it.artifacts }
        targetPersonas.forEach { persona ->
            validateItemId(persona.id, "persona")
            validateResource(persona.artifacts.getValue(target.id))
            if (persona.version.isBlank()) throw GuardianInstallException("Guardian persona version is required: ${persona.id}")
            persona.requiresSkills.forEach { skillId ->
                if (skillId !in skillsById) {
                    throw GuardianInstallException("Guardian persona ${persona.id} requires missing skill: $skillId")
                }
            }
        }

        val targetSnippets = catalog.docSnippets.filter { it.target == target.id }
        targetSnippets.forEach { snippet ->
            validateItemId(snippet.id, "doc snippet")
            validateResource(snippet.resource)
            validateResource(snippet.path)
            if (snippet.version.isBlank()) throw GuardianInstallException("Guardian doc snippet version is required: ${snippet.id}")
            if (snippet.markerBegin.isBlank() || snippet.markerEnd.isBlank() || snippet.markerBegin == snippet.markerEnd) {
                throw GuardianInstallException("Guardian doc snippet needs distinct begin and end markers: ${snippet.id}")
            }
        }

        val plan =
            buildList {
                catalog.skills.forEach { skill ->
                    val relative = target.skillPath(skill.id)
                    add(PlanItem("skill", target.id, skill.id, skill.version, skill.resource, relative, resolveRelative(root, relative)))
                }
                targetPersonas.forEach { persona ->
                    val relative = target.personaPath(persona.id)
                    add(
                        PlanItem(
                            "persona",
                            target.id,
                            persona.id,
                            persona.version,
                            persona.artifacts.getValue(target.id),
                            relative,
                            resolveRelative(root, relative),
                        ),
                    )
                }
                targetSnippets.forEach { snippet ->
                    add(
                        PlanItem(
                            "docSnippet",
                            target.id,
                            snippet.id,
                            snippet.version,
                            snippet.resource,
                            snippet.path,
                            resolveRelative(root, snippet.path),
                            DocRegion(snippet.markerBegin, snippet.markerEnd),
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
        receipt: GuardianInstallReceipt?,
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
                    val payload =
                        try {
                            payloadReader(item.resource).copyOf()
                        } catch (error: Exception) {
                            throw GuardianInstallException("Could not read Guardian payload: ${item.resource}", error)
                        }
                    val region = item.region
                    val content: ByteArray
                    val ownedSha: String
                    if (region == null) {
                        content = payload
                        ownedSha = sha256(payload)
                    } else {
                        val regionText = payload.decodeToString().trim()
                        if (!regionText.startsWith(region.markerBegin) || !regionText.endsWith(region.markerEnd)) {
                            throw GuardianInstallException("Guardian doc snippet must start and end with its markers: ${item.id}")
                        }
                        content = composeRegion(item, region, regionText, receipt)
                        ownedSha = sha256(regionText.encodeToByteArray())
                    }
                    val staged = directory.resolve("payload-${filesafe(item.relativePath)}")
                    Files.write(staged, content, CREATE_NEW)
                    StagedFile(staged, content, ownedSha)
                }
            return StagedPayloads(directory, files)
        } catch (error: Exception) {
            deleteTree(directory)
            throw if (error is GuardianInstallException) error else GuardianInstallException("Could not stage Guardian payloads", error)
        }
    }

    /**
     * Computes the full destination file for a marker-delimited doc snippet. Bytes outside the
     * markers are never changed. A region Guardian did not install, a region the user edited, or
     * mangled markers abort the installation.
     */
    private fun composeRegion(
        item: PlanItem,
        region: DocRegion,
        regionText: String,
        receipt: GuardianInstallReceipt?,
    ): ByteArray {
        val destination = item.destination
        if (!Files.exists(destination, NOFOLLOW_LINKS)) return "$regionText\n".encodeToByteArray()
        if (!Files.isRegularFile(destination, NOFOLLOW_LINKS) || Files.isSymbolicLink(destination)) {
            throw GuardianInstallException("Guardian destination is not a regular file: ${item.relativePath}")
        }
        val existing = Files.readString(destination)
        val begin = existing.indexOf(region.markerBegin)
        val end = existing.indexOf(region.markerEnd)
        if (begin == -1 && end == -1) {
            val separator =
                when {
                    existing.isEmpty() -> ""
                    existing.endsWith("\n") -> "\n"
                    else -> "\n\n"
                }
            return (existing + separator + regionText + "\n").encodeToByteArray()
        }
        val malformed =
            begin == -1 || end == -1 || end < begin ||
                existing.indexOf(region.markerBegin, begin + 1) != -1 ||
                existing.indexOf(region.markerEnd, end + 1) != -1
        if (malformed) {
            throw GuardianInstallException("Guardian markers in ${item.relativePath} are malformed; fix or remove them first")
        }
        val endExclusive = end + region.markerEnd.length
        val currentSha = sha256(existing.substring(begin, endExclusive).encodeToByteArray())
        val owned = receipt?.items?.singleOrNull { it.path == item.relativePath && it.target == item.target }
        if (owned == null) {
            throw GuardianInstallException("Refusing to overwrite unowned Guardian region: ${item.relativePath}")
        }
        if (owned.sha256 != currentSha) {
            throw GuardianInstallException("Managed Guardian region was modified: ${item.relativePath}")
        }
        if (currentSha == sha256(regionText.encodeToByteArray())) return existing.encodeToByteArray()
        return (existing.substring(0, begin) + regionText + existing.substring(endExclusive)).encodeToByteArray()
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

    private data class DocRegion(
        val markerBegin: String,
        val markerEnd: String,
    )

    private data class PlanItem(
        val kind: String,
        val target: String,
        val id: String,
        val version: String,
        val resource: String,
        val relativePath: String,
        val destination: Path,
        val region: DocRegion? = null,
    )

    private data class StagedPayloads(
        val directory: Path,
        val files: Map<PlanItem, StagedFile>,
    )

    private class StagedFile(
        val path: Path,
        val content: ByteArray,
        val ownedSha: String,
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
