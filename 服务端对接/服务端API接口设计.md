# 服务端 API 接口设计

## 一、接口规范

### 1.1 基础信息
- **Base URL**: `https://{server}/api`
- **协议**: HTTPS
- **数据格式**: JSON
- **字符编码**: UTF-8

### 1.2 认证方式
除登录注册接口外，所有接口需在 Header 中携带 JWT Token：
```
Authorization: Bearer {token}
```

### 1.3 通用响应格式
```json
{
    "code": 0,           // 0=成功, 非0=错误码
    "message": "success",// 状态描述
    "data": {}           // 响应数据
}
```

### 1.4 错误码定义
| 错误码 | 描述 |
|--------|------|
| 0 | 成功 |
| 1001 | 参数错误 |
| 1002 | 用户名已存在 |
| 1003 | 用户名或密码错误 |
| 2001 | Token 无效 |
| 2002 | Token 过期 |
| 3001 | 会话不存在 |
| 3002 | 消息不存在 |
| 4001 | 文件上传失败 |
| 5001 | AI 服务调用失败 |
| 9999 | 系统错误 |

---

## 二、用户认证接口

### 2.1 用户注册
```
POST /auth/register
```

**请求体**:
```json
{
    "username": "string",   // 必填，3-20字符
    "password": "string",   // 必填，6-32字符
    "email": "string"       // 可选
}
```

**响应**:
```json
{
    "code": 0,
    "message": "success",
    "data": {
        "user": {
            "id": "u_xxx",
            "username": "testuser",
            "email": "test@example.com",
            "avatarUrl": null
        },
        "token": "eyJhbGciOiJIUzI1NiIs..."
    }
}
```

---

### 2.2 用户登录
```
POST /auth/login
```

**请求体**:
```json
{
    "username": "string",
    "password": "string"
}
```

**响应**:
```json
{
    "code": 0,
    "message": "success",
    "data": {
        "user": {
            "id": "u_xxx",
            "username": "testuser",
            "email": "test@example.com",
            "avatarUrl": "https://server/api/images/avatar_xxx.jpg"
        },
        "token": "eyJhbGciOiJIUzI1NiIs...",
        "expiresAt": 1735689600000
    }
}
```

---

### 2.3 获取当前用户信息
```
GET /user/profile
```

**响应**:
```json
{
    "code": 0,
    "message": "success",
    "data": {
        "id": "u_xxx",
        "username": "testuser",
        "email": "test@example.com",
        "avatarUrl": "https://server/api/images/avatar_xxx.jpg",
        "createdAt": 1701388800000
    }
}
```

---

### 2.4 更新用户信息
```
PUT /user/profile
```

**请求体**:
```json
{
    "username": "string",   // 可选
    "email": "string",      // 可选
    "avatarUrl": "string"   // 可选
}
```

---

### 2.5 修改密码
```
PUT /user/password
```

**请求体**:
```json
{
    "oldPassword": "string",
    "newPassword": "string"
}
```

---

## 三、会话管理接口

### 3.1 获取会话列表
```
GET /sessions?page={page}&size={size}
```

**参数**:
- `page`: 页码，从 0 开始（默认 0）
- `size`: 每页数量（默认 20，最大 100）

**响应**:
```json
{
    "code": 0,
    "message": "success",
    "data": {
        "sessions": [
            {
                "id": "s_xxx",
                "title": "关于 Kotlin 的问题",
                "lastMessage": "好的，我来解释一下...",
                "timestamp": 1701388800000
            }
        ],
        "total": 50,
        "page": 0,
        "size": 20
    }
}
```

---

### 3.2 创建会话
```
POST /sessions
```

**请求体**:
```json
{
    "title": "string"  // 可选，默认"新对话"
}
```

**响应**:
```json
{
    "code": 0,
    "message": "success",
    "data": {
        "id": "s_xxx",
        "title": "新对话",
        "lastMessage": "",
        "timestamp": 1701388800000
    }
}
```

---

### 3.3 获取会话详情
```
GET /sessions/{sessionId}
```

**响应**:
```json
{
    "code": 0,
    "message": "success",
    "data": {
        "id": "s_xxx",
        "title": "关于 Kotlin 的问题",
        "lastMessage": "好的，我来解释一下...",
        "timestamp": 1701388800000
    }
}
```

---

### 3.4 更新会话
```
PUT /sessions/{sessionId}
```

**请求体**:
```json
{
    "title": "string"
}
```

---

### 3.5 删除会话
```
DELETE /sessions/{sessionId}
```

---

### 3.6 清空所有会话
```
DELETE /sessions/all
```

---

## 四、消息管理接口

### 4.1 获取消息列表
```
GET /sessions/{sessionId}/messages?page={page}&size={size}
```

**参数**:
- `page`: 页码（默认 0）
- `size`: 每页数量（默认 50）

**响应**:
```json
{
    "code": 0,
    "message": "success",
    "data": {
        "messages": [
            {
                "id": "m_xxx",
                "sessionId": "s_xxx",
                "content": "你好，请问...",
                "isUser": true,
                "timestamp": 1701388800000,
                "imageUrl": null
            },
            {
                "id": "m_yyy",
                "sessionId": "s_xxx",
                "content": "你好！我可以帮助你...",
                "isUser": false,
                "timestamp": 1701388801000,
                "imageUrl": null
            }
        ],
        "total": 100,
        "page": 0,
        "size": 50
    }
}
```

---

### 4.2 发送消息
```
POST /sessions/{sessionId}/messages
```

**请求体**:
```json
{
    "content": "string",     // 文本内容
    "imageUrl": "string"     // 可选，图片URL
}
```

**响应**（非流式）:
```json
{
    "code": 0,
    "message": "success",
    "data": {
        "userMessage": {
            "id": "m_xxx",
            "sessionId": "s_xxx",
            "content": "你好",
            "isUser": true,
            "timestamp": 1701388800000,
            "imageUrl": null
        },
        "aiMessage": {
            "id": "m_yyy",
            "sessionId": "s_xxx",
            "content": "你好！有什么可以帮助你的吗？",
            "isUser": false,
            "timestamp": 1701388801000,
            "imageUrl": null
        },
        "sessionTitle": "问候对话"  // 仅首条消息时返回
    }
}
```

---

### 4.3 发送消息（流式）
```
POST /sessions/{sessionId}/messages/stream
Content-Type: application/json
Accept: text/event-stream
```

**请求体**:
```json
{
    "content": "string",
    "imageUrl": "string"
}
```

**响应** (SSE):
```
data: {"type":"user_message","data":{"id":"m_xxx","content":"你好"}}

data: {"type":"ai_token","data":"你"}

data: {"type":"ai_token","data":"好"}

data: {"type":"ai_token","data":"！"}

data: {"type":"ai_message","data":{"id":"m_yyy","content":"你好！"}}

data: {"type":"session_title","data":"问候对话"}

data: [DONE]
```

---

### 4.4 编辑消息
```
PUT /sessions/{sessionId}/messages/{messageId}
```

**请求体**:
```json
{
    "content": "string"
}
```

**说明**: 编辑用户消息后，服务端自动删除该消息之后的所有消息并重新生成 AI 回复。

---

### 4.5 删除消息
```
DELETE /sessions/{sessionId}/messages/{messageId}
```

**说明**: 删除指定消息及其之后的所有消息。

---

## 五、图片上传接口

### 5.1 上传图片
```
POST /upload/image
Content-Type: multipart/form-data
```

**请求体**:
- `file`: 图片文件（multipart）

**响应**:
```json
{
    "code": 0,
    "message": "success",
    "data": {
        "imageId": "img_xxx",
        "url": "https://server/api/images/img_xxx.jpg",
        "size": 102400,
        "mimeType": "image/jpeg"
    }
}
```

---

### 5.2 上传图片（Base64）
```
POST /upload/image/base64
```

**请求体**:
```json
{
    "base64": "data:image/jpeg;base64,/9j/4AAQ...",
    "filename": "photo.jpg"  // 可选
}
```

**响应**: 同上

---

### 5.3 获取图片
```
GET /images/{imageId}
```

**响应**: 图片二进制数据

---

## 六、AI 对话网关接口

### 6.1 Chat Completion（兼容 OpenAI 格式）
```
POST /v1/chat/completions
```

**请求体**（完全兼容 OpenAI 格式）:
```json
{
    "model": "qwen-turbo",
    "messages": [
        {"role": "user", "content": "你好"}
    ],
    "temperature": 0.7,
    "max_tokens": 2048,
    "stream": false
}
```

**响应**（非流式，完全兼容 OpenAI 格式）:
```json
{
    "id": "chatcmpl-xxx",
    "object": "chat.completion",
    "created": 1701388800,
    "model": "qwen-turbo",
    "choices": [
        {
            "index": 0,
            "message": {
                "role": "assistant",
                "content": "你好！有什么可以帮助你的吗？"
            },
            "finish_reason": "stop"
        }
    ],
    "usage": {
        "prompt_tokens": 10,
        "completion_tokens": 15,
        "total_tokens": 25
    }
}
```

---

### 6.2 Chat Completion（流式）
```
POST /v1/chat/completions
Content-Type: application/json
Accept: text/event-stream
```

**请求体**:
```json
{
    "model": "qwen-turbo",
    "messages": [
        {"role": "user", "content": "你好"}
    ],
    "stream": true
}
```

**响应** (SSE，完全兼容 OpenAI 格式):
```
data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","created":1701388800,"model":"qwen-turbo","choices":[{"index":0,"delta":{"role":"assistant"},"finish_reason":null}]}

data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","created":1701388800,"model":"qwen-turbo","choices":[{"index":0,"delta":{"content":"你"},"finish_reason":null}]}

data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","created":1701388800,"model":"qwen-turbo","choices":[{"index":0,"delta":{"content":"好"},"finish_reason":null}]}

data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","created":1701388800,"model":"qwen-turbo","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

data: [DONE]
```

---

### 6.3 多模态请求（Vision）
```
POST /v1/chat/completions
```

**请求体**:
```json
{
    "model": "qwen-vl-plus",
    "messages": [
        {
            "role": "user",
            "content": [
                {"type": "text", "text": "这张图片里有什么？"},
                {"type": "image_url", "image_url": {"url": "https://server/api/images/img_xxx.jpg"}}
            ]
        }
    ]
}
```

---

## 七、健康检查接口

### 7.1 服务健康检查
```
GET /health
```

**响应**:
```json
{
    "status": "ok",
    "timestamp": 1701388800000,
    "version": "1.0.0"
}
```
