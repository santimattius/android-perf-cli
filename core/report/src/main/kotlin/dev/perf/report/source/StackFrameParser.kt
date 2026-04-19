package dev.perf.report.source

/**
 * Parses a single JVM stack frame line into its constituent parts.
 *
 * Design decision: object singleton — stateless pure function. No need for
 * injection or instantiation; it's a parsing utility, not a service.
 *
 * Handles Kotlin and Java frames. Does NOT handle synthetic lambda frames
 * (e.g. `Lambda$1.invoke`) — those are filtered out because they don't map
 * to a meaningful source location.
 */
object StackFrameParser {

    // Matches: "at com.example.Foo$Inner.bar(Foo.kt:42)"
    private val FRAME_REGEX = Regex(
        """^\s*at\s+([\w.${'$'}]+)\.([\w<>${'$'}]+)\((\w+\.(?:kt|java)):(\d+)\)$"""
    )

    data class Frame(
        val className: String,   // com.example.Foo$Inner
        val method: String,      // bar
        val file: String,        // Foo.kt
        val line: Int,           // 42
        val raw: String,         // original line, preserved for output
    )

    /**
     * Returns null for lines that are not parseable stack frames
     * (blank lines, "... N more", Proguard-obfuscated frames, etc.).
     */
    fun parse(line: String): Frame? =
        FRAME_REGEX.matchEntire(line.trim())?.let { match ->
            Frame(
                className = match.groupValues[1],
                method = match.groupValues[2],
                file = match.groupValues[3],
                line = match.groupValues[4].toInt(),
                raw = line,
            )
        }
}
