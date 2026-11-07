# 公开整理版验证

源码基线：v0.9.21/code31；公开整理日期：2026-11-07。

本记录针对本仓库的公开源码副本单独执行，原工作目录的历史测试结果未代替本次运行。
源码含原创几何图稿替换、固定个人设备地址移除与公开显示名调整，不能拿历史 APK 的哈希核验本次构建。

## 工具链与实际命令

JDK21.0.12.1、Gradle9.7.1、AGP9.3.2、Kotlin/Compose插件2.4.10；min26/target36/compile37、JVM目标17。

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:bundleRelease :app:testDebugUnitTest :ampp-glass:testDebugUnitTest :app:lintDebug :app:lintRelease -I tools/play-bundle-check.init.gradle checkPlayBundle --offline --console=plain --rerun-tasks
```

**BUILD SUCCESSFUL in 2m 28s；193 actionable tasks: 193 executed。** 未跳过失败测试，未加新的 Lint baseline 或 suppress。

| 检查 | 实测结果 |
| --- | --- |
| Debug / Release / AAB | 均构建成功，Release 开启既有 R8 与资源压缩 |
| App JVM | 72 项，0失败/0错误/0跳过 |
| ampp-glass JVM | 61 项，0失败/0错误/0跳过；包含相位机与拖拽释放回归 |
| Lint Debug / Release | 各 0错误、22警告、7提示；警告仍待后续按影响处理 |
| AAB 结构 | bundletool validate、Manifest 与 config 导出成功；包名 com.iag.lightstick、code31/v0.9.21、min26/target36、非debuggable、HTTPS-only |
| AAB 签名 | JAR 无签名项；jarsigner 实际输出 `jar is unsigned`，不是正式上传包 |
| 16KB 静态 | 四 ABI 的 graphics-path ELF PT_LOAD 段满足16KB条件；AAB配置PAGE_ALIGNMENT_16K；Debug/Release APK 的 `zipalign -c -P 16 4` 均退出0 |
| 导入示例 | 独立 Java 探针调用本次编译的真实 Kotlin 解析器，成功加载1轨3事件；0/1000/2000ms选中事件和3000ms结束边界均符合预期 |
| ADB | 实际执行 `adb devices -l`，退出0，在线设备列表为空 |

机器摘要见 [validation-results.json](validation-results.json)。原始 XML、SARIF、构建日志与本地产物留在发布者的本地证据目录，未随源码上传个人路径和设备资料。

## 本地产物哈希

这些是本次构建的验证产物，未发布为 GitHub Release，也未覆盖原工作目录的旧 APK。

| 工件 | 字节 | SHA256 |
| --- | ---: | --- |
| Debug APK | 17971316 | 51ee4f86f112f022d8476b05e472146affb344e1c3461b88cdda8df8df73c929 |
| unsigned Release APK | 1726279 | e3332e21ecb0341895d9b9addd383228e442efd7bdcae82582ab6bb0a383a1ca |
| unsigned Release AAB | 3728355 | c2ab111e7bac33948e5cbf9fb0f829da3dfc73116394169fa36d7adcb3fb29af |

## 发布门槛与未执行项

- **已满足**：上述本地构建、133项JVM、Lint、AAB结构与16KB静态检查。
- **不符合**：正式上传签名、Play账号/生产权限、公开隐私政策与App入口、Data safety等既有发布条件仍缺失。
- **待验证 / 未执行**：API26/36/37、>=600dp、16KB设备运行、安装/启动、BLE/HCI、系统音频授权/撤销、文件分享、备份恢复、旋转/进程重建、酷安同设备视觉/触感与帧时间/内存/功耗；本轮无在线设备。
- **不适用**：当前没有登录/注册与应用内购买功能，相应账号删除/Billing能力未引入。
- **未执行**：Play Console、正式发布与提交当天的官方实时政策复核；本结果不代表Google Play审核通过。

设备可用后先执行 `adb devices -l`，核对包名、签名和用户数据，再按兼容矩阵验收。BLE 先核已有 HCI 证据，不向未知设备发送猜测指令。
