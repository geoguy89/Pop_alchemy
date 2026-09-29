package com.geoguy89.refinersfire.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoguy89.refinersfire.game.Achievement
import com.geoguy89.refinersfire.game.AchievementCategory
import com.geoguy89.refinersfire.game.Achievements
import com.geoguy89.refinersfire.gfx.Palette
import com.geoguy89.refinersfire.gfx.drawBrassPlate

@Composable
fun AchievementsPanel(vm: GameViewModel) {
    var category by rememberSaveable { mutableStateOf<AchievementCategory?>(null) }
    // Whatever was new is seen once the panel has been opened.
    DisposableEffect(Unit) { onDispose { vm.markAchievementsSeen() } }
    val all = Achievements.all
    val done = all.count { it.id in vm.unlocked }
    GamePanel("Achievements", vm::pop, maxWidth = 640.dp) {
        Text("$done of ${all.size} unlocked", style = bodyStyle(15.sp, Palette.goldLight, bold = true))
        ProgressBar(done.toFloat() / all.size, Modifier.fillMaxWidth().height(8.dp))
        // Category chips, three to a row.
        val cats = listOf<AchievementCategory?>(null) + AchievementCategory.entries
        cats.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                for (c in row) {
                    val label = c?.title ?: "All"
                    BrassButton(label, { vm.click(); category = c }, Modifier.weight(1f), dark = c != category, fontSize = 12.sp, minHeight = 32.dp)
                }
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
        // Newly unlocked first, then unlocked, then closest to done.
        val shown = all.filter { category == null || it.category == category }.sortedWith(
            compareByDescending<Achievement> { it.id in vm.unseenAchievements }
                .thenByDescending { it.id in vm.unlocked }
                .thenByDescending { (it.progress(vm.stats).toFloat() / it.target).coerceAtMost(1f) },
        )
        for (a in shown) AchievementRow(vm, a)
        BrassButton("Close", vm::pop, Modifier.fillMaxWidth())
    }
}

@Composable
private fun AchievementRow(vm: GameViewModel, a: Achievement) {
    val got = a.id in vm.unlocked
    val isNew = a.id in vm.unseenAchievements
    val have = a.progress(vm.stats).coerceAtMost(a.target)
    Row(
        Modifier.fillMaxWidth().drawBehind { drawRect(Color.Black, alpha = if (got) 0.12f else 0.28f) }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(26.dp)) {
            val c = center
            val r = size.minDimension / 2f
            if (got) {
                drawCircle(Palette.gold, r, c)
                drawCircle(Palette.goldLight, r * 0.55f, c)
            } else {
                drawCircle(Palette.stoneDark, r, c)
                drawCircle(Palette.stone, r, c, style = Stroke(r * 0.15f))
            }
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.name, style = bodyStyle(15.sp, if (got) Palette.goldLight else Palette.parchment.copy(alpha = 0.75f), bold = true), maxLines = 1)
                if (isNew) Text("  NEW", style = bodyStyle(11.sp, Palette.hint, bold = true))
            }
            Text(a.description, style = bodyStyle(12.sp, Palette.parchment.copy(alpha = if (got) 0.8f else 0.6f)))
            if (!got && a.target > 1) {
                ProgressBar(have.toFloat() / a.target, Modifier.fillMaxWidth().padding(top = 4.dp).height(5.dp))
                Text("$have / ${a.target}", style = bodyStyle(10.sp, Palette.parchment.copy(alpha = 0.55f)))
            }
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float, modifier: Modifier) {
    Canvas(modifier) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(Palette.stoneDark, size = size, cornerRadius = r)
        drawRoundRect(Palette.gold, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height), cornerRadius = r)
    }
}

/** The one quiet line that mentions new achievements, shown only at a pause. Tap to open the list. */
@Composable
fun AchievementNote(vm: GameViewModel) {
    val text = vm.achievementNote ?: return
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), contentAlignment = Alignment.TopEnd) {
        Row(
            Modifier.widthIn(max = 360.dp)
                .drawBehind { drawBrassPlate(Offset.Zero, size, 12.dp.toPx(), rivets = false, dark = true) }
                .clickable { vm.openAchievements() }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Canvas(Modifier.size(16.dp)) { drawCircle(Palette.gold); drawCircle(Palette.goldLight, size.minDimension * 0.28f) }
            Text(text, style = bodyStyle(13.sp, Palette.goldLight, bold = true), textAlign = TextAlign.Start)
        }
    }
}
