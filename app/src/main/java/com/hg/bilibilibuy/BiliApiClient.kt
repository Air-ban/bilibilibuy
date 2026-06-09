package com.hg.bilibilibuy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

data class AuthStatus(
    val ok: Boolean,
    val loggedIn: Boolean,
    val username: String,
    val hasCookies: Boolean,
    val nextAction: String
)

data class HealthStatus(
    val ok: Boolean,
    val status: String,
    val version: String,
    val message: String
)

data class QrLogin(
    val ok: Boolean,
    val loginUrl: String,
    val qrcodeKey: String,
    val qrImageBase64: String,
    val error: String
)

data class PollResult(
    val ok: Boolean,
    val status: String,
    val message: String,
    val username: String,
    val cookiesCount: Int
)

data class TicketOption(
    val index: Int,
    val id: Long,
    val desc: String,
    val price: Long,
    val screen: String,
    val screenId: Long,
    val display: String,
    val saleStatus: String,
    val saleStart: String,
    val isHotProject: Boolean,
    val linkId: String
)

data class BuyerOption(
    val index: Int,
    val name: String,
    val personalId: String
)

data class AddressOption(
    val index: Int,
    val id: Long,
    val name: String,
    val phone: String,
    val address: String
)

data class PurchaseContext(
    val projectId: Long,
    val projectName: String,
    val projectUrl: String,
    val projectStartTime: String,
    val projectEndTime: String,
    val username: String,
    val isHotProject: Boolean,
    val hasEticket: Boolean,
    val salesDates: List<String>,
    val selectedDate: String,
    val venueName: String,
    val venueAddress: String,
    val tickets: List<TicketOption>,
    val buyers: List<BuyerOption>,
    val addresses: List<AddressOption>,
    val rawJson: JSONObject
)

data class ConfigGenerateResult(
    val ok: Boolean,
    val configPath: String,
    val configJson: String,
    val accountUsername: String,
    val boundUsername: String,
    val errors: List<String>,
    val warnings: List<String>,
    val error: String
)

data class ConfigFile(
    val filename: String,
    val path: String,
    val accountUsername: String,
    val boundAt: String
)

data class ManagedTaskStartResult(
    val ok: Boolean,
    val runId: String,
    val runDir: String,
    val pid: Long,
    val errors: List<String>,
    val warnings: List<String>,
    val error: String
)

data class ManagedTaskStatus(
    val ok: Boolean,
    val runId: String,
    val status: String,
    val detail: String,
    val pid: Long,
    val paymentQrUrl: String,
    val error: String,
    val lastMessage: String,
    val logsPath: String,
    val resultPath: String
)

class BiliApiClient(private val baseUrl: String) {
    suspend fun health(): HealthStatus {
        return try {
            val json = getJson("/api/health")
            HealthStatus(
                ok = json.optString("status") == "ok",
                status = json.optString("status"),
                version = json.optString("version"),
                message = json.toString()
            )
        } catch (error: Exception) {
            HealthStatus(false, "error", "", error.message ?: "连接失败")
        }
    }

    suspend fun authStatus(): AuthStatus {
        val json = getJson("/api/auth/status")
        return AuthStatus(
            ok = json.optBoolean("ok"),
            loggedIn = json.optBoolean("logged_in"),
            username = json.optString("username", "Not login"),
            hasCookies = json.optBoolean("has_cookies"),
            nextAction = json.optString("next_action")
        )
    }

    suspend fun generateQrLogin(): QrLogin {
        val json = postJson("/api/auth/qrcode/generate", JSONObject())
        return QrLogin(
            ok = json.optBoolean("ok"),
            loginUrl = json.optString("login_url"),
            qrcodeKey = json.optString("qrcode_key"),
            qrImageBase64 = json.optString("qr_image_base64"),
            error = json.optString("error")
        )
    }

    suspend fun pollQrLogin(qrcodeKey: String, timeoutSeconds: Int = 3): PollResult {
        val body = JSONObject()
            .put("qrcode_key", qrcodeKey)
            .put("timeout_seconds", timeoutSeconds)
        val json = postJson("/api/auth/qrcode/poll", body)
        return PollResult(
            ok = json.optBoolean("ok"),
            status = json.optString("status"),
            message = json.optString("message"),
            username = json.optString("username"),
            cookiesCount = json.optJSONArray("cookies")?.length() ?: 0
        )
    }

    suspend fun purchaseContext(
        projectInput: String,
        selectedDate: String?,
        phone: String
    ): PurchaseContext {
        val body = JSONObject()
            .put("project_input", projectInput)
            .put("phone", phone)
        if (!selectedDate.isNullOrBlank()) {
            body.put("selected_date", selectedDate)
        }

        val json = postJson("/api/project/purchase-context", body)
        val venue = json.optJSONObject("venue") ?: JSONObject()
        val dates = json.optJSONArray("sales_dates").toStringList()
        val tickets = json.optJSONArray("ticket_options").toTicketOptions()
        val buyers = json.optJSONArray("buyers").toBuyerOptions()
        val addresses = json.optJSONArray("addresses").toAddressOptions()

        return PurchaseContext(
            projectId = json.optLong("project_id"),
            projectName = json.optString("project_name"),
            projectUrl = json.optString("project_url"),
            projectStartTime = json.optString("project_start_time"),
            projectEndTime = json.optString("project_end_time"),
            username = json.optString("username"),
            isHotProject = json.optBoolean("is_hot_project"),
            hasEticket = json.optBoolean("has_eticket"),
            salesDates = dates,
            selectedDate = json.optString("selected_date"),
            venueName = json.optString("venue_name").ifBlank { venue.optString("name") },
            venueAddress = json.optString("venue_address").ifBlank { venue.optString("address") },
            tickets = tickets,
            buyers = buyers,
            addresses = addresses,
            rawJson = json
        )
    }

    suspend fun generateConfig(
        projectInput: String,
        selectedDate: String?,
        ticketIndex: Int,
        buyerIndices: List<Int>,
        addressIndex: Int,
        buyer: String,
        tel: String,
        phone: String,
        purchaseContext: PurchaseContext?
    ): ConfigGenerateResult {
        val body = JSONObject()
            .put("project_input", projectInput)
            .put("ticket_index", ticketIndex)
            .put("buyer_indices", JSONArray(buyerIndices))
            .put("address_index", addressIndex)
            .put("buyer", buyer)
            .put("tel", tel)
            .put("phone", phone)
            .put("save", true)
        if (!selectedDate.isNullOrBlank()) {
            body.put("selected_date", selectedDate)
        }
        if (purchaseContext != null) {
            body.put("purchase_context", purchaseContext.rawJson)
        }

        val json = postJson("/api/config/generate", body)
        val config = json.optJSONObject("config")
        val validation = json.optJSONObject("validation") ?: JSONObject()
        return ConfigGenerateResult(
            ok = json.optBoolean("ok"),
            configPath = json.optString("config_path"),
            configJson = config?.toString(2) ?: "",
            accountUsername = config?.optString("account_username").orEmpty(),
            boundUsername = config?.optString("bound_username").orEmpty(),
            errors = validation.optJSONArray("errors").toStringList(),
            warnings = validation.optJSONArray("warnings").toStringList(),
            error = json.optString("error")
        )
    }

    suspend fun configList(): List<ConfigFile> {
        val json = getJson("/api/config/list")
        val files = json.optJSONArray("files") ?: JSONArray()
        return List(files.length()) { index ->
            val item = files.optJSONObject(index) ?: JSONObject()
            ConfigFile(
                filename = item.optString("filename"),
                path = item.optString("path"),
                accountUsername = item.optString("account_username"),
                boundAt = item.optString("bound_at")
            )
        }
    }

    suspend fun startManagedTask(
        configFile: String,
        timeStart: String,
        interval: Int?,
        httpsProxys: String,
        runId: String?
    ): ManagedTaskStartResult {
        val body = JSONObject()
            .put("config", configFile)
            .put("show_random_message", false)
        if (timeStart.isNotBlank()) {
            body.put("time_start", timeStart)
        }
        if (interval != null) {
            body.put("interval", interval)
        }
        if (httpsProxys.isNotBlank()) {
            body.put("https_proxys", httpsProxys)
        }
        if (!runId.isNullOrBlank()) {
            body.put("run_id", runId)
        }
        val json = postJson("/api/task/start-managed", body)
        val validation = json.optJSONObject("validation") ?: JSONObject()
        val run = json.optJSONObject("run") ?: JSONObject()
        return ManagedTaskStartResult(
            ok = json.optBoolean("ok"),
            runId = json.optString("run_id").ifBlank { run.optString("run_id") },
            runDir = json.optString("run_dir").ifBlank {
                run.optString("run_dir").ifBlank { run.optString("result_path") }
            },
            pid = json.optLong("pid").takeIf { it > 0 } ?: run.optLong("pid"),
            errors = validation.optJSONArray("errors").toStringList(),
            warnings = validation.optJSONArray("warnings").toStringList(),
            error = json.optString("error").ifBlank { run.optString("error") }
        )
    }

    suspend fun managedTaskStatus(runId: String): ManagedTaskStatus {
        val json = getJson("/api/task/managed/${runId.urlEncode()}/status")
        val run = json.optJSONObject("run") ?: JSONObject()
        return ManagedTaskStatus(
            ok = json.optBoolean("ok") && run.optBoolean("ok", true),
            runId = run.optString("run_id"),
            status = run.optString("status"),
            detail = run.optString("detail"),
            pid = run.optLong("pid"),
            paymentQrUrl = run.optString("payment_qr_url"),
            error = json.optString("error").ifBlank { run.optString("error") },
            lastMessage = run.optString("last_message"),
            logsPath = run.optString("logs_path"),
            resultPath = run.optString("result_path")
        )
    }

    suspend fun cancelManagedTask(runId: String): ManagedTaskStatus {
        val json = postJson("/api/task/managed/${runId.urlEncode()}/cancel", JSONObject())
        val run = json.optJSONObject("run") ?: JSONObject()
        return ManagedTaskStatus(
            ok = json.optBoolean("ok"),
            runId = run.optString("run_id", runId),
            status = run.optString("status", "cancelled"),
            detail = run.optString("detail"),
            pid = run.optLong("pid"),
            paymentQrUrl = run.optString("payment_qr_url"),
            error = run.optString("error"),
            lastMessage = if (json.optBoolean("cancelled")) "已取消" else run.optString("last_message"),
            logsPath = run.optString("logs_path"),
            resultPath = run.optString("result_path")
        )
    }

    private suspend fun getJson(path: String): JSONObject = requestJson("GET", path, null)

    private suspend fun postJson(path: String, body: JSONObject): JSONObject =
        requestJson("POST", path, body)

    private suspend fun requestJson(
        method: String,
        path: String,
        body: JSONObject?
    ): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL(buildUrl(path)).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 70_000
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        try {
            if (body != null) {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use {
                    it.write(body.toString())
                }
            }

            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val response = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use {
                it.readText()
            }
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}: $response")
            }
            JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun buildUrl(path: String): String {
        return baseUrl.trim().trimEnd('/') + "/" + path.trimStart('/')
    }
}

private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return List(length()) { index -> optString(index) }.filter { it.isNotBlank() }
}

private fun JSONArray?.toTicketOptions(): List<TicketOption> {
    if (this == null) return emptyList()
    return List(length()) { index ->
        val item = optJSONObject(index) ?: JSONObject()
        TicketOption(
            index = index,
            id = item.optLong("id"),
            desc = item.optString("desc"),
            price = item.optLong("price"),
            screen = item.optString("screen"),
            screenId = item.optLong("screen_id"),
            display = item.optString("display"),
            saleStatus = item.optString("sale_status"),
            saleStart = item.optString("sale_start"),
            isHotProject = item.optBoolean("is_hot_project"),
            linkId = item.optString("link_id")
        )
    }
}

private fun JSONArray?.toBuyerOptions(): List<BuyerOption> {
    if (this == null) return emptyList()
    return List(length()) { index ->
        val item = optJSONObject(index) ?: JSONObject()
        BuyerOption(
            index = index,
            name = item.optString("name"),
            personalId = item.optString("personal_id")
        )
    }
}

private fun JSONArray?.toAddressOptions(): List<AddressOption> {
    if (this == null) return emptyList()
    return List(length()) { index ->
        val item = optJSONObject(index) ?: JSONObject()
        val addressParts = listOf(
            item.optString("prov"),
            item.optString("city"),
            item.optString("area"),
            item.optString("addr")
        ).filter { it.isNotBlank() }
        AddressOption(
            index = index,
            id = item.optLong("id"),
            name = item.optString("name"),
            phone = item.optString("phone"),
            address = addressParts.joinToString("")
        )
    }
}
