import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = ("CREATE TABLE $TABLE_NAME ("
                + "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "$COLUMN_COUNT INTEGER)")
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun insertCount(count: Int) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_COUNT, count)
        }
        db.insert(TABLE_NAME, null, values)
    }

    fun getAllCounts(): List<Int> {
        val counts = mutableListOf<Int>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT $COLUMN_COUNT FROM $TABLE_NAME", null)
        if (cursor.moveToFirst()) {
            do {
                counts.add(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_COUNT)))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return counts
    }

    fun clearDatabase() {
        val db = this.writableDatabase
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }
    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "countDatabase.db"
        private const val TABLE_NAME = "counts"
        private const val COLUMN_ID = "id"
        private const val COLUMN_COUNT = "count"
    }
}