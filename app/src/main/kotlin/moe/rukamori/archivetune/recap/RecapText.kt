/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.recap

import android.content.Context
import android.text.format.DateFormat
import com.google.common.collect.ImmutableList
import moe.rukamori.archivetune.R
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

internal object RecapAppearance {
    const val Background = 0xFF132D53.toInt()
    const val Foreground = 0xFFF5F5F7.toInt()
    const val Secondary = 0xFFD9E8F2.toInt()
    const val Surface = 0xFF326C9B.toInt()
    const val Cream = 0xFFF9EAB9.toInt()
    const val Mint = 0xFF9ED5B5.toInt()
    const val Sky = 0xFF52B8D3.toInt()
    const val Blue = 0xFF326AD8.toInt()
    const val Ink = 0xFF20291F.toInt()
}

internal data class RecapFact(val label: String, val value: String)

internal class RecapText(private val context: Context) {
    private val locale = context.resources.configuration.locales[0]
    private val numbers = NumberFormat.getIntegerInstance(locale)

    fun number(value: Long): String = numbers.format(value)
    fun minutes(milliseconds: Long): String = context.getString(R.string.recap_minutes_value, number(milliseconds / 60_000))
    fun plays(value: Long): String = context.resources.getQuantityString(
        R.plurals.recap_plays_value, value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), number(value),
    )
    fun days(value: Int): String = context.resources.getQuantityString(R.plurals.recap_days_value, value, number(value.toLong()))
    fun month(month: Int, short: Boolean = false): String = Month.of(month).getDisplayName(
        if (short) TextStyle.SHORT else TextStyle.FULL, locale,
    )
    fun period(year: Int, month: Int?): String = month?.let {
        LocalDate.of(year, it, 1).format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "yMMMM"), locale))
    } ?: year.toString()

    fun period(data: RecapData): String {
        val period = period(data.year, data.month)
        return if (data.month == null && data.year == data.throughDate.year) {
            context.getString(R.string.recap_period_so_far, period)
        } else period
    }

    fun chapterTitle(chapter: RecapChapter): String = context.getString(when (chapter) {
        RecapChapter.INTRO -> R.string.recap_story_intro
        RecapChapter.TIME -> R.string.year_in_music_minutes_label
        RecapChapter.TRACK -> R.string.year_in_music_top_track
        RecapChapter.SONGS -> R.string.top_songs
        RecapChapter.ARTISTS -> R.string.top_artists
        RecapChapter.ALBUMS -> R.string.albums
        RecapChapter.MONTHS -> R.string.recap_months
        RecapChapter.HABITS -> R.string.recap_habits
        RecapChapter.SUMMARY -> R.string.recap_summary
        RecapChapter.END -> R.string.recap_story_end
    })

    fun facts(data: RecapData): ImmutableList<RecapFact> = ImmutableList.copyOf(buildList {
        add(RecapFact(context.getString(R.string.recap_active_days), number(data.habits.activeDays.toLong())))
        add(RecapFact(context.getString(R.string.recap_longest_streak),
            context.resources.getQuantityString(R.plurals.recap_days_value, data.habits.longestStreak,
                number(data.habits.longestStreak.toLong()))))
        if (data.month == null) data.habits.busiestMonth?.let {
            add(RecapFact(context.getString(R.string.recap_busiest_month), month(it)))
        }
        data.habits.weekday?.let {
            add(RecapFact(context.getString(R.string.recap_weekday), DayOfWeek.of(it).getDisplayName(TextStyle.FULL, locale)))
        }
        data.habits.hour?.let {
            val pattern = DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(context)) "Hm" else "hm")
            add(RecapFact(context.getString(R.string.recap_hour), LocalTime.of(it, 0).format(DateTimeFormatter.ofPattern(pattern, locale))))
        }
    })
}
