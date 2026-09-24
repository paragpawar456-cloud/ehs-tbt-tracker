package com.ehs.tbttracker.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ehs.tbttracker.ui.portal.Portal
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Chart colours from the validated data-viz reference palette (categorical order fixed, never cycled;
 * light and dark steps chosen separately). Status colours are reserved for done / missed.
 */
@Immutable
data class ChartColors(
    val series: List<Color>,
    val other: Color,
    val good: Color,
    val critical: Color,
    val neutral: Color,
    val grid: Color,
    val axisText: Color,
    val tooltipBg: Color,
    val tooltipText: Color,
)

private val LightChart = ChartColors(
    series = listOf(Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100), Color(0xFFE87BA4)),
    other = Color(0xFF9C9A92), good = Color(0xFF0CA30C), critical = Color(0xFFD03B3B), neutral = Color(0xFFD9D8D3),
    grid = Color(0xFFE7E6E2), axisText = Color(0xFF6B6A66), tooltipBg = Color(0xFF111827), tooltipText = Color.White,
)

private val DarkChart = ChartColors(
    series = listOf(Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500), Color(0xFFD55181)),
    other = Color(0xFF77766F), good = Color(0xFF0CA30C), critical = Color(0xFFD03B3B), neutral = Color(0xFF45443F),
    grid = Color(0xFF2E2E2B), axisText = Color(0xFFA8A7A0), tooltipBg = Color(0xFFF3F4F6), tooltipText = Color(0xFF111827),
)

/** False in screenshot tests (and could follow reduced-motion): charts render fully drawn, no grow-in. */
val LocalChartAnimations = androidx.compose.runtime.staticCompositionLocalOf { true }

object ChartTheme {
    val colors: ChartColors @Composable get() = if (isSystemInDarkTheme()) DarkChart else LightChart
}

/** Card chrome shared by every chart: title, subtitle, content. */
@Composable
fun ChartCard(title: String, subtitle: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = Portal.colors
    Column(
        modifier.fillMaxWidth()
            .background(c.surface, RoundedCornerShape(14.dp))
            .border(1.dp, c.line, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column {
            Text(title, color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = c.muted, fontSize = 12.sp)
        }
        content()
    }
}

// =================================================================== Donut (pie) chart

@Immutable
data class Slice(val label: String, val value: Int, val color: Color)

/**
 * Donut chart with a legend that carries label, value and share (colour never carries identity alone).
 * Tap a slice or a legend row to focus it; the centre then shows that slice.
 */
@Composable
fun DonutChart(
    slices: List<Slice>,
    centerValue: String,
    centerLabel: String,
    unit: String,
    modifier: Modifier = Modifier,
    tag: String = "donut",
) {
    val c = Portal.colors
    val total = slices.sumOf { it.value }.coerceAtLeast(1)
    var selected by remember(slices) { mutableIntStateOf(-1) }
    val animate = LocalChartAnimations.current
    val sweep = remember(slices) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(slices) { if (animate) { sweep.snapTo(0f); sweep.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) } }
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val donutSize = if (maxWidth < 360.dp) 132.dp else 156.dp
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(donutSize), contentAlignment = Alignment.Center) {
                Canvas(
                    Modifier.size(donutSize).testTag(tag)
                        .semantics { contentDescription = slices.joinToString { "${it.label}: ${it.value} $unit" } }
                        .pointerInput(slices) {
                            detectTapGestures { p ->
                                val cx = size.width / 2f; val cy = size.height / 2f
                                val r = hypot(p.x - cx, p.y - cy)
                                val outer = size.width / 2f; val inner = outer - with(density) { 34.dp.toPx() }
                                if (r < inner || r > outer) { selected = -1; return@detectTapGestures }
                                var deg = (atan2(p.y - cy, p.x - cx) * 180.0 / PI).toFloat() + 90f
                                if (deg < 0) deg += 360f
                                var acc = 0f
                                selected = slices.indexOfFirst { s -> acc += s.value * 360f / total; deg <= acc }.let { if (it == selected) -1 else it }
                            }
                        },
                ) {
                    val stroke = 26.dp.toPx()
                    val focusStroke = 32.dp.toPx()
                    val inset = focusStroke / 2f
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    val gapDeg = if (slices.count { it.value > 0 } > 1) (2.dp.toPx() / (arcSize.width / 2f)) * 180f / PI.toFloat() else 0f
                    if (slices.all { it.value == 0 }) {
                        drawArc(c.line, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    }
                    var start = -90f
                    slices.forEachIndexed { i, s ->
                        val full = s.value * 360f / total
                        val sw = (full * sweep.value - gapDeg).coerceAtLeast(0f)
                        if (sw > 0f) {
                            val dim = selected >= 0 && selected != i
                            drawArc(
                                color = if (dim) s.color.copy(alpha = 0.35f) else s.color,
                                startAngle = start + gapDeg / 2f,
                                sweepAngle = sw,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arcSize,
                                style = Stroke(if (i == selected) focusStroke else stroke),
                            )
                        }
                        start += full * sweep.value
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val focus = slices.getOrNull(selected)
                    Text(
                        focus?.let { "${it.value}" } ?: centerValue,
                        color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp,
                    )
                    Text(
                        focus?.let { "${pct(it.value, total)} · $unit" } ?: centerLabel,
                        color = c.muted, fontSize = 11.sp, maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                slices.forEachIndexed { i, s ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (i == selected) c.bg else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { selected = if (selected == i) -1 else i }
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(10.dp).background(s.color, RoundedCornerShape(3.dp)))
                        Spacer(Modifier.width(8.dp))
                        Text(s.label, color = c.ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        Text("${s.value}", color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("  ${pct(s.value, total)}", color = c.muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

private fun pct(v: Int, total: Int) = "${Math.round(v * 100f / total)}%"

// =================================================================== Column (bar) chart

@Immutable
data class Column1(
    /** Short x-axis label ("5"). */
    val label: String,
    /** Tooltip heading ("Sat 5 Sep"). */
    val title: String,
    val value: Int,
    /** Draw as the emphasised bar (e.g. today). */
    val emphasized: Boolean = false,
)

/**
 * Single-series column chart on one y-axis with a recessive grid. The tallest bar carries a direct
 * label; tapping any bar shows a tooltip with its exact value.
 */
@Composable
fun ColumnChart(
    columns: List<Column1>,
    color: Color,
    unit: String,
    modifier: Modifier = Modifier,
    emphasisColor: Color = Portal.colors.ink,
    height: androidx.compose.ui.unit.Dp = 190.dp,
    tag: String = "column_chart",
) {
    val cc = ChartTheme.colors
    val measurer = rememberTextMeasurer()
    var selected by remember(columns) { mutableIntStateOf(-1) }
    val animate = LocalChartAnimations.current
    val grow = remember(columns) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(columns) { if (animate) { grow.snapTo(0f); grow.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) } }
    val maxValue = columns.maxOfOrNull { it.value } ?: 0
    val top = niceCeil(maxValue)
    val peakIndex = columns.indexOfFirst { it.value == maxValue && maxValue > 0 }
    val axisStyle = TextStyle(color = cc.axisText, fontSize = 10.sp)
    val labelStyle = TextStyle(color = Portal.colors.ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    val tipTitle = TextStyle(color = cc.tooltipText, fontSize = 11.sp)
    val tipValue = TextStyle(color = cc.tooltipText, fontSize = 12.sp, fontWeight = FontWeight.Bold)

    Canvas(
        modifier.fillMaxWidth().height(height).testTag(tag)
            .semantics { contentDescription = columns.filter { it.value > 0 }.joinToString { "${it.title}: ${it.value} $unit" } }
            .pointerInput(columns) {
                detectTapGestures { p ->
                    val left = 30.dp.toPx(); val right = size.width.toFloat()
                    val slot = (right - left) / columns.size.coerceAtLeast(1)
                    val i = ((p.x - left) / slot).toInt()
                    selected = if (i in columns.indices && i != selected) i else -1
                }
            },
    ) {
        val left = 30.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val topPad = 26.dp.toPx()
        val plotH = bottom - topPad
        val slot = (size.width - left) / columns.size.coerceAtLeast(1)
        val gap = max(2.dp.toPx(), slot * 0.22f)
        val barW = (slot - gap).coerceAtLeast(1f)
        val radius = minOf(4.dp.toPx(), barW / 2f)

        // Recessive grid + y ticks (0, 1/2, max)
        listOf(0f, 0.5f, 1f).forEach { f ->
            val y = bottom - plotH * f
            drawLine(cc.grid, Offset(left, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                pathEffect = if (f == 0f) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            val t = measurer.measure("${(top * f).toInt()}", axisStyle)
            drawText(t, topLeft = Offset(left - t.size.width - 6.dp.toPx(), y - t.size.height / 2f))
        }

        val labelEvery = ceil(columns.size / 8.0).toInt().coerceAtLeast(1)
        columns.forEachIndexed { i, col ->
            val x = left + slot * i + gap / 2f
            val h = if (top == 0) 0f else plotH * col.value / top * grow.value
            val dim = selected >= 0 && selected != i
            if (col.value > 0) {
                val base = if (col.emphasized) emphasisColor else color
                val path = Path().apply {
                    addRoundRect(RoundRect(x, bottom - h, x + barW, bottom,
                        topLeftCornerRadius = CornerRadius(radius), topRightCornerRadius = CornerRadius(radius)))
                }
                drawPath(path, if (dim) base.copy(alpha = 0.35f) else base)
            }
            if (i % labelEvery == 0 || i == selected || col.emphasized) {
                val t = measurer.measure(col.label, if (col.emphasized) labelStyle else axisStyle)
                drawText(t, topLeft = Offset(x + barW / 2f - t.size.width / 2f, bottom + 3.dp.toPx()))
            }
            if (i == peakIndex && selected < 0) {
                val t = measurer.measure("${col.value}", labelStyle)
                drawText(t, topLeft = Offset(x + barW / 2f - t.size.width / 2f, bottom - h - t.size.height - 2.dp.toPx()))
            }
        }

        // Tooltip for the tapped bar
        columns.getOrNull(selected)?.let { col ->
            val x = left + slot * selected + slot / 2f
            val h = if (top == 0) 0f else plotH * col.value / top
            val t1 = measurer.measure(col.title, tipTitle)
            val t2 = measurer.measure("${col.value} $unit", tipValue)
            val w = max(t1.size.width, t2.size.width) + 16.dp.toPx()
            val th = t1.size.height + t2.size.height + 10.dp.toPx()
            val tx = (x - w / 2f).coerceIn(0f, size.width - w)
            val ty = (bottom - h - th - 6.dp.toPx()).coerceAtLeast(0f)
            drawRoundRect(cc.tooltipBg, Offset(tx, ty), Size(w, th), CornerRadius(6.dp.toPx()))
            drawText(t1, topLeft = Offset(tx + 8.dp.toPx(), ty + 5.dp.toPx()))
            drawText(t2, topLeft = Offset(tx + 8.dp.toPx(), ty + 5.dp.toPx() + t1.size.height))
        }
    }
}

/** Rounds up to 1, 2, 2.5 or 5 x 10^n so the grid shows clean numbers. */
fun niceCeil(v: Int): Int {
    if (v <= 0) return 0
    if (v <= 4) return 4
    val mag = 10.0.pow(ceil(log10(v.toDouble())) - 1)
    val steps = listOf(1.0, 2.0, 2.5, 5.0, 10.0)
    val n = steps.first { it * mag >= v }
    val r = (n * mag).toInt()
    return if (r % 2 == 0) r else r + 1
}

// =================================================================== Ranked horizontal bars

@Immutable
data class RankItem(val label: String, val value: Int, val caption: String? = null)

/** Ranked horizontal bars (one hue, magnitude only), value printed at the end of each row. */
@Composable
fun RankedBars(items: List<RankItem>, color: Color, modifier: Modifier = Modifier, onClick: ((String) -> Unit)? = null) {
    val c = Portal.colors
    val max = (items.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)
    val animate = LocalChartAnimations.current
    val grow = remember(items) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(items) { if (animate) { grow.snapTo(0f); grow.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        items.forEachIndexed { i, it ->
            Column(
                Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick(it.label) } else Modifier),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${i + 1}. ${it.label}", color = c.ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${it.value}", color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (it.caption != null) Text(" ${it.caption}", color = c.muted, fontSize = 11.sp)
                }
                Box(Modifier.fillMaxWidth().height(10.dp).background(c.line.copy(alpha = 0.6f), RoundedCornerShape(5.dp))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((it.value.toFloat() / max * grow.value).coerceIn(0.015f, 1f))
                        .background(color, RoundedCornerShape(5.dp)))
                }
            }
        }
    }
}
