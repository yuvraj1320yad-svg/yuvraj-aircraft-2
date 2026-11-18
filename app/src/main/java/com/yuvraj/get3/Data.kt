package com.yuvraj.get3

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

data class Platform(
    val id: String,
    val name: String,
    val url: String,
    val icon: String,
    val color: String,
    val cat: String,
    val builtin: Boolean,
    val desc: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("url", url)
        put("icon", icon)
        put("color", color)
        put("cat", cat)
        put("builtin", builtin)
        if (desc.isNotEmpty()) put("desc", desc)
    }
}

private fun icon(slug: String, hex: String) = "https://cdn.simpleicons.org/$slug/$hex"

val BUILTIN = listOf(
    Platform("youtube", "YouTube", "https://m.youtube.com", icon("youtube", "FF0000"), "#FF0000", "video", true),
    Platform("instagram", "Instagram", "https://www.instagram.com", icon("instagram", "E4405F"), "#E4405F", "social", true),
    Platform("facebook", "Facebook", "https://m.facebook.com", icon("facebook", "1877F2"), "#1877F2", "social", true)
)

val SUGGESTED = listOf(
    Platform("x", "X (Twitter)", "https://x.com", icon("x", "000000"), "#000000", "social", false, "News and conversations, live"),
    Platform("linkedin", "LinkedIn", "https://www.linkedin.com", icon("linkedin", "0A66C2"), "#0A66C2", "social", false, "Jobs, network and career"),
    Platform("reddit", "Reddit", "https://www.reddit.com", icon("reddit", "FF4500"), "#FF4500", "social", false, "Communities for every topic"),
    Platform("spotify", "Spotify", "https://open.spotify.com", icon("spotify", "1DB954"), "#1DB954", "music", false, "Music and podcasts"),
    Platform("netflix", "Netflix", "https://www.netflix.com", icon("netflix", "E50914"), "#E50914", "video", false, "Movies and series"),
    Platform("telegram", "Telegram", "https://web.telegram.org/a/", icon("telegram", "26A5E4"), "#26A5E4", "social", false, "Fast, secure messaging"),
    Platform("whatsapp", "WhatsApp Web", "https://web.whatsapp.com", icon("whatsapp", "25D366"), "#25D366", "social", false, "Chat with friends and family"),
    Platform("pinterest", "Pinterest", "https://www.pinterest.com", icon("pinterest", "E60023"), "#E60023", "social", false, "Ideas and inspiration"),
    Platform("wikipedia", "Wikipedia", "https://en.m.wikipedia.org", icon("wikipedia", "000000"), "#000000", "learn", false, "The free encyclopedia"),
    Platform("github", "GitHub", "https://github.com", icon("github", "181717"), "#181717", "dev", false, "Code and collaboration")
)

object Validate {
    private val ws = Regex("\\s+")
    private val badName = Regex("[^\\p{L}\\p{N}_ \\-.&]")
    private val schemeRe = Regex("^[a-z][a-z0-9+.\\-]*://")
    private val blocked = listOf("javascript:", "data:", "file:", "vbscript:", "intent:", "blob:")

    fun sanitizeName(raw: String?): String {
        val s = ws.replace(raw ?: "", " ").trim()
        return badName.replace(s, "").trim().take(24)
    }

    fun slugify(name: String): String =
        Regex("[^a-z0-9]+").replace(name.lowercase(), "_").trim('_').ifEmpty { "platform" }

    fun normalizeUrl(raw: String?): String? {
        var s = ws.replace(raw ?: "", "")
        if (s.isEmpty()) return null
        val low = s.lowercase()
        if (blocked.any { low.startsWith(it) }) return null
        if (!schemeRe.containsMatchIn(low)) s = "https://$s"
        val uri = Uri.parse(s)
        val scheme = uri.scheme?.lowercase()
        val host = uri.host
        if ((scheme != "http" && scheme != "https") || host == null || !host.contains(".")) return null
        return s
    }

    fun faviconFor(url: String): String =
        "https://www.google.com/s2/favicons?domain=${Uri.parse(url).host}&sz=64"
}

/** Saves custom platforms + recents (same job as ~/Bharatiy_app/data.json on PC). */
class Store(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("get3", Context.MODE_PRIVATE)
    private val custom = mutableListOf<Platform>()
    private val recents = mutableListOf<String>()

    init {
        load()
    }

    private fun load() {
        try {
            val d = JSONObject(prefs.getString("data", "{}") ?: "{}")
            val arr = d.optJSONArray("custom") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                val name = Validate.sanitizeName(c.optString("name"))
                val url = Validate.normalizeUrl(c.optString("url"))
                val id = c.optString("id")
                if (name.isNotEmpty() && url != null && id.isNotEmpty()) {
                    custom.add(
                        Platform(
                            id, name, url, c.optString("icon"),
                            c.optString("color", "#1D4ED8"), c.optString("cat", "custom"), false
                        )
                    )
                }
            }
            val r = d.optJSONArray("recents") ?: JSONArray()
            for (i in 0 until r.length()) recents.add(r.getString(i))
        } catch (e: Exception) {
            // start fresh
        }
    }

    private fun save() {
        val d = JSONObject()
        d.put("custom", JSONArray().apply { custom.forEach { put(it.toJson()) } })
        d.put("recents", JSONArray().apply { recents.forEach { put(it) } })
        prefs.edit().putString("data", d.toString()).apply()
    }

    @Synchronized
    fun all(): List<Platform> = BUILTIN + custom

    @Synchronized
    fun find(id: String): Platform? = all().firstOrNull { it.id == id }

    @Synchronized
    fun record(id: String) {
        recents.remove(id)
        recents.add(0, id)
        while (recents.size > 6) recents.removeAt(recents.size - 1)
        save()
    }

    private fun uniqueId(base: String): String {
        val used = all().map { it.id }.toSet()
        var pid = base
        var n = 2
        while (pid in used) {
            pid = "${base}_$n"
            n++
        }
        return pid
    }

    @Synchronized
    fun addCustom(name: String, url: String): Platform {
        val p = Platform(uniqueId(Validate.slugify(name)), name, url, Validate.faviconFor(url), "#1D4ED8", "custom", false)
        custom.add(p)
        record(p.id)
        return p
    }

    @Synchronized
    fun addSuggested(sid: String) {
        val s = SUGGESTED.firstOrNull { it.id == sid } ?: return
        if (find(sid) != null) return
        custom.add(Platform(s.id, s.name, s.url, s.icon, s.color, s.cat, false))
        save()
    }

    @Synchronized
    fun remove(pid: String) {
        custom.removeAll { it.id == pid }
        recents.remove(pid)
        save()
    }

    @Synchronized
    fun state(pending: String?): JSONObject {
        val plats = all()
        val ids = plats.map { it.id }.toSet()
        return JSONObject().apply {
            put("platforms", JSONArray().apply { plats.forEach { put(it.toJson()) } })
            put("recents", JSONArray().apply { recents.mapNotNull { find(it) }.forEach { put(it.toJson()) } })
            put("suggested", JSONArray().apply { SUGGESTED.filter { it.id !in ids }.forEach { put(it.toJson()) } })
            put("pending", pending ?: JSONObject.NULL)
        }
    }

    /** Small list injected into the floating bolt menu on every site. */
    @Synchronized
    fun overlayPayload(): String {
        val arr = JSONArray()
        all().forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url).put("color", it.color))
        }
        return JSONObject().put("platforms", arr).toString()
    }
}
