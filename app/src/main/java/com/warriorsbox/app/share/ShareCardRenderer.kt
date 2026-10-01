package com.warriorsbox.app.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import com.warriorsbox.app.R
import com.warriorsbox.app.data.SessionSummary
import com.warriorsbox.core.engine.Units
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Genera la imagen de resumen para compartir en Instagram (historia o post). */
class ShareCardRenderer(private val context: Context) {

    enum class Format(val width: Int, val height: Int, val label: String) {
        STORY(1080, 1920, "Historia"),
        POST(1080, 1350, "Post"),
    }

    private val gold = Color.rgb(0xE0, 0xA9, 0x5B)
    private val titleFont: Typeface =
        ResourcesCompat.getFont(context, R.font.black_ops_one) ?: Typeface.DEFAULT_BOLD

    suspend fun render(summary: SessionSummary, format: Format, backgroundPath: String?, useLb: Boolean): Bitmap =
        withContext(Dispatchers.Default) {
            val w = format.width
            val h = format.height
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            drawBackground(c, w, h, backgroundPath)

            val pad = 72f
            var y = if (format == Format.STORY) 150f else 90f

            // Logo + nombre
            val logo = ContextCompat.getDrawable(context, R.drawable.ic_logo)?.toBitmap(220, 220)
            if (logo != null) {
                c.drawBitmap(logo, (w - logo.width) / 2f, y, null)
                y += logo.height + 20f
            }
            val brand = paint(64f, Color.WHITE, titleFont).apply { textAlign = Paint.Align.CENTER }
            c.drawText("WARRIORS BOX", w / 2f, y + 56f, brand)
            y += 80f
            c.drawRect(w / 2f - 180f, y, w / 2f + 180f, y + 4f, Paint().apply { color = gold })
            y += 60f

            val dateText = summary.date.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es")))
                .replaceFirstChar { it.uppercase() }
            val sub = paint(40f, Color.argb(230, 255, 255, 255)).apply { textAlign = Paint.Align.CENTER }
            if (summary.userName.isNotBlank()) {
                c.drawText(summary.userName, w / 2f, y, paint(54f, Color.WHITE, Typeface.DEFAULT_BOLD).apply { textAlign = Paint.Align.CENTER })
                y += 58f
            }
            c.drawText(dateText, w / 2f, y, sub)
            y += 50f
            if (summary.title.isNotBlank()) {
                c.drawText(ellipsize(summary.title, sub, w - 2 * pad), w / 2f, y, sub)
                y += 70f
            }

            // Tarjetas de métricas
            val metrics = listOf(
                "${summary.kcal}" to "kcal ≈",
                "${summary.durationMin}" to "minutos",
                Units.formatWeight(summary.volumeKg, useLb).substringBefore(' ') to (if (useLb) "lb de volumen" else "kg de volumen"),
                "${summary.streak}" to "días de racha",
            )
            val cardW = (w - 2 * pad - 3 * 24f) / 4
            metrics.forEachIndexed { i, (value, label) ->
                val left = pad + i * (cardW + 24f)
                val rect = RectF(left, y, left + cardW, y + 190f)
                c.drawRoundRect(rect, 28f, 28f, Paint().apply { color = Color.argb(170, 20, 20, 20) })
                c.drawRoundRect(rect, 28f, 28f, Paint().apply { color = gold; style = Paint.Style.STROKE; strokeWidth = 3f })
                val vp = paint(if (value.length > 5) 44f else 58f, gold, titleFont).apply { textAlign = Paint.Align.CENTER }
                c.drawText(value, rect.centerX(), rect.top + 100f, vp)
                c.drawText(label, rect.centerX(), rect.top + 150f, paint(26f, Color.WHITE).apply { textAlign = Paint.Align.CENTER })
            }
            y += 250f

            // Lista de ejercicios
            val maxLines = if (format == Format.STORY) 11 else 7
            val name = paint(38f, Color.WHITE, Typeface.DEFAULT_BOLD)
            val detail = paint(34f, Color.argb(220, 255, 255, 255))
            val lines = summary.lines.take(maxLines)
            val boxTop = y
            val boxBottom = y + lines.size * 92f + 40f
            c.drawRoundRect(RectF(pad, boxTop, w - pad, boxBottom), 32f, 32f, Paint().apply { color = Color.argb(150, 10, 10, 10) })
            y += 70f
            lines.forEach { line ->
                val label = (if (line.record) "🏆 " else "• ") + line.name
                c.drawText(ellipsize(label, name, (w - 2 * pad) * 0.58f), pad + 32f, y, name)
                val d = ellipsize(line.detail, detail, (w - 2 * pad) * 0.38f)
                c.drawText(d, w - pad - 32f - detail.measureText(d), y, detail)
                y += 92f
            }
            if (summary.lines.size > maxLines) {
                c.drawText("+${summary.lines.size - maxLines} ejercicios más", pad + 32f, y - 30f, detail)
            }
            y = boxBottom + 70f

            if (summary.records.isNotEmpty()) {
                val rp = paint(40f, gold, titleFont).apply { textAlign = Paint.Align.CENTER }
                val text = if (summary.records.size == 1) "¡Nuevo récord personal!" else "¡${summary.records.size} récords personales!"
                c.drawText(text, w / 2f, y, rp)
            }

            val footer = paint(30f, Color.argb(200, 255, 255, 255)).apply { textAlign = Paint.Align.CENTER }
            c.drawText("#WarriorsBox  ·  Calorías estimadas (MET)", w / 2f, h - 70f, footer)
            bmp
        }

    private fun drawBackground(c: Canvas, w: Int, h: Int, path: String?) {
        val photo = path?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
            ?: BitmapFactory.decodeResource(context.resources, R.drawable.fondo_predeterminado)
        if (photo != null) {
            val scale = maxOf(w.toFloat() / photo.width, h.toFloat() / photo.height)
            val sw = (w / scale).toInt()
            val sh = (h / scale).toInt()
            val sx = (photo.width - sw) / 2
            val sy = (photo.height - sh) / 2
            c.drawBitmap(photo, Rect(sx, sy, sx + sw, sy + sh), Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            c.drawColor(Color.rgb(13, 13, 13))
        }
        val veil = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.argb(150, 0, 0, 0), Color.argb(225, 0, 0, 0), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), veil)
    }

    private fun paint(size: Float, color: Int, typeface: Typeface = Typeface.DEFAULT) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        this.typeface = typeface
    }

    private fun ellipsize(text: String, paint: Paint, max: Float): String {
        if (paint.measureText(text) <= max) return text
        var t = text
        while (t.isNotEmpty() && paint.measureText("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }

    /** Guarda en caché y abre el menú de compartir del sistema. */
    suspend fun share(bitmap: Bitmap): Intent = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "warriors-box-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        Intent.createChooser(send, "Compartir entrenamiento")
    }

    /** Guarda la imagen en la galería (Pictures/WarriorsBox). */
    suspend fun saveToGallery(bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        val name = "warriors-box-${System.currentTimeMillis()}.png"
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/WarriorsBox")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
                context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                true
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "WarriorsBox").apply { mkdirs() }
                FileOutputStream(File(dir, name)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                true
            }
        }.getOrDefault(false)
    }
}
