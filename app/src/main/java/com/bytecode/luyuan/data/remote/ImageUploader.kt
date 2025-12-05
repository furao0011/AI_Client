package com.bytecode.luyuan.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.bytecode.luyuan.data.local.UserPreferencesDataStore
import com.bytecode.luyuan.data.remote.api.Base64UploadRequest
import com.bytecode.luyuan.data.remote.api.ImageUploadResult
import com.bytecode.luyuan.data.remote.api.UploadApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * 图片上传服务
 * 
 * 支持 URI 和 Base64 两种方式上传图片到服务端，返回图片 URL
 * 
 * v0.1.7.3 新增
 * 
 * @param context Android Context（用于读取 URI 内容）
 * @param authService 认证服务（获取 Token）
 * @param userPreferencesDataStore 用户偏好存储（获取服务端地址）
 */
class ImageUploader(
    private val context: Context,
    private val authService: AuthService,
    private val userPreferencesDataStore: UserPreferencesDataStore
) {
    
    companion object {
        private const val TAG = "ImageUploader"
        
        // 图片压缩参数
        private const val MAX_IMAGE_WIDTH = 1920
        private const val MAX_IMAGE_HEIGHT = 1920
        private const val JPEG_QUALITY = 85
    }
    
    // API 实例缓存
    private var currentBaseUrl: String? = null
    private var retrofit: Retrofit? = null
    private var uploadApi: UploadApi? = null
    
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)  // 上传可能需要较长时间
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
    
    /**
     * 获取或创建 UploadApi 实例
     */
    private suspend fun getApi(): UploadApi {
        val baseUrl = userPreferencesDataStore.serverBaseUrl.first()
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        
        if (currentBaseUrl != normalizedUrl || retrofit == null) {
            currentBaseUrl = normalizedUrl
            retrofit = Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            uploadApi = retrofit!!.create(UploadApi::class.java)
        }
        
        return uploadApi!!
    }
    
    /**
     * 上传图片（通过 URI）
     * 
     * 流程：读取 URI → 压缩图片 → 上传服务端 → 返回 URL
     * 
     * @param uri 图片 URI（来自图片选择器）
     * @return 上传结果（包含 imageUrl）或失败信息
     */
    suspend fun uploadImage(uri: Uri): Result<ImageUploadResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting image upload from URI: $uri")
            
            // 1. 读取并压缩图片
            val compressedBytes = compressImage(uri)
            if (compressedBytes == null) {
                return@withContext Result.failure(Exception("无法读取或压缩图片"))
            }
            
            Log.d(TAG, "Image compressed, size: ${compressedBytes.size} bytes")
            
            // 2. 创建 MultipartBody
            val requestBody = compressedBytes.toRequestBody("image/jpeg".toMediaType())
            val fileName = "image_${System.currentTimeMillis()}.jpg"
            val filePart = MultipartBody.Part.createFormData("file", fileName, requestBody)
            
            // 3. 上传到服务端
            val api = getApi()
            val token = authService.getAuthHeader()
            
            val response = api.uploadImage(token, filePart)
            
            if (response.isSuccessful && response.body()?.isSuccess == true) {
                val result = response.body()?.data
                if (result != null) {
                    Log.d(TAG, "Image uploaded successfully, URL: ${result.url}")
                    return@withContext Result.success(result)
                } else {
                    return@withContext Result.failure(Exception("上传成功但返回数据为空"))
                }
            } else {
                val errorMsg = response.body()?.message ?: "上传失败: HTTP ${response.code()}"
                Log.e(TAG, "Upload failed: $errorMsg")
                return@withContext Result.failure(Exception(errorMsg))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed with exception", e)
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * 上传图片（通过 Base64）
     * 
     * 适用于已经有 Base64 编码数据的场景
     * 
     * @param base64 Base64 编码的图片数据
     * @param filename 文件名（可选）
     * @return 上传结果或失败信息
     */
    suspend fun uploadImageBase64(base64: String, filename: String? = null): Result<ImageUploadResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting image upload from Base64, length: ${base64.length}")
            
            val api = getApi()
            val token = authService.getAuthHeader()
            
            val request = Base64UploadRequest(
                base64 = base64,
                filename = filename ?: "image_${System.currentTimeMillis()}.jpg"
            )
            
            val response = api.uploadImageBase64(token, request)
            
            if (response.isSuccessful && response.body()?.isSuccess == true) {
                val result = response.body()?.data
                if (result != null) {
                    Log.d(TAG, "Image uploaded successfully, URL: ${result.url}")
                    return@withContext Result.success(result)
                } else {
                    return@withContext Result.failure(Exception("上传成功但返回数据为空"))
                }
            } else {
                val errorMsg = response.body()?.message ?: "上传失败: HTTP ${response.code()}"
                Log.e(TAG, "Upload failed: $errorMsg")
                return@withContext Result.failure(Exception(errorMsg))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed with exception", e)
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * 从 URI 压缩并转换图片为 Bitmap
     * 
     * 用于预览显示
     * 
     * @param uri 图片 URI
     * @return 压缩后的 Bitmap 或 null
     */
    fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()
            
            // 计算采样率
            options.inSampleSize = calculateInSampleSize(options)
            options.inJustDecodeBounds = false
            
            val inputStream2 = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream2, null, options)
            inputStream2?.close()
            
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap from URI", e)
            null
        }
    }
    
    /**
     * 压缩图片
     * 
     * @param uri 图片 URI
     * @return 压缩后的字节数组或 null
     */
    private fun compressImage(uri: Uri): ByteArray? {
        return try {
            // 首先获取图片尺寸
            val inputStream = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()
            
            // 计算采样率
            options.inSampleSize = calculateInSampleSize(options)
            options.inJustDecodeBounds = false
            
            // 解码图片
            val inputStream2 = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream2, null, options)
            inputStream2?.close()
            
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap")
                return null
            }
            
            // 压缩为 JPEG
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            
            // 清理 Bitmap
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
            
            outputStream.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compress image", e)
            null
        }
    }
    
    /**
     * 计算图片采样率
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        
        if (height > MAX_IMAGE_HEIGHT || width > MAX_IMAGE_WIDTH) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while ((halfHeight / inSampleSize) >= MAX_IMAGE_HEIGHT 
                && (halfWidth / inSampleSize) >= MAX_IMAGE_WIDTH) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
}
