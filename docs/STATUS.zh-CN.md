# 项目状态

[English (default)](STATUS.md)

版本 1.8.4，包名 `com.benbenyu.btquicklaunch`。目标设备：一加 7T（Android 11、Magisk）。

## v1.8.4 更新说明

- 在顶部状态栏 VPN 图标旁新增实时代理节点区域展示（如 `日本`、`美国`、`英国`、`新加坡` 等）：代理开启时显示当前出口国家中文名称（位于安全节点范围内显示绿色，超出范围或节点不通时高亮红色提示），点击可立即刷新节点状态。

## v1.8.3 更新说明

- 在 `ProxyGuard` 与 `MainActivity` 中新增 **“启动前校验出口节点（防飘号）”** 与 **“安全节点范围”**（默认仅允许 `日本 (JP)`，支持在设置中多选美国、新加坡、台湾、香港、韩国、英国、德国等常用区域）：启动受保护的海外 AI 应用前，依次校验本地 Clash VPN、外网连通性（优先 `chatgpt.com/cdn-cgi/trace`）与出口国家码是否在安全节点范围内；若节点不通或不在允许区域，直接拦截并提示一键打开 Clash 切换节点。

## v1.8.2 更新说明

- 默认关闭 `FLAG_KEEP_SCREEN_ON`（改为省电策略中的 **“桌面保持屏幕常亮”** 可选开关，默认关闭），无论是否插电或直供电，不用时均交由系统自动息屏锁屏省电。

## v1.8.0 封版说明
- 改进 `NetIndicators`：在非飞行模式且检测到可用 SIM 卡时，持续展示蜂窝信号强度（0～4 格）与网络制式（`5G`/`4G`/`3G`/`2G`），支持与 Wi-Fi 共存以及纯数据域驻网（语音域 `OUT_OF_SERVICE` 但数据域 `IN_SERVICE`）。
- 新增 Android 11 `TelephonyDisplayInfo`（5G NSA/SA）与 `ServiceState.getNetworkRegistrationInfoList()` 兜底、默认数据卡解析、无服务 `×` 标记，以及基于系统底层 `TrafficStats` 的实时上下行网速显示（桌面可见时每秒采样刷新）。
- 在 `MainActivity` 中补充首次进入桌面时的 `READ_PHONE_STATE` 运行时权限申请及授权后即时刷新。
- 将右上角设置齿轮的点击行为改为弹出带图标的 2 选 1 菜单（**系统经典桌面** / **应用设置**），方便快捷切换回系统经典桌面。
- 新增 `WifiBeaconGuard`，并在 **通用设置 -> 省电策略** 下整合两条策略：（1）无动静 2 分钟屏幕变暗；（2）Wi-Fi 离开自动锁屏休眠（桌面每 60 秒扫描附近 SSID，无需连接；支持从附近 Wi-Fi 列表点选；支持 2/3/5 分钟超时；超时且无媒体播放或近期操作时先弹出 5 秒倒计时确认，无响应再执行 `KEYCODE_SLEEP`；相关设置已接入 `ConfigBackup`）。
- 在电池图标旁新增 `PowerIndicator`，支持显示实时功耗（`W`）、电流（`mA`）、电压（`V`）与电池温度（`°C`，≥42.0°C 高温变色提醒），并补充 `PowerIndicatorChecks`。
- 新增右上角 VPN/Clash 代理状态、无障碍按键服务绑定状态以及蓝牙音频（`A2DP`/`SCO`）连接状态图标。
- 在 **通用设置 -> 状态展示** 中新增 11 项独立开关（移动网络信号、Wi-Fi、上行下行速度、VPN 代理、按键服务、蓝牙音频、蓝牙、HID 蓝牙与电量、功耗、电池温度、内存占用），关闭后同步停止对应轮询并已接入 `ConfigBackup`。
- 新增 `WifiBeaconChecks` 与 `NetIndicatorsChecks`（与 `ProcessStateChecks` 均通过）；已在一加 7T 真机（中国广电 `46015` 4G）验证信号格数与 `dumpsys telephony.registry` 一致（4/4 格），补充 `cbnet` APN 后实测 `ping` 平均延迟 33.0 ms、0% 丢包、下行约 2.78 MB/s，并截图确认实时 `↑`/`↓` 网速显示正常。

## v1.7.9 更新说明

- 将清理成功提示层文字改为英文 `Apps Cleared`。

## v1.7.8 更新说明

- 将清理成功提示层文字从“搞定了”改为“后台清理成功”。

## v1.7.7 更新说明

- 将清理成功后的派大星“搞定了”提示层停留时间从 3 秒（+200ms 淡出）缩短为 700 毫秒快速闪现（+150ms 淡出），配合提示音即时反馈、不遮挡后续操作。

## v1.7.6 更新说明

- 移除 `MainActivity.onCreate` 中硬编码的开机自启 ChatGPT 逻辑（`boot_count` 判断），手机重启后安静停留在桌面，不再在未唤醒的情况下自动跳入 ChatGPT；若开启了“桌面启动时自动开启 Clash”，仍会正常在后台启动代理。

## v1.7.5 封版说明

- 在 **通用设置 -> 软件更新** 中新增应用内自动更新（`AppUpdater.java` + `UpdateApkProvider.java`），默认从 **GitHub Releases**（`Ronnie2026/Android-BT-QuickLaunch`）检测并下载安装包，支持手动切换更新源，失败时不自动切源。
- 启动自动检查延迟 6 秒在后台线程执行（每 6 小时最多一次），不影响冷/热启动速度。
- 下载完成后先通过 `PackageManager.getPackageArchiveInfo` 校验安装包有效性再拉起系统安装器；检测或下载失败时明确提示并支持一键打开 GitHub Releases 页面手动下载。
- 修复 `HidConnectionManager` 低电量语音滞回防重与 `su` 输出重定向，补充 `MainActivity` 后台回调生命周期保护，并修复浅色电子书主题下的文字对比度。

## v1.7.0 验证

- 内置静态 XML 壁纸，默认黑色，保留系统图片选择；修复“石墨”错误打开图片选择器的索引问题。
- 新增黑色与普通护眼电子书主题，兼容旧纸白主题偏好，主题设置支持备份恢复。
- 新增内存芯片图标和整机占用比例，桌面可见且屏幕可交互时每 10 秒刷新，熄屏／Activity 暂停停止轮询。
- 豆包和 Gemini 等待按钮边界稳定，最多点击三次，并确认通话控件后结束；识别当前豆包挂断与 Gemini Live 文案。
- 设备界面验证覆盖两种电子书模式、内存显示、图片选择器打开／取消及熄屏恢复。
- 启动测试确认豆包冷／热启动进入通话，Gemini 横屏进入 Live；已在通话时不重复点击。
- VoiceTargetChecks、ProcessStateChecks、ReminderEventChecks、SystemToneUriChecks、DeviceChecks、HidStateChecks、ReminderChecks 针对发布版类文件全部通过；备份检查包含黑色电子书设置。
- Grok Bot 是独立应用，本版没有自动语音入口集成。

## 已在设备验证

- 构建、签名和覆盖安装。
- 完整重启后默认桌面保留，无障碍及开机脚本恢复。
- 双击启动与三键清理（输入层模拟）。
- 配置导出／导入往返、非法文件拒绝及大小限制。
- 崩溃记录及上次退出原因。
- 系统接受 Root HID 桥接连接。

## v1.6.0 验证

- 新增按事件编辑器，与实际事件定义一致，显示已保存摘要，支持草稿试听、保存与取消。
- 橙色活跃圆环附着图标视图并按包名匹配，替代依赖布局的下划线。
- ReminderEventChecks 覆盖独立事件选择、真实资源关联、旧设置兼容和总开关。
- 备份检查包含各事件的媒体类型及音效字段。
- ProcessStateChecks、ReminderEventChecks、DeviceChecks、ReminderChecks、HidStateChecks 在设备通过。
- 实际界面草稿试听播放了所选音效；取消未修改原断开提醒设置。
- 截图确认后台豆包有橙色圆环，清理成功后圆环消失。

## v1.5.0 验证

- APK 内置八段 AOSP OGG，播放仅使用这些资源，不依赖 MediaStore 或外部文件。
- SystemToneUriChecks 通过固定 ID 校验及外部／路径／网络拒绝测试；DeviceChecks 通过音效备份恢复和非法外部选择拒绝测试。
- ReminderChecks 解析全部八段音效；SystemToneInstrumentation 在应用进程中验证所选音效播放、默认兜底与清理。
- 设备验证选择窗口的试听／保存／取消。
- 豆包电话按钮自动化通过：观察到资源 ID com.larus.nova:id/real_time_call，日志记录 com.larus.nova 的 VOICE_CLICKED。账号额度耗尽提示可作为进入入口的证据，不声称完成通话。
- VoiceTargetChecks 验证豆包控件匹配、包名隔离及原 GPT／Gemini 入口。
- 清理返回退出码 0 并显示成功覆盖层；实体三键组合也触发了该路径。
- 成功层显示派大星、“搞定了”和默认开启的可选系统确认短音；失败保留错误提示，不显示成功。
- 桌面与设置图标统一圆形遮罩，高德已在设备目视检查。
- 日志确认清理成功音效；新音效开关通过备份恢复测试。
- 最终构建的 VoiceTargetChecks、DeviceChecks、HidStateChecks、ReminderChecks 通过。

## v1.3.0 验证

- APK 构建与 v2／v3 签名校验通过，更新已安装到目标设备。
- HidStateChecks 覆盖初始连接、中间状态、HID／ACL 广播、重试预算耗尽、低电量边界与关闭清理。
- 回连执行器阻塞时，ACL 断开处理约 1 毫秒；这是软件事件响应，不是键盘物理断电检测时间。
- 物理断电后 3～5 秒内端到端提醒尚未验证，取决于蓝牙协议栈发现链路丢失。
- ReminderChecks 通过手机电量阈值／充电规则、去重、总开关和独立开关、音频解析及队列清理。
- VoiceInstrumentation 在目标应用进程中完整播放四段语音。
- 含代理应用列表和全部提醒设置的备份恢复通过；格式错误设置仍被拒绝。
- v1.3.0 新增四段内置离线语音、默认开启的语音提醒及提示音选择、独立开关和自适应图标。
- 实体键盘连接／断开测试确认开启响铃后可听到提醒；语音走媒体通道，显式检查响铃模式并提供振动兜底。
- 当时设备日志：控制器断开原因 8（连接超时），ACL 广播时间 23:26:36.101，播报开始 23:26:36.511，应用响应 410 毫秒。该键盘从物理断电到提醒仍约 10 秒。
- Magisk 看护改为匹配真实 ServiceRecord，而非过期 ConnectionRecord；测试结束必须确认无障碍实际绑定。
- 长时间播放及耗电仍需手工验证。

## 尚未完整验证

- 多小时稳定性、电池与发热、蓝牙掉线统计。
- 键盘休眠／关机／超出距离后的回连。
- 两分钟空闲调暗，以及触摸／HID／媒体事件恢复。
- 真实断网、代理故障和 VPN 切换下的 ChatGPT 流程。
- 最终构建的重启与强行停止恢复。
- 配合真实 SIM 的 Wi-Fi／SIM 信号状态。
- 其他 Android 版本和 ROM。

## 包名变更说明

本仓库之前的版本使用 `com.yijia.home`。新包名会作为**新应用**安装，需要重新设置默认桌面、启用无障碍并授予 Root。
