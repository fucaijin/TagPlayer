[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](https://github.com/rRemix/APlayer/blob/master/LICENSE)
[![Telegram](https://img.shields.io/badge/Telegram-2CA5E0.svg?logo=telegram&style=flat-square)](https://t.me/joinchat/PqrPPBbM4poRPDH7qnXxLw "Join Telegram Group")


# [中文](/README_CN.md)

# APlayer - Android Music Player

## Intro
- A beautiful and powerful music player built with Jetpack Compose

## Download
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
alt="Get it on Google Play"
height="80">](https://play.google.com/store/apps/details?id=remix.myplayer)
[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png"
alt="Get it at IzzyOnDroid"
height="80">](https://apt.izzysoft.de/packages/remix.myplayer)

## New in this fork: Tag Management
This fork adds **tag-based music management** on top of the original project: tag your songs (e.g. sleep aid / light music / piano / nostalgic) and filter them with one tap. It fits scenarios like bedtime or work — for example, filter out light music with the "sleep aid" or "piano" tags before going to sleep, which is not convenient with plain lists.

### Screenshots
|   |   |   |   |
|:-:|:-:|:-:|:-:|
| <img src="screenshoots/12-tag-filter.png" width="200" alt="Tag Display in Song List" /> | <img src="screenshoots/13-tag-filter-expanded.png" width="200" alt="Tag Filter" /> | <img src="screenshoots/14-tag-manage.png" width="200" alt="Tag Manage" /> | <img src="screenshoots/15-tag-song.png" width="200" alt="Single Song Tag" /> |
| Song List | Tag Filter | Tag Manage | Tag a Song |

|   |   |
|:-:|:-:|
| <img src="screenshoots/16-tag-batch.png" width="200" alt="Batch Tag" /> | <img src="screenshoots/17-tag-settings.png" width="200" alt="Song List Settings" /> |
| Batch Tag | Song List Settings |

### Features
- Tag-based music management: tag songs with "sleep aid / light music / piano / nostalgic" etc. and filter by tags; tags are written into the audio file custom field (`AUDIO_TAGS`), so they survive reinstall or device change
- Tag filter panel: expandable/collapsible panel at the top of the song list, with live tag search, draggable height, and AND/OR logic combination
- Tag manage dialog: create, rename, delete tags; supports filtering "no tag" songs
- Batch tagging: multi-select songs, then add to / remove from tags in batch
- Per-song tag manage: a star button in each list item opens the tag dialog; tags are written to the audio file only when you tap "Save"
- Play queue sync: the filtered result becomes the playlist; the current song keeps playing when switching tags
- Settings - Song list: 4 new switches "show tags in list / tag manage button in list / show list number / show artist and album"
- Bottom bar: added "Previous" button, removed the cover thumbnail control
- Tag write failure dialog (with copyable error) and log export/clear for troubleshooting

## Screenshot
|   |   |   |   |
|:-:|:-:|:-:|:-:|
| <img src="screenshoots/1-home.png" width="200" alt="Home Screen" /> | <img src="screenshoots/2-albums.png" width="200" alt="Albums Screen" /> | <img src="screenshoots/3-playing-cover.png" width="200" alt="Playing Cover Screen" /> | <img src="screenshoots/4-playing-lyric.png" width="200" alt="Playing Lyric Screen" /> |
| Home | Albums | Playing Cover | Playing Lyric |

|   |   |   |   |
|:-:|:-:|:-:|:-:|
| <img src="screenshoots/5-dark.png" width="200" alt="Dark Mode" /> | <img src="screenshoots/6-sleep-timer.png" width="200" alt="Sleep Timer" /> | <img src="screenshoots/7-desktop-lyric-widget.png" width="200" alt="Desktop Lyric & Widget" /> | <img src="screenshoots/8-lockscreen.png" width="200" alt="Lockscreen" /> |
| Dark Mode | Sleep Timer | Desktop Lyric & Widget | Lockscreen |

|   |   |   |
|:-:|:-:|:-:|
| <img src="screenshoots/9-land-home.png" width="220" alt="Landscape Home" /> | <img src="screenshoots/10-land-playing.png" width="220" alt="Landscape Playing" /> | <img src="screenshoots/11-land-desktop-lyric-widget.png" width="220" alt="Landscape Desktop Lyric & Widget" /> |
| Landscape Home | Landscape Playing | Landscape Desktop Lyric & Widget |

## Feature
- Configurable tabs: songs, artists, albums, folders, playlists, remote (WebDAV)
- Local and online lyrics: embedded/local/online, word-by-word, searchable with priority
- Floating lyrics and home screen widgets
- Themes: light, dark, and AMOLED black, with customizable colors
- Auto download album and artist artwork
- Built-in tag editor for title/artist/album/lyrics
- Playlists: create, edit, import, export, and per-playlist sorting
- WebDAV streaming from your personal cloud storage
- Playback controls: equalizer, speed control, sleep timer
- Lock screen controls and Android media notification
- Bluetooth/wired headset media buttons
- Auto scan media library or manual folder scan

## Thanks
- [XXPermissions](https://github.com/getActivity/XXPermissions)
- [Retrofit](https://github.com/square/retrofit)
- [Timber](https://github.com/JakeWharton/timber)
- [Leakcanary](https://github.com/square/leakcanary)
- [ImageCropper](https://github.com/CanHub/Android-Image-Cropper)
- [TinyPinyin](https://github.com/promeG/TinyPinyin)
- [TagLib for Android](https://github.com/rRemix/taglib)
- Original project: [rRemix/APlayer](https://github.com/rRemix/APlayer)

## Finally
- Pull request is welcome
- I'll be appreciate if you star
- If you have any question,you can send email to rRemix.me@gmail.com,open an issue or join [tg group](https://t.me/joinchat/PqrPPBbM4poRPDH7qnXxLw)
