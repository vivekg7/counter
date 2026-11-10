package com.crylo.counter.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/** Material "remove" (a minus sign), which the core icon set doesn't include. */
val Icons.Filled.Minus: ImageVector by lazy {
    materialIcon(name = "Filled.Minus") {
        materialPath {
            moveTo(19f, 13f)
            horizontalLineTo(5f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(14f)
            close()
        }
    }
}
