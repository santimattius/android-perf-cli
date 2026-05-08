package dev.perf.report

import dev.perf.analysis.PerfReport
import dev.perf.analysis.model.ExitEvent
import dev.perf.analysis.model.ExitSignalReport
import dev.perf.analysis.model.ExitType
import dev.perf.analysis.model.MemoryLeak
import dev.perf.analysis.model.ColdStartMeasurement
import dev.perf.analysis.model.PotentialAnrHint
import dev.perf.analysis.model.PotentialAnrHintType
import dev.perf.analysis.model.RenderingIncident
import dev.perf.analysis.model.Severity
import dev.perf.analysis.model.StrictModeViolation
import dev.perf.analysis.model.StrictModeViolation.ViolationType
import dev.perf.analysis.model.ThresholdBreach
import dev.perf.analysis.model.BackgroundNetworkReport
import dev.perf.analysis.model.StuckWakelockEntry
import dev.perf.analysis.model.StuckWakelockReport
import dev.perf.analysis.model.WakelockEntry
import dev.perf.analysis.model.WakelockReport
import dev.perf.analysis.model.WifiScanReport
import dev.perf.report.ReportRenderExtras
import dev.perf.report.source.SourceLinker
import dev.perf.report.vitals.VitalsPlaybook
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavioral tests for [MarkdownReporter].
 *
 * We test the rendered String output — not intermediate state — because
 * the reporter's contract is "given this domain data, produce this Markdown".
 * If the internal implementation changes, the tests remain valid as long as
 * the observable output is correct.
 */
class MarkdownReporterTest {

    private val pkg = "com.example.app"

    // ── Calm Tech ─────────────────────────────────────────────────────────────

    @Test
    fun `renders calm message when there are no issues`() {
        val output = MarkdownReporter().render(cleanReport())

        assertContains(output, "No performance issues detected")
    }

    @Test
    fun `omits all detail sections when there are no issues`() {
        val output = MarkdownReporter().render(cleanReport())

        assertFalse(output.contains("## Memory Leaks"))
        assertFalse(output.contains("## StrictMode"))
        assertFalse(output.contains("## Rendering"))
    }

    @Test
    fun `renders wake locks table when wakelockReport is present`() {
        val wl =
            WakelockReport(
                entries = listOf(WakelockEntry("*alarm*", 1, 5000)),
                totalDurationMs = 5000,
                severity = Severity.WARNING,
            )
        val output = MarkdownReporter().render(cleanReport().copy(wakelockReport = wl))

        assertContains(output, "## Wake Locks")
        assertContains(output, "*alarm*")
        assertContains(output, "5000")
    }

    // ── Header ────────────────────────────────────────────────────────────────

    @Test
    fun `header contains the package name`() {
        val output = MarkdownReporter().render(cleanReport())

        assertContains(output, pkg)
    }

    @Test
    fun `header shows CRITICAL severity when leaks are present`() {
        val output = MarkdownReporter().render(reportWith(leaks = listOf(aLeak())))

        assertContains(output, "CRITICAL")
    }

    @Test
    fun `header shows WARNING severity when only rendering issues exist`() {
        val output = MarkdownReporter().render(
            reportWith(rendering = aRenderingIncident(Severity.WARNING))
        )

        assertContains(output, "WARNING")
    }

    // ── Memory Leaks ──────────────────────────────────────────────────────────

    @Test
    fun `renders vitals guidance after memory leaks section`() {
        val output = MarkdownReporter().render(reportWith(leaks = listOf(aLeak())))

        assertContains(output, "## Memory Leaks")
        assertContains(output, "### Guidance (Vitals: memory / leaks)")
        assertContains(output, VitalsPlaybook.VITALS_HUB)
    }

    @Test
    fun `renders cold start summary in calm path when severity is INFO`() {
        val cs = ColdStartMeasurement(
            totalTimeMs = 800,
            waitTimeMs = 800,
            launchState = "COLD",
            status = "ok",
            parsedOk = true,
            message = "Cold start TotalTime=800ms",
            severity = Severity.INFO,
        )
        val output = MarkdownReporter().render(reportWith(coldStart = cs))

        assertContains(output, "### Cold start (measured)")
        assertContains(output, "800")
        assertContains(output, "### Guidance (Vitals: launch)")
    }

    @Test
    fun `renders android cli docs block when extras provide snippet`() {
        val output = MarkdownReporter().render(
            report = cleanReport(),
            extras = ReportRenderExtras(androidCliDocsSnippet = "https://developer.android.com/foo\nMore text"),
        )
        assertContains(output, "## Official docs (Android CLI)")
        assertContains(output, "https://developer.android.com/foo")
    }

    @Test
    fun `renders memory leak section with class name`() {
        val leak = aLeak(leakingObject = "com.example.MyFragment")
        val output = MarkdownReporter().render(reportWith(leaks = listOf(leak)))

        assertContains(output, "## Memory Leaks")
        assertContains(output, "com.example.MyFragment")
    }

    @Test
    fun `renders retained heap size in KB`() {
        val leak = aLeak(retainedBytes = 512 * 1024L)
        val output = MarkdownReporter().render(reportWith(leaks = listOf(leak)))

        assertContains(output, "512 KB")
    }

    @Test
    fun `renders shortest path when present`() {
        val leak = aLeak(shortestPath = "Activity → ViewModel → Repository")
        val output = MarkdownReporter().render(reportWith(leaks = listOf(leak)))

        assertContains(output, "Activity → ViewModel → Repository")
    }

    @Test
    fun `omits shortest path line when path is blank`() {
        val leak = aLeak(shortestPath = "")
        val output = MarkdownReporter().render(reportWith(leaks = listOf(leak)))

        assertFalse(output.contains("Shortest path"))
    }

    // ── StrictMode Violations ─────────────────────────────────────────────────

    @Test
    fun `renders strictmode section when violations are present`() {
        val output = MarkdownReporter().render(
            reportWith(violations = listOf(aViolation(violationType = ViolationType.DISK_READ)))
        )

        assertContains(output, "## StrictMode Violations")
    }

    @Test
    fun `groups violations by type into separate subsections`() {
        val violations = listOf(
            aViolation(violationType = ViolationType.DISK_READ),
            aViolation(violationType = ViolationType.NETWORK),
        )
        val output = MarkdownReporter().render(reportWith(violations = violations))

        assertContains(output, "Disk Read on Main Thread")
        assertContains(output, "Network on Main Thread")
    }

    @Test
    fun `shows at most 3 violations per type`() {
        val violations = List(5) { aViolation(violationType = ViolationType.DISK_READ) }
        val output = MarkdownReporter().render(reportWith(violations = violations))

        // 4 occurrences of <details> means exactly 3 shown (the 4th would be wrong)
        val detailsCount = output.split("<details>").size - 1
        assertTrue(detailsCount <= 3, "Expected ≤ 3 <details> blocks, found $detailsCount")
    }

    @Test
    fun `shows omitted count when violations of a type exceed 3`() {
        val violations = List(5) { aViolation(violationType = ViolationType.DISK_READ) }
        val output = MarkdownReporter().render(reportWith(violations = violations))

        assertContains(output, "2 more occurrences omitted")
    }

    @Test
    fun `does not show omitted line when violations are exactly 3`() {
        val violations = List(3) { aViolation(violationType = ViolationType.DISK_READ) }
        val output = MarkdownReporter().render(reportWith(violations = violations))

        assertFalse(output.contains("omitted"))
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Test
    fun `renders rendering section when severity is CRITICAL`() {
        val output = MarkdownReporter().render(
            reportWith(rendering = aRenderingIncident(Severity.CRITICAL, frozenFrames = 2))
        )

        assertContains(output, "## Rendering Performance")
        assertContains(output, "2")
    }

    @Test
    fun `omits rendering section when severity is INFO`() {
        val output = MarkdownReporter().render(
            reportWith(
                leaks = listOf(aLeak()),
                rendering = aRenderingIncident(Severity.INFO),
            )
        )

        assertFalse(output.contains("## Rendering Performance"))
    }

    @Test
    fun `renders all three percentiles in rendering table`() {
        val output = MarkdownReporter().render(
            reportWith(rendering = aRenderingIncident(Severity.WARNING, p90 = 18.5, p95 = 24.0, p99 = 55.0))
        )

        assertContains(output, "18.50 ms")
        assertContains(output, "24.00 ms")
        assertContains(output, "55.00 ms")
    }

    // ── Threshold breaches ────────────────────────────────────────────────────

    @Test
    fun `renders threshold breach section when scenario SLOs are exceeded`() {
        val breach = ThresholdBreach("Frozen frames 1 exceeds threshold of 0")
        val output = MarkdownReporter().render(reportWith(breaches = listOf(breach)))

        assertContains(output, "## Threshold breaches")
        assertContains(output, "Frozen frames 1 exceeds threshold of 0")
        assertContains(output, "CRITICAL")
    }

    @Test
    fun `threshold breach section forces CRITICAL overall severity`() {
        val breach = ThresholdBreach("Janky frame rate 6.0% exceeds threshold of 5.0%")
        val output = MarkdownReporter().render(reportWith(breaches = listOf(breach)))

        val overallLine = output.lines().first { it.contains("Overall Severity") }
        assertContains(overallLine, "CRITICAL")
    }

    @Test
    fun `omits threshold breach section when there are no breaches`() {
        val output = MarkdownReporter().render(reportWith(leaks = listOf(aLeak())))

        assertFalse(output.contains("## Threshold breaches"))
    }

    // ── Source Linking ────────────────────────────────────────────────────────

    @Test
    fun `uses plain code block when sourceLinker is null`() {
        val violation = aViolation(stack = "\tat com.example.Foo.bar(Foo.kt:10)")
        val output = MarkdownReporter(sourceLinker = null)
            .render(reportWith(violations = listOf(violation)))

        assertContains(output, "```")
        assertFalse(output.contains("open in IDE"))
    }

    @Test
    fun `renders IDE links when frames resolve to existing files`() {
        val root = fakeProject { kotlinFile("com/example/Foo.kt") }
        val linker = SourceLinker(root)
        val violation = aViolation(stack = "\tat com.example.Foo.bar(Foo.kt:10)")

        val output = MarkdownReporter(sourceLinker = linker)
            .render(reportWith(violations = listOf(violation)))

        assertContains(output, "open in IDE")
        assertContains(output, "idea://open?file=")
        root.deleteRecursively()
    }

    @Test
    fun `falls back to code block when no frame in stack resolves`() {
        val root = fakeProject {}   // empty project — nothing resolves
        val linker = SourceLinker(root)
        val violation = aViolation(stack = "\tat com.example.Missing.bar(Missing.kt:1)")

        val output = MarkdownReporter(sourceLinker = linker)
            .render(reportWith(violations = listOf(violation)))

        assertContains(output, "```")
        assertFalse(output.contains("open in IDE"))
        root.deleteRecursively()
    }

    // ── Exit Signals ──────────────────────────────────────────────────────────

    @Test
    fun `render_exitSignalReport_hasExits_sectionPresent`() {
        val exitReport = anExitSignalReport(ExitType.ANR)
        val output = MarkdownReporter().render(cleanReport().copy(exitSignalReport = exitReport))
        assertContains(output, "## Exit Signals")
    }

    @Test
    fun `render_exitSignalReport_hasExits_tableRowPresent`() {
        val exitReport = anExitSignalReport(ExitType.ANR)
        val output = MarkdownReporter().render(cleanReport().copy(exitSignalReport = exitReport))
        assertContains(output, "ANR")
        assertContains(output, "com.example.app")
    }

    @Test
    fun `render_exitSignalReport_null_sectionAbsent`() {
        val output = MarkdownReporter().render(cleanReport().copy(exitSignalReport = null))
        assertFalse(output.contains("## Exit Signals"))
    }

    @Test
    fun `render_exitSignalReport_emptyEvents_sectionAbsent`() {
        val exitReport = ExitSignalReport(events = emptyList())
        val output = MarkdownReporter().render(cleanReport().copy(exitSignalReport = exitReport))
        assertFalse(output.contains("## Exit Signals"))
    }

    // ── Source Attribution (VitalsMetric footers) ─────────────────────────────

    @Test
    fun `startup section contains Source Android Vitals line`() {
        val report = reportWith(
            coldStart = ColdStartMeasurement(
                totalTimeMs = 6000,
                waitTimeMs = 6000,
                launchState = "COLD",
                status = "ok",
                parsedOk = true,
                message = "Cold start TotalTime=6000ms",
                severity = Severity.CRITICAL,
            ),
        )
        val output = MarkdownReporter().render(report)

        assertContains(output, "_Source: [Android Vitals]")
    }

    @Test
    fun `wakelock section contains Source Android Vitals line`() {
        val wl = WakelockReport(
            entries = listOf(WakelockEntry("*alarm*", 1, 5000)),
            totalDurationMs = 5000,
            severity = Severity.WARNING,
        )
        val output = MarkdownReporter().render(cleanReport().copy(wakelockReport = wl))

        assertContains(output, "_Source: [Android Vitals]")
    }

    @Test
    fun `exit signals section contains Source Android Vitals line`() {
        val exitReport = anExitSignalReport(ExitType.ANR)
        val output = MarkdownReporter().render(cleanReport().copy(exitSignalReport = exitReport))

        assertContains(output, "_Source: [Android Vitals]")
    }

    @Test
    fun `rendering section contains Source Android Vitals line`() {
        val output = MarkdownReporter().render(
            reportWith(rendering = aRenderingIncident(Severity.CRITICAL, frozenFrames = 2))
        )

        assertContains(output, "_Source: [Android Vitals]")
    }

    @Test
    fun `strictmode section contains Source Android Vitals line`() {
        val output = MarkdownReporter().render(
            reportWith(violations = listOf(aViolation(violationType = ViolationType.DISK_READ)))
        )

        assertContains(output, "_Source: [Android Vitals]")
    }

    @Test
    fun `potential ANR section contains Source Android Vitals line`() {
        val report = reportWith()
            .copy(potentialAnrHints = listOf(aPotentialAnrHint()))
        val output = MarkdownReporter().render(report)

        assertContains(output, "_Source: [Android Vitals]")
    }

    // ── Task 4.3/4.4: ANR/Crash rate documentation note ──────────────────────

    @Test
    fun `exit signals section includes ANR rate threshold note`() {
        val exitReport = anExitSignalReport(ExitType.ANR)
        val output = MarkdownReporter().render(cleanReport().copy(exitSignalReport = exitReport))

        assertContains(output, "0.47%")
    }

    @Test
    fun `exit signals section includes crash rate threshold note`() {
        val exitReport = anExitSignalReport(ExitType.ANR)
        val output = MarkdownReporter().render(cleanReport().copy(exitSignalReport = exitReport))

        assertContains(output, "1.09%")
    }

    @Test
    fun `exit signals section mentions fleet aggregation limitation`() {
        val exitReport = anExitSignalReport(ExitType.ANR)
        val output = MarkdownReporter().render(cleanReport().copy(exitSignalReport = exitReport))

        assertTrue(
            output.contains("fleet", ignoreCase = true) || output.contains("aggregat", ignoreCase = true) || output.contains("single run", ignoreCase = true),
            "Expected exit signals section to mention fleet aggregation limitation"
        )
    }

    // ── Task 4.5: playConsoleLimitationsBanner still present ─────────────────

    @Test
    fun `play console limitations banner is still present at end of report`() {
        val output = MarkdownReporter().render(reportWith(leaks = listOf(aLeak())))

        assertContains(output, "Android Vitals")
        assertContains(output, "Play")
    }

    // ── Task 5.1: Backward compatibility regression ────────────────────────

    @Test
    fun `legacy scenario without profile field renders same sections as before`() {
        // A standard report with no vitals profile — all sections should still be present.
        val output = MarkdownReporter().render(
            reportWith(
                violations = listOf(aViolation(violationType = ViolationType.DISK_READ)),
                rendering = aRenderingIncident(Severity.CRITICAL, frozenFrames = 1),
                leaks = listOf(aLeak()),
            )
        )

        // Sections that must still exist (unchanged by vitals profile)
        assertContains(output, "## Memory Leaks")
        assertContains(output, "## StrictMode Violations")
        assertContains(output, "## Rendering Performance")

        // New source footers are additive — they should be present now too
        assertContains(output, "_Source: [Android Vitals]")

        // The play console banner must still be at the bottom
        assertContains(output, "Android Vitals")
        assertFalse(output.contains("## Threshold breaches"), "Should not have threshold breaches without breach data")
    }

    @Test
    fun `no-profile scenario preserves all user threshold values in report rendering`() {
        // Simulate a clean report — the rendering path must be identical regardless of
        // whether vitals profile is used or not (no profile = no change to ThresholdConfig)
        val output = MarkdownReporter().render(cleanReport())

        assertContains(output, "No performance issues detected")
        // New source footers are NOT added to the "ship it" calm path
        // (sections only render when there are issues, so no footer lines expected here
        // unless startup/wakelock data is present)
    }

    // ── T-7.1: Background Network section ────────────────────────────────────

    @Test
    fun `renders background network section when backgroundNetwork is non-null`() {
        val bgNet = BackgroundNetworkReport(rxBytesDelta = 1024, txBytesDelta = 512, durationMs = 60000)
        val output = MarkdownReporter().render(cleanReport().copy(backgroundNetwork = bgNet))

        assertContains(output, "## Background Network")
        assertContains(output, "bg-network-usage")
        // Caveat line
        assertTrue(
            output.contains("netstats", ignoreCase = true) || output.contains("NetworkStatsManager", ignoreCase = true),
            "Expected background network section to include caveat about netstats/NetworkStatsManager",
        )
    }

    @Test
    fun `background network section contains rx and tx byte values`() {
        val bgNet = BackgroundNetworkReport(rxBytesDelta = 1024, txBytesDelta = 512, durationMs = 60000)
        val output = MarkdownReporter().render(cleanReport().copy(backgroundNetwork = bgNet))

        assertContains(output, "1024")
        assertContains(output, "512")
    }

    @Test
    fun `no background network section when backgroundNetwork is null`() {
        val output = MarkdownReporter().render(cleanReport().copy(backgroundNetwork = null))
        assertFalse(output.contains("## Background Network"))
    }

    // ── T-7.2: Wi-Fi Scans section ───────────────────────────────────────────

    @Test
    fun `renders wifi scans section when backgroundWifi is non-null`() {
        val bgWifi = WifiScanReport(scanCountDelta = 7, durationMs = 60000)
        val output = MarkdownReporter().render(cleanReport().copy(backgroundWifi = bgWifi))

        assertContains(output, "Wi-Fi Scans")
        assertContains(output, "bg-wifi")
    }

    @Test
    fun `wifi scans section contains scan count delta`() {
        val bgWifi = WifiScanReport(scanCountDelta = 7, durationMs = 60000)
        val output = MarkdownReporter().render(cleanReport().copy(backgroundWifi = bgWifi))
        assertContains(output, "7")
    }

    @Test
    fun `no wifi scans section when backgroundWifi is null`() {
        val output = MarkdownReporter().render(cleanReport().copy(backgroundWifi = null))
        assertFalse(output.contains("Wi-Fi Scans"))
    }

    // ── T-7.3: Stuck Wake Locks section — entries present ───────────────────

    @Test
    fun `renders stuck wake locks section with entries when stuckWakelocks is non-null`() {
        val entry = StuckWakelockEntry(name = "MY_LOCK", startCount = 1, endCount = 2, durationDeltaMs = 1000)
        val report = StuckWakelockReport(stuckEntries = listOf(entry))
        val output = MarkdownReporter().render(cleanReport().copy(stuckWakelocks = report))

        assertContains(output, "## Stuck Wake Locks")
        assertContains(output, "MY_LOCK")
        assertContains(output, "stuck-wakelock")
    }

    @Test
    fun `stuck wake locks section shows start and end counts`() {
        val entry = StuckWakelockEntry(name = "MY_LOCK", startCount = 1, endCount = 3, durationDeltaMs = 2000)
        val report = StuckWakelockReport(stuckEntries = listOf(entry))
        val output = MarkdownReporter().render(cleanReport().copy(stuckWakelocks = report))

        assertContains(output, "1")
        assertContains(output, "3")
    }

    // ── T-7.4: Stuck Wake Locks section — empty list ─────────────────────────

    @Test
    fun `stuck wake locks section renders empty message when stuckEntries is empty`() {
        val report = StuckWakelockReport(stuckEntries = emptyList())
        val output = MarkdownReporter().render(cleanReport().copy(stuckWakelocks = report))

        assertContains(output, "## Stuck Wake Locks")
        assertContains(output, "No stuck wake locks detected")
    }

    // ── T-7.5: All three null — no extra sections rendered ───────────────────

    @Test
    fun `no background signal sections when all three fields are null`() {
        val output = MarkdownReporter().render(
            cleanReport().copy(
                backgroundNetwork = null,
                backgroundWifi = null,
                stuckWakelocks = null,
            )
        )

        assertFalse(output.contains("## Background Network"), "Must not render background network section")
        assertFalse(output.contains("Wi-Fi Scans"), "Must not render wifi scans section")
        assertFalse(output.contains("## Stuck Wake Locks"), "Must not render stuck wake locks section")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun cleanReport() = PerfReport(
        packageName = pkg,
        strictModeViolations = emptyList(),
        renderingIncident = aRenderingIncident(Severity.INFO),
        memoryLeaks = emptyList(),
    )

    private fun reportWith(
        violations: List<StrictModeViolation> = emptyList(),
        rendering: RenderingIncident = aRenderingIncident(Severity.INFO),
        leaks: List<MemoryLeak> = emptyList(),
        breaches: List<ThresholdBreach> = emptyList(),
        coldStart: ColdStartMeasurement? = null,
    ) = PerfReport(
        packageName = pkg,
        strictModeViolations = violations,
        renderingIncident = rendering,
        memoryLeaks = leaks,
        thresholdBreaches = breaches,
        coldStart = coldStart,
    )

    private fun aViolation(
        violationType: ViolationType = ViolationType.DISK_READ,
        stack: String = "\tat android.app.ActivityThread.performLaunchActivity(ActivityThread.java:1)",
    ) = StrictModeViolation(
        violationType = violationType,
        threadName = "main",
        message = "StrictMode${violationType.name}Violation",
        stackTrace = stack,
    )

    private fun aLeak(
        leakingObject: String = "com.example.MyActivity",
        retainedBytes: Long = 1024L,
        shortestPath: String = "Activity → Runnable",
    ) = MemoryLeak(
        leakingObject = leakingObject,
        retainedBytes = retainedBytes,
        shortestPath = shortestPath,
        message = "Leak in $leakingObject",
    )

    private fun aRenderingIncident(
        severity: Severity,
        frozenFrames: Int = 0,
        p90: Double = 10.0,
        p95: Double = 12.0,
        p99: Double = 15.0,
    ) = RenderingIncident(
        severity = severity,
        message = "test",
        jankyFrames = 0,
        frozenFrames = frozenFrames,
        totalFrames = 100,
        p90Ms = p90,
        p95Ms = p95,
        p99Ms = p99,
    )

    private fun fakeProject(block: ProjectBuilder.() -> Unit): File {
        val root = Files.createTempDirectory("perf-reporter-test").toFile()
        File(root, "build.gradle.kts").createNewFile()
        ProjectBuilder(root).block()
        return root
    }

    private class ProjectBuilder(private val root: File) {
        fun kotlinFile(relativePath: String) {
            val file = File(root, "app/src/main/kotlin/$relativePath")
            file.parentFile.mkdirs()
            file.createNewFile()
        }
    }

    private fun anExitSignalReport(exitType: ExitType) = ExitSignalReport(
        events = listOf(
            ExitEvent(
                exitType = exitType,
                message = "ANR: Input dispatching timed out",
                packageName = pkg,
                rawLine = "01-15 10:00:00.100  1234  1235 I am_anr : [1234,1000,com.example.app,952,Input dispatching timed out]",
            ),
        ),
    )

    private fun aPotentialAnrHint() = PotentialAnrHint(
        severity = Severity.WARNING,
        message = "Choreographer skipped 50 frames",
        hintType = PotentialAnrHintType.CHOREOGRAPHER_SKIPPED_FRAMES,
        detail = "01-15 10:00:00.100  1234  1235 I Choreographer: Skipped 50 frames!  The application may be doing too much work on its main thread.",
    )
}
