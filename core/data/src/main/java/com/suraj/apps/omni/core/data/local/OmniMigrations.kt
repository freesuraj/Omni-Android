package com.suraj.apps.omni.core.data.local

import androidx.room.migration.Migration

object OmniMigrations {
    const val SCHEMA_VERSION = 1

    // Keep the registry in place so schema upgrades only need to append new migrations.
    val ALL: Array<Migration> = emptyArray()
}
