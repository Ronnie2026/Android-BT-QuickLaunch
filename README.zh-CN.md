# Android-BT-QuickLaunch

[English](README.md)

一个极简的 Android 桌面（Launcher），让**蓝牙键盘 / 小键盘**变成实体快捷键，一键唤起你指定的应用。

## 项目背景

废旧安卓手机便宜、性能够用，却常年躺在抽屉里。本项目把它改造成**新时代智能车载终端**，用于语音聊天和 AI Agent 场景：一台专属的"Agent 的 Agent 客户端"，驾驶时不用点屏幕，只靠几个实体按键操作。

手机把本程序设为默认桌面，配对的蓝牙小键盘一键即可打开对应应用：AI 语音助手、导航、音乐等，中间没有任何菜单。

## 功能

- 固定横屏桌面，四个可配置应用位，长按可交换位置
- 蓝牙按键：双击 1/2/3 打开前三个位置；三键同按清理后台并回到桌面
- ChatGPT 专用流程：检测 Google/ChatGPT 连通性，必要时启动 Clash 并复检，再进入语音模式
- 后台应用指示、电量 / Wi-Fi / 移动网络 / 蓝牙状态图标
- 彩色与电子书风格主题，自定义壁纸和图标
- 2 分钟空闲自动调暗，降低 OLED 烧屏风险
- 对已配对键盘主动回连
- 配置导入导出（ZIP）、日志导出、崩溃记录

## 环境与限制

> 目前针对单一设备调校，**还不是**通用桌面。

- 在一加 7T（HD1900）、Android 11、Magisk Root 上开发和测试
- 清理后台和主动回连 HID 键盘需要 Root（使用 Android 11 隐藏的蓝牙 HID 接口）
- 无障碍服务用于读取键盘按键和识别 ChatGPT 语音按钮，ChatGPT 界面更新后可能需要适配
- 键盘地址在 [`DeviceConfig.java`](launcher-app/src/com/benbenyu/btquicklaunch/DeviceConfig.java) 中设置，请改成你自己的键盘
- 目前界面文字以中文为主，后续会做多语言

验证情况见 [docs/STATUS.md](docs/STATUS.md)。

## 构建

需要 Windows PowerShell、JDK 17、Android SDK platform 30 与 build-tools（或本地 `.tools` 缓存）。

```powershell
.\build-home.ps1
```

产物：`dist/BT-QuickLaunch-v<版本>.apk`。首次构建会在 `.keys/` 生成本地签名密钥，请妥善保管并持续复用，否则无法覆盖升级。

## 安装

```bash
adb install -r dist/BT-QuickLaunch-v1.1.0.apk
adb shell cmd package set-home-activity com.benbenyu.btquicklaunch/.MainActivity
```

然后开启无障碍服务、配对键盘；如需开机自动恢复，把 `phone-config/92-btquicklaunch.sh` 放到 `/data/adb/service.d/`（权限 755）。

## 目录结构

| 路径 | 内容 |
|---|---|
| `launcher-app/` | Android 应用源码、Manifest、资源、设备检查 |
| `phone-config/` | Magisk 开机脚本、设备测试脚本 |
| `build-home.ps1` | 构建与签名脚本 |
| `docs/` | 架构与项目状态 |

## 许可证

[MIT](LICENSE)