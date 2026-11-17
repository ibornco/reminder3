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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

private const val STORE_BASE_URL = "https://iborn.pythonanywhere.com/api/v1"

private val StoreMint = Color(0xFF7DF9D5)
private val StoreInk = Color(0xFFF4F6FA)
private val StoreMuted = Color(0xFFA4A9B8)
private val StoreBlack = Color(0xFF1A1C23)
private val StoreCard = Color(0xFF2A2D3A)

data class StoreProfile(val id: Int, val username: String, val displayName: String, val role: String)
data class StoreTask(val id: Int, val title: String, val shift: String, val date: String, val assignedName: String?, val done: Boolean, val doneCount: Int = 0, val memberCount: Int = 0)
data class StoreStaff(val id: Int, val username: String, val name: String, val active: Boolean)
data class StoreShift(val id: Int, val date: String, val type: String, val start: String, val end: String, val members: List<String>)
data class StoreProduct(val id: Int, val batchId: Int, val name: String, val category: String, val barcode: String, val expiry: String, val quantity: Int)

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
            val raw = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
            val result = JSONObject(raw)
            if (code !in 200..299) throw IllegalStateException(result.optString("error", "Request failed ($code)"))
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
fun StorePortal(context: Context) {
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
                tasks = api.tasks()
                if (profile?.role == "manager") { staff = api.staff(); shifts = api.shifts(); products = api.products() }
                else { shifts = api.shifts() }
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

    Column(Modifier.fillMaxSize().background(StoreBlack).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("STORE WORKSPACE", color = StoreMint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                Text(if (profile == null) "Shared workspace" else "${profile!!.displayName}", color = StoreInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Rounded.Storefront, null, tint = StoreMint, modifier = Modifier.size(30.dp))
        }
        if (profile == null) {
            Card(colors = CardDefaults.cardColors(containerColor = StoreCard), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Sign in to Store", color = StoreInk, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    StoreField(username, { username = it }, "Username")
                    StoreField(password, { password = it }, "Password", true)
                    Button(onClick = { scope.launch { loading = true; error = ""; try { dataReady = false; profile = api.login(username.trim(), password); introVisible = true; password = "" } catch (e: Exception) { introVisible = false; dataReady = true; error = e.message ?: "Login failed" }; loading = false } }, enabled = !loading && username.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = StoreMint, contentColor = StoreBlack)) { Text(if (loading) "Connecting…" else "Sign in", fontWeight = FontWeight.Bold) }
                    if (error.isNotBlank()) Text(error, color = Color(0xFFFF8A80), fontSize = 12.sp)
                    Text("Sign in once. This device keeps your session until you log out or access is revoked.", color = StoreMuted, fontSize = 12.sp)
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
                tabs.forEach { tab -> FilterChip(selected = page == tab, onClick = { page = tab }, label = { Text(tab, fontSize = 11.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = StoreMint, selectedLabelColor = StoreBlack)) }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = StoreMint)
            when (page) {
                "Staff" -> ManagerStaff(staff, onCreate = { u, n, p -> scope.launch { runCatching { api.createStaff(u,n,p); staff=api.staff() }.onFailure { error=it.message ?: "Create failed" } } }, onToggle = { id, active -> scope.launch { runCatching { api.setStaffActive(id,active); staff=api.staff() }.onFailure { error=it.message ?: "Update failed" } } })
                "Shifts", "Schedule" -> ShiftPanel(manager, staff, shifts, { d,t,ids -> scope.launch { runCatching { api.saveShift(d,t,ids); shifts=api.shifts() }.onFailure { error=it.message ?: "Save failed" } } })
                "Products" -> ProductPanel(products, onCreate = { n,c,b,e,q -> scope.launch { runCatching { api.createProduct(n,c,b,e,q); products=api.products() }.onFailure { error=it.message ?: "Save failed" } } }, onAddBatch = { id,e,q -> scope.launch { runCatching { api.createBatch(id,e,q); products=api.products() }.onFailure { error=it.message ?: "Batch save failed" } } })
                else -> TaskPanel(manager, tasks, staff, onComplete = { id, done -> scope.launch { runCatching { api.complete(id,done); tasks=api.tasks() }.onFailure { error=it.message ?: "Update failed" } } }, onCreate = { title,date,type,assignee -> scope.launch { runCatching { api.createTask(title,date,type,assignee); tasks=api.tasks() }.onFailure { error=it.message ?: "Create failed" } } }, onUpdate = { id,title,date,type,assignee -> scope.launch { runCatching { api.updateTask(id,title,date,type,assignee); tasks=api.tasks() }.onFailure { error=it.message ?: "Edit failed" } } }, onDelete = { id -> scope.launch { runCatching { api.deleteTask(id); tasks=api.tasks() }.onFailure { error=it.message ?: "Delete failed" } } })
            }
            if (error.isNotBlank()) Text(error, color = Color(0xFFFF8A80), fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { refresh() }) { Text("Refresh", color = StoreMint) }
                TextButton(onClick = { scope.launch { api.logout(); profile=null; tasks=emptyList(); staff=emptyList(); shifts=emptyList(); products=emptyList(); page="Tasks" } }) { Text("Log out", color = StoreMuted) }
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
    OutlinedTextField(value, onChange, label={ Text(label) }, singleLine=true, modifier=Modifier.fillMaxWidth(), visualTransformation=if(secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)
}

@Composable private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors=CardDefaults.cardColors(containerColor=StoreCard), shape=RoundedCornerShape(17.dp)) { Column(Modifier.padding(11.dp)) { Text(label,color=StoreMuted,fontSize=9.sp); Text(value,color=StoreMint,fontSize=15.sp,fontWeight=FontWeight.Bold) } }
}

@Composable private fun PanelCard(content: @Composable ColumnScope.()->Unit) {
    Card(colors=CardDefaults.cardColors(containerColor=StoreCard), shape=RoundedCornerShape(20.dp), modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(9.dp), content=content) }
}

@Composable private fun ManagerStaff(staff: List<StoreStaff>, onCreate: (String,String,String)->Unit, onToggle: (Int,Boolean)->Unit) {
    var username by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { PanelCard {
            Text("Add staff member", color=StoreInk, fontWeight=FontWeight.Bold)
            StoreField(username,{username=it},"Username"); StoreField(name,{name=it},"Full name"); StoreField(password,{password=it},"Temporary password",true)
            Button(onClick={ onCreate(username.trim(),name.trim(),password); username="";name="";password="" }, enabled=username.isNotBlank()&&name.isNotBlank()&&password.length>=6, colors=ButtonDefaults.buttonColors(containerColor=StoreMint,contentColor=StoreBlack)) { Text("Create staff account") }
        } }
        item { Text("Staff members", color=StoreInk, fontWeight=FontWeight.Bold) }
        items(staff) { s -> PanelCard { Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(s.name,color=StoreInk,fontWeight=FontWeight.Medium); Text("@${s.username}",color=StoreMuted,fontSize=11.sp) }; TextButton(onClick={onToggle(s.id,!s.active)}) { Text(if(s.active) "Disable" else "Enable",color=if(s.active) Color(0xFFFF8A80) else StoreMint,fontSize=11.sp) } } } }
    }
}

@Composable private fun ShiftPanel(manager: Boolean, staff: List<StoreStaff>, shifts: List<StoreShift>, onSave: (String,String,Set<Int>)->Unit) {
    var date by remember { mutableStateOf(LocalDate.now().toString()) }; var type by remember { mutableStateOf("morning") }; var selected by remember { mutableStateOf(setOf<Int>()) }
    LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp)) {
        if(manager) item { PanelCard {
            Text("Schedule a shift",color=StoreInk,fontWeight=FontWeight.Bold)
            StoreField(date,{date=it},"Date (YYYY-MM-DD)")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("morning","evening").forEach { FilterChip(selected=type==it,onClick={type=it},label={Text(if(it=="morning") "Morning" else "Evening")}) } }
            Text("Select staff",color=StoreMuted,fontSize=12.sp)
            staff.filter { it.active }.forEach { s -> Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(checked=s.id in selected,onCheckedChange={selected=if(it) selected+s.id else selected-s.id},colors=CheckboxDefaults.colors(checkedColor=StoreMint)); Text(s.name,color=StoreInk) } }
            Button(onClick={onSave(date,type,selected);selected= emptySet()},enabled=runCatching{LocalDate.parse(date)}.isSuccess,colors=ButtonDefaults.buttonColors(containerColor=StoreMint,contentColor=StoreBlack)){Text("Save schedule")}
        } }
        item { Text(if(manager) "Upcoming shifts" else "My schedule & colleagues",color=StoreInk,fontWeight=FontWeight.Bold) }
        items(shifts) { s -> PanelCard {
            Text("${s.date} · ${if(s.type=="morning") "Morning" else "Evening"}",color=StoreMint,fontWeight=FontWeight.SemiBold)
            Text(if(s.members.isEmpty()) "No staff assigned" else s.members.joinToString(" · "),color=StoreInk,fontSize=13.sp)
        } }
    }
}

@Composable private fun TaskPanel(manager: Boolean, tasks: List<StoreTask>, staff: List<StoreStaff>, onComplete: (Int,Boolean)->Unit, onCreate: (String,String,String,Int?)->Unit, onUpdate: (Int,String,String,String,Int?)->Unit, onDelete: (Int)->Unit) {
    var title by remember { mutableStateOf("") }; var date by remember { mutableStateOf(LocalDate.now().toString()) }; var type by remember { mutableStateOf("morning") }; var assignee by remember { mutableStateOf("Shared shift task") }; var editingId by remember { mutableStateOf<Int?>(null) }
    LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp)) {
        if(manager) item { PanelCard {
            Text("Create task",color=StoreInk,fontWeight=FontWeight.Bold)
            StoreField(title,{title=it},"Task title"); StoreField(date,{date=it},"Date (YYYY-MM-DD)")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("morning","evening").forEach{FilterChip(selected=type==it,onClick={type=it},label={Text(if(it=="morning") "Morning" else "Evening")})}}
            var expanded by remember { mutableStateOf(false) }
            Box { OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){Text(assignee,color=StoreInk)}; DropdownMenu(expanded,{expanded=false}) {
                DropdownMenuItem(text={Text("Shared shift task")},onClick={ assignee="Shared shift task"; expanded=false })
                staff.filter{it.active}.forEach { s -> DropdownMenuItem(text={Text(s.name)},onClick={ assignee="${s.id}:${s.name}"; expanded=false }) }
            } }
            Button(onClick={val id=assignee.substringBefore(":").toIntOrNull(); if(editingId==null) onCreate(title.trim(),date,type,id) else onUpdate(editingId!!,title.trim(),date,type,id); title=""; editingId=null; assignee="Shared shift task"},enabled=title.isNotBlank()&&runCatching{LocalDate.parse(date)}.isSuccess,colors=ButtonDefaults.buttonColors(containerColor=StoreMint,contentColor=StoreBlack)){Text(if(editingId==null) "Create task" else "Save task changes")}
            Text("Assigned tasks are visible only to that employee and managers.",color=StoreMuted,fontSize=10.sp)
        } }
        item { Text(if(manager) "Store tasks" else "My tasks",color=StoreInk,fontWeight=FontWeight.Bold) }
        items(tasks) { task -> PanelCard {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(task.title,color=StoreInk,fontWeight=FontWeight.Medium); Text("${task.date} · ${task.shift} · ${task.assignedName ?: "Shared"}",color=StoreMuted,fontSize=10.sp) }
                Checkbox(checked=task.done,onCheckedChange={onComplete(task.id,it)},enabled=!manager || task.assignedName!=null,colors=CheckboxDefaults.colors(checkedColor=StoreMint))
                if(manager) {
                    TextButton(onClick={title=task.title;date=task.date;type=task.shift;assignee=staff.firstOrNull { it.name==task.assignedName }?.let { "${it.id}:${it.name}" } ?: "Shared shift task";editingId=task.id}) { Text("Edit",color=StoreMint,fontSize=10.sp) }
                    TextButton(onClick={onDelete(task.id)}){Text("Delete",color=Color(0xFFFF8A80),fontSize=10.sp)}
                }
            }
            if(manager && task.assignedName==null) Text("Completed by ${task.doneCount} of ${task.memberCount} assigned staff",color=StoreMuted,fontSize=10.sp)
        } }
    }
}

@Composable private fun ProductPanel(products: List<StoreProduct>, onCreate: (String,String,String,String,Int)->Unit, onAddBatch: (Int,String,Int)->Unit) {
    var name by remember { mutableStateOf("") }; var category by remember { mutableStateOf("") }; var barcode by remember { mutableStateOf("") }; var expiry by remember { mutableStateOf(LocalDate.now().plusMonths(1).toString()) }; var quantity by remember { mutableStateOf("1") }
    var batchProductId by remember { mutableStateOf<Int?>(null) }; var batchExpanded by remember { mutableStateOf(false) }; var batchExpiry by remember { mutableStateOf(LocalDate.now().plusMonths(2).toString()) }; var batchQuantity by remember { mutableStateOf("1") }
    LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp)) {
        item { PanelCard {
            Text("Add product / expiry batch",color=StoreInk,fontWeight=FontWeight.Bold)
            StoreField(name,{name=it},"Product name"); StoreField(category,{category=it},"Category"); StoreField(barcode,{barcode=it},"Barcode (optional)"); StoreField(expiry,{expiry=it},"Expiry date (YYYY-MM-DD)"); StoreField(quantity,{quantity=it.filter(Char::isDigit)},"Quantity")
            Button(onClick={onCreate(name.trim(),category.trim(),barcode.trim(),expiry,quantity.toIntOrNull()?:0);name="";barcode=""},enabled=name.isNotBlank()&&runCatching{LocalDate.parse(expiry)}.isSuccess&&quantity.toIntOrNull()!=null,colors=ButtonDefaults.buttonColors(containerColor=StoreMint,contentColor=StoreBlack)){Text("Save product")}
            Text("Barcode is optional. Each product can have several batches with separate expiry dates.",color=StoreMuted,fontSize=10.sp)
        } }
        item { PanelCard {
            Text("Add another expiry batch",color=StoreInk,fontWeight=FontWeight.Bold)
            Box {
                OutlinedButton(onClick={batchExpanded=true},modifier=Modifier.fillMaxWidth(),enabled=products.isNotEmpty()){Text(products.firstOrNull { it.id==batchProductId }?.name ?: "Choose existing product",color=StoreInk)}
                DropdownMenu(expanded=batchExpanded,onDismissRequest={batchExpanded=false}) { products.distinctBy { it.id }.forEach { product -> DropdownMenuItem(text={Text(product.name)},onClick={batchProductId=product.id;batchExpanded=false}) } }
            }
            StoreField(batchExpiry,{batchExpiry=it},"Batch expiry (YYYY-MM-DD)")
            StoreField(batchQuantity,{batchQuantity=it.filter(Char::isDigit)},"Batch quantity")
            Button(onClick={onAddBatch(batchProductId!!,batchExpiry,batchQuantity.toIntOrNull()?:0);batchProductId=null},enabled=batchProductId!=null&&runCatching{LocalDate.parse(batchExpiry)}.isSuccess&&batchQuantity.toIntOrNull()!=null,colors=ButtonDefaults.buttonColors(containerColor=StoreMint,contentColor=StoreBlack)){Text("Add batch") }
        } }
        item { Text("Expiry inventory",color=StoreInk,fontWeight=FontWeight.Bold) }
        items(products) { p -> PanelCard { Text(p.name,color=StoreInk,fontWeight=FontWeight.SemiBold); Text("${p.category.ifBlank { "Uncategorized" }} · Qty ${p.quantity}",color=StoreMuted,fontSize=11.sp); Text("Expiry: ${p.expiry}",color=StoreMint,fontSize=12.sp); if(p.barcode.isNotBlank()) Text("Barcode: ${p.barcode}",color=StoreMuted,fontSize=10.sp) } }
    }
}
