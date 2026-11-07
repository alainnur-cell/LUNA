package com.gomobile.luna.ui

import androidx.compose.ui.graphics.Color
import com.gomobile.luna.data.Aging
import com.gomobile.luna.ui.theme.StatusColors
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val numberFormat = NumberFormat.getIntegerInstance(Locale.US)

fun Long.fmt(): String = numberFormat.format(this)
fun Int.fmt(): String = numberFormat.format(this)
fun Double.pct(): String = "${(this * 100).toInt()}%"
fun Double?.pctOrDash(): String = this?.pct() ?: "–"

fun Date?.fmtDateTime(): String =
    this?.let { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.US).format(it) } ?: "–"

/** Green at or above 100% of target, amber from 70%, red below. */
fun achievementColor(ratio: Double?): Color = when {
    ratio == null -> Color.Gray
    ratio >= 1.0 -> StatusColors.good
    ratio >= 0.7 -> StatusColors.warn
    else -> StatusColors.bad
}

fun Aging.color(): Color = when (this) {
    Aging.SELLING -> StatusColors.good
    Aging.NO_SALE_3 -> StatusColors.warn
    Aging.NO_SALE_6, Aging.NO_SALE_10 -> StatusColors.bad
}
