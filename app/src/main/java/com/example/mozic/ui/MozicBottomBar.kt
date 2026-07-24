package com.example.mozic.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import com.example.mozic.navigation.TopLevelDestination

private const val HAIRLINE_WIDTH_DP = 1

/**
 * Icon-only — no label — for a minimal look: one outline glyph per
 * [TopLevelDestination] (no bold/filled swap on selection either), with tint
 * color as the only signal for which tab is active. [TopLevelDestination.labelRes]
 * still reaches [Icon]'s `contentDescription` so screen readers still
 * announce a name per tab even though nothing is drawn on screen for it.
 *
 * [ShortNavigationBar] (Material3's compact bar, 64dp vs. the tall
 * [androidx.compose.material3.NavigationBar]'s 80dp) rather than that taller
 * default with a null label — that per-item still enforces an 80dp minimum
 * height internally, label or not, so it can't be slimmed down by just
 * passing `label = null`.
 */
@Composable
fun MozicBottomBar(
    destinations: List<TopLevelDestination>,
    currentDestination: NavDestination?,
    onNavigateToDestination: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hairlineColor = MaterialTheme.colorScheme.outlineVariant
    ShortNavigationBar(
        modifier = modifier.drawWithContent {
            drawContent()
            val strokeWidth = HAIRLINE_WIDTH_DP.dp.toPx()
            drawLine(
                color = hairlineColor,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = strokeWidth,
            )
        },
    ) {
        destinations.forEach { destination ->
            val selected = currentDestination.isTopLevelDestinationInHierarchy(destination)
            ShortNavigationBarItem(
                selected = selected,
                onClick = { onNavigateToDestination(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = stringResource(destination.labelRes),
                    )
                },
                label = null,
                colors = ShortNavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedIndicatorColor = Color.Transparent,
                ),
            )
        }
    }
}

private fun NavDestination?.isTopLevelDestinationInHierarchy(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true
