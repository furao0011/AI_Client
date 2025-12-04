# 客户端服务端对接开发计划

## 项目概述

将现有客户端从本地存储 + 直连 AI API 的架构，升级为服务端统一管理的架构。主要变更包括：
- 用户认证从本地 Mock 改为服务端鉴权
- 会话/消息数据从 Room 本地存储改为服务端同步
- 图片消息从 Base64 本地存储改为上传服务端获取 URL
- AI 对话支持服务端网关模式（默认）和自定义 API 直连模式

---

## 一、里程碑规划

### Phase 1: 基础架构搭建（预计 3 天）
- [ ] 新增服务端 API 接口定义（Retrofit Interface）
- [ ] 新增认证服务（AuthService）和 Token 管理
- [ ] 改造 DataStore，增加 Token 和服务端配置持久化
- [ ] 新增 API 响应通用包装类和错误处理

### Phase 2: 用户认证对接（预计 2 天）
- [ ] 改造登录界面，对接服务端登录 API
- [ ] 新增注册界面和注册流程
- [ ] 实现 Token 自动刷新机制
- [ ] 实现登录状态恢复（App 启动时）

### Phase 3: 会话与消息同步（预计 4 天）
- [ ] 改造 `AppRepository` 接口，区分本地模式和服务端模式
- [ ] 新增 `OnlineRepository` 实现服务端数据交互
- [ ] 会话列表从服务端获取
- [ ] 消息列表从服务端获取
- [ ] 发送消息对接服务端
- [ ] 流式消息对接服务端 SSE

### Phase 4: 图片消息适配（预计 2 天）
- [ ] 新增图片上传服务（ImageUploader）
- [ ] 改造发送图片消息流程：Base64 → 上传 → URL
- [ ] 改造消息展示：支持 URL 图片加载
- [ ] 数据模型适配：`imageBase64` → `imageUrl`

### Phase 5: AI 网关模式（预计 2 天）
- [ ] 设置界面增加"使用服务端 AI 服务"开关
- [ ] 默认使用服务端网关，可切换自定义 API
- [ ] 服务端网关模式下隐藏 API Key 配置
- [ ] 适配服务端网关 SSE 流式响应

### Phase 6: 测试与优化（预计 3 天）
- [ ] 端到端测试全流程
- [ ] 错误处理优化
- [ ] 网络状态检测与离线提示
- [ ] 性能优化

---

## 二、详细开发任务

### 2.1 Phase 1: 基础架构搭建

#### 任务 1.1: API 接口定义
**文件**: `data/remote/api/` 目录

新增文件:
- `AuthApi.kt` - 认证接口
- `SessionApi.kt` - 会话接口  
- `MessageApi.kt` - 消息接口
- `UploadApi.kt` - 上传接口
- `ApiResponse.kt` - 通用响应包装

#### 任务 1.2: 认证服务
**文件**: `data/remote/AuthService.kt`

功能:
- Token 状态管理（StateFlow）
- 登录/登出方法
- Token 持久化（DataStore）
- 自动添加 Authorization Header

#### 任务 1.3: DataStore 扩展
**文件**: `data/local/UserPreferencesDataStore.kt`

新增字段:
```kotlin
// 认证相关
val authToken: Flow<String?>
val tokenExpiresAt: Flow<Long?>

// 服务端配置
val serverBaseUrl: Flow<String>
val useServerAiService: Flow<Boolean>  // 是否使用服务端 AI 服务
```

#### 任务 1.4: 网络错误处理
**文件**: `data/remote/NetworkErrorHandler.kt`

功能:
- 统一处理 HTTP 错误
- Token 过期自动跳转登录
- 网络异常提示

---

### 2.2 Phase 2: 用户认证对接

#### 任务 2.1: 登录界面改造
**文件**: `ui/screen/LoginScreen.kt`

变更:
- 调用服务端登录 API
- 错误提示优化
- 登录成功后保存 Token

#### 任务 2.2: 新增注册界面
**文件**: `ui/screen/RegisterScreen.kt`

功能:
- 用户名、密码、确认密码、邮箱（可选）
- 输入校验
- 调用注册 API
- 注册成功自动登录

#### 任务 2.3: 导航改造
**文件**: `ui/navigation/NavGraph.kt`

变更:
- 新增注册页面路由
- 登录页增加"注册"入口
- 启动时检查 Token 有效性，自动跳转

---

### 2.3 Phase 3: 会话与消息同步

#### 任务 3.1: Repository 接口扩展
**文件**: `data/repository/AppRepository.kt`

新增方法:
```kotlin
// 会话分页
suspend fun getSessions(page: Int, size: Int): Result<List<Session>>

// 消息分页
suspend fun getMessages(sessionId: String, page: Int, size: Int): Result<List<Message>>

// 流式发送（服务端）
suspend fun sendMessageStreamOnline(
    sessionId: String,
    content: String,
    imageUrl: String?,
    onToken: suspend (String) -> Unit
)
```

#### 任务 3.2: OnlineRepository 实现
**文件**: `data/repository/OnlineRepository.kt`

实现 `AppRepository` 接口，所有数据操作走服务端 API。

#### 任务 3.3: Repository 切换机制
**文件**: `data/AppContainer.kt`

根据配置选择 `OfflineRepository` 或 `OnlineRepository`:
```kotlin
val repository: AppRepository = if (useServerMode) {
    OnlineRepository(...)
} else {
    OfflineRepository(...)
}
```

---

### 2.4 Phase 4: 图片消息适配

#### 任务 4.1: 图片上传服务
**文件**: `data/remote/ImageUploader.kt`

功能:
- 从 URI 读取图片
- 压缩图片
- 上传到服务端
- 返回图片 URL

#### 任务 4.2: Message 数据模型变更
**文件**: `data/model/Models.kt`

变更:
```kotlin
data class Message(
    // ...
    val imageUrl: String? = null  // 替代 imageBase64
)
```

#### 任务 4.3: 数据库迁移
**文件**: `data/local/AppDatabase.kt`

新增 MIGRATION_3_4:
```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE messages ADD COLUMN imageUrl TEXT")
    }
}
```

#### 任务 4.4: 发送图片流程改造
**文件**: `ui/screen/ChatScreen.kt`, `ui/viewmodel/ChatViewModel.kt`

变更流程:
1. 用户选择图片
2. 压缩并上传到服务端
3. 获取图片 URL
4. 发送消息（content + imageUrl）

---

### 2.5 Phase 5: AI 网关模式

#### 任务 5.1: 设置界面改造
**文件**: `ui/screen/SettingsScreen.kt`

新增:
- "使用服务端 AI 服务" 开关（默认开启）
- 开启时隐藏 API URL/Key 配置
- 关闭时显示自定义 API 配置

#### 任务 5.2: OpenAiService 适配
**文件**: `data/remote/OpenAiService.kt`

变更:
- 服务端模式下，baseUrl 指向服务端网关
- 服务端模式下，使用用户 Token 作为认证（服务端会替换为真实 API Key）
- 保持 OpenAI 格式兼容

#### 任务 5.3: API 配置逻辑
**文件**: `data/local/UserPreferencesDataStore.kt`

```kotlin
val effectiveApiConfig: Flow<ApiConfig> = combine(
    useServerAiService,
    serverBaseUrl,
    customApiConfig
) { useServer, serverUrl, customConfig ->
    if (useServer) {
        ApiConfig(
            baseUrl = "$serverUrl/",
            apiKey = "", // 服务端模式使用 authToken
            modelName = "qwen-turbo",
            isConfigured = true
        )
    } else {
        customConfig
    }
}
```

---

## 三、数据模型变更汇总

### 3.1 Message 实体

| 旧字段 | 新字段 | 说明 |
|--------|--------|------|
| imageBase64 | imageUrl | Base64 → URL |

### 3.2 新增字段（DataStore）

| 字段 | 类型 | 说明 |
|------|------|------|
| authToken | String? | JWT Token |
| tokenExpiresAt | Long? | Token 过期时间 |
| serverBaseUrl | String | 服务端地址 |
| useServerAiService | Boolean | 是否使用服务端 AI |

---

## 四、兼容性处理

### 4.1 本地数据迁移
- 保留本地 Room 数据库，作为离线缓存（可选）
- 首次对接服务端时，提示用户数据将同步到云端

### 4.2 API 配置兼容
- 保留多 API 配置功能
- 新增"服务端默认"作为一个特殊配置

### 4.3 图片消息兼容
- 本地旧消息的 imageBase64 仍可显示
- 新消息统一使用 imageUrl

---

## 五、测试清单

### 5.1 认证测试
- [ ] 注册新用户
- [ ] 登录/登出
- [ ] Token 过期重新登录
- [ ] App 重启后自动登录

### 5.2 会话测试
- [ ] 获取会话列表
- [ ] 创建新会话
- [ ] 删除会话
- [ ] 清空所有会话

### 5.3 消息测试
- [ ] 发送文本消息
- [ ] 发送图片消息
- [ ] 流式响应显示
- [ ] 编辑消息重发
- [ ] 复制消息

### 5.4 AI 服务测试
- [ ] 服务端网关模式
- [ ] 自定义 API 模式
- [ ] 模式切换

---

## 六、风险与应对

| 风险 | 影响 | 应对措施 |
|------|------|----------|
| 服务端不可用 | 功能不可用 | 增加离线提示，保留本地缓存 |
| Token 频繁过期 | 用户体验差 | 实现无感刷新机制 |
| 图片上传失败 | 消息发送失败 | 重试机制，失败提示 |
| 数据迁移丢失 | 历史数据丢失 | 充分测试，备份机制 |

---

## 七、预计工期

| Phase | 任务 | 工期 |
|-------|------|------|
| Phase 1 | 基础架构搭建 | 3 天 |
| Phase 2 | 用户认证对接 | 2 天 |
| Phase 3 | 会话与消息同步 | 4 天 |
| Phase 4 | 图片消息适配 | 2 天 |
| Phase 5 | AI 网关模式 | 2 天 |
| Phase 6 | 测试与优化 | 3 天 |
| **总计** | | **16 天** |
