package com.bytecode.luyuan.data

import android.content.Context
import androidx.room.Room
import com.bytecode.luyuan.data.local.AppDatabase
import com.bytecode.luyuan.data.local.UserPreferencesDataStore
import com.bytecode.luyuan.data.remote.AuthService
import com.bytecode.luyuan.data.remote.ImageUploader
import com.bytecode.luyuan.data.remote.OpenAiService
import com.bytecode.luyuan.data.repository.AppRepository
import com.bytecode.luyuan.data.repository.OnlineRepository

/**
 * 依赖注入容器接口
 * 
 * 【架构说明】
 * - 服务端是唯一数据源 (Single Source of Truth)
 * - 本地数据库只作为缓存使用
 * - useServerAiService 开关只控制 AI 请求路由，不影响会话/消息数据源
 */
interface AppContainer {
    /** 主 Repository - 所有数据操作走服务端 */
    val appRepository: AppRepository
    
    /** 认证服务 */
    val authService: AuthService
    
    /** 用户偏好存储 */
    val userPreferencesDataStore: UserPreferencesDataStore
    
    /** 图片上传服务 */
    val imageUploader: ImageUploader
    
    /** OpenAI 服务（用于自定义 API 模式） */
    val openAiService: OpenAiService
}

/**
 * 默认依赖注入容器实现
 * 
 * 提供 Room 数据库、DataStore、认证服务和网络服务的单例实例
 * 
 * 【架构说明】
 * - 用户登录后，所有会话和消息数据从服务端获取
 * - 本地数据库作为缓存，加速加载和支持离线查看
 * - useServerAiService 开关只控制 AI 请求走服务端网关还是自定义 API
 */
class DefaultAppContainer(private val context: Context) : AppContainer {
    
    private val database: AppDatabase by lazy {
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "ai_client_database"
        )
        .addMigrations(
            AppDatabase.MIGRATION_1_2, 
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4
        )
        .build()
    }

    override val userPreferencesDataStore: UserPreferencesDataStore by lazy {
        UserPreferencesDataStore(context)
    }

    override val openAiService: OpenAiService by lazy {
        OpenAiService()
    }

    /**
     * 认证服务单例
     */
    override val authService: AuthService by lazy {
        AuthService(userPreferencesDataStore)
    }

    /**
     * 图片上传服务
     */
    override val imageUploader: ImageUploader by lazy {
        ImageUploader(
            context = context,
            authService = authService,
            userPreferencesDataStore = userPreferencesDataStore
        )
    }

    /**
     * 主 Repository - 服务端为唯一数据源
     * 
     * 所有会话和消息数据从服务端获取，本地只做缓存
     */
    override val appRepository: AppRepository by lazy {
        OnlineRepository(
            authService = authService,
            userPreferencesDataStore = userPreferencesDataStore,
            apiConfigDao = database.apiConfigDao(),
            openAiService = openAiService
        )
    }
}
