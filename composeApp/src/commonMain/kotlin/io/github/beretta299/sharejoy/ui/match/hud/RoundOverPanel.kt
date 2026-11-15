package io.github.beretta299.sharejoy.ui.match.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.beretta299.sharejoy.game.Phase
import io.github.beretta299.sharejoy.presentation.match.MatchUiState
import io.github.beretta299.sharejoy.ui.match.render.PLAYER_COLORS

@Composable
internal fun BoxScope.RoundOver(hud: MatchUiState, onNext: () -> Unit) {
    val matchOver = hud.phase == Phase.MATCH_OVER
    val accent = if (hud.roundWinner >= 0) PLAYER_COLORS[hud.roundWinner] else Gold
    // Sits over the ground, so the fallen Keeper stays in view.
    Column(
        Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
            .clip(PanelShape).background(Panel).border(2.dp, accent, PanelShape)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val title = when {
            hud.roundWinner < 0 -> "Both Keepers fell: a draw"
            matchOver -> "${NAMES[hud.roundWinner]} wins the match!"
            else -> "${NAMES[hud.roundWinner]} wins the round"
        }
        Label(title, 22, Parchment, FontWeight.Bold)
        Label("${NAMES[0]} ${hud.roundsWon[0]} : ${hud.roundsWon[1]} ${NAMES[1]}", 14, Muted)
        Label(
            if (matchOver) "New match" else "Next round", 15, Color(0xFF221810), FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Gold).clickable(onClick = onNext)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}
