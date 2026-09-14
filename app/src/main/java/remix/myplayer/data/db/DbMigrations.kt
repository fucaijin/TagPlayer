package remix.myplayer.data.db

import android.annotation.SuppressLint
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import remix.myplayer.data.db.room.entity.WebDav

internal object DbMigrations {

  val migration3to4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE `PlayQueue` ADD COLUMN `title` TEXT NOT NULL DEFAULT ''")
      db.execSQL("ALTER TABLE `PlayQueue` ADD COLUMN `data` TEXT NOT NULL DEFAULT ''")
      db.execSQL("ALTER TABLE `PlayQueue` ADD COLUMN `account` TEXT")
      db.execSQL("ALTER TABLE `PlayQueue` ADD COLUMN `pwd` TEXT")

      db.execSQL("CREATE TABLE IF NOT EXISTS `WebDav` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `alias` TEXT NOT NULL, `account` TEXT, `pwd` TEXT, `server` TEXT NOT NULL, `lastPath` TEXT, `createAt` INTEGER NOT NULL)")
    }
  }

  val migration4to5 = object : Migration(4, 5) {
    @SuppressLint("Range")
    override fun migrate(db: SupportSQLiteDatabase) {
      val temp = ArrayList<WebDav>()
      val cursor = db.query("select * from `Webdav`")
      while (cursor.moveToNext()) {
        temp.add(
          WebDav(
            cursor.getString(cursor.getColumnIndex("alias")),
            cursor.getString(cursor.getColumnIndex("account")),
            cursor.getString(cursor.getColumnIndex("pwd")),
            cursor.getString(cursor.getColumnIndex("server")),
            cursor.getString(cursor.getColumnIndex("server")),
            cursor.getLong(cursor.getColumnIndex("createAt"))
          ).apply {
            id = cursor.getInt(cursor.getColumnIndex("id"))
          })
      }
      print(temp)
      db.execSQL("DROP TABLE `WebDav`")
      db.execSQL("CREATE TABLE IF NOT EXISTS `WebDav` (`alias` TEXT NOT NULL, `account` TEXT NOT NULL, `pwd` TEXT NOT NULL, `server` TEXT NOT NULL, `lastUrl` TEXT NOT NULL, `createAt` INTEGER NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
      temp.forEach { webDav ->
        db.insert("WebDav", SQLiteDatabase.CONFLICT_REPLACE, ContentValues().apply {
          put("alias", webDav.alias)
          put("account", webDav.account)
          put("pwd", webDav.pwd)
          put("server", webDav.server)
          put("lastUrl", webDav.lastUrl)
          put("createAt", webDav.createAt)
          put("id", webDav.id)
        })
      }
    }
  }

  val migration5to6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("CREATE TABLE IF NOT EXISTS `MetaDataCache` (`url` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `duration` INTEGER NOT NULL, `fileSize` INTEGER NOT NULL, `lastModified` INTEGER NOT NULL, `year` TEXT NOT NULL, `genre` TEXT NOT NULL, `track` TEXT NOT NULL, `updateTime` INTEGER NOT NULL, PRIMARY KEY (`url`))")
    }
  }

  val migration6to7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("CREATE TABLE IF NOT EXISTS `Smb` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `alias` TEXT NOT NULL, `domain` TEXT, `account` TEXT NOT NULL, `pwd` TEXT NOT NULL, `server` TEXT NOT NULL, `share` TEXT NOT NULL, `lastUrl` TEXT NOT NULL, `createAt` INTEGER NOT NULL)")
    }
  }

  val migration7to8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("CREATE TABLE IF NOT EXISTS `SongTagCache` (`path` TEXT NOT NULL, `tags` TEXT NOT NULL, `updateTime` INTEGER NOT NULL, PRIMARY KEY(`path`))")
    }
  }

  val migration8to9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("CREATE TABLE IF NOT EXISTS `TagEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
    }
  }

  val migration9to10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("CREATE TABLE IF NOT EXISTS `SearchHistory` (`keyword` TEXT NOT NULL, `lastSearchTime` INTEGER NOT NULL, `searchCount` INTEGER NOT NULL, PRIMARY KEY(`keyword`))")
    }
  }

  val migration10to11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
      // 播放会话（数据分析）
      db.execSQL("CREATE TABLE IF NOT EXISTS `PlayEvent` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `path` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER NOT NULL, `lastActiveTime` INTEGER NOT NULL, `playedMs` INTEGER NOT NULL, `maxPositionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `completed` INTEGER NOT NULL, `skipped` INTEGER NOT NULL)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_PlayEvent_startTime` ON `PlayEvent` (`startTime`)")
      // 应用使用会话（数据分析）
      db.execSQL("CREATE TABLE IF NOT EXISTS `AppOpenSession` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `openTime` INTEGER NOT NULL, `closeTime` INTEGER NOT NULL)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_AppOpenSession_openTime` ON `AppOpenSession` (`openTime`)")
    }
  }

  val migration11to12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
      // 每小时播放时长（数据分析）：按整点小时拆分，用于每日播放时长与时段热力图
      db.execSQL("CREATE TABLE IF NOT EXISTS `PlayHourStat` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `hourStart` INTEGER NOT NULL, `playedMs` INTEGER NOT NULL)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_PlayHourStat_hourStart` ON `PlayHourStat` (`hourStart`)")
      // 历史会话没有按小时拆分的信息，按旧口径（整段算在开始的那个小时）回填，避免升级后报表变空
      db.execSQL("INSERT INTO `PlayHourStat` (`hourStart`, `playedMs`) SELECT (`startTime` / 3600000) * 3600000, `playedMs` FROM `PlayEvent` WHERE `playedMs` > 0")
    }
  }
}