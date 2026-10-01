package com.warriorsbox.core.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Información de una versión publicada (archivo update.json adjunto a cada Release de GitHub). */
@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val notas: String = "",
    val obligatoria: Boolean = false,
)

object Updates {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): UpdateInfo = json.decodeFromString(UpdateInfo.serializer(), text)

    /** "v1.2.3" o "1.2.3" → 10203. Debe coincidir con el cálculo del workflow de publicación. */
    fun versionCodeOf(versionName: String): Int {
        val parts = versionName.trim().removePrefix("v").removePrefix("V").split('.', '-', '+')
            .take(3).map { it.toIntOrNull() ?: 0 }
        val (major, minor, patch) = (parts + listOf(0, 0, 0)).take(3)
        require(minor in 0..99 && patch in 0..99) { "minor y patch deben estar entre 0 y 99" }
        return major * 10000 + minor * 100 + patch
    }

    fun isNewer(remote: UpdateInfo, installedVersionCode: Int, skippedVersionCode: Int? = null): Boolean {
        if (remote.versionCode <= installedVersionCode) return false
        if (!remote.obligatoria && skippedVersionCode != null && remote.versionCode == skippedVersionCode) return false
        return true
    }
}
