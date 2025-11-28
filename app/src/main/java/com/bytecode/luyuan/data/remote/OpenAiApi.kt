package com.bytecode.luyuan.data.remote

import com.bytecode.luyuan.data.remote.dto.ChatCompletionRequest
import com.bytecode.luyuan.data.remote.dto.ChatCompletionResponse
import com.bytecode.luyuan.data.remote.dto.VisionChatCompletionRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming

/**
 * OpenAI 兼容的 Chat Completion API 接口
 * 
 * 支持所有 OpenAI 格式的 API 端点（OpenAI、Azure、自定义服务等）
 */
interface OpenAiApi {
    
    /**
     * 发送聊天补全请求
     * 
     * @param authorization Bearer Token 格式的 API Key
     * @param request 聊天请求体
     * @return 聊天响应
     */
    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): Response<ChatCompletionResponse>
    
    /**
     * 发送多模态聊天请求（支持图片）
     * 
     * @param authorization Bearer Token 格式的 API Key
     * @param request 多模态聊天请求体
     * @return 聊天响应
     */
    @POST("v1/chat/completions")
    suspend fun visionChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: VisionChatCompletionRequest
    ): Response<ChatCompletionResponse>
    
    /**
     * 发送流式聊天请求（SSE）
     * 
     * @param authorization Bearer Token 格式的 API Key
     * @param request 聊天请求体（stream=true）
     * @return 流式响应体
     */
    @Streaming
    @POST("v1/chat/completions")
    suspend fun chatCompletionStream(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): Response<ResponseBody>
}
