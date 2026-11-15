package io.github.beretta299.sharejoy

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.beretta299.sharejoy.presentation.match.MatchViewModel
import io.github.beretta299.sharejoy.ui.match.MatchRoute
import io.github.beretta299.sharejoy.ui.theme.ShareJoyTheme

/** Entry point shared by Android, desktop and iOS. */
@Composable
fun App(viewModel: MatchViewModel = viewModel { MatchViewModel() }) {
    ShareJoyTheme {
        MatchRoute(viewModel)
    }
}
