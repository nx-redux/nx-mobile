package com.nxredux.mobile.scraper

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * The lenient JSON reads the art sources' parsers share: an unreadable body is null, never a throw, and
 * a field of the wrong kind reads as absent. ScreenScraper's ([ScraperResponse]) trims a number's text first and keeps
 * its own `int`.
 */
internal object LenientJson {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** [body] as a JSON object; null when absent, unreadable or not an object. */
    fun parseObject(body: String?): JsonObject? = body?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }

    /** A number, or a string holding one. */
    fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.let { it.intOrNull ?: it.content.toIntOrNull() }

    /** A string; JSON null and numbers are absent. */
    fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** True only for a `true`; anything else (absent included) is false. */
    fun JsonElement?.bool(): Boolean = (this as? JsonPrimitive)?.booleanOrNull == true
}
