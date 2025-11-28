package com.bytecode.luyuan.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bytecode.luyuan.data.model.ApiConfigEntity
import com.bytecode.luyuan.data.model.Message
import com.bytecode.luyuan.data.model.Session
import com.bytecode.luyuan.data.model.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users LIMIT 1")
    fun getCurrentUser(): Flow<User?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Query("DELETE FROM users")
    suspend fun clearUsers()
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionById(id: String): Session?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: Session)

    @Update
    suspend fun updateSession(session: Session)

    @Delete
    suspend fun deleteSession(session: Session)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSessionById(id: String)

    @Query("DELETE FROM sessions")
    suspend fun clearAllSessions()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<Message>>
    
    /** 同步获取消息列表，用于构建 AI 上下文 */
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSessionSync(sessionId: String): List<Message>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    @Update
    suspend fun updateMessage(message: Message)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesBySessionId(sessionId: String)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId AND timestamp > :timestamp")
    suspend fun deleteMessagesAfter(sessionId: String, timestamp: Long)
    
    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()
}

@Dao
interface ApiConfigDao {
    @Query("SELECT * FROM api_configs ORDER BY createdAt ASC")
    fun getAllConfigs(): Flow<List<ApiConfigEntity>>
    
    @Query("SELECT * FROM api_configs WHERE isDefault = 1 LIMIT 1")
    fun getDefaultConfig(): Flow<ApiConfigEntity?>
    
    @Query("SELECT * FROM api_configs WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultConfigSync(): ApiConfigEntity?
    
    @Query("SELECT * FROM api_configs WHERE id = :id")
    suspend fun getConfigById(id: String): ApiConfigEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: ApiConfigEntity)
    
    @Update
    suspend fun updateConfig(config: ApiConfigEntity)
    
    @Delete
    suspend fun deleteConfig(config: ApiConfigEntity)
    
    @Query("DELETE FROM api_configs WHERE id = :id")
    suspend fun deleteConfigById(id: String)
    
    @Query("UPDATE api_configs SET isDefault = 0")
    suspend fun clearAllDefaults()
    
    @Query("UPDATE api_configs SET isDefault = 1 WHERE id = :id")
    suspend fun setAsDefault(id: String)
}
