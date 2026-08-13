package com.lito.a5launcher.assistant

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

internal class GooglePlacesDestinationResolver(
    private val client: OkHttpClient = PlacesHttpClient.instance,
) {
    fun resolve(
        apiKey: String,
        search: DestinationSearch,
        location: KnownLocation?,
        localeTag: String,
        onResult: (DestinationResolution) -> Unit,
    ): Call {
        val call = client.newCall(buildRequest(apiKey, search, location, localeTag))
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) {
                    onResult(DestinationResolution.Failure(ProviderDiagnostic(message = e.localizedMessage)))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body.string()
                    if (it.isSuccessful) {
                        onResult(parseResponse(body))
                    } else {
                        onResult(
                            DestinationResolution.Failure(
                                ProtocolJson.providerDiagnostic(body, it.code, it.message),
                            ),
                        )
                    }
                }
            }
        })
        return call
    }

    internal companion object {
        private const val ENDPOINT = "https://places.googleapis.com/v1/places:searchText"
        private const val FIELD_MASK =
            "places.displayName,places.formattedAddress,places.location"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun buildRequest(
            apiKey: String,
            search: DestinationSearch,
            location: KnownLocation?,
            localeTag: String,
        ): Request {
            val locale = Locale.forLanguageTag(localeTag)
            val body = JSONObject()
                .put("textQuery", search.query.trim())
                .put("pageSize", 1)
                .put("languageCode", locale.language.ifBlank { "es" })
                .put("regionCode", locale.country.ifBlank { "ES" })
            location?.let {
                body.put(
                    "locationBias",
                    JSONObject().put(
                        "circle",
                        JSONObject()
                            .put("center", JSONObject().put("latitude", it.latitude).put("longitude", it.longitude))
                            .put("radius", 50_000.0),
                    ),
                )
                if (search.mode == DestinationSearchMode.NEAREST) {
                    body.put("rankPreference", "DISTANCE")
                }
            }
            return Request.Builder()
                .url(ENDPOINT)
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", FIELD_MASK)
                .post(body.toString().toRequestBody(JSON))
                .build()
        }

        fun parseResponse(body: String): DestinationResolution = runCatching {
            val place = JSONObject(body).optJSONArray("places")?.optJSONObject(0)
                ?: return DestinationResolution.NotFound
            val coordinates = place.optJSONObject("location") ?: return DestinationResolution.NotFound
            val name = place.optJSONObject("displayName")?.optString("text")
                ?.takeIf(String::isNotBlank)
                ?: place.optString("formattedAddress").takeIf(String::isNotBlank)
                ?: return DestinationResolution.NotFound
            val destination = NavigationDestination(
                name = name,
                latitude = coordinates.optDouble("latitude", Double.NaN),
                longitude = coordinates.optDouble("longitude", Double.NaN),
                address = place.optString("formattedAddress").takeIf(String::isNotBlank),
            )
            when (DestinationValidator.validate(destination)) {
                is DestinationValidation.Valid -> DestinationResolution.Found(destination)
                else -> DestinationResolution.NotFound
            }
        }.getOrElse {
            DestinationResolution.Failure(ProviderDiagnostic(message = "Invalid Places response"))
        }
    }
}

private object PlacesHttpClient {
    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
