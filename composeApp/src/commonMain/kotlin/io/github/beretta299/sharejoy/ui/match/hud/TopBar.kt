package io.github.beretta299.sharejoy.ui.match.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.beretta299.sharejoy.game.Balance
import io.github.beretta299.sharejoy.game.Phase
import io.github.beretta299.sharejoy.presentation.match.MatchAction
import io.github.beretta299.sharejoy.presentation.match.MatchUiState
import io.github.beretta299.sharejoy.ui.match.render.PLAYER_COLORS

/** Built for a narrow portrait phone: badges on one line, then the hint and the ammo picker stacked below. */
@Composable
internal fun BoxScope.TopBar(hud: MatchUiState, onAction: (MatchAction) -> Unit, mine: Boolean) {
    Column(
        Modifier.fillMaxWidth().align(Alignment.TopCenter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PlayerBadge(0, hud)
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.clip(PanelShape).background(Panel).border(1.dp, Gold.copy(alpha = 0.5f), PanelShape)
                    .clickable { onAction(MatchAction.Restart) }.padding(horizontal = 10.dp, vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Label("R${hud.round} · Turn ${hud.turn}", 12)
                Label("Restart", 10, Muted)
            }
            Spacer(Modifier.weight(1f))
            PlayerBadge(1, hud)
        }
        if (hud.suddenDeath) {
            Label(
                "SUDDEN DEATH ×2", 12, Color(0xFFFF7A5C), FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
        val hint = when (hud.phase) {
            Phase.PLAN, Phase.BUILDING -> when {
                !mine -> "${NAMES[hud.current]} is taking aim…"
                hud.phase == Phase.BUILDING -> "Building…"
                hud.aiming -> "Release to fire"
                else -> "${NAMES[hud.current]}: build, then pull the slingshot back"
            }
            else -> ""
        }
        if (hint.isNotEmpty()) {
            Label(hint, 12, Parchment, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 3.dp))
        }
        if (mine && hud.phase == Phase.PLAN && !hud.aiming && hud.ammo.size > 1) AmmoPicker(hud, onAction)
    }
}

@Composable
internal fun PlayerBadge(index: Int, hud: MatchUiState) {
    val active = hud.current == index && hud.phase != Phase.ROUND_OVER && hud.phase != Phase.MATCH_OVER
    val color = PLAYER_COLORS[index]
    Row(
        Modifier
            .clip(PanelShape)
            .background(Brush.horizontalGradient(if (index == 0) listOf(color.copy(alpha = 0.85f), Panel) else listOf(Panel, color.copy(alpha = 0.85f))))
            .border(if (active) 2.dp else 1.dp, if (active) Gold else Gold.copy(alpha = 0.35f), PanelShape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Label(NAMES[index], 14, Parchment, FontWeight.Bold)
        // Rounds won.
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(Balance.ROUNDS_TO_WIN) { i ->
                Box(
                    Modifier.size(9.dp).clip(CircleShape)
                        .background(if (i < hud.roundsWon[index]) Gold else Color(0x55000000))
                        .border(1.dp, Gold.copy(alpha = 0.7f), CircleShape),
                )
            }
        }
        Coin(hud.points[index])
    }
}

@Composable
internal fun AmmoPicker(hud: MatchUiState, onAction: (MatchAction) -> Unit) {
    Row(
        Modifier.clip(PanelShape).background(Panel).border(1.dp, Gold.copy(alpha = 0.5f), PanelShape)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        hud.ammo.forEach { ammo ->
            val selected = ammo == hud.selectedAmmo
            Label(
                ammo.title, 12, if (selected) Color(0xFF221810) else Parchment, FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(7.dp))
                    .background(if (selected) Gold else PanelLight)
                    .clickable { onAction(MatchAction.SelectAmmo(ammo)) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
    }
}
