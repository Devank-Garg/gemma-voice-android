package com.example.gemmaapp.inference

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class JarvisToolSet(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val braveApiKey: String,
    private val onToolActive: (String?) -> Unit = {},
) : ToolSet {

    @Tool(description = "Search the internet for current information, news, or facts. Use this when the user asks about recent events, weather, or anything requiring up-to-date knowledge.")
    fun webSearch(
        @ToolParam(description = "The search query") query: String,
    ): Map<String, Any> {
        onToolActive("SEARCHING WEB")
        return try {
            val url = "https://api.search.brave.com/res/v1/web/search?q=${Uri.encode(query)}&count=5&text_decorations=false&search_lang=en"
            val request = Request.Builder()
                .url(url)
                .header("X-Subscription-Token", braveApiKey)
                .header("Accept", "application/json")
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return mapOf("error" to "Empty response")
            val json = JSONObject(body)

            // Brave returns web.results[] with title + description + url
            val results = json.optJSONObject("web")?.optJSONArray("results")
            if (results == null || results.length() == 0) return mapOf("error" to "No results for: $query")

            val snippets = (0 until minOf(results.length(), 3)).mapNotNull { i ->
                val r = results.optJSONObject(i) ?: return@mapNotNull null
                val title = r.optString("title", "")
                val desc  = r.optString("description", "")
                if (desc.isNotBlank()) "$title: $desc" else null
            }
            mapOf("query" to query, "results" to snippets)
        } catch (e: Exception) {
            mapOf("error" to "Search failed: ${e.message}")
        } finally {
            onToolActive(null)
        }
    }

    @Tool(description = "Get the current date and time on the device")
    fun getCurrentDateTime(): String {
        onToolActive("CHECKING TIME")
        return LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("EEEE, MMMM d yyyy, h:mm a"))
            .also { onToolActive(null) }
    }

    @Tool(description = "Get the device battery level and charging status")
    fun getBatteryLevel(): Map<String, Any> {
        onToolActive("CHECKING BATTERY")
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return mapOf(
            "level_percent" to bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            "charging" to bm.isCharging,
        ).also { onToolActive(null) }
    }

    @Tool(description = "Open an installed app on the device by its name, e.g. WhatsApp, Spotify, Chrome, Maps, Camera")
    fun openApp(
        @ToolParam(description = "App name to launch") appName: String,
    ): String {
        onToolActive("OPENING APP")
        val pm = context.packageManager
        // Query only apps with a launcher activity — same set as the app drawer
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = pm.queryIntentActivities(launcherIntent, 0)
        val match = activities.firstOrNull {
            it.loadLabel(pm).toString().contains(appName, ignoreCase = true)
        } ?: return "App '$appName' not found on this device".also { onToolActive(null) }

        val intent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            ?: return "App found but has no launch intent".also { onToolActive(null) }

        context.startActivity(intent)
        return "Opened ${match.loadLabel(pm)}".also { onToolActive(null) }
    }

    @Tool(description = "Set an alarm on the device for a specific time")
    fun setAlarm(
        @ToolParam(description = "Hour in 24-hour format (0-23)") hour: Int,
        @ToolParam(description = "Minute (0-59)") minute: Int,
        @ToolParam(description = "Alarm label or reason") label: String = "JARVIS Alarm",
    ): String {
        onToolActive("SETTING ALARM")
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Alarm set for ${"%02d:%02d".format(hour, minute)} — $label"
        } catch (e: Exception) {
            "Could not set alarm: ${e.message}"
        } finally {
            onToolActive(null)
        }
    }
}
