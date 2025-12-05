package com.bytecode.luyuan.data.remote.api

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

/**
 * 文件上传 API 接口
 * 
 * 提供图片上传功能，支持 Multipart 和 Base64 两种方式
 */
interface UploadApi {
    
    /**
     * 上传图片（Multipart）
     * 
     * @param token Bearer Token
     * @param file 图片文件
     * @return 图片上传结果
     */
    @Multipart
    @POST("api/upload/image")
    suspend fun uploadImage(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part
    ): Response<ApiResponse<ImageUploadResult>>
    
    /**
     * 上传图片（Base64）
     * 
     * @param token Bearer Token
     * @param request Base64 上传请求体
     * @return 图片上传结果
     */
    @POST("api/upload/image/base64")
    suspend fun uploadImageBase64(
        @Header("Authorization") token: String,
        @Body request: Base64UploadRequest
    ): Response<ApiResponse<ImageUploadResult>>
}

// ==================== 请求数据类 ====================

/**
 * Base64 图片上传请求体
 * 
 * @param base64 Base64 编码的图片数据（可包含 data:image/xxx;base64, 前缀）
 * @param filename 文件名（可选）
 */
data class Base64UploadRequest(
    @SerializedName("base64")
    val base64: String,
    
    @SerializedName("filename")
    val filename: String? = null
)

// ==================== 响应数据类 ====================

/**
 * 图片上传结果
 * 
 * @param imageId 图片唯一 ID
 * @param url 图片访问 URL
 * @param size 文件大小（字节）
 * @param mimeType MIME 类型
 */
data class ImageUploadResult(
    @SerializedName("imageId")
    val imageId: String,
    
    @SerializedName("url")
    val url: String,
    
    @SerializedName("size")
    val size: Long,
    
    @SerializedName("mimeType")
    val mimeType: String
)
