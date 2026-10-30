package he.travelphoto.llo.ui.settings

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import he.travelphoto.llo.data.AppSettings
import he.travelphoto.llo.data.AppStorage
import he.travelphoto.llo.data.MapProvider
import he.travelphoto.llo.ui.components.PageHeader
import he.travelphoto.llo.ui.components.SectionHeader
import he.travelphoto.llo.ui.rememberFolderPicker
import he.travelphoto.llo.ui.theme.ThemeMode
import he.travelphoto.llo.ui.theme.ThemePreset
import he.travelphoto.llo.ui.theme.ThemePresets
import he.travelphoto.llo.ui.theme.ThemeState

@Composable
internal fun SettingsScreen(
    themeState: ThemeState,
    settings: AppSettings,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val shared = AppStorage.isUsingSharedStorage(context)
    val dark = when (themeState.mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val exportFolderPicker = rememberFolderPicker { uri ->
        val name = runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
        settings.setExportFolder(uri, name ?: "自定义文件夹")
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item("header") {
            PageHeader(
                eyebrow = "设置",
                title = "偏好设置",
                subtitle = "存储、导出与显示偏好，都收在这一页。",
            )
        }

        item("storage-title") {
            SectionHeader(
                title = "存储与备份",
                subtitle = "相册、照片与索引放在同一个可携带文件夹",
            )
        }
        item("storage") {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingIcon(Icons.Rounded.SdCard)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "相册存储位置",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = AppStorage.locationLabel(context),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (shared) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                    TextButton(onClick = {
                        if (shared) {
                            Toast.makeText(context, "已经在使用 SD 卡/TravelPhoto", Toast.LENGTH_SHORT).show()
                        } else if (!AppStorage.openSharedStorageSettings(context)) {
                            Toast.makeText(context, "当前系统不支持该设置页", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text(if (shared) "已授权" else "授权 SD 卡")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = AppStorage.displayRoot(context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!shared) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "未授权时先使用应用专属目录，授权后点击“重新加载应用”，旧相册会自动迁移到 SD 卡。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            (context as? Activity)?.recreate()
                        },
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("重新加载应用")
                    }
                    TextButton(onClick = {
                        AppStorage.migrateLegacyData(context)
                        Toast.makeText(context, "旧数据已检查并迁移", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("迁移旧数据")
                    }
                }
            }
        }

        item("export-title") {
            SectionHeader(
                title = "导出偏好",
                subtitle = "选择导出文件的默认保存位置",
            )
        }
        item("export") {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingIcon(Icons.Rounded.Folder)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "默认保存位置",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = settings.exportFolderLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    TextButton(onClick = exportFolderPicker) { Text("更改") }
                }
                if (!settings.usesDefaultExportFolder) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { settings.resetExportFolder() }) {
                            Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("恢复 TravelPhoto/Download")
                        }
                    }
                }
            }
        }

        item("display-title") {
            SectionHeader(
                title = "地图与时间线",
                subtitle = "选择地图来源，并决定照片说明的默认写法",
            )
        }
        item("map-provider") {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingIcon(Icons.Rounded.Map)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "地图来源",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "切换后重新打开相册即可生效",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MapProvider.entries.forEach { provider ->
                        FilterChip(
                            selected = settings.mapProvider == provider,
                            onClick = { settings.changeMapProvider(provider) },
                            label = { Text(provider.label) },
                            leadingIcon = if (settings.mapProvider == provider) {
                                {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
        item("display") {
            SettingsCard {
                SettingSwitch(
                    icon = Icons.Rounded.Schedule,
                    title = "说明中显示时间",
                    subtitle = "默认写成「10月1日 09:30 到达上海」",
                    checked = settings.captionShowsTime,
                    onCheckedChange = settings::changeCaptionShowsTime,
                )
            }
        }

        item("appearance-title") {
            SectionHeader(
                title = "外观模式",
                subtitle = "跟随系统，或固定为浅色 / 深色",
            )
        }
        item("appearance") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ModeCard(
                    mode = ThemeMode.SYSTEM,
                    icon = Icons.Rounded.BrightnessAuto,
                    selected = themeState.mode == ThemeMode.SYSTEM,
                    onClick = { themeState.selectMode(ThemeMode.SYSTEM) },
                    modifier = Modifier.weight(1f),
                )
                ModeCard(
                    mode = ThemeMode.LIGHT,
                    icon = Icons.Rounded.LightMode,
                    selected = themeState.mode == ThemeMode.LIGHT,
                    onClick = { themeState.selectMode(ThemeMode.LIGHT) },
                    modifier = Modifier.weight(1f),
                )
                ModeCard(
                    mode = ThemeMode.DARK,
                    icon = Icons.Rounded.DarkMode,
                    selected = themeState.mode == ThemeMode.DARK,
                    onClick = { themeState.selectMode(ThemeMode.DARK) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item("color-title") {
            SectionHeader(
                title = "主题色",
                subtitle = "八种取自旅途的颜色，切换后所有控件同步换色",
                action = {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
        }
        item("colors") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemePresets.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { preset ->
                            ThemeSwatch(
                                preset = preset,
                                selected = preset.id == themeState.themeId,
                                dark = dark,
                                onClick = { themeState.selectPreset(preset.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(4 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        item("preview-title") {
            SectionHeader(
                title = "主题预览",
                subtitle = "当前主题下的卡片与标题效果",
            )
        }
        item("preview") {
            val preset = ThemePresets.firstOrNull { it.id == themeState.themeId } ?: ThemePresets.first()
            ThemePreview(preset = preset, dark = dark)
        }

        item("about-title") {
            SectionHeader(
                title = "关于",
                subtitle = "开发者、版本与开源仓库",
            )
        }
        item("about") {
            SettingsCard {
                Surface(
                    onClick = onOpenAbout,
                    color = Color.Transparent,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingIcon(Icons.Rounded.Info)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "关于 TravelPhoto",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "he0llo · 版本 1.0.0 · Material Design 3",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "照片只留在你的设备上。不创建账号、不上传照片，拍摄时间与地点从照片 EXIF 读取。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SettingIcon(icon: ImageVector) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SettingSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(icon)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ModeCard(
    mode: ThemeMode,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                text = mode.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ThemeSwatch(
    preset: ThemePreset,
    selected: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.size(48.dp)) {
                Row(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .then(
                            if (selected) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(preset.previewPrimary(dark)),
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(preset.previewSecondary(dark)),
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(preset.previewContainer(dark)),
                    )
                }
                if (selected) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(19.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "已选择",
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = preset.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun ThemePreview(preset: ThemePreset, dark: Boolean) {
    val ink = preset.previewPrimary(dark)
    val soft = preset.previewContainer(dark)
    val accent = preset.previewSecondary(dark)
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = soft,
        contentColor = ink,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Landscape, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "下一次出发",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "山海之间，\n把光收进相册。",
                style = MaterialTheme.typography.headlineSmall,
                color = ink,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "2026 年 10 月 · 12 张照片 · 6 个足迹",
                style = MaterialTheme.typography.bodySmall,
                color = ink.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewPill("旅行地图", ink)
                PreviewPill("时间线", ink)
                PreviewPill("导出分享", ink)
            }
        }
    }
}

@Composable
private fun PreviewPill(text: String, ink: Color) {
    Surface(
        shape = CircleShape,
        color = ink.copy(alpha = 0.12f),
        contentColor = ink,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun AboutLine(title: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(48.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}
