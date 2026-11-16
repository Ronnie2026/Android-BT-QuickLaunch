# 架构

[English (default)](ARCHITECTURE.md)

源码位于 `launcher-app/src/com/benbenyu/btquicklaunch`。

| 类 | 职责 |
|---|---|
| `MainActivity` | 横屏桌面、四个应用位、排序、壁纸、主题、亮度和设置 |
| `ButtonService` | 指定键盘的无障碍按键过滤、双击、三键组合及语音按钮检测 |
| `ProxyGuard` | 本地网络、VPN 接口、Clash 进程和回环端口检查，不做外网探测 |
| `HidConnectionManager` | 处理 HID／ACL 广播和提醒音，每秒轮询状态，独立执行器回连 |
| `RootHidProvider`／`RootHidConnect` | 受限 Root HID 连接桥接；Provider 不导出，并检查调用 UID |
| `IdlePower` | 桌面空闲两分钟后调暗，触摸、HID 和媒体播放可重置 |
| `WifiBeaconGuard` | 桌面每 60 秒扫描目标 Wi-Fi SSID 信标；连续超时未检测到时弹出 5 秒倒计时并调用 Root 熄屏休眠 |
| `ImeGuard` | 禁用或恢复系统软键盘输入法，防止弹出键盘遮挡语音按钮 |
| `IosUi` | 设置页 iOS 风格分组卡片、开关、跳转行、分割线与页脚说明 |
| `CircularIconView` | 桌面与设置页应用图标的统一圆形遮罩视图 |
| `MemoryIndicator` | 右上角整机内存芯片图标与占用比例显示 |
| `PowerIndicator` | 右上角实时功耗（W）、电流（mA）与电压（V）显示，仅桌面可见时每 2 秒采样 |
| `NightWallpaper` | 内置静态 XML 暗色与电子书壁纸 |
| `NetIndicators`／`StatusGlyph` | Wi-Fi、移动网络、电量和蓝牙图标 |
| `ReminderEvent`／`ReminderAudio` | 按事件定义的提醒规则、语音／提示音路由与摘要格式化 |
| `SystemTonePlayer` | 内置 AOSP OGG 提示音播放及设置页草稿试听 |
| `ConfigBackup` | ZIP 配置导出／导入，提供类型校验和大小限制 |
| `AppLog`／`HomeApplication` | 每日日志、14 天清理、崩溃与上次退出原因 |
| `OfflineVoiceAlerts` | 有上限的离线音频队列、连接事件优先、播放超时和生命周期清理 |
| `AppUpdater`／`UpdateApkProvider` | 应用内 GitHub Releases 检查更新、APK 校验下载与受限 `content://` 安装桥接 |
| `DeviceConfig` | 设备专属常量，如键盘地址 |

`phone-config/92-btquicklaunch.sh` 是 Magisk `service.d` 脚本，开机恢复默认桌面、电池白名单和无障碍服务。

`phone-config/grant-home.sh` 记录了特定设备的 Magisk 授权，含硬编码 UID，不可原样用于其他安装。
