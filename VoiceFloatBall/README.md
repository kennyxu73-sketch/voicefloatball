# 语音悬浮球 VoiceFloatBall

安卓全局语音输入悬浮球，点击即说话，文字自动填入当前输入框。

## 功能

- 全局悬浮球，可拖动位置
- 点击开始录音（系统离线语音识别，无需联网）
- 识别完成后自动填入当前焦点输入框
- 无障碍服务回退：若无法自动填入，复制到剪贴板
- 开机自启
- 支持 Android 8.0 ~ 14（minSdk 26）

## 编译方式

### 方式1：GitHub Actions（推荐，无需本地环境）

1. 把这个项目 push 到你的 GitHub 仓库
2. 进入 Actions 标签页
3. 选择 "Build APK" → Run workflow
4. 等待约 3-5 分钟，下载 Artifacts 里的 APK

### 方式2：Android Studio 本地编译

1. 安装 Android Studio（https://developer.android.com/studio）
2. 打开此项目
3. 点击 Build → Build Bundle(s) / APK(s) → Build APK(s)
4. APK 在 `app/build/outputs/apk/debug/` 目录下

## 安装步骤

1. 手机开启"允许安装未知来源应用"
2. 安装 APK
3. 打开 App，按提示授予权限：
   - **悬浮窗权限**（必须）
   - **录音权限**（必须）
   - **无障碍服务**（推荐，用于自动填入文字）
4. 点击"启动悬浮球"
5. 最小化 App，屏幕上会出现紫色悬浮球

## 使用

- 点击悬浮球 → 弹出录音界面
- 说话 → 识别文字自动填入当前输入框
- 长按拖动悬浮球到任意位置

## 权限说明

| 权限 | 用途 | 是否必须 |
|------|------|----------|
| 悬浮窗 | 显示悬浮球 | 必须 |
| 录音 | 语音识别 | 必须 |
| 无障碍服务 | 自动填入文字 | 推荐（否则需手动粘贴） |
| 开机自启 | 开机自动启动 | 可选 |

## 项目结构

```
VoiceFloatBall/
├── app/src/main/
│   ├── java/com/voiceball/app/
│   │   ├── MainActivity.kt           # 权限引导主界面
│   │   ├── FloatingBallService.kt    # 悬浮球前台服务
│   │   ├── VoiceRecognitionActivity.kt # 语音识别界面
│   │   ├── VoiceAccessibilityService.kt # 无障碍自动填字
│   │   └── BootReceiver.kt           # 开机自启
│   ├── res/
│   │   ├── layout/                   # 布局文件
│   │   ├── drawable/                 # 图标资源
│   │   └── xml/accessibility_service_config.xml
│   └── AndroidManifest.xml
├── .github/workflows/build.yml       # GitHub Actions 自动编译
└── README.md
```
