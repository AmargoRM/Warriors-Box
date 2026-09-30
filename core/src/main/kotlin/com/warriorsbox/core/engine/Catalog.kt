package com.warriorsbox.core.engine

import com.warriorsbox.core.model.Exercise
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CatalogSource(val name: String, val url: String? = null, val license: String)

@Serializable
data class CatalogFile(
    val version: Int,
    val sources: List<CatalogSource> = emptyList(),
    val exercises: List<Exercise>,
)

object Catalog {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun parse(text: String): CatalogFile = json.decodeFromString(CatalogFile.serializer(), text)
}
