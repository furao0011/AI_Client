package com.bytecode.luyuan.data

import android.content.Context
import androidx.room.Room
import com.bytecode.luyuan.data.local.AppDatabase
import com.bytecode.luyuan.data.local.UserPreferencesDataStore
import com.bytecode.luyuan.data.remote.AuthService
import com.bytecode.luyuan.data.remote.OpenAiService
import com.bytecode.luyuan.data.repository.AppRepository
import com.bytecode.luyuan.data.repository.OfflineRepository
import com.bytecode.luyuan.data.repository.OnlineRepository
import kotlinx.coroutines.flow.Flow

/**
 * 依赖注入容器接口
 */
interface AppContainer {
    val appRepository: AppRepository
    val authService: AuthService
    val userPreferencesDataStore: UserPreferencesDataStore
    
    /** 离线模式 Repository（用于本地数据操作） */
    val offlineRepository: OfflineRepository
    
    /** 在线模式 Repository（用于服务端同步） */
    val onlineRepository: OnlineRepository
    
    /** 当前是否使用在线模式 */
    val useOnlineMode: Flow<Boolean>
}

/**
 * 默认依赖注入容器实现
 * 
 * 提供 Room 数据库、DataStore、认证服务和网络服务的单例实例
 * 支持离线/在线模式动态切换
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

    override val userPreferencesDataStore: UserPreferencesDataStore by lazy {
        UserPreferencesDataStore(context)
    }

    private val openAiService: OpenAiService by lazy {
        OpenAiService()
    }

    /**
     * 认证服务单例
     * 
     * 管理用户登录状态、Token 持久化和 API 调用
     */
    override val authService: AuthService by lazy {
        AuthService(userPreferencesDataStore)
    }

    /**
     * 离线模式 Repository
     * 
     * 使用本地 Room 数据库存储数据，直连 AI API
     */
    override val offlineRepository: OfflineRepository by lazy {
        OfflineRepository(
            userDao = database.userDao(),
            sessionDao = database.sessionDao(),
            messageDao = database.messageDao(),
            apiConfigDao = database.apiConfigDao(),
            userPreferencesDataStore = userPreferencesDataStore,
            openAiService = openAiService
        )
    }

    /**
     * 在线模式 Repository
     * 
     * 所有数据操作走服务端 API，支持会话和消息的服务端同步
     */
    override val onlineRepository: OnlineRepository by lazy {
        OnlineRepository(
            authService = authService,
            userPreferencesDataStore = userPreferencesDataStore,
            apiConfigDao = database.apiConfigDao()
        )
    }

    /**
     * 当前是否使用在线模式
     * 
     * 由 useServerAiService 设置决定
     */
    override val useOnlineMode: Flow<Boolean> = userPreferencesDataStore.useServerAiService

    /**
     * 默认 Repository（离线模式）
     * 
     * 注意：ViewModel 应根据 useOnlineMode 动态选择使用 offlineRepository 或 onlineRepository
     * 这里默认返回离线模式，确保向后兼容
     */
    override val appRepository: AppRepository by lazy {
        offlineRepository
    }
}
