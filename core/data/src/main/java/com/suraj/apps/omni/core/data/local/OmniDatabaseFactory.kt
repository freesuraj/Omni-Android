package com.suraj.apps.omni.core.data.local

import android.content.Context
import androidx.room.Room

object OmniDatabaseFactory {
    private const val DATABASE_NAME = "omni.db"

    fun create(context: Context): OmniDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            OmniDatabase::class.java,
            DATABASE_NAME
        )
            .addMigrations(*OmniMigrations.ALL)
            .build()
    }
}
