[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](https://github.com/rRemix/APlayer/blob/master/LICENSE)
[![Telegram](https://img.shields.io/badge/Telegram-2CA5E0.svg?logo=telegram&style=flat-square)](https://t.me/joinchat/PqrPPBbM4poRPDH7qnXxLw "Join Telegram Group")

# [English](/README.md)

# APlayer - 安卓本地音乐播放器

## 简介
- 一款基于Jetpack Compose，简洁、功能强大的的音乐播放器

## 下载
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
alt="Get it on Google Play"
height="80">](https://play.google.com/store/apps/details?id=remix.myplayer)
[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png"
alt="Get it at IzzyOnDroid"
height="80">](https://apt.izzysoft.de/packages/remix.myplayer)

## 本分支新增：标签管理
本分支在原项目基础上新增**标签管理音乐**功能：给歌曲打上"助眠/轻音乐/钢琴/怀旧"等标签，即可按标签一键过滤出想听的音乐。适合睡觉、工作等场景——比如睡前用"助眠/钢琴"过滤出轻音乐，而用列表一个个翻找很不方便。

### 截图
|   |   |   |   |
|:-:|:-:|:-:|:-:|
| <img src="screenshoots/12-tag-filter.png" width="200" alt="歌曲列表标签显示" /> | <img src="screenshoots/13-tag-filter-expanded.png" width="200" alt="标签过滤" /> | <img src="screenshoots/14-tag-manage.png" width="200" alt="标签管理" /> | <img src="screenshoots/15-tag-song.png" width="200" alt="单曲标签管理" /> |
| 歌曲列表 | 标签过滤 | 标签管理 | 单曲打标签 |

|   |   |
|:-:|:-:|
| <img src="screenshoots/16-tag-batch.png" width="200" alt="批量打标签" /> | <img src="screenshoots/17-tag-settings.png" width="200" alt="歌曲列表设置" /> |
| 批量打标签 | 歌曲列表设置 |

### 特点
- 标签管理音乐：给歌曲打上"助眠/轻音乐/钢琴/怀旧"等标签，支持按标签快速过滤；标签写入音频文件自定义字段（`AUDIO_TAGS`），重装应用/换设备不丢失
- 标签过滤区：歌曲列表顶部可展开/收起，支持标签实时搜索、拖动调整高度、"与/或"逻辑组合过滤
- 标签管理弹窗：创建、重命名、删除标签，支持"无标签"过滤
- 批量打标签：多选歌曲后批量"添加到标签/从标签移除"
- 单曲标签管理：列表条目五角星按钮一键管理该歌曲标签，点"保存"才写入音频文件
- 播放队列联动：过滤结果即播放列表，切换标签时当前歌曲保持播放不打断
- 设置-歌曲列表：新增"列表条目标签显示/列表条目标签管理/显示歌曲列表序号/显示艺术家专辑名"四个开关
- 底部栏新增"上一首"按钮，取消专辑封面控件
- 标签写入失败弹窗（可复制错误信息）、日志导出/清空，便于排查问题

## 截图
|   |   |   |   |
|:-:|:-:|:-:|:-:|
| <img src="screenshoots/1-home.png" width="200" alt="主页" /> | <img src="screenshoots/2-albums.png" width="200" alt="专辑" /> | <img src="screenshoots/3-playing-cover.png" width="200" alt="播放页封面" /> | <img src="screenshoots/4-playing-lyric.png" width="200" alt="播放页歌词" /> |
| 主页 | 专辑 | 播放页封面 | 播放页歌词 |

|   |   |   |   |
|:-:|:-:|:-:|:-:|
| <img src="screenshoots/5-dark.png" width="200" alt="深色模式" /> | <img src="screenshoots/6-sleep-timer.png" width="200" alt="睡眠定时" /> | <img src="screenshoots/7-desktop-lyric-widget.png" width="200" alt="桌面 歌词 & 组件" /> | <img src="screenshoots/8-lockscreen.png" width="200" alt="锁屏" /> |
| 深色模式 | 睡眠定时 | 桌面 歌词 & 组件 | 锁屏 |

|   |   |   |
|:-:|:-:|:-:|
| <img src="screenshoots/9-land-home.png" width="220" alt="横屏主页" /> | <img src="screenshoots/10-land-playing.png" width="220" alt="横屏播放" /> | <img src="screenshoots/11-land-desktop-lyric-widget.png" width="220" alt="横屏桌面 歌词 & 组件" /> |
| 横屏主页 | 横屏播放 | 横屏桌面 歌词 & 组件 |


## 特点
- 首页 Tab 可配置：歌曲/艺术家/专辑/文件夹/播放列表/远程(WebDAV)
- 歌词体验：内嵌/本地/在线歌词(网易/酷狗/QQ)，支持逐字歌词与优先级设置
- 悬浮歌词与桌面小组件
- 主题：普通/暗色/纯黑，支持自定义主题色
- 专辑/艺术家封面自动补全
- 内置标签编辑器：标题/歌手/专辑/歌词
- 歌单管理：创建、编辑、导入、导出，支持每个歌单独立排序
- WebDAV 支持：个人云盘串流播放
- 音效与控制：均衡器、播放速度、睡眠定时
- 锁屏控制与通知栏播放控制
- 蓝牙/有线耳机按键控制
- 自动扫描媒体库，或手动扫描目录


## 感谢
- [XXPermissions](https://github.com/getActivity/XXPermissions)
- [Retrofit](https://github.com/square/retrofit)
- [Timber](https://github.com/JakeWharton/timber)
- [Leakcanary](https://github.com/square/leakcanary)
- [ImageCropper](https://github.com/CanHub/Android-Image-Cropper)
- [TinyPinyin](https://github.com/promeG/TinyPinyin)
- [TagLib for Android](https://github.com/rRemix/taglib)
- 原项目：[rRemix/APlayer](https://github.com/rRemix/APlayer)


## 最后
- 如果喜欢或者能给你提供帮助，欢迎Star
- 因为是刚学安卓的时候就开始做了，很多代码待完善或者重构，还有一些待开发的功能，欢迎Pull Request
- 关于此分支，有任何问题可以发邮件到我的邮箱: fucaijin999@gmail.com
