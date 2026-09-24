package com.ehs.tbttracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.ehs.tbttracker.domain.model.SyncState

@Database(entities = [TbtEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class TbtDatabase : RoomDatabase() {
    abstract fun tbtDao(): TbtDao

    companion object {
        const val NAME = "ehs_tbt.db"
    }
}

class Converters {
    @TypeConverter fun fromSyncState(s: SyncState): String = s.name
    @TypeConverter fun toSyncState(s: String): SyncState = SyncState.valueOf(s)
}
