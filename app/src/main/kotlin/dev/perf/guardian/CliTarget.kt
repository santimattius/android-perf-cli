package dev.perf.guardian

/**
 * A coding agent Guardian can install for. Per-agent variation is data only: where skills and
 * personas live and which persona file extension the agent reads.
 */
enum class CliTarget(
    val id: String,
    val skillsDir: String,
    val agentsDir: String,
    val personaExtension: String,
) {
    CLAUDE("claude", ".claude/skills", ".claude/agents", "md"),
    CODEX("codex", ".agents/skills", ".codex/agents", "toml"),
    ;

    fun skillPath(skillId: String): String = "$skillsDir/$skillId/SKILL.md"

    fun personaPath(personaId: String): String = "$agentsDir/$personaId.$personaExtension"

    companion object {
        fun byId(id: String): CliTarget? = entries.firstOrNull { it.id == id }
    }
}
