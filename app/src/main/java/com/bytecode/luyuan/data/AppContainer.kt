package com.bytecode.luyuan.data

import android.content.Context
import androidx.room.Room
import com.bytecode.luyuan.data.local.AppDatabase
import com.bytecode.luyuan.data.local.UserPreferencesDataStore
import com.bytecode.luyuan.data.remote.OpenAiService
import com.bytecode.luyuan.data.repository.AppRepository
import com.bytecode.luyuan.data.repository.OfflineRepository

/**
 * 依赖注入容器接口
 */
interface AppContainer {
    val appRepository: AppRepository
}

/**
 * 默认依赖注入容器实现
 * 
 * 提供 Room 数据库、DataStore 和网络服务的单例实例
 */
class DefaultAppContainer(private val context: Context) : AppContainer {
    
    private val database: AppDatabase by lazy {
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "ai_client_database"
        )
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
        .build()
    }

    private val userPreferencesDataStore: UserPreferencesDataStore by lazy {
        UserPreferencesDataStore(context)
    }

    private val openAiService: OpenAiService by lazy {
        OpenAiService()
    }

    override val appRepository: AppRepository by lazy {
        OfflineRepository(
            userDao = database.userDao(),
            sessionDao = database.sessionDao(),
            messageDao = database.messageDao(),
            apiConfigDao = database.apiConfigDao(),
            userPreferencesDataStore = userPreferencesDataStore,
            openAiService = openAiService
        )
    }
}
