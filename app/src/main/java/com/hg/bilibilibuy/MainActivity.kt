package com.hg.bilibilibuy

import android.app.Activity
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hg.bilibilibuy.ui.theme.BilibilibuyTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DEFAULT_SERVER_URL = ""
private const val PROJECT_REPOSITORY_URL = "https://github.com/Air-ban/bilibilibuy"
private const val PREFS_NAME = "bili_buy_settings"
private const val KEY_SERVER_URL = "server_url"
private const val KEY_CUSTOM_SERVER_URL = "custom_server_url"
private const val KEY_ISOLATED_USERNAME = "isolated_username"
private const val KEY_USER_ACCESS_KEY = "user_access_key"
private val BILIBILI_APP_PACKAGES = listOf(
    "tv.danmaku.bili",
    "com.bilibili.app.in",
    "tv.danmaku.bilibilihd",
    "com.bilibili.app.blue"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BilibilibuyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BiliBuyApp()
                }
            }
        }
    }
}

private enum class AppTab(
    val title: String,
    val label: String,
    val mark: String
) {
    Settings("服务器设置", "设置", "S"),
    Login("账号登录", "登录", "L"),
    Config("配置生成", "配置", "C"),
    Task("启动抢票", "抢票", "T")
}

private enum class ActionState {
    Idle,
    Loading,
    Success,
    Error
}

@Composable
fun BiliBuyApp() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf(AppTab.Settings) }
    val initialServerUrl = remember {
        prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
    }
    var customServerUrl by remember {
        mutableStateOf(
            prefs.getString(KEY_CUSTOM_SERVER_URL, "")?.ifBlank {
                initialServerUrl.takeIf { it != DEFAULT_SERVER_URL }.orEmpty()
            }.orEmpty()
        )
    }
    var useCustomServer by remember { mutableStateOf(initialServerUrl != DEFAULT_SERVER_URL) }
    var isolatedUsername by remember {
        mutableStateOf(prefs.getString(KEY_ISOLATED_USERNAME, "") ?: "")
    }
    var userAccessKey by remember {
        mutableStateOf(prefs.getString(KEY_USER_ACCESS_KEY, "") ?: "")
    }
    var healthMessage by remember { mutableStateOf("未测试连接") }
    var serverConnectionState by remember { mutableStateOf(ActionState.Idle) }
    var authMessage by remember { mutableStateOf("未检查登录状态") }
    var authActionState by remember { mutableStateOf(ActionState.Idle) }
    var qrLogin by remember { mutableStateOf<QrLogin?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var isPolling by remember { mutableStateOf(false) }
    var qrPollingJob by remember { mutableStateOf<Job?>(null) }

    var projectInput by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf("") }
    var purchaseContext by remember { mutableStateOf<PurchaseContext?>(null) }
    var purchaseContextCacheKey by remember { mutableStateOf("") }
    var loadedProjectInput by remember { mutableStateOf("") }
    var selectedTicketIndex by remember { mutableIntStateOf(-1) }
    val selectedBuyerIndices = remember { mutableStateListOf<Int>() }
    var selectedAddressIndex by remember { mutableIntStateOf(-1) }
    var contactName by remember { mutableStateOf("") }
    var contactTel by remember { mutableStateOf("") }
    var projectMessage by remember { mutableStateOf("") }
    var configFiles by remember { mutableStateOf<List<ConfigFile>>(emptyList()) }
    var selectedTaskConfig by remember { mutableStateOf("") }
    var taskTimeStart by remember { mutableStateOf("") }
    var taskInterval by remember { mutableStateOf("1000") }
    var taskProxy by remember { mutableStateOf("") }
    var taskRunIdInput by remember { mutableStateOf("") }
    var taskMessage by remember { mutableStateOf("请选择已有配置文件启动托管任务") }
    var taskActionState by remember { mutableStateOf(ActionState.Idle) }
    var isTaskPolling by remember { mutableStateOf(false) }
    var managedTaskPollingJob by remember { mutableStateOf<Job?>(null) }
    var currentManagedRunId by remember { mutableStateOf("") }
    var managedTaskStatus by remember { mutableStateOf<ManagedTaskStatus?>(null) }

    fun activeServerUrl() = if (useCustomServer) customServerUrl.trim() else DEFAULT_SERVER_URL

    fun api() = BiliApiClient(activeServerUrl(), isolatedUsername.trim(), userAccessKey.trim())

    fun rememberUserCredentials(username: String, accessKey: String = userAccessKey) {
        val normalized = username.trim()
        if (!normalized.isBiliUsername()) return
        val normalizedAccessKey = accessKey.trim()
        isolatedUsername = normalized
        if (normalizedAccessKey.isNotBlank()) {
            userAccessKey = normalizedAccessKey
        }
        prefs.edit()
            .putString(KEY_ISOLATED_USERNAME, normalized)
            .putString(KEY_USER_ACCESS_KEY, normalizedAccessKey)
            .apply()
    }

    fun clearLocalUser() {
        isolatedUsername = ""
        userAccessKey = ""
        prefs.edit()
            .remove(KEY_ISOLATED_USERNAME)
            .remove(KEY_USER_ACCESS_KEY)
            .apply()
        qrPollingJob?.cancel()
        qrPollingJob = null
        isPolling = false
        qrLogin = null
        qrBitmap = null
        authActionState = ActionState.Idle
        authMessage = "已清除本机账号，请重新登录"
        purchaseContext = null
        purchaseContextCacheKey = ""
        loadedProjectInput = ""
        configFiles = emptyList()
        selectedTaskConfig = ""
    }

    fun saveServerUrl() {
        val selectedUrl = activeServerUrl()
        prefs.edit()
            .putString(KEY_SERVER_URL, selectedUrl)
            .putString(KEY_CUSTOM_SERVER_URL, customServerUrl.trim())
            .putString(KEY_ISOLATED_USERNAME, isolatedUsername.trim())
            .putString(KEY_USER_ACCESS_KEY, userAccessKey.trim())
            .apply()
    }

    fun resetSelections(contextData: PurchaseContext) {
        selectedTicketIndex = contextData.tickets.firstOrNull()?.index ?: -1
        selectedBuyerIndices.clear()
        contextData.buyers.firstOrNull()?.let { selectedBuyerIndices.add(it.index) }
        selectedAddressIndex = contextData.addresses.firstOrNull()?.index ?: -1
        contactName = contextData.addresses.firstOrNull()?.name
            ?: contextData.buyers.firstOrNull()?.name
            ?: contactName
        contactTel = contextData.addresses.firstOrNull()?.phone ?: contactTel
        selectedDate = contextData.selectedDate.ifBlank { selectedDate }
    }

    fun clearPurchaseContext(clearSelectedDate: Boolean = false) {
        purchaseContext = null
        purchaseContextCacheKey = ""
        loadedProjectInput = ""
        selectedTicketIndex = -1
        selectedBuyerIndices.clear()
        selectedAddressIndex = -1
        if (clearSelectedDate) {
            selectedDate = ""
        }
    }

    fun purchaseContextKey(): String {
        return listOf(
            activeServerUrl(),
            isolatedUsername.trim(),
            userAccessKey.trim(),
            projectInput.trim(),
            selectedDate.trim(),
            phone.trim()
        ).joinToString("\u001F")
    }

    fun runHealthCheck() {
        saveServerUrl()
        scope.launch {
            isBusy = true
            serverConnectionState = ActionState.Loading
            healthMessage = "正在连接服务器..."
            val health = api().health()
            healthMessage = if (health.ok) {
                serverConnectionState = ActionState.Success
                "连接成功，版本 ${health.version.ifBlank { "未知" }}"
            } else {
                serverConnectionState = ActionState.Error
                "连接失败：${health.message}"
            }
            isBusy = false
        }
    }

    fun checkAuthStatus() {
        saveServerUrl()
        scope.launch {
            isBusy = true
            authActionState = ActionState.Loading
            authMessage = "正在检查登录状态..."
            try {
                val status = api().authStatus()
                authMessage = if (status.loggedIn) {
                    rememberUserCredentials(status.username)
                    authActionState = ActionState.Success
                    "已登录：${status.username}${status.cookiesPath.displayPathSuffix()}"
                } else {
                    authActionState = ActionState.Idle
                    "未登录，下一步：${status.nextAction.ifBlank { "扫码登录" }}${status.cookiesPath.displayPathSuffix()}"
                }
            } catch (error: Exception) {
                if (error.isInvalidAccessKey()) {
                    clearLocalUser()
                    authActionState = ActionState.Idle
                    authMessage = "本机账号密钥已失效，请重新登录"
                } else {
                    authActionState = ActionState.Error
                    authMessage = "检查失败：${error.message ?: "服务器不可用"}"
                }
            }
            isBusy = false
        }
    }

    fun startQrPolling(
        qr: QrLogin? = qrLogin,
        serverUrl: String = activeServerUrl()
    ) {
        val currentQr = qr?.takeIf { it.qrcodeKey.isNotBlank() } ?: return
        val pollApi = BiliApiClient(serverUrl)
        qrPollingJob?.cancel()
        qrPollingJob = scope.launch {
            isPolling = true
            authActionState = ActionState.Loading
            authMessage = "正在等待扫码确认..."
            repeat(120) {
                if (!isPolling) return@launch
                try {
                    val result = pollApi.pollQrLogin(currentQr.qrcodeKey)
                    authMessage = when {
                        result.ok -> "登录成功：${result.username}，cookies ${result.cookiesCount} 条"
                        result.status.isNotBlank() -> "${result.status}：${result.message}"
                        else -> result.message.ifBlank { "等待确认..." }
                    }
                    if (result.ok) {
                        rememberUserCredentials(result.username, result.accessKey)
                        isPolling = false
                        qrPollingJob = null
                        authActionState = ActionState.Success
                        qrLogin = null
                        qrBitmap = null
                        authMessage = "已成功登录：${result.username}，cookies ${result.cookiesCount} 条${result.username.displayUsernameSuffix()}"
                        context.returnToApp()
                        return@launch
                    }
                } catch (error: Exception) {
                    authActionState = ActionState.Error
                    authMessage = "轮询失败：${error.message ?: "服务器不可用"}"
                }
                delay(1_500)
            }
            isPolling = false
            qrPollingJob = null
            authActionState = ActionState.Error
            authMessage = "登录确认超时，请重新生成二维码"
        }
    }

    fun stopQrPolling() {
        qrPollingJob?.cancel()
        qrPollingJob = null
        isPolling = false
        if (qrLogin != null) {
            authActionState = ActionState.Idle
            authMessage = "已停止自动轮询，可重新生成二维码后自动重试"
        }
    }

    fun generateQrLogin() {
        saveServerUrl()
        scope.launch {
            stopQrPolling()
            isBusy = true
            authActionState = ActionState.Loading
            authMessage = "正在生成二维码..."
            try {
                val loginApi = api()
                val qr = loginApi.generateQrLogin()
                qrLogin = qr
                qrBitmap = qr.qrImageBase64.decodeBitmap()
                if (qr.ok && qr.qrcodeKey.isNotBlank()) {
                    authMessage = "二维码已生成，正在自动等待确认..."
                    startQrPolling(qr, activeServerUrl())
                } else {
                    authActionState = ActionState.Error
                    authMessage = "二维码生成失败：${qr.error.ifBlank { "缺少登录轮询 key" }}"
                }
            } catch (error: Exception) {
                authActionState = ActionState.Error
                authMessage = "二维码生成失败：${error.message ?: "服务器不可用"}"
            }
            isBusy = false
        }
    }

    fun loadPurchaseContext() {
        saveServerUrl()
        scope.launch {
            isBusy = true
            projectMessage = "正在获取项目上下文..."
            try {
                val cacheKey = purchaseContextKey()
                purchaseContext?.takeIf { purchaseContextCacheKey == cacheKey }?.let { data ->
                    resetSelections(data)
                    projectMessage = "已加载：${data.projectName}，票档 ${data.tickets.size} 个"
                    isBusy = false
                    return@launch
                }
                val data = api().purchaseContext(
                    projectInput.trim(),
                    selectedDate.takeIf { it.isNotBlank() },
                    phone.trim()
                )
                purchaseContext = data
                resetSelections(data)
                purchaseContextCacheKey = purchaseContextKey()
                loadedProjectInput = projectInput.trim()
                projectMessage = "已加载：${data.projectName}，票档 ${data.tickets.size} 个"
            } catch (error: Exception) {
                if (error.isInvalidAccessKey()) {
                    clearLocalUser()
                    projectMessage = "本机账号密钥已失效，请重新登录"
                } else {
                    projectMessage = "加载失败：${error.message ?: "服务器不可用"}"
                }
            }
            isBusy = false
        }
    }

    fun generateConfig() {
        saveServerUrl()
        scope.launch {
            isBusy = true
            projectMessage = "正在生成配置文件..."
            try {
                val result = api().generateConfig(
                    projectInput = projectInput.trim(),
                    selectedDate = selectedDate.takeIf { it.isNotBlank() },
                    ticketIndex = selectedTicketIndex,
                    buyerIndices = selectedBuyerIndices.sorted(),
                    addressIndex = selectedAddressIndex,
                    buyer = contactName.trim(),
                    tel = contactTel.trim(),
                    phone = phone.trim(),
                    purchaseContext = purchaseContext
                )
                projectMessage = if (result.ok) {
                    "配置生成成功"
                } else {
                    "配置生成失败：${result.error}"
                }
            } catch (error: Exception) {
                projectMessage = "生成失败：${error.message ?: "服务器不可用"}"
            }
            isBusy = false
        }
    }

    fun updateManagedTaskStatus(status: ManagedTaskStatus, fallbackRunId: String) {
        currentManagedRunId = status.runId.ifBlank { fallbackRunId }
        managedTaskStatus = status
        taskActionState = status.toActionState()
        taskMessage = "当前状态：${status.status.ifBlank { "未知" }}"
    }

    fun stopManagedTaskPolling() {
        managedTaskPollingJob?.cancel()
        managedTaskPollingJob = null
        isTaskPolling = false
    }

    fun startManagedTaskPolling(runId: String) {
        if (runId.isBlank()) return
        managedTaskPollingJob?.cancel()
        managedTaskPollingJob = scope.launch {
            isTaskPolling = true
            var consecutiveErrors = 0
            while (true) {
                try {
                    val status = api().managedTaskStatus(runId)
                    consecutiveErrors = 0
                    updateManagedTaskStatus(status, runId)
                    if (status.isTerminal()) break
                } catch (error: Exception) {
                    consecutiveErrors += 1
                    taskActionState = ActionState.Error
                    taskMessage = "查询失败：${error.message ?: "服务器不可用"}"
                    if (consecutiveErrors >= 5) break
                }
                delay(1_500)
            }
            isTaskPolling = false
            managedTaskPollingJob = null
        }
    }

    fun loadConfigFilesForTask() {
        saveServerUrl()
        scope.launch {
            isBusy = true
            taskActionState = ActionState.Loading
            taskMessage = "正在加载配置文件..."
            try {
                val files = api().configList()
                configFiles = files
                val currentName = selectedTaskConfig.configFilename()
                selectedTaskConfig = if (
                    currentName.isNotBlank() &&
                    files.any { it.taskConfigName() == currentName }
                ) {
                    currentName
                } else {
                    files.firstOrNull()?.taskConfigName().orEmpty()
                }
                taskActionState = ActionState.Success
                taskMessage = "已加载 ${files.size} 个配置文件"
            } catch (error: Exception) {
                taskActionState = ActionState.Error
                taskMessage = "加载失败：${error.message ?: "服务器不可用"}"
            }
            isBusy = false
        }
    }

    fun startManagedTask() {
        val normalizedInterval = taskInterval.trim().takeIf { it.isNotBlank() }?.toIntOrNull()
        if (taskInterval.isNotBlank() && normalizedInterval == null) {
            taskMessage = "请求间隔必须是整数毫秒，或留空使用后端默认值"
            return
        }
        saveServerUrl()
        scope.launch {
            stopManagedTaskPolling()
            isBusy = true
            taskActionState = ActionState.Loading
            taskMessage = "正在启动托管抢票任务..."
            try {
                val result = api().startManagedTask(
                    configFile = selectedTaskConfig.trim(),
                    timeStart = taskTimeStart.trim(),
                    interval = normalizedInterval,
                    httpsProxys = taskProxy.trim(),
                    runId = taskRunIdInput.takeIf { it.isNotBlank() }
                )
                val startedRunId = result.runId
                    .ifBlank { taskRunIdInput.trim() }
                    .ifBlank { result.runDir.runIdFromPath() }
                currentManagedRunId = startedRunId
                taskRunIdInput = startedRunId.ifBlank { taskRunIdInput }
                managedTaskStatus = ManagedTaskStatus(
                    ok = result.ok,
                    runId = startedRunId,
                    status = "pending",
                    detail = "",
                    pid = result.pid,
                    paymentQrUrl = "",
                    error = result.error,
                    lastMessage = "任务已提交",
                    logsPath = "",
                    resultPath = result.runDir
                )
                taskMessage = if (result.ok) {
                    taskActionState = ActionState.Loading
                    startManagedTaskPolling(startedRunId)
                    "启动成功：${startedRunId.ifBlank { "等待 run_id" }}"
                } else {
                    taskActionState = ActionState.Error
                    "启动失败：${result.error}"
                }
            } catch (error: Exception) {
                taskActionState = ActionState.Error
                taskMessage = "启动失败：${error.message ?: "服务器不可用"}"
            }
            isBusy = false
        }
    }

    fun refreshManagedTaskStatus() {
        val runId = currentManagedRunId.ifBlank { taskRunIdInput }
        if (runId.isBlank()) {
            taskMessage = "没有可查询的 run_id"
            return
        }
        saveServerUrl()
        scope.launch {
            isBusy = true
            taskActionState = ActionState.Loading
            taskMessage = "正在查询任务状态..."
            try {
                val status = api().managedTaskStatus(runId.trim())
                updateManagedTaskStatus(status, runId.trim())
            } catch (error: Exception) {
                taskActionState = ActionState.Error
                taskMessage = "查询失败：${error.message ?: "服务器不可用"}"
            }
            isBusy = false
        }
    }

    fun cancelManagedTask() {
        val runId = currentManagedRunId.ifBlank { taskRunIdInput }
        if (runId.isBlank()) {
            taskMessage = "没有可取消的 run_id"
            return
        }
        saveServerUrl()
        scope.launch {
            stopManagedTaskPolling()
            isBusy = true
            taskActionState = ActionState.Loading
            taskMessage = "正在取消任务..."
            try {
                val status = api().cancelManagedTask(runId.trim())
                updateManagedTaskStatus(status, runId.trim())
                taskActionState = ActionState.Error
                taskMessage = "取消请求已发送：${status.runId.ifBlank { runId }}"
            } catch (error: Exception) {
                taskActionState = ActionState.Error
                taskMessage = "取消失败：${error.message ?: "服务器不可用"}"
            }
            isBusy = false
        }
    }

    LaunchedEffect(Unit) {
        checkAuthStatus()
    }

    LaunchedEffect(currentTab) {
        if (currentTab == AppTab.Task) {
            loadConfigFilesForTask()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Text(tab.mark, fontWeight = FontWeight.Bold) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AppHeader(currentTab.title)
            when (currentTab) {
                AppTab.Settings -> SettingsScreen(
                    customServerUrl = customServerUrl,
                    useCustomServer = useCustomServer,
                    onUseCustomServerChange = { useCustom ->
                        useCustomServer = useCustom
                        serverConnectionState = ActionState.Idle
                        healthMessage = "未测试连接"
                    },
                    onCustomServerUrlChange = { url ->
                        customServerUrl = url
                        serverConnectionState = ActionState.Idle
                        healthMessage = "未测试连接"
                    },
                    isolatedUsername = isolatedUsername,
                    onClearLocalUser = ::clearLocalUser,
                    healthMessage = healthMessage,
                    connectionState = serverConnectionState,
                    isBusy = isBusy,
                    onTest = ::runHealthCheck
                )

                AppTab.Login -> LoginScreen(
                    authMessage = authMessage,
                    actionState = authActionState,
                    qrBitmap = qrBitmap,
                    qrLogin = qrLogin,
                    isBusy = isBusy,
                    isPolling = isPolling,
                    onCheckStatus = ::checkAuthStatus,
                    onGenerateQr = ::generateQrLogin,
                    onOpenBiliAppLogin = { url ->
                        if (context.openBiliLoginUrl(url)) {
                            authActionState = ActionState.Loading
                            authMessage = "已跳转 B 站 APP，请确认登录后回到本应用等待结果"
                            if (!isPolling) startQrPolling()
                        } else if (context.openExternalUrl(url)) {
                            authActionState = ActionState.Loading
                            authMessage = "未找到可处理登录链接的 B 站 APP，已改用浏览器打开"
                            if (!isPolling) startQrPolling()
                        } else {
                            authActionState = ActionState.Error
                            authMessage = "无法打开登录链接，请检查是否安装浏览器或 B 站 APP"
                        }
                    },
                    onOpenLoginUrl = { url ->
                        if (context.openExternalUrl(url)) {
                            if (!isPolling) startQrPolling()
                        } else {
                            authActionState = ActionState.Error
                            authMessage = "无法打开登录链接，请检查是否安装浏览器"
                        }
                    },
                    onStopPolling = ::stopQrPolling
                )

                AppTab.Config -> ConfigScreen(
                    projectInput = projectInput,
                    onProjectInputChange = {
                        val shouldClearSelectedDate = purchaseContext != null &&
                            loadedProjectInput.isNotBlank() &&
                            it.trim() != loadedProjectInput
                        projectInput = it
                        clearPurchaseContext(clearSelectedDate = shouldClearSelectedDate)
                    },
                    phone = phone,
                    onPhoneChange = {
                        phone = it
                        clearPurchaseContext()
                    },
                    selectedDate = selectedDate,
                    onSelectedDateChange = {
                        selectedDate = it
                        clearPurchaseContext()
                    },
                    purchaseContext = purchaseContext,
                    selectedTicketIndex = selectedTicketIndex,
                    onSelectedTicketIndexChange = { selectedTicketIndex = it },
                    selectedBuyerIndices = selectedBuyerIndices,
                    selectedAddressIndex = selectedAddressIndex,
                    onSelectedAddressIndexChange = { index, address ->
                        selectedAddressIndex = index
                        contactName = address.name
                        contactTel = address.phone
                    },
                    contactName = contactName,
                    onContactNameChange = { contactName = it },
                    contactTel = contactTel,
                    onContactTelChange = { contactTel = it },
                    projectMessage = projectMessage,
                    actionState = projectMessage.toActionState(),
                    isBusy = isBusy,
                    canGenerate = selectedTicketIndex >= 0 &&
                        selectedBuyerIndices.isNotEmpty() &&
                        selectedAddressIndex >= 0 &&
                        contactName.isNotBlank() &&
                        contactTel.isNotBlank(),
                    onLoadContext = ::loadPurchaseContext,
                    onGenerateConfig = ::generateConfig
                )

                AppTab.Task -> TaskScreen(
                    configFiles = configFiles,
                    selectedConfig = selectedTaskConfig,
                    onSelectedConfigChange = { selectedTaskConfig = it },
                    timeStart = taskTimeStart,
                    onTimeStartChange = { taskTimeStart = it },
                    interval = taskInterval,
                    onIntervalChange = { taskInterval = it },
                    proxy = taskProxy,
                    onProxyChange = { taskProxy = it },
                    runIdInput = taskRunIdInput,
                    onRunIdInputChange = { taskRunIdInput = it },
                    taskMessage = taskMessage,
                    actionState = taskActionState,
                    status = managedTaskStatus,
                    isPolling = isTaskPolling,
                    isBusy = isBusy,
                    onLoadConfigs = ::loadConfigFilesForTask,
                    onStart = ::startManagedTask,
                    onRefreshStatus = ::refreshManagedTaskStatus,
                    onCancel = ::cancelManagedTask
                )
            }
        }
    }
}

@Composable
private fun AppHeader(title: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Bili 演出抢票配置生成",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsScreen(
    customServerUrl: String,
    useCustomServer: Boolean,
    onUseCustomServerChange: (Boolean) -> Unit,
    onCustomServerUrlChange: (String) -> Unit,
    isolatedUsername: String,
    onClearLocalUser: () -> Unit,
    healthMessage: String,
    connectionState: ActionState,
    isBusy: Boolean,
    onTest: () -> Unit
) {
    ScreenList {
        item {
            SectionCard(title = "API 服务器") {
                RadioRow(
                    selected = !useCustomServer,
                    onClick = { onUseCustomServerChange(false) },
                    title = "默认服务器",
                    subtitle = "使用内置 API 服务"
                )
                RadioRow(
                    selected = useCustomServer,
                    onClick = { onUseCustomServerChange(true) },
                    title = "自定义服务器",
                    subtitle = customServerUrl.ifBlank { "添加自定义 API 地址" }
                )
                if (useCustomServer) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customServerUrl,
                        onValueChange = onCustomServerUrlChange,
                        label = { Text("自定义服务器地址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onTest,
                        enabled = !isBusy && (!useCustomServer || customServerUrl.isNotBlank())
                    ) {
                        Text("连接")
                    }
                    ActionIndicator(state = connectionState)
                }
                ActionStatus(message = healthMessage, state = connectionState)
            }
        }

        item {
            SectionCard(title = "当前账号") {
                OutlinedTextField(
                    value = isolatedUsername.ifBlank { "未登录" },
                    onValueChange = {},
                    label = { Text("当前 B站账号") },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onClearLocalUser,
                    enabled = isolatedUsername.isNotBlank() && !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("清除本机账号")
                }
            }
        }

        item {
            SectionCard(title = "关于项目") {
                val context = LocalContext.current
                val appVersion = remember(context) { context.appVersionName() }
                Text(
                    text = "bilibilibuy 是用于配合 bilibili 购票 API 服务的 Android 客户端。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "开发者：溪落",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "版本：v$appVersion",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_REPOSITORY_URL))
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_github_mark),
                        contentDescription = "GitHub",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("GitHub 仓库")
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(
    authMessage: String,
    actionState: ActionState,
    qrBitmap: Bitmap?,
    qrLogin: QrLogin?,
    isBusy: Boolean,
    isPolling: Boolean,
    onCheckStatus: () -> Unit,
    onGenerateQr: () -> Unit,
    onOpenBiliAppLogin: (String) -> Unit,
    onOpenLoginUrl: (String) -> Unit,
    onStopPolling: () -> Unit
) {
    ScreenList {
        item {
            SectionCard(title = "账号状态") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onCheckStatus,
                        enabled = !isBusy
                    ) {
                        Text("检查状态")
                    }
                    OutlinedButton(
                        onClick = onGenerateQr,
                        enabled = !isBusy
                    ) {
                        Text("生成二维码")
                    }
                }
                ActionStatus(message = authMessage, state = actionState)
                if (isPolling) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        item {
            SectionCard(title = "扫码登录") {
                qrBitmap?.let { bitmap ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "登录二维码",
                            modifier = Modifier.size(220.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                qrLogin?.let { qr ->
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onOpenBiliAppLogin(qr.loginUrl) },
                            enabled = qr.loginUrl.isNotBlank()
                        ) {
                            Text("B站 APP 登录")
                        }
                        OutlinedButton(
                            onClick = { onOpenLoginUrl(qr.loginUrl) },
                            enabled = qr.loginUrl.isNotBlank()
                        ) {
                            Text("浏览器登录")
                        }
                        if (isPolling) {
                            TextButton(onClick = onStopPolling) {
                                Text("停止")
                            }
                        }
                    }
                    if (qr.loginUrl.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SelectionContainer {
                            Text(
                                text = qr.loginUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } ?: Text(
                    text = if (authMessage.startsWith("已成功登录") || authMessage.startsWith("已登录")) {
                        "登录已完成，可以进入配置页选择演出。"
                    } else {
                        "生成二维码后会自动等待确认；可点 B站 APP 登录、浏览器登录，或直接扫码。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ConfigScreen(
    projectInput: String,
    onProjectInputChange: (String) -> Unit,
    phone: String,
    onPhoneChange: (String) -> Unit,
    selectedDate: String,
    onSelectedDateChange: (String) -> Unit,
    purchaseContext: PurchaseContext?,
    selectedTicketIndex: Int,
    onSelectedTicketIndexChange: (Int) -> Unit,
    selectedBuyerIndices: MutableList<Int>,
    selectedAddressIndex: Int,
    onSelectedAddressIndexChange: (Int, AddressOption) -> Unit,
    contactName: String,
    onContactNameChange: (String) -> Unit,
    contactTel: String,
    onContactTelChange: (String) -> Unit,
    projectMessage: String,
    actionState: ActionState,
    isBusy: Boolean,
    canGenerate: Boolean,
    onLoadContext: () -> Unit,
    onGenerateConfig: () -> Unit
) {
    ScreenList {
        item {
            SectionCard(title = "项目") {
                OutlinedTextField(
                    value = projectInput,
                    onValueChange = onProjectInputChange,
                    label = { Text("演出 ID、详情页 URL 或 b23.tv 短链") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = selectedDate,
                        onValueChange = onSelectedDateChange,
                        label = { Text("日期，可留空") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = onPhoneChange,
                        label = { Text("手机号") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onLoadContext,
                    enabled = !isBusy && projectInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("获取项目上下文")
                }
                Text(
                    text = "支持 b23.tv 短链自动展开；日期支持 YYYY-MM-DD 或 YYYY/MM/DD，留空时后端会合并项目场次、每日场次和周边票档。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                ActionStatus(message = projectMessage, state = actionState)
            }
        }

        purchaseContext?.let { data ->
            item {
                SectionCard(title = "场次") {
                    Text(
                        text = data.projectName.ifBlank { "项目 ${data.projectId}" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = listOf(data.venueName, data.venueAddress)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val timeRange = listOf(data.projectStartTime, data.projectEndTime)
                        .filter { it.isNotBlank() }
                        .joinToString(" 至 ")
                    if (timeRange.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "活动时间：$timeRange",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (data.projectUrl.isNotBlank()) {
                        Text(
                            text = data.projectUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (data.isHotProject) {
                            AssistChip(onClick = {}, label = { Text("热门项目") })
                        }
                        AssistChip(
                            onClick = {},
                            label = { Text(if (data.hasEticket) "电子票" else "非电子票") }
                        )
                        if (data.username.isNotBlank()) {
                            AssistChip(onClick = {}, label = { Text(data.username) })
                        }
                    }
                    if (data.salesDates.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            data.salesDates.forEach { date ->
                                FilterChip(
                                    selected = selectedDate == date,
                                    onClick = { onSelectedDateChange(date) },
                                    label = { Text(date) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "票档") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        data.tickets.forEach { ticket ->
                            RadioRow(
                                selected = selectedTicketIndex == ticket.index,
                                onClick = { onSelectedTicketIndexChange(ticket.index) },
                                title = ticket.display.ifBlank {
                                    "${ticket.screen} ${ticket.desc} ${ticket.price.formatFen()}"
                                },
                                subtitle = listOfNotNull(
                                    ticket.saleStatus,
                                    ticket.saleStart.takeIf { it.isNotBlank() }?.let { "开售 $it" },
                                    "sku ${ticket.id}",
                                    ticket.linkId.takeIf { it.isNotBlank() }?.let { "link $it" },
                                    if (ticket.isHotProject) "热门" else null
                                )
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · ")
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = "购票人") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        data.buyers.forEach { buyer ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedBuyerIndices.contains(buyer.index),
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (!selectedBuyerIndices.contains(buyer.index)) {
                                                selectedBuyerIndices.add(buyer.index)
                                            }
                                            if (contactName.isBlank()) onContactNameChange(buyer.name)
                                        } else {
                                            selectedBuyerIndices.remove(buyer.index)
                                        }
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(buyer.name.ifBlank { "购票人 ${buyer.index + 1}" })
                                    Text(
                                        buyer.personalId,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "收货与联系人") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        data.addresses.forEach { address ->
                            RadioRow(
                                selected = selectedAddressIndex == address.index,
                                onClick = {
                                    onSelectedAddressIndexChange(address.index, address)
                                },
                                title = "${address.name} ${address.phone}",
                                subtitle = address.address.ifBlank { "地址 ID ${address.id}" }
                            )
                        }
                    }
                    OptionDivider()
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = contactName,
                            onValueChange = onContactNameChange,
                            label = { Text("联系人") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = contactTel,
                            onValueChange = onContactTelChange,
                            label = { Text("联系电话") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onGenerateConfig,
                        enabled = !isBusy && canGenerate,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("生成配置文件")
                    }
                    Text(
                        text = "生成配置会复用本次获取的项目上下文，不再重复请求 B 站项目、票档、购票人和地址接口。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskScreen(
    configFiles: List<ConfigFile>,
    selectedConfig: String,
    onSelectedConfigChange: (String) -> Unit,
    timeStart: String,
    onTimeStartChange: (String) -> Unit,
    interval: String,
    onIntervalChange: (String) -> Unit,
    proxy: String,
    onProxyChange: (String) -> Unit,
    runIdInput: String,
    onRunIdInputChange: (String) -> Unit,
    taskMessage: String,
    actionState: ActionState,
    status: ManagedTaskStatus?,
    isPolling: Boolean,
    isBusy: Boolean,
    onLoadConfigs: () -> Unit,
    onStart: () -> Unit,
    onRefreshStatus: () -> Unit,
    onCancel: () -> Unit
) {
    ScreenList {
        item {
            SectionCard(title = "选择已有配置") {
                Button(
                    onClick = onLoadConfigs,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("刷新配置文件")
                }
                ActionStatus(message = taskMessage, state = actionState)
                if (configFiles.isEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "还没有加载到配置文件。进入本页会自动拉取；也可以先在“配置”页生成配置文件。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    OptionDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        configFiles.forEach { file ->
                            val configName = file.taskConfigName()
                            RadioRow(
                                selected = selectedConfig.configFilename() == configName,
                                onClick = { onSelectedConfigChange(configName) },
                                title = configName.ifBlank { "未命名配置" },
                                subtitle = listOf(
                                    file.accountUsername.takeIf { it.isNotBlank() }?.let { "账号 $it" },
                                    file.boundAt.takeIf { it.isNotBlank() }?.let { "绑定 $it" }
                                ).filterNotNull().joinToString(" · ")
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "启动参数") {
                OutlinedTextField(
                    value = selectedConfig.configFilename(),
                    onValueChange = onSelectedConfigChange,
                    label = { Text("配置文件 *") },
                    singleLine = true,
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = timeStart,
                    onValueChange = onTimeStartChange,
                    label = { Text("开始时间，可留空") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "支持 ISO 时间或 HH:MM[:SS]；留空表示立即启动。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = interval,
                        onValueChange = onIntervalChange,
                        label = { Text("间隔毫秒，可留空") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = proxy,
                        onValueChange = onProxyChange,
                        label = { Text("代理，可留空") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = runIdInput,
                    onValueChange = onRunIdInputChange,
                    label = { Text("run_id，可留空") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onStart,
                    enabled = !isBusy && selectedConfig.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("启动托管抢票")
                }
                Text(
                    text = "* 为必填项。请选择已生成的配置文件；其它运行参数留空时使用后端默认值。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        item {
            SectionCard(title = "任务状态") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onRefreshStatus,
                        enabled = !isBusy && (status?.runId?.isNotBlank() == true || runIdInput.isNotBlank())
                    ) {
                        Text("查询状态")
                    }
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = !isBusy && (status?.runId?.isNotBlank() == true || runIdInput.isNotBlank())
                    ) {
                        Text("取消任务")
                    }
                }
                if (status == null) {
                    StatusText("启动后可在这里查看托管任务状态。")
                    return@SectionCard
                }
                OptionDivider()
                if (isPolling) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Text(
                    text = "${status.runId.ifBlank { runIdInput }} · ${status.status.ifBlank { "未知" }}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val paymentUrl = status.paymentUrl()
                if (status.isTicketSecured(paymentUrl)) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "已抢到票，请尽快完成支付。",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                if (paymentUrl.isNotBlank()) {
                    val paymentQrBitmap = remember(paymentUrl) { paymentUrl.toQrBitmap() }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "支付二维码",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "仅支持微信支付；如需使用其他渠道支付，请前往哔哩哔哩会员购。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    paymentQrBitmap?.let { bitmap ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "支付二维码",
                                modifier = Modifier.size(260.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            text = paymentUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (status.error.isNotBlank()) {
                    StatusText("错误：${status.error}")
                }
            }
        }
    }
}

@Composable
private fun ScreenList(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = {
            content()
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    )
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun StatusText(text: String) {
    if (text.isNotBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActionStatus(
    message: String,
    state: ActionState,
    modifier: Modifier = Modifier
) {
    if (message.isBlank()) return
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionIndicator(state = state)
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = when (state) {
                ActionState.Success -> Color(0xFF138A36)
                ActionState.Error -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ActionIndicator(state: ActionState) {
    val color = when (state) {
        ActionState.Success -> Color(0xFF138A36)
        ActionState.Error -> MaterialTheme.colorScheme.error
        ActionState.Loading -> MaterialTheme.colorScheme.primary
        ActionState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = when (state) {
            ActionState.Success -> "➜"
            ActionState.Error -> "!"
            ActionState.Loading -> "…"
            ActionState.Idle -> "○"
        },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = color
    )
}

@Composable
private fun OptionDivider() {
    Spacer(modifier = Modifier.height(14.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(14.dp))
}

@Composable
private fun RadioRow(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(title.ifBlank { "未命名选项" })
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun String.decodeBitmap(): Bitmap? {
    if (isBlank()) return null
    return try {
        val bytes = Base64.decode(this, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun Context.openBiliLoginUrl(url: String): Boolean {
    if (!url.isRealUrl()) return false
    return url.toBiliAppLoginUris().any { uri ->
        BILIBILI_APP_PACKAGES.any { packageName ->
            val intent = Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .withNewTaskWhenNeeded(this)
                .setPackage(packageName)
            startActivitySafely(intent)
        } || startActivitySafely(
            Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .withNewTaskWhenNeeded(this)
        )
    }
}

private fun Context.openExternalUrl(url: String): Boolean {
    if (!url.isRealUrl()) return false
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return startActivitySafely(intent)
}

private fun Context.returnToApp(): Boolean {
    val movedTask = try {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val task = activityManager?.appTasks?.firstOrNull()
        if (task != null) {
            task.moveToFront()
            true
        } else {
            false
        }
    } catch (_: Exception) {
        false
    }
    val intent = Intent(this, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
    return startActivitySafely(intent) || movedTask
}

private fun Intent.withNewTaskWhenNeeded(context: Context): Intent {
    if (context.findActivity() == null) {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return this
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private fun Context.startActivitySafely(intent: Intent): Boolean {
    return try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

private fun Context.appVersionName(): String {
    return try {
        val info = packageManager.getPackageInfo(packageName, 0)
        info.versionName ?: "未知"
    } catch (_: Exception) {
        "未知"
    }
}

private fun String.toBiliAppLoginUris(): List<Uri> {
    val encodedUrl = Uri.encode(this)
    return listOf(
        Uri.parse("bilibili://browser?url=$encodedUrl"),
        Uri.parse("bilibili://browser/?url=$encodedUrl")
    )
}

private fun String.displayPathSuffix(): String {
    return trim().takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty()
}

private fun String.displayUsernameSuffix(): String {
    return trim().takeIf { it.isNotBlank() }?.let { "（隔离用户：$it）" }.orEmpty()
}

private fun String.isBiliUsername(): Boolean {
    val normalized = trim()
    return normalized.isNotBlank() &&
        normalized != "Not login" &&
        normalized != "未登录" &&
        normalized != "unknown-user"
}

private fun Throwable.isInvalidAccessKey(): Boolean {
    val text = message.orEmpty()
    return "HTTP 403" in text || "invalid user access key" in text
}

private fun ManagedTaskStatus.paymentUrl(): String {
    if (paymentQrUrl.isRealUrl()) return paymentQrUrl
    val candidates = listOf(lastMessage, detail)
    val explicitPattern = Regex("""PAYMENT_QR_URL=(https?://\S+)""")
    val payUrlPattern = Regex("""https?://pay\.bilibili\.com/\S+""")
    for (text in candidates) {
        explicitPattern.find(text)?.groupValues?.getOrNull(1)?.cleanUrl()?.let {
            if (it.isRealUrl()) return it
        }
        payUrlPattern.find(text)?.value?.cleanUrl()?.let {
            if (it.isRealUrl()) return it
        }
    }
    return ""
}

private fun ManagedTaskStatus.isTicketSecured(paymentUrl: String = paymentUrl()): Boolean {
    if (paymentUrl.isNotBlank()) return true
    val text = listOf(status, detail, lastMessage).joinToString(" ").lowercase()
    if (text.isBlank()) return false
    val negativeKeywords = listOf("失败", "错误", "取消", "已取消", "未抢到", "sold out", "cancel", "error", "fail")
    if (negativeKeywords.any { it in text }) return false
    val securedKeywords = listOf(
        "抢票成功",
        "抢到票",
        "已抢到",
        "下单成功",
        "订单创建成功",
        "待支付",
        "支付二维码",
        "payment_qr_url",
        "payment_qr",
        "paying",
        "pending_payment",
        "success"
    )
    return securedKeywords.any { it in text }
}

private fun ManagedTaskStatus.isTerminal(): Boolean {
    if (isTicketSecured()) return true
    val text = listOf(status, detail, lastMessage, error).joinToString(" ").lowercase()
    if (text.isBlank()) return false
    val terminalKeywords = listOf(
        "success",
        "succeeded",
        "paid",
        "pending_payment",
        "failed",
        "failure",
        "error",
        "cancel",
        "cancelled",
        "timeout",
        "done",
        "finished",
        "成功",
        "已抢到",
        "待支付",
        "失败",
        "错误",
        "取消",
        "已取消",
        "超时",
        "结束",
        "完成"
    )
    return terminalKeywords.any { it in text }
}

private fun ManagedTaskStatus.toActionState(): ActionState {
    if (isTicketSecured()) return ActionState.Success
    val text = listOf(status, detail, lastMessage, error).joinToString(" ").lowercase()
    val errorKeywords = listOf("failed", "failure", "error", "cancel", "timeout", "失败", "错误", "取消", "超时")
    return when {
        errorKeywords.any { it in text } -> ActionState.Error
        isTerminal() -> ActionState.Success
        else -> ActionState.Loading
    }
}

private fun String.toActionState(): ActionState {
    if (isBlank()) return ActionState.Idle
    val text = lowercase()
    val errorKeywords = listOf("失败", "错误", "不可用", "fail", "error")
    val successKeywords = listOf("成功", "已加载", "已生成", "已登录", "已连接", "success")
    return when {
        errorKeywords.any { it in text } -> ActionState.Error
        successKeywords.any { it in text } -> ActionState.Success
        text.startsWith("正在") -> ActionState.Loading
        else -> ActionState.Idle
    }
}

private fun String.toQrBitmap(size: Int = 720): Bitmap? {
    if (!isRealUrl()) return null
    return try {
        LocalQrCode.render(this, size)
    } catch (_: Exception) {
        null
    }
}

private fun String.cleanUrl(): String {
    return trim().trimEnd('。', '，', ',', ';', '；', ')', '）')
}

private fun String.runIdFromPath(): String {
    if (isBlank()) return ""
    return trim()
        .replace('\\', '/')
        .trimEnd('/')
        .substringAfterLast('/')
}

private fun String.configFilename(): String {
    val normalized = trim().replace('\\', '/').trimEnd('/')
    return normalized.substringAfterLast('/').ifBlank { normalized }
}

private fun ConfigFile.taskConfigName(): String {
    return filename.ifBlank { path.configFilename() }
}

private fun String.isRealUrl(): Boolean {
    val normalized = trim()
    return normalized.startsWith("http://") || normalized.startsWith("https://")
}

private fun Long.formatFen(): String {
    if (this <= 0L) return ""
    return "¥%.2f".format(this / 100.0)
}
