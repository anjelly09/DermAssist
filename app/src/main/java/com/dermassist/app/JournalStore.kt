package com.dermassist.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Deliberately saves text only; neither a photo nor a URI enters persistent storage. */
class JournalStore(context: Context) {
    private val preferences = context.getSharedPreferences("local_journal", Context.MODE_PRIVATE)
    fun read(): List<Assessment> = runCatching {
        val entries = JSONArray(preferences.getString("entries", "[]"))
        (0 until entries.length()).map { index ->
            val item = entries.getJSONObject(index)
            Assessment(item.getString("id"), item.getLong("date"), Symptoms(
                item.getString("location"), item.getString("duration"), item.getString("itching"),
                item.getString("pain"), item.getString("spread"),
            ), screening = screeningFromJson(item.optJSONObject("screening")))
        }
    }.getOrDefault(emptyList())
    // Keep commit() so a failed disk write is reported rather than silently ignored.
    @android.annotation.SuppressLint("UseKtx")
    fun write(entries: List<Assessment>) {
        val json = JSONArray()
        entries.forEach { entry ->
            json.put(JSONObject().apply {
                put("id", entry.id); put("date", entry.createdAt)
                put("location", entry.symptoms.location); put("duration", entry.symptoms.duration)
                put("itching", entry.symptoms.itching); put("pain", entry.symptoms.pain)
                put("spread", entry.symptoms.spread)
                put("screening", screeningToJson(entry.screening))
            })
        }
        check(preferences.edit().putString("entries", json.toString()).commit()) { "The record could not be saved. Try again." }
    }
}


fun screeningToJson(result: ScreeningResult) = JSONObject().apply {
    put("status", result.status.name); put("condition", result.condition)
    put("reason", result.reason); put("modelId", result.modelId); put("latencyMs", result.latencyMs)
}
fun screeningFromJson(json: JSONObject?): ScreeningResult {
    if (json == null) return ScreeningResult()
    return runCatching { ScreeningResult(
        status = ScreeningStatus.valueOf(json.getString("status")),
        condition = if (json.isNull("condition")) null else json.getString("condition"),
        reason = json.getString("reason"),
        modelId = if (json.isNull("modelId")) null else json.getString("modelId"),
        latencyMs = json.optLong("latencyMs"),
    ) }.getOrDefault(ScreeningResult())
}
