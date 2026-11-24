# 项目知识背景与迭代指南

[English (default)](PROJECT_CONTEXT.md)

实现基线：v1.8.5。本文描述已发布代码，不承诺未来第三方应用版本始终兼容。

## 项目目标与运行环境

Android-BT-QuickLaunch 将安卓旧手机改造成专用车载语音终端。通过配对的蓝牙小键盘，以实体快捷键启动 AI 助手和用户指定应用，减少触屏步骤。应用作为默认桌面运行，提供横屏四个可配置应用位。

优先目标是可预测的唤醒与启动、可见反馈、离线提醒和有限的后台工作。目标设备是一加 7T、Android 11、Magisk Root。本项目针对特定设备调校，并非适配所有安卓设备的通用桌面。Root 不能保证 Android 或厂商永远不会停止服务。

应用包名为 `com.benbenyu.btquicklaunch`，设备专属键盘配置在 `DeviceConfig.java` 中。其他设备安装时，必须核实授权脚本里的 UID，不能直接照搬。

## 按键、应用启动与语音入口

1. `ButtonService` 是安卓无障碍服务，过滤指定外接键盘的数字键 1/2/3；双击启动对应的前三个应用位，三键同时按下请求清理后台。
2. 服务唤醒手机并向 `MainActivity` 传递 `button_action`。应用位按保存的配置执行：目标包名、可选前置应用、等待时间和自动语音开关。
3. 对启用网络保护的应用，`ProxyGuard` 先检查本地网络、VPN 和 Clash 状态（必要时启动 Clash 后复检）；开启“启动前校验出口节点”（`proxy_require_jp`）时，还会优先经本地 Clash 代理端口探测外网出口国家（`chatgpt.com/cdn-cgi/trace`、`cloudflare.com/cdn-cgi/trace` 或 `ipinfo.io/country`，兼容分应用 VPN 模式），不在安全节点范围内或不通时拦截启动。这些检查仍不能证明账号额度充足。
4. 支持自动语音的应用启用相应开关后，桌面启动应用并发起有时限的无障碍搜索。GPT 启动还使用限定目标包名的 `https://chatgpt.com/voice` Intent。
5. 语音匹配限定在目标应用包名内。Gemini 启动包名为 `com.google.android.apps.bard`，但显示界面可能属于 `com.google.android.googlequicksearchbox`。

目前已集成 GPT、豆包和 Gemini 的语音入口。Grok（`ai.x.grok`）与 Grok Bot（`ai.x.grok.bot`）是两个独立应用，本基线均未适配自动语音入口。安装应用或把应用加入代理列表，不代表自动获得语音适配。

## 当前“按钮识别”的准确含义

无障碍节点提供不同属性：

| 属性 | Android API | 当前语音匹配是否使用 |
|---|---|---|
| 控件资源 ID，讨论中也称无障碍 ID／控件 ID | `getViewIdResourceName()` | 使用，前提是应用暴露已知 ID |
| 无障碍描述 | `getContentDescription()` | 使用，用于入口和通话状态控件 |
| 控件可见文字 | `getText()` | 当前语音按钮匹配没有使用 |
| 可见性、启用状态和边界 | `isVisibleToUser()`、`isEnabled()`、`getBoundsInScreen()` | 使用，用于判断控件条件和位置 |

本项目的“无障碍 ID”和“控件 ID”并非两套独立识别机制，实际指无障碍接口暴露的控件资源 ID。

准确表述是：**通过资源 ID 和无障碍描述匹配**。不能写成“只使用 ID”或“没有用描述”。描述虽然可能包含可读文字，但与控件可见文字属于不同属性；读取无障碍树属性不是 OCR。

| 应用 | 入口识别 | 点击与结束判断 |
|---|---|---|
| GPT | “语音模式／Voice mode”等无障碍描述；启动时还使用限定包名的语音 URL | 优先点击节点或父节点，必要时按识别出的边界模拟手势。当前点击请求被接受即结束搜索；也能通过已有通话控件描述结束搜索。没有豆包／Gemini 同样的点击后确认循环。 |
| 豆包 | `com.larus.nova:id/real_time_call` 或 `com.larus.nova:id/real_time_call_ic_container`；“打电话”描述作为兜底，限定在屏幕上三分之一 | 优先节点／父节点点击，必要时按识别出的边界手势点击或重试。通过可见通话根节点／挂断 ID 或“挂断通话”等描述确认。 |
| Gemini | `com.google.android.googlequicksearchbox:id/assistant_robin_input_voice_chat_button_compose` 或“打开 Gemini Live／Open Gemini Live”等描述 | 按识别到的按钮边界模拟手势，通过“结束 Live 模式”“打开/关闭麦克风”等可见描述确认；检测到输入法窗口时先收起再搜索。 |

ID 和描述属于可替代的匹配条件，不要求两者同时匹配。实际规则见 `ButtonService.isVoiceTrigger()`、`isActiveDesc()`、`find()` 和 `clickVoiceButton()`。

### 坐标用于点击已识别控件

代码没有通过固定像素坐标、截图模板或 OCR 识别按钮。先找到符合条件的无障碍节点，再读取其当前边界，需要时在中心位置发送触摸手势。坐标用于执行点击，而非判断哪个按钮是目标。

豆包和 Gemini 的按钮边界必须在多次观察中至少稳定 350 毫秒。搜索最多持续 15 秒，每轮遍历不超过 512 个节点，最多点击三次，点击间隔至少 1500 毫秒。常规重试间隔为 600 毫秒，无障碍窗口事件可提前触发检查。对这两种适配，点击请求被接受不足以结束流程，后续必须识别到可见的通话控件。

这些控件只能证明进入了通话界面，不能证明音频连接正常或服务端任务完成。较宽泛的通话状态描述仍属于启发式判断，适配新版本时应重新检查。

### 兼容性边界

只移动按钮，且 ID、描述和无障碍暴露保持不变时，通常仍能适配。若改版改变 ID、语言、描述、界面宿主包名或无障碍可见性，可能需要更新规则。豆包的描述兜底还假设电话按钮位于屏幕上三分之一。位置稳定判断可减少旋转时机问题，但不能消除所有页面切换竞争。

当前未实现图像识别、OCR、固定或比例坐标匹配。增加更多界面启发式规则之前，应优先调查官方支持的语音启动 API／深层链接。尚未实现和验证的改进只能标注为计划。

## 蓝牙、提醒与恢复机制

- `HidConnectionManager` 接收 HID、ACL 和适配器广播，并每秒轮询状态作为兜底。Root 回连使用独立执行器，避免阻塞连接操作拖住状态处理。
- 键盘突然断电后，Android 蓝牙协议栈可能要等待链路超时才判定断开；应用轮询无法保证物理断电后 3～5 秒内提醒。
- `RootHidProvider`／`RootHidConnect` 提供受限 Root HID 桥接；Provider 不对外导出，并检查调用方。
- `phone-config/92-btquicklaunch.sh` 在开机时恢复默认桌面、蓝牙、电池优化白名单及无障碍启用状态；约每 30 秒检查服务，修复时保留其他已启用无障碍服务。
- 使用 `dumpsys accessibility` 核实实际绑定；仅设置为启用或存在过期服务连接记录，并不能证明服务正常。
- 提醒使用内置中文语音 WAV 或 AOSP 音效。键盘连接、断开、键盘低电量和手机低电量分别配置；清理成功有独立音效设置。
- 低电量阈值为严格低于 30%，并抑制重复提醒；手机充电时不播报低电量。声音要求响铃模式；静音／振动模式改为振动，语音和提示音分别遵循对应音量通道。

## 桌面反馈与资源占用

- 图标统一使用圆形遮罩。橙色圆环表示检测到对应包名／进程正在运行，不代表应用健康或正在语音通话。交换应用位后，状态仍跟随包名。
- 清理操作强行停止配置／默认目标应用及 Via，排除本桌面和 Clash；并非杀掉 Android 全部后台进程。命令成功后展示派大星、`Apps Cleared` 和可选内置音效。
- 壁纸为内置静态 XML 资源，默认纯黑，可选用户图片。电子书主题覆盖壁纸：普通版使用纸张背景，黑色版使用纯黑和降低亮度的灰阶渲染。
- 内存芯片显示 `ActivityManager.MemoryInfo` 提供的整机占用，计算为 `(totalMem - availMem) / totalMem`，不是本应用占用。可用内存包括可回收内存。桌面处于恢复状态且屏幕可交互时每 10 秒刷新，熄屏／暂停停止轮询。
- 默认关闭 `FLAG_KEEP_SCREEN_ON`（省电策略中提供 `keep_screen_on` 可选常亮开关），交由系统休眠超时息屏。`IdlePower` 在两分钟无操作后调暗桌面，并响应输入、播放等本地事件；`WifiBeaconGuard` 可选在桌面可见时每 60 秒扫描指定 Wi-Fi SSID，连续缺失超时后弹出 5 秒倒计时并触发 `KEYCODE_SLEEP`。静态资源和有限轮询减少不必要工作，不代表零内存或零功耗。
- `NetIndicators` 在右上角状态栏展示 Wi-Fi、蜂窝网络信号与实时上下行网速（`↑`/`↓`）。只要未开启飞行模式且检测到就绪 SIM 卡（或存在已连接的蜂窝网络），即使同时连接 Wi-Fi 或处于纯数据域驻网（`NetworkRegistrationInfo.isRegistered()`），也会显示 0～4 格信号与制式（`5G`/`4G`/`3G`/`2G`）；无服务时显示 0 格灰柱与 `×` 标记。实时网速直接调用 Android 系统底层的 `TrafficStats.getTotalTxBytes()` 与 `TrafficStats.getTotalRxBytes()`，仅在桌面处于前台（`start()`/`stop()`）时每秒采样一次，无需额外权限或 Root。正常进入桌面时会请求一次 `READ_PHONE_STATE` 权限，用于在 Android 11+ 读取 `TelephonyDisplayInfo`（含 5G NSA）及默认数据卡状态。
- 在目标机型的 Android 11 原厂固件（一加 7T，系统数据库 `content://telephony/carriers`）中，已内置三大运营商配置，插卡默认即可使用：**中国联通（`46001`）** 内置 `3gnet`（`default,supl`）及 `ims`；**中国电信（`46003` / `46011`）** 内置 `ctnet`（`default,hipri,ia,supl`）及 `ims`；**中国移动（`46000`）** 内置 `cmnet`（`default,net,supl`）及 `ims`。由于该版系统固件发布早于中国广电（`46015`）2022 年正式放号，原厂 APN 与 IMS 表未收录 `46015`，容易出现 4G 满格但语音域 `OUT_OF_SERVICE`、数据拨号停在 `DATA_DISCONNECTED` 的情况；在系统“移动网络 -> 接入点名称 (APN)”中手动添加广电 APN（名称：`中国广电`，APN：`cbnet`，APN 类型：`default,supl`）并选中即可正常上网。

卸载系统动态壁纸组件、安装防火墙等仅对测试手机的改动，不属于桌面源码功能，安装本 APK 不会自动复现。

## 源码索引与验证依据

建议先阅读[架构](ARCHITECTURE.zh-CN.md)、[当前状态](STATUS.zh-CN.md)和[语音提醒](VOICE_ALERTS.zh-CN.md)。

| 模块 | 主要源码 |
|---|---|
| 桌面、设置、应用位、启动取消 | `MainActivity.java` |
| 实体按键、语音目标隔离、识别、点击和重试 | `ButtonService.java` |
| 网络／代理前置检查 | `ProxyGuard.java` |
| 蓝牙状态与恢复 | `HidConnectionManager.java`、`RootHidProvider.java`、`RootHidConnect.java` |
| 事件设置与离线音频 | `ReminderEvent.java`、`ReminderAudio.java`、`OfflineVoiceAlerts.java`、`SystemTonePlayer.java` |
| 内存与空闲生命周期 | `MemoryIndicator.java`、`IdlePower.java` |
| 备份校验与诊断 | `ConfigBackup.java`、`AppLog.java`、`HomeApplication.java` |

本地排查事件包括 `HID_ACTION`、`LAUNCH`、`VOICE_REQUEST`、`VOICE_CLICK_ATTEMPT`、`VOICE_ALREADY_ACTIVE` 和 `VOICE_TIMEOUT`。豆包／Gemini 应结合点击记录、已识别通话控件和实际界面判断；不能把 `accepted=true` 当成通话入口成功。旧版 `VOICE_CLICKED` 也只证明点击请求被接受。

v1.7.0 设备检查覆盖语音目标隔离／当前文案、进程匹配、事件设置、内置音效 ID、备份校验、HID 状态和提醒策略。手工界面测试作为补充；仅匹配规则测试通过，不足以证明兼容第三方版本。

## 后续迭代要求

1. 复现问题时记录已安装版本、应用位包名／语音开关、服务绑定及相关本地日志事件。
2. 检查目标应用当前无障碍树，记录 ID、描述、宿主包名、可见性和边界；不提交私人对话或手机转储。
3. 优先使用稳定资源 ID 和限定范围的无障碍描述，区分麦克风听写与完整语音通话；不要改成跨任意应用匹配“语音”等泛化文字。
4. 保留启动取消、遍历上限、有限重试和已在通话状态的判断；未经明确设计，不改变各适配器的不同处理方式。
5. 验证冷启动、热启动、已在通话、旋转和启动中断；共享语音逻辑修改后复查 GPT、豆包和 Gemini，测试结束后退出通话。
6. 新增设置应同步备份校验，保留已有用户偏好，生命周期结束时释放监听器和定时任务。
7. 开发前阅读并遵守 [CONTRIBUTING.md](../CONTRIBUTING.md) 和 [AGENTS.md](../AGENTS.md)。两份 README 及所有中英文文档必须在同一提交同步更新；不提交密钥、工具缓存、APK、手机日志或备份。
8. 使用原签名密钥构建，统一递增版本标识并校验签名；APK 作为 Release 附件发布，不提交源码仓库。Windows 中文路径可能需要 ASCII 盘符映射，以兼容旧资源打包工具。

不能把预期设计默认为已实现行为。识别规则或支持目标变化时，必须更新本文。
