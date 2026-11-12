package dev.perf.guardian

import kotlinx.serialization.Serializable

/**
 * The embedded, versioned catalog of everything `perf guardian install` can write:
 * skills, personas (per CLI target), and doc snippets (e.g. Codex's `AGENTS.md` block).
 *
 * This is READ-ONLY and ships with the binary — see design decision D2
 * (`sdd/android-agent-guardian/design`): the catalog is distinct from the
 * per-repo mutable installed receipt (`.guardian-manifest.json`, introduced in PR2).
 *
 * PR1 ships this schema with an EMPTY roster (no skills/personas/docSnippets yet) —
 * it exists solely to prove classpath resource reachability under `--no-fallback`
 * GraalVM native-image. PR2/PR3/PR4 populate the roster; this file's shape does not
 * need to change to accommodate them.
 */
@Serializable
data class GuardianManifest(
    val guardianVersion: String,
    val verifiedAgainst: List<VerifiedAgainstEntry> = emptyList(),
    val skills: List<SkillEntry> = emptyList(),
    val personas: List<PersonaEntry> = emptyList(),
    val docSnippets: List<DocSnippetEntry> = emptyList(),
)

/** A citation for a spec/standard the guardian catalog format itself is verified against. */
@Serializable
data class VerifiedAgainstEntry(
    val spec: String,
    val source: String,
    val date: String,
)

/** One installable skill: a `SKILL.md` classpath resource plus optional extra resources. */
@Serializable
data class SkillEntry(
    val id: String,
    val version: String,
    val resource: String,
    val extraResources: List<String> = emptyList(),
)

/**
 * One installable persona, mapped to a `perf analyze <domain>` and the skill(s) it requires.
 * [artifacts] is keyed by [CliTarget.id] — a persona may lack an entry for a CLI target that
 * does not support it yet (e.g. PR2/PR3 ship Claude-only entries; PR4 adds `"codex"`).
 */
@Serializable
data class PersonaEntry(
    val id: String,
    val version: String,
    val domain: String,
    val requiresSkills: List<String> = emptyList(),
    val artifacts: Map<String, String> = emptyMap(),
)

/** A marker-delimited doc snippet appended to a file like `AGENTS.md` (design decision D6). */
@Serializable
data class DocSnippetEntry(
    val id: String,
    val target: String,
    val version: String,
    val path: String,
    val resource: String,
    val markerBegin: String,
    val markerEnd: String,
)
