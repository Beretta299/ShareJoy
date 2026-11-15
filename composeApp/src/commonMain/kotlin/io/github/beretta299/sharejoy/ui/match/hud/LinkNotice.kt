package io.github.beretta299.sharejoy.ui.match.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.beretta299.sharejoy.presentation.match.MatchAction
import io.github.beretta299.sharejoy.presentation.match.MatchLinkState

/** Tells the player when the other phone is late, gone, or no longer showing the same game. */
@Composable
internal fun LinkNotice(link: MatchLinkState, onAction: (MatchAction) -> Unit, modifier: Modifier = Modifier) {
    val (title, detail, canLeave) = when (link) {
        MatchLinkState.SHARED_DEVICE, MatchLinkState.CONNECTED -> return
        MatchLinkState.WAITING -> Triple("Waiting for the other phone…", "Keep the phones close together.", false)
        MatchLinkState.DISCONNECTED -> Triple("The other phone left", "The connection was lost.", true)
        MatchLinkState.OUT_OF_SYNC -> Triple("The phones fell out of step", "Start a new match to keep playing.", true)
    }
    Column(
        modifier.clip(PanelShape).background(Panel).border(1.dp, Gold.copy(alpha = 0.6f), PanelShape)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Label(title, 16, Parchment, FontWeight.Bold)
        Label(detail, 12, Muted)
        if (canLeave) {
            Label(
                "Back to menu", 14, Gold, FontWeight.Bold,
                modifier = Modifier.clip(PanelShape).background(PanelLight).clickable { onAction(MatchAction.Leave) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}
