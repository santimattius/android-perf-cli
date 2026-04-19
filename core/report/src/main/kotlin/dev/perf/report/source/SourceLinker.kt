package dev.perf.report.source

import java.io.File

/**
 * Maps Android class names to local source files and generates IDE deep-links.
 *
 * Design decision: project root detection walks UP from cwd rather than
 * requiring explicit configuration. Same algorithm as git — run the CLI from
 * anywhere inside the project tree and it resolves correctly.
 *
 * Design decision: `idea://open?file=...&line=...` is the IntelliJ/Android
 * Studio URL protocol. It works out-of-the-box on macOS/Linux when AS is
 * installed. Emitting the raw URL lets both the Markdown reporter (embed as
 * `[text](url)`) and the console reporter (OSC 8 hyperlink) use the same
 * resolved value without duplicating resolution logic.
 *
 * Design decision: [linkFor] returns null (not throws) when the file cannot
 * be located. Reporters treat null as "omit the link" — a missing link is
 * never a reason to abort the report.
 */
class SourceLinker(val projectRoot: File = detectProjectRoot()) {

    fun linkFor(frame: StackFrameParser.Frame): String? {
        val file = resolveFile(frame.className) ?: return null
        return "idea://open?file=${file.absolutePath}&line=${frame.line}"
    }

    fun resolveFile(androidClass: String): File? {
        val topLevelClass = androidClass.substringBefore('$')
        val ktPath = topLevelClass.replace('.', '/') + ".kt"
        val javaPath = topLevelClass.replace('.', '/') + ".java"

        return sequenceOf(ktPath, javaPath)
            .flatMap { path -> SOURCE_ROOTS.map { root -> File(projectRoot, "$root/$path") } }
            .firstOrNull { it.exists() }
    }

    companion object {
        private val SOURCE_ROOTS = listOf(
            "app/src/main/kotlin",
            "app/src/main/java",
            "src/main/kotlin",
            "src/main/java",
        )

        fun detectProjectRoot(from: File = File(System.getProperty("user.dir"))): File {
            var current: File? = from
            while (current != null) {
                if (File(current, ".git").exists() ||
                    File(current, "build.gradle.kts").exists() ||
                    File(current, "build.gradle").exists()
                ) return current
                current = current.parentFile
            }
            return from
        }
    }
}
