package io.github.beretta299.sharejoy.ui.match.hud

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.beretta299.sharejoy.game.Phase
import io.github.beretta299.sharejoy.presentation.match.MatchAction
import io.github.beretta299.sharejoy.presentation.match.MatchUiState
import io.github.beretta299.sharejoy.ui.match.render.PLAYER_COLORS

/** [viewer] is the player this phone belongs to when two phones are paired, or null when both share one screen. */
@Composable
fun MatchHud(hud: MatchUiState, viewer: Int?, onAction: (MatchAction) -> Unit) {
    val mine = viewer == null || viewer == hud.currentPlayer
    Box(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        TopBar(hud, onAction, mine)

        AnimatedVisibility(
            visible = mine && hud.phase == Phase.PLAN && !hud.aiming,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(), exit = fadeOut(),
        ) {
            BuildBar(hud, onAction)
        }

        AnimatedVisibility(hud.phase == Phase.HANDOVER, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
            Banner(if (viewer == hud.currentPlayer) "Your turn" else "${NAMES[hud.currentPlayer]}'s turn", PLAYER_COLORS[hud.currentPlayer])
        }
        AnimatedVisibility(hud.canSplit && mine, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp), enter = fadeIn(), exit = fadeOut()) {
            Banner("Tap to split!", Gold, small = true)
        }
        if (hud.phase == Phase.ROUND_OVER || hud.phase == Phase.MATCH_OVER) {
            RoundOver(hud, onNext = { onAction(MatchAction.NextRound) })
        }
        LinkNotice(hud.link, onAction, Modifier.align(Alignment.Center))
    }
}
