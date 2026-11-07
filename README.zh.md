<p align="center"><img src="docs/logo.png" width="160" alt="AX-HUD 标志"></p>

<p align="center"><a href="README.md">English</a> · <a href="README.tr.md">Türkçe</a> · <a href="README.de.md">Deutsch</a> · <b>中文</b></p>

# AX-HUD: AX12 视频监视器、HUD 与语音播报

**AX-HUD** 是适用于 RadioMaster AX12 的 Android 应用。它对遥控器**只读**, 不会写入任何设置。

## 功能

* **全屏**显示 HDMI 输入的视频; 无画面时底部出现一条细状态栏, 并自动重新连接。
* 按你自己编写的规则**语音播报**, 例如 `ch5 >= 1500 -> 已解锁`。规则可在应用内编辑: 阈值、`and`/`or` 逻辑、开关检测、`{vbat/6:2}` 这样的实时数值。
* **警告状态栏**使用同样的规则逻辑 (严重 = 红色并闪烁, 警告 = 琥珀色, 信息 = 蓝色; 可设置 N 秒后自动隐藏)。
* **叠加显示:** 摇杆视图和辅助开关通道条 (名称可改、可反向)、飞行统计 (最大速度、最大高度、峰值电流)、雷达和飞行前检查清单。
* **从 GitHub 更新:** 设置 > 更新。两个渠道: Nightly (每次提交) 和 Stable (正式版)。详见 [UPDATES.md](UPDATES.md)。
* **语言:** 英语、土耳其语、德语、中文。设置 > 语言。语音可以使用与屏幕不同的语言。

## 要求与安装

* RadioMaster **AX12** (Android 9)。作者的测试环境: 2026 年 10 月系统更新、INAV 9.1、ExpressLRS。其他环境**未测试**。
* 在 **Releases** 页面下载 `ax-hud.apk` (带串口锁补丁的版本, 在 AX12 上可用) 并安装。
* **关闭** AX12 自带的 RadioMasterAX 应用: 串口同一时间只能被一个应用使用。AX-HUD 关闭后会归还串口。

## 安全

本应用为实验性质, 仅作为**备用信息来源**, 不能替代飞控的 OSD。使用风险自负。请遵守当地法规飞行。请勿在飞行中更新。

## 隐私

联网权限仅用于从 github.com 获取更新信息和 APK。不会发送任何数据。仅当你开启“设置 > 飞行日志”(默认关闭)时, GPS 位置才会写入遥控器上的文件, 文件保留在遥控器内。不要公开显示你飞行地点的日志。

## 致谢

感谢 **RadioMaster** 公开发布 AX SDK, 使本应用成为可能。AX-HUD 是独立项目, 与 RadioMaster 无关联。

完整说明 (构建、串口锁、规则、测试) 请见[英文 README](README.md)。翻译修正请见 [TRANSLATING.md](TRANSLATING.md)。

## 许可证

AX-HUD 是自由软件, 采用 **GNU 通用公共许可证第 3 版或更高版本** (GPL-3.0-or-later), 并附加一项允许与 RadioMaster AX SDK 结合的**额外许可**: 见 [LICENSE](LICENSE)。简而言之: 你可以使用、研究、修改和分享它; 如果你分享修改后的版本或基于它的程序, 必须以相同许可证提供源代码。版权持有人可以向特定一方另行授予条件不同的书面许可, 其他人不能。RadioMaster SDK、Qt 库以及 APK 内的其他组件保留各自的许可证, 它们连同全文列在 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 中, 也可在应用的 *设置 > 关于* 中查看。贡献请见 [CONTRIBUTING.md](CONTRIBUTING.md)。
