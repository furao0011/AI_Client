package com.bytecode.luyuan.data.remote

import com.bytecode.luyuan.data.remote.dto.ChatCompletionRequest
import com.bytecode.luyuan.data.remote.dto.ChatCompletionResponse
import com.bytecode.luyuan.data.remote.dto.ChatMessage
import com.bytecode.luyuan.data.remote.dto.ImageContent
import com.bytecode.luyuan.data.remote.dto.ImageUrl
import com.bytecode.luyuan.data.remote.dto.StreamChatCompletionChunk
import com.bytecode.luyuan.data.remote.dto.TextContent
import com.bytecode.luyuan.data.remote.dto.VisionChatCompletionRequest
import com.bytecode.luyuan.data.remote.dto.VisionChatMessage
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 首条消息响应数据类
 * 包含 AI 回复内容和会话标题
 */
data class FirstMessageResponse(
    val content: String,
    val title: String
)

/**
 * OpenAI API 服务封装
 * 
 * 负责管理 Retrofit 实例和 API 调用，支持自动重试
 */
class OpenAiService {
    
    companion object {
        /** 默认重试次数 */
        const val DEFAULT_RETRY_COUNT = 3
        /** 重试延迟基数（毫秒）*/
        const val RETRY_DELAY_BASE_MS = 1000L
    }
    
    private var currentBaseUrl: String? = null
    private var retrofit: Retrofit? = null
    private var api: OpenAiApi? = null
    private val gson = Gson()
    
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
    
    /**
     * 带重试的请求执行器
     * 
     * @param maxRetries 最大重试次数
     * @param block 要执行的请求
     * @return 请求结果
     */
    private suspend fun <T> withRetry(
        maxRetries: Int = DEFAULT_RETRY_COUNT,
        block: suspend () -> Result<T>
    ): Result<T> {
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                val result = block()
                if (result.isSuccess) {
                    return result
                }
                
                // 如果是客户端错误（4xx），不重试
                val exception = result.exceptionOrNull()
                if (exception?.message?.contains("HTTP 4") == true) {
                    return result
                }
                
                lastException = exception as? Exception
            } catch (e: IOException) {
                lastException = e
            } catch (e: Exception) {
                return Result.failure(e)
            }
            
            // 指数退避延迟
            if (attempt < maxRetries - 1) {
                delay(RETRY_DELAY_BASE_MS * (attempt + 1))
            }
        }
        
        return Result.failure(lastException ?: Exception("Request failed after $maxRetries attempts"))
    }
    
    /**
     * 获取或创建 API 实例
     * 
     * @param baseUrl API 基础 URL
     * @return OpenAiApi 实例
     */
    private fun getApi(baseUrl: String): OpenAiApi {
        // 确保 baseUrl 以 / 结尾
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        
        // 如果 baseUrl 变化，重新创建 Retrofit 实例
        if (currentBaseUrl != normalizedUrl || api == null) {
            currentBaseUrl = normalizedUrl
            retrofit = Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            api = retrofit!!.create(OpenAiApi::class.java)
        }
        
        return api!!
    }
    
    /**
     * 发送聊天消息并获取 AI 回复（带自动重试）
     * 
     * @param baseUrl API 基础 URL
     * @param apiKey API 密钥
     * @param model 模型名称
     * @param messages 聊天历史消息
     * @return AI 回复内容，失败时返回错误信息
     */
    suspend fun chat(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>
    ): Result<String> = withRetry {
        try {
            val api = getApi(baseUrl)
            val request = ChatCompletionRequest(
                model = model,
                messages = messages
            )
            
            val response = api.chatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )
            
            if (response.isSuccessful) {
                val body = response.body()
                val content = body?.choices?.firstOrNull()?.message?.content
                
                if (content != null) {
                    Result.success(content)
                } else if (body?.error != null) {
                    Result.failure(Exception("API Error: ${body.error.message}"))
                } else {
                    Result.failure(Exception("Empty response from API"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Result.failure(Exception("HTTP ${response.code()}: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 发送首条消息并获取 AI 回复和会话标题
     * 
     * 要求 AI 以 JSON 格式返回：{"content": "回复内容", "title": "会话标题"}
     * 
     * @param baseUrl API 基础 URL
     * @param apiKey API 密钥
     * @param model 模型名称
     * @param userMessage 用户消息
     * @return FirstMessageResponse 包含回复内容和标题
     */
    suspend fun chatWithTitle(
        baseUrl: String,
        apiKey: String,
        model: String,
        userMessage: String
    ): Result<FirstMessageResponse> {
        return try {
            val systemPrompt = """You are a helpful AI assistant. For this first message in our conversation, please respond in the following JSON format:
{"content": "your helpful response here", "title": "a short title (max 20 chars) summarizing this conversation topic"}

Important:
1. The "content" should be your complete, helpful response to the user
2. The "title" should be a brief, descriptive title for this conversation (in the same language as the user's message)
3. Return ONLY the JSON object, no additional text"""
            
            val messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = userMessage)
            )
            
            val api = getApi(baseUrl)
            val request = ChatCompletionRequest(
                model = model,
                messages = messages
            )
            
            val response = api.chatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )
            
            if (response.isSuccessful) {
                val body = response.body()
                val rawContent = body?.choices?.firstOrNull()?.message?.content
                
                if (rawContent != null) {
                    // 尝试解析 JSON 响应
                    try {
                        val parsed = gson.fromJson(rawContent, FirstMessageResponse::class.java)
                        if (parsed.content.isNotBlank() && parsed.title.isNotBlank()) {
                            Result.success(parsed)
                        } else {
                            // JSON 解析成功但字段为空，使用原始内容
                            Result.success(FirstMessageResponse(
                                content = rawContent,
                                title = userMessage.take(20)
                            ))
                        }
                    } catch (e: JsonSyntaxException) {
                        // JSON 解析失败，使用原始内容作为回复，用户消息截取作为标题
                        Result.success(FirstMessageResponse(
                            content = rawContent,
                            title = userMessage.take(20)
                        ))
                    }
                } else if (body?.error != null) {
                    Result.failure(Exception("API Error: ${body.error.message}"))
                } else {
                    Result.failure(Exception("Empty response from API"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Result.failure(Exception("HTTP ${response.code()}: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 测试 API 连接
     * 
     * @param baseUrl API 基础 URL
     * @param apiKey API 密钥
     * @param model 模型名称
     * @return 连接成功返回 true
     */
    suspend fun testConnection(
        baseUrl: String,
        apiKey: String,
        model: String
    ): Result<Boolean> {
        return try {
            val testMessages = listOf(
                ChatMessage(role = "user", content = "Hi")
            )
            val result = chat(baseUrl, apiKey, model, testMessages)
            if (result.isSuccess) {
                Result.success(true)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 发送多模态消息（支持图片）
     * 
     * @param baseUrl API 基础 URL
     * @param apiKey API 密钥
     * @param model 模型名称（建议使用 gpt-4-vision-preview 等支持视觉的模型）
     * @param textContent 文本内容
     * @param imageBase64 Base64 编码的图片数据（可选）
     * @param historyMessages 历史消息（纯文本）
     * @return AI 回复内容
     */
    suspend fun chatWithImage(
        baseUrl: String,
        apiKey: String,
        model: String,
        textContent: String,
        imageBase64: String?,
        historyMessages: List<ChatMessage> = emptyList()
    ): Result<String> {
        return try {
            val api = getApi(baseUrl)
            
            // 构建消息列表
            val messages = mutableListOf<VisionChatMessage>()
            
            // 添加历史消息
            historyMessages.forEach { msg ->
                messages.add(VisionChatMessage(role = msg.role, content = msg.content))
            }
            
            // 构建当前消息（可能包含图片）
            val currentContent: Any = if (imageBase64 != null) {
                // 多模态消息格式
                listOf(
                    mapOf(
                        "type" to "text",
                        "text" to textContent
                    ),
                    mapOf(
                        "type" to "image_url",
                        "image_url" to mapOf(
                            "url" to "data:image/jpeg;base64,$imageBase64",
                            "detail" to "auto"
                        )
                    )
                )
            } else {
                textContent
            }
            
            messages.add(VisionChatMessage(role = "user", content = currentContent))
            
            val request = VisionChatCompletionRequest(
                model = model,
                messages = messages
            )
            
            val response = api.visionChatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )
            
            if (response.isSuccessful) {
                val body = response.body()
                val content = body?.choices?.firstOrNull()?.message?.content
                
                if (content != null) {
                    Result.success(content)
                } else if (body?.error != null) {
                    Result.failure(Exception("API Error: ${body.error.message}"))
                } else {
                    Result.failure(Exception("Empty response from API"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Result.failure(Exception("HTTP ${response.code()}: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 流式聊天请求（SSE Streaming）
     * 
     * 返回 Flow，逐步发送 AI 回复内容
     * 
     * @param baseUrl API 基础 URL
     * @param apiKey API 密钥
     * @param model 模型名称
     * @param messages 聊天历史消息
     * @return Flow 发送每次收到的文本增量
     */
    fun chatStream(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>
    ): Flow<String> = flow {
        val api = getApi(baseUrl)
        val request = ChatCompletionRequest(
            model = model,
            messages = messages,
            stream = true
        )
        
        val response = api.chatCompletionStream(
            authorization = "Bearer $apiKey",
            request = request
        )
        
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            throw Exception("HTTP ${response.code()}: $errorBody")
        }
        
        val responseBody = response.body() ?: throw Exception("Empty response body")
        
        responseBody.byteStream().bufferedReader().use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val data = line ?: continue
                
                // SSE 格式: "data: {...}"
                if (data.startsWith("data: ")) {
                    val json = data.removePrefix("data: ").trim()
                    
                    // 检查结束标志
                    if (json == "[DONE]") {
                        break
                    }
                    
                    try {
                        val chunk = gson.fromJson(json, StreamChatCompletionChunk::class.java)
                        val content = chunk.choices?.firstOrNull()?.delta?.content
                        if (!content.isNullOrEmpty()) {
                            emit(content)
                        }
                    } catch (e: JsonSyntaxException) {
                        // 忽略解析错误的行
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
