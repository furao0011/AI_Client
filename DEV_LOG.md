# 开发日志

<!--
开发文档撰写规范：
- 日期作为二号标题，版本号作为三号标题。
- 内容条目必须使用 [Feature], [Fix], [Refactor] 标签开头。
- 不需要流水账，只记录架构决策、核心 API 变动和关键 Bug 修复。
- 当文档超过 200 行时，将 3 天前的记录折叠进 <details> 标签中。
-->

## 2025年12月5日

### v0.1.7.3
- [Feature] 图片消息服务端适配:
  - 新增 `ImageUploader.kt`: 图片上传服务，支持 URI 和 Base64 两种上传方式
    - `uploadImage(uri)`: 从 URI 读取、压缩并上传图片，返回服务端 URL
    - `uploadImageBase64(base64)`: 上传 Base64 图片
    - `loadBitmapFromUri(uri)`: 加载 Bitmap 用于预览
  - `Message` 数据模型新增 `imageUrl` 字段（优先使用服务端 URL，兼容 `imageBase64`）
  - 数据库迁移 v3→v4: `ALTER TABLE messages ADD COLUMN imageUrl TEXT`
- [Feature] 图片上传状态管理:
  - 新增 `ImageUploadState` 密封类: `None`, `Uploading`, `Success`, `Failed` 四种状态
  - `ChatViewModel` 新增 `imageUploadState` 状态及相关方法:
    - `selectAndUploadImage(uri)`: 选择图片并自动上传
    - `clearSelectedImage()`: 清除选中的图片
    - 图片上传成功后才允许发送消息
- [Feature] ChatScreen UI 更新:
  - `ChatInputArea` 支持显示上传状态: 上传中显示加载动画、失败显示重试按钮
  - `MessageBubble` 支持显示服务端图片（Coil `AsyncImage` 组件）
  - 发送按钮在图片上传中/失败时禁用
- [Refactor] `OnlineRepository` 重构:
  - 新增 `sendMessageWithUrl(sessionId, content, imageUrl)` 方法
  - `sendMessage` 接口兼容处理：在线模式下 imageBase64 参数实际传入 imageUrl
- [Refactor] `AppContainer` 扩展: 新增 `imageUploader` 依赖注入

### v0.1.7.2
- [Feature] 会话与消息服务端同步:
  - 新增 `OnlineRepository.kt`: 实现 `AppRepository` 接口，所有数据操作走服务端 API
  - 会话列表从 `/api/sessions` 获取，支持分页 (`refreshSessions(page, size)`)
  - 消息列表从 `/api/sessions/{id}/messages` 获取，支持分页 (`refreshMessages(sessionId, page, size)`)
  - 发送消息对接 `/api/sessions/{id}/messages`，自动更新缓存
  - 流式消息对接 `/api/sessions/{id}/messages/stream`，支持 SSE 流式响应
- [Feature] Repository 动态切换:
  - `AppContainer` 新增 `offlineRepository` 和 `onlineRepository` 独立访问
  - 新增 `useOnlineMode` Flow，根据 `useServerAiService` 设置自动选择模式
  - ViewModel 根据模式动态选择使用离线或在线 Repository
- [Refactor] `SessionListViewModel` 重构:
  - 移除对不存在的 `SessionService` 依赖
  - 新增 `onlineSessions` StateFlow，展示服务端会话列表
  - 自动根据 `useServerMode` 选择数据源
- [Refactor] `ChatViewModel` 重构:
  - 移除对不存在的 `MessageService` 依赖
  - 新增 `onlineMessages` StateFlow，展示服务端消息列表
  - 统一使用 `currentRepository` 处理发送和编辑消息

### v0.1.7.1
- [Feature] 用户认证对接:
  - 新增 `RegisterScreen.kt`: 注册界面，支持用户名、密码、确认密码、邮箱（可选）输入
  - 新增 `RegisterViewModel.kt`: 注册逻辑，包含完整的输入校验（用户名3-20字符、密码6-32字符、邮箱格式）
  - `LoginScreen` 已包含注册入口，点击跳转到注册页面
  - 注册成功后自动登录并跳转到会话列表
- [Feature] 登录状态恢复:
  - `AuthService.isLoginValid()`: App 启动时从 DataStore 恢复 Token 并验证
  - `LoginViewModel` 初始化时自动检查登录状态，有效则直接跳转
- [Refactor] `AuthService` 优化:
  - 新增简化版 `login(username, password)` 和 `register(username, password, email?)` 方法，自动使用 `serverBaseUrl`
  - 减少调用方需要传递的参数
- [Refactor] `NetworkErrorHandler` 扩展:
  - 新增 `getErrorMessage(code)` 方法，根据业务错误码返回用户友好的错误消息
  - 支持所有服务端 API 错误码的本地化消息

### v0.1.7
- [Feature] 服务端对接基础架构搭建:
  - 新增 `data/remote/api/` 目录，定义服务端 API 接口：
    - `AuthApi.kt`: 用户认证接口（注册、登录、获取/更新用户信息、修改密码）
    - `SessionApi.kt`: 会话管理接口（CRUD、分页获取、清空所有）
    - `MessageApi.kt`: 消息管理接口（获取、发送、编辑、删除，支持流式 SSE）
    - `UploadApi.kt`: 图片上传接口（Multipart 和 Base64 两种方式）
    - `ApiResponse.kt`: 通用响应包装类，统一错误码定义
  - 新增 `AuthService.kt`: 认证服务，管理 Token 状态、登录/登出、Token 恢复
  - 新增 `NetworkErrorHandler.kt`: 网络错误处理器，统一异常转换、支持重试判断
- [Refactor] `UserPreferencesDataStore` 扩展:
  - 新增认证字段: `authToken`、`tokenExpiresAt`
  - 新增服务端配置字段: `serverBaseUrl`、`useServerAiService`
  - 新增 `effectiveApiConfig` Flow，根据模式自动选择 API 配置
  - 新增方法: `setAuthToken()`、`clearAuthToken()`、`setServerBaseUrl()`、`setUseServerAiService()`
- [Refactor] `AppContainer` 扩展:
  - 暴露 `authService` 和 `userPreferencesDataStore` 供外部访问
  - 为后续 Repository 切换（离线/在线模式）做准备

## 2025年11月28日

### v0.1.6.3
- [Feature] 顶部栏改造:
  - 显示当前会话标题，替代固定的 "AI Assistant" 文字。
  - 支持下拉菜单快速切换会话（显示最近 10 个会话）。
  - 状态标签动态显示：加载中显示 "AI 正在思考"，否则显示 "在线"。
  - `ChatViewModel` 新增 `sessions` 和 `currentSession` 状态。
- [Feature] 消息加载状态动画:
  - 新增 `LoadingMessageBubble` 组件，显示三点跳动动画。
  - 使用 `infiniteRepeatable` + `tween` 实现平滑的交错跳动效果。
  - 等待 AI 响应时自动显示，流式响应开始后自动隐藏。
- [Refactor] `Strings.kt` 新增 `selectSession`、`currentSession`、`switchSession`、`aiThinking` 多语言字符串。

### v0.1.6.2
- [Feature] 网络请求重试机制:
  - `OpenAiService` 新增 `withRetry()` 函数，支持指数退避重试（最多 3 次，延迟 1s→2s→3s）。
  - 自动识别可重试错误（5xx、超时、网络错误），4xx 客户端错误不重试。
- [Feature] 流式响应（SSE Streaming）:
  - 新增 `StreamChatCompletionChunk`、`StreamChoice`、`DeltaMessage` DTO 解析流式响应。
  - `OpenAiApi` 新增 `@Streaming` 注解的 `chatCompletionStream()` 端点。
  - `OpenAiService` 新增 `chatStream()` 方法，返回 `Flow<String>` 逐字发送 AI 回复。
  - `ChatViewModel` 新增 `streamingContent`、`isLoading` 状态，支持流式和非流式两种模式。
  - `ChatScreen` 新增 `StreamingMessageBubble` 组件，实时显示流式响应内容（带光标效果）。
- [Feature] 消息复制功能:
  - `MessageBubble` 新增复制按钮（所有消息），点击后复制到剪贴板并显示 Toast 提示。
  - `Strings.kt` 新增 `copyMessage`、`messageCopied` 多语言字符串。
- [Feature] 多 API 配置管理:
  - 新增 `ApiConfigEntity` Room 实体存储多个 API 配置（name, baseUrl, apiKey, modelName, isDefault）。
  - 新增 `ApiConfigDao` DAO，支持配置的增删改查及默认配置切换。
  - 数据库迁移 v2→v3，新增 `api_configs` 表。
  - `AppRepository` 新增 `savedApiConfigs` Flow 及配置管理方法（saveApiConfig, deleteApiConfig, switchToApiConfig, setDefaultApiConfig）。
  - `SettingsViewModel` 新增多配置管理相关状态和方法。
  - `SettingsScreen` 新增已保存配置列表对话框、添加配置对话框，支持配置切换和设为默认。
- [Refactor] 输入区优化: `ChatInputArea` 新增 `enabled` 参数，发送消息期间禁用输入和发送按钮。

### v0.1.6.1
- [Feature] Markdown 渲染: 引入 `compose-markdown` 库，消息气泡支持 Markdown 格式解析和展示（代码块、列表、链接等）。
- [Feature] 会话标题自动生成: 首条消息时 AI 以 JSON 格式返回回复内容和会话标题，自动更新 Session 标题。
- [Feature] 多模态输入支持:
  - `Message` 实体新增 `imageBase64` 字段存储图片数据。
  - 数据库迁移 v1→v2，新增 `imageBase64` 列。
  - `OpenAiService` 新增 `chatWithImage()` 方法支持 Vision API。
  - `ChatScreen` 新增图片选择器，支持从相册选取图片发送。
  - 消息气泡支持图片和文本混合显示。
- [Refactor] API 数据模型扩展: 新增 `VisionChatCompletionRequest`、`VisionChatMessage`、`ImageContent` 等多模态 DTO。

### v0.1.6
- [Feature] 网络层集成（Retrofit + OkHttp）:
  - 新增 `data/remote/dto/OpenAiModels.kt`：定义 OpenAI API 请求/响应数据模型（ChatCompletionRequest, ChatCompletionResponse, ChatMessage 等）。
  - 新增 `data/remote/OpenAiApi.kt`：Retrofit 接口，定义 `POST /v1/chat/completions` 端点。
  - 新增 `data/remote/OpenAiService.kt`：封装 API 调用逻辑，支持动态 baseUrl、Bearer Token 认证、60s/120s 超时配置。
- [Feature] API 配置管理:
  - 扩展 `UserPreferencesDataStore`：新增 `ApiConfig` 数据类及持久化存储（baseUrl, apiKey, modelName）。
  - 扩展 `AppRepository` 接口：新增 `apiConfig` Flow、`setApiConfig()`、`testApiConnection()` 方法。
  - `SettingsViewModel` 新增 API 配置状态管理和连接测试功能。
- [Feature] 真实 AI 对话:
  - `OfflineRepository.sendMessage()` 和 `editMessage()` 现在调用真实 OpenAI 兼容 API。
  - `MessageDao` 新增 `getMessagesForSessionSync()` 用于构建对话上下文。
- [Feature] 设置界面优化:
  - 新增 AI Service 设置组，支持配置 API URL、API Key、模型名称。
  - 支持测试连接功能，实时显示连接状态。
- [Refactor] 依赖注入: `AppContainer` 新增 `OpenAiService` 单例注入。

### v0.1.5.2
- [Fix] 消息显示修复（关键 - ForeignKey CASCADE 问题）: 
  - **根因**: `SessionDao.insertSession` 使用 `OnConflictStrategy.REPLACE` 会触发 DELETE+INSERT，导致 `Message` 表的 `ForeignKey.CASCADE` 级联删除所有消息。
  - **解决**: 新增 `SessionDao.updateSession()` 方法使用 `@Update` 注解，更新会话时不再触发级联删除。
  - 使用 `key = "chat_$sessionId"` 为每个会话创建独立的 ViewModel 实例。
  - 将 `SharingStarted.WhileSubscribed(5000)` 改为 `SharingStarted.Eagerly`。
- [Fix] 用户消息气泡位置修复: 为 `MessageBubble` 内的 `Column` 添加 `horizontalAlignment` 属性，确保用户消息靠右对齐；简化编辑按钮布局，移除多余的 `Row` 包装。
- [Feature] DataStore 持久化: 引入 `UserPreferencesDataStore` 持久化用户偏好设置（语言、深色模式）。
- [Refactor] 接口升级: `AppRepository` 的 `setLanguage/setDarkMode` 改为 suspend 函数，`language/darkMode` 改为 Flow。

### v0.1.5.1
- [Fix] MockRepository 清理: 移除 MainActivity 中残留的 MockRepository 引用，统一使用 AppContainer 依赖注入。
- [Refactor] 消息编辑交互: 将弹窗编辑改为内联编辑模式（类似 ChatGPT），提升用户体验。
- [Feature] 编辑逻辑优化: 编辑消息后自动删除该消息之后的所有对话，重新生成 AI 回复。

### v0.1.5
- [Refactor] 数据持久化: 引入 Room 数据库，替代 MockRepository 实现本地数据持久化。
- [Refactor] 架构优化: 实现 Repository 模式与依赖注入（Manual DI），解耦 ViewModel 与数据层。
- [Feature] 数据库迁移: 定义 User, Session, Message 实体及 DAO，支持会话与消息的增删改查。

### v0.1.4.2
- [Refactor] UI 优化: 全面升级为 Material Design 3 风格，优化登录页、会话列表及聊天页视觉体验。
- [Feature] 逻辑完善: 实现会话删除、清空历史、消息编辑及自动重发功能。
- [Feature] 异常处理: 增加基础错误处理机制。

## 2025年11月21日

### v0.1.4.1
- [Feature] 全局语言适配: 实现中英文切换及配置持久化。
- [Feature] 深色模式: 实现应用级深色模式切换。

### v0.1.4
- [Fix] 键盘适配: 修复键盘遮挡聊天界面的问题。
- [Feature] 设置功能: 完善通知开关、深色模式开关及清除历史记录入口。

## 2025年11月20日

### v0.1.3
- [Feature] 逻辑实现: 引入 MockRepository，实现 MVVM 架构下的登录、会话和消息流转。

### v0.1.2
- [Feature] 功能实现: 完成 AI 对话板和用户设置界面的核心 UI 开发。

### v0.1.1
- [Feature] 界面实现: 完成登录页和会话列表页的美化与导航集成。

### v0.1.0
- [Refactor] 架构搭建: 引入 Navigation Compose，搭建多界面导航框架。
