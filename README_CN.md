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


## 最后
- 如果喜欢或者能给你提供帮助，欢迎Star
- 因为是刚学安卓的时候就开始做了，很多代码待完善或者重构，还有一些待开发的功能，欢迎Pull Request
- 有任何问题可以发邮件到我的邮箱: rRemix.me@gmail.com,或者加[tg群](https://t.me/joinchat/PqrPPBbM4poRPDH7qnXxLw)

## fcj改
我是在此项目的基础上进行修改。变更需求是：
我想让本应用支持给歌曲打标签，比如打上“怀旧”、“经典”等标签, 然后可以在播放列表里根据标签过滤歌曲，并可以搜索标签，对标签进行管理。具体实现如下：
- 在SongScreen.kt第51行的Column控件里面的顶部添加一个可上下展开的标签过滤区，展开后可拖动展开或收起到自定义高度，也可以点击收起按钮，直接收起过滤区
  - 过滤区顶部是标签的搜索框，可以实时搜索并过滤显示过滤区标签
  - 过滤区搜索框的右边有一个switch切换是“与”或“或”关系来过滤已选的标签。选或的话，只要有选中的标签就显示，与的话，全部只有歌曲有选中的所有标签才显示
  - 过滤区的switch右边应该是管理按钮，应该是一个小齿轮图标，点击后，在弹窗中显示标签管理功能，包括创建、编辑、删除标签，支持将歌曲批量添加到某个或某些标签或从标签移除
- 设置中，应该有一个开关"列表条目标签显示"，默认开启，用于决定是否在SongScreen.kt第74行列表条目底部显示该歌曲的标签（在条目中的"艺术家-专辑名"控件的下方，添加一个控件，该开关用于决定该控件是否显示），但是该控件显示的空间有限，所以如果显示的话，不用显示所有标签，超出控件的内容就用"..."显示
- 设置中，应该有一个开关"列表条目标签管理"，默认开启，用于设置是否显示歌曲列表中每个条目的标签管理按钮，如果打开的话，SongScreen.kt第74行列表条目的右边的三点按钮左边，应该显示一个五角星按钮，点击后，弹出一个弹窗显示所有标签，并高亮该歌曲已有的标签，点击某个标签按钮，可以切换选中或未选中，弹窗提供"取消"和"保存"两个按钮，点击"保存"时才将标签一次性写入音频文件，点击"取消"则丢弃本次修改
- 设置中，应该有一个开关"显示歌曲列表序号"，默认开启，用于是否在歌曲列表中的歌曲名开头，显示序号的功能，开启的话就显示序号，不开启的话就不显示序号
- 设置中，应该有一个开关"显示艺术家专辑名"，默认开启，用于决定是否在歌曲列表的条目中，显示"艺术家-专辑名"的控件（控件具体名称我不知道，还得你搜索一下），不开启的话，就不显示该控件
- 在Bottombar中的Row添加上一首按钮（目前已经有播放/暂停和下一首按钮了），将下一首按钮旋转180度作为上一首按钮（我只是让你复用它的图形作为按钮），点击后就播放上一首歌
我们不要一次性完成所有工作，我们一步一步实现
- 取消在Bottombar中的专辑封面图控件