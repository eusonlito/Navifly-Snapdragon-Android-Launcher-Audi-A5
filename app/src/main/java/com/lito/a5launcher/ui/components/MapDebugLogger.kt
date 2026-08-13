package com.lito.a5launcher.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class MapDebugLogStats(val fileCount: Int, val sizeBytes: Long)

internal object MapDebugLogArchive {
    fun isMapLog(file: File): Boolean =
        file.isFile && file.name.startsWith("map-debug-") && file.extension == "log"

    fun addFiles(files: List<File>, zip: ZipOutputStream) {
        files.sortedBy(File::getName).forEach { file ->
            zip.putNextEntry(ZipEntry("map-debug/${file.name}"))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
}

@SuppressLint("StaticFieldLeak")
class MapDebugLogger private constructor(private val context: Context) {
    companion object {
        private const val LOG_DIRECTORY = "map-debug-logs"
        @Volatile
        private var instance: MapDebugLogger? = null

        fun get(context: Context): MapDebugLogger =
            instance ?: synchronized(this) {
                instance ?: MapDebugLogger(context.applicationContext).also { instance = it }
            }

        fun deleteAll(context: Context): Int = get(context).deleteAll()

        fun stats(context: Context): MapDebugLogStats = get(context).stats()

    }

    private val ioDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val lock = Any()
    private val directory = File(context.filesDir, LOG_DIRECTORY)
    private var enabled = false
    private var generation = 0L
    private var activeFile: File? = null

    var displayPath: String = ""
        private set

    fun setEnabled(value: Boolean) {
        synchronized(lock) {
            if (enabled == value) return
            if (value) {
                generation++
                directory.mkdirs()
                activeFile = File(directory, "map-debug-${fileTimestamp()}.log")
                displayPath = activeFile?.name.orEmpty()
                enabled = true
            }
        }
        if (value) {
            write(
                "DEBUG ACTIVADO | Android ${Build.VERSION.RELEASE} API ${Build.VERSION.SDK_INT} " +
                    "| ${Build.MANUFACTURER} ${Build.MODEL} | pid=${android.os.Process.myPid()}"
            )
        } else {
            write("DEBUG DESACTIVADO")
            synchronized(lock) { enabled = false }
        }
    }

    fun write(message: String) {
        val target = synchronized(lock) {
            if (!enabled) return
            val file = activeFile ?: return
            PendingWrite(file, generation, "${timestamp()} | $message\n")
        }
        scope.launch {
            synchronized(lock) {
                if (target.generation == generation && target.file == activeFile) {
                    runCatching {
                        target.file.appendText(target.line)
                    }
                }
            }
        }
    }

    fun writeImmediately(message: String) {
        synchronized(lock) {
            if (!enabled) return
            val file = activeFile ?: return
            runCatching {
                file.appendText("${timestamp()} | $message\n")
            }
        }
    }

    fun suggestedExportName(): String = "map-debug-${fileTimestamp()}.zip"

    suspend fun export(destination: Uri): Boolean = withContext(ioDispatcher) {
        val internalFiles = synchronized(lock) { internalFiles() }
        if (internalFiles.isEmpty()) return@withContext false
        runCatching {
            context.contentResolver.openOutputStream(destination, "w")!!.use { output ->
                ZipOutputStream(output.buffered()).use { zip ->
                    MapDebugLogArchive.addFiles(internalFiles, zip)
                }
            }
        }.isSuccess
    }

    fun stats(): MapDebugLogStats = synchronized(lock) {
        val internalFiles = internalFiles()
        MapDebugLogStats(
            fileCount = internalFiles.size,
            sizeBytes = internalFiles.sumOf(File::length),
        )
    }

    fun deleteAll(): Int {
        synchronized(lock) {
            enabled = false
            generation++
            activeFile = null
            displayPath = ""
            val internal = internalFiles()
            val deletedInternal = internal.count { it.delete() }
            directory.delete()
            return deletedInternal
        }
    }

    private fun internalFiles(): List<File> = directory.listFiles()
        ?.filter(MapDebugLogArchive::isMapLog)
        .orEmpty()

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    private fun fileTimestamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())

    private data class PendingWrite(val file: File, val generation: Long, val line: String)

}
