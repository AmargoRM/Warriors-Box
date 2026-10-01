package com.warriorsbox.app.coach

import com.warriorsbox.core.coach.CoachProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Buzón donde un celular deja mensajes y el otro los recoge. */
interface Relay {
    suspend fun publish(channel: String, text: String)
    suspend fun poll(channel: String): List<CoachProtocol.RelayMessage>
    suspend fun download(url: String): String
}

/**
 * Buzón público y gratuito ntfy.sh (sin cuenta). Guarda los mensajes 12 horas y los adjuntos 3 horas;
 * por eso el coach reenvía el plan hasta que el alumno confirma que le llegó.
 * Todo lo que se publica va cifrado: ntfy.sh nunca ve el contenido.
 */
class NtfyRelay(private val http: OkHttpClient, private val baseUrl: String = "https://ntfy.sh") : Relay {

    private val plain = "text/plain; charset=utf-8".toMediaType()

    override suspend fun publish(channel: String, text: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/$channel").put(text.toRequestBody(plain))
        // Los mensajes grandes (planes con fotos) van como archivo adjunto.
        if (text.length > CoachProtocol.MAX_INLINE) request.header("Filename", "wb.txt")
        http.newCall(request.build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("El buzón respondió ${r.code}")
        }
    }

    override suspend fun poll(channel: String): List<CoachProtocol.RelayMessage> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/$channel/json?poll=1&since=all").get().build()
        http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("El buzón respondió ${r.code}")
            CoachProtocol.parseNtfy(r.body?.string().orEmpty())
        }
    }

    override suspend fun download(url: String): String = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Adjunto no disponible (${r.code})")
            r.body?.string().orEmpty()
        }
    }
}
