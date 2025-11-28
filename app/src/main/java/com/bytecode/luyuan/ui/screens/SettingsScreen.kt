package com.bytecode.luyuan.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.bytecode.luyuan.data.model.ApiConfigEntity
import com.bytecode.luyuan.data.model.User
import com.bytecode.luyuan.ui.navigation.Screen
import com.bytecode.luyuan.ui.theme.AppStrings
import com.bytecode.luyuan.ui.theme.LocalAppStrings
import com.bytecode.luyuan.ui.viewmodel.ApiTestState
import com.bytecode.luyuan.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: SettingsViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val darkModeEnabled by viewModel.darkModeEnabled.collectAsState()
    val language by viewModel.language.collectAsState()
    val apiConfig by viewModel.apiConfig.collectAsState()
    val apiTestState by viewModel.apiTestState.collectAsState()
    val savedApiConfigs by viewModel.savedApiConfigs.collectAsState()

    val strings = LocalAppStrings.current
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showApiConfigDialog by remember { mutableStateOf(false) }
    var showSavedConfigsDialog by remember { mutableStateOf(false) }
    var showAddConfigDialog by remember { mutableStateOf(false) }
    
    // API Config dialog state
    var apiBaseUrl by remember(apiConfig) { mutableStateOf(apiConfig.baseUrl) }
    var apiKey by remember(apiConfig) { mutableStateOf(apiConfig.apiKey) }
    var modelName by remember(apiConfig) { mutableStateOf(apiConfig.modelName) }
    
    // Add config dialog state
    var newConfigName by remember { mutableStateOf("") }
    var newConfigBaseUrl by remember { mutableStateOf("https://api.openai.com") }
    var newConfigApiKey by remember { mutableStateOf("") }
    var newConfigModelName by remember { mutableStateOf("gpt-3.5-turbo") }

    // Language Dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(strings.selectLanguage) },
            text = {
                Column {
                    Text(
                        text = "English",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setLanguage("English")
                                showLanguageDialog = false
                            }
                            .padding(16.dp)
                    )
                    Text(
                        text = "中文",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setLanguage("中文")
                                showLanguageDialog = false
                            }
                            .padding(16.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
    
    // API Config Dialog
    if (showApiConfigDialog) {
        LaunchedEffect(Unit) {
            viewModel.resetApiTestState()
        }
        
        AlertDialog(
            onDismissRequest = { 
                showApiConfigDialog = false
                viewModel.resetApiTestState()
            },
            title = { Text(strings.apiConfig) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = apiBaseUrl,
                        onValueChange = { apiBaseUrl = it },
                        label = { Text(strings.apiBaseUrl) },
                        placeholder = { Text("https://api.openai.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(strings.apiKey) },
                        placeholder = { Text("sk-...") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = modelName,
                        onValueChange = { modelName = it },
                        label = { Text(strings.modelName) },
                        placeholder = { Text("gpt-3.5-turbo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Test Connection Result
                    when (val state = apiTestState) {
                        is ApiTestState.Idle -> {}
                        is ApiTestState.Testing -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                Text(strings.testing, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is ApiTestState.Success -> {
                            Text(
                                text = strings.connectionSuccess,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        is ApiTestState.Error -> {
                            Text(
                                text = "${strings.connectionFailed}: ${state.message}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    
                    // Test Connection Button
                    OutlinedButton(
                        onClick = { 
                            viewModel.saveApiConfig(apiBaseUrl, apiKey, modelName)
                            viewModel.testApiConnection() 
                        },
                        enabled = apiBaseUrl.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank() && apiTestState !is ApiTestState.Testing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.testConnection)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveApiConfig(apiBaseUrl, apiKey, modelName)
                        showApiConfigDialog = false
                        viewModel.resetApiTestState()
                    },
                    enabled = apiBaseUrl.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank()
                ) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showApiConfigDialog = false
                    viewModel.resetApiTestState()
                    // Reset to original values
                    apiBaseUrl = apiConfig.baseUrl
                    apiKey = apiConfig.apiKey
                    modelName = apiConfig.modelName
                }) {
                    Text(strings.cancel)
                }
            }
        )
    }
    
    // Saved API Configs Dialog
    if (showSavedConfigsDialog) {
        AlertDialog(
            onDismissRequest = { showSavedConfigsDialog = false },
            title = { Text(strings.savedConfigs) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (savedApiConfigs.isEmpty()) {
                        Text(
                            text = strings.noSavedConfigs,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        savedApiConfigs.forEach { config ->
                            SavedConfigItem(
                                config = config,
                                strings = strings,
                                onSwitch = {
                                    viewModel.switchToApiConfig(config.id)
                                    showSavedConfigsDialog = false
                                },
                                onSetDefault = {
                                    viewModel.setDefaultApiConfig(config.id)
                                },
                                onDelete = {
                                    viewModel.deleteApiConfig(config.id)
                                }
                            )
                        }
                    }
                    
                    // Add new config button
                    OutlinedButton(
                        onClick = { 
                            showSavedConfigsDialog = false
                            showAddConfigDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.addConfig)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSavedConfigsDialog = false }) {
                    Text(strings.close)
                }
            }
        )
    }
    
    // Add New Config Dialog
    if (showAddConfigDialog) {
        AlertDialog(
            onDismissRequest = { 
                showAddConfigDialog = false
                newConfigName = ""
                newConfigBaseUrl = "https://api.openai.com"
                newConfigApiKey = ""
                newConfigModelName = "gpt-3.5-turbo"
            },
            title = { Text(strings.addConfig) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = newConfigName,
                        onValueChange = { newConfigName = it },
                        label = { Text(strings.configName) },
                        placeholder = { Text("My API Config") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = newConfigBaseUrl,
                        onValueChange = { newConfigBaseUrl = it },
                        label = { Text(strings.apiBaseUrl) },
                        placeholder = { Text("https://api.openai.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = newConfigApiKey,
                        onValueChange = { newConfigApiKey = it },
                        label = { Text(strings.apiKey) },
                        placeholder = { Text("sk-...") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = newConfigModelName,
                        onValueChange = { newConfigModelName = it },
                        label = { Text(strings.modelName) },
                        placeholder = { Text("gpt-3.5-turbo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveNewApiConfig(
                            name = newConfigName,
                            baseUrl = newConfigBaseUrl,
                            apiKey = newConfigApiKey,
                            modelName = newConfigModelName
                        )
                        showAddConfigDialog = false
                        newConfigName = ""
                        newConfigBaseUrl = "https://api.openai.com"
                        newConfigApiKey = ""
                        newConfigModelName = "gpt-3.5-turbo"
                    },
                    enabled = newConfigName.isNotBlank() && newConfigBaseUrl.isNotBlank() 
                        && newConfigApiKey.isNotBlank() && newConfigModelName.isNotBlank()
                ) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showAddConfigDialog = false
                    newConfigName = ""
                    newConfigBaseUrl = "https://api.openai.com"
                    newConfigApiKey = ""
                    newConfigModelName = "gpt-3.5-turbo"
                }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.settingsTitle) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Profile Section
            ProfileHeader(user = currentUser, strings = strings)
            
            Spacer(modifier = Modifier.height(24.dp))

            // Settings Groups
            SettingsGroupTitle(strings.generalGroup)
            SettingsItem(
                icon = Icons.Default.Person, 
                title = strings.account, 
                subtitle = strings.accountSubtitle
            )
            SettingsSwitchItem(
                icon = Icons.Default.Notifications, 
                title = strings.notifications, 
                checked = notificationsEnabled,
                onCheckedChange = { viewModel.toggleNotifications(it) }
            )
            
            SettingsSwitchItem(
                icon = Icons.Default.Info, 
                title = strings.darkMode,
                checked = darkModeEnabled,
                onCheckedChange = { viewModel.toggleDarkMode(it) }
            )

            SettingsItem(
                icon = Icons.Default.Info,
                title = strings.language,
                subtitle = language,
                onClick = { showLanguageDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            // AI Service Group
            SettingsGroupTitle(strings.apiConfigGroup)
            SettingsItem(
                icon = Icons.Default.Settings,
                title = strings.apiConfig,
                subtitle = if (apiConfig.isConfigured) strings.configured else strings.notConfigured,
                onClick = { showApiConfigDialog = true }
            )
            SettingsItem(
                icon = Icons.Default.Star,
                title = strings.savedConfigs,
                subtitle = "${savedApiConfigs.size} ${strings.configsSaved}",
                onClick = { showSavedConfigsDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SettingsGroupTitle(strings.dataPrivacyGroup)
            SettingsItem(
                icon = Icons.Default.Delete, 
                title = strings.clearHistory, 
                subtitle = strings.clearHistorySubtitle, 
                isDestructive = true,
                onClick = { viewModel.clearHistory() }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SettingsGroupTitle(strings.aboutGroup)
            SettingsItem(
                icon = Icons.Default.Info, 
                title = strings.version, 
                subtitle = "v0.1.6.3"
            )
            SettingsItem(
                icon = Icons.Default.Info, 
                title = strings.helpSupport
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SettingsItem(
                icon = Icons.Default.ExitToApp,
                title = strings.logout,
                isDestructive = true,
                onClick = {
                    viewModel.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}

@Composable
fun ProfileHeader(user: User?, strings: AppStrings) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Profile Picture",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = user?.username ?: strings.guest, style = MaterialTheme.typography.titleLarge)
        Text(text = user?.email ?: strings.notLoggedIn, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingsGroupTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    isDestructive: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * 保存的 API 配置项
 */
@Composable
fun SavedConfigItem(
    config: ApiConfigEntity,
    strings: AppStrings,
    onSwitch: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (config.isDefault) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSwitch)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = config.name,
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (config.isDefault) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Star,
                            contentDescription = strings.defaultConfig,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = config.modelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Set as default button
            if (!config.isDefault) {
                IconButton(onClick = onSetDefault) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = strings.setAsDefault,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Delete button
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = strings.deleteConfig,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
