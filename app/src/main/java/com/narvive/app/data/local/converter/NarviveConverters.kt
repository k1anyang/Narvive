package com.narvive.app.data.local.converter

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room TypeConverters for Narvive's custom types.
 * Locator JSON and character card JSON are stored as raw String.
 */
class NarviveConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringList(value: List<String>?): String? =
        value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toStringList(value: String?): List<String>? =
        value?.let { json.decodeFromString(it) }

    @TypeConverter
    fun fromStringMap(value: Map<String, String>?): String? =
        value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toStringMap(value: String?): Map<String, String>? =
        value?.let { json.decodeFromString(it) }
}
