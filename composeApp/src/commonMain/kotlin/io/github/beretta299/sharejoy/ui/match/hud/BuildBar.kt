package io.github.beretta299.sharejoy.ui.match.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import io.github.beretta299.sharejoy.game.Ammo
import io.github.beretta299.sharejoy.game.Balance
import io.github.beretta299.sharejoy.game.Track
import io.github.beretta299.sharejoy.presentation.match.MatchAction
import io.github.beretta299.sharejoy.presentation.match.MatchUiState

/** Two rows of three cards, so everything fits the width of a portrait phone without scrolling. */
@Composable
internal fun BuildBar(hud: MatchUiState, onAction: (MatchAction) -> Unit) {
    Column(Modifier.widthIn(max = 440.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Track.entries.chunked(3).forEachIndexed { row, tracks ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                tracks.forEach { track ->
                    val trackIndex = track.ordinal
                    val level = hud.levels.getOrElse(trackIndex) { 0 }
                    UpgradeCard(
                        Modifier.weight(1f),
                        title = track.title,
                        subtitle = nextPerk(track, level),
                        level = level,
                        cost = hud.costs.getOrNull(trackIndex),
                        enabled = hud.canBuy.getOrElse(trackIndex) { false },
                        onClick = { onAction(MatchAction.BuyUpgrade(track)) },
                    )
                }
                if (row == 1) {
                    UpgradeCard(Modifier.weight(1f), "Repair", "Mend ${Balance.BLOCKS_PER_REPAIR} blocks", null, Balance.REPAIR_COST, hud.canRepair) { onAction(MatchAction.Repair) }
                }
            }
        }
    }
}

internal fun nextPerk(track: Track, level: Int): String = when (track) {
    Track.POWER -> listOf("+11% speed", "+22% speed", "+33% speed", "Max power")[level]
    Track.AIM -> listOf("Longer guide", "Long guide", "Full arc", "Full arc")[level]
    Track.AMMO -> listOf(Ammo.SPLIT.title, Ammo.IRON.title, Ammo.KEG.title, "All unlocked")[level]
    Track.WALLS -> listOf("Stone hall", "Iron pillars", "Iron wall", "Fortified")[level]
    Track.COVER -> listOf("Parapet", "Second roof", "High wall", "Covered")[level]
}

@Composable
internal fun UpgradeCard(modifier: Modifier, title: String, subtitle: String, level: Int?, cost: Int?, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier
            .alpha(if (enabled || cost == null) 1f else 0.62f)
            .clip(PanelShape)
            .background(Brush.verticalGradient(if (enabled) listOf(PanelLight, Panel) else listOf(Panel, Panel)))
            .border(1.dp, if (enabled) Gold else Gold.copy(alpha = 0.3f), PanelShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(title, 13, Parchment, FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (cost != null) Coin(cost, dim = !enabled) else Label("MAX", 11, Gold, FontWeight.Bold)
        }
        Label(subtitle, 11, Muted)
        if (level != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 2.dp)) {
                repeat(MAX_LEVEL) { pip ->
                    Box(Modifier.width(20.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (pip < level) Gold else Color(0x66000000)))
                }
            }
        } else {
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** Pips shown under each upgrade card: one per level the track can reach. */
private const val MAX_LEVEL = 3
