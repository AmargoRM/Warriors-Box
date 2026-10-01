package com.warriorsbox.app.updates

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.IntentCompat
import com.warriorsbox.app.BuildConfig
import com.warriorsbox.app.data.BackupManager
import com.warriorsbox.app.data.SettingsStore
import com.warriorsbox.app.notifications.Notifier
import com.warriorsbox.core.engine.UpdateInfo
import com.warriorsbox.core.engine.Updates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * Actualizaciones sin Play Store: consulta el último Release de GitHub, descarga el APK,
 * verifica SHA-256 y firma, hace una copia de seguridad y abre el instalador del sistema.
 */
class UpdateManager(
    private val context: Context,
    private val settings: SettingsStore,
    private val backup: BackupManager,
    private val http: OkHttpClient,
) {
    sealed interface CheckResult {
        data class Available(val info: UpdateInfo) : CheckResult
        data object UpToDate : CheckResult
        data class Error(val message: String) : CheckResult
    }

    val installedVersionCode: Int get() = BuildConfig.VERSION_CODE
    val installedVersionName: String get() = BuildConfig.VERSION_NAME

    /** Las versiones de prueba (debug) tienen otro nombre de paquete y no se actualizan solas. */
    val enabled: Boolean get() = !BuildConfig.DEBUG

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(respectSkipped: Boolean = true): CheckResult = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext CheckResult.Error("Esta es una versión de prueba: instala la versión publicada para recibir actualizaciones.")
        try {
            val latest = get("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
            val release = json.parseToJsonElement(latest).jsonObject
            val assets = (release["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val updateAsset = assets.firstOrNull { (it["name"] as? JsonPrimitive)?.contentOrNull == "update.json" }
                ?: return@withContext CheckResult.Error("El último Release no tiene update.json.")
            val url = (updateAsset["browser_download_url"] as? JsonPrimitive)?.contentOrNull
                ?: return@withContext CheckResult.Error("No se encontró la dirección de update.json.")
            val info = Updates.parse(get(url))
            settings.setLastUpdateCheck(System.currentTimeMillis())
            val skipped = if (respectSkipped) settings.current().skippedVersion else null
            if (Updates.isNewer(info, installedVersionCode, skipped)) CheckResult.Available(info) else CheckResult.UpToDate
        } catch (e: Exception) {
            CheckResult.Error("No se pudo revisar: ${e.message ?: "sin conexión"}")
        }
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("Accept", "application/vnd.github+json").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Respuesta ${response.code}")
            return response.body?.string() ?: error("Respuesta vacía")
        }
    }

    /** Descarga el APK a la caché y verifica SHA-256 y firma. */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "warriors-box-${info.versionName}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        http.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { response ->
            if (!response.isSuccessful) error("No se pudo descargar (código ${response.code})")
            val body = response.body ?: error("Descarga vacía")
            val total = body.contentLength().takeIf { it > 0 }
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buffer).also { read = it } >= 0) {
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        if (total != null) onProgress(done.toFloat() / total)
                    }
                }
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        if (!hash.equals(info.sha256.trim(), ignoreCase = true)) {
            file.delete()
            error("El archivo descargado está dañado (SHA-256 no coincide). Intenta de nuevo.")
        }
        if (!sameSignature(file)) {
            file.delete()
            error("El APK no está firmado con la misma llave que la app instalada. Por seguridad no se instalará.")
        }
        file
    }

    @Suppress("DEPRECATION")
    private fun signatures(info: PackageInfo?): Set<String> {
        if (info == null) return emptySet()
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else {
            info.signatures
        }
        return sigs.orEmpty().map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    @Suppress("DEPRECATION")
    fun sameSignature(apk: File): Boolean {
        val pm = context.packageManager
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = signatures(pm.getPackageInfo(context.packageName, flag))
        val archive = signatures(pm.getPackageArchiveInfo(apk.absolutePath, flag))
        return installed.isNotEmpty() && archive.isNotEmpty() && installed.intersect(archive).isNotEmpty()
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Copia de seguridad automática y luego instalación con el instalador del sistema. */
    suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        runCatching { backup.autoBackup() }
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("warriors-box.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(
                context, sessionId, Intent(context, InstallResultReceiver::class.java), flags,
            )
            session.commit(pending.intentSender)
        }
    }
}

/** Recibe el resultado del instalador: pide confirmación al usuario o informa el error. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }.onFailure {
                    Notifier.show(
                        context, Notifier.ID_UPDATE, Notifier.CHANNEL_UPDATES, "Actualización lista",
                        "Toca para abrir Warriors Box y terminar de instalar la nueva versión.",
                        Notifier.openAppIntent(context, Notifier.ID_UPDATE) { putExtra(Notifier.EXTRA_OPEN_UPDATE, true) },
                    )
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "código $status"
                Notifier.show(context, Notifier.ID_UPDATE, Notifier.CHANNEL_UPDATES, "No se pudo actualizar", message)
            }
        }
    }
}
