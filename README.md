# TravelPhoto · 旅行相册

一个本地优先的旅行相册 Android 应用。选好照片后，TravelPhoto 会读取拍摄时间与 GPS，
把旅程排成一张地图和一条时间线，并支持导出为 Word、PDF 或可再次导入的 `.travelphoto`。

- 包名：`he.travelphoto.llo`
- 技术栈：Kotlin + Jetpack Compose + Material 3
- minSdk 24 / targetSdk 36 / compileSdk 37
- 无账号、无云端、无地图 API Key；相册默认保存在 SD 卡 `TravelPhoto`，也可回退到应用专属目录

## 功能

### 主界面

- 左上角主操作卡「创建旅行相册」，右侧稍小的「导入相册文件」
- 本地相册搜索
- 相册卡片列表：封面、日期范围、照片数/天数、足迹数，支持重命名与删除
- 底部悬浮胶囊导航：主页 / 导出 / 设置

### 相册编辑器

- 进入相册后是全屏瓦片地图 + 可上下拖动、可完全隐藏的「时间线」抽屉
- 地图支持平移、双指缩放，缩放范围 3–18 级，可放大到街道/地表级别
- 地图来源可在设置中切换：高德地图 / 开放地图（OpenStreetMap），两者均使用 Web Mercator
- 地图标记按距离聚合，标记上显示照片缩略图与数量
- 长按标记约 0.5 秒弹出删除确认；标记不需要长按即可直接拖动换位，没有额外防误触延迟
- 标记只承担「记录点」职责，没有点击详情等附加操作
- 搜索城市或精确地点（内置中文城市库，找不到时用 OpenStreetMap Nominatim 补充），
  选中后地图直接放大到该地点；在地图上创建记录点时会请求反查地址，
  优先显示「北京市西城区菜市口」这类精确地名
- 自动读取 EXIF 拍摄时间与 GPS；无定位照片显示在时间线抽屉顶部
- 待定位照片不再需要拖拽：点缩略图或「点此定位」进入选点状态，
  再点地图上的记录点即可归入该点，点空地则新建记录点
- 时间线按「日期 × 地点」生成记录点，记录点带默认说明并可单独编辑
- 单张照片默认没有说明，时间线显示「添加说明」；点击铅笔自由填写
- 点击照片打开详情：
  - 修改拍摄日期与时间（Material 3 DatePicker + TimeInput）
  - 调整/移除位置
  - 设为封面、编辑说明、删除照片
- 编辑器内可继续导入照片、重命名或删除整个相册
- 主界面胶囊切换仍使用导航自身的选中动画，不做整页滑出；
  打开相册、关于等子页面时使用横向划入/划出转场
- 地图底图使用公开瓦片，高德来源底部保留「© 高德地图」署名，开放地图保留 OSM 署名

### 导出

- 打开导出页时默认只勾选 PDF，并自动选中最近编辑的一个相册
- 也可以再勾选更多相册，并同时生成任意组合的三种格式：
  - **Word `.docx`**：手写 OOXML，可继续编辑
  - **PDF**：A4 封面 + 按天排版，适合打印和分享
  - **`.travelphoto`**：ZIP 容器，含 `manifest.json` 与原始照片，可重新导入
- PDF 支持每页 1–8 张照片；按所选数量自动排版，照片保持原始比例、完整显示，不裁切、不旋转
- 时间线/地点的公共说明只在每天照片之后输出一次，不会重复写在每张照片下面
- 默认直接保存到 `SD 卡/TravelPhoto/Download`，不再强制走 SAF
- 也可以「更改」保存位置，通过系统文件选择器（SAF）选任意目录
- 「我的导出」会记录最近生成的文件，可以直接打开或通过系统分享面板发送

### 设置

- **存储与备份**：显示当前是 SD 卡 `TravelPhoto` 还是应用专属目录，可重新加载应用、迁移旧数据
- **导出偏好**：默认保存位置（可改）
- **地图与时间线**：地图来源（高德 / 开放地图）、说明是否显示时间
- 跟随系统 / 浅色 / 深色三种外观
- 8 个取自旅途的主题色：远行蓝、松林绿、落日橙、樱花粉、暮光紫、海岸青、沙漠金、火山红
- 主题切换后全部 Material 3 控件自动换色，不在控件中硬编码颜色
- **关于**：自动读取当前应用图标，展示开发者 he0llo、版本与项目仓库 `he0llo/TravelPhoto`

## 构建

推荐使用 Android Studio 的 JBR 21：

```powershell
$env:JAVA_HOME='C:\AppEnglish\AndroidStudio\AndroidStudio\jbr'
.\gradlew.bat :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

静态检查：

```powershell
$env:JAVA_HOME='C:\AppEnglish\AndroidStudio\AndroidStudio\jbr'
.\gradlew.bat :app:lintDebug
```

### 版本说明

- AGP 9.3 使用内置 Kotlin，不额外应用 `org.jetbrains.kotlin.android`
- Compose 编译器插件 `org.jetbrains.kotlin.plugin.compose` 2.3.21
- Compose BOM 2026.08.00（Material 3 1.4.0）
- 因为 Compose 1.12 系列要求，`compileSdk` 使用 37

## 数据与隐私

- 默认根目录：`/sdcard/TravelPhoto`
  - 相册索引：`/sdcard/TravelPhoto/albums.json`
  - 照片原图：`/sdcard/TravelPhoto/Albums/<albumId>/photos/`
  - 缩略图：`/sdcard/TravelPhoto/Albums/<albumId>/thumbs/`
  - 默认导出：`/sdcard/TravelPhoto/Download/`
- 未授予「所有文件访问权限」时，回退到 `getExternalFilesDir()/TravelPhoto`，首页与设置页仍可正常使用
- 首次启动会说明存储位置；授权后可一键迁移旧数据
- 应用不申请任何账号，照片不会被上传
- 搜索地点时仅把用户输入的地点关键词发送给 OpenStreetMap Nominatim；照片与坐标不会上传
- 主题偏好：`SharedPreferences` 的 `travelphoto_appearance`
- 功能偏好：`SharedPreferences` 的 `travelphoto_settings`
- Android 13+ 使用系统 Photo Picker；Android 17 会在选择照片前询问是否包含位置信息，
  只有选择「包含位置信息」后才会保留 EXIF GPS
- Android 13–16 首次选图时会请求 `ACCESS_MEDIA_LOCATION`，用于保留照片中的 GPS；
  拒绝后仍可正常导入照片，只是无法自动落点
- 为支持 SD 卡存储与地点搜索，Manifest 声明 `MANAGE_EXTERNAL_STORAGE`、
  `READ/WRITE_EXTERNAL_STORAGE`（旧系统）、`INTERNET` 与 `ACCESS_NETWORK_STATE`

## `.travelphoto` 格式

ZIP 结构：

```
manifest.json
photos/<photoId>.<ext>
```

`manifest.json` 关键字段：

```json
{
  "format": "travelphoto",
  "version": 2,
  "generator": "TravelPhoto",
  "album": {
    "title": "2026 年 10 月 · 旅行",
    "createdAt": 1759296000000,
    "updatedAt": 1759593600000,
    "coverPhotoId": "p_xxx",
    "photos": [
      {
        "id": "p_xxx",
        "name": "shanghai.jpg",
        "path": "photos/p_xxx.jpg",
        "takenAt": 1759296000000,
        "latitude": 31.2304,
        "longitude": 121.4737,
        "placeName": "上海",
        "note": "清晨在外滩散步",
        "width": 1200,
        "height": 900
      }
    ],
    "placeNotes": [
      {
        "id": "pl_xxx",
        "latitude": 31.2304,
        "longitude": 121.4737,
        "name": "上海",
        "note": "这次住在武康路附近",
        "createdAt": 1759296000000
      }
    ]
  }
}
```

导入时会校验 ZIP 条目、照片数量与体积，并拒绝路径穿越；同名相册会自动追加「（2）」。

## 代码结构

```
app/src/main/java/he/travelphoto/llo/
├── MainActivity.kt
├── data/
│   ├── Models.kt                 # Photo / Album / PlaceNote + JSON 序列化
│   ├── AppStorage.kt             # SD 卡 / 回退目录、权限与迁移
│   ├── AlbumRepository.kt        # 相册状态、导入、编辑、持久化
│   ├── AlbumStorage.kt           # 相册目录布局
│   ├── AppSettings.kt            # 导出/地图/显示偏好
│   ├── PlaceSearch.kt            # 本地城市库 + Nominatim 搜索
│   ├── PhotoImporter.kt          # 复制照片、EXIF 时间/GPS、缩略图
│   ├── TravelPhotoArchive.kt     # .travelphoto 导入/导出
│   ├── DocumentExporter.kt       # 手写 DOCX
│   ├── PdfExporter.kt            # PdfDocument 排版
│   └── AlbumExporter.kt          # 批量导出 + SAF / 默认目录
├── ui/
│   ├── TravelPhotoApp.kt         # 应用级导航
│   ├── MainTab.kt
│   ├── MediaPickers.kt           # Photo Picker / 相册文件 / 目录选择
│   ├── components/Common.kt
│   ├── home/HomeScreen.kt
│   ├── editor/AlbumEditorScreen.kt
│   ├── editor/WorldMap.kt         # Web Mercator 瓦片地图 + 标记手势
│   ├── editor/AmapTileLoader.kt   # 高德 / OSM 瓦片下载与内存/磁盘缓存
│   ├── editor/PlaceSearchDialog.kt
│   ├── export/ExportScreen.kt
│   ├── settings/SettingsScreen.kt
│   ├── settings/AboutScreen.kt
│   └── theme/Theme.kt, SeedPalettes.kt
└── util/
    ├── Formatters.kt             # 日期、中文地名、默认说明
    ├── Geo.kt
    ├── ImageCodec.kt
    └── PlaceDirectory.kt         # 内置中文城市库
```

## 设计约束

- 所有界面颜色取自 `MaterialTheme.colorScheme` 的角色，页面中不写死颜色
- 形状、排版、触控尺寸遵循 Material 3
- 地图、时间线、插画均为 Compose Canvas / Vector 自绘；底图使用高德 / OpenStreetMap 公开瓦片
- 公开瓦片端点仅用于学习与测试，正式发布请改用高德官方 SDK + Key，并遵守 OSM 瓦片使用政策
- 中文界面，日期使用本地时区
