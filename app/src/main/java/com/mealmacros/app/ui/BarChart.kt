package com.mealmacros.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** A round axis maximum at or above [value] (1, 2, 2.5 or 5 × a power of ten). */
fun niceCeiling(value: Double): Double {
    if (value <= 0.0) return 1.0
    val magnitude = 10.0.pow(floor(log10(value)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it * magnitude >= value }
    return step * magnitude
}

/**
 * A single-series bar chart: one bar per bucket, one y-axis, recessive gridlines, an
 * optional dashed [reference] line (e.g. the daily value) and tap-to-select a bar.
 *
 * Labels are drawn for every [labelEvery]th bar so they don't collide.
 */
@Composable
fun BarChart(
    values: List<Double>,
    labels: List<String>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    reference: Double? = null,
    referenceLabel: String = "",
    labelEvery: Int = 1,
    height: Dp = 180.dp,
) {
    val colors = MaterialTheme.colorScheme
    val barColor = colors.primary
    val dimmedBar = colors.primary.copy(alpha = 0.45f)
    val gridColor = colors.outlineVariant
    val textColor = colors.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = textColor)
    val axisMax = niceCeiling(max(values.maxOrNull() ?: 0.0, reference ?: 0.0) * 1.05)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(values.size) {
                detectTapGestures { pos ->
                    val left = 44.dp.toPx()
                    val slot = (size.width - left) / values.size
                    val i = ((pos.x - left) / slot).toInt()
                    onSelect(if (pos.x >= left && i in values.indices) i else null)
                }
            }
    ) {
        val left = 44.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        val top = 8.dp.toPx()
        val plotHeight = bottom - top
        fun y(v: Double) = bottom - (v / axisMax * plotHeight).toFloat()

        // Gridlines at 0, half and max, with value labels on the left.
        for (f in listOf(0.0, 0.5, 1.0)) {
            val v = axisMax * f
            val gy = y(v)
            drawLine(gridColor, Offset(left, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
            val text = measurer.measure(AnnotatedString(formatAxis(v)), labelStyle)
            drawText(text, topLeft = Offset(left - text.size.width - 6.dp.toPx(), gy - text.size.height / 2f))
        }

        val slot = (size.width - left) / values.size
        val gap = max(2.dp.toPx(), slot * 0.25f)
        val barWidth = max(1f, slot - gap)
        val radius = minOf(4.dp.toPx(), barWidth / 2f)
        // Highlight behind the selected bar.
        if (selected != null && selected in values.indices) {
            val x = left + selected * slot
            drawRect(gridColor.copy(alpha = 0.35f), topLeft = Offset(x, top), size = Size(slot, plotHeight))
        }
        values.forEachIndexed { i, v ->
            val x = left + i * slot + gap / 2f
            if (v > 0) {
                val by = minOf(y(v), bottom - 1f)
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = x, top = by, right = x + barWidth, bottom = bottom,
                            topLeftCornerRadius = CornerRadius(radius), topRightCornerRadius = CornerRadius(radius),
                        )
                    )
                }
                drawPath(path, if (selected == null || selected == i) barColor else dimmedBar)
            }
            if (i % labelEvery == 0) {
                val text = measurer.measure(AnnotatedString(labels[i]), labelStyle)
                drawText(
                    text,
                    topLeft = Offset(x + barWidth / 2f - text.size.width / 2f, bottom + 4.dp.toPx()),
                )
            }
        }

        if (reference != null && reference > 0) {
            val ry = y(reference)
            drawLine(
                textColor, Offset(left, ry), Offset(size.width, ry), strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
            val text = measurer.measure(AnnotatedString(referenceLabel), labelStyle)
            drawText(text, topLeft = Offset(size.width - text.size.width, ry - text.size.height - 2.dp.toPx()))
        }
    }
}

private fun formatAxis(v: Double): String = when {
    v >= 10_000 -> "${(v / 1000).toInt()}k"
    v >= 100 -> v.toInt().toString()
    v == ceil(v) -> v.toInt().toString()
    else -> "%.1f".format(v)
}
