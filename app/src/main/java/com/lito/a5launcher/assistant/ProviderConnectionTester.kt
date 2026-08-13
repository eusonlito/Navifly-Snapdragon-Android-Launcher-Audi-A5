package com.lito.a5launcher.assistant

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

internal interface ProviderConnectionTest {
    fun cancel()
}

internal class ProviderConnectionTester(
    private val client: OkHttpClient = defaultRealtimeHttpClient(),
) {
    fun test(
        provider: AssistantProvider,
        apiKey: String,
        onResult: (ProviderFailure?) -> Unit,
    ): ProviderConnectionTest {
        val call = client.newCall(buildRequest(provider, apiKey))
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) {
                    onResult(
                        ProviderFailure(
                            ProviderFailureKind.CONNECTION,
                            ProviderDiagnostic(message = e.localizedMessage),
                        ),
                    )
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        onResult(null)
                    } else {
                        onResult(
                            ProviderFailure(
                                ProviderFailureKind.REJECTED_REQUEST,
                                ProtocolJson.providerDiagnostic(
                                    it.body.string(),
                                    it.code,
                                    it.message,
                                ),
                            ),
                        )
                    }
                }
            }
        })
        return object : ProviderConnectionTest {
            override fun cancel() = call.cancel()
        }
    }

    internal fun buildRequest(provider: AssistantProvider, apiKey: String): Request = when (provider) {
        AssistantProvider.OPENAI -> Request.Builder()
            .url("https://api.openai.com/v1/responses")
            .header("Authorization", "Bearer $apiKey")
            .post(
                JSONObject()
                    .put("model", OPENAI_TEST_MODEL)
                    .put("input", "Reply OK")
                    .put("max_output_tokens", 16)
                    .toString().toRequestBody(JSON_MEDIA_TYPE),
            )
            .build()

        AssistantProvider.GEMINI -> Request.Builder()
            .url(
                "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "$GEMINI_TEST_MODEL:generateContent",
            )
            .header("x-goog-api-key", apiKey)
            .post(
                JSONObject()
                    .put(
                        "contents",
                        JSONArray().put(
                            JSONObject().put(
                                "parts",
                                JSONArray().put(JSONObject().put("text", "Reply OK")),
                            ),
                        ),
                    )
                    .put(
                        "generationConfig",
                        JSONObject().put("maxOutputTokens", 8),
                    )
                    .toString().toRequestBody(JSON_MEDIA_TYPE),
            )
            .build()

        AssistantProvider.DISABLED -> error("Disabled provider cannot be tested")
    }

    internal companion object {
        const val OPENAI_TEST_MODEL = "gpt-5.4-nano"
        const val GEMINI_TEST_MODEL = "gemini-3.5-flash-lite"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
