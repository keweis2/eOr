package com.gamelaunch.frontend.ui.screen.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gamelaunch.frontend.domain.model.InstalledApp
import com.gamelaunch.frontend.launcher.PackageManagerHelper
import com.gamelaunch.frontend.ui.component.AppIcon
import com.gamelaunch.frontend.ui.theme.BounceDurationMs
import com.gamelaunch.frontend.ui.theme.BounceEasing
import com.gamelaunch.frontend.ui.theme.BrandBlue
import com.gamelaunch.frontend.ui.theme.TileSub
import com.gamelaunch.frontend.ui.theme.TileText
import com.gamelaunch.frontend.ui.theme.glassTile
import com.gamelaunch.frontend.ui.theme.tileColor

@Composable
fun AppsContent(
    apps: List<InstalledApp>,
    isLoading: Boolean,
    focusedIndex: Int,
    columns: Int,
    packageManagerHelper: PackageManagerHelper,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = BrandBlue)
        }
        return
    }
    if (apps.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No apps found", color = TileSub)
        }
        return
    }

    val gridState = rememberLazyGridState()
    LaunchedEffect(focusedIndex) {
        if (focusedIndex in apps.indices) gridState.animateScrollToItem(focusedIndex)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        itemsIndexed(apps, key = { _, a -> a.packageName }) { index, app ->
            AppCard(
                app = app,
                isFocused = index == focusedIndex,
                color = tileColor(index),
                packageManagerHelper = packageManagerHelper,
                onClick = { onAppClick(app.packageName) }
            )
        }
    }
}

@Composable
private fun AppCard(
    app: InstalledApp,
    isFocused: Boolean,
    color: Color,
    packageManagerHelper: PackageManagerHelper,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = tween(durationMillis = BounceDurationMs, easing = BounceEasing),
        label = "appTileScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glassTile(shape, color = color, selected = isFocused)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp)
    ) {
        AppIcon(
            packageName = app.packageName,
            packageManagerHelper = packageManagerHelper,
            modifier = Modifier.size(52.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelMedium,
            color = TileText,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
