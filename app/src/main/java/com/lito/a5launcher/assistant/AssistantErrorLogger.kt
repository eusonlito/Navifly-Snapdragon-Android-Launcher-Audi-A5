package com.lito.a5launcher.assistant

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class AssistantErrorLogStats(val fileCount: Int, val sizeBytes: Long)

enum class AssistantFailurePhase { TURN, CONNECTION_TEST, DESTINATION_SEARCH }

enum class AssistantFailureSource(val model: String?) {
    OPENAI(AssistantProvider.OPENAI.model),
    GEMINI(AssistantProvider.GEMINI.model),
    GOOGLE_PLACES(null);

    companion object {
        fun from(provider: AssistantProvider): AssistantFailureSource = when (provider) {
            AssistantProvider.OPENAI -> OPENAI
            AssistantProvider.GEMINI -> GEMINI
            AssistantProvider.DISABLED -> error("Disabled provider cannot produce a request failure")
        }
    }
}

class AssistantErrorLogger(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, ERROR_DIRECTORY)
    private val lock = Any()

    fun record(
        source: AssistantFailureSource,
        phase: AssistantFailurePhase,
        failure: ProviderFailure,
    ) = synchronized(lock) {
        directory.mkdirs()
        val timestamp = System.currentTimeMillis()
        val record = JSONObject()
            .put("timestamp", ISO_TIMESTAMP.format(Date(timestamp)))
            .put("source", source.name)
            .putOpt("model", source.model)
            .put("phase", phase.name)
            .put("failure", failure.kind.name)
        failure.diagnostic?.let { diagnostic ->
            record.put(
                "diagnostic",
                JSONObject()
                    .putOpt("http_status", diagnostic.httpStatus)
                    .putOpt("code", diagnostic.code)
                    .putOpt("message", diagnostic.message),
            )
        }
        File(
            directory,
            "assistant-error-${FILE_TIMESTAMP.format(Date(timestamp))}-${UUID.randomUUID()}.json",
        ).writeText(record.toString(2))
    }

    fun stats(): AssistantErrorLogStats = synchronized(lock) {
        val files = errorFiles()
        AssistantErrorLogStats(files.size, files.sumOf(File::length))
    }

    fun clear(): Int = synchronized(lock) {
        val files = errorFiles()
        files.forEach(File::delete)
        if (directory.isDirectory && directory.listFiles().isNullOrEmpty()) directory.delete()
        files.count { !it.exists() }
    }

    fun suggestedExportName(): String =
        "assistant-errors-${FILE_TIMESTAMP.format(Date())}.zip"

    fun export(destination: Uri): Boolean = synchronized(lock) {
        val files = errorFiles()
        if (files.isEmpty()) return false
        val resolver = appContext.contentResolver
        runCatching {
            resolver.openOutputStream(destination, "w")!!.use { output ->
                ZipOutputStream(output.buffered()).use { zip ->
                    files.forEach { file ->
                        zip.putNextEntry(ZipEntry("$ERROR_DIRECTORY/${file.name}"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }.isSuccess
    }

    private fun errorFiles(): List<File> = directory.listFiles()
        ?.filter { it.isFile && it.extension == "json" }
        ?.sortedBy { it.name }
        .orEmpty()

    private companion object {
        const val ERROR_DIRECTORY = "assistant-errors"
        val ISO_TIMESTAMP = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
        val FILE_TIMESTAMP = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US)
    }
}
