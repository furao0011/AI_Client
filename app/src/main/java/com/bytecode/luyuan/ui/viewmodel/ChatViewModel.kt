package com.bytecode.luyuan.ui.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bytecode.luyuan.data.model.Message
import com.bytecode.luyuan.data.model.Session
import com.bytecode.luyuan.data.remote.ImageUploader
import com.bytecode.luyuan.data.repository.AppRepository
import com.bytecode.luyuan.data.repository.OnlineRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 图片上传状态
 */
sealed class ImageUploadState {
    /** 无图片 */
    data object None : ImageUploadState()
    
    /** 正在上传 */
    data class Uploading(val bitmap: Bitmap) : ImageUploadState()
    
    /** 上传成功 */
    data class Success(val bitmap: Bitmap, val imageUrl: String) : ImageUploadState()
    
    /** 上传失败 */
    data class Failed(val bitmap: Bitmap, val error: String) : ImageUploadState()
}

/**
 * 聊天界面的 ViewModel
 * 
 * 【架构说明】
 * - 服务端是唯一数据源 (Single Source of Truth)
 * - 会话和消息数据从服务端获取
 * - useServerAiService 开关只控制 AI 请求路由，不影响数据源
 */
class ChatViewModel(
    private val repository: AppRepository,
    private val imageUploader: ImageUploader? = null
) : ViewModel() {
    
    private val _currentSessionId = MutableStateFlow<String?>(null)
    
    /** 是否启用流式响应 */
    private val _streamingEnabled = MutableStateFlow(true)
    val streamingEnabled: StateFlow<Boolean> = _streamingEnabled.asStateFlow()
    
    /** 当前正在流式生成的消息内容 */
    private val _streamingContent = MutableStateFlow<String?>(null)
    val streamingContent: StateFlow<String?> = _streamingContent.asStateFlow()
    
    /** 是否正在加载（发送消息中） */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 错误信息 */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    /** 图片上传状态 */
    private val _imageUploadState = MutableStateFlow<ImageUploadState>(ImageUploadState.None)
    val imageUploadState: StateFlow<ImageUploadState> = _imageUploadState.asStateFlow()

    /** 所有会话列表 */
    val sessions: StateFlow<List<Session>> = repository.sessions
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    
    /** 当前会话信息 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentSession: StateFlow<Session?> = _currentSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else repository.sessions.map { list -> list.find { it.id == id } }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    
    /** 当前会话的消息列表 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<Message>> = _currentSessionId
        .flatMapLatest { sessionId ->
            if (sessionId == null) flowOf(emptyList())
            else repository.getMessages(sessionId)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * 设置当前会话 ID
     */
    fun setSessionId(sessionId: String) {
        if (_currentSessionId.value != sessionId) {
            _currentSessionId.value = sessionId
            // 从服务端加载消息
            loadMessages(sessionId)
        }
    }

    /**
     * 从服务端加载消息
     */
    private fun loadMessages(sessionId: String) {
        viewModelScope.launch {
            if (repository is OnlineRepository) {
                val result = repository.refreshMessages(sessionId)
                result.onFailure { throwable ->
                    _error.value = throwable.message ?: "加载消息失败"
                }
            }
        }
    }
    
    /**
     * 切换流式响应开关
     */
    fun toggleStreaming(enabled: Boolean) {
        _streamingEnabled.value = enabled
    }

    /**
     * 选择图片并开始上传
     */
    fun selectAndUploadImage(uri: Uri) {
        if (imageUploader == null) {
            _error.value = "图片上传服务未初始化"
            return
        }
        
        viewModelScope.launch {
            val bitmap = imageUploader.loadBitmapFromUri(uri)
            if (bitmap == null) {
                _error.value = "无法加载图片"
                return@launch
            }
            
            _imageUploadState.value = ImageUploadState.Uploading(bitmap)
            
            val result = imageUploader.uploadImage(uri)
            result.fold(
                onSuccess = { uploadResult ->
                    _imageUploadState.value = ImageUploadState.Success(bitmap, uploadResult.url)
                },
                onFailure = { throwable ->
                    _imageUploadState.value = ImageUploadState.Failed(bitmap, throwable.message ?: "上传失败")
                }
            )
        }
    }
    
    /**
     * 重试上传图片
     */
    fun retryUploadImage(uri: Uri) {
        selectAndUploadImage(uri)
    }
    
    /**
     * 清除选中的图片
     */
    fun clearSelectedImage() {
        _imageUploadState.value = ImageUploadState.None
    }

    /**
     * 发送消息
     */
    fun sendMessage(content: String, imageBase64: String? = null) {
        val sessionId = _currentSessionId.value ?: return
        if (_isLoading.value) return
        
        // 获取已上传的图片 URL
        val imageUrl = when (val state = _imageUploadState.value) {
            is ImageUploadState.Success -> state.imageUrl
            else -> null
        }
        
        _isLoading.value = true
        _error.value = null
        
        viewModelScope.launch {
            try {
                if (_streamingEnabled.value && imageUrl == null) {
                    // 流式响应模式（无图片）
                    _streamingContent.value = ""
                    repository.sendMessageStream(sessionId, content, imageUrl) { token ->
                        _streamingContent.value = (_streamingContent.value ?: "") + token
                    }
                    _streamingContent.value = null
                } else {
                    // 非流式响应模式或有图片
                    repository.sendMessage(sessionId, content, imageUrl)
                }
                
                // 发送成功后清除图片状态
                _imageUploadState.value = ImageUploadState.None
                
            } catch (e: Exception) {
                _error.value = e.message ?: "发送消息失败"
                _streamingContent.value = null
            }
            _isLoading.value = false
        }
    }

    /**
     * 编辑消息
     */
    fun editMessage(message: Message, newContent: String) {
        viewModelScope.launch {
            try {
                repository.editMessage(message, newContent)
            } catch (e: Exception) {
                _error.value = e.message ?: "编辑消息失败"
            }
        }
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _error.value = null
    }
}
