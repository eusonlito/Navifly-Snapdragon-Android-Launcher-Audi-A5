package com.lito.a5launcher

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

internal object LauncherUpdateInstaller {
    const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val FILE_PROVIDER_SUFFIX = ".update-files"
    private const val UPDATE_DIRECTORY = "updates"
    private const val UPDATE_FILE = "A5Cockpit-update.apk"
    private const val MAX_APK_SIZE_BYTES = 250L * 1024L * 1024L

    sealed interface PreparationResult {
        data class Ready(val file: File) : PreparationResult
        data object Unreadable : PreparationResult
        data object TooLarge : PreparationResult
        data object InvalidApk : PreparationResult
        data object WrongApplication : PreparationResult
    }

    fun prepare(context: Context, source: Uri): PreparationResult {
        val updateDirectory = File(context.cacheDir, UPDATE_DIRECTORY).apply { mkdirs() }
        val temporaryFile = File(updateDirectory, "$UPDATE_FILE.part")
        val updateFile = File(updateDirectory, UPDATE_FILE)
        temporaryFile.delete()

        var tooLarge = false
        val copied = runCatching {
            context.contentResolver.openInputStream(source)?.use { input ->
                temporaryFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_APK_SIZE_BYTES) {
                            tooLarge = true
                            break
                        }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: return PreparationResult.Unreadable
        }.isSuccess

        if (tooLarge) {
            temporaryFile.delete()
            return PreparationResult.TooLarge
        }
        if (!copied) {
            temporaryFile.delete()
            return PreparationResult.Unreadable
        }

        val archivePackage = readArchivePackageName(context.packageManager, temporaryFile)
            ?: return PreparationResult.InvalidApk.also { temporaryFile.delete() }
        if (archivePackage != context.packageName) {
            temporaryFile.delete()
            return PreparationResult.WrongApplication
        }

        updateFile.delete()
        if (!temporaryFile.renameTo(updateFile)) {
            temporaryFile.delete()
            return PreparationResult.Unreadable
        }
        return PreparationResult.Ready(updateFile)
    }

    fun canInstallPackages(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun requestPermissionIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        "package:${context.packageName}".toUri(),
    )

    fun install(context: Context, apk: File): Result<Unit> = runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + FILE_PROVIDER_SUFFIX,
            apk,
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME_TYPE)
                clipData = ClipData.newRawUri("A5 Cockpit update", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
    }

    @Suppress("DEPRECATION")
    private fun readArchivePackageName(packageManager: PackageManager, apk: File): String? =
        packageManager.getPackageArchiveInfo(apk.absolutePath, 0)?.packageName
}
