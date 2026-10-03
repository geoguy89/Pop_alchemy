package com.geoguy89.refinersfire.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.geoguy89.refinersfire.game.GameState
import com.geoguy89.refinersfire.gfx.Palette
import kotlin.math.sin

/**
 * Where a foldable's hinge is, if the device is half-folded. Values are in dp relative to the window.
 * [tabletopHingeY]: a horizontal fold (phone propped up like a tiny laptop).
 * [bookHingeX]: a vertical fold (held open like a book).
 */
data class Posture(val tabletopHingeY: Dp? = null, val bookHingeX: Dp? = null, val hingeThickness: Dp = 0.dp)

@Composable
fun GameScreen(vm: GameViewModel, posture: Posture) {
    val state = vm.state ?: return
    BoxWithConstraints(Modifier.fillMaxSize().woodBackground()) {
        val hingeY = posture.tabletopHingeY
        val hingeX = posture.bookHingeX
        when {
            hingeY != null && hingeY > maxHeight * 0.3f && hingeY < maxHeight * 0.7f ->
                TabletopLayout(vm, state, hingeY, posture.hingeThickness)
            hingeX != null && hingeX > maxWidth * 0.3f && hingeX < maxWidth * 0.7f ->
                WideLayout(vm, state, panelWidth = hingeX, gap = posture.hingeThickness)
            maxWidth / maxHeight >= 0.9f ->
                WideLayout(vm, state, panelWidth = (maxWidth * 0.27f).coerceIn(210.dp, 330.dp), gap = 0.dp)
            else -> TallLayout(vm, state)
        }
    }
}

private fun discardPulse(vm: GameViewModel): Float = if (vm.noMoves) 0.5f + 0.5f * sin(vm.fx.now * 5f) else 0f

/** Paid hint: shows the legal squares for the current stone, once per stone. */
@Composable
private fun HintButton(vm: GameViewModel, modifier: Modifier) {
    SmallBrass(vm.hintLabel, vm::useHint, modifier, enabled = vm.hintAvailable)
}

@Composable
private fun WideLayout(vm: GameViewModel, state: GameState, panelWidth: Dp, gap: Dp) {
    Row(Modifier.fillMaxSize().safeDrawingPadding()) {
        BoxWithConstraints(Modifier.width(panelWidth).fillMaxHeight().padding(12.dp)) {
            val short = maxHeight < 520.dp
            val panelW = maxWidth
            val panelH = maxHeight
            Column(
                Modifier.fillMaxSize().widthIn(max = 330.dp).align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (short) 6.dp else 12.dp, Alignment.CenterVertically),
            ) {
                val m = vm.match
                if (m != null) {
                    OpponentCard(vm, m, Modifier.fillMaxWidth())
                    ScorePlaque(state, Modifier.fillMaxWidth(), digitsSize = 22.sp, showRank = false)
                } else {
                    if (!short) Logo(vm, 34.sp)
                    ScorePlaque(state, Modifier.fillMaxWidth(), digitsSize = if (short) 22.sp else 30.sp, showRank = true)
                    ModeLabel(state)
                }
                val instrumentsWidth = min(panelW, if (short) (panelH - 250.dp).coerceAtLeast(120.dp) * 1.6f else 330.dp)
                Row(
                    Modifier.width(instrumentsWidth),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HandSlot(vm, state, Modifier.weight(1f), showLabel = !short)
                    Forge(vm, state, Modifier.weight(0.8f), showLabel = !short)
                }
                NextStrip(vm, Modifier.fillMaxWidth())
                BrassButton("Discard!", vm::discard, Modifier.fillMaxWidth(), pulse = discardPulse(vm))
                HintButton(vm, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallBrass("Menu", { vm.push(Overlay.Pause) }, Modifier.weight(1f))
                    SmallBrass("Options", { vm.push(Overlay.Options) }, Modifier.weight(1f))
                }
                MatchChatButton(vm, Modifier.fillMaxWidth())
            }
        }
        if (gap > 0.dp) Spacer(Modifier.width(gap))
        BoardView(vm, Modifier.weight(1f).fillMaxHeight().padding(8.dp))
    }
}

@Composable
private fun TallLayout(vm: GameViewModel, state: GameState) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val m = vm.match
            if (m != null) {
                OpponentCard(vm, m, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                ScorePlaque(state, Modifier.widthIn(min = 130.dp), digitsSize = 22.sp, showRank = false)
            } else {
                Column {
                    Logo(vm, 30.sp)
                    ModeLabel(state)
                }
                ScorePlaque(state, Modifier.widthIn(min = 150.dp), digitsSize = 24.sp)
            }
        }
        Spacer(Modifier.weight(0.6f))
        BoardView(vm, Modifier.fillMaxWidth().aspectRatio(BoardGeom.ASPECT))
        Spacer(Modifier.weight(0.2f))
        NextStrip(vm, Modifier.fillMaxWidth().padding(vertical = 4.dp))
        Spacer(Modifier.weight(0.2f))
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            val instrument = min(maxWidth * 0.34f, 170.dp)
            val buttonsW = maxWidth * 0.3f
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                HandSlot(vm, state, Modifier.width(instrument))
                Forge(vm, state, Modifier.width(instrument * 0.8f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(buttonsW)) {
                    BrassButton("Discard!", vm::discard, Modifier.fillMaxWidth(), pulse = discardPulse(vm), fontSize = 15.sp)
                    HintButton(vm, Modifier.fillMaxWidth())
                    SmallBrass("Menu", { vm.push(Overlay.Pause) }, Modifier.fillMaxWidth())
                    SmallBrass("Options", { vm.push(Overlay.Options) }, Modifier.fillMaxWidth())
                }
            }
        }
        Spacer(Modifier.weight(1f))
        MatchChatButton(vm, Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

/** Half-folded, hinge horizontal: the board sits on the upper screen, the controls on the lower one. */
@Composable
private fun TabletopLayout(vm: GameViewModel, state: GameState, hingeY: Dp, hinge: Dp) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(hingeY).safeDrawingPadding().padding(6.dp), contentAlignment = Alignment.Center) {
            BoardView(vm, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(hinge))
        Row(
            Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            val m = vm.match
            if (m != null) OpponentCard(vm, m, Modifier.width(190.dp))
            ScorePlaque(state, Modifier.widthIn(min = 150.dp), digitsSize = 24.sp, showRank = m == null)
            HandSlot(vm, state, Modifier.heightIn(max = 230.dp).fillMaxHeight().aspectRatio(0.8f))
            Forge(vm, state, Modifier.heightIn(max = 230.dp).fillMaxHeight().aspectRatio(0.65f))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(150.dp)) {
                NextStrip(vm, Modifier.fillMaxWidth())
                BrassButton("Discard!", vm::discard, Modifier.fillMaxWidth(), pulse = discardPulse(vm))
                HintButton(vm, Modifier.fillMaxWidth())
                SmallBrass("Menu", { vm.push(Overlay.Pause) }, Modifier.fillMaxWidth())
                SmallBrass("Options", { vm.push(Overlay.Options) }, Modifier.fillMaxWidth())
                MatchChatButton(vm, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ModeLabel(state: GameState) {
    Text(
        "${state.difficulty.displayName} · ${state.mode.displayName}",
        style = bodyStyle(12.sp, Palette.parchment.copy(alpha = 0.8f), bold = true),
    )
    // A challenge run: who it's against and how far along it is.
    if (state.challengeId != null) Text(
        "Challenge vs ${state.challengeRival ?: "friend"} · board ${state.boardsCleared + 1} of ${state.challengeBoards}",
        style = bodyStyle(12.sp, Palette.hint, bold = true),
    )
}
