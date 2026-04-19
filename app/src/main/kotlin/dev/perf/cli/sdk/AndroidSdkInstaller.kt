package dev.perf.cli.sdk

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Downloads Android command-line tools, installs [platform-tools] (adb) via [sdkmanager],
 * and returns the SDK root directory.
 *
 * Uses the same default locations as Android Studio when [targetRoot] is null
 * ([~/Library/Android/sdk] on macOS, [~/Android/Sdk] on Linux, [%LOCALAPPDATA%/Android/Sdk] on Windows).
 */
object AndroidSdkInstaller {

    private const val CMDLINE_TOOLS_REVISION = "13114758"

    fun defaultInstallRoot(): File = when (Os.current) {
        Os.MAC -> File(System.getProperty("user.home"), "Library/Android/sdk")
        Os.LINUX -> File(System.getProperty("user.home"), "Android/Sdk")
        Os.WINDOWS -> {
            val base = System.getenv("LOCALAPPDATA")
                ?: System.getenv("USERPROFILE")
                ?: System.getProperty("user.home")
            File(base, "Android/Sdk")
        }
    }

    fun installMinimal(
        targetRoot: File = defaultInstallRoot(),
        log: (String) -> Unit = {},
    ): File {
        targetRoot.mkdirs()
        require(targetRoot.isDirectory) { "Cannot create SDK directory: ${targetRoot.absolutePath}" }

        val sdkmanager = findSdkManager(targetRoot)
        if (sdkmanager != null) {
            log("Using existing sdkmanager at ${sdkmanager.absolutePath}")
            acceptLicenses(targetRoot, sdkmanager, log)
            installPlatformTools(targetRoot, sdkmanager, log)
            return targetRoot.absoluteFile
        }

        val zipUrl = commandLineToolsUrl()
        val zipFile = File.createTempFile("cmdline-tools", ".zip")
        try {
            log("Downloading command-line tools from $zipUrl …")
            downloadToFile(URL(zipUrl), zipFile, log)
            log("Extracting command-line tools into ${targetRoot.absolutePath} …")
            unzipCmdlineTools(zipFile, targetRoot, log)
        } finally {
            zipFile.delete()
        }

        val sm = findSdkManager(targetRoot)
            ?: error("sdkmanager not found after extracting command-line tools.")

        acceptLicenses(targetRoot, sm, log)
        installPlatformTools(targetRoot, sm, log)

        require(AndroidSdkLocator.hasAdb(targetRoot)) {
            "adb was not installed at ${targetRoot.absolutePath}/platform-tools/adb"
        }
        return targetRoot.absoluteFile
    }

    private fun commandLineToolsUrl(): String {
        val base = "https://dl.google.com/android/repository"
        return when (Os.current) {
            Os.MAC -> "$base/commandlinetools-mac-${CMDLINE_TOOLS_REVISION}_latest.zip"
            Os.LINUX -> "$base/commandlinetools-linux-${CMDLINE_TOOLS_REVISION}_latest.zip"
            Os.WINDOWS -> "$base/commandlinetools-win-${CMDLINE_TOOLS_REVISION}_latest.zip"
        }
    }

    private fun findSdkManager(sdkRoot: File): File? {
        val latest = File(sdkRoot, "cmdline-tools/latest/bin/sdkmanager")
        val latestBat = File(sdkRoot, "cmdline-tools/latest/bin/sdkmanager.bat")
        when {
            latest.isFile -> return latest
            latestBat.isFile -> return latestBat
        }
        return null
    }

    private fun downloadToFile(url: URL, dest: File, log: (String) -> Unit) {
        val conn = url.openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 30_000
        conn.readTimeout = 120_000
        conn.connect()
        val code = conn.responseCode
        require(code in 200..299) {
            "Download failed: HTTP $code for $url"
        }
        val len = conn.contentLengthLong
        if (len > 0) {
            log("Download size: ${len / 1024 / 1024} MiB")
        }
        conn.inputStream.use { input ->
            FileOutputStream(dest).use { out ->
                input.copyTo(out)
            }
        }
    }

    /**
     * Google zips contain a single top-level [cmdline-tools] directory; we place it under
     * [sdkRoot]/cmdline-tools/latest/.
     */
    private fun unzipCmdlineTools(zipFile: File, sdkRoot: File, log: (String) -> Unit) {
        val cmdlineDir = File(sdkRoot, "cmdline-tools")
        val latestDir = File(cmdlineDir, "latest")
        if (latestDir.exists()) {
            latestDir.deleteRecursively()
        }
        latestDir.mkdirs()

        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val outFile = safeZipTarget(latestDir, entry.name)
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { zis.copyTo(it) }
                }
                entry = zis.nextEntry
            }
        }
        // Zip layout is cmdline-tools/... ; we unzipped into latest/ with paths like cmdline-tools/bin/...
        val nested = File(latestDir, "cmdline-tools")
        if (nested.isDirectory) {
            log("Normalizing cmdline-tools layout …")
            nested.listFiles()?.forEach { child ->
                val target = File(latestDir, child.name)
                if (target.exists()) target.deleteRecursively()
                check(child.renameTo(target)) { "Failed to move ${child.name} into cmdline-tools/latest" }
            }
            nested.deleteRecursively()
        }
    }

    private fun safeZipTarget(baseDir: File, entryName: String): File {
        val stripped = entryName.trimStart('/').replace('\\', '/')
        val out = File(baseDir, stripped).canonicalFile
        val base = baseDir.canonicalFile
        require(out.path.startsWith(base.path + File.separator) || out == base) {
            "Bad zip entry: $entryName"
        }
        return out
    }

    private fun acceptLicenses(sdkRoot: File, sdkmanager: File, log: (String) -> Unit) {
        log("Accepting SDK licenses (one-time) …")
        val pb = ProcessBuilder(sdkManagerInvocation(sdkRoot, sdkmanager, "--licenses"))
            .redirectErrorStream(true)
        val p = pb.start()
        p.outputStream.bufferedWriter().use { w ->
            repeat(4000) {
                w.write("y\n")
                w.flush()
            }
        }
        val out = p.inputStream.bufferedReader().readText()
        val code = p.waitFor()
        if (code != 0) {
            error("sdkmanager --licenses failed (exit $code):\n$out")
        }
    }

    private fun installPlatformTools(sdkRoot: File, sdkmanager: File, log: (String) -> Unit) {
        log("Installing platform-tools (adb) …")
        val pb = ProcessBuilder(
            sdkManagerInvocation(
                sdkRoot,
                sdkmanager,
                "--install",
                "platform-tools",
            ),
        ).redirectErrorStream(true)
        val p = pb.start()
        p.outputStream.bufferedWriter().use { w ->
            repeat(4000) {
                w.write("y\n")
                w.flush()
            }
        }
        val out = p.inputStream.bufferedReader().readText()
        val code = p.waitFor()
        if (code != 0) {
            error("sdkmanager install platform-tools failed (exit $code):\n$out")
        }
    }

    private fun sdkManagerInvocation(sdkRoot: File, sdkmanager: File, vararg args: String): List<String> {
        val root = "--sdk_root=${sdkRoot.absolutePath}"
        return if (sdkmanager.extension.equals("bat", ignoreCase = true)) {
            listOf("cmd.exe", "/c", sdkmanager.absolutePath, root, *args)
        } else {
            listOf(sdkmanager.absolutePath, root, *args)
        }
    }
}
