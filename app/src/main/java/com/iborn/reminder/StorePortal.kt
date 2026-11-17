package com.iborn.reminder

import android.content.Context
import android.net.Uri
import android.widget.VideoView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import android.content.res.Configuration

private const val STORE_BASE_URL = "https://iborn.pythonanywhere.com/api/v1"

private val StoreMint = Color(0xFF7DF9D5)
private val StoreInk = Color(0xFFF4F6FA)
private val StoreMuted = Color(0xFFA4A9B8)
private val StoreBlack = Color(0xFF1A1C23)
private val StoreCard = Color(0xFF2A2D3A)

private val LocalStoreEnglish = staticCompositionLocalOf { true }

private fun displayStoreDate(raw: String, english: Boolean): String {
    if (english) return raw
    val date = runCatching { LocalDate.parse(raw) }.getOrNull() ?: return raw
    val gy = date.year
    val gm = date.monthValue
    val gd = date.dayOfMonth
    val monthDays = intArrayOf(0,31,59,90,120,151,181,212,243,273,304,334)
    val gy2 = if (gm > 2) gy + 1 else gy
    var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + monthDays[gm - 1]
    var jy = -1595 + 33 * (days / 12053)
    days %= 12053
    jy += 4 * (days / 1461)
    days %= 1461
    if (days > 365) { jy += (days - 1) / 365; days = (days - 1) % 365 }
    val jm: Int
    val jd: Int
    if (days < 186) { jm = 1 + days / 31; jd = 1 + days % 31 }
    else { jm = 7 + (days - 186) / 30; jd = 1 + (days - 186) % 30 }
    return "%04d/%02d/%02d".format(jy, jm, jd).map { ch -> if (ch in '0'..'9') ('۰'.code + (ch - '0')).toChar() else ch }.joinToString("")
}

@Composable private fun storeText(value: String): String {
    if (LocalStoreEnglish.current) return value
    val translations = mapOf(
        "STORE WORKSPACE" to "محیط کاری فروشگاه", "Shared workspace" to "فضای کاری مشترک", "Sign in to Store" to "ورود به فروشگاه", "Your shared team workspace" to "فضای مشترک تیم شما",
        "Username" to "نام کاربری", "Password" to "رمز عبور", "Connecting…" to "در حال اتصال…", "Sign in" to "ورود", "Login failed" to "ورود ناموفق بود",
        "Sign in once. This device keeps your session until you log out or access is revoked." to "یک‌بار وارد شوید؛ نشست شما تا زمان خروج یا لغو دسترسی روی این دستگاه باقی می‌ماند.",
        "ROLE" to "نقش", "MANAGER" to "مدیر", "STAFF" to "کارمند", "TASKS" to "وظایف", "DONE" to "انجام‌شده", "Tasks" to "وظایف", "Staff" to "پرسنل", "Shifts" to "شیفت‌ها", "Products" to "محصولات", "My tasks" to "وظایف من", "Schedule" to "برنامه شیفت",
        "Refresh" to "بروزرسانی", "Log out" to "خروج", "Choose date" to "انتخاب تاریخ", "Select" to "انتخاب", "Cancel" to "انصراف",
        "Add staff member" to "افزودن پرسنل", "Create staff account" to "ایجاد حساب پرسنل", "Full name" to "نام و نام خانوادگی", "Temporary password" to "رمز عبور موقت", "Staff members" to "اعضای پرسنل", "Disable" to "غیرفعال‌کردن", "Enable" to "فعال‌کردن", "Create failed" to "ایجاد اطلاعات ناموفق بود", "Update failed" to "بروزرسانی ناموفق بود", "Create failed" to "ایجاد ناموفق بود",
        "Schedule a shift" to "ثبت شیفت", "Shift date" to "تاریخ شیفت", "Morning" to "صبح", "Evening" to "عصر", "Select staff" to "انتخاب پرسنل", "Save schedule" to "ذخیره برنامه", "Upcoming shifts" to "شیفت‌های آینده", "My schedule & colleagues" to "برنامه و همکاران من", "No staff assigned" to "پرسنلی تعیین نشده است",
        "Create task" to "ایجاد وظیفه", "Task title" to "عنوان وظیفه", "Task date" to "تاریخ وظیفه", "Shared shift task" to "وظیفه مشترک شیفت", "Save task changes" to "ذخیره تغییرات وظیفه", "Assigned tasks are visible only to that employee and managers." to "وظایف اختصاصی فقط برای همان کارمند و مدیران قابل مشاهده است.", "Store tasks" to "وظایف فروشگاه", "Edit" to "ویرایش", "Delete" to "حذف", "Shared" to "مشترک", "Completed by" to "انجام‌شده توسط", "of" to "از", "assigned staff" to "پرسنل تعیین‌شده",
        "Add product / expiry batch" to "افزودن محصول / سری انقضا", "Product name" to "نام محصول", "Category" to "دسته‌بندی", "Barcode (optional)" to "بارکد (اختیاری)", "Expiry date" to "تاریخ انقضا", "Quantity" to "تعداد", "Save product" to "ذخیره محصول", "Barcode is optional. Each product can have several batches with separate expiry dates." to "ثبت بارکد اختیاری است. هر محصول می‌تواند چند سری با تاریخ انقضای جداگانه داشته باشد.", "Add another expiry batch" to "افزودن سری انقضای دیگر", "Choose existing product" to "انتخاب محصول موجود", "Batch expiry date" to "تاریخ انقضای سری", "Batch quantity" to "تعداد این سری", "Add batch" to "افزودن سری", "Expiry inventory" to "موجودی و انقضای محصولات", "Uncategorized" to "بدون دسته‌بندی", "Qty" to "تعداد", "Expiry:" to "انقضا:", "Barcode:" to "بارکد:",
        "Could not load Store data" to "بارگذاری اطلاعات فروشگاه انجام نشد", "unauthorized" to "نشست معتبر نیست؛ دوباره وارد شوید", "forbidden" to "اجازه انجام این عملیات را ندارید", "invalid_credentials" to "نام کاربری یا رمز عبور اشتباه است", "username_name_and_password_required_min_6_password" to "نام کاربری، نام و رمز عبور حداقل ۶ حرفی الزامی است", "username_exists" to "این نام کاربری قبلاً ثبت شده است", "password_min_6" to "رمز عبور باید حداقل ۶ حرف باشد", "not_found" to "مورد درخواستی پیدا نشد", "display_name_required" to "نام نمایشی الزامی است", "invalid_date_range" to "بازه تاریخ نامعتبر است", "invalid_date_shift_or_staff_list" to "تاریخ، نوع شیفت یا فهرست پرسنل نامعتبر است", "invalid_staff_id" to "شناسه پرسنل نامعتبر است", "invalid_task" to "اطلاعات وظیفه نامعتبر است", "invalid_assignee" to "مسئول وظیفه نامعتبر است", "invalid_quantity" to "تعداد باید عددی معتبر و مثبت باشد", "invalid_product_or_batch" to "محصول یا سری انتخاب‌شده معتبر نیست", "invalid_batch" to "سری محصول معتبر نیست", "name_required" to "نام محصول الزامی است", "Save failed" to "ذخیره‌سازی ناموفق بود", "Batch save failed" to "ذخیره سری ناموفق بود", "Request failed" to "درخواست ناموفق بود", "Server returned a non-JSON response" to "سرور پاسخ معتبر ارسال نکرد", "Server returned an empty response" to "پاسخ سرور خالی است"
    )
    if (translations.containsKey(value)) return translations.getValue(value)
    if (value == "Create failed") return "ایجاد اطلاعات ناموفق بود"
    if (value == "Update failed") return "بروزرسانی ناموفق بود"
    if (value == "Save failed") return "ذخیره‌سازی ناموفق بود"
    if (value == "Batch save failed") return "ذخیره سری ناموفق بود"
    if (value == "Edit failed") return "ویرایش ناموفق بود"
    if (value.startsWith("Server returned a non-JSON response")) return "پاسخ سرور معتبر نیست؛ کد خطا: " + value.substringAfter("HTTP ", "")
    if (value.startsWith("Server returned an empty response")) return "پاسخ سرور خالی است؛ گزارش‌های API را بررسی کنید."
    if (value.startsWith("Request failed")) return "درخواست ناموفق بود؛ " + value.substringAfter("Request failed", "")
    if (value.startsWith("Completed by ")) return value.replace("Completed by ", "انجام‌شده توسط ").replace(" of ", " از ").replace(" assigned staff", " نفر پرسنل")
    return value
}

data class StoreProfile(val id: Int, val username: String, val displayName: String, val role: String)
data class StoreTask(val id: Int, val title: String, val shift: String, val date: String, val assignedName: String?, val done: Boolean, val doneCount: Int = 0, val memberCount: Int = 0)
data class StoreStaff(val id: Int, val username: String, val name: String, val active: Boolean)
data class StoreShift(val id: Int, val date: String, val type: String, val start: String, val end: String, val members: List<String>)
data class StoreProduct(val id: Int, val batchId: Int, val name: String, val category: String, val barcode: String, val expiry: String, val quantity: Int)
data class StoreOverview(val taskCount: Int = 0, val doneCount: Int = 0, val shiftCount: Int = 0, val batchCount: Int = 0, val expiringCount: Int = 0, val role: String = "")

class StoreApiClient(context: Context) {
    private val prefs = context.getSharedPreferences("reminder_store_auth", Context.MODE_PRIVATE)
    var token: String
        get() = prefs.getString("token", "") ?: ""
        private set(value) { prefs.edit().putString("token", value).apply() }

    private suspend fun call(method: String, path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL(STORE_BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val result = try {
                if (raw.isBlank()) JSONObject() else JSONObject(raw)
            } catch (_: Exception) {
                JSONObject().put("error", "Server returned a non-JSON response (HTTP $code)")
            }
            if (code !in 200..299) {
                val detail = result.optString("error").ifBlank { "Request failed (HTTP $code)" }
                throw IllegalStateException(detail)
            }
            if (raw.isBlank()) throw IllegalStateException("Server returned an empty response (HTTP $code). Check the PythonAnywhere API logs.")
            result
        } finally { connection.disconnect() }
    }

    suspend fun login(username: String, password: String): StoreProfile {
        val result = call("POST", "/auth/login", JSONObject().put("username", username).put("password", password))
        token = result.getString("token")
        return result.getJSONObject("profile").toProfile()
    }
    suspend fun profile(): StoreProfile = call("GET", "/me").getJSONObject("profile").toProfile()
    private fun JSONObject.toProfile() = StoreProfile(getInt("id"), getString("username"), getString("display_name"), getString("role"))

    suspend fun tasks(): List<StoreTask> {
        val from = LocalDate.now().toString(); val to = LocalDate.now().plusMonths(12).toString()
        val a = call("GET", "/tasks?from=$from&to=$to").optJSONArray("tasks") ?: JSONArray()
        return (0 until a.length()).map { i -> a.getJSONObject(i).let { x ->
            StoreTask(x.getInt("id"), x.getString("title"), x.getString("shift_type"), x.getString("task_date"),
                x.optString("assigned_name").takeIf { it != "null" && it.isNotBlank() }, x.optInt("done") == 1,
                x.optInt("done_count"), x.optInt("member_count"))
        } }
    }
    suspend fun complete(id: Int, done: Boolean) { call("POST", "/tasks/$id/complete", JSONObject().put("done", done)) }
    suspend fun staff(): List<StoreStaff> {
        val a = call("GET", "/staff").optJSONArray("staff") ?: JSONArray()
        return (0 until a.length()).map { a.getJSONObject(it).let { x -> StoreStaff(x.getInt("id"), x.getString("username"), x.getString("display_name"), x.optInt("active") == 1) } }
    }
    suspend fun createStaff(username: String, name: String, password: String) {
        call("POST", "/staff", JSONObject().put("username", username).put("display_name", name).put("password", password))
    }
    suspend fun setStaffActive(id: Int, active: Boolean) {
        call("PATCH", "/staff/$id", JSONObject().put("active", active))
    }
    suspend fun shifts(): List<StoreShift> {
        val from = LocalDate.now().toString(); val to = LocalDate.now().plusMonths(12).toString()
        val a = call("GET", "/shifts?from=$from&to=$to").optJSONArray("shifts") ?: JSONArray()
        return (0 until a.length()).map { a.getJSONObject(it).let { x ->
            val members = x.optJSONArray("members") ?: JSONArray()
            StoreShift(x.getInt("id"), x.getString("shift_date"), x.getString("shift_type"), x.optString("start_time"), x.optString("end_time"),
                (0 until members.length()).map { members.getJSONObject(it).getString("display_name") })
        } }
    }
    suspend fun saveShift(date: String, type: String, ids: Set<Int>) {
        val a = JSONArray(); ids.forEach { a.put(it) }
        call("PUT", "/shifts", JSONObject().put("date", date).put("shift_type", type).put("staff_ids", a))
    }
    suspend fun createTask(title: String, date: String, type: String, assignedId: Int?) {
        val b = JSONObject().put("title", title).put("date", date).put("shift_type", type)
        if (assignedId != null) b.put("assigned_user_id", assignedId)
        call("POST", "/tasks", b)
    }
    suspend fun updateTask(id: Int, title: String, date: String, type: String, assignedId: Int?) {
        val b = JSONObject().put("title", title).put("date", date).put("shift_type", type)
        if (assignedId != null) b.put("assigned_user_id", assignedId) else b.put("assigned_user_id", JSONObject.NULL)
        call("PATCH", "/tasks/$id", b)
    }
    suspend fun deleteTask(id: Int) { call("DELETE", "/tasks/$id") }
    suspend fun products(): List<StoreProduct> {
        val a = call("GET", "/products").optJSONArray("products") ?: JSONArray()
        return (0 until a.length()).map { a.getJSONObject(it).let { x -> StoreProduct(x.getInt("id"), x.optInt("batch_id"), x.getString("name"), x.optString("category"), x.optString("barcode"), x.optString("expiry_date"), x.optInt("quantity")) } }
    }
    suspend fun createProduct(name: String, category: String, barcode: String, expiry: String, quantity: Int) {
        call("POST", "/products", JSONObject().put("name", name).put("category", category).put("barcode", barcode).put("expiry_date", expiry).put("quantity", quantity))
    }
    suspend fun createBatch(productId: Int, expiry: String, quantity: Int) {
        call("POST", "/products/$productId/batches", JSONObject().put("expiry_date", expiry).put("quantity", quantity))
    }
    suspend fun logout() { if (token.isNotBlank()) runCatching { call("POST", "/auth/logout", JSONObject()) }; token = "" }
}

@Composable
fun StorePortal(context: Context, english: Boolean = true, onOverview: (StoreOverview) -> Unit = {}) {
    val api = remember { StoreApiClient(context) }
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf<StoreProfile?>(null) }
    var tasks by remember { mutableStateOf<List<StoreTask>>(emptyList()) }
    var staff by remember { mutableStateOf<List<StoreStaff>>(emptyList()) }
    var shifts by remember { mutableStateOf<List<StoreShift>>(emptyList()) }
    var products by remember { mutableStateOf<List<StoreProduct>>(emptyList()) }
    var page by remember { mutableStateOf("Tasks") }
    var error by remember { mutableStateOf("") }; var loading by remember { mutableStateOf(false) }
    var introVisible by remember { mutableStateOf(false) }; var dataReady by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            loading = true; dataReady = false; error = ""
            try {
                coroutineScope {
                    val taskRequest = async { api.tasks() }
                    val shiftRequest = async { api.shifts() }
                    val manager = profile?.role == "manager"
                    val staffRequest = if (manager) async { api.staff() } else null
                    val productRequest = if (manager) async { api.products() } else null
                    tasks = taskRequest.await()
                    shifts = shiftRequest.await()
                    if (manager) {
                        staff = staffRequest!!.await()
                        products = productRequest!!.await()
                    }
                    val soon = if (manager) products.count { product ->
                        runCatching { val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(product.expiry)); days in 0..7 }.getOrDefault(false)
                    } else 0
                    onOverview(StoreOverview(tasks.size, tasks.count { it.done }, shifts.size, products.size, soon, profile?.role.orEmpty()))
                }
            } catch (e: Exception) { error = e.message ?: "Could not load Store data" }
            finally { loading = false; dataReady = true }
        }
    }
    LaunchedEffect(Unit) {
        if (api.token.isNotBlank()) {
            loading = true
            try { dataReady = false; profile = api.profile(); introVisible = true } catch (_: Exception) { api.logout(); dataReady = true }
            loading = false
        }
    }
    LaunchedEffect(profile?.id) { if (profile != null) refresh() }
    LaunchedEffect(introVisible) {
        if (introVisible) {
            kotlinx.coroutines.delay(2200)
            while (!dataReady) kotlinx.coroutines.delay(100)
            introVisible = false
        }
    }

    if (introVisible) { StoreLoadingVideo(context); return }

    CompositionLocalProvider(LocalStoreEnglish provides english) {
    Column(Modifier.fillMaxSize().background(StoreBlack).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(storeText("STORE WORKSPACE"), color = StoreMint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                Text(if (profile == null) storeText("Shared workspace") else profile!!.displayName, color = StoreInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Rounded.Storefront, null, tint = StoreMint, modifier = Modifier.size(30.dp))
        }
        if (profile == null) {
            Card(colors = CardDefaults.cardColors(containerColor = StoreCard), shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(storeText("Sign in to Store"), color = StoreInk, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text(storeText("Your shared team workspace"), color = StoreMuted, fontSize = 12.sp)
                    StoreField(username, { username = it }, "Username")
                    StoreField(password, { password = it }, "Password", true)
                    Button(onClick = { scope.launch { loading = true; error = ""; try { dataReady = false; profile = api.login(username.trim(), password); introVisible = true; password = "" } catch (e: Exception) { introVisible = false; dataReady = true; error = e.message ?: "Login failed" }; loading = false } }, enabled = !loading && username.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = StoreMint, contentColor = StoreBlack)) { Text(storeText(if (loading) "Connecting…" else "Sign in"), fontWeight = FontWeight.Bold) }
                    if (error.isNotBlank()) Text(storeText(error), color = Color(0xFFFF8A80), fontSize = 12.sp)
                    Text(storeText("Sign in once. This device keeps your session until you log out or access is revoked."), color = StoreMuted, fontSize = 12.sp)
                }
            }
        } else {
            val manager = profile!!.role == "manager"
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("ROLE", if (manager) "MANAGER" else "STAFF", Modifier.weight(1f))
                Metric("TASKS", tasks.size.toString(), Modifier.weight(1f))
                Metric("DONE", tasks.count { it.done }.toString(), Modifier.weight(1f))
            }
            val tabs = if (manager) listOf("Tasks", "Staff", "Shifts", "Products") else listOf("My tasks", "Schedule")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                tabs.forEach { tab -> FilterChip(selected = page == tab, onClick = { page = tab }, label = { Text(storeText(tab), fontSize = 11.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = StoreMint, selectedLabelColor = StoreBlack)) }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = StoreMint)
            when (page) {
                "Staff" -> ManagerStaff(staff, onCreate = { u, n, p -> scope.launch { runCatching { api.createStaff(u,n,p); staff=api.staff() }.onFailure { error=it.message ?: "Create failed" } } }, onToggle = { id, active -> scope.launch { runCatching { api.setStaffActive(id,active); staff=api.staff() }.onFailure { error=it.message ?: "Update failed" } } })
                "Shifts", "Schedule" -> ShiftPanel(manager, staff, shifts, { d,t,ids -> scope.launch { runCatching { api.saveShift(d,t,ids); shifts=api.shifts() }.onFailure { error=it.message ?: "Save failed" } } })
                "Products" -> ProductPanel(products, onCreate = { n,c,b,e,q -> scope.launch { runCatching { api.createProduct(n,c,b,e,q); products=api.products() }.onFailure { error=it.message ?: "Save failed" } } }, onAddBatch = { id,e,q -> scope.launch { runCatching { api.createBatch(id,e,q); products=api.products() }.onFailure { error=it.message ?: "Batch save failed" } } })
                else -> TaskPanel(manager, tasks, staff, onComplete = { id, done -> scope.launch { runCatching { api.complete(id,done); tasks=api.tasks() }.onFailure { error=it.message ?: "Update failed" } } }, onCreate = { title,date,type,assignee -> scope.launch { runCatching { api.createTask(title,date,type,assignee); tasks=api.tasks() }.onFailure { error=it.message ?: "Create failed" } } }, onUpdate = { id,title,date,type,assignee -> scope.launch { runCatching { api.updateTask(id,title,date,type,assignee); tasks=api.tasks() }.onFailure { error=it.message ?: "Edit failed" } } }, onDelete = { id -> scope.launch { runCatching { api.deleteTask(id); tasks=api.tasks() }.onFailure { error=it.message ?: "Delete failed" } } })
            }
            if (error.isNotBlank()) Text(storeText(error), color = Color(0xFFFF8A80), fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { refresh() }) { Text(storeText("Refresh"), color = StoreMint) }
                TextButton(onClick = { scope.launch { api.logout(); profile=null; tasks=emptyList(); staff=emptyList(); shifts=emptyList(); products=emptyList(); page="Tasks"; onOverview(StoreOverview()) } }) { Text(storeText("Log out"), color = StoreMuted) }
            }
        }
    }
    }
}

@Composable private fun StoreLoadingVideo(context: Context) {
    Box(Modifier.fillMaxSize().background(StoreBlack), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                VideoView(viewContext).apply {
                    setVideoURI(Uri.parse("android.resource://${viewContext.packageName}/${R.raw.loading_intro}"))
                    setOnPreparedListener { player -> player.isLooping = true; start() }
                }
            },
            update = { if (!it.isPlaying) it.start() },
            onRelease = { it.stopPlayback() }
        )
    }
}

@Composable private fun StoreField(value: String, onChange: (String)->Unit, label: String, secret: Boolean = false) {
    OutlinedTextField(
        value, onChange, label = { Text(storeText(label)) }, singleLine = true,
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp),
        visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = StoreMint, unfocusedBorderColor = Color(0xFF454B5B),
            focusedLabelColor = StoreMint, cursorColor = StoreMint,
            focusedTextColor = StoreInk, unfocusedTextColor = StoreInk
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun StoreDatePicker(value: String, onChange: (String) -> Unit, label: String) {
    var dialogOpen by remember { mutableStateOf(false) }
    val baseConfiguration = LocalConfiguration.current
    val pickerConfiguration = remember(baseConfiguration, LocalStoreEnglish.current) { Configuration(baseConfiguration).apply { setLocale(if (LocalStoreEnglish.current) Locale.ENGLISH else Locale("fa", "IR")) } }
    OutlinedButton(
        onClick = { dialogOpen = true },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = StoreInk),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF454B5B))
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(storeText(label), color = StoreMuted, fontSize = 10.sp)
                Text(displayStoreDate(value, LocalStoreEnglish.current), color = StoreInk, fontSize = 14.sp)
            }
            Text(storeText("Choose date"), color = StoreMint, fontSize = 11.sp)
        }
    }

    if (dialogOpen) {
        val currentDate = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = currentDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { dialogOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    dialogOpen = false
                }) { Text(storeText("Select"), color = StoreMint) }
            },
            dismissButton = {
                TextButton(onClick = { dialogOpen = false }) { Text(storeText("Cancel"), color = StoreMuted) }
            }
        ) {
            CompositionLocalProvider(LocalConfiguration provides pickerConfiguration) { DatePicker(state = pickerState, showModeToggle = false) }
        }
    }
}

@Composable private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors=CardDefaults.cardColors(containerColor=StoreCard), shape=RoundedCornerShape(20.dp), elevation=CardDefaults.cardElevation(defaultElevation = 1.dp)) { Column(Modifier.padding(11.dp)) { Text(storeText(label),color=StoreMuted,fontSize=9.sp); Text(storeText(value),color=StoreMint,fontSize=15.sp,fontWeight=FontWeight.Bold) } }
}

@Composable private fun PanelCard(content: @Composable ColumnScope.()->Unit) {
    Card(colors=CardDefaults.cardColors(containerColor=StoreCard), shape=RoundedCornerShape(23.dp), modifier=Modifier.fillMaxWidth(), elevation=CardDefaults.cardElevation(defaultElevation = 1.dp)) { Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(9.dp), content=content) }
}

@Composable private fun ManagerStaff(staff: List<StoreStaff>, onCreate: (String,String,String)->Unit, onToggle: (Int,Boolean)->Unit) {
    var username by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { PanelCard {
            Text(storeText("Add staff member"), color=StoreInk, fontWeight=FontWeight.Bold)
            StoreField(username,{username=it},"Username"); StoreField(name,{name=it},"Full name"); StoreField(password,{password=it},"Temporary password",true)
            Button(onClick={ onCreate(username.trim(),name.trim(),password); username="";name="";password="" }, enabled=username.isNotBlank()&&name.isNotBlank()&&password.length>=6, colors=ButtonDefaults.buttonColors(containerColor=StoreMint,contentColor=StoreBlack)) { Text(storeText("Create staff account")) }
        } }
        item { Text(storeText("Staff members"), color=StoreInk, fontWeight=FontWeight.Bold) }
        items(staff) { s -> PanelCard { Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(s.name,color=StoreInk,fontWeight=FontWeight.Medium); Text(storeText("@${s.username}"),color=StoreMuted,fontSize=11.sp) }; TextButton(onClick={onToggle(s.id,!s.active)}) { Text(storeText(if(s.active) "Disable" else "Enable"),color=if(s.active) Color(0xFFFF8A80) else StoreMint,fontSize=11.sp) } } } }
    }
}

@Composable private fun ShiftPanel(manager: Boolean, staff: List<StoreStaff>, shifts: List<StoreShift>, onSave: (String,String,Set<Int>)->Unit) {
    var date by remember { mutableStateOf(LocalDate.now().toString()) }; var type by remember { mutableStateOf("morning") }; var selected by remember { mutableStateOf(setOf<Int>()) }
    LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp)) {
        if(manager) item { PanelCard {
            Text(storeText("Schedule a shift"),color=StoreInk,fontWeight=FontWeight.Bold)
            StoreDatePicker(date,{date=it},"Shift date")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("morning","evening").forEach { FilterChip(selected=type==it,onClick={type=it},label={Text(storeText(if(it=="morning") "Morning" else "Evening"))}) } }
            Text(storeText("Select staff"),color=StoreMuted,fontSize=12.sp)
            staff.filter { it.active }.forEach { s -> Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(checked=s.id in selected,onCheckedChange={selected=if(it) selected+s.id else selected-s.id},colors=CheckboxDefaults.colors(checkedColor=StoreMint)); Text(s.name,color=StoreInk) } }
            Button(onClick={onSave(date,type,selected);selected= emptySet()},enabled=runCatching{LocalDate.parse(date)}.isSuccess,colors=ButtonDefaults.buttonColors(containerColor=StoreMint,contentColor=StoreBlack)){Text(storeText("Save schedule"))}
        } }
        item { Text(storeText(if(manager) "Upcoming shifts" else "My schedule & colleagues"),color=StoreInk,fontWeight=FontWeight.Bold) }
        items(shifts) { s -> PanelCard {
            Text("${displayStoreDate(s.date, LocalStoreEnglish.current)} · ${storeText(if(s.type=="morning") "Morning" else "Evening")}",color=StoreMint,fontWeight=FontWeight.SemiBold)
            Text(if(s.members.isEmpty()) storeText("No staff assigned") else s.members.joinToString(" · "),color=StoreInk,fontSize=13.sp)
        } }
    }
}

@Composable private fun TaskPanel(manager: Boolean, tasks: List<StoreTask>, staff: List<StoreStaff>, onComplete: (Int,Boolean)->Unit, onCreate: (String,String,String,Int?)->Unit, onUpdate: (Int,String,String,String,Int?)->Unit, onDelete: (Int)->Unit) {
    var title by remember { mutableStateOf("") }; var date by remember { mutableStateOf(LocalDate.now().toString()) }; var type by remember { mutableStateOf("morning") }; var assignee by remember { mutableStateOf("Shared shift task") }; var editingId by remember { mutableStateOf<Int?>(null) }
    LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp)) {
        if(manager) item { PanelCard {
            Text(storeText("Create task"),color=StoreInk,fontWeight=FontWeight.Bold)
            StoreField(title,{title=it},"Task title"); StoreDatePicker(date,{date=it},"Task date")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("morning","evening").forEach{FilterChip(selected=type==it,onClick={type=it},label={Text(storeText(if(it=="morning") "Morning" else "Evening"))})}}
            var expanded by remember { mutableStateOf(false) }
            Box { OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){Text(storeText(assignee),color=StoreInk)}; DropdownMenu(expanded,{expanded=false}) {
                DropdownMenuItem(text={Text(storeText("Shared shift task"))},onClick={ assignee="Shared shift task"; expanded=false })
                staff.filter{it.active}.forEach { s -> DropdownMenuItem(text={Text(s.name)},onClick={ assignee="${s.id}:${s.name}"; expanded=false }) }
            } }
            Button(onClick={val id=assignee.substringBefore(":").toIntOrNull(); if(editingId==null) onCreate(title.trim(),date,type,id) else onUpdate(editingId!!,title.trim(),date,type,id); title=""; editingId=null; assignee="Shared shift task"},enabled=title.isNotBlank()&&runCatching{LocalDate.parse(date)}.isSuccess,colors=ButtonDefaults.buttonColors(containerColor=StoreMint,contentColor=StoreBlack)){Text(storeText(if(editingId==null) "Create task" else "Save task changes"))}
            Text(storeText("Assigned tasks are visible only to that employee and managers."),color=StoreMuted,fontSize=10.sp)
        } }
        item { Text(storeText(if(manager) "Store tasks" else "My tasks"),color=StoreInk,fontWeight=FontWeight.Bold) }
        items(tasks) { task -> PanelCard {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(task.title,color=StoreInk,fontWeight=FontWeight.Medium); Text("${displayStoreDate(task.date, LocalStoreEnglish.current)} · ${storeText(if(task.shift=="morning") "Morning" else if(task.shift=="evening") "Evening" else task.shift)} · ${task.assignedName?.let { storeText(it) } ?: storeText("Shared")}",color=StoreMuted,fontSize=10.sp) }
                Checkbox(checked=task.done,onCheckedChange={onComplete(task.id,it)},enabled=!manager || task.assignedName!=null,colors=CheckboxDefaults.colors(checkedColor=StoreMint))
                if(manager) {
                    TextButton(onClick={title=task.title;date=task.date;type=task.shift;assignee=staff.firstOrNull { it.name==task.assignedName }?.let { "${it.id}:${it.name}" } ?: "Shared shift task";editingId=task.id}) { Text(storeText("Edit"),color=StoreMint,fontSize=10.sp) }
                    TextButton(onClick={onDelete(task.id)}){Text(storeText("Delete"),color=Color(0xFFFF8A80),fontSize=10.sp)}
                }
            }
            if(manager && task.assignedName==null) Text(if(LocalStoreEnglish.current) "Completed by ${task.doneCount} of ${task.memberCount} assigned staff" else "${task.memberCount} نفر پرسنل؛ ${task.doneCount} نفر انجام داده‌اند",color=StoreMuted,fontSize=10.sp)
        } }
    }
}

@Composable private fun ProductPanel(products: List<StoreProduct>, onCreate: (String,String,String,String,Int)->Unit, onAddBatch: (Int,String,Int)->Unit) {
    var name by remember { mutableStateOf("") }; var category by remember { mutableStateOf("") }; var barcode by remember { mutableStateOf("") }; var expiry by remember { mutableStateOf(LocalDate.now().plusMonths(1).toString()) }; var quantity by remember { mutableStateOf("1") }
    var batchProductId by remember { mutableStateOf<Int?>(null) }; var batchExpanded by remember { mutableStateOf(false) }; var batchExpiry by remember { mutableStateOf(LocalDate.now().plusMonths(2).toString()) }; var batchQuantity by remember { mutableStateOf("1") }
    LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp)) {
        item { PanelCard {
            Text(storeText("Add product / expiry batch"),color=StoreInk,fontWeight=FontWeight.Bold)
            StoreField(name,{name=it},"Product name"); StoreField(category,{category=it},"Category"); StoreField(barcode,{barcode=it},"Barcode (optional)"); StoreDatePicker(expiry,{expiry=it},"Expiry date"); StoreField(quantity,{quantity=it.filter(Char::isDigit)},"Quantity")
            Button(onClick={onCreate(name.trim(),category.trim(),barcode.trim(),expiry,quantity.toIntOrNull()?:0);name="";barcode=""},enabled=name.isNotBlank()&&runCatching{LocalDate.parse(expiry)}.isSuccess&&quantity.toIntOrNull()!=null,colors=ButtonDefaults.buttonColors(containerColor=StoreMint,contentColor=StoreBlack)){Text(storeText("Save product"))}
            Text(storeText("Barcode is optional. Each product can have several batches with separate expiry dates."),color=StoreMuted,fontSize=10.sp)
        } }
        item { PanelCard {
            Text(storeText("Add another expiry batch"),color=StoreInk,fontWeight=FontWeight.Bold)
            Box {
                OutlinedButton(onClick={batchExpanded=true},modifier=Modifier.fillMaxWidth(),enabled=products.isNotEmpty()){Text(products.firstOrNull { it.id==batchProductId }?.name ?: storeText("Choose existing product"),color=StoreInk)}
                DropdownMenu(expanded=batchExpanded,onDismissRequest={batchExpanded=false}) { products.distinctBy { it.id }.forEach { product -> DropdownMenuItem(text={Text(product.name)},onClick={batchProductId=product.id;batchExpanded=false}) } }
            }
            StoreDatePicker(batchExpiry,{batchExpiry=it},"Batch expiry date")
            StoreField(batchQuantity,{batchQuantity=it.filter(Char::isDigit)},"Batch quantity")
            Button(onClick={onAddBatch(batchProductId!!,batchExpiry,batchQuantity.toIntOrNull()?:0);batchProductId=null},enabled=batchProductId!=null&&runCatching{LocalDate.parse(batchExpiry)}.isSuccess&&batchQuantity.toIntOrNull()!=null,colors=ButtonDefaults.buttonColors(containerColor=StoreMint,contentColor=StoreBlack)){Text(storeText("Add batch")) }
        } }
        item { Text(storeText("Expiry inventory"),color=StoreInk,fontWeight=FontWeight.Bold) }
        items(products) { p -> PanelCard { Text(p.name,color=StoreInk,fontWeight=FontWeight.SemiBold); Text("${p.category.ifBlank { storeText("Uncategorized") }} · ${storeText("Qty")} ${p.quantity}",color=StoreMuted,fontSize=11.sp); Text("${storeText("Expiry:")} ${displayStoreDate(p.expiry, LocalStoreEnglish.current)}",color=StoreMint,fontSize=12.sp); if(p.barcode.isNotBlank()) Text("${storeText("Barcode:")} ${p.barcode}",color=StoreMuted,fontSize=10.sp) } }
    }
}
