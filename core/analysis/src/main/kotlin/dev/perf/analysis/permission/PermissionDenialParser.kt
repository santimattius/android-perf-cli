package dev.perf.analysis.permission

import dev.perf.analysis.model.PermissionDenial
import dev.perf.analysis.model.Severity

// File-scope regexes — GraalVM-safe (initialized eagerly at AOT time; no companion object, no init block).

/**
 * Matches a "Permission Denial:" logcat line from ActivityManager:W or PackageManager:W.
 *
 * The line may contain optional prefixes before the (pid=..., uid=...) block:
 *   - "opening provider <ProviderClass> " (PackageManager shape)
 *   - "from ProcessRecord{...} " (ActivityManager shape; ProcessRecord may contain spaces)
 *
 * Strategy: instead of matching every optional prefix token, we find the
 * (pid=..., uid=...) block first with a lazy lookahead, then the "requires" clause.
 * This is robust to any prefix shape the OEM might emit.
 *
 * Capture groups:
 *   [1] pid  (may be empty when the (pid=..., uid=...) block is absent)
 *   [2] uid  (may be empty when the block is absent)
 *   [3] permission (the dotted Android permission string after "requires")
 */
private val DENIAL_LINE_REGEX = Regex(
    """Permission Denial:.*?\(pid=(\d+),\s*uid=(\d+)\).*?requires?\s+([\w.]+)""",
    setOf(RegexOption.DOT_MATCHES_ALL),
)

/**
 * Matches the first `userId=<int>` token in the output of `dumpsys package <pkg>`.
 *
 * Multi-user devices emit multiple lines; per ADR-3 we take the FIRST match (default user, user 0).
 */
private val UID_FROM_DUMPSYS_PACKAGE_REGEX = Regex("""userId=(\d+)""")

internal object PermissionDenialParser {

    /**
     * Parses a single logcat line tagged `ActivityManager:W` or `PackageManager:W`.
     *
     * Returns null when:
     *  - line does not contain `Permission Denial:` (fast path)
     *  - regex match fails to recover both `uid` and `permission`
     *  - extracted `uid` does not equal [expectedUid] (UID-based filtering, ADR-3)
     *
     * The caller is responsible for routing lines from the correct stream, but UID equality is
     * also enforced here as defense-in-depth (matches `ExitSignalParser` style).
     */
    fun parseDenialLine(line: String, expectedUid: Int, packageName: String): PermissionDenial? {
        if (!line.contains("Permission Denial:")) return null
        val match = DENIAL_LINE_REGEX.find(line) ?: return null
        val uidStr = match.groupValues[2].ifBlank { return null }
        val parsedUid = uidStr.toIntOrNull() ?: return null
        if (parsedUid != expectedUid) return null
        val permission = match.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() } ?: "UNKNOWN"
        return PermissionDenial(
            severity = Severity.WARNING,
            message = "Permission Denial: $permission",
            packageName = packageName,
            permission = permission,
            uid = parsedUid,
            rawLine = line,
        )
    }

    /**
     * Extracts the first `userId=<int>` token from the output of `dumpsys package <pkg>`.
     *
     * Returns null when no match is present (blank input, package not found, or malformed output).
     *
     * Per ADR-3: multi-user devices may emit MULTIPLE `userId=` lines. We deliberately pick the
     * FIRST match (default user, user 0 on most devices).
     */
    fun extractUid(dumpsysPackageOutput: String): Int? =
        UID_FROM_DUMPSYS_PACKAGE_REGEX.find(dumpsysPackageOutput)
            ?.groupValues?.get(1)?.toIntOrNull()
}
