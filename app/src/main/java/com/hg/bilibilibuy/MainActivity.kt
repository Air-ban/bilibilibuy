package com.hg.bilibilibuy

import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hg.bilibilibuy.ui.theme.BilibilibuyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DEFAULT_SERVER_URL = "http://10.0.2.2:8000"
private const val PREFS_NAME = "bili_buy_settings"
private const val KEY_SERVER_URL = "server_url"

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
    Files("配置文件", "文件", "F"),
    Task("启动抢票", "抢票", "T"),
    Result("生成结果", "结果", "R")
}

@Composable
fun BiliBuyApp() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf(AppTab.Settings) }
    var serverUrl by remember {
        mutableStateOf(prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL)
    }
    var healthMessage by remember { mutableStateOf("未测试连接") }
    var authMessage by remember { mutableStateOf("未检查登录状态") }
    var qrLogin by remember { mutableStateOf<QrLogin?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var isPolling by remember { mutableStateOf(false) }

    var projectInput by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf("") }
    var purchaseContext by remember { mutableStateOf<PurchaseContext?>(null) }
    var selectedTicketIndex by remember { mutableIntStateOf(-1) }
    val selectedBuyerIndices = remember { mutableStateListOf<Int>() }
    var selectedAddressIndex by remember { mutableIntStateOf(-1) }
    var contactName by remember { mutableStateOf("") }
    var contactTel by remember { mutableStateOf("") }
    var configResult by remember { mutableStateOf<ConfigGenerateResult?>(null) }
    var projectMessage by remember { mutableStateOf("") }
    var configFiles by remember { mutableStateOf<List<ConfigFile>>(emptyList()) }
    var configBindings by remember { mutableStateOf<List<ConfigBinding>>(emptyList()) }
    var filesMessage by remember { mutableStateOf("未加载配置文件") }
    var bindingUsernameFilter by remember { mutableStateOf("") }
    var selectedTaskConfig by remember { mutableStateOf("") }
    var taskTimeStart by remember { mutableStateOf("") }
    var taskInterval by remember { mutableStateOf("1000") }
    var taskProxy by remember { mutableStateOf("") }
    var taskRunIdInput by remember { mutableStateOf("") }
    var taskMessage by remember { mutableStateOf("请选择已有配置文件启动托管任务") }
    var currentManagedRunId by remember { mutableStateOf("") }
    var managedTaskStatus by remember { mutableStateOf<ManagedTaskStatus?>(null) }

    fun api() = BiliApiClient(serverUrl)

    fun saveServerUrl() {
        prefs.edit().putString(KEY_SERVER_URL, serverUrl.trim()).apply()
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
        configResult = null
    }

    fun runHealthCheck() {
        saveServerUrl()
        scope.launch {
            isBusy = true
            val health = api().health()
            healthMessage = if (health.ok) {
                "连接成功，版本 ${health.version.ifBlank { "未知" }}"
            } else {
                "连接失败：${health.message}"
            }
            isBusy = false
        }
    }

    fun checkAuthStatus() {
        saveServerUrl()
        scope.launch {
            isBusy = true
            authMessage = "正在检查登录状态..."
            try {
                val status = api().authStatus()
                authMessage = if (status.loggedIn) {
                    "已登录：${status.username}"
                } else {
                    "未登录，下一步：${status.nextAction.ifBlank { "扫码登录" }}"
                }
            } catch (error: Exception) {
                authMessage = "检查失败：${error.message ?: "服务器不可用"}"
            }
            isBusy = false
        }
    }

    fun generateQrLogin() {
        saveServerUrl()
        scope.launch {
            isBusy = true
            authMessage = "正在生成二维码..."
            try {
                val qr = api().generateQrLogin()
                qrLogin = qr
                qrBitmap = qr.qrImageBase64.decodeBitmap()
                authMessage = if (qr.ok) {
                    "二维码已生成，可扫码或打开 URL 登录"
                } else {
                    "二维码生成失败：${qr.error}"
                }
            } catch (error: Exception) {
                authMessage = "二维码生成失败：${error.message ?: "服务器不可用"}"
            }
            isBusy = false
        }
    }

    fun startQrPolling() {
        val qr = qrLogin ?: return
        scope.launch {
            isPolling = true
            authMessage = "正在等待扫码确认..."
            repeat(40) {
                if (!isPolling) return@repeat
                try {
                    val result = api().pollQrLogin(qr.qrcodeKey)
                    authMessage = when {
                        result.ok -> "登录成功：${result.username}，cookies ${result.cookiesCount} 条"
                        result.status.isNotBlank() -> "${result.status}：${result.message}"
                        else -> result.message.ifBlank { "等待确认..." }
                    }
                    if (result.ok) {
                        isPolling = false
                        qrLogin = null
                        qrBitmap = null
                        authMessage = "已成功登录：${result.username}，cookies ${result.cookiesCount} 条"
                        return@launch
                    }
                } catch (error: Exception) {
                    authMessage = "轮询失败：${error.message ?: "服务器不可用"}"
                }
                delay(1_500)
            }
            isPolling = false
        }
    }

    fun loadPurchaseContext() {
        saveServerUrl()
        scope.launch {
            isBusy = true
            projectMessage = "正在获取项目上下文..."
            try {
                val data = api().purchaseContext(
                    projectInput.trim(),
                    selectedDate.takeIf { it.isNotBlank() },
                    phone.trim()
                )
                purchaseContext = data
                resetSelections(data)
                projectMessage = "已加载：${data.projectName}，票档 ${data.tickets.size} 个"
            } catch (error: Exception) {
                projectMessage = "加载失败：${error.message ?: "服务器不可用"}"
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
                configResult = result
                projectMessage = if (result.ok) {
                    "配置生成成功"
                } else {
                    "配置生成失败：${result.error}"
                }
                currentTab = AppTab.Result
            } catch (error: Exception) {
                projectMessage = "生成失败：${error.message ?: "服务器不可用"}"
            }
            isBusy = false
        }
    }

    fun loadConfigFilesAndBindings() {
        saveServerUrl()
        scope.launch {
            isBusy = true
            filesMessage = "正在加载配置文件和绑定关系..."
            try {
                val files = api().configList()
                val bindings = api().configBindings(bindingUsernameFilter.takeIf { it.isNotBlank() })
                configFiles = files
                configBindings = bindings
                filesMessage = "已加载：配置 ${files.size} 个，绑定 ${bindings.size} 条"
            } catch (error: Exception) {
                filesMessage = "加载失败：${error.message ?: "服务器不可用"}"
            }
            isBusy = false
        }
    }

    fun loadConfigFilesForTask() {
        saveServerUrl()
        scope.launch {
            isBusy = true
            taskMessage = "正在加载配置文件..."
            try {
                val files = api().configList()
                configFiles = files
                if (selectedTaskConfig.isBlank()) {
                    selectedTaskConfig = files.firstOrNull()?.let { file ->
                        file.path.ifBlank { file.filename }
                    }.orEmpty()
                }
                taskMessage = "已加载 ${files.size} 个配置文件"
            } catch (error: Exception) {
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
            isBusy = true
            taskMessage = "正在启动托管抢票任务..."
            try {
                val result = api().startManagedTask(
                    configFile = selectedTaskConfig.trim(),
                    timeStart = taskTimeStart.trim(),
                    interval = normalizedInterval,
                    httpsProxys = taskProxy.trim(),
                    runId = taskRunIdInput.takeIf { it.isNotBlank() }
                )
                currentManagedRunId = result.runId
                managedTaskStatus = ManagedTaskStatus(
                    ok = result.ok,
                    runId = result.runId,
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
                    "启动成功：${result.runId}"
                } else {
                    "启动失败：${result.error}"
                }
            } catch (error: Exception) {
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
            taskMessage = "正在查询任务状态..."
            try {
                val status = api().managedTaskStatus(runId.trim())
                currentManagedRunId = status.runId.ifBlank { runId.trim() }
                managedTaskStatus = status
                taskMessage = "当前状态：${status.status.ifBlank { "未知" }}"
            } catch (error: Exception) {
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
            isBusy = true
            taskMessage = "正在取消任务..."
            try {
                val status = api().cancelManagedTask(runId.trim())
                managedTaskStatus = status
                taskMessage = "取消请求已发送：${status.runId.ifBlank { runId }}"
            } catch (error: Exception) {
                taskMessage = "取消失败：${error.message ?: "服务器不可用"}"
            }
            isBusy = false
        }
    }

    LaunchedEffect(Unit) {
        checkAuthStatus()
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
                    serverUrl = serverUrl,
                    onServerUrlChange = { serverUrl = it },
                    healthMessage = healthMessage,
                    isBusy = isBusy,
                    onTest = ::runHealthCheck
                )

                AppTab.Login -> LoginScreen(
                    authMessage = authMessage,
                    qrBitmap = qrBitmap,
                    qrLogin = qrLogin,
                    isBusy = isBusy,
                    isPolling = isPolling,
                    onCheckStatus = ::checkAuthStatus,
                    onGenerateQr = ::generateQrLogin,
                    onOpenLoginUrl = { url ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    onStartPolling = ::startQrPolling,
                    onStopPolling = { isPolling = false }
                )

                AppTab.Config -> ConfigScreen(
                    projectInput = projectInput,
                    onProjectInputChange = { projectInput = it },
                    phone = phone,
                    onPhoneChange = { phone = it },
                    selectedDate = selectedDate,
                    onSelectedDateChange = { selectedDate = it },
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
                    isBusy = isBusy,
                    canGenerate = selectedTicketIndex >= 0 &&
                        selectedBuyerIndices.isNotEmpty() &&
                        selectedAddressIndex >= 0 &&
                        contactName.isNotBlank() &&
                        contactTel.isNotBlank(),
                    onLoadContext = ::loadPurchaseContext,
                    onGenerateConfig = ::generateConfig
                )

                AppTab.Files -> FilesScreen(
                    configFiles = configFiles,
                    configBindings = configBindings,
                    filesMessage = filesMessage,
                    usernameFilter = bindingUsernameFilter,
                    onUsernameFilterChange = { bindingUsernameFilter = it },
                    isBusy = isBusy,
                    onLoad = ::loadConfigFilesAndBindings
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
                    status = managedTaskStatus,
                    isBusy = isBusy,
                    onLoadConfigs = ::loadConfigFilesForTask,
                    onStart = ::startManagedTask,
                    onRefreshStatus = ::refreshManagedTaskStatus,
                    onCancel = ::cancelManagedTask
                )

                AppTab.Result -> ResultScreen(configResult = configResult)
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
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    healthMessage: String,
    isBusy: Boolean,
    onTest: () -> Unit
) {
    ScreenList {
        item {
            SectionCard(title = "API 服务器") {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = onServerUrlChange,
                    label = { Text("服务器地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onTest,
                        enabled = !isBusy && serverUrl.isNotBlank()
                    ) {
                        Text("保存并测试")
                    }
                }
                StatusText(healthMessage)
            }
        }
    }
}

@Composable
private fun LoginScreen(
    authMessage: String,
    qrBitmap: Bitmap?,
    qrLogin: QrLogin?,
    isBusy: Boolean,
    isPolling: Boolean,
    onCheckStatus: () -> Unit,
    onGenerateQr: () -> Unit,
    onOpenLoginUrl: (String) -> Unit,
    onStartPolling: () -> Unit,
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
                StatusText(authMessage)
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
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { onOpenLoginUrl(qr.loginUrl) },
                            enabled = qr.loginUrl.isNotBlank()
                        ) {
                            Text("浏览器登录")
                        }
                        Button(
                            onClick = onStartPolling,
                            enabled = !isPolling && qr.qrcodeKey.isNotBlank()
                        ) {
                            Text("开始轮询")
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
                        "先生成二维码，再使用 B 站客户端扫码，或打开登录 URL 后回到应用轮询。"
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
                    label = { Text("演出 ID 或详情页 URL") },
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
                    text = "日期支持 YYYY-MM-DD 或 YYYY/MM/DD；留空时后端会合并项目场次、每日场次和周边票档。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                StatusText(projectMessage)
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
private fun FilesScreen(
    configFiles: List<ConfigFile>,
    configBindings: List<ConfigBinding>,
    filesMessage: String,
    usernameFilter: String,
    onUsernameFilterChange: (String) -> Unit,
    isBusy: Boolean,
    onLoad: () -> Unit
) {
    ScreenList {
        item {
            SectionCard(title = "配置文件列表") {
                OutlinedTextField(
                    value = usernameFilter,
                    onValueChange = onUsernameFilterChange,
                    label = { Text("绑定账号筛选，可留空") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onLoad,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("刷新配置和绑定")
                }
                StatusText(filesMessage)
                if (configFiles.isNotEmpty()) {
                    OptionDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        configFiles.forEach { file ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(file.filename.ifBlank { "未命名配置" }, fontWeight = FontWeight.Medium)
                                Text(
                                    text = listOf(
                                        file.accountUsername.takeIf { it.isNotBlank() }?.let { "账号 $it" },
                                        file.boundAt.takeIf { it.isNotBlank() }?.let { "绑定 $it" }
                                    ).filterNotNull().joinToString(" · ").ifBlank { file.path },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (file.path.isNotBlank()) {
                                    Text(
                                        text = file.path,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "绑定关系") {
                if (configBindings.isEmpty()) {
                    Text(
                        text = "暂无绑定关系，刷新后查看 API 记录。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    return@SectionCard
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    configBindings.forEach { binding ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "${binding.filename} -> ${binding.username.ifBlank { "未绑定账号" }}",
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = binding.detail.ifBlank {
                                    "project ${binding.projectId} / screen ${binding.screenId} / sku ${binding.skuId}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (binding.boundAt.isNotBlank()) {
                                Text(
                                    text = "绑定时间：${binding.boundAt}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
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
    status: ManagedTaskStatus?,
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
                StatusText(taskMessage)
                if (configFiles.isEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "还没有加载到配置文件。请先到“文件”页刷新，或在“配置”页生成配置文件。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    OptionDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        configFiles.forEach { file ->
                            val configPath = file.path.ifBlank { file.filename }
                            RadioRow(
                                selected = selectedConfig == configPath,
                                onClick = { onSelectedConfigChange(configPath) },
                                title = file.filename.ifBlank { "未命名配置" },
                                subtitle = listOf(
                                    file.accountUsername.takeIf { it.isNotBlank() }?.let { "账号 $it" },
                                    file.boundAt.takeIf { it.isNotBlank() }?.let { "绑定 $it" },
                                    file.path.takeIf { it.isNotBlank() }
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
                    value = selectedConfig,
                    onValueChange = onSelectedConfigChange,
                    label = { Text("配置文件所在路径 *") },
                    singleLine = true,
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
                    text = "* 为必填项。配置列表选择时会传入服务端返回的 path；其它运行参数留空时使用后端默认值。",
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
                Text(
                    text = "${status.runId.ifBlank { runIdInput }} · ${status.status.ifBlank { "未知" }}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val details = listOfNotNull(
                    status.detail.takeIf { it.isNotBlank() },
                    status.pid.takeIf { it > 0 }?.let { "pid $it" },
                    status.lastMessage.takeIf { it.isNotBlank() }
                )
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                if (status.logsPath.isNotBlank() || status.resultPath.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = listOf(status.logsPath, status.resultPath)
                            .filter { it.isNotBlank() }
                            .joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultScreen(configResult: ConfigGenerateResult?) {
    ScreenList {
        item {
            SectionCard(title = "配置文件") {
                val result = configResult
                if (result == null) {
                    Text(
                        text = "还没有生成配置。请到“配置”页完成项目、票档、购票人和地址选择。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    return@SectionCard
                }

                if (result.configPath.isNotBlank()) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = result.configPath,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
                if (result.accountUsername.isNotBlank() || result.boundUsername.isNotBlank()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        if (result.accountUsername.isNotBlank()) {
                            AssistChip(
                                onClick = {},
                                label = { Text("账号 ${result.accountUsername}") }
                            )
                        }
                        if (result.boundUsername.isNotBlank()) {
                            AssistChip(
                                onClick = {},
                                label = { Text("绑定 ${result.boundUsername}") }
                            )
                        }
                    }
                }
                if (result.errors.isNotEmpty()) {
                    StatusText("错误：${result.errors.joinToString("；")}")
                }
                if (result.warnings.isNotEmpty()) {
                    StatusText("警告：${result.warnings.joinToString("；")}")
                }
                if (result.configJson.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            text = result.configJson,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(420.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(8.dp)
                        )
                    }
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

private fun String.isRealUrl(): Boolean {
    val normalized = trim()
    return normalized.startsWith("http://") || normalized.startsWith("https://")
}

private fun Long.formatFen(): String {
    if (this <= 0L) return ""
    return "¥%.2f".format(this / 100.0)
}
