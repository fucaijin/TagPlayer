[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](https://github.com/rRemix/APlayer/blob/master/LICENSE)
[![Telegram](https://img.shields.io/badge/Telegram-2CA5E0.svg?logo=telegram&style=flat-square)](https://t.me/joinchat/PqrPPBbM4poRPDH7qnXxLw "Join Telegram Group")


# [中文](/README_CN.md)

# TagPlayer - Android Music Player

## Intro
TagPlayer is a local music player modified from the excellent open-source music player APlayer, featuring **powerful tag-based management with corresponding filtering** and **rich listening analytics**. You can use tags to classify, filter, and exclude songs, keeping your library perfectly organized; on top of standard playback it lets you organize your library with custom tags, understand your habits through rich statistics, and personalize the UI — sort rules, filename display, heatmaps and analysis modules — to fit the way you listen.

## New in this fork: Tag Management
This fork adds **tag-based music management** on top of the original project: tag your songs (e.g. sleep aid / light music / piano / nostalgic) and filter them with one tap. It fits scenarios like bedtime or work — for example, filter out light music with the "sleep aid" or "piano" tags before going to sleep, which is not convenient with plain lists.

### TagPlayer Screenshots
|   |   |   |   |
|:-:|:-:|:-:|:-:|
| <img src="screenshoots/12-tag-filter.jpg" width="200" alt="Tag Display in Song List" /> | <img src="screenshoots/13-tag-filter-expanded.png" width="200" alt="Tag Filter" /> | <img src="screenshoots/14-tag-manage.png" width="200" alt="Tag Manage" /> | <img src="screenshoots/15-tag-song.png" width="200" alt="Single Song Tag" /> |
| Song List | Tag Filter | Tag Manage | Tag a Song |

|   |   |
|:-:|:-:|
| <img src="screenshoots/16-tag-batch.jpg" width="200" alt="Batch Tag" /> | <img src="screenshoots/17-tag-settings.png" width="200" alt="Song List Settings" /> |
| Batch Tag | Song List Settings |

|   |   |   |
|:-:|:-:|:-:|
| <img src="screenshoots/18-data-analysis.jpg" width="200" alt="Data Analysis Statistics" /> | <img src="screenshoots/19-data-analysis-charts.jpg" width="200" alt="Daily Stats & Trends" /> | <img src="screenshoots/20-data-analysis-heatmap.jpg" width="200" alt="Tag Ranking & Heatmaps" /> |
| Data Analysis Statistics | Daily Stats & Trends | Tag Ranking & Heatmaps |

### Features
- Tag-based music management: tag songs with "sleep aid / light music / piano / nostalgic" etc. and filter by tags; tags are written into the audio file custom field (`AUDIO_TAGS`), so they survive reinstall or device change
- Tag filter panel: expandable/collapsible panel at the top of the song list, with live tag search, draggable height, and AND/OR logic combination
- Tag manage dialog: create, rename, delete tags; supports filtering "no tag" songs
- Batch tagging: multi-select songs, then add to / remove from tags in batch
- Per-song tag manage: a star button in each list item opens the tag dialog; tags are written to the audio file only when you tap "Save"
- Play queue sync: the filtered result becomes the playlist; the current song keeps playing when switching tags
- Data analysis: rich usage statistics and visual reports — play count / play duration / skip rankings, daily listening time, daily earliest & latest listening time, tag song counts, favorite tags, and 7×24h / 24h hour heatmaps, helping you understand your listening habits
- Settings - Song list: 4 new switches "show tags in list / tag manage button in list / show list number / show artist and album"
- Bottom bar: added "Previous" button, removed the cover thumbnail control
- Tag write failure dialog (with copyable error) and log export/clear for troubleshooting
- Data analysis settings popup: toggle each analysis module (rankings, charts, heatmaps…) on/off, set list rows; heatmap offers single/multi-color scheme and adjustable x-axis time buckets (24/12/8/6)
- Customizable song list sort rules: choose which sort options appear in the sort menu (at least one is kept)
- Bottom bar and playing-screen title can display the file name instead of the metadata title
- Editing a song's metadata (title/artist/album) now refreshes the play-event stats, so rankings show the updated title immediately
- Tag export/import via the system file picker (JSON backup keyed by file path)
- Batch rename songs with a template ({title}{artist}{album}{track}{year}); the default rename template and default tags can be configured in Settings, and default tags are pre-selected when a song has none

## Aplayer Screenshot
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

## Localization (i18n)

TagPlayer can switch to **any language**, not just the built-in Chinese / English:

- Built-in languages (System / Simplified Chinese / Traditional Chinese / Japanese) can be chosen directly in **Settings → Language**.
- For any other language (French, Spanish, Korean, …) no rebuild is needed:
  1. Open **Settings → Language**;
  2. Tap **Export template** — a dialog shows where the template file is saved (it stays open until you confirm);
  3. Tap **AI translation prompt** to copy the prompt, send it together with the template to an AI and tell it your target language;
  4. Import the AI-generated `.json` via **Import template**; the new language then appears in the list and takes effect immediately.
- The exported template uses English as the source; its keys map 1:1 to the app's strings. Imported translations only override matched strings and fall back to built-in resources for the rest.

## Thanks
- Original project: [rRemix/APlayer](https://github.com/rRemix/APlayer)
- [XXPermissions](https://github.com/getActivity/XXPermissions)
- [Retrofit](https://github.com/square/retrofit)
- [Timber](https://github.com/JakeWharton/timber)
- [Leakcanary](https://github.com/square/leakcanary)
- [ImageCropper](https://github.com/CanHub/Android-Image-Cropper)
- [TinyPinyin](https://github.com/promeG/TinyPinyin)
- [TagLib for Android](https://github.com/rRemix/taglib)

## Finally
- Pull request is welcome
- I'll be appreciate if you star
- If you have any question,you can send email to rRemix.me@gmail.com,open an issue or join [tg group](https://t.me/joinchat/PqrPPBbM4poRPDH7qnXxLw)
