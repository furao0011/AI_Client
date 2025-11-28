package com.bytecode.luyuan.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bytecode.luyuan.data.model.ApiConfigEntity
import com.bytecode.luyuan.data.model.Message
import com.bytecode.luyuan.data.model.Session
import com.bytecode.luyuan.data.model.User

@Database(entities = [User::class, Session::class, Message::class, ApiConfigEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun apiConfigDao(): ApiConfigDao
    
    companion object {
        /**
         * 数据库迁移：v1 -> v2
         * 添加 imageBase64 字段用于多模态输入
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE messages ADD COLUMN imageBase64 TEXT")
            }
        }
        
        /**
         * 数据库迁移：v2 -> v3
         * 添加 api_configs 表用于多 API 配置管理
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS api_configs (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        baseUrl TEXT NOT NULL,
                        apiKey TEXT NOT NULL,
                        modelName TEXT NOT NULL,
                        isDefault INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """)
            }
        }
    }
}
