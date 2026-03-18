package com.hita.agent.core.data.localrag.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [IndexedFileEntity::class, DocChunkEntity::class, DocChunkFtsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class LocalRagDatabase : RoomDatabase() {
    abstract fun dao(): LocalRagDao

    companion object {
        fun build(context: Context): LocalRagDatabase {
            return Room.databaseBuilder(context, LocalRagDatabase::class.java, "local_rag.db")
                .build()
        }
    }
}
