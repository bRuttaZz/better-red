package site.brutt.betterred.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import site.brutt.betterred.Words
import site.brutt.betterred.domain.DayLog
import site.brutt.betterred.domain.Flow
import site.brutt.betterred.domain.Mood
import site.brutt.betterred.domain.Sex
import site.brutt.betterred.domain.Symptom
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate

/**
 * The whole app's storage: one plain SQLite file (better_red.db).
 *
 * Tables
 *   day_log  (date TEXT PK 'YYYY-MM-DD', flow TEXT, mood TEXT, symptoms TEXT csv, note TEXT, sex TEXT, updated_at INTEGER)
 *   settings (key TEXT PK, value TEXT)
 *   feed     (id INTEGER PK, url TEXT UNIQUE, title TEXT, enabled INTEGER, builtin INTEGER)   ← news sources
 *   read_item(link TEXT PK, read_at INTEGER)                                                ← articles she opened
 *
 * WAL is turned off, so the .db file on disk is always complete and can be
 * exported by copying it, and opened by any SQLite tool on a computer.
 *
 * Not thread-safe on its own: the ViewModel calls it from one coroutine at a time.
 */
class Store(private val context: Context) {

    private class Helper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        init { setWriteAheadLoggingEnabled(false) }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE day_log(
                    date TEXT PRIMARY KEY NOT NULL,
                    flow TEXT,
                    mood TEXT,
                    symptoms TEXT NOT NULL DEFAULT '',
                    note TEXT NOT NULL DEFAULT '',
                    sex TEXT,
                    updated_at INTEGER NOT NULL)"""
            )
            db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
            createNewsTables(db)
        }

        private fun createNewsTables(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE feed(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    url TEXT NOT NULL UNIQUE,
                    title TEXT NOT NULL,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    builtin INTEGER NOT NULL DEFAULT 0)"""
            )
            db.execSQL("CREATE TABLE read_item(link TEXT PRIMARY KEY NOT NULL, read_at INTEGER NOT NULL)")
            insertDefaultFeeds(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // One step per version, so old databases and old backups upgrade cleanly.
            if (oldVersion < 2) db.execSQL("ALTER TABLE day_log ADD COLUMN sex TEXT")
            if (oldVersion < 3) createNewsTables(db)
        }
    }

    private val helper = Helper(context)

    /** Close when done (used by widgets, which open the database briefly). */
    fun close() = helper.close()
    private val db: SQLiteDatabase get() = helper.writableDatabase
    private val dbFile: File get() = context.getDatabasePath(DB_NAME)

    // ---------- day logs ----------

    fun loadLogs(): Map<LocalDate, DayLog> {
        val out = HashMap<LocalDate, DayLog>()
        db.rawQuery("SELECT date, flow, mood, symptoms, note, sex FROM day_log", null).use { c ->
            while (c.moveToNext()) {
                val date = runCatching { LocalDate.parse(c.getString(0)) }.getOrNull() ?: continue
                out[date] = DayLog(
                    date = date,
                    flow = c.getStringOrNull(1)?.let { enumOrNull<Flow>(it) },
                    mood = c.getStringOrNull(2)?.let { enumOrNull<Mood>(it) },
                    symptoms = c.getString(3).split(',').mapNotNull { enumOrNull<Symptom>(it) }.toSet(),
                    note = c.getString(4),
                    sex = c.getStringOrNull(5)?.let { enumOrNull<Sex>(it) },
                )
            }
        }
        return out
    }

    /** Saves the day, or deletes it if nothing is left on it. */
    fun saveLog(log: DayLog) {
        if (log.isEmpty) {
            db.delete("day_log", "date = ?", arrayOf(log.date.toString()))
            return
        }
        val values = ContentValues().apply {
            put("date", log.date.toString())
            put("flow", log.flow?.name)
            put("mood", log.mood?.name)
            put("symptoms", log.symptoms.joinToString(",") { it.name })
            put("note", log.note.trim())
            put("sex", log.sex?.name)
            put("updated_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("day_log", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ---------- settings ----------

    fun loadSettings(): Map<String, String> {
        val out = HashMap<String, String>()
        db.rawQuery("SELECT key, value FROM settings", null).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = c.getString(1)
        }
        return out
    }

    fun putSettings(values: Map<String, String>) {
        db.beginTransaction()
        try {
            for ((k, v) in values) {
                val cv = ContentValues().apply { put("key", k); put("value", v) }
                db.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---------- news feeds ----------

    fun loadFeeds(): List<Feed> {
        val out = mutableListOf<Feed>()
        db.rawQuery("SELECT id, url, title, enabled, builtin FROM feed ORDER BY builtin DESC, id", null).use { c ->
            while (c.moveToNext()) out += Feed(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3) == 1, c.getInt(4) == 1)
        }
        return out
    }

    fun addFeed(url: String, title: String) {
        val cv = ContentValues().apply { put("url", url); put("title", title); put("enabled", 1); put("builtin", 0) }
        db.insertOrThrow("feed", null, cv)
    }

    fun setFeedEnabled(id: Long, enabled: Boolean) {
        db.update("feed", ContentValues().apply { put("enabled", if (enabled) 1 else 0) }, "id = ?", arrayOf(id.toString()))
    }

    /** Only her own feeds can be removed; built-in ones can just be turned off. */
    fun removeFeed(id: Long) {
        db.delete("feed", "id = ? AND builtin = 0", arrayOf(id.toString()))
    }

    fun loadReadLinks(): Set<String> {
        // Forget articles read more than a year ago; feeds only show recent posts anyway.
        val yearAgo = System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000
        db.delete("read_item", "read_at < ?", arrayOf(yearAgo.toString()))
        val out = HashSet<String>()
        db.rawQuery("SELECT link FROM read_item", null).use { c -> while (c.moveToNext()) out += c.getString(0) }
        return out
    }

    fun markRead(link: String) {
        val cv = ContentValues().apply { put("link", link); put("read_at", System.currentTimeMillis()) }
        db.insertWithOnConflict("read_item", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ---------- whole database ----------

    fun deleteEverything() {
        db.delete("day_log", null, null)
        db.delete("settings", null, null)
        db.delete("read_item", null, null)
        db.delete("feed", "builtin = 0", null)
        db.update("feed", ContentValues().apply { put("enabled", 1) }, null, null)
    }

    /** Writes a copy of the database file. */
    fun exportTo(out: OutputStream) {
        helper.close() // make sure nothing is half-written; it reopens on next use
        dbFile.inputStream().use { it.copyTo(out) }
    }

    /**
     * Replaces the database with a backup. The file is checked first, so a wrong
     * file can't wipe her data.
     */
    fun importFrom(input: InputStream) {
        val tmp = File(context.cacheDir, "import.db")
        try {
            tmp.outputStream().use { input.copyTo(it) }
            validateBackup(tmp)
            helper.close()
            File(dbFile.path + "-journal").delete()
            tmp.copyTo(dbFile, overwrite = true)
        } finally {
            tmp.delete()
        }
        db // reopen now so a broken file fails here, not later
    }

    private fun validateBackup(file: File) {
        val backup = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            throw IllegalArgumentException(Words.Messages.NOT_A_BACKUP, e)
        }
        backup.use {
            val tables = it.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('day_log', 'settings')", null
            ).use { c -> c.count }
            require(tables == 2) { Words.Messages.NOT_A_BACKUP }
            require(it.version <= DB_VERSION) { Words.Messages.BACKUP_TOO_NEW }
        }
    }

    companion object {
        const val DB_NAME = "better_red.db"
        const val DB_VERSION = 3

        private fun insertDefaultFeeds(db: SQLiteDatabase) {
            for ((url, title) in Feeds.DEFAULTS) {
                val cv = ContentValues().apply { put("url", url); put("title", title); put("enabled", 1); put("builtin", 1) }
                db.insertWithOnConflict("feed", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
            }
        }
    }
}

private fun android.database.Cursor.getStringOrNull(i: Int): String? = if (isNull(i)) null else getString(i)

private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
    if (name.isBlank()) null else runCatching { enumValueOf<T>(name) }.getOrNull()
