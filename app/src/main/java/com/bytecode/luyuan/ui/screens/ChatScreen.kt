package com.bytecode.luyuan.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.bytecode.luyuan.data.model.Message
import com.bytecode.luyuan.ui.viewmodel.ChatViewModel
import dev.jeziellago.compose.markdowntext.MarkdownText

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding

import com.bytecode.luyuan.ui.theme.LocalAppStrings
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController, 
    viewModel: ChatViewModel,
    sessionId: String
) {
    val context = LocalContext.current
    
    // 确保 ViewModel 在 UI 渲染前已设置 sessionId
    LaunchedEffect(Unit) {
        viewModel.setSessionId(sessionId)
    }
    
    val messages by viewModel.messages.collectAsState()
    val streamingContent by viewModel.streamingContent.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val strings = LocalAppStrings.current
    
    // 会话选择下拉菜单状态
    var showSessionMenu by remember { mutableStateOf(false) }
    
    // 记录当前正在编辑的消息ID
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    
    // 图片选择状态
    var selectedImageBase64 by remember { mutableStateOf<String?>(null) }
    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                
                if (bitmap != null) {
                    selectedImageBitmap = bitmap
                    // 压缩并转换为 Base64
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                    val byteArray = outputStream.toByteArray()
                    selectedImageBase64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Auto-scroll to bottom when new message is added or streaming
    LaunchedEffect(messages.size, streamingContent) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1 + (if (streamingContent != null) 1 else 0))
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { 
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.clickable { showSessionMenu = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentSession?.title ?: strings.newChat,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1
                            )
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = strings.switchSession,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // 会话选择下拉菜单
                        DropdownMenu(
                            expanded = showSessionMenu,
                            onDismissRequest = { showSessionMenu = false }
                        ) {
                            sessions.take(10).forEach { session ->
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            text = session.title,
                                            maxLines = 1,
                                            style = if (session.id == sessionId) 
                                                MaterialTheme.typography.bodyMedium.copy(
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            else MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    onClick = {
                                        showSessionMenu = false
                                        if (session.id != sessionId) {
                                            navController.navigate("chat/${session.id}") {
                                                popUpTo("chat/$sessionId") { inclusive = true }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            ChatInputArea(
                value = inputText,
                onValueChange = { inputText = it },
                onSend = {
                    if ((inputText.isNotBlank() || selectedImageBase64 != null) && !isLoading) {
                        viewModel.sendMessage(inputText, selectedImageBase64)
                        inputText = ""
                        selectedImageBase64 = null
                        selectedImageBitmap = null
                    }
                },
                onAddImage = { imagePickerLauncher.launch("image/*") },
                selectedImageBitmap = selectedImageBitmap,
                onRemoveImage = {
                    selectedImageBase64 = null
                    selectedImageBitmap = null
                },
                modifier = Modifier.navigationBarsPadding().imePadding(),
                placeholder = strings.typeMessage,
                enabled = !isLoading
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(messages) { message ->
                MessageBubble(
                    message = message,
                    isEditing = editingMessageId == message.id,
                    onStartEdit = { editingMessageId = message.id },
                    onCancelEdit = { editingMessageId = null },
                    onConfirmEdit = { newContent ->
                        viewModel.editMessage(message, newContent)
                        editingMessageId = null
                    },
                    onCopyMessage = { content ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("message", content)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, strings.messageCopied, Toast.LENGTH_SHORT).show()
                    }
                )
            }
            
            // 显示流式响应内容
            if (streamingContent != null) {
                item {
                    StreamingMessageBubble(content = streamingContent ?: "")
                }
            } else if (isLoading && messages.isNotEmpty() && messages.last().isUser) {
                // 等待 AI 回复时显示加载动画
                item {
                    LoadingMessageBubble()
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: Message, 
    isEditing: Boolean,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onConfirmEdit: (String) -> Unit,
    onCopyMessage: (String) -> Unit = {}
) {
    val isUser = message.isUser
    var editContent by remember(message.id) { mutableStateOf(message.content) }
    
    // 重置编辑内容当开始编辑时
    LaunchedEffect(isEditing) {
        if (isEditing) {
            editContent = message.content
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            Avatar(icon = Icons.Default.Face, backgroundColor = MaterialTheme.colorScheme.secondaryContainer)
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            // 用户消息内容右对齐，AI 消息内容左对齐
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (isUser) 20.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 20.dp
                ),
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                if (isEditing && isUser) {
                    // 内联编辑模式
                    Column(modifier = Modifier.padding(12.dp)) {
                        OutlinedTextField(
                            value = editContent,
                            onValueChange = { editContent = it },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 5,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.onPrimary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                                focusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                unfocusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                cursorColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(
                                onClick = onCancelEdit,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Cancel",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { onConfirmEdit(editContent) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Confirm",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                } else {
                    // 正常显示模式
                    Column(modifier = Modifier.padding(16.dp)) {
                        // 如果有图片，先显示图片
                        message.imageBase64?.let { base64 ->
                            val bitmap = remember(base64) {
                                try {
                                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            bitmap?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "Attached image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                        
                        // 显示文本（支持 Markdown）
                        if (message.content.isNotBlank()) {
                            MarkdownText(
                                markdown = message.content,
                                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
            
            // 用户消息显示编辑按钮（非编辑状态下），AI 消息显示复制按钮
            if (!isEditing) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 复制按钮（所有消息都显示）
                    IconButton(
                        onClick = { onCopyMessage(message.content) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Copy",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // 用户消息显示编辑按钮
                    if (isUser) {
                        IconButton(
                            onClick = onStartEdit,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Avatar(icon = Icons.Default.Person, backgroundColor = MaterialTheme.colorScheme.primaryContainer)
        }
    }
}

@Composable
fun Avatar(icon: ImageVector, backgroundColor: Color) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
fun ChatInputArea(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAddImage: () -> Unit = {},
    selectedImageBitmap: Bitmap? = null,
    onRemoveImage: () -> Unit = {},
    modifier: Modifier = Modifier,
    placeholder: String = "Type a message...",
    enabled: Boolean = true
) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 显示已选择的图片预览
            selectedImageBitmap?.let { bitmap ->
                Box(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Selected image",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    // 删除按钮
                    IconButton(
                        onClick = onRemoveImage,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(24.dp)
                            .background(
                                MaterialTheme.colorScheme.errorContainer,
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove image",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 添加图片按钮
                IconButton(
                    onClick = onAddImage,
                    modifier = Modifier.size(40.dp),
                    enabled = enabled
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add image",
                        tint = if (enabled) MaterialTheme.colorScheme.primary 
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(placeholder) },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    enabled = enabled
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onSend,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (enabled) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.surfaceVariant, 
                            CircleShape
                        ),
                    enabled = enabled
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (enabled) MaterialTheme.colorScheme.onPrimary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 流式响应消息气泡
 * 
 * 显示正在生成的 AI 回复，带有打字效果
 */
@Composable
fun StreamingMessageBubble(content: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Avatar(icon = Icons.Default.Face, backgroundColor = MaterialTheme.colorScheme.secondaryContainer)
        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = 4.dp,
                bottomEnd = 20.dp
            ),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (content.isNotEmpty()) {
                    MarkdownText(
                        markdown = content,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
                // 闪烁的光标效果
                Text(
                    text = "▌",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * 加载中消息气泡
 * 
 * 显示三点跳动加载动画，等待 AI 回复
 */
@Composable
fun LoadingMessageBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    
    // 三个点的动画偏移
    val dot1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    
    val dot2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 150),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    
    val dot3Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Avatar(icon = Icons.Default.Face, backgroundColor = MaterialTheme.colorScheme.secondaryContainer)
        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = 4.dp,
                bottomEnd = 20.dp
            ),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .padding(top = (-dot1Offset).dp.coerceAtLeast(0.dp))
                        .background(
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                            CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .padding(top = (-dot2Offset).dp.coerceAtLeast(0.dp))
                        .background(
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                            CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .padding(top = (-dot3Offset).dp.coerceAtLeast(0.dp))
                        .background(
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                            CircleShape
                        )
                )
            }
        }
    }
}
