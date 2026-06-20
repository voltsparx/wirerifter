package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PacketEntity::class,
        DeviceEntity::class,
        IdsRuleEntity::class,
        SessionEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class WireRifterDatabase : RoomDatabase() {
    abstract fun packetDao(): PacketDao
    abstract fun deviceDao(): DeviceDao
    abstract fun idsRuleDao(): IdsRuleDao
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var INSTANCE: WireRifterDatabase? = null

        fun getDatabase(context: Context): WireRifterDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WireRifterDatabase::class.java,
                    "wirerifter_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
