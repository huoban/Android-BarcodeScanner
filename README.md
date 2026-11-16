# 条码扫描记录

自用快递面单条码扫描与记录工具。扫码时保存面单截图，方便回看面单底部的商品信息；历史记录支持模糊搜索和按天清理，也可以把单号通过 HTTP 地址提交到 NAS。

- 当前版本：v1.0.24
- 最低系统：Android 9.0（API 28）
- 目标系统：Android 15（API 35）
- 项目地址：<https://github.com/huoban/Android-BarcodeScanner>

## 功能

### 扫码

- CameraX 全屏相机预览，Google ML Kit 离线条码识别
- 取景框按 76mm × 130mm 物理尺寸换算，最大不超过屏幕的 80%
- `STRATEGY_KEEP_ONLY_LATEST` 帧策略，并保证同一时间只处理一帧
- 连续 2 帧识别结果一致才确认，降低误识别
- 默认仅开启 Code 128，可在设置中开启其他条码格式
- 支持前置/后置摄像头、闪光灯、变焦
- 扫码成功播放提示音、振动（均可在设置中关闭）

支持的条码格式：

| 开关 | 格式 |
|---|---|
| QR | QR Code |
| DataMatrix | Data Matrix |
| Code39 | Code 39 |
| UPCEAN | EAN-13、EAN-8、UPC-A、UPC-E |
| Code128 | Code 128 |
| PDF | PDF417 |
| Aztec | Aztec |
| Code93 | Code 93 |
| Code2of5 | ITF（交叉二五码） |
| Codabar | Codabar |

### 结果处理

- 显示条码类型、格式和扫码内容
- 一键复制扫码内容
- 可选自动复制到剪贴板
- 可选自动提交：使用应用内 WebView 打开自定义地址，地址中的 `{result}` 会替换为 URL 编码后的扫码内容；未包含 `{result}` 时直接拼接在地址末尾
- 扫码成功后保存 WebP 截图（质量 80%）。截图来自 CameraX ImageAnalysis 分析帧，约 640×480，在可读性和存储空间之间取平衡

### 历史记录

- Room 本地数据库，按扫码时间倒序展示
- 按扫码内容模糊搜索
- 点击记录：复制内容并打开截图预览
- 图片预览：默认完整居中显示；支持双指缩放、双击放大/复位、放大后拖动
- 删除菜单：
  - 删除 15 天前
  - 删除 30 天前
  - 删除 60 天前
  - 删除 90 天前
  - 删除 180 天前
  - 清空全部历史
- 删除规则：删除 N 天前 = 保留今天及往前 N-1 天，共 N 个自然日，其余记录全部删除
- 所有删除操作都有二次确认，确认框内会说明删除规则
- 删除记录时同步删除对应的截图文件

### 设置

- 10 个条码格式开关，对应上表中的 13 种格式
- 前置摄像头
- 扫码提示音：开关、频率、时长、调制、音量
- 振动
- 自动复制到剪贴板
- 自动提交及自定义提交地址
- 关于页面：应用版本、ML Kit 版本、项目地址（点击调用系统浏览器打开）

> 扫描方向、扫描分辨率、努力级别、字符串编码属于历史预留设置项，目前仅保存配置，不影响实际扫码。

## 技术栈

| 类别 | 选型 |
|---|---|
| 语言 | Kotlin 1.9.22 |
| 构建 | AGP 8.2.0、Gradle 8.5、JDK 17 |
| 相机 | AndroidX CameraX 1.3.1 |
| 扫码 | Google ML Kit barcode-scanning 17.3.0 |
| 数据库 | Room 2.6.1 + KSP |
| 协程 | kotlinx-coroutines 1.7.3 |
| UI | AppCompat 1.6.1、Material 1.11.0、ConstraintLayout 2.1.4 |
| 最低版本 | Android 9.0（API 28） |
| 目标/编译版本 | Android 15（API 35） |
| ABI | arm64-v8a |

## 数据存储

- 数据库：`barcode_scanner_database`，表 `history`
- 字段：条码类型、条码格式、扫码内容、GS1 标记、原始字节、方向、时间戳、截图路径
- 截图目录：`filesDir/images/{timestamp}.webp`
- 时间戳字段带索引，历史列表按时间倒序查询
- 数据库使用 `fallbackToDestructiveMigration()`：后续版本如果修改表结构，升级时会清空历史数据
- 系统备份仅包含 SharedPreferences，数据库和截图不会自动备份

## 权限

| 权限 | 用途 |
|---|---|
| `CAMERA` | 相机预览与扫码 |
| `VIBRATE` | 扫码成功振动 |
| `INTERNET` | 打开自定义提交地址 |
| `READ_MEDIA_IMAGES` | Android 13+ 读取图片 |
| `READ_EXTERNAL_STORAGE` | Android 12L 及以下读取图片，`maxSdkVersion=32` |

应用允许明文 HTTP，便于访问局域网 NAS；如果提交地址支持 HTTPS，建议优先使用 HTTPS。

## 构建

### 环境要求

- JDK 17
- Android SDK Platform 35、Build-Tools 35
- Gradle 8.5（项目包含 Gradle Wrapper）

### 命令

```bash
# Debug
./gradlew :app:assembleDebug

# Release
./gradlew :app:assembleRelease
```

如果本地没有配置 `local.properties`，请先写入 Android SDK 路径：

```properties
sdk.dir=/path/to/android-sdk
```

### 产物

| 类型 | 路径 |
|---|---|
| Debug | `app/build/outputs/apk/debug/app-arm64-v8a-debug_<version>.apk` |
| Release | `app/build/outputs/apk/release/app-arm64-v8a-release_<version>.apk` |

- 仅打包 `arm64-v8a`
- Release 开启 R8 混淆和资源压缩
- Release 使用项目内的 debug 签名配置，适合自用安装；如需上架或分发，请替换为正式签名

## 项目结构

```text
app/src/main/
├── java/com/example/barcodescanner/
│   ├── SplashActivity.kt              启动页与权限申请
│   ├── DisplayModeActivity.kt         扫码主界面
│   ├── HistoryActivity.kt             历史记录、搜索、删除
│   ├── ImagePreviewActivity.kt        截图预览
│   ├── SettingsActivity.kt            设置容器
│   ├── SettingsFragment.kt            设置项与关于入口
│   ├── AboutActivity.kt               关于页面、项目地址
│   ├── WebViewActivity.kt             提交地址内置浏览器
│   ├── camera/CameraManager.kt        CameraX 生命周期、闪光灯、变焦
│   ├── scanner/                       ML Kit 封装、多帧确认、结果模型
│   ├── data/                          偏好设置、Room 数据库
│   ├── utils/                         截图保存、提示音、剪贴板、CSV
│   └── view/TouchImageView.kt         图片缩放、拖动、居中
└── res/
    ├── layout/                        页面布局
    ├── drawable-nodpi/splash_logo.png 启动页/关于页品牌图
    ├── mipmap-*/                      应用图标
    └── values/                        字符串、颜色、主题
```

## 版本说明

### v1.0.24

- 应用名称调整为「条码扫描记录」
- 替换应用图标、启动页品牌图和关于页图标
- 关于页新增项目地址，点击使用系统浏览器打开
- 历史记录新增 60/90/180 天前删除选项
- 删除前增加二次确认并说明删除规则
- 删除历史记录时同步清理截图文件
- 修复图片预览默认不居中、需要手动拖动的问题
- 修复扫码帧并发、相机生命周期、自动提交暂停、闪光灯图标等问题
- 更新 README 为当前版本功能说明
