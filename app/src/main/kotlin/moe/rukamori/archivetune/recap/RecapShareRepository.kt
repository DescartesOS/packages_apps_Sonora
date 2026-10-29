/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.recap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.graphics.withSave
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.utils.reportException

class RecapShareRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    internal suspend fun export(data: RecapData, chapter: RecapChapter, font: Typeface): Uri {
        val entries: List<RecapEntry> = when (chapter) {
            RecapChapter.ARTISTS -> data.artists
            RecapChapter.ALBUMS -> data.albums
            RecapChapter.TRACK -> data.songs.take(1)
            RecapChapter.MONTHS -> data.months.mapNotNull { it.artist }
            else -> data.songs
        }
        val artwork = withContext(Dispatchers.Default) {
            val images = linkedMapOf<String, Bitmap>()
            entries.forEach { entry ->
                val url = entry.artwork ?: return@forEach
                if (url in images) return@forEach
                val size = if (chapter == RecapChapter.TRACK) 720 else 360
                val result = context.imageLoader.execute(ImageRequest.Builder(context).data(url)
                    .memoryCacheKey(url).diskCacheKey(url).size(size).allowHardware(false).build())
                result.image?.toBitmap()?.let { images[url] = it }
                if (result is coil3.request.ErrorResult) reportException(result.throwable)
            }
            images
        }
        return withContext(Dispatchers.Default) {
            currentCoroutineContext().ensureActive()
            val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
            var output: File? = null
            try {
                RecapPoster(context, Canvas(bitmap), font, artwork).draw(data, chapter, entries)
                currentCoroutineContext().ensureActive()
                withContext(Dispatchers.IO) {
                    val directory = File(context.cacheDir, "recap")
                    check(directory.isDirectory || directory.mkdirs())
                    directory.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 86_400_000L }
                        ?.forEach { it.delete() }
                    val file = File(directory, "recap_${data.year}_${data.month ?: 0}_${chapter.name}_${UUID.randomUUID()}.png")
                    output = file
                    file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    currentCoroutineContext().ensureActive()
                    FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
                }
            } catch (failure: Exception) {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { output?.delete() }
                throw failure
            } finally {
                bitmap.recycle()
            }
        }
    }
}

private class RecapPoster(
    private val context: Context,
    private val canvas: Canvas,
    private val font: Typeface,
    private val artwork: Map<String, Bitmap>,
) {
    private val text = RecapText(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val foreground = RecapAppearance.Foreground
    private val secondary = RecapAppearance.Secondary

    fun draw(data: RecapData, chapter: RecapChapter, entries: List<RecapEntry>) {
        val topColor = if (chapter == RecapChapter.TIME || chapter == RecapChapter.HABITS) RecapAppearance.Surface else RecapAppearance.Blue
        paint.shader = LinearGradient(0f, 0f, 0f, 1920f, topColor, RecapAppearance.Background, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 1080f, 1920f, paint)
        val mint = RecapAppearance.Mint and 0x00FFFFFF
        paint.shader = RadialGradient(900f, 1400f, 1000f, intArrayOf(mint or 0x59000000, mint), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 1080f, 1920f, paint)
        paint.shader = null
        paint.color = 0x40000000
        canvas.drawRect(0f, 0f, 1080f, 1920f, paint)
        paragraph(text.period(data), 72f, 64f, 936, 42f, 2, color = secondary)
        when (chapter) {
            RecapChapter.INTRO, RecapChapter.END -> {
                paragraph(text.chapterTitle(chapter), 72f, 720f, 936, 92f, 4, bold = true)
            }
            RecapChapter.TIME -> {
                paragraph(context.getString(R.string.recap_time_statement), 72f, 220f, 936, 76f, 3, bold = true)
                paragraph(text.number(data.milliseconds / 60_000), 72f, 520f, 936, 156f, 2, bold = true)
                label(context.getString(R.string.year_in_music_minutes_unit), 72f, 850f, 46f, foreground)
                label(text.plays(data.plays), 72f, 940f, 38f, secondary)
                label(context.getString(R.string.recap_distinct_tracks) + " " + text.number(data.distinctTracks), 72f, 1010f, 34f, secondary)
                mosaic(data.songs, 1140f)
            }
            RecapChapter.TRACK -> entries.firstOrNull()?.let { entry ->
                paragraph(text.chapterTitle(chapter), 72f, 176f, 936, 42f, 2, color = secondary)
                heroArt(entry, 350f, 720f)
                paragraph(entry.title, 72f, 1160f, 936, 66f, 3, bold = true)
                paragraph(entry.subtitle, 72f, 1430f, 936, 38f, 2, color = secondary)
                label(text.minutes(entry.milliseconds), 72f, 1610f, 48f, foreground)
                label(text.plays(entry.plays), 72f, 1680f, 36f, secondary)
            }
            RecapChapter.SONGS, RecapChapter.ARTISTS, RecapChapter.ALBUMS -> {
                paragraph(text.chapterTitle(chapter), 72f, 176f, 936, 66f, 2, bold = true)
                heroArt(entries.firstOrNull(), 370f, 240f)
                ranked(entries, 700f, 196f)
            }
            RecapChapter.MONTHS -> {
                paragraph(text.chapterTitle(chapter), 72f, 176f, 936, 66f, 2, bold = true)
                data.months.filter { it.available && it.milliseconds > 0 }.forEachIndexed { index, month ->
                    val left = 72f + (index % 3) * 320f
                    val top = 420f + (index / 3) * 320f
                    month.artist?.let { art(it, RectF(left + 100f, top, left + 280f, top + 180f)) }
                    paragraph(text.month(month.month, short = true), left, top + 135f, 296, 68f, 1, bold = true)
                    month.artist?.let { paragraph(it.title, left, top + 220f, 296, 28f, 2) }
                    paragraph(text.minutes(month.milliseconds), left, top + 283f, 296, 24f, 1, color = secondary)
                }
            }
            RecapChapter.HABITS -> {
                paragraph(context.getString(R.string.recap_streak_statement, text.days(data.habits.longestStreak)),
                    72f, 250f, 936, 96f, 5, bold = true)
                mosaic(data.songs, 1060f)
            }
            RecapChapter.SUMMARY -> {
                mosaic(data.songs, 220f)
                paragraph(text.period(data.year, data.month), 72f, 830f, 936, if (data.month == null) 180f else 92f, 2)
                val facts = buildList {
                    data.albums.firstOrNull()?.let { add(Triple(context.getString(R.string.recap_top_album), it.title, it.subtitle)) }
                    data.songs.firstOrNull()?.let { add(Triple(context.getString(R.string.recap_top_song), it.title, it.subtitle)) }
                    add(Triple(context.getString(R.string.year_in_music_minutes_unit), text.number(data.milliseconds / 60_000), null))
                    data.artists.firstOrNull()?.let { add(Triple(context.getString(R.string.recap_top_artist), it.title, null)) }
                }
                facts.forEachIndexed { index, (label, value, subtitle) ->
                    summaryFact(label, value, subtitle, 72f + (index % 2) * 488f, 1100f + (index / 2) * 350f)
                }
            }
        }
        paint.color = secondary
        canvas.drawRect(72f, 1790f, 1008f, 1792f, paint)
        val logo = checkNotNull(context.getDrawable(R.drawable.small_icon)).mutate()
        logo.setTint(foreground)
        logo.setBounds(72, 1828, 119, 1875)
        logo.draw(canvas)
        label(context.getString(R.string.app_name), 142f, 1865f, 34f, foreground)
        paragraph(context.getString(R.string.recap_recorded_listening), 520f, 1834f, 488, 25f, 1, color = secondary)
    }

    private fun ranked(entries: List<RecapEntry>, start: Float, spacing: Float) {
        entries.take(5).forEachIndexed { index, entry ->
            val top = start + index * spacing
            label(text.number(index.toLong() + 1), 72f, top + 45f, 32f, secondary)
            art(entry, RectF(132f, top, 260f, top + 128f))
            paragraph(entry.title, 296f, top, 712, 40f, 2, bold = true)
            if (entry.subtitle.isNotBlank()) paragraph(entry.subtitle, 296f, top + 100f, 712, 27f, 1, color = secondary)
            paragraph(text.minutes(entry.milliseconds) + " · " + text.plays(entry.plays),
                296f, top + 140f, 712, 25f, 1, color = secondary)
        }
    }

    private fun mosaic(entries: List<RecapEntry>, top: Float) {
        val rows = entries.take(5).chunked(3)
        rows.forEachIndexed { rowIndex, row ->
            val width = (936f - 24f * (row.size - 1)) / row.size
            row.forEachIndexed { column, entry ->
                val left = 72f + column * (width + 24f)
                art(entry, RectF(left, top + rowIndex * 280f, left + width, top + rowIndex * 280f + 256f))
            }
        }
    }

    private fun summaryFact(label: String, value: String, subtitle: String?, left: Float, top: Float) {
        paragraph(label, left, top, 448, 32f, 2, color = secondary)
        val height = paragraph(value, left, top + 64f, 448, 52f, 3, bold = true)
        if (!subtitle.isNullOrBlank()) paragraph(subtitle, left, top + 80f + height, 448, 28f, 2, color = secondary)
    }

    private fun heroArt(entry: RecapEntry?, top: Float, size: Float) {
        if (entry != null) art(entry, RectF((1080 - size) / 2, top, (1080 + size) / 2, top + size))
    }

    private fun art(entry: RecapEntry, bounds: RectF) {
        val image = entry.artwork?.let(artwork::get)
        canvas.withSave {
            val shape = android.graphics.Path().apply {
                if (entry.kind == RecapEntryKind.ARTIST) addOval(bounds, android.graphics.Path.Direction.CW)
                else addRoundRect(bounds, 24f, 24f, android.graphics.Path.Direction.CW)
            }
            clipPath(shape)
            paint.color = RecapAppearance.Surface
            drawRect(bounds, paint)
            if (image != null) {
                val scale = maxOf(bounds.width() / image.width, bounds.height() / image.height)
                val width = image.width * scale
                val height = image.height * scale
                drawBitmap(image, null, RectF(bounds.centerX() - width / 2, bounds.centerY() - height / 2,
                    bounds.centerX() + width / 2, bounds.centerY() + height / 2), paint)
            } else {
                val icon = checkNotNull(context.getDrawable(R.drawable.small_icon)).mutate()
                icon.setTint(secondary)
                val inset = bounds.width() * 0.3f
                icon.setBounds((bounds.left + inset).toInt(), (bounds.top + inset).toInt(),
                    (bounds.right - inset).toInt(), (bounds.bottom - inset).toInt())
                icon.draw(this)
            }
        }
    }

    private fun label(value: String, x: Float, baseline: Float, size: Float, color: Int) {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = font; this.color = color }
        canvas.drawText(value, x, baseline, textPaint)
    }

    private fun paragraph(value: String, x: Float, y: Float, width: Int, size: Float, lines: Int,
                          bold: Boolean = false, color: Int = foreground): Float {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = if (bold) Typeface.create(font, Typeface.BOLD) else font
            this.color = color
        }
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setMaxLines(lines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END).build()
        canvas.withSave { translate(x, y); layout.draw(this) }
        return layout.height.toFloat()
    }
}
