# Android 4.4.2 兼容版

基于 fork 的 `468cf6e`（Aerial 1.43），源码在 `legacy/`。最低 API 19，重点适配
Android 4.4.2。现代版 `app/` 的 Compose / Media3 依赖保持不变。

Compose 的最低版本是 API 21，强行降低原版 `minSdk` 或使用 `tools:overrideLibrary`
不能让它在 Dalvik 上运行。因此兼容版采用独立的系统 View 界面和系统 MediaPlayer，
不依赖 AndroidX、Compose、Media3、Kotlin 运行库或本地 ABI 库，共用原项目航拍目录。

## 功能与边界

| 功能 | 兼容版 |
| --- | --- |
| 遥控首页 | 960×540 设计画布、五列应用网格、清晰焦点框、系统图标 |
| 应用 | LAUNCHER / LEANBACK / HOME 枚举、按包去重、启动、置顶、隐藏、详情、恢复隐藏 |
| 时钟 | 跟随系统 12/24 小时制、校时、时区和待机恢复时立即刷新 |
| 壁纸 | 三种渐变、系统文件选择器导入照片（采样解码，复制到私有目录） |
| 屏保 | 时钟与壁纸、原目录的 1080p H.264 航拍、空闲计时、系统 Dream |
| 视频失败 | 准备/首帧超时、最多尝试三段、最终退回时钟，不无限重试 |
| 自启 / 守护 | 默认关闭；设置中可开启。守护只观察 HOME 窗口，不过滤遥控器按键 |
| 暂未移植 | 手机配置页、天气、文件夹、家长密码、每日壁纸、动态壁纸、4K/HDR、HDMI 私有接口、自动升级 |

这是具有部分原版功能的兼容实现，不是原版的完整等效移植。
Android 4.0–4.3 暂不支持；Android 5+ 继续优先使用原 `app/`。

视频始终选择目录的 `url`（H.264），不会请求 HEVC / HDR。旧系统的根证书或 TLS
能力可能过时，Apple 公开影片发生 I/O 错误时，只允许 `sylvan.apple.com` 的视频
从 HTTPS 回退 HTTP；不关闭证书验证。连接超时也可能与网络有关。图片不上传。
有网不等于能播放，仍取决于电视硬件解码器、容器支持和 CDN 可达性。

## 编译

JDK 17，Android SDK Platform 36 和 Build Tools 36.0.0：

```sh
./gradlew :legacy:assembleStandardRelease :legacy:assembleGuardRelease \
  :legacy:lintStandardRelease :legacy:lintGuardRelease
```

- 普通版：`legacy/build/outputs/apk/standard/release/legacy-standard-release.apk`
- 守护版：`legacy/build/outputs/apk/guard/release/legacy-guard-release.apk`
- CI：`.github/workflows/legacy.yml`，可手动触发，下载 `aerial-android44` 构建产物。

两个 APK 的包名均为 `app.orchard.tv.legacy`，不能同时安装两种 flavor；它们可与
原版 `app.orchard.tv` 并存，不读取、迁移或清除原版数据。standard 声明 HOME，guard
不声明 HOME。guard 版需要手动开启无障碍服务，且固件必须发送原桌面窗口事件。

release 优先读取 `local/legacy-signing.properties`，字段模板见
`tools/legacy-signing.properties.example`，密钥文件相对 `local/` 解析。
没有配置时使用构建机的 debug 签名，适合本地验证。保留同一密钥才能覆盖升级；
不同 CI 运行的临时密钥不能保证相同。正式发布前应配置 fork 自己的持久化签名，
不要使用上游签名，也不要将密钥提交进仓库。

## 安装和回退

先看 APK 的安装与显示效果，不要直接运行原项目的 `tools/install.sh`：该脚本面向
现代版包名，且可能调用 Android 4.4 没有的命令。

```sh
adb install -r legacy/build/outputs/apk/standard/release/legacy-standard-release.apk
adb shell am start -n app.orchard.tv.legacy/.MainActivity
```

方向键选择，确定打开；第一排按上到设置；菜单或长按确定整理；返回先回第一排，
再次返回预览屏保。屏保唤醒时整次按键被消费，不会顺便打开应用。

默认不设置为系统桌面、不改系统属性、不写无障碍列表、不修改系统屏保、不停用
任何系统包。开启守护需在系统无障碍设置里选择 Aerial；固件没有入口时会提示。
从原桌面磁贴进入后，守护会暂停，直到手动重新打开 Aerial，方便访问原生功能。
开机自启依赖固件是否发放 `BOOT_COMPLETED`，不能保证绕过厂商限制。

要回退，先在兼容版设置中关闭开机自启，在系统无障碍设置中关闭其守护服务，
若曾设为默认桌面，先从系统应用设置清除本应用默认项并选择原桌面。
然后可选卸载 `app.orchard.tv.legacy`（会删除兼容版本身的设置和导入照片）。
不要卸载/禁用原桌面，不要清理原版 Aerial 的数据。

## 验证方法

必须在 API 19 真机或模拟器上测试 release。Android 4.4 没有 `cmd package compile`，
不要照抄现代电视的 ART 编译命令。

```sh
./gradlew :legacy:assembleStandardReleaseAndroidTest
adb install -r legacy/build/outputs/apk/standard/release/legacy-standard-release.apk
adb install -r legacy/build/outputs/apk/androidTest/standard/release/legacy-standard-release-androidTest.apk
adb shell am instrument -w \
  app.orchard.tv.legacy.test/app.orchard.tv.legacy.LegacyInstrumentation
```

测试器无额外测试库，在 release 上检查应用枚举、左右导航、设置入口上下导航、
应用启动与返回、三次屏保唤醒及按键不穿透。加 `-e aerial true` 可额外测试
航拍是否渲染首帧（需要网络，临时选择航拍，结束恢复原屏保偏好）。
测试时关闭系统浮层，避免同时操作遥控器；不要把注入事件当作实体遥控器验收。
航拍网络/硬解、真实遥控器、Dream、守护和冷启动仍需
单独验证，不能用通过编译代替。设备私有地址和测试原始资料放 `local/`，不提交。

旧固件没有 `logcat` 命令时，可用 `adb shell dumpsys activity app.orchard.tv.legacy`
读取页面的 `Aerial Legacy` 诊断行（API、应用数、时钟、播放状态、首帧次数、错误码）。

## 本次验证（2026-10-24）

海信 Hi3751 平台、Android 4.4.2 / API 19 真机，standard release：

- 安装成功；v1（JAR）签名校验通过；单 DEX，无原生 ABI 依赖。
- 枚举 19 个用户/系统应用；测试辅助包安装时为 20 个；界面左右、设置上下导航通过。
- 由桌面启动独立测试应用，再自动返回桌面通过。测试器使用自己的无数据辅助页，
  避免任意厂商应用的登录流程干扰；测试后移除辅助包。
- 三次时钟屏保进入/确定键唤醒通过，唤醒键没有顺带启动磁贴。
- 在线航拍实测 `state=rendering renderStarts=1 lastError=none`，H.264 首帧成功渲染。
  这是短时播放验证，尚未做长时间轮播/网络中断/全部 114 段解码测试。
- standard/guard release 构建与严格 lint 均通过，报告 `No issues found`。

照片导入、应用置顶/隐藏持久化、系统 Dream、实体遥控器长按、实际待机/冷启动、
无障碍守护，以及 guard flavor 的安装接管未在本轮真机验收；代码已提供，不能
据上述测试断言这些功能在所有旧固件上正常。
