# 帝豪 GL 2019 音频行为采集

## 最新实车结论（2026-11-07）

用户确认 GK19Play 优化 release 测试通过。验证组合为 **2019 款吉利帝豪 GL / Android 4.4.2 / iPhone 13 Pro**：音乐播放约 1–2 秒自动暂停的问题已修复；Spotify、高德导航语音及高德内音乐、播放/暂停/恢复正常，微信录像结束后音乐可自行恢复。原生设置入口保留，但该固件无法访问。本结论仅覆盖用户报告的组合与场景，不扩展为全部车型、长期性能、Siri 或麦克风验收。

下文保留各轮构建时的“待实车验证”作为历史记录，当前验收状态以本节为准。APK 和诊断原文仅保留本地，不随源码提交到 fork。

原始诊断版仅增加观测与导出，不更改焦点策略。2026-11-07 新增下文 A/B 焦点对照包；两档均不强制恢复播放、不关闭原厂蓝牙。代码基于仓库当前 main；不代表已移植到 C7A-R2 发布 APK，新对照包尚未通过实车验证。

## 实车复现

请停车后操作，避免行车中查看日志或设置。

1. 记录车机 Android 版本、应用版本、连接方式、手机 iOS 版本与音乐 App。
2. 进入 CarPlay，播放音乐，等待自动暂停。记下大致时间；之后手动播放、暂停各一次，方便建立对照。
3. 如方便，在相同条件下分别测试原车蓝牙音乐连接/断开两组；记录每组条件，不要同时修改音频通道、焦点或缓冲参数。再触发一次导航语音作为对照。
4. 回到 DiPlay 设置的诊断区域，选择导出磁盘，核对 U 盘实际挂载路径；若插盘后未出现，刷新磁盘。文件位于所选盘的 `DiPlay-diagnostics`，成功提示显示完整路径。
5. 等待保存成功后再拔盘；带回 TXT 报告及上述测试条件。重启/重连会轮转日志，请尽快导出。

## 报告字段

- `AUDIO_EVENT uptimeMs=...`：事件发生时的单调时钟，外层时间为墙上时钟；用于比对先后顺序。
- `mediaCommand`：DiPlay 媒体按键/控制回调。`phase=received` 表示本应用接到操作；`destination=iphone queued=true` 只表示成功入队，不表示手机已收到或执行。
- `iphonePlayback` / `iphoneNowPlaying`：iPhone 上报播放状态与进度，不是声音已实际输出的证明；不记录歌名、歌手、专辑。
- `focus owner=... change=...` / `Audio: ... focus ...`：本应用监听器收到的焦点事件与请求结果；`1` 获取、`-1` 丢失、`-2` 临时丢失、`-3` 可降低音量。看不到系统中其他应用的完整焦点栈。
- `Audio: stream start/stop`、`track play`、`track pause reason=bufferDrained`：流与音轨生命周期；缓冲暂停不等于收到媒体暂停指令。
- `audio stats`：已有的收包、包间隔、写入错误、播放头与缓冲统计，用于区分停止收包和输出停滞。
- `broadcast a2dpConnection/a2dpPlaying`、`sco`、`audioBecomingNoisy`：公开系统广播，可能受固件/权限限制。A2DP 连接状态 `0/1/2/3` 为断开/连接中/已连接/断开中，播放状态 `10/11` 为播放中/未播放。这些标准广播未必覆盖车机 A2DP Sink 或原厂私有蓝牙。
- `snapshot`：每 5 秒记录设备全局音频 mode、musicActive、旧式蓝牙/有线路由标记、音乐音量；不是本应用精确路由或蓝牙连接证明。

日志使用已有脱敏、异步有界队列和轮转文件，不录制 PCM/声音、不新增系统 logcat 权限。导出前最多等待 2 秒排空队列，报告注明是否排空及进程累计丢弃队列条目数。持续写入或存储故障时不能保证日志零丢失；缺少事件不能直接排除原厂干预。

## 其他入口边界

热点按钮直接复用 `CarHotspotTools.start()` 请求系统 API，并非仅跳转设置；能否成功仍取决于固件权限。旧诊断包的 Android 设置入口曾尝试通用设置 action，可能被原厂车机设置接管；下文 `GL19-A-OPUS-SW-TOOLS` 已改为仅尝试 `com.android.settings` 的显式组件及同包限定 action，不解禁受保护组件，也不修改系统分区。旧 Android 的次级盘写入可能被固件限制，导出失败不会偷偷改存到其他磁盘；有系统文件选择器时可改用该入口。

## GL19 安装包构建

```sh
ANDROID_HOME=/path/to/android-sdk sh ./gradlew :mobile:assembleDebug \
  -Pdiplay.gl19Diagnostics=true -Pandroid.injected.testOnly=false \
  --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m
```

该开关仅影响显式 GL19 构建：包名 `com.shihab.diplay.gl19diag`，名称“DiPlay GL19 诊断”，版本 `0.2.10-gl19-diag`，最低 API18（Android 4.3，覆盖 4.4/API19），仅打包 ARMv7。`compileSdk/targetSdk=37` 不表示最低运行版本为 37；不要改成 automotive 模块，也不要给旧 32 位车机打仅 ARM64 的包。GL19 开关为 native 使用 NDK r23c/API18，而非默认 NDK r28 的 API21 平台下限。

使用本地 debug 签名，保留适配 API18/19 的 v1 签名；`testOnly=false` 允许普通安装器安装，不要求 adb `-t`。与原 `repair1` 包并存，不共享设置或认证数据；实车测试时不要同时运行两套 CarPlay 服务。未连接实车，构建成功不是硬件兼容验收。

未指定 `DIPLAY_AUTH_ASSETS_DIR` 时，输出**不含 CarPlay 认证身份**，可以测试设置入口、热点按钮和日志导出，但无法建立新的 CarPlay 会话，因此也不能用它验证实际音乐播放日志。完整连接测试需提供自己有权使用的外部认证目录（`offline-mfi/identity.pk8`、`offline-mfi/certificate.p7b`），保留相同参数改用 `:mobile:assembleStandaloneDebug`。不会自动从现有发布 APK 抽取身份，也不覆盖原包。

### 2026-11-06 本机构建核验

- 安装包：`mobile/build/outputs/apk/debug/DiPlay-GL19-Android43plus-ARMv7-tools-only-20261106.apk`，11,524,809 字节。
- SHA-256：`350a0d3c49fc31e480ed27e256db7e580a628adbaf76e6e7ff2e3d047d0ae63c`。
- 已核验包名/版本、minSdk18、仅 armeabi-v7a、无 testOnly、native 自动提取、ZIP 完整性与 4 字节对齐；apksigner API18/19 范围验证通过，v1/v2 签名有效。三个最终打包 SO 均为 ELF32/ARM，Android note 为 API18、NDK r23c。
- 未注入认证资产，不能用于真实 CarPlay 音乐复现；无连接的 adb 设备/可用模拟器，本轮没有执行安装、冷启动或实车验证。

### 2026-11-06 完整认证车测包

按用户明确要求，复用仓库自带 `versions/P21-20261008/DiPlay-H52-P21-OpusSoftwareHudGate-test.apk` 的两项 `assets/offline-mfi` 文件。仅提取这两个文件到 Git 忽略的 `.private` 目录，通过 `DIPLAY_AUTH_ASSETS_DIR` 显式注入后执行 `:mobile:assembleStandaloneDebug`；未复制旧包代码，也未变更认证逻辑。

- 文件：`mobile/build/outputs/apk/debug/DiPlay-GL19-Android43plus-ARMv7-CarPlay-test-20261106.apk`，11,525,897 字节。
- SHA-256：`3f88221b2bebc0fef9e657daa2836c6d0bfa34dd1116fdadf80acde7506b349d`。
- 与工具包相同包名 `com.shihab.diplay.gl19diag`、版本 `0.2.10-gl19-diag` / code29、同一本地 debug 签名；可作为同包更新安装。与原 `repair1` 并存，原包设置不会自动迁移。
- 打包后两项认证资产与 P21 原始条目逐一摘要对比一致；使用生产 `LocalMfiAuthenticationClient.load` 成功加载，并通过三组实际挑战签名/验签及篡改挑战拒绝验证。此项在主机 JVM 执行，不是 Android4.4 实机验收。
- 包内仅 ARMv7、minSdk18、无 testOnly、v1/v2 签名校验、ZIP 完整性与 4 字节对齐检查通过。三项 native 库使用此前已核验的 NDK r23c/API18 构建产物。
- 此包已具备项目所需本地认证输入，可用于后续 CarPlay 连接测试；不表示已完成真实 iPhone 握手或车辆连接验证，也不保证实验性身份被所有 iPhone/iOS 接受。实车测试时先退出原 DiPlay，避免两套服务竞争。

## 2026-11-07 A/B 音乐暂停对照包

两包以相同源码和诊断增强构建，只通过 `-Pdiplay.gl19AudioTest=a` / `b` 选择策略。不改 AAC 解码、缓冲、音频通道或渲染器焦点策略；不自动续播。保留之前的热点、系统设置入口和 U 盘导出功能，显式注入相同的本地认证资产。

| 项目 | A 档 | B 档 |
| --- | --- | --- |
| 应用名 | DiPlay GL19 A 焦点测试 | DiPlay GL19 B RCC测试 |
| 包名 | com.shihab.diplay.gl19diaga | com.shihab.diplay.gl19diagb |
| 版本 | 0.2.10-gl19-a / code30 | 0.2.10-gl19-b / code30 |
| API18–20 媒体按键焦点申请 | 跳过 | 跳过 |
| RemoteControlClient 注册与状态发布 | 保留 | 不注册，不发布 |
| 媒体按键广播接收器 | 保留 | 保留；原车按键是否仍投递取决于固件 |
| 日志标识 | GL19-A-NO-KEY-FOCUS | GL19-B-NO-KEY-FOCUS-NO-RCC |

交付目录为仓库根目录 `build/gl19-audio-ab-20261107/`，不再放在 `mobile/build/outputs/apk/debug/` 内：Gradle 打包会清理该任务的输出目录，包括手工放入的旧 APK；此前记录的该目录路径是历史构建记录，不保证仍存在。

- A：`DiPlay-GL19-A-NoKeyFocus-ARMv7-CarPlay-20261107.apk`，11,514,601 字节，SHA-256 `8b00c4c4707951e7fc01b5a8308d643eb8a0000b19ba72fd0316ef3b763d6d16`。
- B：`DiPlay-GL19-B-NoKeyFocus-NoRCC-ARMv7-CarPlay-20261107.apk`，11,514,597 字节，SHA-256 `a32aa64bf8db59eefb81efaa653c9285f8489e45d7384b29e1a95594adbac1fd`。

导出文件名分别以 `DiPlay-GL19-A-NO-KEY-FOCUS-诊断报告-` 和 `DiPlay-GL19-B-NO-KEY-FOCUS-NO-RCC-诊断报告-` 开头，仍保存到所选磁盘的 `DiPlay-diagnostics`。报告头、每个会话头及音频事件包含 `GL19_AUDIO_TEST=` 标识；内部轮转文件名仍是 `diplay.log` / `previous*.log`，但两包私有数据目录独立。

新增日志：

- `focus owner=legacyMediaKeys request=skipped reason=audioExperiment`：两档确实跳过媒体按键模块的焦点请求。
- `legacyRcc registration=skipped`：仅 B 档跳过 RCC。
- `sessionAudioConfig`：会话启动时的渲染器焦点开关、吉利音频路由开关。导出头的设置值仅是导出时保存值，不代表正在运行的配置。
- `Audio: focus owner=renderer request ... result=...`：渲染器旧式焦点申请结果。
- `Audio: modesChanged`：固定白名单内、限制长度的资源/应用状态枚举，不记录整个协议消息，也不直接推断枚举的归属含义。
- `Audio: teardown received` / `Audio: streamStopRequested reason=...`：区分手机 TEARDOWN、SETUP 替换和会话关闭。

### 测试顺序

1. 停车操作。可同时安装，但测试前应在应用管理中强行停止原诊断包及另一档；不要让多套 CarPlay 服务同时运行，关闭不测试包的开机启动/自动连接。
2. 两个独立包不继承旧包或彼此的设置与配对记录。分别完成连接配置；保持手机、音乐 App、曲目、原车媒体页面、渲染器焦点开关、音频通道与缓冲等条件一致，不要为了连接改变音频选项。
3. 先测 A：先不打开 QQ，连续播放至少 2 分钟；若出现暂停，手动播放复现 3 次，立即导出 A 日志。若正常，再测试 QQ 拍照后的恢复，记下时间。
4. 停止 A，按相同条件测试 B 并导出。B 可能影响方向盘媒体按键/原车播放状态联动，请另记结果。声音是否正常与界面是否显示播放要分别记录。
5. 保留两份原始 TXT，不必手工改名；反馈每档是否仍约 2 秒暂停、导航语音是否正常，以及做过的手机/原车操作。

### 核验范围

- A 完整构建/测试通过，B 构建与 common 测试通过；common 180 项通过，shared 458 项中 457 项通过、1 项跳过，0 失败/错误。shared 的源码与 native 在两档相同。
- 最终两包逐一核验清单包名/应用名、DEX 中构建档位及策略调用、minSdk18、仅 ARMv7、无 testOnly、native 自动提取、ZIP 完整性与 4 字节对齐；v1/v2 签名有效，API18–19 范围 v1 验签通过。三项 native 库在 A/B 完全一致，Android note 标示 API18、NDK r23c。
- 两包认证资产逐字节与显式输入对比一致。未连接 adb 设备，未做本轮真实 Android4.4 安装、启动、蓝牙/CarPlay 或音乐验收；这是定位问题的测试包，不是已确认修复版。

## 2026-11-07 A 档导航及工具修复包

### 日志依据与修改

实车反馈 A/B 两档 Spotify 可以正常播放，但高德导航及高德内音乐无声；微信录像后能延时恢复。两份报告中合计 30 条导航 Opus 统计均为 `outputBuffers=0`、`totalWrittenFrames=0`、`head=0`，而收包持续；AAC 音乐的输出及播放头正常增长。因此本版保留 A 档跳过媒体按键焦点申请、保留 RCC 的策略，不增加强制续播，不调整原车蓝牙和渲染器焦点。

- **旧系统 Opus 下行解码**：参考仓库 P21 的软件解码方案，API18–20 使用 Concentus 1.0.2，每条渲染流持有独立解码器，再进入原 PCM/AudioTrack 路径。支持合法短静音包，坏包有限记录且不阻断后续解码。AAC 与 API21+ 的解码路径保持不变。
- **原生设置选择**：首页 / Wi-Fi / 无线与网络 / 应用管理 / 开发者选项 / 关于设备。先尝试 `com.android.settings` 显式组件，再尝试同包 action，不再回退到不限定包的通用设置。报告记录最近一次目标、解析结果和请求结果；`launchRequested` 不证明页面实际显示。OEM 若替换原生包、重定向首页、移除页面或施加权限保护，普通应用无法保证进入；可试 Wi-Fi / 应用管理并导出日志。
- **打开 U 盘 / 浏览磁盘**：首页及权限帮助区域增加入口，选择挂载位置后可看文件夹、返回上一级、查看文件名、只读预览 UTF-8 文本前 64 KiB；每目录最多显示 500 项。其他类型不执行、不安装、不播放。磁盘选择仍区分主存储与其他挂载位置，不声称每个位置都是 U 盘。
- **先浏览再导出**：日志导出选盘后可以先浏览内容确认 U 盘，浏览器提供“导出报告到此磁盘”。报告仍写到该盘根目录的 `DiPlay-diagnostics`，不是当前浏览子目录；只读盘禁用导出。后台读目录，检查挂载状态与根目录边界，拔盘不自动重建根路径。

### 安装与标识

- 文件：`build/gl19-a-opus-tools-20261107/DiPlay-GL19-A-OpusSoftware-Tools-ARMv7-CarPlay-20261107.apk`。
- 应用名“DiPlay GL19 A 导航修复”；包名 `com.shihab.diplay.gl19diaga`；版本 `0.2.10-gl19-a-opus-tools` / code32。与原 A 包同包名、同签名，可覆盖安装保留 A 的设置与配对；不迁移 B 或原始诊断包的数据。测试前强行停止其他 DiPlay 包并关闭其自动启动，避免服务竞争。
- 日志唯一标识：`GL19-A-OPUS-SW-TOOLS`。导出文件名：`DiPlay-GL19-A-OPUS-SW-TOOLS-诊断报告-时间.txt`。
- 11,818,638 字节；SHA-256：`55e1de8ec1fb92ef5e13f34af5ff676cf046bd92157d6fd33558d734752b4540`。
- 复用前次用户授权的外部认证输入，仅注入所需两项资产，不改变认证逻辑，不把密钥加入源码。

构建参数：`DIPLAY_AUTH_ASSETS_DIR=<私有认证目录> sh ./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :mobile:assembleStandaloneDebug -Pdiplay.gl19Diagnostics=true -Pdiplay.gl19AudioTest=aopus -Pandroid.injected.testOnly=false --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m`。需设置 `ANDROID_HOME`。

### 本地核验与实车待验

- 完整构建通过；common 184 项全部通过，shared 465 项中 464 项通过、1 项跳过，0 失败/错误。新增测试覆盖真实编码生成的 mono/stereo Opus、短静音/坏包恢复、独立实例、导航及语音下行渲染、设置组件候选、磁盘排序/预览上限/越界与拔盘。渲染测试使用 Robolectric SDK28 并模拟 API19 分支，不等于真实 Android4.4 系统测试。
- 清单最低 API18 / 仅 `armeabi-v7a` / 无 `testOnly=true` / native 自动提取；v1/v2 签名、API18–19 验签、ZIP 完整性及 4 字节对齐通过。签名与原 A 相同；三项 native 与原 A 逐字节一致，ELF32 ARM、NDK r23c/API18；认证资产与显式输入逐字节一致，Concentus 许可随包提供。
- 未连接 adb 设备，未完成安装、冷启动、原生设置显示、磁盘访问及真实 iPhone/车辆音频验收。下行 Opus 修复不包含麦克风 Opus 编码，不宣称 Siri/通话麦克风问题已解决。
- **请停车测试**：先确认 Spotify 连续播放，再在音乐中触发高德导航播报、单独触发高德内音乐；记录是否恢复及播报后音乐状态。再测试微信录像结束后的恢复时间。最后分别尝试原生设置首页、Wi-Fi、应用管理，浏览 U 盘中一个已知目录，再从该盘导出带新标识的 TXT。记录操作时间；导航故障时检查 `backend=concentus-software`、`decoder first output`、`first PCM` 及输出/播放头是否增长。

## 2026-11-07 GK19Play 优化 release 包

### 本轮实车反馈与性能依据

用户确认上一版导航语音、高德内音乐、Spotify、播放/暂停/恢复均正常，微信录像后音乐自行恢复。对应报告 `DiPlay-GL19-A-OPUS-SW-TOOLS-诊断报告-20261107-010913-792.txt` 中 5 次软件 Opus 首帧解码均有 PCM 输出，和反馈一致。本版保持 A 档焦点/RCC、软件 Opus、AAC、视频分辨率与帧率设置不变，不新增强制续播。

卡顿不能仅归因于 debug：上一版清单确实 `debuggable=true`，但这不表示实际连接了调试器。报告使用 Freescale H.264 硬解，01:04:42.191 和 01:07:18.024 两次出现 `video backlog exceeded 250 ms` 并等待关键帧；66 条视频统计的接收帧率均值约 19.6 fps、范围 11.0–30.6 fps，**这些不是屏幕实际显示 FPS**，也没有 CPU/GC/Wi-Fi 性能采样，暂不能锁定瓶颈。

原生 Wi-Fi 设置三种候选均为 `missing`，只能确认当前跳转找不到页面，不能仅凭此区分组件禁用、删除或 OEM 改名。本版按用户要求保留入口，不继续绕过系统限制。

### 发布版调整

- 新开关 `-Pdiplay.gl19Release=true` 要求同时指定 `diplay.gl19Diagnostics=true`、`diplay.gl19AudioTest=aopus`，仅影响 GL19 优化交付，不改变其他车型默认 release 配置。
- 真实 `release` variant，`debuggable=false`，启用 AGP/R8 代码优化、压缩和混淆及资源优化；移除 Android Log.v/d/i 调用，保留 warning/error 与文件诊断。保留 API18/19 Surface 的反射字段/方法、JNI 名称及异常构造、认证算法所需类型，并保留高版本 API 隔离层，防止跨层内联破坏旧系统。
- 约占本次日志一半的 950 条逐包 iAP tx/rx 日志在发布档位不再进入 UI/文件处理；连接失败、播放状态、焦点、解码、音视频统计及恢复事件仍保留。普通会话日志改用既有有界异步写入器，避免这些日志在主线程同步开关文件。没有关闭整个日志导出。
- 桌面名称、首页/连接页/关于页标题与前台服务通知改为 **GK19Play**；旧项目授权、署名、协议设备身份及用户设置键保留，不做不必要的全仓字符串替换。
- 日志标识 `GK19PLAY-RELEASE-A-OPUS-SW`，`buildProfile=release-opt`；文件名 `GK19Play-GK19PLAY-RELEASE-A-OPUS-SW-诊断报告-时间.txt`。磁盘目录仍为 `DiPlay-diagnostics`，避免改变既有导出位置。

### 安装包与核验

- 文件：`build/gk19play-release-20261107/GK19Play-Release-ARMv7-CarPlay-20261107.apk`。
- 包名保持 `com.shihab.diplay.gl19diaga`，版本 `0.2.10-gk19play-release` / code33，签名证书与已通过车测的 A 包相同，可覆盖更新保留设置与配对。使用旧本地 debug **签名证书**仅为更新兼容，不表示启用 debug **运行模式**，也不是新增生产发布身份。
- 最低 API18 / Android4.3，覆盖目标 Android4.4.2/API19；仅 ARMv7；NDK r23c/API18。已注入同一授权认证输入并逐字节核验，认证策略不变。
- 5,497,537 字节（约 5.24 MiB），较旧 11,818,638 字节缩小 53.5%；体积缩小不代表同等幅度性能提升。SHA-256：`30ae85d0ccbcbed4417db91b236a86d2253e350dd5d50f103fd8eb921273d02a`。
- 构建及 release vital lint 通过；common 单测 187 项通过，shared 464 项通过、1 项跳过，0 失败/错误。库单测任务为 `testDebugUnitTest`，本轮注入 `GL19_RELEASE=true` 测试发布策略，不冒充 R8 后 APK 的实机测试。
- 已核验 non-debuggable、无 testOnly、包名/版本/名称、ARMv7、native 自动提取、ZIP 完整性、4 字节对齐、v1/v2 及 API18–19 验签、认证/许可文件、优化后 DEX 的旧 Surface 反射入口和软件 Opus 分支；调试清单中的探针不再注册。混淆映射留在同目录 `mapping.txt` 供后续分析崩溃。
- 无 adb 设备；新 release 尚未做安装、冷启动或车测性能验收。先覆盖安装，在与上一版相同设置下比较滚动/点击/地图拖动，再回归导航、音乐及微信录像恢复并导出新标识日志。若仍卡顿，需继续区分传输、解密、解码/显示和 CPU/GC，不能声称换 release 已根治。

构建示例（SDK/认证目录自行设置）：

```sh
DIPLAY_AUTH_ASSETS_DIR=<私有认证目录> sh ./gradlew \
  :common:testDebugUnitTest :shared:testDebugUnitTest :mobile:assembleStandaloneRelease \
  -Pdiplay.gl19Diagnostics=true -Pdiplay.gl19AudioTest=aopus -Pdiplay.gl19Release=true \
  -Pandroid.injected.testOnly=false --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2048m
```
