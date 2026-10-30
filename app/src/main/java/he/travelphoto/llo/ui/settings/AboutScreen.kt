@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package he.travelphoto.llo.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import he.travelphoto.llo.ui.components.SectionHeader

private const val REPOSITORY_URL = "https://github.com/he0llo/TravelPhoto"
private const val DEVELOPER = "he0llo"
private const val VERSION = "1.0.0"

@Composable
internal fun AboutScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val appIcon = remember(context) {
        runCatching {
            context.packageManager
                .getApplicationIcon(context.packageName)
                .toBitmap(176, 176)
                .asImageBitmap()
        }.getOrNull()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = 40.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("hero") {
                Surface(
                    shape = RoundedCornerShape(30.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(88.dp),
                        ) {
                            if (appIcon != null) {
                                Image(
                                    bitmap = appIcon,
                                    contentDescription = "旅行相册图标",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(26.dp)),
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.PhotoLibrary,
                                    contentDescription = "旅行相册图标",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(20.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "TravelPhoto",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "旅行相册 · 把路上的光收好",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        )
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        ) {
                            Text(
                                text = "版本 $VERSION",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }

            item("developer-title") {
                SectionHeader(title = "开发者", subtitle = "一个人维护的旅行相册")
            }
            item("developer") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        InfoRow(
                            icon = Icons.Rounded.Person,
                            title = "开发者",
                            value = DEVELOPER,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        InfoRow(
                            icon = Icons.Rounded.Code,
                            title = "项目仓库",
                            value = "github.com/he0llo/TravelPhoto",
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { runCatching { uriHandler.openUri(REPOSITORY_URL) } },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("打开 GitHub 仓库")
                        }
                    }
                }
            }

            item("privacy-title") {
                SectionHeader(title = "数据与隐私", subtitle = "照片始终属于你")
            }
            item("privacy") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        InfoRow(
                            icon = Icons.Rounded.Shield,
                            title = "本地优先",
                            value = "不创建账号、不上传照片；拍摄时间与地点从 EXIF 读取，导出文件只写入你选择的位置。",
                            onContainer = true,
                        )
                    }
                }
            }

            item("features-title") {
                SectionHeader(title = "这本相册能做什么", subtitle = "为旅行者做的记录工具")
            }
            item("features") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        InfoRow(
                            icon = Icons.Rounded.Landscape,
                            title = "足迹地图",
                            value = "内嵌世界地图与中文城市名，不依赖网络与地图密钥。",
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        InfoRow(
                            icon = Icons.Rounded.PhotoLibrary,
                            title = "时间线与地点说明",
                            value = "照片自动按拍摄时间整理，也能自己写一句当时的感受。",
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        InfoRow(
                            icon = Icons.Rounded.Code,
                            title = "开放格式",
                            value = "导出 Word、PDF 和可再次导入的 .travelphoto 相册文件。",
                        )
                    }
                }
            }

            item("footer") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Material Design 3 · Jetpack Compose",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "为路上的光，留一本相册。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    value: String,
    onContainer: Boolean = false,
) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = CircleShape,
            color = if (onContainer) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = if (onContainer) {
                MaterialTheme.colorScheme.onTertiary
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
        ) {
            Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = if (onContainer) {
                    MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
