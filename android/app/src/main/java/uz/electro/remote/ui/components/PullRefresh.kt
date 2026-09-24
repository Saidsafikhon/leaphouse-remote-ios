package uz.electro.remote.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlinx.coroutines.delay
import uz.electro.remote.ui.theme.ElectroColors

/** Обновление жестом «потянуть вниз» — как .refreshable на iOS. Без onRefresh — просто содержимое. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRefresh(onRefresh: (() -> Unit)?, content: @Composable () -> Unit) {
    if (onRefresh == null) { content(); return }
    val state = rememberPullToRefreshState()
    if (state.isRefreshing) {
        LaunchedEffect(true) { onRefresh(); delay(900); state.endRefresh() }
    }
    Box(Modifier.fillMaxSize().nestedScroll(state.nestedScrollConnection)) {
        content()
        PullToRefreshContainer(
            state = state, modifier = Modifier.align(Alignment.TopCenter),
            containerColor = ElectroColors.SurfaceElevated, contentColor = ElectroColors.Accent,
        )
    }
}
