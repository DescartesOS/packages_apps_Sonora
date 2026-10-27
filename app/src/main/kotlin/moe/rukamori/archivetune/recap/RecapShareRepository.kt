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
import android.graphics.Paint
import android.graphics.RectF
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.utils.reportException
import java.io.File
import java.util.UUID
import javax.inject.Inject

class RecapShareRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    internal suspend fun export(data: RecapData, chapter: RecapChapter, font: Typeface): Uri {
        val entries: List<RecapEntry> = when (chapter) {
            RecapChapter.ARTISTS -> data.artists
            RecapChapter.ALBUMS -> data.albums
            RecapChapter.TIME, RecapChapter.TRACK -> data.songs.take(1)
            RecapChapter.SONGS -> data.songs
            RecapChapter.SUMMARY -> data.songs.take(3)
            RecapChapter.MONTHS, RecapChapter.HABITS -> emptyList()
        }
        val artwork = withContext(Dispatchers.Default) {
            val images = linkedMapOf<String, Bitmap>()
            entries.forEachIndexed { index, entry ->
                val url = entry.artwork ?: return@forEachIndexed
                if (url in images) return@forEachIndexed
                val size = when {
                    chapter == RecapChapter.TRACK -> 720
                    chapter == RecapChapter.TIME -> 500
                    chapter == RecapChapter.SUMMARY && index == 0 -> 380
                    chapter == RecapChapter.SUMMARY -> 84
                    else -> 152
                }
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
        canvas.drawColor(RecapAppearance.Background)
        paragraph(text.period(data), 72f, 64f, 936, 42f, 2, color = secondary)
        paragraph(text.chapterTitle(chapter), 72f, 162f, 936, 66f, 2, bold = true)
        when (chapter) {
            RecapChapter.TIME -> {
                heroArt(data.songs.firstOrNull(), 344f, 500f)
                paragraph(text.number(data.milliseconds / 60_000), 72f, 960f, 936, 142f, 2, bold = true)
                label(context.getString(R.string.year_in_music_minutes_unit), 72f, 1280f, 42f, secondary)
                label(text.plays(data.plays), 72f, 1400f, 40f, foreground)
                label(context.getString(R.string.recap_distinct_tracks), 72f, 1500f, 36f, secondary)
                label(text.number(data.distinctTracks), 72f, 1555f, 56f, foreground)
            }
            RecapChapter.TRACK -> entries.firstOrNull()?.let { entry ->
                heroArt(entry, 360f, 720f)
                paragraph(entry.title, 72f, 1130f, 936, 66f, 3, bold = true)
                paragraph(entry.subtitle, 72f, 1380f, 936, 38f, 2, color = secondary)
                label(text.minutes(entry.milliseconds), 72f, 1550f, 48f, foreground)
                label(text.plays(entry.plays), 72f, 1620f, 36f, secondary)
            }
            RecapChapter.SONGS, RecapChapter.ARTISTS, RecapChapter.ALBUMS -> ranked(entries, 430f, 238f)
            RecapChapter.MONTHS -> {
                val peak = data.months.maxOf { it.milliseconds }.coerceAtLeast(1)
                data.months.forEachIndexed { index, month ->
                    val top = 380f + index * 106f
                    label(text.month(month.month, short = true), 72f, top, 32f, secondary)
                    if (month.available) {
                        paint.color = RecapAppearance.Surface
                        canvas.drawRoundRect(RectF(220f, top - 25, 740f, top + 4), 12f, 12f, paint)
                        paint.color = foreground
                        val width = 520f * month.milliseconds / peak
                        if (width > 0) canvas.drawRoundRect(RectF(220f, top - 25, 220f + width, top + 4), 12f, 12f, paint)
                        paragraph(text.minutes(month.milliseconds), 770f, top - 34, 238, 28f, 1)
                    }
                }
            }
            RecapChapter.HABITS -> text.facts(data).forEachIndexed { index, fact ->
                val top = 390f + index * 252f
                label(fact.label, 72f, top, 34f, secondary)
                paragraph(fact.value, 72f, top + 28, 936, 70f, 2, bold = true)
            }
            RecapChapter.SUMMARY -> {
                val song = data.songs.firstOrNull()
                heroArt(song, 326f, 320f)
                paragraph(text.number(data.milliseconds / 60_000), 72f, 710f, 936, 112f, 2, bold = true)
                label(context.getString(R.string.year_in_music_minutes_unit), 72f, 966f, 36f, secondary)
                label(context.getString(R.string.top_songs), 72f, 1050f, 32f, secondary)
                ranked(data.songs.take(3), 1090f, 108f, compact = true)
                data.artists.firstOrNull()?.let {
                    label(context.getString(R.string.top_artists), 72f, 1470f, 30f, secondary)
                    paragraph(it.title, 72f, 1494f, 936, 40f, 2, bold = true)
                }
                data.albums.firstOrNull()?.let {
                    label(context.getString(R.string.albums), 72f, 1630f, 30f, secondary)
                    paragraph(it.title, 72f, 1654f, 936, 40f, 2, bold = true)
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

    private fun ranked(entries: List<RecapEntry>, start: Float, spacing: Float, compact: Boolean = false) {
        entries.take(5).forEachIndexed { index, entry ->
            val top = start + index * spacing
            label(text.number(index.toLong() + 1), 72f, top + 50f, 32f, secondary)
            art(entry, RectF(132f, top, if (compact) 216f else 284f, top + if (compact) 84f else 152f))
            val left = if (compact) 242f else 320f
            paragraph(entry.title, left, top, (1008 - left).toInt(), if (compact) 30f else 43f, 2, bold = true)
            if (!compact) paragraph(entry.subtitle, left, top + 105, (1008 - left).toInt(), 29f, 1, color = secondary)
            paragraph(text.minutes(entry.milliseconds), left, top + if (compact) 67f else 158f,
                (1008 - left).toInt(), 27f, 1, color = secondary)
        }
    }

    private fun heroArt(entry: RecapEntry?, top: Float, size: Float) {
        if (entry != null) art(entry, RectF((1080 - size) / 2, top, (1080 + size) / 2, top + size))
    }

    private fun art(entry: RecapEntry, bounds: RectF) {
        val image = entry.artwork?.let(artwork::get)
        canvas.withSave {
            val shape = android.graphics.Path().apply {
                addRoundRect(bounds, 24f, 24f, android.graphics.Path.Direction.CW)
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
                          bold: Boolean = false, color: Int = foreground) {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = if (bold) Typeface.create(font, Typeface.BOLD) else font
            this.color = color
        }
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setMaxLines(lines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END).build()
        canvas.withSave { translate(x, y); layout.draw(this) }
    }
}
