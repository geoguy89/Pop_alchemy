package com.geoguy89.refinersfire.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.geoguy89.refinersfire.ThemeId
import com.geoguy89.refinersfire.resources.Res
import com.geoguy89.refinersfire.resources.audiowide
import com.geoguy89.refinersfire.resources.cinzel
import com.geoguy89.refinersfire.resources.cinzeldecorative_bold
import com.geoguy89.refinersfire.resources.cinzeldecorative_regular
import com.geoguy89.refinersfire.resources.imfellenglish
import com.geoguy89.refinersfire.resources.pirataone
import com.geoguy89.refinersfire.resources.rajdhani_bold
import com.geoguy89.refinersfire.resources.rajdhani_medium
import com.geoguy89.refinersfire.resources.dellarespira
import com.geoguy89.refinersfire.resources.marcellus
import com.geoguy89.refinersfire.resources.uncialantiqua
import com.geoguy89.refinersfire.gfx.Palette
import com.geoguy89.refinersfire.gfx.drawBackdrop
import com.geoguy89.refinersfire.gfx.drawBrassPlate
import kotlin.math.sin

/** The game's typefaces, per theme. Loaded once by [ProvideFonts]; the serif fallback covers the first frame. */
object Fonts {
    private var families: Map<ThemeId, Pair<FontFamily, FontFamily>> = emptyMap()

    val title: FontFamily get() = families[Palette.theme]?.first ?: FontFamily.Serif
    val body: FontFamily get() = families[Palette.theme]?.second ?: FontFamily.Serif

    @Composable
    fun ProvideFonts(content: @Composable () -> Unit) {
        val font = @Composable { r: org.jetbrains.compose.resources.FontResource, w: FontWeight -> org.jetbrains.compose.resources.Font(r, w) }
        families = mapOf(
            ThemeId.MODERN to (
                FontFamily(font(Res.font.cinzeldecorative_bold, FontWeight.Bold), font(Res.font.cinzeldecorative_regular, FontWeight.Normal)) to
                    FontFamily(font(Res.font.cinzel, FontWeight.Normal), font(Res.font.cinzel, FontWeight.Bold))
                ),
            ThemeId.TEMPLE to (
                FontFamily(font(Res.font.pirataone, FontWeight.Bold), font(Res.font.pirataone, FontWeight.Normal)) to
                    FontFamily(font(Res.font.imfellenglish, FontWeight.Normal), font(Res.font.imfellenglish, FontWeight.Bold))
                ),
            ThemeId.FUTURE to (
                FontFamily(font(Res.font.audiowide, FontWeight.Bold), font(Res.font.audiowide, FontWeight.Normal)) to
                    FontFamily(font(Res.font.rajdhani_medium, FontWeight.Normal), font(Res.font.rajdhani_bold, FontWeight.Bold))
                ),
            ThemeId.GARDEN to (
                FontFamily(font(Res.font.uncialantiqua, FontWeight.Bold), font(Res.font.uncialantiqua, FontWeight.Normal)) to
                    FontFamily(font(Res.font.dellarespira, FontWeight.Normal), font(Res.font.dellarespira, FontWeight.Bold))
                ),
            ThemeId.STARLIGHT to (
                FontFamily(font(Res.font.marcellus, FontWeight.Bold), font(Res.font.marcellus, FontWeight.Normal)) to
                    FontFamily(font(Res.font.marcellus, FontWeight.Normal), font(Res.font.marcellus, FontWeight.Bold))
                ),
        )
        content()
    }
}

fun titleStyle(size: TextUnit, color: Color = Palette.goldLight) = TextStyle(
    fontFamily = Fonts.title, fontWeight = FontWeight.Bold, fontSize = size, color = color,
    shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(2f, 3f), 6f),
)

fun bodyStyle(size: TextUnit = 16.sp, color: Color = Palette.parchment, bold: Boolean = false) = TextStyle(
    fontFamily = Fonts.body, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontSize = size, color = color,
    shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(1f, 2f), 3f),
)

/** The theme's backdrop: carved wood, a temple wall or a neon grid. */
fun Modifier.woodBackground(): Modifier = drawBehind {
    if (Palette.theme == ThemeId.MODERN) drawWood() else drawBackdrop(Palette.theme)
}

fun DrawScope.drawWood() {
    // Warm oak planks: visible grain, seams and knots, lit from the middle, darkening only at the very edges.
    drawRect(Brush.verticalGradient(listOf(Color(0xFF4A301C), Color(0xFF3B2616), Color(0xFF2F1E11))))
    val plankW = (size.minDimension / 3.2f).coerceAtLeast(120f)
    val planks = (size.width / plankW).toInt() + 1
    for (pk in 0 until planks) {
        val x0 = pk * plankW
        // Each plank a slightly different tone.
        val tone = ((pk * 37) % 5) / 5f
        drawRect(Color(0xFF6A4528), Offset(x0, 0f), Size(plankW, size.height), alpha = 0.06f + 0.08f * tone)
        // Grain runs along the plank (vertically), wavy.
        val lines = 14
        for (i in 0 until lines) {
            val gx = x0 + plankW * (i + 0.5f) / lines
            val phase = pk * 2.3f + i * 1.1f
            var prev = Offset(gx, 0f)
            val steps = 28
            for (k in 1..steps) {
                val y = size.height * k / steps
                val p = Offset(gx + sin(k * 0.45f + phase) * plankW * 0.018f + sin(k * 1.3f + phase * 0.7f) * plankW * 0.006f, y)
                val light = i % 3 == 0
                drawLine(if (light) Color(0xFF7A5230) else Color(0xFF1E120A), prev, p, if (light) 1.6f else 1.2f, alpha = if (light) 0.45f else 0.35f)
                prev = p
            }
        }
        // A knot or two.
        for (kn in 0 until 2) {
            val ky = size.height * (((pk * 53 + kn * 31) % 97) / 97f)
            val kc = Offset(x0 + plankW * (0.3f + 0.4f * (((pk + kn) * 29 % 11) / 11f)), ky)
            drawOval(Color(0xFF1E120A), kc - Offset(plankW * 0.05f, plankW * 0.09f), Size(plankW * 0.1f, plankW * 0.18f), alpha = 0.35f)
            drawOval(Color(0xFF6A4528), kc - Offset(plankW * 0.028f, plankW * 0.05f), Size(plankW * 0.056f, plankW * 0.1f), alpha = 0.4f)
        }
        // Seam between planks.
        drawLine(Color.Black, Offset(x0, 0f), Offset(x0, size.height), 3f, alpha = 0.55f)
        drawLine(Color(0xFF7A5230), Offset(x0 + 2.5f, 0f), Offset(x0 + 2.5f, size.height), 1f, alpha = 0.35f)
    }
    drawRect(
        Brush.radialGradient(
            0f to Color(0x40FFB060), 0.55f to Color.Transparent, 1f to Color(0x99000000),
            center = Offset(size.width * 0.5f, size.height * 0.45f),
            radius = size.maxDimension * 0.8f,
        ),
    )
}

@Composable
fun BrassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pulse: Float = 0f,
    dark: Boolean = false,
    fontSize: TextUnit = 17.sp,
    minHeight: Dp = 46.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "press")
    Box(
        modifier
            .heightIn(min = minHeight)
            .scale(scale)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .drawBehind {
                val modern = Palette.theme == ThemeId.MODERN
                drawBrassPlate(Offset.Zero, size, size.height / 2f, rivets = false, dark = dark || pressed || (!enabled && !modern))
                if (pulse > 0f) {
                    drawRoundRect(Palette.ember, Offset.Zero, size, CornerRadius(size.height / 2f), alpha = 0.55f * pulse, style = Stroke(5f))
                    drawRoundRect(Palette.ember, Offset.Zero, size, CornerRadius(size.height / 2f), alpha = 0.2f * pulse)
                }
                if (!enabled && modern) drawRoundRect(Color.Black, Offset.Zero, size, CornerRadius(size.height / 2f), alpha = 0.45f)
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Shrink the label until it fits, so narrow panels (e.g. an unfolded Fold) never clip it.
        var fitted by remember(text, fontSize) { mutableStateOf(fontSize) }
        Text(
            text,
            style = bodyStyle(
                fitted,
                when {
                    !enabled && Palette.theme != ThemeId.MODERN -> Palette.parchment.copy(alpha = 0.4f)
                    dark || pressed -> Palette.goldLight
                    else -> Palette.ink
                },
                bold = true,
            ).copy(
                shadow = if (dark || pressed) Shadow(Color.Black, Offset(1f, 2f), 3f) else Shadow(Color.White.copy(alpha = 0.5f), Offset(0f, 1.5f), 0f),
            ),
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { if (it.didOverflowWidth && fitted.value > 8f) fitted = fitted * 0.9f },
        )
    }
}

/** Single-line text that shrinks its font until it fits the available width. */
@Composable
fun FitText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    var fitted by remember(text, style.fontSize) { mutableStateOf(style.fontSize) }
    Text(
        text, style = style.copy(fontSize = fitted), modifier = modifier, textAlign = TextAlign.Center,
        maxLines = 1, softWrap = false,
        onTextLayout = { if (it.didOverflowWidth && fitted.value > 7f) fitted = fitted * 0.9f },
    )
}

/** A modal panel in the style of the original: dark wood inset in a brass frame, over a dimmed scrim. */
@Composable
fun GamePanel(
    title: String,
    onDismiss: (() -> Unit)?,
    maxWidth: Dp = 560.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable(remember { MutableInteractionSource() }, indication = null) { onDismiss?.invoke() }
            .safeDrawingPadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .drawBehind { drawPanelFrame() }
                .padding(horizontal = 22.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = titleStyle(28.sp), textAlign = TextAlign.Center)
            content()
        }
    }
}

fun DrawScope.drawPanelFrame() {
    val r = 22.dp.toPx()
    drawBrassPlate(Offset.Zero, size, r, rivets = true)
    val inset = 7.dp.toPx()
    val innerSize = Size(size.width - inset * 2, size.height - inset * 2)
    drawRoundRect(
        Brush.verticalGradient(Palette.colors.panel),
        Offset(inset, inset), innerSize, CornerRadius(r - inset),
    )
    drawRoundRect(Color.Black, Offset(inset, inset), innerSize, CornerRadius(r - inset), alpha = 0.6f, style = Stroke(3f))
}

@Composable
fun BoxScope.CenteredColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, content = content)
}

/** A simple canvas slot that just draws; used for piece previews in help pages. */
@Composable
fun DrawBox(modifier: Modifier, onDraw: DrawScope.() -> Unit) = Canvas(modifier, onDraw)
