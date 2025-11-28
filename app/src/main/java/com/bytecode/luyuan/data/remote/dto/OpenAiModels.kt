package com.bytecode.luyuan.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * OpenAI Chat Completion 请求体
 */
data class ChatCompletionRequest(
    @SerializedName("model")
    val model: String,
    
    @SerializedName("messages")
    val messages: List<ChatMessage>,
    
    @SerializedName("temperature")
    val temperature: Double = 0.7,
    
    @SerializedName("max_tokens")
    val maxTokens: Int? = null,
    
    @SerializedName("stream")
    val stream: Boolean = false
)

/**
 * 多模态聊天请求体（支持图片）
 */
data class VisionChatCompletionRequest(
    @SerializedName("model")
    val model: String,
    
    @SerializedName("messages")
    val messages: List<VisionChatMessage>,
    
    @SerializedName("temperature")
    val temperature: Double = 0.7,
    
    @SerializedName("max_tokens")
    val maxTokens: Int? = 4096
)

/**
 * 聊天消息
 */
data class ChatMessage(
    @SerializedName("role")
    val role: String,  // "system", "user", "assistant"
    
    @SerializedName("content")
    val content: String
)

/**
 * 多模态聊天消息（支持图片）
 */
data class VisionChatMessage(
    @SerializedName("role")
    val role: String,  // "system", "user", "assistant"
    
    @SerializedName("content")
    val content: Any  // String 或 List<ContentPart>
)

/**
 * 多模态内容部分
 */
sealed class ContentPart

/**
 * 文本内容
 */
data class TextContent(
    @SerializedName("type")
    val type: String = "text",
    
    @SerializedName("text")
    val text: String
) : ContentPart()

/**
 * 图片内容
 */
data class ImageContent(
    @SerializedName("type")
    val type: String = "image_url",
    
    @SerializedName("image_url")
    val imageUrl: ImageUrl
) : ContentPart()

/**
 * 图片 URL 信息
 */
data class ImageUrl(
    @SerializedName("url")
    val url: String,  // 可以是 URL 或 "data:image/jpeg;base64,{base64_data}"
    
    @SerializedName("detail")
    val detail: String = "auto"  // "low", "high", "auto"
)

/**
 * OpenAI Chat Completion 响应体
 */
data class ChatCompletionResponse(
    @SerializedName("id")
    val id: String?,
    
    @SerializedName("object")
    val objectType: String?,
    
    @SerializedName("created")
    val created: Long?,
    
    @SerializedName("model")
    val model: String?,
    
    @SerializedName("choices")
    val choices: List<Choice>?,
    
    @SerializedName("usage")
    val usage: Usage?,
    
    @SerializedName("error")
    val error: ApiError?
)

/**
 * 响应选项
 */
data class Choice(
    @SerializedName("index")
    val index: Int,
    
    @SerializedName("message")
    val message: ChatMessage?,
    
    @SerializedName("finish_reason")
    val finishReason: String?
)

/**
 * Token 使用统计
 */
data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    
    @SerializedName("total_tokens")
    val totalTokens: Int
)

/**
 * API 错误信息
 */
data class ApiError(
    @SerializedName("message")
    val message: String?,
    
    @SerializedName("type")
    val type: String?,
    
    @SerializedName("code")
    val code: String?
)

/**
 * SSE 流式响应数据块
 */
data class StreamChatCompletionChunk(
    @SerializedName("id")
    val id: String?,
    
    @SerializedName("object")
    val objectType: String?,
    
    @SerializedName("created")
    val created: Long?,
    
    @SerializedName("model")
    val model: String?,
    
    @SerializedName("choices")
    val choices: List<StreamChoice>?
)

/**
 * 流式响应选项
 */
data class StreamChoice(
    @SerializedName("index")
    val index: Int,
    
    @SerializedName("delta")
    val delta: DeltaMessage?,
    
    @SerializedName("finish_reason")
    val finishReason: String?
)

/**
 * 增量消息（流式）
 */
data class DeltaMessage(
    @SerializedName("role")
    val role: String?,
    
    @SerializedName("content")
    val content: String?
)
