---
name: Bug report
about: 报告插件错误、歌词加载异常或匹配问题
title: "[Bug]: "
labels: bug
assignees: GaBoron

---

## 问题描述 / Describe the bug

请简单说明你遇到的问题。

例如：
- 插件无法加载
- 没有搜索到 AMLL 歌词
- 匹配到了错误歌词
- 歌词时间轴不正确
- 手动匹配窗口异常
- 本地/默认歌词回退异常


## 复现步骤 / Steps to reproduce

请写出如何复现这个问题：

1. 打开 Salt Player for Windows
2. 播放歌曲：`歌曲名 - 歌手`
3. 打开/使用插件功能：`例如自动匹配 / 手动匹配`
4. 出现问题：`描述具体现象`


## 预期结果 / Expected behavior

你原本希望插件怎么工作？

例如：
- 应该自动加载 AMLL 歌词
- 应该回退到本地歌词
- 应该显示正确的歌词来源
- 手动匹配结果应该正常保存


## 实际结果 / Actual behavior

实际发生了什么？

例如：
- 没有显示歌词
- 一直显示本地歌词
- 加载了错误歌曲的歌词
- 出现报错或插件无响应
- Salt Player 崩溃


## 歌曲信息 / Track information

请填写出问题歌曲的信息，方便排查匹配问题：

- 歌曲名 / Title:
- 歌手 / Artist:
- 专辑 / Album:
- 本地文件格式 / File type: `mp3 / flac / m4a / other`
- 歌曲元数据是否完整 / Is metadata complete: `是 / 否 / 不确定`


## 插件和环境信息 / Environment

- 插件版本 / Plugin version: `例如 1.0.1`
- Salt Player for Windows 版本 / Salt Player version:
- Windows 版本 / Windows version:
- 网络环境是否能访问 GitHub raw / Can access GitHub raw: `是 / 否 / 不确定`
- 是否使用代理 / Proxy or VPN: `是 / 否`


## 日志 / Logs

请附上相关日志片段。

日志目录：

```text
%APPDATA%\Salt Player for Windows\workshop\amll-ttml-loader-cache\logs
````

请优先粘贴出问题时间附近的日志，不要直接上传包含隐私信息的完整日志。

```text
在这里粘贴日志片段
```

## 截图 / Screenshots

如果方便，请附上截图，例如：

* 歌词显示异常截图
* 手动匹配窗口截图
* 插件设置页面截图
* 报错信息截图

## 额外说明 / Additional context

还有其他可能有帮助的信息可以写在这里。

例如：

* 是否只有某一首歌有问题
* 是否所有歌曲都无法加载 AMLL 歌词
* 清理缓存后是否恢复
* 手动匹配是否能解决
