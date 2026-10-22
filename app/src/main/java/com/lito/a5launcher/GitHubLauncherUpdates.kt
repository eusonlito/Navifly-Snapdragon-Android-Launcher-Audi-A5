package com.lito.a5launcher

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

private const val RELEASE_REPOSITORY = "eusonlito/Navifly-Snapdragon-Android-Launcher-Audi-A5"
private const val RELEASE_APK = "A5Cockpit.apk"

internal data class LauncherRelease(val version: String) {
    val apkUrl: String get() = "https://github.com/$RELEASE_REPOSITORY/releases/download/v$version/$RELEASE_APK"
    val checksumUrl: String get() = "$apkUrl.sha256"

    fun isNewerThan(installed: String): Boolean {
        val availableParts = versionParts(version)
        val installedParts = versionParts(installed)
        for (index in availableParts.indices) {
            val comparison = availableParts[index].compareTo(installedParts[index])
            if (comparison != 0) return comparison > 0
        }
        return false
    }
}

private fun versionParts(version: String): List<Int> {
    require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(version))
    return version.split('.').map(String::toInt)
}

internal class GitHubLauncherUpdates(
    private val client: Call.Factory = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.MINUTES)
        .followSslRedirects(false)
        .build(),
) {
    fun latest(): LauncherRelease = parseRelease(
        readText("https://api.github.com/repos/$RELEASE_REPOSITORY/releases/latest", 256 * 1024),
    )

    fun download(release: LauncherRelease, directory: File): File {
        val checksum = parseChecksum(readText(release.checksumUrl, 1024))
        val file = File.createTempFile("a5-release-", ".apk", directory)
        try {
            client.newCall(request(release.apkUrl)).execute().use { response ->
                if (!response.isSuccessful) throw IOException("APK HTTP ${response.code}")
                response.body.byteStream().use { input ->
                    file.outputStream().buffered().use { output ->
                        copyVerifiedApk(input, output, checksum)
                    }
                }
            }
            return file
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    private fun readText(url: String, limit: Int): String =
        client.newCall(request(url)).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Release HTTP ${response.code}")
            val bytes = response.body.byteStream().use { it.readNBytes(limit + 1) }
            if (bytes.size > limit) throw IOException("Release response too large")
            bytes.toString(Charsets.UTF_8)
        }

    private fun request(url: String): Request = Request.Builder().url(url)
        .header("User-Agent", "A5Cockpit/${BuildConfig.VERSION_NAME}")
        .build()

    companion object {
        internal fun parseRelease(json: String): LauncherRelease {
            val release = JSONObject(json)
            require(!release.getBoolean("draft") && !release.getBoolean("prerelease"))
            val version = release.getString("tag_name").removePrefix("v")
            versionParts(version)
            val assets = release.getJSONArray("assets")
            val names = (0 until assets.length()).map { assets.getJSONObject(it) }
                .filter { it.optString("state") == "uploaded" && it.optLong("size") > 0 }
                .map { it.getString("name") }
            require(RELEASE_APK in names && "$RELEASE_APK.sha256" in names)
            return LauncherRelease(version)
        }

        internal fun parseChecksum(text: String): String =
            Regex("([0-9a-fA-F]{64})[ \\t]+\\*?A5Cockpit\\.apk")
                .matchEntire(text.trim())?.groupValues?.get(1)?.lowercase()
                ?: throw IOException("Invalid APK checksum")

        internal fun copyVerifiedApk(input: InputStream, output: OutputStream, checksum: String) {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > LauncherUpdateInstaller.MAX_APK_SIZE_BYTES) throw IOException("APK too large")
                digest.update(buffer, 0, count)
                output.write(buffer, 0, count)
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != checksum) throw IOException("APK checksum mismatch")
        }
    }
}
