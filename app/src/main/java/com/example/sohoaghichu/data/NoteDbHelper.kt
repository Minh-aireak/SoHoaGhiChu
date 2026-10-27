package com.example.sohoaghichu.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Lưu trữ ghi chú bằng SQLite.
 */
class NoteDbHelper private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "so_hoa_ghi_chu.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_NOTES = "notes"
        private const val COL_ID = "id"
        private const val COL_TITLE = "title"
        private const val COL_CONTENT = "content"
        private const val COL_IMAGE_PATH = "image_path"
        private const val COL_CREATED_AT = "created_at"
        private const val COL_UPDATED_AT = "updated_at"

        @Volatile
        private var instance: NoteDbHelper? = null

        // @JvmStatic để phía Java gọi được NoteDbHelper.getInstance(context)
        @JvmStatic
        fun getInstance(context: Context): NoteDbHelper =
            instance ?: synchronized(this) {
                instance ?: NoteDbHelper(context.applicationContext).also { instance = it }
            }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABLE_NOTES (" +
                    "$COL_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$COL_TITLE TEXT NOT NULL, " +
                    "$COL_CONTENT TEXT NOT NULL DEFAULT '', " +
                    "$COL_IMAGE_PATH TEXT, " +
                    "$COL_CREATED_AT INTEGER NOT NULL, " +
                    "$COL_UPDATED_AT INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NOTES")
        onCreate(db)
    }

    fun insertNote(note: Note): Long {
        val id = writableDatabase.insert(TABLE_NOTES, null, toValues(note))
        note.id = id
        return id
    }

    fun updateNote(note: Note): Int =
        writableDatabase.update(TABLE_NOTES, toValues(note), "$COL_ID = ?", arrayOf(note.id.toString()))

    fun deleteNote(id: Long): Int =
        writableDatabase.delete(TABLE_NOTES, "$COL_ID = ?", arrayOf(id.toString()))

    /** Trả về null nếu không có ghi chú với id này. */
    fun getNoteById(id: Long): Note? =
        readableDatabase.query(TABLE_NOTES, null, "$COL_ID = ?", arrayOf(id.toString()), null, null, null)
            .use { c -> if (c.moveToFirst()) fromCursor(c) else null }

    /** Lấy tất cả ghi chú, mới cập nhật nằm trên cùng. */
    fun getAllNotes(): List<Note> =
        readableDatabase.query(TABLE_NOTES, null, null, null, null, null, "$COL_UPDATED_AT DESC")
            .use { c ->
                val notes = ArrayList<Note>()
                while (c.moveToNext()) notes.add(fromCursor(c))
                notes
            }

    private fun toValues(note: Note) = ContentValues().apply {
        put(COL_TITLE, note.title)
        put(COL_CONTENT, note.content)
        put(COL_IMAGE_PATH, note.imagePath)
        put(COL_CREATED_AT, note.createdAt)
        put(COL_UPDATED_AT, note.updatedAt)
    }

    private fun fromCursor(c: Cursor) = Note(
        id = c.getLong(c.getColumnIndexOrThrow(COL_ID)),
        title = c.getString(c.getColumnIndexOrThrow(COL_TITLE)) ?: "",
        content = c.getString(c.getColumnIndexOrThrow(COL_CONTENT)) ?: "",
        imagePath = c.getString(c.getColumnIndexOrThrow(COL_IMAGE_PATH)),
        createdAt = c.getLong(c.getColumnIndexOrThrow(COL_CREATED_AT)),
        updatedAt = c.getLong(c.getColumnIndexOrThrow(COL_UPDATED_AT))
    )
}
