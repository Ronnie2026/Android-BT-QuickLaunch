# 离线提醒

[English (default)](VOICE_ALERTS.md)

四段 WAV 均内置于 `launcher-app/assets/`，在本地使用 Windows Microsoft Huihui Desktop 中文语音合成。播放不依赖安卓 TTS 引擎或网络。

| 资源 | 播报内容 |
|---|---|
| `keyboard-connected.wav` | 键盘已连接。 |
| `keyboard-disconnected.wav` | 键盘断开。 |
| `keyboard-low-battery.wav` | 蓝牙键盘电量过低，请及时充电。 |
| `phone-low-battery.wav` | 手机电量过低，请及时充电。 |

设置保存在 `home` 偏好中。`reminder_enabled` 总开关及 `reminder_keyboard_connected`、`reminder_keyboard_disconnected`、`reminder_keyboard_low`、`reminder_phone_low` 四个开关默认均为 true。`reminder_media` 默认 `voice`，另一个支持值为 `tone`。这些设置包含在配置备份中。

仅正常响铃模式允许播放声音，静音和振动模式改为 220 毫秒振动。语音使用媒体音量，提示音使用通知音量；应用不修改系统音量或响铃模式。更改提醒设置或退出响铃模式时，取消排队及正在播放的声音。

键盘低电量阈值为严格低于 30%。连接时等待有效电量读取，断开时使用最后有效值；重复状态／电量事件不重复提醒。手机充电时抑制低电量提醒，电量先恢复至至少 30% 后才重新具备提醒条件。

HID 和 ACL 广播驱动连接反馈，每秒轮询兜底。键盘断电后，蓝牙控制器可能等待链路超时才判定失联；设备测试中断电到提醒约 10 秒，而 ACL 断开广播到开始播报仅 410 毫秒。不保证物理断电后 3～5 秒内检测。

`OfflineVoiceAlerts` 串行播放，优先最新连接事件，限制排队数量，并在完成、错误、超时和关闭时释放资源。GATT 电量读取有 10 秒超时，断开后忽略过期回调。

设备检查包括 `HidStateChecks`、`DeviceChecks`、`ReminderChecks`。`VoiceInstrumentation` 仅放在独立签名的测试 APK 中，在目标应用进程内播放各语音。测试后删除测试 APK，必要时恢复无障碍服务，并确认它出现在 **Bound services**，且 **Crashed services** 中没有记录；过期的 Activity 服务 ConnectionRecord 不能证明服务健康。

## 可选内置系统音效

八段未经修改的 AOSP 通知 OGG 文件位于 assets/system-tones。选择窗口提供固定目录、试听／保存／取消，不查询 MediaStore，也不支持外部导入。源码修订与 Apache 2.0 许可记录在 THIRD_PARTY_NOTICES.md，并随 APK 打包。

reminder_tone 和 clean_success_tone 只保存稳定目录 ID：空值／默认、adara、alya、ceres、cobalt、helium、procyon、tejat。备份拒绝其他值，包括系统／content URI 和外部路径。关闭窗口、页面跳转和 Activity 暂停均停止试听。播放上限为五秒，解码失败时使用内置短音兜底。

## 按事件配置（v1.6.0）

ReminderEvent 统一定义设置标题、摘要、语音资源和实际事件。四个蓝牙／电量事件分别保存 <event-key>_media 与 <event-key>_tone；旧 reminder_media／reminder_tone 保留为兼容默认值。清理仍使用独立 clean_success_sound 和 clean_success_tone。

每个事件打开一个草稿编辑器：关闭、语音（支持时）或提示音；语音文案或内置音效下拉框；一个试听按钮及保存／取消。试听读取草稿，不修改偏好；保存更新事件及摘要，取消丢弃修改。蓝牙／电量总开关不控制清理成功反馈。
