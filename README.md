# IAG Light Stick - Android 荧光棒控制与编排工具

<p align="center"><a href="#快速开始">快速开始</a>&emsp;&emsp;<a href="#构建与测试">构建与测试</a>&emsp;&emsp;<a href="https://github.com/gem0816-1/iag-lightstick-android/issues">问题反馈</a></p>

IAG Light Stick 是一款 Android 荧光棒工具，提供 BLE 设备连接、灯光预览、编排文件导入与播放，以及基于系统播放音频的音乐律动和指纹跟随。

界面使用 Kotlin 与 Jetpack Compose，包含控制、编排、律动、设备四页，以及迷你播放器、展开播放器和可调节的液态玻璃底栏。玻璃模块移植自开源 AM++ / Backdrop，并保留其来源与许可证。

当前源码基线为 **v0.9.21 / versionCode 31**，支持 **Android 8.0 / API 26 及以上**；系统音频捕获需要 **Android 10 / API 29 及以上**。本仓库是公开整理版：替换了原工作副本中来源未核定的图片及播放器图标，移除了固定测试设备地址和未使用的旧移植文件。它与历史测试 APK 的资源和显示名称不同，应用 ID、工程格式与既有 BLE 指令保持不变。

**当前版本的实机兼容性、真实荧光棒效果和音频捕获仍未验收。** JVM 测试与构建成功不能替代这些结果，也不代表通过 Google Play 审核。

## 功能

| 页面 / 入口 | 主要功能 |
| --- | --- |
| 控制 | 颜色选择、亮度、常亮/关闭/闪烁/呼吸等灯效，以及灯光预览 |
| 编排 | 导入、保存、播放、暂停、定位、导出和分享编排；支持轨道优先级与时间轴 |
| Resolve 文件衔接 | 导入 Cue JSON，保留帧率、灯效参数及原始工程内容；可导出返回电脑继续编辑 |
| 律动 | 用户授权后读取系统正在播放的媒体音频，按电平控制灯光；不使用麦克风作为音源 |
| 指纹跟随 | 录制或使用参考指纹匹配视频音频；无参考时可从当前进度手动跟随 |
| 设备 | 按 BLE 名称或既有服务 UUID 发现候选设备、连接状态与重连处理；公开版不内置个人设备地址 |
| 播放器与外观 | 迷你/展开播放器、深浅主题、玻璃开关、模糊与收缩/回弹设置 |

现有硬件协议针对项目已实现的荧光棒型号，不能据此推定任意蓝牙设备兼容。Resolve 衔接通过文件完成，未提供手机与电脑播放头的实时同步。

## 快速开始

### 使用条件

- Android 8.0 及以上、支持 BLE 的手机；系统音频功能需要 Android 10 及以上。
- 使用与现有实现相符的荧光棒，并根据系统版本授予附近设备或扫描定位权限。
- 系统音频功能需要用户主动启动并确认系统捕获授权；播放来源也必须允许音频捕获。
- 在线曲库需要网络；已保存的工程可在本机使用。

### 获取与安装

目前以源码形式提供，不将历史 APK 作为公开整理版的安装包。克隆后使用 Android Studio 打开仓库根目录，或在已配置 JDK 与 Android SDK 的终端构建：

```powershell
git clone https://github.com/gem0816-1/iag-lightstick-android.git
cd iag-lightstick-android
.\gradlew.bat :app:assembleDebug
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。可复制到手机安装，也可在设备已授权 USB 调试后使用自己的 ADB 安装；安装前核对现有应用的包名、签名与数据，避免通过卸载解决签名冲突。

### 导入与播放工程

1. 从桌面编排器保存 `.iaglight`，或准备 RRNN JSON / Resolve Cue JSON。
2. 将文件复制到手机，在“编排”页导入；也可尝试通过系统“打开方式”或分享入口交给本应用。
3. 导入时校验格式、轨道、颜色与时间边界；无效输入不会覆盖已有有效工程。
4. 连接兼容的荧光棒后播放；暂停、定位或停止通过现有控制层更新灯效。
5. 使用导出或分享保存工程原文；分享、旋转恢复和真实设备播放仍需实机回归。

示例见 [examples/basic.iaglight](examples/basic.iaglight)。示例只含简单颜色事件，不包含歌曲音频、演唱会场控数据或参考指纹。

## 构建与测试

已核验的工具链：**JDK 21、Gradle Wrapper 9.7.1、AGP 9.3.2、Kotlin/Compose 插件 2.4.10**。编译目标 API 37，`targetSdk=36`、`minSdk=26`，Java/Kotlin 字节码目标 17。保留项目的既有工具链组合，未迁移 AGP 内置 Kotlin。

通过 Android Studio 配置 Android SDK 37 与 Build Tools 37.0.0；也可设置 `ANDROID_HOME`，或在本机创建 `local.properties` 指定 `sdk.dir`。不要提交个人 SDK 路径、签名密钥和密码。首次构建需要网络下载对应的 Wrapper 与依赖，依赖缓存齐全后可加 `--offline`。

在 PowerShell 中运行：

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:bundleRelease :app:testDebugUnitTest :ampp-glass:testDebugUnitTest :app:lintDebug :app:lintRelease -I tools/play-bundle-check.init.gradle checkPlayBundle --console=plain
```

Linux/macOS 将 `.\gradlew.bat` 换成 `./gradlew`。`checkPlayBundle` 复用 AGP 的 bundletool 检查 AAB 结构及 Manifest/config，不签名、不上传。

- 单元测试：`app/build/test-results/testDebugUnitTest/` 与 `ampp-glass/build/test-results/testDebugUnitTest/`。
- Lint：`app/build/reports/lint-results-debug.html`、`lint-results-release.html`。
- Release AAB：`app/build/outputs/bundle/release/app-release.aab`，当前未配置正式上传签名。
- R8 mapping：`app/build/outputs/mapping/release/`，按工件内部归档。

实际验证范围和公开整理版结果见 [docs/VALIDATION.md](docs/VALIDATION.md)。签名、公开隐私政策、Data safety、账号与设备验收等发布条件仍未完成，当前不提供 Play 上架承诺。

## 目录结构

```text
.
├── README.md                    # 项目说明
├── LICENSE                      # GPLv3 许可全文
├── THIRD_PARTY_NOTICES.md        # 来源、素材替换与第三方许可
├── build.gradle.kts             # AGP / Kotlin 插件版本
├── settings.gradle.kts          # app 与 ampp-glass 模块
├── gradle/wrapper/              # 固定版本与分发 SHA256
├── app/
│   ├── src/main/java/           # UI、存储、编排、BLE、音频与指纹
│   ├── src/main/res/            # 原创几何图片、图标与 Android 资源
│   └── src/test/               # 数据边界、格式与播放策略等 JVM 测试
├── ampp-glass/                  # AM++ / Backdrop 玻璃移植与测试
├── docs/
│   ├── VALIDATION.md           # 实际验证结果与未执行项
│   └── public-assets.json      # 新图稿来源与 SHA256
├── examples/basic.iaglight     # 无音频的简单颜色工程
└── tools/
    ├── play-bundle-check.init.gradle
    └── generate_public_assets.py
```

## 数据保存与权限

- **工程文件**：保存到应用私有目录；导入、导出与分享保留原始 JSON 中的参考及扩展字段。读取失败的坏文件保留，界面会提示有文件无法读取。
- **玻璃设置**：保存到本机 SharedPreferences，可在外观入口调整；关闭玻璃不清除已调参数。
- **备份与迁移规则**：只包含 `arrangements/` 和 `glass.xml`；独立指纹与在线曲库版本不在白名单中，工程原文内嵌的 reference 仍保留。真实系统恢复尚未验证。
- **系统播放音频**：用户每次启动时授权，PCM 在本机处理，参考指纹可保存在应用私有目录；现有代码没有上传录音、工程或设备列表的请求体。
- **在线曲库**：默认向 `https://rrnn.bj.cn/iag-lightapp/library.json` 发起 HTTPS 请求。服务端能接触 IP 与请求头，日志保留和运营信息仍需由服务运营方核实。
- **蓝牙与通知**：依系统版本请求扫描/连接权限；编排播放和音频捕获使用相应前台服务。授权拒绝、撤销与后台流程仍待实机验收。

应用没有登录、支付、广告或分析 SDK。这里的数据流说明用于介绍现有实现，不能代替正式发布所需的隐私政策及 Console 声明。

## 常见问题

| 问题 | 排查方法 |
| --- | --- |
| 搜索不到荧光棒 | 检查蓝牙、附近设备/旧系统扫描定位权限、设备电量与占用状态；确认型号符合既有名称或服务 UUID。 |
| 系统音乐没有电平 | 确认 Android 10+、已同意捕获授权且来源允许被捕获；部分受保护内容不能捕获。 |
| 跟随无法识别进度 | 自动定位需要有效参考指纹；没有参考时只能从当前进度手动跟随，应用不直接读取视频 App 的播放头。 |
| 导入失败 | 检查格式、引用轨道、颜色与时间边界；新外部导入上限 4MiB。先备份原文件，不要覆盖现有工程。 |
| 工程提示无法读取 | 原文件仍保留；恢复/修复或导出损坏文件的专用流程尚未完成。 |
| 安装报签名不一致 | 本地构建使用自己的 debug 密钥，未必与历史 APK 同签名；先保存数据并核对证书，不通过直接卸载规避。 |
| 玻璃效果与参考不完全一致 | 当前 JVM 测试不绘制画面，原版 spring 数值尚未完整恢复，同设备视觉与触感对比未执行。 |
| AAB 构建成功能否直接上架 | 当前 AAB 未配置正式签名，账号、隐私、资料与设备验收仍缺失；结构校验不能代替 Play 审核。 |

## 开发与反馈

- 页面与主要交互：`MainActivity.kt`、`AmUi.kt`、`AmGlassDock.kt` 和 `AmExpandedPlayer.kt`。
- 工程存储与格式：`ArrangementStorage.kt`、`ResolveCues.kt`、`Rrnn.kt`、`FileSafety.kt`。
- 音频与跟随：`CaptureService.kt`、`FingerprintEngine.kt`、`FollowEngine.kt`。
- 蓝牙控制：`LightStickController.kt`；保留已有协议，涉及硬件行为先核对实际 HCI 与设备证据。
- 玻璃手势与渲染：`ampp-glass/`；调整共享动效角色后运行模块测试，设备结论另行记录。

反馈请提供源码提交或版本号、手机型号、Android/ROM 版本、荧光棒型号、复现步骤、错误信息和实际测试结果。分享日志或截图前去掉个人设备地址、序列号与授权信息。没有运行的测试请注明“未执行”。

新图稿已包含在仓库中，构建 Android App 不需要 Python。需要重新生成图片时，运行 `python tools/generate_public_assets.py`，此开发辅助脚本使用 Pillow。

## 第三方项目与许可

| 项目 | 使用部分 | 许可与来源 |
| --- | --- | --- |
| [AM++](https://github.com/Zennmn/AM-plus-plus) | 玻璃导航、迷你条、材质与策略；来源提交 `86762b5` | [GPLv3](ampp-glass/src/main/assets/licenses/AM-plus-plus-GPL-3.0.txt) |
| [AndroidLiquidGlass / Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) | Backdrop 2.0.1 渲染与效果；来源提交 `65ab177e90e5c1d8c62e70cf7755841982da65f6` | [Apache-2.0](ampp-glass/src/main/assets/licenses/AndroidLiquidGlass-Apache-2.0.txt) |

本公开整理版采用 **GNU GPL v3.0（GPL-3.0-only）**，全文见 [LICENSE](LICENSE)。第三方代码保留其版权与许可，移植记录见 [ampp-glass/source-provenance.json](ampp-glass/source-provenance.json) 和 [第三方声明](THIRD_PARTY_NOTICES.md)。

公开版的四张位图和八个播放器图标由几何图形程序生成，未包含旧 Apple Music APK 矢量、来源未核定的品牌图稿、反编译工程或抓包资料。公开代码不代表已经获得特定商标的官方授权；本仓库不是原厂官方应用的发布渠道。

本 README 的章节组织参考 [搞机工具箱 Web 的 README](https://github.com/gem0816-1/gjgjxweb/blob/master/README.md)，功能、命令与验证结论均按本安卓项目重新撰写。
