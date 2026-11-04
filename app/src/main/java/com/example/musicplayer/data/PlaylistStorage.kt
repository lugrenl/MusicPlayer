package com.example.musicplayer.data

import android.net.Uri
import org.json.JSONArray
import org.json.JSONException

internal object PlaylistStorage {

    private const val LEGACY_SEPARATOR = ", "

    sealed interface Decoded {
        data class Parsed(val uris: List<Uri>, val legacy: Boolean) : Decoded
        object Empty : Decoded
        object Invalid : Decoded
    }

    fun encode(uris: List<Uri>): String {
        val array = JSONArray()
        uris.forEach { array.put(it.toString()) }
        return array.toString()
    }

    fun decode(raw: String?): Decoded {
        if (raw.isNullOrBlank()) return Decoded.Empty

        val value = raw.trim()
        return if (value.startsWith("[")) {
            decodeJson(value)
        } else {
            decodeLegacy(raw)
        }
    }

    private fun decodeJson(value: String): Decoded {
        return try {
            val array = JSONArray(value)
            val uris = mutableListOf<Uri>()
            for (index in 0 until array.length()) {
                val element = array.get(index)
                if (element !is String) return Decoded.Invalid

                val uri = element.trim()
                if (uri.isNotEmpty()) uris.add(Uri.parse(uri))
            }
            Decoded.Parsed(uris.distinct(), legacy = false)
        } catch (_: JSONException) {
            Decoded.Invalid
        }
    }

    private fun decodeLegacy(value: String): Decoded {
        val uris = value.split(LEGACY_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { Uri.parse(it) }
            .distinct()

        if (uris.isEmpty()) return Decoded.Empty
        if (uris.any { it.scheme.isNullOrEmpty() }) return Decoded.Invalid
        return Decoded.Parsed(uris, legacy = true)
    }
}
