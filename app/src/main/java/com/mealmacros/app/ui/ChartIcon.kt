package com.mealmacros.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Bar-chart icon for the Dashboard tab (the core icon set has none). */
val BarChartIcon: ImageVector by lazy {
    ImageVector.Builder(name = "BarChart", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .path(fill = SolidColor(Color.Black)) {
            // Three bars on a baseline.
            moveTo(4f, 20f); lineTo(4f, 11f); lineTo(8f, 11f); lineTo(8f, 20f); close()
            moveTo(10f, 20f); lineTo(10f, 4f); lineTo(14f, 4f); lineTo(14f, 20f); close()
            moveTo(16f, 20f); lineTo(16f, 14f); lineTo(20f, 14f); lineTo(20f, 20f); close()
        }
        .build()
}
