package com.lito.a5launcher

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class GitHubLauncherUpdatesTest {
    @get:Rule val temporary = TemporaryFolder()
    private val checksum = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test
    fun stableReleaseRequiresBothUploadedAssetsAndUsesTheProjectDownloadUrl() {
        val release = GitHubLauncherUpdates.parseRelease(metadata())
        assertEquals("1.10.0", release.version)
        assertEquals(
            "https://github.com/eusonlito/Navifly-Snapdragon-Android-Launcher-Audi-A5/releases/download/v1.10.0/A5Cockpit.apk",
            release.apkUrl,
        )
        for (flag in listOf("draft", "prerelease")) {
            val json = JSONObject(metadata()).put(flag, true).toString()
            assertThrows(IllegalArgumentException::class.java) { GitHubLauncherUpdates.parseRelease(json) }
        }
        val incomplete = JSONObject(metadata())
        incomplete.getJSONArray("assets").getJSONObject(1).put("state", "new")
        assertThrows(IllegalArgumentException::class.java) { GitHubLauncherUpdates.parseRelease(incomplete.toString()) }
    }

    @Test
    fun comparesVersionsNumericallyAndDoesNotOfferDowngradesOrReinstalls() {
        assertTrue(LauncherRelease("1.10.0").isNewerThan("1.9.9"))
        assertTrue(LauncherRelease("2.0.0").isNewerThan("1.99.99"))
        assertFalse(LauncherRelease("1.3.4").isNewerThan("1.3.7"))
        assertFalse(LauncherRelease("1.3.7").isNewerThan("1.3.7"))
        for (tag in listOf("v1.3.8-beta", "../../evil", "v999999999999.0.0")) {
            assertThrows(IllegalArgumentException::class.java) {
                GitHubLauncherUpdates.parseRelease(JSONObject(metadata()).put("tag_name", tag).toString())
            }
        }
    }

    @Test
    fun downloadsAndVerifiesTheApkFromTheSameRelease() {
        val requests = mutableListOf<String>()
        val updater = updater(requests)
        val release = updater.latest()
        val directory = temporary.newFolder()
        val file = updater.download(release, directory)
        assertArrayEquals("abc".toByteArray(), file.readBytes())
        assertEquals(listOf(
            "https://api.github.com/repos/eusonlito/Navifly-Snapdragon-Android-Launcher-Audi-A5/releases/latest",
            release.checksumUrl,
            release.apkUrl,
        ), requests)
    }

    @Test
    fun checksumMismatchDeletesThePartialDownload() {
        val directory = temporary.newFolder()
        val updater = updater(mutableListOf(), apk = "corrupted")
        assertThrows(IOException::class.java) { updater.download(LauncherRelease("1.10.0"), directory) }
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test
    fun rejectsFailedRequestsAndInvalidChecksumFiles() {
        assertThrows(IOException::class.java) { updater(mutableListOf(), status = 403).latest() }
        assertEquals(checksum, GitHubLauncherUpdates.parseChecksum("${checksum.uppercase()}  A5Cockpit.apk\n"))
        for (text in listOf("$checksum  Other.apk", "broken", "$checksum  A5Cockpit.apk\nextra")) {
            assertThrows(IOException::class.java) { GitHubLauncherUpdates.parseChecksum(text) }
        }
    }

    @Test
    fun oversizedStreamsAreRejectedWithoutTrustingContentLength() {
        val input = object : InputStream() {
            private var remaining = LauncherUpdateInstaller.MAX_APK_SIZE_BYTES + 1
            override fun read(): Int = if (remaining-- > 0) 0 else -1
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (remaining == 0L) return -1
                val count = minOf(remaining, length.toLong()).toInt()
                remaining -= count
                return count
            }
        }
        assertThrows(IOException::class.java) {
            GitHubLauncherUpdates.copyVerifiedApk(input, OutputStream.nullOutputStream(), checksum)
        }
    }

    private fun metadata(): String = """
        {"tag_name":"v1.10.0","draft":false,"prerelease":false,"assets":[
            {"name":"A5Cockpit.apk","state":"uploaded","size":3},
            {"name":"A5Cockpit.apk.sha256","state":"uploaded","size":80}
        ]}
    """.trimIndent()

    private fun updater(requests: MutableList<String>, apk: String = "abc", status: Int = 200): GitHubLauncherUpdates =
        GitHubLauncherUpdates(OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request.url.toString()
            val body = when {
                request.url.encodedPath.endsWith("/latest") -> metadata()
                request.url.encodedPath.endsWith(".sha256") -> "$checksum  A5Cockpit.apk\n"
                else -> apk
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(status).message("Test response").body(body.toResponseBody()).build()
        }.build())
}
