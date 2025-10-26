// app/src/main/java/com/vadim170/bitchatscanner/DetectionStore.kt
package com.vadim170.bitchatscanner

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class DetectionRow(
    val timestamp: Long,
    val address: String,
    val name: String?,
    val rssi: Int,
    val lat: Double?,
    val lon: Double?,
    val accuracy: Float?,
    val provider: String?,
    val serviceDataHex: String?
)

data class LocationPoint(
    val lat: Double,
    val lon: Double,
    val rssi: Int,
    val accuracy: Float?
)

data class DeviceSummary(
    val address: String,
    val name: String?,
    val lastSeen: Long,
    val firstSeen: Long,
    val rssi: Int,
    val lat: Double?,
    val lon: Double?,
    val accuracy: Float?,
    val provider: String?,
    val serviceDataHex: String?,
    val detectionCount: Int
)

private const val DB_NAME = "bitchat_log.db"
private const val DB_VER = 3
private const val TABLE = "detections"
private const val DEVICES_TABLE = "device_summaries"
private const val MAX_ROWS = 1000

class DetectionDbHelper(ctx: Context) :
    SQLiteOpenHelper(ctx, DB_NAME, null, DB_VER) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                address TEXT NOT NULL,
                name TEXT,
                rssi INTEGER NOT NULL,
                lat REAL,
                lon REAL,
                accuracy REAL,
                provider TEXT,
                service_data_hex TEXT
            );
            CREATE INDEX idx_${TABLE}_ts ON $TABLE(timestamp);
            """.trimIndent()
        )
        
        db.execSQL(
            """
            CREATE TABLE $DEVICES_TABLE (
                address TEXT PRIMARY KEY,
                name TEXT,
                last_seen INTEGER NOT NULL,
                first_seen INTEGER NOT NULL,
                rssi INTEGER NOT NULL,
                lat REAL,
                lon REAL,
                accuracy REAL,
                provider TEXT,
                service_data_hex TEXT,
                detection_count INTEGER NOT NULL DEFAULT 1
            );
            CREATE INDEX idx_${DEVICES_TABLE}_last_seen ON $DEVICES_TABLE(last_seen);
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                """
                CREATE TABLE $DEVICES_TABLE (
                    address TEXT PRIMARY KEY,
                    name TEXT,
                    last_seen INTEGER NOT NULL,
                    rssi INTEGER NOT NULL,
                    lat REAL,
                    lon REAL,
                    accuracy REAL,
                    provider TEXT,
                    service_data_hex TEXT,
                    detection_count INTEGER NOT NULL DEFAULT 1
                );
                CREATE INDEX idx_${DEVICES_TABLE}_last_seen ON $DEVICES_TABLE(last_seen);
                """.trimIndent()
            )
        }
        if (oldVersion < 3) {
            // Добавляем колонку first_seen
            db.execSQL("ALTER TABLE $DEVICES_TABLE ADD COLUMN first_seen INTEGER NOT NULL DEFAULT 0")
            // Устанавливаем first_seen равным last_seen для существующих записей
            db.execSQL("UPDATE $DEVICES_TABLE SET first_seen = last_seen WHERE first_seen = 0")
        }
    }

    fun insertAndPrune(row: DetectionRow) {
        writableDatabase.beginTransaction()
        try {
            // Вставляем в основную таблицу
            val cv = ContentValues().apply {
                put("timestamp", row.timestamp)
                put("address", row.address)
                put("name", row.name)
                put("rssi", row.rssi)
                put("lat", row.lat)
                put("lon", row.lon)
                put("accuracy", row.accuracy)
                put("provider", row.provider)
                put("service_data_hex", row.serviceDataHex)
            }
            writableDatabase.insert(TABLE, null, cv)

            // Обновляем таблицу устройств
            val deviceCv = ContentValues().apply {
                put("address", row.address)
                put("name", row.name)
                put("last_seen", row.timestamp)
                put("rssi", row.rssi)
                put("lat", row.lat)
                put("lon", row.lon)
                put("accuracy", row.accuracy)
                put("provider", row.provider)
                put("service_data_hex", row.serviceDataHex)
            }
            
            // Используем INSERT OR REPLACE и увеличиваем счетчик
            writableDatabase.execSQL(
                """
                INSERT OR REPLACE INTO $DEVICES_TABLE 
                (address, name, last_seen, first_seen, rssi, lat, lon, accuracy, provider, service_data_hex, detection_count)
                VALUES (?, ?, ?, 
                    COALESCE((SELECT first_seen FROM $DEVICES_TABLE WHERE address = ?), ?), 
                    ?, ?, ?, ?, ?, ?, 
                    COALESCE((SELECT detection_count + 1 FROM $DEVICES_TABLE WHERE address = ?), 1))
                """.trimIndent(),
                arrayOf(
                    row.address, row.name, row.timestamp, row.address, row.timestamp,
                    row.rssi, row.lat, row.lon, row.accuracy, row.provider, row.serviceDataHex, 
                    row.address
                )
            )

            // Удаляем старые записи из основной таблицы
            writableDatabase.execSQL(
                """
                DELETE FROM $TABLE
                WHERE id NOT IN (
                    SELECT id FROM $TABLE
                    ORDER BY timestamp DESC
                    LIMIT $MAX_ROWS
                )
                """.trimIndent()
            )
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    /** Возвращает последние N записей, отсортированные по убыванию времени. */
    fun latest(limit: Int = MAX_ROWS): List<DetectionRow> {
        val res = mutableListOf<DetectionRow>()
        readableDatabase.rawQuery(
            """
            SELECT timestamp,address,name,rssi,lat,lon,accuracy,provider,service_data_hex
            FROM $TABLE
            ORDER BY timestamp DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString())
        ).use { c ->
            val tsI = 0; val addrI = 1; val nameI = 2; val rssiI = 3
            val latI = 4; val lonI = 5; val accI = 6; val provI = 7; val sdI = 8
            while (c.moveToNext()) {
                res += DetectionRow(
                    timestamp = c.getLong(tsI),
                    address = c.getString(addrI),
                    name = c.getString(nameI),
                    rssi = c.getInt(rssiI),
                    lat = if (!c.isNull(latI)) c.getDouble(latI) else null,
                    lon = if (!c.isNull(lonI)) c.getDouble(lonI) else null,
                    accuracy = if (!c.isNull(accI)) c.getFloat(accI) else null,
                    provider = c.getString(provI),
                    serviceDataHex = c.getString(sdI)
                )
            }
        }
        return res
    }

    /** Очищает все записи из базы данных. */
    fun clearAll() {
        writableDatabase.delete(TABLE, null, null)
        writableDatabase.delete(DEVICES_TABLE, null, null)
    }

    /** Возвращает все обнаруженные устройства, отсортированные по времени последнего обнаружения. */
    fun getAllDevices(): List<DeviceSummary> {
        val res = mutableListOf<DeviceSummary>()
        readableDatabase.rawQuery(
            """
            SELECT address, name, last_seen, first_seen, rssi, lat, lon, accuracy, provider, service_data_hex, detection_count
            FROM $DEVICES_TABLE
            ORDER BY last_seen DESC
            """.trimIndent(),
            null
        ).use { c ->
            val addrI = 0; val nameI = 1; val lastSeenI = 2; val firstSeenI = 3; val rssiI = 4
            val latI = 5; val lonI = 6; val accI = 7; val provI = 8; val sdI = 9; val countI = 10
            while (c.moveToNext()) {
                res += DeviceSummary(
                    address = c.getString(addrI),
                    name = c.getString(nameI),
                    lastSeen = c.getLong(lastSeenI),
                    firstSeen = c.getLong(firstSeenI),
                    rssi = c.getInt(rssiI),
                    lat = if (!c.isNull(latI)) c.getDouble(latI) else null,
                    lon = if (!c.isNull(lonI)) c.getDouble(lonI) else null,
                    accuracy = if (!c.isNull(accI)) c.getFloat(accI) else null,
                    provider = c.getString(provI),
                    serviceDataHex = c.getString(sdI),
                    detectionCount = c.getInt(countI)
                )
            }
        }
        return res
    }

    /** Возвращает устройства, обнаруженные за последний час. */
    fun getRecentDevices(hoursBack: Int = 1): List<DeviceSummary> {
        val cutoffTime = System.currentTimeMillis() - (hoursBack * 60 * 60 * 1000)
        val res = mutableListOf<DeviceSummary>()
        readableDatabase.rawQuery(
            """
            SELECT address, name, last_seen, first_seen, rssi, lat, lon, accuracy, provider, service_data_hex, detection_count
            FROM $DEVICES_TABLE
            WHERE last_seen > ?
            ORDER BY last_seen DESC
            """.trimIndent(),
            arrayOf(cutoffTime.toString())
        ).use { c ->
            val addrI = 0; val nameI = 1; val lastSeenI = 2; val firstSeenI = 3; val rssiI = 4
            val latI = 5; val lonI = 6; val accI = 7; val provI = 8; val sdI = 9; val countI = 10
            while (c.moveToNext()) {
                res += DeviceSummary(
                    address = c.getString(addrI),
                    name = c.getString(nameI),
                    lastSeen = c.getLong(lastSeenI),
                    firstSeen = c.getLong(firstSeenI),
                    rssi = c.getInt(rssiI),
                    lat = if (!c.isNull(latI)) c.getDouble(latI) else null,
                    lon = if (!c.isNull(lonI)) c.getDouble(lonI) else null,
                    accuracy = if (!c.isNull(accI)) c.getFloat(accI) else null,
                    provider = c.getString(provI),
                    serviceDataHex = c.getString(sdI),
                    detectionCount = c.getInt(countI)
                )
            }
        }
        return res
    }
    
    /** Возвращает все координаты обнаружений для конкретного устройства с RSSI и точностью. */
    fun getDeviceLocations(deviceAddress: String): List<LocationPoint> {
        val locations = mutableListOf<LocationPoint>()
        readableDatabase.rawQuery(
            """
            SELECT DISTINCT lat, lon, rssi, accuracy
            FROM $TABLE
            WHERE address = ? AND lat IS NOT NULL AND lon IS NOT NULL
            """.trimIndent(),
            arrayOf(deviceAddress)
        ).use { c ->
            val latI = 0; val lonI = 1; val rssiI = 2; val accuracyI = 3
            while (c.moveToNext()) {
                val lat = c.getDouble(latI)
                val lon = c.getDouble(lonI)
                val rssi = c.getInt(rssiI)
                val accuracy = if (c.isNull(accuracyI)) null else c.getFloat(accuracyI)
                locations.add(LocationPoint(lat, lon, rssi, accuracy))
            }
        }
        return locations
    }
}