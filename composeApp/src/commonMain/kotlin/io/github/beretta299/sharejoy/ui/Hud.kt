package io.github.beretta299.sharejoy.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.beretta299.sharejoy.GameSession
import io.github.beretta299.sharejoy.HudState
import io.github.beretta299.sharejoy.game.Ammo
import io.github.beretta299.sharejoy.game.Balance
import io.github.beretta299.sharejoy.game.Phase
import io.github.beretta299.sharejoy.game.Track
import io.github.beretta299.sharejoy.render.PLAYER_COLORS

private val Panel = Color(0xE6221810)
private val PanelLight = Color(0xE63A2A1B)
private val Gold = Color(0xFFD8B25A)
private val Parchment = Color(0xFFF3E6C8)
private val Muted = Color(0xFFB9A888)
private val PanelShape = RoundedCornerShape(10.dp)
private val NAMES = listOf("Red", "Blue")

@Composable
fun Hud(session: GameSession) {
    val hud = session.hud
    Box(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        TopBar(hud, session)

        AnimatedVisibility(
            visible = hud.phase == Phase.PLAN && !hud.aiming,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(), exit = fadeOut(),
        ) {
            BuildBar(hud, session)
        }

        AnimatedVisibility(hud.phase == Phase.HANDOVER, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
            Banner("${NAMES[hud.current]}'s turn", PLAYER_COLORS[hud.current])
        }
        AnimatedVisibility(hud.canSplit, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp), enter = fadeIn(), exit = fadeOut()) {
            Banner("Tap to split!", Gold, small = true)
        }
        if (hud.phase == Phase.ROUND_OVER || hud.phase == Phase.MATCH_OVER) {
            RoundOver(hud, onNext = session::nextRound)
        }
    }
}

@Composable
private fun AmmoPicker(hud: HudState, session: GameSession) {
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
                    .clickable { session.selectAmmo(ammo) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
    }
}

/** Built for a narrow portrait phone: badges on one line, then the hint and the ammo picker stacked below. */
@Composable
private fun BoxScope.TopBar(hud: HudState, session: GameSession) {
    val onRestart = session::restart
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
                    .clickable(onClick = onRestart).padding(horizontal = 10.dp, vertical = 3.dp),
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
            Phase.PLAN -> if (hud.aiming) "Release to fire" else "${NAMES[hud.current]}: build, then pull the slingshot back"
            Phase.BUILDING -> "Building…"
            else -> ""
        }
        if (hint.isNotEmpty()) {
            Label(hint, 12, Parchment, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 3.dp))
        }
        if (hud.phase == Phase.PLAN && !hud.aiming && hud.ammo.size > 1) AmmoPicker(hud, session)
    }
}

@Composable
private fun PlayerBadge(index: Int, hud: HudState) {
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
private fun Coin(amount: Int, dim: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(if (dim) 0.45f else 1f)) {
        Box(
            Modifier.size(13.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFFFFE9A6), Color(0xFFC9972B))))
                .border(1.dp, Color(0xFF7A5A14), CircleShape),
        )
        Spacer(Modifier.width(4.dp))
        Label("$amount", 14, Parchment, FontWeight.Bold)
    }
}

/** Two rows of three cards, so everything fits the width of a portrait phone without scrolling. */
@Composable
private fun BuildBar(hud: HudState, session: GameSession) {
    Column(Modifier.widthIn(max = 440.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Track.entries.chunked(3).forEachIndexed { row, tracks ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                tracks.forEach { track ->
                    val i = track.ordinal
                    val level = hud.levels.getOrElse(i) { 0 }
                    UpgradeCard(
                        Modifier.weight(1f),
                        title = track.title,
                        subtitle = nextPerk(track, level),
                        level = level,
                        cost = hud.costs.getOrNull(i),
                        enabled = hud.canBuy.getOrElse(i) { false },
                        onClick = { session.buy(track) },
                    )
                }
                if (row == 1) {
                    UpgradeCard(Modifier.weight(1f), "Repair", "Mend ${Balance.REPAIR_BLOCKS} blocks", null, Balance.REPAIR_COST, hud.canRepair) { session.repair() }
                }
            }
        }
    }
}

private fun nextPerk(track: Track, level: Int): String = when (track) {
    Track.POWER -> listOf("+11% speed", "+22% speed", "+33% speed", "Max power")[level]
    Track.AIM -> listOf("Longer guide", "Long guide", "Full arc", "Full arc")[level]
    Track.AMMO -> listOf(Ammo.SPLIT.title, Ammo.IRON.title, Ammo.KEG.title, "All unlocked")[level]
    Track.MATERIALS -> listOf("Stone hall", "Iron pillars", "Iron wall", "Fortified")[level]
    Track.COVER -> listOf("Parapet", "Second roof", "High wall", "Covered")[level]
}

@Composable
private fun UpgradeCard(modifier: Modifier, title: String, subtitle: String, level: Int?, cost: Int?, enabled: Boolean, onClick: () -> Unit) {
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
                repeat(3) { i ->
                    Box(Modifier.width(20.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i < level) Gold else Color(0x66000000)))
                }
            }
        } else {
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun Banner(text: String, accent: Color, small: Boolean = false) {
    Label(
        text, if (small) 16 else 26, Parchment, FontWeight.Bold,
        modifier = Modifier.clip(PanelShape).background(Panel).border(2.dp, accent, PanelShape)
            .padding(horizontal = if (small) 16.dp else 28.dp, vertical = if (small) 6.dp else 12.dp),
    )
}

@Composable
private fun BoxScope.RoundOver(hud: HudState, onNext: () -> Unit) {
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

@Composable
private fun Label(
    text: String, size: Int, color: Color = Parchment, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier,
) {
    Text(
        text, modifier = modifier, color = color, fontSize = size.sp, fontWeight = weight,
        fontFamily = FontFamily.Serif, textAlign = TextAlign.Start, maxLines = 1,
    )
}
