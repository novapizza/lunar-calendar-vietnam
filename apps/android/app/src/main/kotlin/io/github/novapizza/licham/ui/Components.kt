package io.github.novapizza.licham.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.novapizza.licham.core.SolarDate
import io.github.novapizza.licham.core.weekdayIndex

// Shapes of the web's Tailwind classes: rounded-xl 12, rounded-2xl 16, --radius-card 20, rounded-[1.75rem] 28.
val R12 = RoundedCornerShape(12.dp)
val R16 = RoundedCornerShape(16.dp)
val CardShape = RoundedCornerShape(20.dp)
val HeroShape = RoundedCornerShape(28.dp)
val Pill = RoundedCornerShape(50)

val WEEKDAY_NAMES = listOf("Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật")
/** Short weekday under a date tile, as date-fns "EEEEEE" in Vietnamese ("Th 2" … "CN"). */
val WEEKDAY_SHORT: List<String> get() = io.github.novapizza.licham.core.CalendarData.calendar.weekDaysShort

fun SolarDate.weekdayName() = WEEKDAY_NAMES[weekdayIndex]
/** "Thứ Bảy, 21/11/2026" */
fun SolarDate.longLabel() = "${weekdayName()}, $day/$month/$year"

/** Letter spacing of h1–h4 on the web (-0.01em). */
val HeadingTracking = (-0.01).em

/**
 * --shadow-card: 0 1px 2px tint/5%, 0 4px 16px -4px tint/8% (tint = --shadow-tint).
 * [float] = --shadow-float: 0 8px 32px -8px tint/22% (hero cards, sheets).
 */
@Composable
fun Modifier.webShadow(shape: Shape, float: Boolean = false): Modifier {
    val tint = LocalLichAmColors.current.shadowTint
    return if (float) shadow(10.dp, shape, clip = false, ambientColor = tint.copy(alpha = 0.18f), spotColor = tint.copy(alpha = 0.32f))
    else shadow(3.dp, shape, clip = false, ambientColor = tint.copy(alpha = 0.06f), spotColor = tint.copy(alpha = 0.14f))
}

/** `.card`: surface, radius 20, shadow-card. */
@Composable
fun Card(modifier: Modifier = Modifier, padding: Dp = 16.dp, color: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalLichAmColors.current
    Column(
        modifier
            .fillMaxWidth()
            .webShadow(CardShape)
            .clip(CardShape)
            .background(color ?: c.surface)
            .padding(padding),
        content = content,
    )
}

/** `.section-label`: 11px, semibold, uppercase, tracking 0.08em, fg-3. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color? = null) {
    val c = LocalLichAmColors.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(13.dp), tint = color ?: c.fg3)
            Spacer(Modifier.width(6.dp))
        }
        Text(text.uppercase(), color = color ?: c.fg3, fontSize = 11.sp, lineHeight = 16.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em)
    }
}

/** Page title (h1): extrabold, -0.01em. */
@Composable
fun PageTitle(text: String, size: Float = 24f, modifier: Modifier = Modifier) {
    Text(
        text, color = LocalLichAmColors.current.fg, fontSize = size.sp, lineHeight = (size * 1.33f).sp,
        fontWeight = FontWeight.ExtraBold, letterSpacing = HeadingTracking, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier,
    )
}

/** Rounded square tile with an icon (w-10 h-10 rounded-2xl by default). */
@Composable
fun IconTile(icon: ImageVector, bg: Color, fg: Color, size: Dp = 40.dp, iconSize: Dp = 18.dp, shape: Shape = R16) {
    Box(Modifier.size(size).clip(shape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(iconSize), tint = fg)
    }
}

/** The web's `Row`: px-4 py-3, tile, title text-sm semibold, detail text-xs. */
@Composable
fun InfoRow(icon: ImageVector, bg: Color, fg: Color, title: String, detail: String? = null, trailing: @Composable (RowScope.() -> Unit)? = null) {
    val c = LocalLichAmColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, bg, fg)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.fg, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
            if (detail != null) Text(detail, color = c.fg2, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke(this)
    }
}

@Composable
fun Divider() = HorizontalDivider(color = LocalLichAmColors.current.line, thickness = 1.dp)

/** `.chip`: rounded-full px-3.5 py-2, text-sm semibold, surface, shadow-card; icon 15 in brand. */
@Composable
fun Chip(text: String, icon: ImageVector? = null, onClick: () -> Unit, tint: Color? = null, textColor: Color? = null) {
    val c = LocalLichAmColors.current
    Row(
        Modifier.webShadow(Pill).clip(Pill).background(c.surface).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(15.dp), tint = tint ?: c.brand)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = textColor ?: c.fg, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Small pill badge: text-[11px] bold, px-2.5 py-1. */
@Composable
fun Badge(text: String, fg: Color, bg: Color, icon: ImageVector? = null, fontSize: Float = 11f) {
    Row(Modifier.clip(Pill).background(bg).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(if (fontSize >= 12f) 13.dp else 11.dp), tint = fg)
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = fg, fontSize = fontSize.sp, lineHeight = (fontSize * 1.5f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** `.icon-btn`: 40×40 round, icon 20 in fg-2. */
@Composable
fun IconBtn(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color? = null, bg: Color = Color.Transparent, size: Dp = 40.dp, iconSize: Dp = 20.dp) {
    val c = LocalLichAmColors.current
    Box(
        Modifier.size(size).clip(Pill).background(bg).clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(iconSize), tint = tint ?: c.fg2)
    }
}

/** `.btn`: rounded-xl px-4 py-2.5 text-sm semibold, gap 6; disabled 40% opacity. */
@Composable
private fun Btn(text: String, onClick: () -> Unit, bg: Color, fg: Color, modifier: Modifier, enabled: Boolean, icon: ImageVector?, padV: Dp = 10.dp, padH: Dp = 16.dp, fontSize: Float = 14f) {
    Row(
        modifier
            .clip(R12)
            .background(if (enabled) bg else bg.copy(alpha = bg.alpha * 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = padH, vertical = padV),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (enabled) fg else fg.copy(alpha = 0.6f)
        if (icon != null) {
            Icon(icon, null, Modifier.size(16.dp), tint = color)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = color, fontSize = fontSize.sp, lineHeight = (fontSize * 1.43f).sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** `.btn-primary`; [tall] = the sheets' footer buttons (py-3). */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, tall: Boolean = false) {
    val c = LocalLichAmColors.current
    Btn(text, onClick, c.brand, c.onBrand, modifier, enabled, icon, padV = if (tall) 12.dp else 10.dp)
}

/** `.btn-soft` (small: px-3 py-1.5 text-xs as in the events sheet). */
@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = LocalLichAmColors.current
    Btn(text, onClick, c.brandSoft, c.brand, modifier, true, icon, padV = 6.dp, padH = 12.dp, fontSize = 12f)
}

/** `.btn-ghost`. */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, bg: Color? = null) {
    val c = LocalLichAmColors.current
    Btn(text, onClick, bg ?: c.surface2, c.fg, modifier, true, null)
}

/**
 * `.field` as a select: rounded-xl, surface-2, no border, px-3.5 py-2.5, semibold.
 * Phones force 16px in fields on the web (iOS zoom guard), so the value is 16sp.
 */
@Composable
fun <T> SelectField(value: T, options: List<T>, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier, bg: Color? = null) {
    val c = LocalLichAmColors.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().clip(R12).background(bg ?: c.surface2).clickable { open = true }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label(value), color = c.fg, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
            Icon(Icons.Filled.ExpandMore, null, Modifier.size(18.dp), tint = c.fg2)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 320.dp).background(c.surface)) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(label(o), color = c.fg, fontWeight = if (o == value) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { open = false; onSelect(o) },
                )
            }
        }
    }
}

/**
 * `.field` as a text box: rounded-xl, surface-2 with a transparent border that turns brand
 * (and the fill surface) on focus, px-3.5 py-2.5, 16sp on phones.
 */
@Composable
fun WebTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    singleLine: Boolean = false,
    bg: Color? = null,
) {
    val c = LocalLichAmColors.current
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        textStyle = TextStyle(color = c.fg, fontSize = 16.sp, lineHeight = 24.sp, fontFamily = BeVietnamPro),
        cursorBrush = SolidColor(c.brand),
        minLines = minLines,
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().clip(R12)
                    .background(if (focused) c.surface else (bg ?: c.surface2))
                    .border(1.dp, if (focused) c.brand else Color.Transparent, R12)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, color = c.fg3, fontSize = 16.sp, lineHeight = 24.sp)
                inner()
            }
        },
    )
}

/** Calls [onNext] / [onPrev] on a horizontal swipe (left = next), like the web app's useSwipe. */
fun Modifier.swipe(onNext: () -> Unit, onPrev: () -> Unit): Modifier = pointerInput(onNext, onPrev) {
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = {
            if (total < -60f) onNext() else if (total > 60f) onPrev()
        },
        onHorizontalDrag = { _, dx -> total += dx },
    )
}

/** Scrollable page column: px-4 pt-4 pb-10, gap 16 (the web's views on phones). */
@Composable
fun Page(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp), gap: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/** AboutLink: "Thông tin ứng dụng", text-xs fg-3, centred. */
@Composable
fun AboutLink(onClick: () -> Unit) {
    val c = LocalLichAmColors.current
    // mt-8 on the web; the page gap adds 16 of it.
    Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
        Text(
            "Thông tin ứng dụng", color = c.fg3, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.clip(Pill).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/**
 * The hero cards' decorative "moon": circles positioned like the web's absolute boxes
 * (right/top offsets in dp, may be negative), drawn behind the content so they never
 * change the card's size.
 */
fun Modifier.heroMoons(color: Color, vararg circles: Triple<Float, Float, Float>): Modifier = drawBehind {
    // Triple(right, top, size): the web's -right-N / -top-N / w-N in dp.
    circles.forEach { (right, top, diameter) ->
        val r = diameter.dp.toPx() / 2
        drawCircle(color, radius = r, center = Offset(size.width - right.dp.toPx() - r, top.dp.toPx() + r))
    }
}

/**
 * CSS `leading-none` (line box = font size): Android never lays a line out shorter than the
 * font's own height, so the box is fixed and the glyphs may overflow it, as in a browser.
 */
@Composable
fun Modifier.leadingNone(fontSize: TextUnit): Modifier {
    // sp → dp with the user's font size setting (Android 14+ scales large text non-linearly).
    val box = with(LocalDensity.current) { fontSize.toDp() }
    return height(box).wrapContentHeight(Alignment.CenterVertically, unbounded = true)
}

/**
 * A text size that grows with the user's font setting only up to [maxScale]: for text in
 * boxes whose size does not grow with it (the month grid's square day cells).
 */
@Composable
fun cappedSp(size: Float, maxScale: Float = MAX_FONT_SCALE): TextUnit {
    val scale = LocalDensity.current.fontScale
    return if (scale <= maxScale) size.sp else (size * maxScale / scale).sp
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))
@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))
