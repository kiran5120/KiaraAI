package com.kiara.ai.data

import com.kiara.ai.domain.Attachment
import com.kiara.ai.domain.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Minimal Gemini REST client (generateContent + model listing). The key is sent in a header, never stored here. */
class GeminiClient {
    sealed class Result {
        data class Ok(val text: String) : Result()
        data class Err(val message: String) : Result()
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val base = "https://generativelanguage.googleapis.com/v1beta"
    private val json = "application/json; charset=utf-8".toMediaType()

    suspend fun generate(
        apiKey: String,
        model: String,
        system: String,
        history: List<ChatMessage>,
        userText: String,
        attachment: Attachment? = null,
        temperature: Float = 0.8f,
        webSearch: Boolean = false
    ): Result = withContext(Dispatchers.IO) {
        try {
            val contents = JSONArray()
            history.dropWhile { it.role != "user" }.forEach {
                contents.put(
                    JSONObject().put("role", it.role)
                        .put("parts", JSONArray().put(JSONObject().put("text", it.text)))
                )
            }
            val parts = JSONArray().put(JSONObject().put("text", userText))
            if (attachment?.base64 != null) {
                parts.put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject().put("mime_type", attachment.mimeType).put("data", attachment.base64)
                    )
                )
            }
            contents.put(JSONObject().put("role", "user").put("parts", parts))

            val body = JSONObject()
                .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                .put("contents", contents)
                .put("generationConfig", JSONObject().put("temperature", temperature.toDouble()))
            if (webSearch) body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))

            val req = Request.Builder()
                .url("$base/models/${model.trim()}:generateContent")
                .header("x-goog-api-key", apiKey)
                .post(body.toString().toRequestBody(json))
                .build()

            http.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext Result.Err(errorMessage(resp.code, raw))
                val root = JSONObject(raw)
                val cands = root.optJSONArray("candidates")
                if (cands == null || cands.length() == 0) {
                    val block = root.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                    return@withContext Result.Err(if (block.isNotEmpty()) "Request was blocked ($block)." else "Gemini returned no answer.")
                }
                val ps = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                val sb = StringBuilder()
                if (ps != null) for (i in 0 until ps.length()) sb.append(ps.getJSONObject(i).optString("text"))
                val out = sb.toString().trim()
                if (out.isEmpty()) Result.Err("Gemini returned an empty answer.") else Result.Ok(out)
            }
        } catch (e: java.io.IOException) {
            Result.Err("Network problem: ${e.message ?: "no connection"}")
        } catch (e: Exception) {
            Result.Err("Unexpected error: ${e.message}")
        }
    }

    /** Lists models that support generateContent for this key. */
    suspend fun listModels(apiKey: String): kotlin.Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$base/models?pageSize=200").header("x-goog-api-key", apiKey).get().build()
            http.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext kotlin.Result.failure(Exception(errorMessage(resp.code, raw)))
                val arr = JSONObject(raw).optJSONArray("models") ?: JSONArray()
                val names = (0 until arr.length()).map { arr.getJSONObject(it) }
                    .filter { m ->
                        val methods = m.optJSONArray("supportedGenerationMethods")
                        methods != null && (0 until methods.length()).any { methods.getString(it) == "generateContent" }
                    }
                    .map { it.getString("name").removePrefix("models/") }
                    .filter { it.startsWith("gemini") }
                kotlin.Result.success(names)
            }
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }

    private fun errorMessage(code: Int, raw: String): String {
        val msg = try { JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty() } catch (_: Exception) { "" }
        val hint = when (code) {
            400 -> "Bad request or invalid API key."
            403 -> "API key rejected or not allowed."
            404 -> "Model not found. Pick another model in Settings."
            429 -> "Rate limit or quota reached. Try again later."
            else -> "Server error."
        }
        return "Gemini error $code: $hint ${msg.take(160)}".trim()
    }
}
