package com.warriorsbox.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ajustes")

data class AppSettings(
    val backgroundPath: String? = null,
    val veil: Float = 0.45f,
    val useLb: Boolean = false,
    val useFeet: Boolean = false,
    val trainerMode: Boolean = false,
    val trainerPin: String? = null,
    val skippedVersion: Int? = null,
    val lastUpdateCheck: Long = 0,
    val autoUpdateCheck: Boolean = true,
    val lastCatalogSync: Long = 0,
    val disclaimerShown: Boolean = false,
    val lastBackupAt: Long = 0,
    val lastPhrase: String? = null,
    /** Usuarios con "modo sin filtro" (recordatorios groseros). */
    val rudeUsers: Set<Long> = emptySet(),
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val background = stringPreferencesKey("fondo")
        val veil = floatPreferencesKey("velo")
        val useLb = booleanPreferencesKey("usar_lb")
        val useFeet = booleanPreferencesKey("usar_pies")
        val trainerMode = booleanPreferencesKey("modo_entrenador")
        val trainerPin = stringPreferencesKey("pin_entrenador")
        val skippedVersion = intPreferencesKey("version_omitida")
        val lastUpdateCheck = longPreferencesKey("ultima_revision_actualizacion")
        val autoUpdateCheck = booleanPreferencesKey("revisar_actualizaciones")
        val lastCatalogSync = longPreferencesKey("ultima_sincronizacion")
        val disclaimerShown = booleanPreferencesKey("aviso_mostrado")
        val lastBackupAt = longPreferencesKey("ultima_copia")
        val lastPhrase = stringPreferencesKey("ultima_frase")
        val rudeUsers = stringSetPreferencesKey("modo_sin_filtro")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            backgroundPath = p[Keys.background],
            veil = p[Keys.veil] ?: 0.45f,
            useLb = p[Keys.useLb] ?: false,
            useFeet = p[Keys.useFeet] ?: false,
            trainerMode = p[Keys.trainerMode] ?: false,
            trainerPin = p[Keys.trainerPin],
            skippedVersion = p[Keys.skippedVersion],
            lastUpdateCheck = p[Keys.lastUpdateCheck] ?: 0,
            autoUpdateCheck = p[Keys.autoUpdateCheck] ?: true,
            lastCatalogSync = p[Keys.lastCatalogSync] ?: 0,
            disclaimerShown = p[Keys.disclaimerShown] ?: false,
            lastBackupAt = p[Keys.lastBackupAt] ?: 0,
            lastPhrase = p[Keys.lastPhrase],
            rudeUsers = p[Keys.rudeUsers].orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setBackground(path: String?) = context.dataStore.edit {
        if (path == null) it.remove(Keys.background) else it[Keys.background] = path
    }

    suspend fun setVeil(value: Float) = context.dataStore.edit { it[Keys.veil] = value.coerceIn(0f, 0.8f) }
    suspend fun setUseLb(value: Boolean) = context.dataStore.edit { it[Keys.useLb] = value }
    suspend fun setUseFeet(value: Boolean) = context.dataStore.edit { it[Keys.useFeet] = value }

    suspend fun setTrainerMode(enabled: Boolean, encodedPin: String?) = context.dataStore.edit {
        it[Keys.trainerMode] = enabled
        if (encodedPin != null) it[Keys.trainerPin] = encodedPin
        if (!enabled) it.remove(Keys.trainerPin)
    }

    suspend fun setSkippedVersion(code: Int?) = context.dataStore.edit {
        if (code == null) it.remove(Keys.skippedVersion) else it[Keys.skippedVersion] = code
    }

    suspend fun setLastUpdateCheck(time: Long) = context.dataStore.edit { it[Keys.lastUpdateCheck] = time }
    suspend fun setAutoUpdateCheck(value: Boolean) = context.dataStore.edit { it[Keys.autoUpdateCheck] = value }
    suspend fun setLastCatalogSync(time: Long) = context.dataStore.edit { it[Keys.lastCatalogSync] = time }
    suspend fun setDisclaimerShown() = context.dataStore.edit { it[Keys.disclaimerShown] = true }
    suspend fun setLastBackupAt(time: Long) = context.dataStore.edit { it[Keys.lastBackupAt] = time }
    suspend fun setRude(userId: Long, enabled: Boolean) = context.dataStore.edit {
        val current = it[Keys.rudeUsers].orEmpty()
        it[Keys.rudeUsers] = if (enabled) current + userId.toString() else current - userId.toString()
    }

    suspend fun setLastPhrase(text: String) = context.dataStore.edit { it[Keys.lastPhrase] = text }

    /** Para copias de seguridad: los ajustes visibles (no el PIN). */
    suspend fun exportable(): Map<String, String> {
        val s = current()
        return buildMap {
            put("veil", s.veil.toString())
            put("useLb", s.useLb.toString())
            put("useFeet", s.useFeet.toString())
        }
    }

    suspend fun import(values: Map<String, String>) {
        values["veil"]?.toFloatOrNull()?.let { setVeil(it) }
        values["useLb"]?.toBooleanStrictOrNull()?.let { setUseLb(it) }
        values["useFeet"]?.toBooleanStrictOrNull()?.let { setUseFeet(it) }
    }
}
