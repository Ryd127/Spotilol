package com.project.lol.ui.components

import org.json.JSONObject
import java.util.UUID

/** Display-only command/reply boundary. Authentication never crosses into native UI. */
data class NativeActionRequest(
    val kind: String,
    val uri: String = "",
    val target: String = "",
    val name: String = "",
    val saved: Boolean = true,
    val collaborative: Boolean = false,
    val id: String = UUID.randomUUID().toString()
) {
    fun json(): String = JSONObject().put("id", id).put("kind", kind).put("uri", uri)
        .put("target", target).put("name", name).put("saved", saved)
        .put("collaborative", collaborative).toString()
}

data class NativeActionReply(
    val state: String = "error", val code: String = "unavailable", val uri: String = "",
    val saved: Boolean? = null, val playlists: List<MobileBrowseItem> = emptyList(), val more: Boolean = false
) {
    companion object {
        fun parse(raw: String): NativeActionReply {
            val o = JSONObject(raw)
            val list = o.optJSONArray("playlists")
            return NativeActionReply(o.optString("state", "error"), o.optString("code", "unavailable"),
                o.optString("uri", ""), if (o.has("saved")) o.optBoolean("saved") else null,
                if (list == null) emptyList() else (0 until minOf(list.length(), 500)).mapNotNull {
                    list.optJSONObject(it)?.let(MobileBrowseItem::parse)
                }, o.optBoolean("more"))
        }
    }
}

internal fun mediaUri(path: String): String {
    val value = if (path.startsWith("spotify:")) path else {
        val m = Regex("^/(track|episode|album|artist|playlist|show)/([A-Za-z0-9]{22})$").matchEntire(path)
            ?: return ""
        "spotify:${m.groupValues[1]}:${m.groupValues[2]}"
    }
    return value.takeIf { Regex("^spotify:(track|episode|album|artist|playlist|show):[A-Za-z0-9]{22}$").matches(it) }.orEmpty()
}

internal fun NativeActionReply.errorText(): String = when {
    state == "unknown" -> "Нет подтверждения от Spotify. Проверь медиатеку перед повторной попыткой."
    code == "auth" -> "Войди в Spotify заново и повтори действие."
    code == "permission" -> "Spotify не разрешил это действие. Можно попробовать через веб-плеер."
    code == "rate" -> "Слишком много запросов. Попробуй немного позже."
    code == "busy" -> "Предыдущее действие ещё выполняется."
    code == "invalid" -> "Проверь название или выбранный объект."
    else -> "Не удалось выполнить действие. Проверь соединение."
}
