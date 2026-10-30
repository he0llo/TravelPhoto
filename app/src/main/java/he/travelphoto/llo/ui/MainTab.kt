package he.travelphoto.llo.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class MainTab(val label: String, val icon: ImageVector) {
    HOME("主页", Icons.Rounded.Home),
    EXPORT("导出", Icons.Rounded.IosShare),
    SETTINGS("设置", Icons.Rounded.Settings),
}
