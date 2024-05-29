import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = ("CREATE TABLE $TABLE_NAME ("
                + "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "$COLUMN_COUNT INTEGER, "
                + "$COLUMN_TIMESTAMP TEXT)")
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_TIMESTAMP TEXT")
        }
    }

    fun insertCount(count: Int, timestamp: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_COUNT, count)
            put(COLUMN_TIMESTAMP, timestamp)
        }
        db.insert(TABLE_NAME, null, values)
    }

    fun getAllCounts(): List<Pair<Int, String>> {
        val counts = mutableListOf<Pair<Int, String>>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT $COLUMN_COUNT, $COLUMN_TIMESTAMP FROM $TABLE_NAME", null)
        if (cursor.moveToFirst()) {
            do {
                val count = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_COUNT))
                val timestamp = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                counts.add(Pair(count, timestamp))
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
        private const val DATABASE_VERSION = 2
        private const val DATABASE_NAME = "countDatabase.db"
        private const val TABLE_NAME = "counts"
        private const val COLUMN_ID = "id"
        private const val COLUMN_COUNT = "count"
        private const val COLUMN_TIMESTAMP = "timestamp"
    }
}