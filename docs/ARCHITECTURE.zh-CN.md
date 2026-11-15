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
| `NetIndicators`／`StatusGlyph` | Wi-Fi、移动网络、电量和蓝牙图标 |
| `ConfigBackup` | ZIP 配置导出／导入，提供类型校验和大小限制 |
| `AppLog`／`HomeApplication` | 每日日志、14 天清理、崩溃与上次退出原因 |
| `OfflineVoiceAlerts` | 有上限的离线音频队列、连接事件优先、播放超时和生命周期清理 |
| `DeviceConfig` | 设备专属常量，如键盘地址 |

`phone-config/92-btquicklaunch.sh` 是 Magisk `service.d` 脚本，开机恢复默认桌面、电池白名单和无障碍服务。

`phone-config/grant-home.sh` 记录了特定设备的 Magisk 授权，含硬编码 UID，不可原样用于其他安装。
