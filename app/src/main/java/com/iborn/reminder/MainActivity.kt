package com.iborn.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.NumberPicker
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

private val Mint = Color(0xFF7DF9D5)
private val Lilac = Mint
private val PaleLilac = Color(0xFF2A2D3A)
private val Ink = Color(0xFFF4F6FA)
private val Muted = Color(0xFFA4A9B8)
private val AppBlack = Color(0xFF1A1C23)
private val CardDark = Color(0xFF2A2D3A)
private val CardDarkAlt = Color(0xFF343848)
private val JournalPaper = Color(0xFF1A1C23)
private val JournalCard = Color(0xFF2A2D3A)
private val JournalInk = Color(0xFFF4F6FA)
private val JournalRed = Color(0xFF7DF9D5)
private fun s(en: String, fa: String, english: Boolean) = if (english) en else fa

data class ReminderItem(val id: String, val title: String, val date: String, val time: String, val category: String, val done: Boolean = false)
data class BatchItem(val id: String, val product: String, val barcode: String, val quantity: Int, val expiry: String, val category: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val channel = NotificationChannel("reminders", "Reminder alerts", NotificationManager.IMPORTANCE_HIGH)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 44)
        setContent { ReminderApp(this) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ReminderApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("reminder_local", Context.MODE_PRIVATE) }
    var dark by remember {
        val needsDarkDefault = !prefs.getBoolean("journal_theme_migrated_v1", false)
        if (needsDarkDefault) prefs.edit().putBoolean("dark", false).putBoolean("journal_theme_migrated_v1", true).apply()
        mutableStateOf(prefs.getBoolean("dark", false))
    }
    val english = true
    LaunchedEffect(Unit) { prefs.edit().putBoolean("english", true).apply() }
    var tab by remember { mutableIntStateOf(0) }
    var reminders by remember { mutableStateOf(loadReminders(prefs)) }
    var batches by remember { mutableStateOf(loadBatches(prefs)) }
    var productCategories by remember { mutableStateOf(loadCategories(prefs)) }
    var showReminder by remember { mutableStateOf(false) }
    var showBatch by remember { mutableStateOf(false) }
    var reminderCategory by remember { mutableStateOf("Daily") }
    var barcode by remember { mutableStateOf("") }
    val scanner = remember { GmsBarcodeScanning.getClient(context) }
    val colors = darkColorScheme(primary = Mint, secondary = Mint, background = JournalPaper, surface = JournalCard, surfaceVariant = CardDarkAlt, onBackground = JournalInk, onSurface = JournalInk)
    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { BottomBar(tab, english) { tab = it } },
            floatingActionButton = {
                if (tab in 1..4) FloatingActionButton(onClick = { if (tab == 5) showBatch = true else { reminderCategory = listOf("", "Daily", "Installments", "Birthdays", "Miscellaneous")[tab]; showReminder = true } }, containerColor = Lilac, contentColor = Color.White) { Icon(Icons.Rounded.Add, contentDescription = s("Add", "افزودن", english)) }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Header(
                    english,
                    { tab = 1 },
                    {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Reminder — reminders and product expiry tracker")
                        }
                        context.startActivity(Intent.createChooser(share, s("Share Reminder", "اشتراک‌گذاری Reminder", english)))
                    }
                )
                AnimatedContent(targetState = tab, transitionSpec = { (fadeIn(tween(170)) + slideInHorizontally(tween(170)) { it / 14 }) togetherWith (fadeOut(tween(120)) + slideOutHorizontally(tween(120)) { -it / 18 }) }, label = "tab-transition") { page ->
                    when (page) {
                        0 -> HomeScreen(reminders, batches, english, { tab = 1 }, { tab = 5 }, { reminderCategory = "Daily"; showReminder = true }, { item -> reminders = reminders.map { if (it.id == item.id) it.copy(done = !it.done) else it }; saveReminders(prefs, reminders) })
                        1 -> ReminderList(reminders, english, { reminderCategory = "Daily"; showReminder = true }, { item -> reminders = reminders.map { if (it.id == item.id) it.copy(done = !it.done) else it }; saveReminders(prefs, reminders) }, { item -> reminders = reminders.filterNot { it.id == item.id }; saveReminders(prefs, reminders) })
                        2 -> SimpleCategory(s("Installments", "اقساط", english), s("Payments and due dates", "پرداخت‌ها و سررسیدها", english), reminders.filter { it.category == "Installments" }, english, { reminderCategory = "Installments"; showReminder = true }, { item -> reminders = reminders.filterNot { it.id == item.id }; saveReminders(prefs, reminders) })
                        3 -> SimpleCategory(s("Birthdays", "تولدها", english), s("Important dates", "تاریخ‌های مهم", english), reminders.filter { it.category == "Birthdays" }, english, { reminderCategory = "Birthdays"; showReminder = true }, { item -> reminders = reminders.filterNot { it.id == item.id }; saveReminders(prefs, reminders) })
                        4 -> SimpleCategory(s("Miscellaneous", "متفرقه", english), s("Other reminders and notes", "یادآورها و یادداشت‌های دیگر", english), reminders.filter { it.category == "Miscellaneous" }, english, { reminderCategory = "Miscellaneous"; showReminder = true }, { item -> reminders = reminders.filterNot { it.id == item.id }; saveReminders(prefs, reminders) })
                        5 -> StorePortal(context)
                        else -> ProfileScreen(dark, { dark = it; prefs.edit().putBoolean("dark", it).apply() })
                    }
                }
            }
        }
        if (showReminder) ReminderDialog(reminderCategory, english, { showReminder = false }) { title, date, time, category ->
            val item = ReminderItem(UUID.randomUUID().toString(), title, date, time, category)
            reminders = reminders + item; saveReminders(prefs, reminders); scheduleReminder(context, item); showReminder = false
        }
        if (showBatch) BatchDialog(barcode, productCategories, english, onScan = { scanner.startScan().addOnSuccessListener { barcode = it.rawValue ?: "" } }, onDismiss = { showBatch = false }, onSave = { product, code, qty, expiry, category ->
            batches = batches + BatchItem(UUID.randomUUID().toString(), product, code, qty, expiry, category); saveBatches(prefs, batches); scheduleExpiry(context, product, expiry); if (!productCategories.contains(category)) { productCategories = productCategories + category; saveCategories(prefs, productCategories) }; showBatch = false; barcode = ""
        })
    }
}

@Composable private fun Header(
    english: Boolean,
    openCalendar: () -> Unit,
    shareApp: () -> Unit
) {
    val today = remember { SimpleDateFormat("EEEE, MMM dd", Locale.ENGLISH).format(Calendar.getInstance().time) }
    Row(
        Modifier.fillMaxWidth().background(JournalPaper).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Hey, there!", color = JournalInk, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(today, color = Muted, fontSize = 12.sp)
        }
        Surface(shape = RoundedCornerShape(13.dp), color = JournalCard, modifier = Modifier.size(42.dp).clickable { openCalendar() }) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.GridView, "Menu", tint = JournalInk, modifier = Modifier.size(21.dp)) }
        }
    }
}

@Composable private fun HomeScreen(
    reminders: List<ReminderItem>,
    batches: List<BatchItem>,
    english: Boolean,
    goReminders: () -> Unit,
    goExpiry: () -> Unit,
    add: () -> Unit,
    toggleTask: (ReminderItem) -> Unit
) {
    var filter by remember { mutableStateOf("All tasks") }
    val task = remember(reminders) { reminders.filter { it.category == "Daily" && !it.done }.minWithOrNull(compareBy<ReminderItem> { it.date }.thenBy { it.time }) }
    val expiringCount = remember(batches) { batches.count { daysUntil(it.expiry) in 0..7 } }
    val work = remember(reminders) { reminders.filter { it.category == "Installments" && !it.done }.minWithOrNull(compareBy<ReminderItem> { it.date }.thenBy { it.time }) }
    val birthdayCount = remember(reminders) { reminders.count { it.category == "Birthdays" && !it.done } }
    val doneCount = remember(reminders) { reminders.count { it.done } }
    val total = reminders.size
    val completion = if (total == 0) 0 else doneCount * 100 / total
    val filters = listOf("All tasks", "Work", "Personal", "Birthdays")
    val visible = when (filter) {
        "Work" -> reminders.filter { it.category == "Installments" }
        "Personal" -> reminders.filter { it.category == "Daily" || it.category == "Miscellaneous" }
        "Birthdays" -> reminders.filter { it.category == "Birthdays" }
        else -> reminders
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(JournalPaper).padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 5.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = Mint, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("TODAY AT A GLANCE", color = Color(0xFF153C36), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                            Text(task?.title ?: "Your day is wide open", color = Color(0xFF102B27), fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                            Text(task?.let { "${it.date} · ${it.time}" } ?: "Add a reminder and make it count", color = Color(0xFF315C53), fontSize = 11.sp)
                        }
                        Box(Modifier.size(66.dp).background(Color(0xFF5DE6C0), CircleShape), contentAlignment = Alignment.Center) {
                            Text("$completion%", color = Color(0xFF102B27), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MintStat("$total", "TASKS", Modifier.weight(1f))
                        MintStat("$doneCount", "DONE", Modifier.weight(1f))
                        MintStat("$expiringCount", "EXPIRING", Modifier.weight(1f))
                        MintStat("$birthdayCount", "BIRTHDAYS", Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                filters.forEach { value ->
                    val active = filter == value
                    Surface(shape = RoundedCornerShape(30.dp), color = if (active) Mint else JournalCard,
                        modifier = Modifier.weight(1f).clickable { filter = value }) {
                        Text(value, modifier = Modifier.padding(horizontal = 5.dp, vertical = 11.dp), textAlign = TextAlign.Center,
                            color = if (active) Color(0xFF102B27) else Muted, fontSize = 10.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                DarkTaskCard("NEAR EXPIRY", "$expiringCount", if (expiringCount == 1) "batch within 7 days" else "batches within 7 days", Icons.Rounded.Inventory2, expiringCount > 0, goExpiry, Modifier.weight(1f))
                DarkTaskCard("WORK DEADLINE", work?.title ?: "All clear", work?.let { "${it.date} · ${it.time}" } ?: "No pending deadline", Icons.Rounded.Event, work != null, goReminders, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                DarkTaskCard("BIRTHDAYS", "$birthdayCount", "upcoming dates", Icons.Rounded.Cake, birthdayCount > 0, goReminders, Modifier.weight(1f))
                DarkTaskCard("QUICK NOTE", "Capture an idea", "Add a personal reminder", Icons.Rounded.EditNote, false, add, Modifier.weight(1f))
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your reminders", modifier = Modifier.weight(1f), color = JournalInk, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("+ Add", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { add() })
            }
        }
        if (visible.isEmpty()) item { Text("Nothing here yet. Add your first reminder.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp)) }
        else items(visible.take(6), key = { "dark-${it.id}" }) { item ->
            Surface(shape = RoundedCornerShape(18.dp), color = JournalCard, modifier = Modifier.fillMaxWidth().clickable { toggleTask(item) }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (item.done) Icons.Rounded.CheckCircle else Icons.Rounded.NotificationsNone, null, tint = Mint, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                        Text(item.title, color = JournalInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("${item.date} · ${item.time}   •   ${categoryLabel(item.category, true)}", color = Muted, fontSize = 10.sp)
                    }
                    Switch(checked = item.done, onCheckedChange = { toggleTask(item) }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF102B27), checkedTrackColor = Mint, uncheckedThumbColor = Muted, uncheckedTrackColor = CardDarkAlt))
                }
            }
        }
    }
}

@Composable private fun MintStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Color(0xFF69EBC8), RoundedCornerShape(12.dp)).padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color(0xFF102B27), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Color(0xFF315C53), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
    }
}

@Composable private fun DarkTaskCard(title: String, value: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var enabled by remember { mutableStateOf(active) }
    val bg = if (enabled) Mint else JournalCard
    val fg = if (enabled) Color(0xFF102B27) else JournalInk
    Surface(shape = RoundedCornerShape(22.dp), color = bg, modifier = modifier.heightIn(min = 158.dp).clickable { onClick() }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = fg, modifier = Modifier.size(21.dp))
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.NotificationsNone, null, tint = if (enabled) Color(0xFF315C53) else Muted, modifier = Modifier.size(17.dp))
            }
            Text(title, color = if (enabled) Color(0xFF315C53) else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
            Text(value, color = fg, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(detail, color = if (enabled) Color(0xFF315C53) else Muted, fontSize = 10.sp, maxLines = 2)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (enabled) "ON" else "OFF", color = fg, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF102B27), checkedTrackColor = Color(0xFF4DE0B8), uncheckedThumbColor = Muted, uncheckedTrackColor = CardDarkAlt))
            }
        }
    }
}

@Composable private fun JournalCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier, shape = RoundedCornerShape(24.dp), color = JournalCard, shadowElevation = 2.dp) {
        Column(Modifier.padding(15.dp), content = content)
    }
}

@Composable private fun JournalPill(value: String, label: String, modifier: Modifier = Modifier) {
    Row(modifier.background(Color(0xFFF0E0D2), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(value, color = JournalRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text(label, color = Muted, fontSize = 10.sp)
    }
}

@Composable private fun JournalMiniCard(title: String, value: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit, warning: Boolean) {
    JournalCard(modifier.clickable { onClick() }) {
        Icon(icon, null, tint = JournalRed, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(9.dp))
        Text(title, color = JournalRed, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp, maxLines = 1)
        Spacer(Modifier.height(7.dp))
        Text(value, color = JournalInk, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, maxLines = 2)
        Spacer(Modifier.height(4.dp))
        Text(detail, color = if (warning) JournalRed else Muted, fontSize = 10.sp, fontWeight = if (warning) FontWeight.Bold else FontWeight.Normal, maxLines = 2)
    }
}

@Composable private fun ProfileScreen(dark: Boolean, onTheme: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().background(JournalPaper).padding(20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Text("PROFILE", color = Mint, fontSize = 10.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold)
        Text("Your space", color = JournalInk, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        JournalCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(54.dp).background(Color(0xFF343848), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PersonOutline, null, tint = Mint, modifier = Modifier.size(28.dp))
                }
                Column(Modifier.padding(start = 13.dp)) {
                    Text("Reminder user", color = JournalInk, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Personal organizer", color = Muted, fontSize = 11.sp)
                }
            }
        }
        Text("Your reminders and store expiry lists are saved locally on this device.", color = Muted, fontSize = 12.sp)
    }
}

@Composable private fun SettingsScreen() {
    Column(Modifier.fillMaxSize().background(JournalPaper).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("SETTINGS", color = Mint, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
        Text("App preferences", color = JournalInk, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        JournalCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.NotificationsActive, null, tint = Mint)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Notifications", color = JournalInk, fontWeight = FontWeight.SemiBold)
                    Text("Reminder alerts are managed by Android", color = Muted, fontSize = 11.sp)
                }
            }
        }
        JournalCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Storage, null, tint = Mint)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Local data", color = JournalInk, fontWeight = FontWeight.SemiBold)
                    Text("Your reminders stay on this device", color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable private fun OverviewStat(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, tint = Lilac, modifier = Modifier.size(17.dp))
            Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, color = Muted, fontSize = 9.sp, maxLines = 1)
        }
    }
}

@Composable private fun DarkEmptyState(title: String, subtitle: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(Lilac.copy(alpha = .12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.EventAvailable, null, tint = Lilac, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(subtitle, color = Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable private fun HomeSectionHeading(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground)
        TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
            Text(action, color = Lilac, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Rounded.ChevronRight, null, tint = Lilac, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable private fun HomeReminderRow(item: ReminderItem, english: Boolean) {
    Surface(shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Lilac.copy(alpha = .13f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.EventNote, null, tint = Lilac, modifier = Modifier.size(21.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Text("${item.date}  •  ${categoryLabel(item.category, english)}", color = Muted, fontSize = 10.sp, maxLines = 1)
            }
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
                Text(item.time, modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp), color = Lilac, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}

@Composable private fun HomeBatchRow(item: BatchItem, english: Boolean) {
    val days = daysUntil(item.expiry)
    val accent = if (days < 0) Color(0xFFFF6B6B) else if (days <= 7) Lilac else Color(0xFFB8B8B8)
    Surface(shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(accent.copy(alpha = .12f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Inventory2, null, tint = accent, modifier = Modifier.size(21.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.product, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Text("${item.quantity} ${s("units", "عدد", english)}  •  ${item.expiry}", color = Muted, fontSize = 10.sp)
            }
            Text(if (days < 0) s("Expired", "منقضی", english) else if (days == 0) s("Today", "امروز", english) else "$days ${s("days", "روز", english)}", color = accent, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        }
    }
}

@Composable private fun ReminderList(list: List<ReminderItem>, english: Boolean, add: () -> Unit, toggle: (ReminderItem) -> Unit, delete: (ReminderItem) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) { SectionTitle(s("Daily reminders", "یادآورهای روزانه", english), s("Tasks and appointments", "کارها و قرارهای مهمت", english), add); if (list.isEmpty()) EmptyState(s("Your list is empty", "لیستت خالیه", english), s("Add your first reminder", "اولین یادآورت رو ثبت کن", english)) else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) { items(list, key = { it.id }) { ReminderRow(it, english, { toggle(it) }, { delete(it) }) } } }
}

@Composable private fun SimpleCategory(title: String, subtitle: String, list: List<ReminderItem>, english: Boolean, add: () -> Unit, delete: (ReminderItem) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) { SectionTitle(title, subtitle, add); if (list.isEmpty()) EmptyState(s("Nothing here yet", "هنوز موردی ثبت نشده", english), s("Tap + to add an item", "برای اضافه‌کردن روی + بزن", english)) else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(list, key = { it.id }) { ReminderRow(it, english, {}, { delete(it) }) } } }
}

@Composable private fun ExpiryScreen(batches: List<BatchItem>, categories: List<String>, english: Boolean, add: () -> Unit, delete: (BatchItem) -> Unit) {
    val grouped = remember(batches) { batches.groupBy { it.category }.mapValues { (_, lots) -> lots.sortedBy { daysUntil(it.expiry) } } }
    val soonCount = remember(batches) { batches.count { daysUntil(it.expiry) in 0..7 } }
    val expiredCount = remember(batches) { batches.count { daysUntil(it.expiry) < 0 } }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        SectionTitle(s("Product expiry", "انقضای محصولات", english), s("Track multiple batches for each product", "برای هر محصول چند سری ثبت کن", english), add)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(bottom = 12.dp)) { MiniInfo(s("Batches", "کل سری‌ها", english), batches.size.toString(), Modifier.weight(1f)); MiniInfo(s("Soon", "نزدیک انقضا", english), soonCount.toString(), Modifier.weight(1f)); MiniInfo(s("Expired", "منقضی‌شده", english), expiredCount.toString(), Modifier.weight(1f)) }
        if (batches.isEmpty()) EmptyState(s("No products added", "هنوز محصولی ثبت نکردی", english), s("Add manually or scan a barcode", "محصول رو دستی وارد کن یا بارکدش رو اسکن کن", english)) else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            grouped.forEach { (category, lots) -> item { Text(category, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Lilac, modifier = Modifier.padding(top = 9.dp, bottom = 2.dp)) }; items(lots, key = { it.id }) { BatchRow(it, english) { delete(it) } } }
        }
    }
}

@Composable private fun MiniInfo(label: String, value: String, modifier: Modifier = Modifier) { Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = modifier) { Column(Modifier.padding(11.dp)) { Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Lilac); Text(label, fontSize = 10.sp, color = Muted) } } }
@Composable private fun SectionTitle(title: String, subtitle: String, add: () -> Unit) { Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground); Text(subtitle, fontSize = 11.sp, color = Muted) }; Surface(shape = CircleShape, color = Lilac, modifier = Modifier.size(42.dp).clickable { add() }) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = Color.White) } } } }

@Composable private fun ReminderRow(item: ReminderItem, english: Boolean, toggle: () -> Unit, delete: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) { Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Surface(shape = RoundedCornerShape(12.dp), color = PaleLilac, modifier = Modifier.size(42.dp).clickable { toggle() }) { Box(contentAlignment = Alignment.Center) { Icon(if (item.done) Icons.Rounded.CheckCircle else Icons.Rounded.Notifications, null, tint = Lilac) } }; Column(Modifier.weight(1f).padding(horizontal = 11.dp)) { Text(item.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface); Text("${item.date}  •  ${item.time}  •  ${categoryLabel(item.category, english)}", color = Muted, fontSize = 10.sp) }; IconButton(onClick = delete) { Icon(Icons.Rounded.DeleteOutline, null, tint = Muted) } } }
}
@Composable private fun BatchRow(item: BatchItem, english: Boolean, delete: () -> Unit) { val days = daysUntil(item.expiry); val warning = days < 0 || days <= 7; Surface(shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) { Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Surface(shape = RoundedCornerShape(12.dp), color = if (warning) Color(0xFF35251F) else PaleLilac, modifier = Modifier.size(42.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Inventory2, null, tint = if (warning) Color(0xFFFF8A65) else Lilac) } }; Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text(item.product, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface); Text("${item.quantity} ${s("units", "عدد", english)}  •  ${item.expiry}", fontSize = 11.sp, color = Muted); if (item.barcode.isNotBlank()) Text("Barcode: ${item.barcode}", fontSize = 9.sp, color = Muted) }; Text(if (days < 0) s("Expired", "منقضی", english) else if (days == 0) s("Today", "امروز", english) else "$days ${s("days", "روز", english)}", color = if (warning) Color(0xFFFF8A65) else Lilac, fontWeight = FontWeight.Bold, fontSize = 10.sp); IconButton(onClick = delete) { Icon(Icons.Rounded.DeleteOutline, null, tint = Muted) } } } }

@Composable private fun EmptyState(title: String, subtitle: String) { Column(Modifier.fillMaxWidth().padding(vertical = 35.dp), horizontalAlignment = Alignment.CenterHorizontally) { Surface(shape = CircleShape, color = PaleLilac, modifier = Modifier.size(62.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, tint = Lilac, modifier = Modifier.size(28.dp)) } }; Spacer(Modifier.height(12.dp)); Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground); Text(subtitle, color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center) } }

@Composable private fun BottomBar(selected: Int, english: Boolean, onSelect: (Int) -> Unit) {
    val selectedNav = when (selected) { 5 -> 0; 0, 1, 2, 3, 4 -> 1; 7 -> 2; else -> 3 }
    val navItems = listOf(
        Triple("Store", Icons.Rounded.ShoppingBag, 5),
        Triple("Personal", Icons.Rounded.Home, 0),
        Triple("Settings", Icons.Rounded.Settings, 7),
        Triple("Profile", Icons.Rounded.PersonOutline, 6)
    )
    Row(Modifier.fillMaxWidth().background(JournalPaper).padding(horizontal = 13.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        navItems.forEachIndexed { index, item ->
            val active = selectedNav == index
            Column(Modifier.weight(1f).clickable { onSelect(item.third) }.padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(item.second, null, tint = if (active) Mint else Muted, modifier = Modifier.size(21.dp))
                    AnimatedVisibility(visible = active, enter = fadeIn(tween(180)), exit = fadeOut(tween(120))) {
                        Text(item.first, color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 5.dp), maxLines = 1)
                    }
                }
                Box(Modifier.size(width = 4.dp, height = 4.dp).background(if (active) Mint else Color.Transparent, CircleShape))
            }
        }
    }
}

@Composable private fun ReminderDialog(initialCategory: String, english: Boolean, onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }; var date by remember { mutableStateOf("1405/09/01") }; var time by remember { mutableStateOf("09:00") }; var category by remember { mutableStateOf(initialCategory) }; var picker by remember { mutableStateOf(0) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(s("New reminder", "یادآور جدید", english), fontWeight = FontWeight.Bold) }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(title, { title = it }, label = { Text(s("Title", "عنوان یادآور", english)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { picker = 1 }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text("$date") }
        OutlinedButton(onClick = { picker = 2 }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Schedule, null); Spacer(Modifier.width(8.dp)); Text(time) }
        Text(s("Category", "دسته‌بندی", english), color = Muted, fontSize = 12.sp); Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("Daily", "Installments", "Birthdays", "Miscellaneous").forEach { FilterChip(category == it, { category = it }, label = { Text(categoryLabel(it, english), fontSize = 10.sp) }) } }
    } }, confirmButton = { TextButton(onClick = { if (title.isNotBlank() && validJalali(date)) onSave(title, date, time, category) }) { Text(s("Save", "ذخیره", english), color = Lilac) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(s("Cancel", "انصراف", english)) } })
    if (picker != 0) WheelPickerDialog(picker == 1, date, time, english, { date = it; picker = 0 }, { time = it; picker = 0 }, { picker = 0 })
}

@Composable private fun WheelPickerDialog(isDate: Boolean, date: String, time: String, english: Boolean, onDate: (String) -> Unit, onTime: (String) -> Unit, onDismiss: () -> Unit) {
    val dp = date.split('/'); val tp = time.split(':'); var y by remember { mutableIntStateOf(dp.getOrNull(0)?.toIntOrNull() ?: 1405) }; var m by remember { mutableIntStateOf(dp.getOrNull(1)?.toIntOrNull() ?: 9) }; var d by remember { mutableIntStateOf(dp.getOrNull(2)?.toIntOrNull() ?: 1) }; var h by remember { mutableIntStateOf(tp.getOrNull(0)?.toIntOrNull() ?: 9) }; var min by remember { mutableIntStateOf(tp.getOrNull(1)?.toIntOrNull() ?: 0) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (isDate) s("Choose date", "انتخاب تاریخ", english) else s("Choose time", "انتخاب ساعت", english)) }, text = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            if (isDate) { Wheel(y, 1400, 1450) { y = it }; Wheel(m, 1, 12) { m = it; d = d.coerceAtMost(if (m <= 6) 31 else 30) }; Wheel(d, 1, if (m <= 6) 31 else 30) { d = it } }
            else { Wheel(h, 0, 23) { h = it }; Text(":", fontSize = 22.sp, fontWeight = FontWeight.Bold); Wheel(min, 0, 59) { min = it } }
        }
    }, confirmButton = { TextButton(onClick = { if (isDate) onDate("%04d/%02d/%02d".format(y,m,d)) else onTime("%02d:%02d".format(h,min)) }) { Text(s("Done", "تأیید", english), color = Lilac) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(s("Cancel", "انصراف", english)) } })
}
@Composable private fun Wheel(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) { AndroidView(factory = { ctx -> NumberPicker(ctx).apply { minValue = min; maxValue = max; this.value = value; wrapSelectorWheel = true; descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS; setOnValueChangedListener { _, _, new -> onChange(new) } } }, update = { if (it.value != value) it.value = value }, modifier = Modifier.width(76.dp).height(145.dp)) }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable private fun BatchDialog(initialBarcode: String, categories: List<String>, english: Boolean, onScan: () -> Unit, onDismiss: () -> Unit, onSave: (String, String, Int, String, String) -> Unit) {
    var product by remember { mutableStateOf("") }; var code by remember { mutableStateOf(initialBarcode) }; var qty by remember { mutableStateOf("1") }; var expiry by remember { mutableStateOf("1405/09/01") }; var category by remember { mutableStateOf(categories.firstOrNull() ?: "General") }; var newCategory by remember { mutableStateOf("") }; var picker by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(s("Add product batch", "ثبت سری محصول", english), fontWeight = FontWeight.Bold) }, text = { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        OutlinedTextField(product, { product = it }, label = { Text(s("Product name", "نام محصول", english)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(code, { code = it }, label = { Text(s("Barcode (optional)", "بارکد (اختیاری)", english)) }, singleLine = true, trailingIcon = { IconButton(onClick = onScan) { Icon(Icons.Rounded.QrCodeScanner, s("Scan barcode", "اسکن بارکد", english), tint = Lilac) } })
        OutlinedTextField(qty, { qty = it.filter(Char::isDigit) }, label = { Text(s("Quantity", "تعداد", english)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Text(s("Product category", "دسته‌بندی محصول", english), color = Muted, fontSize = 12.sp)
        if (categories.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) { categories.forEach { FilterChip(category == it, { category = it }, label = { Text(it, fontSize = 10.sp) }) } }
        OutlinedTextField(newCategory, { newCategory = it; if (it.isNotBlank()) category = it.trim() }, label = { Text(s("Add a new category", "دسته‌بندی جدید", english)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text("${s("Expiry date", "تاریخ انقضا", english)}: $expiry") }
        Text(s("Each batch keeps its own expiry date. Categories are saved for next time.", "هر سری تاریخ انقضای مستقل دارد و دسته‌بندی برای دفعات بعد ذخیره می‌شود.", english), color = Muted, fontSize = 10.sp)
    } }, confirmButton = { TextButton(onClick = { val count = qty.toIntOrNull() ?: 0; val cat = category.trim().ifBlank { "General" }; if (product.isNotBlank() && count > 0 && validJalali(expiry)) onSave(product.trim(), code, count, expiry, cat) }) { Text(s("Save batch", "ثبت سری", english), color = Lilac) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(s("Cancel", "انصراف", english)) } })
    if (picker) WheelPickerDialog(true, expiry, "09:00", english, { expiry = it; picker = false }, {}, { picker = false })
}

private fun categoryLabel(category: String, english: Boolean) = when (category) { "Daily" -> s("Daily", "روزانه", english); "Installments" -> s("Installments", "اقساط", english); "Birthdays" -> s("Birthdays", "تولدها", english); "Miscellaneous" -> s("Miscellaneous", "متفرقه", english); else -> category }
private fun saveReminders(p: android.content.SharedPreferences, list: List<ReminderItem>) { val a=JSONArray(); list.forEach { a.put(JSONObject().put("id",it.id).put("title",it.title).put("date",it.date).put("time",it.time).put("category",it.category).put("done",it.done)) }; p.edit().putString("reminders",a.toString()).apply() }
private fun loadReminders(p: android.content.SharedPreferences): List<ReminderItem> = try { val a=JSONArray(p.getString("reminders","[]")); (0 until a.length()).map { val o=a.getJSONObject(it); ReminderItem(o.optString("id"),o.optString("title"),o.optString("date"),o.optString("time"),o.optString("category"),o.optBoolean("done")) } } catch (_:Exception) { emptyList() }
private fun saveBatches(p: android.content.SharedPreferences, list: List<BatchItem>) { val a=JSONArray(); list.forEach { a.put(JSONObject().put("id",it.id).put("product",it.product).put("barcode",it.barcode).put("quantity",it.quantity).put("expiry",it.expiry).put("category",it.category)) }; p.edit().putString("batches",a.toString()).apply() }
private fun loadBatches(p: android.content.SharedPreferences): List<BatchItem> = try { val a=JSONArray(p.getString("batches","[]")); (0 until a.length()).map { val o=a.getJSONObject(it); BatchItem(o.optString("id"),o.optString("product"),o.optString("barcode"),o.optInt("quantity"),o.optString("expiry"),o.optString("category","General")) } } catch (_:Exception) { emptyList() }
private fun saveCategories(p: android.content.SharedPreferences, list: List<String>) { p.edit().putString("product_categories", JSONArray(list).toString()).apply() }
private fun loadCategories(p: android.content.SharedPreferences): List<String> = try { val a=JSONArray(p.getString("product_categories","[]")); (0 until a.length()).map { a.getString(it) } } catch (_:Exception) { emptyList() }
private fun validJalali(s:String):Boolean { val p=s.split('/'); if(p.size!=3)return false; val y=p[0].toIntOrNull()?:return false; val m=p[1].toIntOrNull()?:return false; val d=p[2].toIntOrNull()?:return false; return y in 1300..1500 && m in 1..12 && d in 1..if(m<=6)31 else if(m<=11)30 else 30 }
private fun daysUntil(j:String):Int = try { val p=j.split('/'); val g=jalaliToGregorian(p[0].toInt(),p[1].toInt(),p[2].toInt()); val target=Calendar.getInstance().apply { set(g[0],g[1]-1,g[2],0,0,0);set(Calendar.MILLISECOND,0) }; val today=Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0) }; ((target.timeInMillis-today.timeInMillis)/86400000L).toInt() } catch (_:Exception) { 9999 }
private fun jalaliToGregorian(jy:Int,jm:Int,jd:Int):IntArray { var year=jy+1595; var days=-355668+365*year+(year/33)*8+(year%33+3)/4+jd+(if(jm<7)(jm-1)*31 else (jm-7)*30+186); var gy=400*(days/146097); days%=146097; if(days>36524){gy+=100*((--days)/36524);days%=36524;if(days>=365)days++}; gy+=4*(days/1461); days%=1461; if(days>365){gy+=(days-1)/365;days=(days-1)%365}; var gd=days+1; val leap=(gy%4==0&&gy%100!=0)||gy%400==0; val md=intArrayOf(31,if(leap)29 else 28,31,30,31,30,31,31,30,31,30,31); var gm=0; while(gm<12&&gd>md[gm]){gd-=md[gm];gm++}; return intArrayOf(gy,gm+1,gd) }
private fun scheduleReminder(c:Context,i:ReminderItem) { try { val p=i.date.split('/'); val g=jalaliToGregorian(p[0].toInt(),p[1].toInt(),p[2].toInt()); val t=i.time.split(':'); val cal=Calendar.getInstance().apply { set(g[0],g[1]-1,g[2],t[0].toInt(),t[1].toInt(),0);set(Calendar.MILLISECOND,0) }; schedule(c,i.id.hashCode(),i.title,cal.timeInMillis) } catch (_:Exception) {} }
private fun scheduleExpiry(c:Context,product:String,expiry:String) { try { val p=expiry.split('/'); val g=jalaliToGregorian(p[0].toInt(),p[1].toInt(),p[2].toInt()); val cal=Calendar.getInstance().apply { set(g[0],g[1]-1,g[2],9,0,0);set(Calendar.MILLISECOND,0) }; val base=product.hashCode()+expiry.hashCode(); schedule(c,base-7,"Reminder: $product expires in 7 days",cal.timeInMillis-7*86400000L); schedule(c,base-1,"Reminder: $product expires tomorrow",cal.timeInMillis-86400000L); schedule(c,base,"Product expiry: $product",cal.timeInMillis) } catch (_:Exception) {} }
private fun schedule(c:Context,id:Int,title:String,at:Long) { if(at<System.currentTimeMillis())return; val i=Intent(c,ReminderReceiver::class.java).putExtra("title",title); val pi=PendingIntent.getBroadcast(c,id,i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); val am=c.getSystemService(Context.ALARM_SERVICE) as AlarmManager; if(Build.VERSION.SDK_INT>=31&&!am.canScheduleExactAlarms())am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi) else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi) }
class ReminderReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){val title=i.getStringExtra("title")?:"Reminder";val n=NotificationCompat.Builder(c,"reminders").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Reminder").setContentText(title).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build();val m=c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;if(Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(c,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)m.notify(title.hashCode(),n)}}
class BootReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){if(i.action==Intent.ACTION_BOOT_COMPLETED){val p=c.getSharedPreferences("reminder_local",Context.MODE_PRIVATE);loadReminders(p).filter{!it.done}.forEach{scheduleReminder(c,it)};loadBatches(p).forEach{scheduleExpiry(c,it.product,it.expiry)}}}}
