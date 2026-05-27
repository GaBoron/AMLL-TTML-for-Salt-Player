# AMLL TTML Loader for Salt Player

**简体中文** | [English](README.en.md)

AMLL TTML Loader 是一个 Salt Player for Windows workshop 插件。它会根据当前播放歌曲搜索 AMLL TTML DB，加载并转换逐词 TTML 歌词；当在线匹配不可靠、超时或不可用时，会回退到本地歌词或播放器默认歌词流程。

当前版本：`1.0.5`

📖 用户手册：见 [docs/USER_GUIDE.md](docs/USER_GUIDE.md)

## 下载与安装

1. 从最新 GitHub Release 下载 `AMLL-TTML-Loader-1.0.5.zip`。
2. 将 zip 文件复制到：

   ```text
   %APPDATA%\Salt Player for Windows\workshop\plugins
   ```

3. 重启 Salt Player for Windows。

普通用户安装插件不需要 JDK。详细使用方法、手动匹配、歌词偏移、日志和排障说明见 [用户手册](docs/USER_GUIDE.md)。

## 截图

### 自动加载 AMLL 歌词

![自动加载 AMLL 歌词](docs/images/lyrics-page.png)

### 歌词偏移调整

![歌词偏移调整](docs/images/lyric-offset-per-track-dialog.png)

### 手动匹配当前歌曲

![手动匹配当前歌曲](docs/images/manual-match-dialog.png)

## 主要功能

- 根据歌曲标题、歌手、专辑信息搜索 AMLL TTML DB。
- 将 TTML 逐词歌词转换为 Salt Player 可用的 SPL 样式歌词。
- 支持自动匹配失败时回退到本地歌词或播放器默认歌词流程。
- 支持手动匹配当前歌曲、预览候选歌词、固定 AMLL 结果。
- 支持为当前歌曲固定使用本地/元数据歌词。
- 支持全局歌词偏移和当前歌曲单独偏移。
- 支持运行日志，便于排查匹配、网络和缓存问题。

## 说明

- 插件依赖 AMLL TTML DB、GitHub raw 相关资源和歌曲元数据质量，不能保证所有歌曲都能匹配到准确歌词。
- 插件会在本机保存歌词缓存、匹配记录、偏移设置和日志。
- 插件不会上传音频文件。
- 完整使用说明见 [docs/USER_GUIDE.md](docs/USER_GUIDE.md)。
- 常见问题排查见 [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)。

## 开发与维护

### 项目结构

- `src/main/java/dev/amll/saltplayer/ttml`：插件主体代码
- `src/spwApiStubs/java`：SPW API 编译期 stub，只用于本地编译，不打入插件 jar
- `src/main/resources/preference_config.json`：Salt Player 插件设置声明
- `src/main/resources/fonts`：弹窗 UI 内置字体和字体许可证
- `docs/images`：README 截图
- `out/plugin`：Gradle `plugin` 任务生成的插件 zip

### 从源码构建

开发环境需要 JDK 21，并确保 `JAVA_HOME` 与 `PATH` 指向可用的 JDK。

普通验证：

```powershell
.\gradlew.bat build
```

生成插件包：

```powershell
.\gradlew.bat plugin
```

输出文件：

```text
out\plugin\AMLL-TTML-Loader-1.0.5.zip
```

### 发布

- 版本号来自 `build.gradle.kts`。
- User-Agent 版本在 `src/main/java/dev/amll/saltplayer/ttml/AmllTtmlLoader.java`。
- 推送 `v*` tag 会触发 `.github/workflows/build.yml`，由 GitHub Actions 构建并上传 Release 资产。

## 第三方说明

- [AMLL TTML DB](https://github.com/amll-dev/amll-ttml-db)：歌词数据来源，使用 CC0-1.0
- Salt Player for Windows：插件运行平台
- [spw-workshop-api](https://github.com/Moriafly/spw-workshop-api)：Salt Player workshop API 参考，使用 Apache-2.0；本仓库仅包含编译期 stub，不打入插件包
- [Noto Sans CJK](https://github.com/notofonts/noto-cjk)：弹窗界面内置字体，使用 OFL-1.1；字体许可证随插件包分发
- [PF4J](https://github.com/pf4j/pf4j)：插件机制 API 参考，使用 Apache-2.0；本仓库仅包含编译期 stub，不打入插件包
- Gradle：构建工具和 Gradle Wrapper，使用 Apache-2.0

完整第三方许可证说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

本插件只负责搜索、转换、缓存和显示歌词。歌词内容来自 AMLL TTML DB。用户应尊重原音乐作品、歌词文本及相关权利人的权益。
