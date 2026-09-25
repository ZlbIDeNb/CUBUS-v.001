package ru.zilisnik.mobile

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card as MaterialCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip as MaterialFilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.zilisnik.mobile.data.ApplicationDetails
import ru.zilisnik.mobile.data.ApplicationMapPoint
import ru.zilisnik.mobile.data.ApplicationPhoto
import ru.zilisnik.mobile.data.ApplicationStatusCount
import ru.zilisnik.mobile.data.ApplicationSummary
import ru.zilisnik.mobile.data.AdminUser
import ru.zilisnik.mobile.data.AdminEmployeeWorkspace
import ru.zilisnik.mobile.data.ActivityLogItem
import ru.zilisnik.mobile.data.RegistryAddress
import ru.zilisnik.mobile.data.RegistryStats
import ru.zilisnik.mobile.data.AddMeterRequest
import ru.zilisnik.mobile.data.DocumentationContent
import ru.zilisnik.mobile.data.DocumentationCreate
import ru.zilisnik.mobile.data.DocumentationItem
import ru.zilisnik.mobile.data.DailyStatistics
import ru.zilisnik.mobile.data.TodayStatistics
import ru.zilisnik.mobile.data.PeriodReport
import ru.zilisnik.mobile.data.PriceListItem
import ru.zilisnik.mobile.data.MeterCatalogItem
import ru.zilisnik.mobile.data.UserProfile
import ru.zilisnik.mobile.data.ScheduleDay
import ru.zilisnik.mobile.data.WaterMeter
import ru.zilisnik.mobile.data.WarehouseItem
import ru.zilisnik.mobile.data.WeatherSnapshot
import ru.zilisnik.mobile.update.AppUpdater
import java.text.SimpleDateFormat
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import android.util.LruCache
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

private val CubusColorScheme = lightColorScheme(
    primary = Color(0xFF2B5A78),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE3EF),
    onPrimaryContainer = Color(0xFF0C202C),
    secondary = Color(0xFF3E657D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E7EF),
    onSecondaryContainer = Color(0xFF102630),
    tertiary = Color(0xFF476875),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD5E7EC),
    onTertiaryContainer = Color(0xFF102328),
    background = Color(0xFFF7FAFC),
    onBackground = Color(0xFF172126),
    surface = Color(0xFFF7FAFC),
    onSurface = Color(0xFF172126),
    surfaceVariant = Color(0xFFE2EDF2),
    onSurfaceVariant = Color(0xFF3D4B54),
    outline = Color(0xFF718793),
)

private val CubusBlockColor = Color(0xFFE2EDF2)
private val CubusButtonColor = Color(0xFF2B5A78)
private val WarehouseBlockColor = Color(0xFFE2EDF2)
private val ColdWaterHeaderColor = Color(0xFFD8EEF8)
private val HotWaterHeaderColor = Color(0xFFF7DDE3)
private val CubusComponentShape = RoundedCornerShape(16.dp)
private fun abbreviatedName(value: String): String {
    val parts = value.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    if (parts.isEmpty()) return "Метролог"
    return buildString {
        append(parts.first())
        parts.drop(1).take(2).forEach { part ->
            append(" ")
            append(part.first().uppercaseChar())
            append(".")
        }
    }
}
private fun normalizeWarehouseName(value: String): String = value
    .lowercase(Locale("ru"))
    .replace('ё', 'е')
    .replace(Regex("[^0-9a-zа-я]+"), " ")
    .trim()

private val warehouseDisplayOrder = mapOf(
    normalizeWarehouseName("Счетчик ЭКО НОМ СВ 15-80") to 1,
    normalizeWarehouseName("Счетчик ЭКО НОМ СВ 15-100") to 2,
    normalizeWarehouseName("Счетчик ХВС ITELMA WFK24.D080 (Импульс)") to 3,
    normalizeWarehouseName("Счетчик ГВС ITELMA WFW24.D080 (Импульс)") to 4,
    normalizeWarehouseName("Счетчик ХВС/ГВС УНИВЕРСАЛЬНЫЙ.D080 (Импульс)") to 5,
    normalizeWarehouseName("Обратный клапан (Таблетка)") to 6,
    normalizeWarehouseName("Кран 1/2") to 7,
    normalizeWarehouseName("Муфта 1/2") to 8,
    normalizeWarehouseName("Нипель 1/2") to 9,
    normalizeWarehouseName("Ниппель 1/2") to 9,
    normalizeWarehouseName("Угол 1/2") to 10,
    normalizeWarehouseName("Угол 1/2 (Цанга)") to 11,
    normalizeWarehouseName("Угол Муфта 1/2") to 12,
    normalizeWarehouseName("Футорка В/Р 3/4 на 1/2") to 13,
    normalizeWarehouseName("Футорка Н/Р 3/4 на 1/2") to 14,
    normalizeWarehouseName("Цанга 1/2 (прямая, внешняя резьба)") to 15,
    normalizeWarehouseName("Цанга 1/2 (прямая, внутренняя резьба)") to 17,
    normalizeWarehouseName("Удлинитель 10мм") to 18,
    normalizeWarehouseName("Удлинитель 20мм") to 19,
    normalizeWarehouseName("Удлинитель 30мм") to 20,
    normalizeWarehouseName("Удлинитель 40мм") to 21,
    normalizeWarehouseName("Удлинитель 50мм") to 22,
    normalizeWarehouseName("Тройник 112") to 23,
    normalizeWarehouseName("Тройник 1/2") to 23,
    normalizeWarehouseName("Аэратор") to 24,
    normalizeWarehouseName("Гибкая подводка воды 80 см") to 25,
    normalizeWarehouseName("Труба Металлопласт (1м)") to 26,
    normalizeWarehouseName("Проволока пломбировочная витая 0.65/100") to 27,
    normalizeWarehouseName("Прокладка (Упаковка 100шт.)") to 28,
    normalizeWarehouseName("Лён") to 29,
)
private val warehouseItemComparator = compareBy<WarehouseItem>(
    { warehouseDisplayOrder[normalizeWarehouseName(it.name)] ?: Int.MAX_VALUE },
    { normalizeWarehouseName(it.name) },
)

private fun warehouseNameFontSize(value: String) = when {
    value.length <= 28 -> 12.sp
    value.length <= 40 -> 10.sp
    value.length <= 55 -> 8.sp
    else -> 7.sp
}

private val CubusShapes = Shapes(
    small = CubusComponentShape,
    medium = CubusComponentShape,
    large = CubusComponentShape,
)

@Composable
private fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    MaterialButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = contentPadding,
        shape = CubusComponentShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = CubusButtonColor,
            contentColor = Color.White,
            disabledContainerColor = CubusButtonColor.copy(alpha = 0.38f),
            disabledContentColor = Color.White.copy(alpha = 0.70f),
        ),
        content = content,
    )
}

@Composable
private fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
private fun BackIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.back_button),
            contentDescription = "Назад",
            modifier = Modifier.size(52.dp).clickable(onClick = onClick),
        )
    }
}

@Composable
private fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    MaterialFilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        shape = CubusComponentShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = CubusButtonColor,
            selectedLabelColor = Color.White,
        ),
    )
}

@Composable
private fun Card(
    modifier: Modifier = Modifier,
    containerColor: Color = CubusBlockColor,
    border: BorderStroke? = BorderStroke(1.dp, CubusButtonColor),
    content: @Composable ColumnScope.() -> Unit,
) {
    MaterialCard(
        modifier = modifier,
        shape = CubusComponentShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border,
        content = content,
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = CubusColorScheme,
                shapes = CubusShapes,
            ) {
                ZilisnikApp()
            }
        }
    }
}

private enum class AppSection {
    MAIN, PROFILE, APPLICATIONS, METERS, ADDRESSES, CLIENTS,
    WAREHOUSE, SERVICE, REPORT, DOCUMENTATION, USERS, REGISTRY
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ZilisnikApp(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var section by remember { mutableStateOf(AppSection.MAIN) }
    var previousSection by remember { mutableStateOf(AppSection.MAIN) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var updateInstalling by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }

    fun navigateTo(target: AppSection) {
        if (target != section) {
            previousSection = section
            section = target
        }
    }

    state.appUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = {
                if (!update.mandatory && !updateInstalling) vm.dismissAppUpdate()
            },
            title = { Text("Доступно обновление CUBUS ${update.version_name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        update.release_notes.ifBlank {
                            "Рекомендуется установить новую версию приложения."
                        },
                    )
                    if (update.file_size > 0) {
                        Text("Размер: %.1f МБ".format(update.file_size / 1024.0 / 1024.0))
                    }
                    updateMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            },
            confirmButton = {
                MaterialButton(
                    enabled = !updateInstalling,
                    onClick = {
                        scope.launch {
                            updateInstalling = true
                            updateMessage = "Загружаем и проверяем обновление…"
                            runCatching { AppUpdater.prepareApk(context, update) }
                                .onSuccess { apk ->
                                    if (AppUpdater.requestInstall(context, apk)) {
                                        updateMessage = "Подтвердите установку в окне Android."
                                    } else {
                                        updateMessage = "Разрешите установку из CUBUS, вернитесь и снова нажмите «Обновить»."
                                    }
                                }
                                .onFailure { error ->
                                    updateMessage = error.message ?: "Не удалось загрузить обновление"
                                }
                            updateInstalling = false
                        }
                    },
                ) {
                    if (updateInstalling) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Text("Обновить")
                    }
                }
            },
            dismissButton = if (update.mandatory) null else {
                {
                    MaterialButton(
                        enabled = !updateInstalling,
                        onClick = vm::dismissAppUpdate,
                    ) { Text("Позже") }
                }
            },
        )
    }

    if (state.mustChangePassword) {
        Scaffold { padding ->
            ChangePasswordScreen(
                loading = state.passwordChangeLoading,
                error = state.passwordChangeError,
                onChangePassword = vm::changePassword,
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            )
        }
        return
    }

    if (!state.authorized) {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.cubus_logo),
                        contentDescription = "Жилищник — служба метрологии",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .aspectRatio(702f / 389f),
                    )
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    if (state.loading) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(progress = { state.loadingProgress })
                            Text(state.loadingMessage, textAlign = TextAlign.Center)
                            Text("${(state.loadingProgress * 100).roundToInt()} %")
                        }
                    } else LoginScreen(vm::login)
                }
                Image(
                    painter = painterResource(R.drawable.cubus_brand),
                    contentDescription = "CUBUS",
                    modifier = Modifier.fillMaxWidth(0.26f).aspectRatio(1082f / 797f),
                )
                Text(
                    "CUBUS v. ${BuildConfig.VERSION_NAME}",
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
            }
        }
        return
    }

    LaunchedEffect(section, state.authorized, state.selected?.id) {
        while (section == AppSection.MAIN && state.authorized && state.selected == null) {
            delay(60_000)
            if (section == AppSection.MAIN && state.selected == null) {
                vm.refreshDashboard()
            }
        }
    }
    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(10_000)
            vm.clearMessage()
        }
    }
    LaunchedEffect(state.authorized) {
        val preferences = context.getSharedPreferences("cubus_automatic_updates", Context.MODE_PRIVATE)
        while (state.authorized) {
            val now = Calendar.getInstance()
            val today = now.get(Calendar.DAY_OF_WEEK)
            val currentTime = "%02d:%02d".format(
                Locale.US,
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE),
            )
            val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)
            listOf<Pair<String, () -> Unit>>(
                "work_schedule" to { vm.refreshSchedule() },
                "warehouse" to { vm.loadWarehouse() },
            ).forEach { (key, update) ->
                val days = preferences.getString("${key}_days", "2,3,4,5,6")
                    .orEmpty().split(',').mapNotNull(String::toIntOrNull).toSet()
                val scheduledTime = preferences.getString("${key}_time", "08:00").orEmpty()
                val runKey = "${key}_last_run"
                if (today in days && currentTime >= scheduledTime &&
                    preferences.getString(runKey, "") != todayKey
                ) {
                    update()
                    preferences.edit().putString(runKey, todayKey).apply()
                }
            }
            delay(30_000)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.fillMaxHeight()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.profile_icon),
                        contentDescription = "Личный кабинет",
                        modifier = Modifier.size(48.dp).clickable {
                            navigateTo(AppSection.PROFILE)
                            scope.launch { drawerState.close() }
                        },
                    )
                    Text(
                        abbreviatedName(
                            state.profile?.let { it.full_name.ifBlank { it.login } } ?: "Метролог",
                        ),
                        modifier = Modifier.weight(1f).clickable {
                            navigateTo(AppSection.PROFILE)
                            scope.launch { drawerState.close() }
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Image(
                        painter = painterResource(R.drawable.delete_icon),
                        contentDescription = "Закрыть меню",
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { scope.launch { drawerState.close() } },
                    )
                }
                DrawerItem("Основной экран", AppSection.MAIN, section) {
                    navigateTo(AppSection.MAIN)
                    scope.launch { drawerState.close() }
                }
                DrawerItem("Заявки", AppSection.APPLICATIONS, section) {
                    navigateTo(AppSection.APPLICATIONS)
                    scope.launch { drawerState.close() }
                }
                DrawerItem("Приборы ИПУ", AppSection.METERS, section) {
                    navigateTo(AppSection.METERS)
                    scope.launch { drawerState.close() }
                }
                DrawerItem("Адреса", AppSection.ADDRESSES, section) {
                    navigateTo(AppSection.ADDRESSES)
                    scope.launch { drawerState.close() }
                }
                DrawerItem("Клиенты", AppSection.CLIENTS, section) {
                    navigateTo(AppSection.CLIENTS)
                    scope.launch { drawerState.close() }
                }
                DrawerItem("Склад метролога", AppSection.WAREHOUSE, section) {
                    navigateTo(AppSection.WAREHOUSE)
                    vm.ensureWarehouseLoaded()
                    scope.launch { drawerState.close() }
                }
                DrawerItem("Отчёт", AppSection.REPORT, section) {
                    navigateTo(AppSection.REPORT)
                    scope.launch { drawerState.close() }
                }
                DrawerItem("Сервис", AppSection.SERVICE, section) {
                    navigateTo(AppSection.SERVICE)
                    scope.launch { drawerState.close() }
                }
                DrawerItem("Документация", AppSection.DOCUMENTATION, section) {
                    navigateTo(AppSection.DOCUMENTATION)
                    vm.loadDocuments()
                    scope.launch { drawerState.close() }
                }
                if (isAdministrator(state.profile?.role.orEmpty())) {
                    DrawerItem("Пользователи", AppSection.USERS, section) {
                        navigateTo(AppSection.USERS)
                        vm.loadAdminUsers()
                        scope.launch { drawerState.close() }
                    }
                    DrawerItem("Единый реестр", AppSection.REGISTRY, section) {
                        navigateTo(AppSection.REGISTRY)
                        vm.loadRegistry()
                        scope.launch { drawerState.close() }
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        scope.launch { drawerState.close() }
                        vm.logout()
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) { Text("Выйти из аккаунта") }
                Image(
                    painter = painterResource(R.drawable.cubus_brand),
                    contentDescription = "CUBUS",
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .fillMaxWidth(0.24f)
                        .aspectRatio(1082f / 797f),
                )
                Text(
                    "CUBUS v. ${BuildConfig.VERSION_NAME}",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                if ((section != AppSection.WAREHOUSE && section != AppSection.SERVICE) || state.selected != null) {
                    TopAppBar(
                        title = {
                            Text(
                                state.selected?.let { "Заявка № ${it.number}" } ?: when (section) {
                                    AppSection.MAIN -> "Основной экран"
                                    AppSection.PROFILE -> "Личный кабинет"
                                    AppSection.APPLICATIONS -> "Заявки"
                                    AppSection.METERS -> "Приборы ИПУ"
                                    AppSection.ADDRESSES -> "Адреса"
                                    AppSection.CLIENTS -> "Клиенты"
                                    AppSection.WAREHOUSE -> "Склад метролога"
                                    AppSection.SERVICE -> "Сервис"
                                    AppSection.REPORT -> "Отчёт"
                                    AppSection.DOCUMENTATION -> "Документация"
                                    AppSection.USERS -> "Пользователи"
                                    AppSection.REGISTRY -> "Единый реестр"
                                },
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        navigationIcon = {
                            if (state.selected != null || section != AppSection.MAIN) {
                                BackIconButton(
                                    modifier = Modifier.padding(start = 4.dp),
                                    onClick = {
                                        if (state.selected != null) {
                                            vm.back()
                                        } else {
                                            val target = previousSection
                                            previousSection = section
                                            section = target
                                        }
                                    },
                                )
                            } else {
                                Image(
                                    painter = painterResource(R.drawable.menu_icon),
                                    contentDescription = "Открыть меню",
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(52.dp)
                                        .clickable { scope.launch { drawerState.open() } },
                                )
                            }
                        },
                        actions = { Spacer(Modifier.width(56.dp)) },
                    )
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    state.selected != null && state.completionWizard -> CompletionWizardScreen(
                        details = state.selected!!,
                        priceList = state.priceList,
                        meterCatalog = state.meterCatalog,
                        meterCatalogError = state.meterCatalogError,
                        nomenclatureLoading = state.nomenclatureLoading,
                        nomenclatureError = state.nomenclatureError,
                        photoLoadingKey = state.photoLoadingKey,
                        photoLoadError = state.photoLoadError,
                        onBack = vm::back,
                        onUploadPhoto = vm::uploadPhoto,
                        onDeletePhoto = vm::deletePhoto,
                        onLoadPhoto = vm::loadPhoto,
                        onAddNomenclature = vm::addNomenclature,
                        onDeleteNomenclature = vm::deleteNomenclature,
                        onAddMeter = vm::addMeter,
                        onUpdateMeter = vm::updateMeter,
                        onDeleteMeter = vm::deleteMeter,
                        onConfirm = { id, payment, cash, card ->
                            vm.close(id, payment, cash, card)
                            section = AppSection.MAIN
                        },
                    )
                    state.selected != null -> DetailsScreen(
                        details = state.selected!!,
                        onBack = vm::back,
                        onComplete = vm::startCompletion,
                        onRework = vm::prepareRework,
                        onUploadPhoto = vm::uploadPhoto,
                        onDeletePhoto = vm::deletePhoto,
                        onLoadPhoto = vm::loadPhoto,
                        nomenclatureLoading = state.nomenclatureLoading,
                        nomenclatureError = state.nomenclatureError,
                        photoLoadingKey = state.photoLoadingKey,
                        photoLoadError = state.photoLoadError,
                    )
                    state.loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    section == AppSection.MAIN -> MainScreen(
                        state.profile,
                        state.weather,
                        state.todayStatusCounts,
                        state.todayApplications,
                        state.dailyStatistics,
                        state.todayStatistics,
                        state.todayStatisticsHistory,
                        state.applicationMapPoints,
                        state.applicationMapLoading,
                        state.applicationMapError,
                        vm::select,
                        vm::refreshDashboard,
                        vm::reorderMapPoints,
                    )
                    section == AppSection.PROFILE -> ProfileScreen(
                        state.profile,
                        state.scheduleDays,
                        state.scheduleMonthOffset,
                        state.homeAddressSaving,
                        state.homeAddressError,
                        vm::selectScheduleMonth,
                        vm::refreshSchedule,
                        vm::saveHomeAddress,
                        vm::saveAdministrativeExpenses,
                    )
                    section == AppSection.APPLICATIONS -> ApplicationsScreen(
                        applications = state.applications,
                        selectedDayOffset = state.applicationDayOffset,
                        onDaySelect = vm::selectApplicationDay,
                        onOpen = vm::select,
                        onComplete = vm::startCompletion,
                        onRework = vm::prepareRework,
                    )
                    section == AppSection.METERS -> DirectorySectionScreen(
                        entries = emptyList(),
                        emptyText = "Раздел приборов ИПУ подключим к таблице 610 следующим этапом.",
                    )
                    section == AppSection.ADDRESSES -> DirectorySectionScreen(
                        entries = state.todayApplications.map { it.address }
                            .filter(String::isNotBlank).distinct(),
                        emptyText = "Адреса на сегодня не найдены",
                    )
                    section == AppSection.CLIENTS -> DirectorySectionScreen(
                        entries = state.todayApplications.map { application ->
                            listOf(application.client, application.phone_number)
                                .filter(String::isNotBlank).joinToString(" | ")
                        }.filter(String::isNotBlank).distinct(),
                        emptyText = "Клиенты на сегодня не найдены",
                    )
                    section == AppSection.WAREHOUSE -> WarehouseScreen(
                        warehouseItems = state.warehouseItems,
                        loading = state.warehouseLoading,
                        error = state.warehouseError,
                        onRefresh = { vm.loadWarehouse() },
                        onBack = {
                            val target = previousSection
                            previousSection = section
                            section = target
                        },
                    )
                    section == AppSection.SERVICE -> ServiceScreen(
                        priceListCount = state.priceList.size,
                        meterCatalogCount = state.meterCatalog.size,
                        refreshing = state.serviceRefreshing,
                        onRefreshCatalogs = vm::refreshReferenceCatalogs,
                        onLoadPriceList = vm::loadPriceList,
                        onBack = {
                            val target = previousSection
                            previousSection = section
                            section = target
                        },
                    )
                    section == AppSection.REPORT -> ReportScreen(
                        report = state.report,
                        loading = state.reportLoading,
                        error = state.reportError,
                        onGenerate = vm::generateReport,
                    )
                    section == AppSection.USERS && state.adminWorkspace != null ->
                        AdminEmployeeWorkspaceScreen(
                            workspace = state.adminWorkspace!!,
                            loading = state.adminWorkspaceLoading,
                            error = state.adminWorkspaceError,
                            content = state.adminDocumentContent,
                            applicationDetails = state.adminApplicationDetails,
                            onClose = vm::closeAdminWorkspace,
                            onReload = vm::loadAdminWorkspace,
                            onAddDocument = vm::addAdminDocument,
                            onReplaceDocument = vm::replaceAdminDocument,
                            onDeleteDocument = vm::deleteAdminDocument,
                            onLoadDocument = vm::loadAdminDocumentContent,
                            onContentHandled = vm::clearAdminDocumentContent,
                            onOpenApplication = vm::loadAdminApplication,
                            onApplicationHandled = vm::clearAdminApplication,
                        )
                    section == AppSection.USERS -> AdminUsersScreen(
                            users = state.adminUsers,
                            loading = state.adminUsersLoading,
                            error = state.adminUsersError ?: state.adminWorkspaceError,
                            temporaryPassword = state.temporaryPassword,
                            onRefresh = vm::loadAdminUsers,
                            onCreate = vm::createAdminUser,
                            onSetActive = vm::setAdminUserActive,
                            onResetPassword = vm::resetAdminUserPassword,
                            onControl = { email ->
                                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                vm.loadAdminWorkspace(email, today, today)
                            },
                            onTemporaryPasswordHandled = vm::clearTemporaryPassword,
                        )
                    section == AppSection.REGISTRY -> RegistryScreen(
                        addresses = state.registryAddresses,
                        stats = state.registryStats,
                        loading = state.registryLoading,
                        error = state.registryError,
                        onSearch = vm::loadRegistry,
                        onSync = vm::syncRegistry,
                    )
                    else -> DocumentationScreen(
                        documents = state.documents,
                        loading = state.documentationLoading,
                        error = state.documentationError,
                        content = state.documentContent,
                        onAdd = vm::addDocument,
                        onDelete = vm::deleteDocument,
                        onLoadContent = vm::loadDocumentContent,
                        onContentHandled = vm::clearDocumentContent,
                    )
                }
                state.message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    state.reworkApplicationId?.let { applicationId ->
        ReworkDialog(
            applicationId = applicationId,
            reasons = state.reworkReasons,
            error = state.reworkError,
            onDismiss = vm::cancelRework,
            onConfirm = vm::sendToRework,
        )
    }
}

@Composable
private fun ColumnScope.WarehouseScreen(
    warehouseItems: List<WarehouseItem>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val inventoryPreferences = remember {
        context.getSharedPreferences("cubus_inventory", Context.MODE_PRIVATE)
    }
    val gson = remember { Gson() }
    val journalType = remember {
        object : TypeToken<List<InventoryJournalEntry>>() {}.type
    }
    var inventoryMode by rememberSaveable { mutableStateOf(false) }
    var inventoryValues by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var showJournal by rememberSaveable { mutableStateOf(false) }
    var journal by remember {
        mutableStateOf<List<InventoryJournalEntry>>(
            runCatching {
                gson.fromJson<List<InventoryJournalEntry>>(
                    inventoryPreferences.getString("journal", "[]"),
                    journalType,
                )
            }.getOrNull() ?: emptyList(),
        )
    }
    var pendingInventoryWorkbook by remember { mutableStateOf<ByteArray?>(null) }
    val exportInventoryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        ),
    ) { uri ->
        val workbook = pendingInventoryWorkbook
        if (uri != null && workbook != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(workbook)
                } ?: error("Не удалось открыть выбранный файл")
            }.onSuccess {
                Toast.makeText(context, "Журнал сохранён в Excel", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(context, "Не удалось сохранить журнал", Toast.LENGTH_LONG).show()
            }
        }
        pendingInventoryWorkbook = null
    }
    Box(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        BackIconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            "Склад метролога",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 60.dp),
        )
    }
    when {
        loading -> Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        error != null -> Card(Modifier.fillMaxWidth()) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
        warehouseItems.isEmpty() -> Card(Modifier.fillMaxWidth()) {
            Text(
                "На складе метролога нет позиций",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
        else -> LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "НАИМЕНОВАНИЕ",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (inventoryMode) "Факт" else "Кол-во",
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(76.dp),
                    )
                }
            }
            items(warehouseItems.sortedWith(warehouseItemComparator), key = { it.id }) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Card(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        containerColor = WarehouseBlockColor,
                        border = BorderStroke(1.dp, CubusButtonColor),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                item.name.ifBlank { "Позиция № ${item.id}" },
                                fontSize = warehouseNameFontSize(item.name),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                            )
                        }
                    }
                    Card(
                        modifier = Modifier.width(76.dp).fillMaxHeight(),
                        containerColor = WarehouseBlockColor,
                        border = BorderStroke(1.dp, CubusButtonColor),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (inventoryMode) {
                                BasicTextField(
                                    value = inventoryValues[item.id].orEmpty(),
                                    onValueChange = { value ->
                                        inventoryValues = inventoryValues + (
                                            item.id to value.filter { it.isDigit() || it == ',' || it == '.' || it == '-' }
                                        )
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.titleMedium.copy(
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                Text(
                                    item.balance.ifBlank { "0" },
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = !loading,
        onClick = onRefresh,
    ) { Text(if (loading) "Загружаем…" else "Обновить склад") }
    if (inventoryMode) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val differences = warehouseItems.mapNotNull { item ->
                    val expected = item.balance.replace(" ", "").replace(',', '.').toDoubleOrNull() ?: 0.0
                    val actual = inventoryValues[item.id]?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                    val difference = actual - expected
                    if (kotlin.math.abs(difference) < 0.0001) null else {
                        InventoryDifference(
                            name = item.name,
                            warehouse = formatInventoryNumber(expected),
                            metrologist = formatInventoryNumber(actual),
                            difference = formatInventoryNumber(difference),
                        )
                    }
                }
                val record = InventoryJournalEntry(
                    date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru")).format(Date()),
                    rows = differences,
                )
                journal = listOf(record) + journal
                inventoryPreferences.edit().putString("journal", gson.toJson(journal)).apply()
                inventoryMode = false
                showJournal = true
            },
        ) { Text("Свести склад") }
        TextButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { inventoryMode = false },
        ) { Text("Отменить инвентаризацию") }
    } else {
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = warehouseItems.isNotEmpty(),
            onClick = {
                inventoryValues = warehouseItems.associate { it.id to "" }
                inventoryMode = true
            },
        ) { Text("Пройти инвентаризацию") }
    }
    TextButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = { showJournal = true },
    ) { Text("Журнал инвентаризаций (${journal.size})") }
    if (showJournal) {
        AlertDialog(
            onDismissRequest = { showJournal = false },
            title = { Text("Журнал инвентаризаций") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().height(360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (journal.isEmpty()) Text("Инвентаризации ещё не проводились")
                    journal.forEach { record ->
                        val rows = inventoryRows(record)
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(6.dp)) {
                                Text(
                                    record.date,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp),
                                )
                                if (rows.isEmpty()) {
                                    Text("Расхождений нет", modifier = Modifier.padding(6.dp))
                                } else {
                                    InventoryTableHeader()
                                    rows.forEachIndexed { index, row ->
                                        HorizontalDivider()
                                        InventoryTableRow(index + 1, row)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = journal.isNotEmpty(),
                        onClick = {
                            runCatching { buildInventoryJournalXlsx(journal) }
                                .onSuccess { workbook ->
                                    pendingInventoryWorkbook = workbook
                                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                    exportInventoryLauncher.launch(
                                        "Журнал_инвентаризаций_$date.xlsx",
                                    )
                                }
                                .onFailure {
                                    Toast.makeText(
                                        context,
                                        "Не удалось сформировать Excel",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                        },
                    ) { Text("Выгрузить в Excel") }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showJournal = false },
                    ) { Text("Закрыть") }
                }
            },
        )
    }
}

private data class InventoryJournalEntry(
    val date: String,
    val differences: List<String>? = null,
    val rows: List<InventoryDifference>? = null,
)

private data class InventoryDifference(
    val name: String,
    val warehouse: String,
    val metrologist: String,
    val difference: String,
)

private fun inventoryRows(record: InventoryJournalEntry): List<InventoryDifference> {
    val structured = record.rows.orEmpty()
    if (structured.isNotEmpty()) return structured
    val pattern = Regex("^(.*): КБ ([^,]+), факт ([^,]+), расхождение (.+)$")
    return record.differences.orEmpty().mapNotNull { oldValue ->
        val match = pattern.matchEntire(oldValue) ?: return@mapNotNull null
        InventoryDifference(
            name = match.groupValues[1],
            warehouse = match.groupValues[2],
            metrologist = match.groupValues[3],
            difference = match.groupValues[4],
        )
    }
}

private fun buildInventoryJournalXlsx(journal: List<InventoryJournalEntry>): ByteArray {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output, StandardCharsets.UTF_8).use { zip ->
        fun entry(path: String, content: String) {
            zip.putNextEntry(ZipEntry(path))
            zip.write(content.toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }

        entry(
            "[Content_Types].xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                </Types>""".trimIndent(),
        )
        entry(
            "_rels/.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>""".trimIndent(),
        )
        entry(
            "xl/workbook.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets><sheet name="Журнал инвентаризаций" sheetId="1" r:id="rId1"/></sheets>
                </workbook>""".trimIndent(),
        )
        entry(
            "xl/_rels/workbook.xml.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                </Relationships>""".trimIndent(),
        )
        entry(
            "xl/styles.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <numFmts count="2">
                    <numFmt numFmtId="164" formatCode="dd.mm.yyyy hh:mm"/>
                    <numFmt numFmtId="165" formatCode="0.##"/>
                  </numFmts>
                  <fonts count="3">
                    <font><sz val="11"/><name val="Arial"/></font>
                    <font><b/><color rgb="FFFFFFFF"/><sz val="14"/><name val="Arial"/></font>
                    <font><b/><color rgb="FFFFFFFF"/><sz val="11"/><name val="Arial"/></font>
                  </fonts>
                  <fills count="3">
                    <fill><patternFill patternType="none"/></fill>
                    <fill><patternFill patternType="gray125"/></fill>
                    <fill><patternFill patternType="solid"><fgColor rgb="FF2B5A78"/><bgColor indexed="64"/></patternFill></fill>
                  </fills>
                  <borders count="2">
                    <border><left/><right/><top/><bottom/><diagonal/></border>
                    <border><left style="thin"><color rgb="FFB8C8D2"/></left><right style="thin"><color rgb="FFB8C8D2"/></right><top style="thin"><color rgb="FFB8C8D2"/></top><bottom style="thin"><color rgb="FFB8C8D2"/></bottom><diagonal/></border>
                  </borders>
                  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                  <cellXfs count="6">
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
                    <xf numFmtId="0" fontId="2" fillId="2" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
                    <xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
                    <xf numFmtId="165" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>
                  </cellXfs>
                  <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
                </styleSheet>""".trimIndent(),
        )

        val rows = buildString {
            append("<row r=\"1\" ht=\"26\" customHeight=\"1\">")
            append(inlineInventoryCell("A1", "Журнал инвентаризаций", 1))
            append("</row>")
            append("<row r=\"2\" ht=\"34\" customHeight=\"1\">")
            listOf("Дата", "№", "Наименование", "По данным КБ", "Фактически", "Расхождение")
                .forEachIndexed { index, value ->
                    append(inlineInventoryCell("${('A'.code + index).toChar()}2", value, 2))
                }
            append("</row>")
            var sheetRow = 3
            journal.forEach { record ->
                val differences = inventoryRows(record)
                if (differences.isEmpty()) {
                    append("<row r=\"$sheetRow\">")
                    append(dateInventoryCell("A$sheetRow", record.date))
                    append(inlineInventoryCell("C$sheetRow", "Расхождений нет", 5))
                    append("</row>")
                    sheetRow++
                } else {
                    differences.forEachIndexed { index, difference ->
                        append("<row r=\"$sheetRow\">")
                        append(dateInventoryCell("A$sheetRow", record.date))
                        append(numberInventoryCell("B$sheetRow", (index + 1).toString()))
                        append(inlineInventoryCell("C$sheetRow", difference.name, 5))
                        append(numberInventoryCell("D$sheetRow", difference.warehouse))
                        append(numberInventoryCell("E$sheetRow", difference.metrologist))
                        append(numberInventoryCell("F$sheetRow", difference.difference))
                        append("</row>")
                        sheetRow++
                    }
                }
            }
        }
        val lastRow = 2 + journal.sumOf { maxOf(1, inventoryRows(it).size) }
        entry(
            "xl/worksheets/sheet1.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <dimension ref="A1:F$lastRow"/>
                  <sheetViews><sheetView workbookViewId="0" showGridLines="0"><pane ySplit="2" topLeftCell="A3" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>
                  <sheetFormatPr defaultRowHeight="18"/>
                  <cols><col min="1" max="1" width="19" customWidth="1"/><col min="2" max="2" width="7" customWidth="1"/><col min="3" max="3" width="48" customWidth="1"/><col min="4" max="6" width="16" customWidth="1"/></cols>
                  <sheetData>$rows</sheetData>
                  <autoFilter ref="A2:F$lastRow"/>
                  <mergeCells count="1"><mergeCell ref="A1:F1"/></mergeCells>
                  <pageMargins left="0.35" right="0.35" top="0.5" bottom="0.5" header="0.2" footer="0.2"/>
                  <pageSetup orientation="landscape" fitToWidth="1" fitToHeight="0"/>
                </worksheet>""".trimIndent(),
        )
    }
    return output.toByteArray()
}

private fun inlineInventoryCell(reference: String, value: String, style: Int): String =
    "<c r=\"$reference\" s=\"$style\" t=\"inlineStr\"><is><t>${escapeInventoryXml(value)}</t></is></c>"

private fun numberInventoryCell(reference: String, value: String): String {
    val number = value.replace(" ", "").replace(',', '.').toDoubleOrNull()
    return if (number == null) {
        inlineInventoryCell(reference, value, 5)
    } else {
        "<c r=\"$reference\" s=\"4\"><v>$number</v></c>"
    }
}

private fun dateInventoryCell(reference: String, value: String): String {
    val serial = runCatching {
        val date = LocalDateTime.parse(value, DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        val base = java.time.LocalDate.of(1899, 12, 30)
        java.time.temporal.ChronoUnit.DAYS.between(base, date.toLocalDate()).toDouble() +
            date.toLocalTime().toSecondOfDay() / 86400.0
    }.getOrNull()
    return if (serial == null) {
        inlineInventoryCell(reference, value, 5)
    } else {
        "<c r=\"$reference\" s=\"3\"><v>$serial</v></c>"
    }
}

private fun escapeInventoryXml(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

@Composable
private fun InventoryTableHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xFFD5E5ED)).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InventoryTableCell("№", 0.22f, true, true)
        InventoryTableCell("Название", 1.35f, true)
        InventoryTableCell("На складе", 0.52f, true, true)
        InventoryTableCell("У метролога", 0.58f, true, true)
        InventoryTableCell("Разница", 0.48f, true, true)
    }
}

@Composable
private fun InventoryTableRow(number: Int, row: InventoryDifference) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InventoryTableCell(number.toString(), 0.22f, centered = true)
        InventoryTableCell(row.name, 1.35f)
        InventoryTableCell(row.warehouse, 0.52f, centered = true)
        InventoryTableCell(row.metrologist, 0.58f, centered = true)
        InventoryTableCell(row.difference, 0.48f, centered = true)
    }
}

@Composable
private fun RowScope.InventoryTableCell(
    value: String,
    weight: Float,
    header: Boolean = false,
    centered: Boolean = false,
) {
    Text(
        value,
        fontSize = if (header) 9.sp else 10.sp,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        maxLines = if (header) 2 else 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(weight).padding(horizontal = 2.dp),
    )
}

private fun formatInventoryNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(Locale.US, value)

@Composable
private fun ColumnScope.ServiceScreen(
    priceListCount: Int,
    meterCatalogCount: Int,
    refreshing: String?,
    onRefreshCatalogs: () -> Unit,
    onLoadPriceList: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
    Box(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        BackIconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart))
        Text(
            "Сервис",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 60.dp),
        )
    }
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Справочник ИПУ", style = MaterialTheme.typography.titleMedium)
            Text("Сохранено записей: $meterCatalogCount")
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = refreshing == null,
                onClick = onRefreshCatalogs,
            ) {
                Text(if (refreshing == "catalogs") "Обновляем…" else "Обновить справочники")
            }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Прайс-лист", style = MaterialTheme.typography.titleMedium)
            Text("Сохранено позиций: $priceListCount")
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = refreshing == null,
                onClick = onLoadPriceList,
            ) {
                Text(if (refreshing == "price-list") "Загружаем…" else "Загрузить Прайс Лист")
            }
        }
    }
    AutomaticUpdateCard(
        title = "Обновление графика работы",
        preferenceKey = "work_schedule",
    )
    AutomaticUpdateCard(
        title = "Обновление склада",
        preferenceKey = "warehouse",
    )
    Text(
        "Данные сохраняются в локальной базе приложения и используются без повторного обращения к КБ.",
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    }
}

@Composable
private fun AutomaticUpdateCard(
    title: String,
    preferenceKey: String,
) {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences("cubus_automatic_updates", Context.MODE_PRIVATE)
    }
    var selectedDays by rememberSaveable(preferenceKey) {
        mutableStateOf(
            preferences.getString("${preferenceKey}_days", "2,3,4,5,6")
                .orEmpty().split(',').mapNotNull(String::toIntOrNull).toSet(),
        )
    }
    var updateTime by rememberSaveable(preferenceKey) {
        mutableStateOf(preferences.getString("${preferenceKey}_time", "08:00").orEmpty())
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf(
                    Calendar.MONDAY to "Пн", Calendar.TUESDAY to "Вт",
                    Calendar.WEDNESDAY to "Ср", Calendar.THURSDAY to "Чт",
                    Calendar.FRIDAY to "Пт", Calendar.SATURDAY to "Сб",
                    Calendar.SUNDAY to "Вс",
                ).forEach { (day, label) ->
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = day in selectedDays,
                        onClick = {
                            selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day
                        },
                        label = { Text(label, fontSize = 11.sp) },
                    )
                }
            }
            OutlinedTextField(
                value = updateTime,
                onValueChange = { value -> updateTime = value.filter { it.isDigit() || it == ':' }.take(5) },
                label = { Text("Время обновления, ЧЧ:ММ") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = updateTime.matches(Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")) && selectedDays.isNotEmpty(),
                onClick = {
                    preferences.edit()
                        .putString("${preferenceKey}_days", selectedDays.sorted().joinToString(","))
                        .putString("${preferenceKey}_time", updateTime)
                        .apply()
                },
            ) { Text("Сохранить расписание") }
        }
    }
}

@Composable
private fun ColumnScope.ReportScreen(
    report: PeriodReport?,
    loading: Boolean,
    error: String?,
    onGenerate: (String, String) -> Unit,
) {
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var dateFrom by rememberSaveable { mutableStateOf(today) }
    var dateTo by rememberSaveable { mutableStateOf(today) }
    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Период отчёта", style = MaterialTheme.typography.titleMedium)
                WizardDateField("С даты", dateFrom) { dateFrom = it }
                WizardDateField("По дату", dateTo) { dateTo = it }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading && dateFrom <= dateTo,
                    onClick = { onGenerate(dateFrom, dateTo) },
                ) { Text(if (loading) "Формируем…" else "Сформировать отчёт") }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        report?.let { value ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Финансовый отчёт", style = MaterialTheme.typography.titleMedium)
                    InformationLine("Выполнено заявок", value.applications_count.toString())
                    InformationLine("Общая сумма", value.total)
                    InformationLine("Наличные", value.cash)
                    InformationLine("Эквайринг", value.card)
                    HorizontalDivider()
                    InformationLine(
                        "Административные расходы",
                        value.administrative_expenses_status,
                    )
                    InformationLine("Начислено метрологу", value.metrologist_gross)
                    InformationLine("Комиссия банка 5%", value.bank_commission)
                    InformationLine(
                        "Административные расходы 15%",
                        value.administrative_expenses,
                    )
                    InformationLine("Итого метрологу", value.metrologist_net)
                    InformationLine("Итого компании", value.company)
                }
            }
            ReportLinesCard("Предоставленные услуги", value.services, showPayout = true)
            ReportLinesCard("Расход материалов", value.materials, showPayout = false)
        }
    }
}

@Composable
private fun ReportLinesCard(
    title: String,
    lines: List<ru.zilisnik.mobile.data.ReportLine>,
    showPayout: Boolean,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (lines.isEmpty()) Text("Нет данных за выбранный период")
            lines.forEachIndexed { index, line ->
                Text("${index + 1}. ${line.name}")
                Text(
                    if (showPayout) {
                        "Кол-во: ${line.quantity}  |  Цена: ${line.unit_price}  |  Сумма: ${line.total}"
                    } else {
                        "Кол-во: ${line.quantity}  |  Сумма: ${line.total}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (showPayout) {
                    Text(
                        "Метрологу начислено: ${line.metrologist_gross}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Комиссия банка: −${line.bank_commission}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (line.administrative_expenses != "0" && line.administrative_expenses != "0.00") {
                        Text(
                            "Административные расходы 15%: −${line.administrative_expenses}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        "Итого метрологу: ${line.metrologist_net}  |  Компании: ${line.company}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (index < lines.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ColumnScope.DocumentationScreen(
    documents: List<DocumentationItem>,
    loading: Boolean,
    error: String?,
    content: DocumentationContent?,
    onAdd: (DocumentationCreate) -> Unit,
    onReplace: ((Long, DocumentationCreate) -> Unit)? = null,
    onDelete: (Long) -> Unit,
    onLoadContent: (Long) -> Unit,
    onContentHandled: () -> Unit,
) {
    val context = LocalContext.current
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var comment by rememberSaveable { mutableStateOf("") }
    var filename by rememberSaveable { mutableStateOf("") }
    var mimeType by rememberSaveable { mutableStateOf("") }
    var fileContent by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf("") }
    var pendingDownload by remember { mutableStateOf<DocumentationContent?>(null) }
    var preview by remember { mutableStateOf<ApplicationPhoto?>(null) }
    var expandedDocumentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var replacementId by rememberSaveable { mutableStateOf<Long?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selected ->
            readImageContent(context, selected)?.let { (name, encoded) ->
                val reportedMime = context.contentResolver.getType(selected).orEmpty()
                val selectedMime = when {
                    reportedMime == "application/pdf" || name.endsWith(".pdf", true) -> "application/pdf"
                    reportedMime.startsWith("image/jpeg") || name.endsWith(".jpg", true) ||
                        name.endsWith(".jpeg", true) -> "image/jpeg"
                    else -> ""
                }
                if (selectedMime.isNotBlank()) {
                    filename = name
                    mimeType = selectedMime
                    fileContent = encoded
                }
            }
        }
    }
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val downloaded = pendingDownload
        if (uri != null && downloaded != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(Base64.decode(downloaded.content_base64, Base64.DEFAULT))
                }
            }
        }
        pendingDownload = null
        pendingAction = ""
        onContentHandled()
    }
    LaunchedEffect(content) {
        val loaded = content ?: return@LaunchedEffect
        if (pendingAction == "view") {
            if (loaded.mime_type.startsWith("image/")) {
                preview = ApplicationPhoto(
                    field = "document",
                    title = loaded.filename,
                    filename = loaded.filename,
                    content_base64 = loaded.content_base64,
                )
            } else {
                val directory = java.io.File(context.cacheDir, "documents").apply { mkdirs() }
                val safeName = loaded.filename.replace(Regex("[^0-9A-Za-zА-Яа-я._-]"), "_")
                val file = java.io.File(directory, safeName)
                file.writeBytes(Base64.decode(loaded.content_base64, Base64.DEFAULT))
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, loaded.mime_type)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { context.startActivity(intent) }
            }
            pendingAction = ""
            onContentHandled()
        } else {
            pendingDownload = loaded
            saveLauncher.launch(loaded.filename)
        }
    }
    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showEditor = !showEditor },
        ) { Text(if (showEditor) "Закрыть добавление" else "Добавить") }
        if (showEditor) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Название документа") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { filePicker.launch("*/*") },
                    ) { Text(if (filename.isBlank()) "Прикрепить PDF или JPEG" else filename) }
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text("Комментарий") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = title.isNotBlank() && fileContent.isNotBlank() && !loading,
                        onClick = {
                            val request = DocumentationCreate(title, comment, filename, mimeType, fileContent)
                            replacementId?.let { id -> onReplace?.invoke(id, request) } ?: onAdd(request)
                            title = ""; comment = ""; filename = ""; mimeType = ""; fileContent = ""
                            replacementId = null
                            showEditor = false
                        },
                    ) { Text("Сохранить") }
                }
            }
        }
        if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!loading && documents.isEmpty()) Text("Документы ещё не добавлены")
        documents.forEach { document ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        document.title,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    if (document.comment.isNotBlank()) Text(document.comment)
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            expandedDocumentId = document.id
                            pendingAction = "view"
                            onLoadContent(document.id)
                        },
                    ) { Text("Посмотреть") }
                    if (expandedDocumentId == document.id) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    pendingAction = "download"
                                    onLoadContent(document.id)
                                },
                            ) { Text("Скачать") }
                            if (onReplace != null) {
                                Button(
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        replacementId = document.id
                                        title = document.title
                                        comment = document.comment
                                        showEditor = true
                                    },
                                ) { Text("Заменить") }
                            }
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    expandedDocumentId = null
                                    onDelete(document.id)
                                },
                            ) { Text("Удалить") }
                        }
                    }
                }
            }
        }
    }
    preview?.let { PhotoPreview(it) { preview = null } }
}

@Composable
private fun DrawerItem(
    title: String,
    target: AppSection,
    selected: AppSection,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
        containerColor = WarehouseBlockColor,
        border = BorderStroke(1.dp, CubusButtonColor),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                title,
                color = CubusButtonColor,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun ColumnScope.DirectorySectionScreen(
    entries: List<String>,
    emptyText: String,
) {
    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (entries.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Text(emptyText, modifier = Modifier.padding(16.dp))
            }
        } else {
            entries.forEachIndexed { index, value ->
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "${index + 1}. $value",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(onLogin: (String, String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppHeading("Вход в CUBUS")
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Электронная почта") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = email.contains('@') && password.length >= 8,
            onClick = { onLogin(email, password) },
        ) { Text("Войти") }
    }
}

@Composable
private fun ChangePasswordScreen(
    loading: Boolean,
    error: String?,
    onChangePassword: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.cubus_logo),
            contentDescription = "CUBUS",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).aspectRatio(702f / 389f),
        )
        AppHeading("Придумайте новый пароль")
        Text(
            "Временный пароль использован. Новый пароль должен содержать не менее 10 символов.",
            textAlign = TextAlign.Center,
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Новый пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = confirmation,
            onValueChange = { confirmation = it },
            label = { Text("Повторите пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading && password.length >= 10 && confirmation.length >= 10,
            onClick = { onChangePassword(password, confirmation) },
        ) { Text(if (loading) "Сохраняем…" else "Сохранить пароль") }
    }
}

private fun isAdministrator(role: String): Boolean =
    role.trim().lowercase(Locale("ru")) in setOf("администратор", "admin")

@Composable
private fun ColumnScope.AdminUsersScreen(
    users: List<AdminUser>,
    loading: Boolean,
    error: String?,
    temporaryPassword: String?,
    onRefresh: () -> Unit,
    onCreate: (String, String, String) -> Unit,
    onSetActive: (String, Boolean) -> Unit,
    onResetPassword: (String) -> Unit,
    onControl: (String) -> Unit,
    onTemporaryPasswordHandled: () -> Unit,
) {
    var showCreate by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = { showCreate = true },
    ) { Text("Добавить пользователя") }
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = !loading,
        onClick = onRefresh,
    ) { Text(if (loading) "Обновляем…" else "Обновить список") }
    error?.let {
        Text(
            it,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (loading && users.isEmpty()) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            items(users, key = { it.email }) { user ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = if (user.active) WarehouseBlockColor else Color(0xFFF1F1F1),
                    border = BorderStroke(1.dp, if (user.active) CubusButtonColor else Color.Gray),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(user.email, style = MaterialTheme.typography.titleMedium)
                        Text("Логин КБ: ${user.login}")
                        Text("Роль: ${user.role}")
                        Text(
                            when {
                                !user.active -> "Заблокирован"
                                user.must_change_password -> "Ожидает смены временного пароля"
                                else -> "Доступ разрешён"
                            },
                            color = if (user.active) CubusButtonColor else MaterialTheme.colorScheme.error,
                        )
                        user.last_login_at?.let { Text("Последний вход: ${formatAdminTimestamp(it)}") }
                        if (user.role.lowercase(Locale("ru")) in setOf("метролог", "администратор")) {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = user.active,
                                onClick = { onControl(user.email) },
                            ) { Text("Открыть данные сотрудника") }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(
                                modifier = Modifier.weight(1f),
                                onClick = { onResetPassword(user.email) },
                            ) { Text("Сбросить пароль") }
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { onSetActive(user.email, !user.active) },
                            ) { Text(if (user.active) "Заблокировать" else "Разблокировать") }
                        }
                    }
                }
            }
        }
    }
    if (showCreate) {
        CreateAdminUserDialog(
            onDismiss = { showCreate = false },
            onCreate = { email, login, role ->
                showCreate = false
                onCreate(email, login, role)
            },
        )
    }
    temporaryPassword?.let { password ->
        AlertDialog(
            onDismissRequest = onTemporaryPasswordHandled,
            title = { Text("Временный пароль") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Передайте этот пароль сотруднику. После первого входа CUBUS потребует заменить его.")
                    Text(password, style = MaterialTheme.typography.headlineSmall)
                }
            },
            confirmButton = {
                Button(onClick = {
                    clipboard.setText(AnnotatedString(password))
                    onTemporaryPasswordHandled()
                }) { Text("Скопировать") }
            },
            dismissButton = {
                TextButton(onClick = onTemporaryPasswordHandled) { Text("Закрыть") }
            },
        )
    }
}

@Composable
private fun ColumnScope.AdminEmployeeWorkspaceScreen(
    workspace: AdminEmployeeWorkspace,
    loading: Boolean,
    error: String?,
    content: DocumentationContent?,
    applicationDetails: ApplicationDetails?,
    onClose: () -> Unit,
    onReload: (String, String, String) -> Unit,
    onAddDocument: (String, DocumentationCreate) -> Unit,
    onReplaceDocument: (String, Long, DocumentationCreate) -> Unit,
    onDeleteDocument: (String, Long) -> Unit,
    onLoadDocument: (String, Long) -> Unit,
    onContentHandled: () -> Unit,
    onOpenApplication: (String, Long) -> Unit,
    onApplicationHandled: () -> Unit,
) {
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var dateFrom by rememberSaveable(workspace.user.email) { mutableStateOf(today) }
    var dateTo by rememberSaveable(workspace.user.email) { mutableStateOf(today) }
    var tab by rememberSaveable(workspace.user.email) { mutableStateOf("Отчёт") }
    Button(modifier = Modifier.fillMaxWidth(), onClick = onClose) {
        Text("← К списку сотрудников")
    }
    Card(Modifier.fillMaxWidth(), containerColor = CubusBlockColor) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(workspace.user.email, style = MaterialTheme.typography.titleMedium)
            Text("Сотрудник КБ: ${workspace.user.login}")
            Text("Роль: ${workspace.user.role}")
            Text("Вы работаете как администратор. Все изменения записываются в историю.")
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("Отчёт", "Склад", "Заявки", "Документы", "История").forEach { item ->
            FilterChip(
                modifier = Modifier.weight(1f),
                selected = tab == item,
                onClick = { tab = item },
                label = { Text(item.take(4), fontSize = 10.sp) },
            )
        }
    }
    if (tab == "Отчёт") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { WizardDateField("С даты", dateFrom) { dateFrom = it } }
            Box(Modifier.weight(1f)) { WizardDateField("По дату", dateTo) { dateTo = it } }
        }
        Button(
            modifier = Modifier.fillMaxWidth(), enabled = !loading && dateFrom <= dateTo,
            onClick = { onReload(workspace.user.email, dateFrom, dateTo) },
        ) { Text(if (loading) "Загружаем…" else "Сформировать") }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    when (tab) {
        "Отчёт" -> LazyColumn(
            modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        val report = workspace.report
                        InformationLine("Выполнено заявок", report.applications_count.toString())
                        InformationLine("Общая сумма", report.total)
                        InformationLine("Наличные", report.cash)
                        InformationLine("Эквайринг", report.card)
                        InformationLine("Итого метрологу", report.metrologist_net)
                        InformationLine("Итого компании", report.company)
                    }
                }
            }
            item { ReportLinesCard("Предоставленные услуги", workspace.report.services, true) }
            item { ReportLinesCard("Расход материалов", workspace.report.materials, false) }
        }
        "Склад" -> LazyColumn(
            modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (workspace.warehouse.isEmpty()) item { Text("На складе нет позиций") }
            items(workspace.warehouse, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth(), containerColor = WarehouseBlockColor) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        InformationLine("Приход", item.incoming)
                        InformationLine("Расход", item.outgoing)
                        InformationLine("Остаток", item.balance)
                        InformationLine("Всего списано", item.total_written_off)
                    }
                }
            }
        }
        "Заявки" -> LazyColumn(
            modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (workspace.applications.isEmpty()) item { Text("Заявки сотрудника не найдены") }
            items(workspace.applications, key = { it.id }) { application ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Заявка № ${application.number}", style = MaterialTheme.typography.titleMedium)
                        Text(application.status)
                        Text(application.address)
                        if (application.client.isNotBlank()) Text(application.client)
                        val phones = listOf(application.phone_number, application.phone_number_2)
                            .filter(String::isNotBlank).joinToString(" | ")
                        if (phones.isNotBlank()) Text(phones)
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onOpenApplication(workspace.user.email, application.id) },
                        ) { Text("Открыть заявку") }
                    }
                }
            }
        }
        "Документы" -> DocumentationScreen(
            documents = workspace.documents,
            loading = loading,
            error = error,
            content = content,
            onAdd = { onAddDocument(workspace.user.email, it) },
            onReplace = { id, request -> onReplaceDocument(workspace.user.email, id, request) },
            onDelete = { onDeleteDocument(workspace.user.email, it) },
            onLoadContent = { onLoadDocument(workspace.user.email, it) },
            onContentHandled = onContentHandled,
        )
        else -> LazyColumn(
            modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (workspace.history.isEmpty()) item { Text("История пока пуста") }
            items(workspace.history, key = { it.id }) { entry -> ActivityHistoryCard(entry) }
        }
    }
    applicationDetails?.let { details ->
        AlertDialog(
            onDismissRequest = onApplicationHandled,
            title = { Text("Заявка № ${details.number}") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    InformationLine("Статус", details.status)
                    InformationLine("Адрес", details.address)
                    InformationLine("Клиент", details.client)
                    InformationLine("Телефон", details.phone_number)
                    InformationLine("Доп. телефон", details.phone_number_2)
                    InformationLine("Интервал", details.interval)
                    InformationLine("Этаж", details.floor)
                    InformationLine("Подъезд", details.entrance)
                    InformationLine("Код домофона", details.entrance_code)
                    if (details.comments.isNotBlank()) InformationLine("Комментарий", details.comments)
                    if (details.metrolog_comments.isNotBlank()) {
                        InformationLine("Комментарий метролога", details.metrolog_comments)
                    }
                    HorizontalDivider()
                    Text("Приборы учёта: ${details.water_meters.size}")
                    details.water_meters.forEachIndexed { index, meter ->
                        Text("${index + 1}. ${meter.device_kind} ${meter.meter_type} № ${meter.serial_number}")
                    }
                    Text("Фотографии и документы заявки: ${details.photos.size}")
                }
            },
            confirmButton = {
                Button(onClick = onApplicationHandled) { Text("Закрыть") }
            },
        )
    }
}

@Composable
private fun ActivityHistoryCard(entry: ActivityLogItem) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.action, style = MaterialTheme.typography.titleMedium)
            Text("Автор: ${entry.actor_email.ifBlank { entry.actor_login }}")
            if (entry.details.isNotBlank()) Text(entry.details)
            Text(formatAdminTimestamp(entry.created_at), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ColumnScope.RegistryScreen(
    addresses: List<RegistryAddress>,
    stats: RegistryStats?,
    loading: Boolean,
    error: String?,
    onSearch: (String) -> Unit,
    onSync: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    stats?.let {
        Card(Modifier.fillMaxWidth(), containerColor = CubusBlockColor) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Централизованная база", style = MaterialTheme.typography.titleMedium)
                Text("Адресов: ${it.addresses}  |  Клиентов: ${it.clients}")
                Text("Телефонов: ${it.phones}  |  Приборов: ${it.meters}")
                Text("Связанных заявок: ${it.applications}")
            }
        }
    }
    OutlinedTextField(
        value = query, onValueChange = { query = it }, label = { Text("Поиск по адресу") },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(modifier = Modifier.weight(1f), enabled = !loading, onClick = { onSearch(query) }) {
            Text("Найти")
        }
        Button(modifier = Modifier.weight(1f), enabled = !loading, onClick = onSync) {
            Text(if (loading) "Обновляем…" else "Собрать данные")
        }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    LazyColumn(
        modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!loading && addresses.isEmpty()) item { Text("Адреса ещё не собраны") }
        items(addresses, key = { it.id }) { address ->
            var expanded by rememberSaveable(address.id) { mutableStateOf(false) }
            Card(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                containerColor = WarehouseBlockColor,
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(address.address, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Клиентов: ${address.clients.size} | Приборов: ${address.meters.size} | " +
                            "Заявок: ${address.applications_count}",
                    )
                    if (expanded) {
                        HorizontalDivider()
                        address.clients.forEach { client ->
                            Text("Клиент: ${client.name}")
                            client.phones.forEach { Text("Телефон: ${it.phone}") }
                        }
                        address.meters.forEachIndexed { index, meter ->
                            HorizontalDivider()
                            Text("Прибор ${index + 1}: ${meter.device_kind} ${meter.meter_type}".trim())
                            if (meter.serial_number.isNotBlank()) Text("Заводской №: ${meter.serial_number}")
                            if (meter.registry_number.isNotBlank()) Text("№ Госреестра: ${meter.registry_number}")
                            if (meter.next_check.isNotBlank()) Text("Следующая поверка: ${meter.next_check}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateAdminUserDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var login by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf("Метролог") }
    var roleMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый пользователь") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Электронная почта") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    label = { Text("Логин сотрудника в КБ") },
                    singleLine = true,
                )
                Box {
                    Button(onClick = { roleMenu = true }) { Text("Роль: $role") }
                    DropdownMenu(expanded = roleMenu, onDismissRequest = { roleMenu = false }) {
                        listOf("Метролог", "Диспетчер", "Администратор").forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    role = option
                                    roleMenu = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = email.contains('@') && login.isNotBlank(),
                onClick = { onCreate(email.trim(), login.trim(), role) },
            ) { Text("Создать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun formatAdminTimestamp(value: String): String = value
    .replace('T', ' ')
    .substringBefore('.')
    .take(16)

@Composable
private fun ColumnScope.MainScreen(
    profile: UserProfile?,
    weather: WeatherSnapshot?,
    statusCounts: List<ApplicationStatusCount>,
    applications: List<ApplicationSummary>,
    dailyStatistics: List<DailyStatistics>,
    todayStatistics: TodayStatistics?,
    todayStatisticsHistory: List<TodayStatistics>,
    mapPoints: List<ApplicationMapPoint>,
    mapLoading: Boolean,
    mapError: String?,
    onOpen: (Long) -> Unit,
    onRefresh: () -> Unit,
    onReorderMapPoints: (Int, Int) -> Unit,
) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1_000)
        }
    }
    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WeatherDashboard(weather, now)
        AppHeading("Статистика на сегодня")
        StatusCard(
            "Новые",
            todayStatistics?.initial_new ?: statusCounts.countFor("Новая"),
            applications.filter { dashboardStatus(it.status) == "Новая" },
            onOpen,
        )
        StatusCard(
            "Выполненные",
            todayStatistics?.completed ?: statusCounts.countFor("Выполнено"),
            applications.filter { dashboardStatus(it.status) == "Выполнено" },
            onOpen,
        )
        StatusCard(
            "На доработке",
            todayStatistics?.rework ?: statusCounts.countFor("На Доработку"),
            applications.filter { dashboardStatus(it.status) == "На Доработку" },
            onOpen,
        )
        StatusCard(
            "Отложенные",
            todayStatistics?.postponed ?: statusCounts.countFor("Отложено"),
            applications.filter { dashboardStatus(it.status) == "Отложено" },
            onOpen,
        )
        StatusCard(
            "Отказы",
            todayStatistics?.refusals ?: statusCounts.countFor("Отказ"),
            applications.filter { dashboardStatus(it.status) == "Отказ" },
            onOpen,
        )
        TrackingSummary(todayStatistics)
        TrackingChecks(todayStatisticsHistory)
        Button(modifier = Modifier.fillMaxWidth(), onClick = onRefresh) {
            Text("Обновить данные на экране")
        }
        AppHeading("Заявки на карте")
        ApplicationMapCard(
            points = mapPoints,
            homeAddress = profile?.home_address.orEmpty(),
            homeLatitude = profile?.home_latitude,
            homeLongitude = profile?.home_longitude,
            loading = mapLoading,
            error = mapError,
            onOpen = onOpen,
        )
        if (mapPoints.isNotEmpty()) {
            RouteCardsSelector(
                points = mapPoints,
                onMove = onReorderMapPoints,
                onOpen = onOpen,
            )
        }
    }
}

@Composable
private fun WeatherDashboard(weather: WeatherSnapshot?, now: Date) {
    val time = remember(now) { SimpleDateFormat("HH:mm:ss", Locale("ru")).format(now) }
    val date = remember(now) { SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(now) }
    val temperature = weather?.temperature?.let {
        String.format(Locale("ru"), "%.1f °C", it)
    } ?: "—"
    val humidity = weather?.humidity?.let { "${it.roundToInt()} %" } ?: "—"
    val pressure = weather?.pressure_mm_hg?.let {
        String.format(Locale("ru"), "%.0f мм", it)
    } ?: "—"
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DashboardCell("Город", "г. Москва", Modifier.weight(1f))
            DashboardCell("Время (МСК)", time, Modifier.weight(1f))
            DashboardCell("Дата", date, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DashboardCell("Влажность", humidity, Modifier.weight(1f))
            DashboardCell("Температура", temperature, Modifier.weight(1f))
            DashboardCell("Давление", pressure, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DashboardCell(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(76.dp),
        containerColor = Color.White,
        border = BorderStroke(1.dp, CubusButtonColor),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ApplicationMapCard(
    points: List<ApplicationMapPoint>,
    homeAddress: String,
    homeLatitude: Double?,
    homeLongitude: Double?,
    loading: Boolean,
    error: String?,
    onOpen: (Long) -> Unit,
) {
    val hasHomePoint = homeLatitude != null && homeLongitude != null
    Card(
        modifier = Modifier.fillMaxWidth(),
        containerColor = WarehouseBlockColor,
        border = BorderStroke(1.dp, CubusButtonColor),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                loading && !hasHomePoint && points.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                !hasHomePoint && points.isEmpty() -> Text(
                    if (error.isNullOrBlank()) {
                        "Сначала сохраните домашний адрес метролога"
                    } else {
                        error
                    },
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                else -> {
                    ApplicationsMap(
                        points = points,
                        homeAddress = homeAddress,
                        homeLatitude = homeLatitude,
                        homeLongitude = homeLongitude,
                        onOpen = onOpen,
                    )
                    if (loading) {
                        Text("Добавляем заявки на карту…", style = MaterialTheme.typography.bodySmall)
                    } else if (!error.isNullOrBlank()) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                    } else if (points.isEmpty()) {
                        Text(
                            "Стартовая точка добавлена. Для заявок координаты пока не найдены.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteCardsSelector(
    points: List<ApplicationMapPoint>,
    onMove: (Int, Int) -> Unit,
    onOpen: (Long) -> Unit,
) {
    var commentPoint by remember { mutableStateOf<ApplicationMapPoint?>(null) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        containerColor = Color.White,
        border = BorderStroke(1.dp, CubusButtonColor),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Порядок маршрута", style = MaterialTheme.typography.titleMedium)
            Text(
                "Удерживайте заявку и перемещайте её вверх или вниз",
                style = MaterialTheme.typography.bodySmall,
            )
            points.forEachIndexed { index, point ->
                var dragOffset by remember(point.application_id) { mutableStateOf(0f) }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationY = dragOffset }
                        .pointerInput(point.application_id, points.size) {
                            detectDragGesturesAfterLongPress(
                                onDragEnd = {
                                    val rowHeight = 72.dp.toPx()
                                    val shift = (dragOffset / rowHeight).roundToInt()
                                    val target = (index + shift).coerceIn(points.indices)
                                    dragOffset = 0f
                                    if (target != index) onMove(index, target)
                                },
                                onDragCancel = { dragOffset = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount.y
                                },
                            )
                        }
                        .clickable { onOpen(point.application_id) },
                    containerColor = Color.White,
                    border = BorderStroke(1.dp, CubusButtonColor),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(CubusButtonColor, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                (index + 1).toString(),
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                point.address,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOfNotNull(
                                    point.interval.ifBlank { point.delivery_time }
                                        .takeIf { it.isNotBlank() },
                                    "№${point.number}",
                                ).joinToString("   "),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                            )
                            if (point.comments.isNotBlank()) {
                                Text(
                                    "Комментарий: ${point.comments}",
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        commentPoint = point
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CubusButtonColor,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Text("☰", color = CubusButtonColor, fontSize = 22.sp)
                    }
                }
            }
        }
    }
    commentPoint?.let { point ->
        AlertDialog(
            onDismissRequest = { commentPoint = null },
            title = { Text("Комментарий к заявке №${point.number}") },
            text = { Text(point.comments) },
            confirmButton = {
                Button(onClick = { commentPoint = null }) { Text("Закрыть") }
            },
        )
    }
}

@Composable
private fun ApplicationsMap(
    points: List<ApplicationMapPoint>,
    homeAddress: String,
    homeLatitude: Double?,
    homeLongitude: Double?,
    onOpen: (Long) -> Unit,
) {
    val context = LocalContext.current
    var routeRequest by remember { mutableStateOf<NativeMapLocation?>(null) }
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(260.dp),
        factory = { context -> NativeOsmMapView(context) },
        update = { mapView ->
            mapView.updateLocations(
                homeAddress = homeAddress,
                homeLatitude = homeLatitude,
                homeLongitude = homeLongitude,
                applications = points,
                onOpen = onOpen,
                onRoute = { routeRequest = it },
                onCall = { phone ->
                    val normalized = phone.filter { it.isDigit() || it == '+' }
                    if (normalized.isNotBlank()) {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$normalized")))
                    }
                },
            )
        },
    )
    routeRequest?.let { location ->
        AlertDialog(
            onDismissRequest = { routeRequest = null },
            title = { Text("Проложить маршрут?") },
            text = { Text(location.address) },
            confirmButton = {
                Button(onClick = {
                    val label = Uri.encode(location.address)
                    val uri = Uri.parse(
                        "geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}($label)",
                    )
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    routeRequest = null
                }) { Text("Да") }
            },
            dismissButton = {
                TextButton(onClick = { routeRequest = null }) { Text("Нет") }
            },
        )
    }
}

private data class NativeMapLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String,
    val applicationId: Long? = null,
    val isHome: Boolean = false,
    val applicationNumber: String = "",
    val interval: String = "",
    val address: String = "",
    val phoneNumber: String = "",
    val clientName: String = "",
)

private data class ProjectedPoint(val x: Double, val y: Double)

private class NativeOsmMapView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(43, 90, 120)
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val attributionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.DKGRAY
        textAlign = Paint.Align.RIGHT
        textSize = 10f * density
    }
    private var locations: List<NativeMapLocation> = emptyList()
    private var screenLocations: List<Pair<NativeMapLocation, ProjectedPoint>> = emptyList()
    private var onOpen: (Long) -> Unit = {}
    private var onRoute: (NativeMapLocation) -> Unit = {}
    private var onCall: (String) -> Unit = {}
    private var hintCloseArea: RectF? = null
    private var hintAddressArea: RectF? = null
    private var hintPhoneArea: RectF? = null
    private var zoomOffset = 0
    private var panX = 0.0
    private var panY = 0.0
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touchMoved = false
    private var scaleAccumulator = 1f
    private var selectedApplicationId: Long? = null
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleAccumulator *= detector.scaleFactor
                when {
                    scaleAccumulator > 1.18f -> {
                        changeZoom(1)
                        scaleAccumulator = 1f
                    }
                    scaleAccumulator < 0.85f -> {
                        changeZoom(-1)
                        scaleAccumulator = 1f
                    }
                }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                scaleAccumulator = 1f
            }
        },
    )

    fun updateLocations(
        homeAddress: String,
        homeLatitude: Double?,
        homeLongitude: Double?,
        applications: List<ApplicationMapPoint>,
        onOpen: (Long) -> Unit,
        onRoute: (NativeMapLocation) -> Unit,
        onCall: (String) -> Unit,
    ) {
        this.onOpen = onOpen
        this.onRoute = onRoute
        this.onCall = onCall
        val updated = buildList {
            if (homeLatitude != null && homeLongitude != null) {
                // The home address is only the route origin. It never receives
                // an application sequence number.
                add(
                    NativeMapLocation(
                        latitude = homeLatitude,
                        longitude = homeLongitude,
                        label = "",
                        isHome = true,
                    ),
                )
            }
            applications.forEachIndexed { index, point ->
                add(
                    NativeMapLocation(
                        point.latitude,
                        point.longitude,
                        (index + 1).toString(),
                        point.application_id,
                        applicationNumber = point.number,
                        interval = point.interval.ifBlank { point.delivery_time },
                        address = point.address,
                        phoneNumber = point.phone_number,
                        clientName = point.client,
                    ),
                )
            }
        }
        if (locations != updated) {
            val samePlaces = locations.map {
                Triple(it.applicationId, it.latitude, it.longitude)
            }.toSet() == updated.map {
                Triple(it.applicationId, it.latitude, it.longitude)
            }.toSet()
            locations = updated
            if (!samePlaces) {
                selectedApplicationId = null
                hintCloseArea = null
                hintAddressArea = null
                hintPhoneArea = null
                zoomOffset = 0
                panX = 0.0
                panY = 0.0
            }
            invalidate()
        }
        contentDescription = if (homeAddress.isBlank()) "Карта заявок" else "Старт: $homeAddress"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(android.graphics.Color.rgb(226, 237, 242))
        if (locations.isEmpty() || width == 0 || height == 0) return
        val zoom = (chooseZoom() + zoomOffset).coerceIn(3, 18)
        val projected = locations.map { project(it.latitude, it.longitude, zoom) }
        val centerX = (projected.minOf { it.x } + projected.maxOf { it.x }) / 2.0
        val centerY = (projected.minOf { it.y } + projected.maxOf { it.y }) / 2.0
        val originX = centerX - width / 2.0 - panX
        val originY = centerY - height / 2.0 - panY
        drawTiles(canvas, zoom, originX, originY)
        val rawScreen = projected.map { ProjectedPoint(it.x - originX, it.y - originY) }
        val screen = separateOverlappingMarkers(rawScreen)
        if (screen.size > 1) {
            val path = Path().apply {
                moveTo(screen.first().x.toFloat(), screen.first().y.toFloat())
                screen.drop(1).forEach { lineTo(it.x.toFloat(), it.y.toFloat()) }
            }
            canvas.drawPath(path, routePaint)
        }
        locations.zip(screen).forEach { (location, point) ->
            val isHome = location.isHome
            val radius = (if (isHome) 18f else 15f) * density
            markerPaint.style = Paint.Style.FILL
            markerPaint.color = if (isHome) {
                android.graphics.Color.rgb(43, 90, 120)
            } else {
                android.graphics.Color.rgb(226, 237, 242)
            }
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radius, markerPaint)
            markerPaint.style = Paint.Style.STROKE
            markerPaint.strokeWidth = 2f * density
            markerPaint.color = if (isHome) android.graphics.Color.WHITE else android.graphics.Color.rgb(43, 90, 120)
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radius, markerPaint)
            if (isHome) {
                drawHomeIcon(canvas, point.x.toFloat(), point.y.toFloat())
            } else {
                markerTextPaint.color = android.graphics.Color.rgb(43, 90, 120)
                markerTextPaint.textSize = 13f * density
                val baseline = point.y.toFloat() -
                    (markerTextPaint.ascent() + markerTextPaint.descent()) / 2f
                canvas.drawText(location.label, point.x.toFloat(), baseline, markerTextPaint)
            }
        }
        screenLocations = locations.zip(screen)
        canvas.drawText("© OpenStreetMap", width - 6f * density, height - 5f * density, attributionPaint)
        drawZoomControls(canvas)
        selectedApplicationId?.let { selectedId ->
            val selected = screenLocations.firstOrNull { it.first.applicationId == selectedId }
            if (selected != null) drawApplicationHint(canvas, selected.first, selected.second)
        }
    }

    private fun separateOverlappingMarkers(points: List<ProjectedPoint>): List<ProjectedPoint> {
        val minimumDistance = 38.0 * density
        val adjusted = mutableListOf<ProjectedPoint>()
        points.forEachIndexed { index, point ->
            var candidate = point
            var attempt = 0
            while (adjusted.any { existing ->
                    val dx = candidate.x - existing.x
                    val dy = candidate.y - existing.y
                    dx * dx + dy * dy < minimumDistance * minimumDistance
                } && attempt < 12
            ) {
                val angle = Math.toRadians((attempt * 60.0) - 90.0)
                val ring = 1 + attempt / 6
                candidate = ProjectedPoint(
                    point.x + kotlin.math.cos(angle) * minimumDistance * ring,
                    point.y + kotlin.math.sin(angle) * minimumDistance * ring,
                )
                attempt++
            }
            adjusted += candidate
        }
        return adjusted
    }

    private fun drawApplicationHint(
        canvas: Canvas,
        location: NativeMapLocation,
        point: ProjectedPoint,
    ) {
        val left = 10f * density
        val right = width - 10f * density
        val boxHeight = 126f * density
        val top = if (point.y > height / 2.0) 10f * density else height - boxHeight - 10f * density
        markerPaint.style = Paint.Style.FILL
        markerPaint.color = android.graphics.Color.argb(245, 255, 255, 255)
        canvas.drawRoundRect(left, top, right, top + boxHeight, 12f * density, 12f * density, markerPaint)
        markerPaint.style = Paint.Style.STROKE
        markerPaint.strokeWidth = 1.5f * density
        markerPaint.color = android.graphics.Color.rgb(43, 90, 120)
        canvas.drawRoundRect(left, top, right, top + boxHeight, 12f * density, 12f * density, markerPaint)
        markerTextPaint.textAlign = Paint.Align.LEFT
        markerTextPaint.color = android.graphics.Color.rgb(23, 33, 38)
        markerTextPaint.textSize = 13f * density
        val x = left + 12f * density
        canvas.drawText(
            "№ ${location.applicationNumber} | ${location.interval.ifBlank { "интервал не указан" }}",
            x,
            top + 24f * density,
            markerTextPaint,
        )
        val address = location.address.let { if (it.length > 43) it.take(42) + "…" else it }
        markerTextPaint.color = android.graphics.Color.rgb(43, 90, 120)
        canvas.drawText("Адрес: $address", x, top + 50f * density, markerTextPaint)
        canvas.drawText(
            "Телефон: ${location.phoneNumber.ifBlank { "не указан" }}",
            x,
            top + 76f * density,
            markerTextPaint,
        )
        val client = location.clientName.let { if (it.length > 40) it.take(39) + "…" else it }
        canvas.drawText(
            "Клиент: ${client.ifBlank { "не указан" }}",
            x,
            top + 102f * density,
            markerTextPaint,
        )
        hintAddressArea = RectF(left, top + 30f * density, right - 42f * density, top + 61f * density)
        hintPhoneArea = RectF(left, top + 61f * density, right - 42f * density, top + 89f * density)
        hintCloseArea = RectF(right - 43f * density, top, right, top + 43f * density)
        markerPaint.style = Paint.Style.STROKE
        markerPaint.strokeWidth = 4f * density
        markerPaint.color = android.graphics.Color.RED
        val closeCenterX = right - 20f * density
        val closeCenterY = top + 20f * density
        val closeSize = 9f * density
        canvas.drawLine(
            closeCenterX - closeSize, closeCenterY - closeSize,
            closeCenterX + closeSize, closeCenterY + closeSize, markerPaint,
        )
        canvas.drawLine(
            closeCenterX + closeSize, closeCenterY - closeSize,
            closeCenterX - closeSize, closeCenterY + closeSize, markerPaint,
        )
        markerTextPaint.textAlign = Paint.Align.CENTER
    }

    private fun drawHomeIcon(canvas: Canvas, centerX: Float, centerY: Float) {
        val size = 10f * density
        val house = Path().apply {
            moveTo(centerX - size, centerY - size * 0.15f)
            lineTo(centerX, centerY - size)
            lineTo(centerX + size, centerY - size * 0.15f)
            lineTo(centerX + size * 0.72f, centerY - size * 0.15f)
            lineTo(centerX + size * 0.72f, centerY + size)
            lineTo(centerX - size * 0.72f, centerY + size)
            lineTo(centerX - size * 0.72f, centerY - size * 0.15f)
            close()
        }
        markerPaint.style = Paint.Style.STROKE
        markerPaint.strokeWidth = 2f * density
        markerPaint.color = android.graphics.Color.WHITE
        canvas.drawPath(house, markerPaint)
    }

    private fun chooseZoom(): Int {
        if (locations.size == 1) return 15
        for (zoom in 17 downTo 3) {
            val points = locations.map { project(it.latitude, it.longitude, zoom) }
            val spanX = points.maxOf { it.x } - points.minOf { it.x }
            val spanY = points.maxOf { it.y } - points.minOf { it.y }
            if (spanX <= width - 70 * density && spanY <= height - 70 * density) return zoom
        }
        return 3
    }

    private fun project(latitude: Double, longitude: Double, zoom: Int): ProjectedPoint {
        val scale = TILE_SIZE * 2.0.pow(zoom)
        val limitedLatitude = latitude.coerceIn(-85.05112878, 85.05112878)
        val latitudeSin = sin(Math.toRadians(limitedLatitude))
        return ProjectedPoint(
            x = (longitude + 180.0) / 360.0 * scale,
            y = (0.5 - ln((1 + latitudeSin) / (1 - latitudeSin)) / (4 * Math.PI)) * scale,
        )
    }

    private fun drawTiles(canvas: Canvas, zoom: Int, originX: Double, originY: Double) {
        val tileCount = 1 shl zoom
        val firstX = kotlin.math.floor(originX / TILE_SIZE).toInt()
        val lastX = kotlin.math.floor((originX + width) / TILE_SIZE).toInt()
        val firstY = kotlin.math.floor(originY / TILE_SIZE).toInt()
        val lastY = kotlin.math.floor((originY + height) / TILE_SIZE).toInt()
        for (tileX in firstX..lastX) for (tileY in firstY..lastY) {
            if (tileY !in 0 until tileCount) continue
            val wrappedX = ((tileX % tileCount) + tileCount) % tileCount
            val key = "$zoom/$wrappedX/$tileY"
            val left = (tileX * TILE_SIZE - originX).toFloat()
            val top = (tileY * TILE_SIZE - originY).toFloat()
            val bitmap = synchronized(tileCache) { tileCache.get(key) }
            if (bitmap != null) {
                canvas.drawBitmap(bitmap, left, top, null)
            } else {
                requestTile(key)
            }
        }
    }

    private fun requestTile(key: String) {
        if (!loadingTiles.add(key) || failedTiles.contains(key)) return
        tileExecutor.execute {
            try {
                val connection = URL("https://tile.openstreetmap.org/$key.png").openConnection() as HttpURLConnection
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.setRequestProperty("User-Agent", "CUBUS-Metrolog/1.02")
                connection.inputStream.use { input ->
                    BitmapFactory.decodeStream(input)?.let { bitmap ->
                        synchronized(tileCache) { tileCache.put(key, bitmap) }
                    }
                }
                postInvalidate()
            } catch (_: Exception) {
                failedTiles.add(key)
            } finally {
                loadingTiles.remove(key)
            }
        }
    }

    private fun drawZoomControls(canvas: Canvas) {
        val radius = 19f * density
        val x = width - radius - 8f * density
        val firstY = radius + 8f * density
        markerPaint.style = Paint.Style.FILL
        markerPaint.color = android.graphics.Color.argb(225, 255, 255, 255)
        canvas.drawCircle(x, firstY, radius, markerPaint)
        canvas.drawCircle(x, firstY + radius * 2.25f, radius, markerPaint)
        markerPaint.style = Paint.Style.STROKE
        markerPaint.strokeWidth = 1.5f * density
        markerPaint.color = android.graphics.Color.rgb(43, 90, 120)
        canvas.drawCircle(x, firstY, radius, markerPaint)
        canvas.drawCircle(x, firstY + radius * 2.25f, radius, markerPaint)
        markerTextPaint.color = android.graphics.Color.rgb(43, 90, 120)
        markerTextPaint.textSize = 24f * density
        val plusBaseline = firstY - (markerTextPaint.ascent() + markerTextPaint.descent()) / 2f
        val minusY = firstY + radius * 2.25f
        val minusBaseline = minusY - (markerTextPaint.ascent() + markerTextPaint.descent()) / 2f
        canvas.drawText("+", x, plusBaseline, markerTextPaint)
        canvas.drawText("−", x, minusBaseline, markerTextPaint)
    }

    private fun changeZoom(delta: Int) {
        zoomOffset = (zoomOffset + delta).coerceIn(-10, 10)
        panX = 0.0
        panY = 0.0
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                touchMoved = false
            }
            MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                if (kotlin.math.abs(dx) > 1f || kotlin.math.abs(dy) > 1f) touchMoved = true
                panX += dx
                panY += dy
                lastTouchX = event.x
                lastTouchY = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP -> if (!touchMoved) {
                val radius = 19f * density
                val controlX = width - radius - 8f * density
                val plusY = radius + 8f * density
                val minusY = plusY + radius * 2.25f
                fun inControl(y: Float): Boolean {
                    val dx = event.x - controlX
                    val dy = event.y - y
                    return dx * dx + dy * dy <= radius * radius
                }
                when {
                    hintCloseArea?.contains(event.x, event.y) == true -> {
                        selectedApplicationId = null
                        hintCloseArea = null
                        hintAddressArea = null
                        hintPhoneArea = null
                        invalidate()
                    }
                    hintAddressArea?.contains(event.x, event.y) == true -> {
                        selectedApplicationId?.let { id ->
                            locations.firstOrNull { it.applicationId == id }?.let(onRoute)
                        }
                    }
                    hintPhoneArea?.contains(event.x, event.y) == true -> {
                        selectedApplicationId?.let { id ->
                            locations.firstOrNull { it.applicationId == id }
                                ?.phoneNumber?.takeIf(String::isNotBlank)?.let(onCall)
                        }
                    }
                    inControl(plusY) -> changeZoom(1)
                    inControl(minusY) -> changeZoom(-1)
                    else -> openApplicationAt(event.x, event.y)
                }
            }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    private fun openApplicationAt(x: Float, y: Float) {
            val hitRadius = 28f * density
            screenLocations.firstOrNull { (_, point) ->
                val dx = x - point.x.toFloat()
                val dy = y - point.y.toFloat()
                dx * dx + dy * dy <= hitRadius * hitRadius
            }?.first?.let { location ->
                val applicationId = location.applicationId ?: return
                if (selectedApplicationId == applicationId) {
                    onOpen(applicationId)
                } else {
                    selectedApplicationId = applicationId
                    invalidate()
                }
            }
    }

    companion object {
        private const val TILE_SIZE = 256
        private val tileCache = LruCache<String, Bitmap>(48)
        private val loadingTiles = ConcurrentHashMap.newKeySet<String>()
        private val failedTiles = ConcurrentHashMap.newKeySet<String>()
        private val tileExecutor = Executors.newFixedThreadPool(3)
    }
}

private fun List<ApplicationStatusCount>.countFor(status: String): Int =
    firstOrNull { it.status == status }?.count ?: 0

private fun dashboardStatus(value: String): String? {
    val normalized = value.lowercase(Locale("ru"))
        .replace('ё', 'е')
        .trim()
        .replace(Regex("\\s+"), " ")
    return when {
        normalized.startsWith("нов") -> "Новая"
        normalized.startsWith("выполн") -> "Выполнено"
        "доработ" in normalized -> "На Доработку"
        normalized.startsWith("отлож") || normalized == "в отложке" -> "Отложено"
        normalized.startsWith("отказ") || normalized.startsWith("отмен") -> "Отказ"
        else -> null
    }
}

@Composable
private fun TrackingSummary(statistics: TodayStatistics?) {
    Card(Modifier.fillMaxWidth(), containerColor = Color.White) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Изменения в течение дня", style = MaterialTheme.typography.titleMedium)
            StatisticLine("Остались новыми", statistics?.remaining_new ?: 0)
            StatisticLine("Добавлены позже", statistics?.added_later ?: 0)
            StatisticLine("Перенесены на другую дату", statistics?.moved_to_other_date ?: 0)
            StatisticLine(
                "Переданы другому сотруднику",
                statistics?.transferred_to_other_employee ?: 0,
            )
            val time = statistics?.snapshot_at?.let(::formatSnapshotTime).orEmpty()
            Text(
                if (time.isBlank()) "Контрольный снимок ещё не сделан"
                else "Последняя проверка: $time МСК",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatisticLine(title: String, count: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title)
        Text(count.toString(), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun TrackingChecks(statistics: List<TodayStatistics>) {
    if (statistics.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Контрольные проверки (${statistics.size})", style = MaterialTheme.typography.titleMedium)
                Text(if (expanded) "▲" else "▼")
            }
            if (expanded) {
                statistics.forEachIndexed { index, item ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            "Проверка ${statistics.size - index} — ${formatSnapshotTime(item.snapshot_at)}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "Новые ${item.initial_new}; выполнены ${item.completed}; " +
                                "доработка ${item.rework}; отложены ${item.postponed}; отказы ${item.refusals}"
                        )
                        Text(
                            "Остались ${item.remaining_new}; перенесены ${item.moved_to_other_date}; " +
                                "переданы ${item.transferred_to_other_employee}; добавлены ${item.added_later}"
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun formatSnapshotTime(value: String): String = runCatching {
    val instant = java.time.OffsetDateTime.parse(value).toInstant()
    val moscow = instant.atZone(java.time.ZoneId.of("Europe/Moscow"))
    moscow.format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale("ru")))
}.getOrDefault(value.replace('T', ' ').substringBefore('.').takeLast(8))

@Composable
private fun DailyStatisticsHistory(statistics: List<DailyStatistics>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, CubusButtonColor)) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("История статистики по дням", style = MaterialTheme.typography.titleMedium)
                Text(if (expanded) "▲" else "▼", style = MaterialTheme.typography.titleMedium)
            }
            if (expanded) {
                if (statistics.isEmpty()) {
                    Text(
                        "История появится после первого обновления статистики.",
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    )
                }
                statistics.forEach { day ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(formatStatisticsDate(day.work_date), style = MaterialTheme.typography.titleSmall)
                        Text("Новые — ${day.new_count}  •  Выполненные — ${day.completed}")
                        Text("На доработке — ${day.rework}  •  Отложенные — ${day.postponed}")
                        Text("Отказы — ${day.refusals}")
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun formatStatisticsDate(value: String): String = runCatching {
    val input = SimpleDateFormat("yyyy-MM-dd", Locale("ru"))
    val output = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))
    output.format(input.parse(value)!!)
}.getOrDefault(value)

@Composable
private fun StatusCard(
    title: String,
    count: Int,
    applications: List<ApplicationSummary>,
    onOpen: (Long) -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text("$count  ${if (expanded) "▲" else "▼"}", style = MaterialTheme.typography.titleMedium)
            }
            if (expanded) {
                if (applications.isEmpty()) {
                    Text("Заявок нет", modifier = Modifier.padding(start = 16.dp, bottom = 16.dp))
                }
                applications.sortedWith(applicationDeliveryComparator()).forEachIndexed { index, item ->
                    Text(
                        "${index + 1}. № ${item.number}",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(item.id) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

private fun applicationDeliveryComparator(): Comparator<ApplicationSummary> =
    compareBy<ApplicationSummary> { it.delivery_time.isBlank() }
        .thenBy { it.delivery_time }
        .thenBy { it.number }

@Composable
private fun ColumnScope.ProfileScreen(
    profile: UserProfile?,
    scheduleDays: List<ScheduleDay>,
    scheduleMonthOffset: Int,
    homeAddressSaving: Boolean,
    homeAddressError: String?,
    onMonthSelect: (Int) -> Unit,
    onRefreshSchedule: () -> Unit,
    onSaveHomeAddress: (String) -> Unit,
    onSaveAdministrativeExpenses: (String) -> Unit,
) {
    val context = LocalContext.current
    var homeAddress by remember(profile?.home_address) {
        mutableStateOf(profile?.home_address.orEmpty())
    }
    val homeLatitude = profile?.home_latitude
    val homeLongitude = profile?.home_longitude
    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppHeading("Профиль метролога")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ФИО", style = MaterialTheme.typography.labelLarge)
                Text(
                    profile?.full_name?.ifBlank { "—" } ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                )
                ProfileLine("Должность", profile?.position)
                ProfileLine("Контактный телефон", profile?.phone)
                ProfileLine("График работы", profile?.work_schedule)
                ProfileLine("Max кол-во заявок", profile?.max_applications)
                ProfileLine("Номер Папки", profile?.folder_number)
                Text("Административные расходы", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("Да", "Нет").forEach { value ->
                        FilterChip(
                            label = { Text(value) },
                            selected = profile?.administrative_expenses == value,
                            onClick = { onSaveAdministrativeExpenses(value) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                OutlinedTextField(
                    value = homeAddress,
                    onValueChange = { homeAddress = it },
                    label = { Text("Домашний адрес") },
                    placeholder = { Text("Улица и номер дома") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = homeAddress.isNotBlank() && !homeAddressSaving,
                    onClick = { onSaveHomeAddress(homeAddress) },
                ) {
                    Text(if (homeAddressSaving) "Получаем координаты…" else "Получить координаты")
                }
                OutlinedTextField(
                    value = if (homeLatitude != null && homeLongitude != null) {
                        "$homeLatitude, $homeLongitude"
                    } else {
                        ""
                    },
                    onValueChange = {},
                    label = { Text("Координаты") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    singleLine = true,
                )
                homeAddressError?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        AppHeading("График работы")
        WorkScheduleCalendar(scheduleDays, scheduleMonthOffset, onMonthSelect)
        Button(modifier = Modifier.fillMaxWidth(), onClick = onRefreshSchedule) {
            Text("Обновить график работы")
        }
        AppHeading("Оборудование у метролога")
        profile?.equipment.orEmpty().forEach { equipment ->
            CollapsibleSection(
                title = equipment.category,
                initiallyExpanded = false,
                compactHeader = true,
            ) {
                ProfileLine("Наименование", equipment.name)
                ProfileLine("Серийный номер", equipment.serial_number)
                if (equipment.registry_number.isNotBlank()) {
                    ProfileLine("Номер в госреестре", equipment.registry_number)
                }
                if (equipment.certificate_number.isNotBlank()) {
                    ProfileLine("Номер и ФИО", equipment.certificate_number)
                }
                if (equipment.verification_date.isNotBlank()) {
                    ProfileLine("Дата поверки", equipment.verification_date)
                }
                if (equipment.arshin_url.isNotBlank()) {
                    ActionInformationLine("АРШИН", equipment.arshin_url) {
                        openWebPage(context, equipment.arshin_url)
                    }
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { openWebPage(context, "https://t.me/+QlRfXDUiO4syMjFi") },
                ) { Text("Сообщить о неточности") }
            }
        }
    }
}

@Composable
private fun WorkScheduleCalendar(
    days: List<ScheduleDay>,
    monthOffset: Int,
    onMonthSelect: (Int) -> Unit,
) {
    val monthCalendar = remember(monthOffset) {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, monthOffset)
        }
    }
    val monthName = remember(monthOffset) {
        SimpleDateFormat("LLLL yyyy", Locale("ru")).format(monthCalendar.time)
            .replaceFirstChar { it.uppercase(Locale("ru")) }
    }
    val firstWeekday = remember(monthOffset) {
        (monthCalendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    }
    val cells: List<ScheduleDay?> = List(firstWeekday) { null } + days
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = monthOffset == 0,
                    onClick = { onMonthSelect(0) },
                    label = { Text("Этот месяц") },
                )
                FilterChip(
                    selected = monthOffset == 1,
                    onClick = { onMonthSelect(1) },
                    label = { Text("Следующий месяц") },
                )
            }
            Text(
                monthName,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth()) {
                listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { name ->
                    Text(name, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    (week + List(7 - week.size) { null }).forEach { day ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(
                                    when {
                                        day == null -> Color.Transparent
                                        !day.has_record -> Color(0xFFE0E0E0)
                                        day.work_status == "Отпуск" -> Color(0xFFFFF59D)
                                        day.is_working -> Color(0xFFC8E6C9)
                                        else -> Color(0xFFFFCDD2)
                                    },
                                    RoundedCornerShape(6.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            day?.let { Text(it.day.toString()) }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("■ Рабочий", color = Color(0xFF2E7D32))
                Text("■ Выходной", color = Color(0xFFC62828))
                Text("■ Отпуск", color = Color(0xFFF9A825))
                Text("■ Нет записи", color = Color(0xFF757575))
            }
        }
    }
}

@Composable
private fun ProfileLine(title: String, value: String?) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    Text(value?.ifBlank { "—" } ?: "—")
}

@Composable
private fun ReworkDialog(
    applicationId: Long,
    reasons: List<String>,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (Long, String, String) -> Unit,
) {
    var selectedReason by remember(applicationId) { mutableStateOf("") }
    var comment by remember(applicationId) { mutableStateOf("") }
    var reasonMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Передать на доработку") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Дата звонка будет установлена автоматически: сегодня")
                Box(Modifier.fillMaxWidth()) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = reasons.isNotEmpty(),
                        onClick = { reasonMenu = true },
                    ) {
                        Text(selectedReason.ifBlank { "Выбрать причину" })
                    }
                    DropdownMenu(
                        expanded = reasonMenu,
                        onDismissRequest = { reasonMenu = false },
                    ) {
                        reasons.forEach { reason ->
                            DropdownMenuItem(
                                text = { Text(reason) },
                                onClick = {
                                    selectedReason = reason
                                    reasonMenu = false
                                },
                            )
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Комментарий метролога") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = selectedReason.isNotBlank() && comment.isNotBlank(),
                onClick = { onConfirm(applicationId, selectedReason, comment.trim()) },
            ) { Text("Передать") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
private fun ColumnScope.ApplicationsScreen(
    applications: List<ApplicationSummary>,
    selectedDayOffset: Int,
    onDaySelect: (Int) -> Unit,
    onOpen: (Long) -> Unit,
    onComplete: (Long) -> Unit,
    onRework: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            modifier = Modifier.weight(1f),
            selected = selectedDayOffset == 0,
            onClick = { onDaySelect(0) },
            label = { Text("Заявки на сегодня") },
        )
        FilterChip(
            modifier = Modifier.weight(1f),
            selected = selectedDayOffset == 1,
            onClick = { onDaySelect(1) },
            label = { Text("Заявки на завтра") },
        )
    }
    if (applications.isEmpty()) Text("Новых заявок на выбранный день нет")
    LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(
            applications.sortedWith(applicationDeliveryComparator()),
            key = { _, item -> item.id },
        ) { index, item ->
            ApplicationCard(index + 1, item, onOpen, onComplete, onRework)
        }
    }
}

@Composable
private fun ApplicationCard(
    position: Int,
    item: ApplicationSummary,
    onOpen: (Long) -> Unit,
    onComplete: (Long) -> Unit,
    onRework: (Long) -> Unit,
) {
    val context = LocalContext.current
    var actionsExpanded by remember(item.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "$position. Заявка № ${item.number}",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CompactInformationLine("Дата выезда", item.work_date.orEmpty(), Modifier.weight(1f))
                CompactInformationLine("Интервал", item.interval, Modifier.weight(1f))
            }
            HorizontalDivider()
            InlineInformationLine("ФИО клиента", item.client)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CompactActionInformationLine(
                    "Телефон", item.phone_number, Modifier.weight(1f)
                ) { openDialer(context, item.phone_number) }
                CompactActionInformationLine(
                    "Телефон 2", item.phone_number_2, Modifier.weight(1f)
                ) { openDialer(context, item.phone_number_2) }
            }
            HorizontalDivider()
            InlineActionInformationLine("Адрес", item.address) {
                openNavigator(context, item.address)
            }
            HorizontalDivider()
            InlineInformationLine("Шлагбаум", item.barrier)
            InlineInformationLine("Комментарий", item.comments)
            HorizontalDivider()
            Box(Modifier.fillMaxWidth()) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { actionsExpanded = true },
                ) { Text("ОТКРЫТЬ ЗАЯВКУ") }
                DropdownMenu(
                    expanded = actionsExpanded,
                    onDismissRequest = { actionsExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("ОТКРЫТЬ ЗАЯВКУ") },
                        onClick = { actionsExpanded = false; onOpen(item.id) },
                    )
                    DropdownMenuItem(
                        text = { Text("Завершить заявку") },
                        onClick = { actionsExpanded = false; onComplete(item.id) },
                    )
                    DropdownMenuItem(
                        text = { Text("Передать на доработку") },
                        onClick = { actionsExpanded = false; onRework(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.CompletionWizardScreen(
    details: ApplicationDetails,
    priceList: List<PriceListItem>,
    meterCatalog: List<MeterCatalogItem>,
    meterCatalogError: String?,
    nomenclatureLoading: Boolean,
    nomenclatureError: String?,
    photoLoadingKey: String?,
    photoLoadError: String?,
    onBack: () -> Unit,
    onUploadPhoto: (Long, String, String, String) -> Unit,
    onDeletePhoto: (Long, String, String) -> Unit,
    onLoadPhoto: (Long, String, String) -> Unit,
    onAddNomenclature: (Long, Long, Int, String?) -> Unit,
    onDeleteNomenclature: (Long, Long) -> Unit,
    onAddMeter: (Long, AddMeterRequest, (WaterMeter) -> Unit) -> Unit,
    onUpdateMeter: (Long, Long, AddMeterRequest, (WaterMeter) -> Unit) -> Unit,
    onDeleteMeter: (Long, Long, () -> Unit) -> Unit,
    onConfirm: (Long, String, Int, Int) -> Unit,
) {
    var step by rememberSaveable(details.id) { mutableStateOf(0) }
    var photoIndex by rememberSaveable(details.id) { mutableStateOf(0) }
    var selectedPriceId by rememberSaveable(details.id) { mutableStateOf<Long?>(null) }
    var quantity by rememberSaveable(details.id) { mutableStateOf("1") }
    var usePriceListAmount by rememberSaveable(details.id) { mutableStateOf(true) }
    var customAmount by rememberSaveable(details.id) { mutableStateOf("") }
    var waterKind by rememberSaveable(details.id) { mutableStateOf("ИПУ ХВС") }
    var meterType by rememberSaveable(details.id) { mutableStateOf("") }
    var serialNumber by rememberSaveable(details.id) { mutableStateOf("") }
    var registryNumber by rememberSaveable(details.id) { mutableStateOf("") }
    var meterYear by rememberSaveable(details.id) { mutableStateOf("") }
    var lastCheck by rememberSaveable(details.id) { mutableStateOf("") }
    var nextCheck by rememberSaveable(details.id) { mutableStateOf("") }
    var ipuStatus by rememberSaveable(details.id) { mutableStateOf("Годен") }
    var replacementDone by rememberSaveable(details.id) { mutableStateOf(false) }
    var mpiYears by rememberSaveable(details.id) { mutableStateOf(4) }
    var statusMenu by remember { mutableStateOf(false) }
    var sessionMeterIds by rememberSaveable(details.id) { mutableStateOf(emptyList<Long>()) }
    var editingMeterId by rememberSaveable(details.id) { mutableStateOf<Long?>(null) }
    var devicePhotoName by rememberSaveable(details.id) { mutableStateOf("") }
    var devicePhotoBase64 by rememberSaveable(details.id) { mutableStateOf("") }
    var passportPhotoName by rememberSaveable(details.id) { mutableStateOf("") }
    var passportPhotoBase64 by rememberSaveable(details.id) { mutableStateOf("") }
    var meterSearch by rememberSaveable(details.id) { mutableStateOf("") }
    var showRussianKeyboard by rememberSaveable(details.id) { mutableStateOf(false) }
    var paymentType by rememberSaveable(details.id) { mutableStateOf("Наличные") }
    var cashSum by rememberSaveable(details.id) { mutableStateOf("0") }
    var cardSum by rememberSaveable(details.id) { mutableStateOf("0") }
    var serviceMenu by remember { mutableStateOf(false) }
    var materialMenu by remember { mutableStateOf(false) }
    val wizardScrollState = rememberScrollState()
    val context = LocalContext.current
    val currentPhotoType = photoTypes[photoIndex.coerceIn(0, photoTypes.lastIndex)]
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                val filename = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
                } ?: "photo-${System.currentTimeMillis()}.jpg"
                onUploadPhoto(
                    details.id,
                    currentPhotoType.first,
                    filename,
                    Base64.encodeToString(bytes, Base64.NO_WRAP),
                )
            }
        }
    }
    val devicePhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selected ->
            readImageContent(context, selected)?.let { (filename, content) ->
                devicePhotoName = filename
                devicePhotoBase64 = content
            }
        }
    }
    val passportPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selected ->
            readImageContent(context, selected)?.let { (filename, content) ->
                passportPhotoName = filename
                passportPhotoBase64 = content
            }
        }
    }
    val selectedPrice = priceList.firstOrNull { it.id == selectedPriceId }
    val servicePriceList = priceList.filter { it.item_kind == "service" }
    val materialPriceList = priceList.filter { it.item_kind == "material" }
    val calculatedAmount = ((selectedPrice?.price
        ?.replace(" ", "")?.replace(",", ".")?.toDoubleOrNull() ?: 0.0) *
        (quantity.toIntOrNull() ?: 0)).let { value ->
        if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(Locale.US, value)
    }
    val nomenclatureTotal = details.nomenclature.sumOf { item ->
        item.total.replace(" ", "").replace(",", ".").toDoubleOrNull() ?: 0.0
    }.roundToInt()

    LaunchedEffect(step, nomenclatureTotal, paymentType) {
        if (step == 3) {
            when (paymentType) {
                "Наличные" -> { cashSum = nomenclatureTotal.toString(); cardSum = "0" }
                "Эквайринг" -> { cashSum = "0"; cardSum = nomenclatureTotal.toString() }
            }
        }
    }

    LaunchedEffect(step) {
        wizardScrollState.scrollTo(0)
    }

    LaunchedEffect(ipuStatus, lastCheck, mpiYears) {
        if (ipuStatus == "Годен" && lastCheck.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            nextCheck = calculateNextVerification(lastCheck, mpiYears)
        }
        if (ipuStatus == "Новый") lastCheck = ""
        if (ipuStatus == "Не Годен") nextCheck = ""
    }

    fun clearMeterForm() {
        editingMeterId = null
        meterType = ""
        serialNumber = ""
        registryNumber = ""
        meterYear = ""
        lastCheck = ""
        nextCheck = ""
        ipuStatus = "Годен"
        replacementDone = false
        mpiYears = 4
        devicePhotoName = ""
        devicePhotoBase64 = ""
        passportPhotoName = ""
        passportPhotoBase64 = ""
    }

    fun editMeter(meter: WaterMeter) {
        editingMeterId = meter.id
        waterKind = meter.device_kind
        meterType = meter.meter_type
        serialNumber = meter.serial_number
        registryNumber = meter.registry_number
        meterYear = meter.year
        lastCheck = meter.last_check.substringBefore(" ")
        nextCheck = meter.next_check.substringBefore(" ")
        ipuStatus = meter.status.ifBlank { "Годен" }
        replacementDone = meter.status == "Не Годен" && meter.replacement == "Нет"
        devicePhotoName = meter.device_photo
        devicePhotoBase64 = ""
        passportPhotoName = meter.passport_photo
        passportPhotoBase64 = ""
    }

    fun meterRequest() = AddMeterRequest(
        device_kind = waterKind,
        ipu_status = ipuStatus,
        replacement_done = replacementDone,
        meter_type = meterType,
        serial_number = serialNumber,
        registry_number = registryNumber,
        year = meterYear,
        last_check = lastCheck,
        next_check = nextCheck,
        device_photo_filename = devicePhotoName,
        device_photo_base64 = devicePhotoBase64,
        passport_photo_filename = passportPhotoName,
        passport_photo_base64 = passportPhotoBase64,
    )

    Column(
        modifier = Modifier.weight(1f).verticalScroll(wizardScrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppHeading("Завершение заявки: шаг ${step + 1} из 5")
        when (step) {
            0 -> {
                val photosOfType = details.photos.filter { it.field == currentPhotoType.first }
                AppHeading(currentPhotoType.second)
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "Фотографии ${photoIndex + 1} из ${photoTypes.size}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        if (photosOfType.isEmpty()) {
                            Text("Фотография ещё не добавлена")
                        } else {
                            photosOfType.forEach { Text("✓ ${it.filename}") }
                        }
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { photoPicker.launch("image/*") },
                        ) {
                            Text("Добавить фотографию")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (photoIndex > 0) {
                                BackIconButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = { photoIndex-- },
                                )
                            } else {
                                TextButton(modifier = Modifier.weight(1f), onClick = onBack) {
                                    Text("Отмена")
                                }
                            }
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (photoIndex < photoTypes.lastIndex) photoIndex++ else step = 1
                                },
                            ) {
                                Text(if (photosOfType.isEmpty()) "Пропустить" else "Продолжить")
                            }
                        }
                    }
                }
                AppHeading("Загруженные фотографии")
                WizardPhotoGallery(
                    applicationId = details.id,
                    photos = details.photos.filter { it.field != "f12800" },
                    loadingKey = photoLoadingKey,
                    loadError = photoLoadError,
                    onLoad = onLoadPhoto,
                    onDelete = onDeletePhoto,
                )
            }

            1 -> {
                AppHeading("Номенклатура")
                when {
                    nomenclatureLoading -> Text("Загружаем номенклатуру…")
                    nomenclatureError != null -> Text(
                        nomenclatureError,
                        color = MaterialTheme.colorScheme.error,
                    )
                    details.nomenclature.isEmpty() -> Text("Позиции пока не добавлены")
                }
                details.nomenclature.forEachIndexed { index, item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${index + 1}. ${item.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Image(
                                    painter = painterResource(R.drawable.delete_icon),
                                    contentDescription = "Удалить позицию",
                                    modifier = Modifier.size(38.dp).clickable {
                                        onDeleteNomenclature(details.id, item.id)
                                    },
                                )
                            }
                            HorizontalDivider()
                            Text(
                                "Цена ${item.price}  |  Кол-во ${item.quantity}  |  Сумма ${item.total}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                if (priceList.isEmpty()) Text("Загружаем актуальный прайс-лист…")
                Box(Modifier.fillMaxWidth()) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = servicePriceList.isNotEmpty(),
                        onClick = { serviceMenu = true },
                    ) {
                        Text(
                            if (selectedPrice?.item_kind == "service") selectedPrice.name
                            else "Выбрать услуги",
                        )
                    }
                    DropdownMenu(
                        expanded = serviceMenu,
                        onDismissRequest = { serviceMenu = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Spacer(Modifier.height(56.dp))
                        BackIconButton(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            onClick = { serviceMenu = false },
                        )
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        servicePriceList.forEach { item ->
                            DropdownMenuItem(
                                text = { Text("${item.name} — ${item.price}") },
                                onClick = { selectedPriceId = item.id; serviceMenu = false },
                            )
                        }
                        Spacer(Modifier.height(56.dp))
                    }
                }
                Box(Modifier.fillMaxWidth()) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = materialPriceList.isNotEmpty(),
                        onClick = { materialMenu = true },
                    ) {
                        Text(
                            if (selectedPrice?.item_kind == "material") selectedPrice.name
                            else "Материалы",
                        )
                    }
                    DropdownMenu(
                        expanded = materialMenu,
                        onDismissRequest = { materialMenu = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Spacer(Modifier.height(56.dp))
                        BackIconButton(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            onClick = { materialMenu = false },
                        )
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        materialPriceList.forEach { item ->
                            DropdownMenuItem(
                                text = { Text("${item.name} — ${item.price}") },
                                onClick = { selectedPriceId = item.id; materialMenu = false },
                            )
                        }
                        Spacer(Modifier.height(56.dp))
                    }
                }
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it.filter(Char::isDigit) },
                    label = { Text("Количество") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Checkbox(
                        checked = usePriceListAmount,
                        onCheckedChange = {
                            usePriceListAmount = it
                            if (it) customAmount = calculatedAmount
                        },
                    )
                    Text("Стоимость по прайс-листу")
                }
                OutlinedTextField(
                    value = if (usePriceListAmount) calculatedAmount else customAmount,
                    onValueChange = { value ->
                        if (!usePriceListAmount) {
                            customAmount = value.filter { it.isDigit() || it == '.' || it == ',' }
                        }
                    },
                    readOnly = usePriceListAmount,
                    label = { Text("Сумма выбранной позиции") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedPriceId != null && (quantity.toIntOrNull() ?: 0) > 0 &&
                        (if (usePriceListAmount) calculatedAmount else customAmount)
                            .replace(',', '.').toDoubleOrNull() != null,
                    onClick = {
                        onAddNomenclature(
                            details.id,
                            selectedPriceId!!,
                            quantity.toInt(),
                            if (usePriceListAmount) calculatedAmount else customAmount.replace(',', '.'),
                        )
                    },
                ) { Text("Добавить номенклатуру") }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BackIconButton(
                        modifier = Modifier.weight(1f),
                        onClick = { step = 0 },
                    )
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { step = 2 },
                    ) { Text("Продолжить") }
                }
            }

            2 -> {
                AppHeading("Добавление ИПУ")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = waterKind == "ИПУ ХВС",
                        onClick = { waterKind = "ИПУ ХВС" },
                        label = { Text("Холодная вода") },
                    )
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = waterKind == "ИПУ ГВС",
                        onClick = { waterKind = "ИПУ ГВС" },
                        label = { Text("Горячая вода") },
                    )
                }
                Text("Для поиска введите:", style = MaterialTheme.typography.titleMedium)
                val normalizedSearch = meterSearch.trim()
                val catalogMatches = if (normalizedSearch.length < 2) {
                    emptyList()
                } else {
                    meterCatalog.filter { item ->
                        item.registry_number.contains(normalizedSearch, ignoreCase = true) ||
                            item.designation.contains(normalizedSearch, ignoreCase = true)
                    }.sortedWith(
                        compareBy<MeterCatalogItem> { it.designation.lowercase(Locale("ru")) }
                            .thenBy { it.registry_number.lowercase(Locale("ru")) }
                    ).take(30)
                }
                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = meterSearch,
                        onValueChange = { meterSearch = it },
                        label = { Text("Номер или обозначение типа СИ") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(
                        expanded = normalizedSearch.length >= 2 && catalogMatches.isNotEmpty(),
                        onDismissRequest = { meterSearch = "" },
                        modifier = Modifier.fillMaxWidth(0.9f),
                    ) {
                        catalogMatches.forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(item.designation)
                                        Text(
                                            item.registry_number,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                },
                                onClick = {
                                    meterType = item.designation
                                    registryNumber = item.registry_number
                                    meterSearch = ""
                                },
                            )
                        }
                    }
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showRussianKeyboard = !showRussianKeyboard },
                ) {
                    Text(if (showRussianKeyboard) "Скрыть русские буквы" else "Показать русские буквы")
                }
                if (showRussianKeyboard) {
                    RussianSearchKeyboard(
                        onLetter = { meterSearch += it },
                        onBackspace = { if (meterSearch.isNotEmpty()) meterSearch = meterSearch.dropLast(1) },
                        onClear = { meterSearch = "" },
                    )
                }
                meterCatalogError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                if (normalizedSearch.length >= 2 && catalogMatches.isEmpty() && meterCatalogError == null) {
                    Text("Совпадения не найдены")
                }
                WizardField("Тип СИ", meterType) { meterType = it }
                WizardField("Серийный номер", serialNumber) { serialNumber = it }
                WizardField("Номер в госреестре", registryNumber) { registryNumber = it }
                WizardField("Год выпуска", meterYear) { meterYear = it }
                AppHeading("Статус ИПУ")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("Годен", "Новый", "Не Годен").forEach { status ->
                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = ipuStatus == status,
                            onClick = { ipuStatus = status; replacementDone = false },
                            label = { Text(status) },
                        )
                    }
                }
                when (ipuStatus) {
                    "Новый" -> {
                        WizardDateField("Дата очередной поверки", nextCheck) { nextCheck = it }
                    }
                    "Годен" -> {
                        VerificationDateWithToday("Дата последней поверки", lastCheck) { lastCheck = it }
                        AppHeading("Межповерочный интервал")
                        Card(Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    listOf(4, 5, 6).forEach { years ->
                                        FilterChip(
                                            modifier = Modifier.weight(1f),
                                            selected = mpiYears == years,
                                            onClick = { mpiYears = years },
                                            label = { Text("$years лет") },
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = nextCheck,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Дата очередной поверки") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    else -> {
                        VerificationDateWithToday("Дата последней поверки", lastCheck) { lastCheck = it }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(replacementDone, { replacementDone = it })
                            Text("Была замена прибора")
                        }
                    }
                }
                AppHeading("Фото прибора")
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(modifier = Modifier.weight(1f), onClick = { devicePhotoPicker.launch("image/*") }) {
                            Text(if (devicePhotoName.isBlank()) "Фото прибора *" else "Фото выбрано")
                        }
                        Button(modifier = Modifier.weight(1f), onClick = { passportPhotoPicker.launch("image/*") }) {
                            Text(if (passportPhotoName.isBlank()) "Фото паспорта" else "Паспорт выбран")
                        }
                    }
                }
                val requiredPhotoReady = devicePhotoName.isNotBlank() &&
                    (editingMeterId != null || devicePhotoBase64.isNotBlank())
                val datesReady = when (ipuStatus) {
                    "Новый" -> nextCheck.isNotBlank()
                    "Годен" -> lastCheck.isNotBlank() && nextCheck.isNotBlank()
                    else -> lastCheck.isNotBlank()
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = meterType.isNotBlank() && serialNumber.isNotBlank() &&
                        requiredPhotoReady && datesReady,
                    onClick = {
                        val meterId = editingMeterId
                        if (meterId == null) {
                            onAddMeter(details.id, meterRequest()) { added ->
                                sessionMeterIds = (sessionMeterIds + added.id).distinct()
                                clearMeterForm()
                            }
                        } else {
                            onUpdateMeter(details.id, meterId, meterRequest()) {
                                clearMeterForm()
                            }
                        }
                    },
                ) { Text(if (editingMeterId == null) "Добавить ИПУ" else "Сохранить изменения") }
                if (editingMeterId != null) {
                    TextButton(onClick = { clearMeterForm() }) { Text("Отменить редактирование") }
                }
                val sessionMeters = details.water_meters.filter { it.id in sessionMeterIds }
                SessionMeterList(
                    title = "Холодная вода",
                    meters = sessionMeters.filter { it.device_kind.contains("ХВС", true) },
                    onEdit = ::editMeter,
                    onDelete = { meter ->
                        onDeleteMeter(details.id, meter.id) {
                            sessionMeterIds = sessionMeterIds - meter.id
                            if (editingMeterId == meter.id) clearMeterForm()
                        }
                    },
                )
                SessionMeterList(
                    title = "Горячая вода",
                    meters = sessionMeters.filter { it.device_kind.contains("ГВС", true) },
                    onEdit = ::editMeter,
                    onDelete = { meter ->
                        onDeleteMeter(details.id, meter.id) {
                            sessionMeterIds = sessionMeterIds - meter.id
                            if (editingMeterId == meter.id) clearMeterForm()
                        }
                    },
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = sessionMeterIds.isNotEmpty() && editingMeterId == null,
                    onClick = { step = 3 },
                ) { Text("Продолжить") }
            }

            3 -> {
                AppHeading("Вид оплаты")
                InformationLine("Сумма номенклатуры", nomenclatureTotal.toString())
                listOf("Наличные", "Эквайринг", "Эквайринг + Наличные").forEach { type ->
                    FilterChip(
                        selected = paymentType == type,
                        onClick = {
                            paymentType = type
                            when (type) {
                                "Наличные" -> { cashSum = nomenclatureTotal.toString(); cardSum = "0" }
                                "Эквайринг" -> { cashSum = "0"; cardSum = nomenclatureTotal.toString() }
                                else -> { cashSum = "0"; cardSum = "0" }
                            }
                        },
                        label = { Text(type) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (paymentType == "Эквайринг + Наличные") {
                    OutlinedTextField(
                        cashSum,
                        { cashSum = it.filter(Char::isDigit) },
                        label = { Text("Сумма наличными") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        cardSum,
                        { cardSum = it.filter(Char::isDigit) },
                        label = { Text("Сумма эквайринга") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val entered = (cashSum.toIntOrNull() ?: 0) + (cardSum.toIntOrNull() ?: 0)
                    Text(
                        "Указано: $entered из $nomenclatureTotal",
                        color = if (entered == nomenclatureTotal) {
                            MaterialTheme.colorScheme.primary
                        } else MaterialTheme.colorScheme.error,
                    )
                }
                val paymentValid = paymentType != "Эквайринг + Наличные" ||
                    (cashSum.toIntOrNull() ?: 0) + (cardSum.toIntOrNull() ?: 0) == nomenclatureTotal
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = paymentValid,
                    onClick = { step = 4 },
                ) { Text("Сформировать отчёт") }
            }

            else -> {
                AppHeading("Итоговый отчёт")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InformationLine("Номер заявки", "№ ${details.number}")
                        InformationLine("Контакты клиента", listOf(details.client, details.phone_number, details.phone_number_2).filter(String::isNotBlank).joinToString(" | "))
                        InformationLine("Адрес", details.address)
                    }
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Фото документов", style = MaterialTheme.typography.titleMedium)
                        WizardPhotoGallery(
                            applicationId = details.id,
                            photos = details.photos.filter { it.field != "f12800" },
                            loadingKey = photoLoadingKey,
                            loadError = photoLoadError,
                            onLoad = onLoadPhoto,
                        )
                    }
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Номенклатура", style = MaterialTheme.typography.titleMedium)
                        details.nomenclature.forEachIndexed { index, item ->
                            Text("${index + 1}. ${item.name} | ${item.quantity} × ${item.price} = ${item.total}")
                        }
                        HorizontalDivider()
                        Text("Общий итог: $nomenclatureTotal", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Приборы", style = MaterialTheme.typography.titleMedium)
                        details.water_meters.forEachIndexed { index, meter ->
                            Text("${index + 1}. ${meter.device_kind}: ${meter.meter_type} | ГрСИ ${meter.registry_number} | SN ${meter.serial_number}")
                            Text("Поверка: ${meter.last_check.ifBlank { "—" }} → ${meter.next_check.ifBlank { "—" }}")
                        }
                    }
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Вид оплаты", style = MaterialTheme.typography.titleMedium)
                        InformationLine("Вид оплаты", paymentType)
                        InformationLine("Наличные", cashSum.ifBlank { "0" })
                        InformationLine("Эквайринг", cardSum.ifBlank { "0" })
                    }
                }
                Text(
                    "После подтверждения заявка перейдёт в статус «Выполнено».",
                    color = MaterialTheme.colorScheme.primary,
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onConfirm(
                            details.id,
                            paymentType,
                            cashSum.toIntOrNull() ?: 0,
                            cardSum.toIntOrNull() ?: 0,
                        )
                    },
                ) { Text("Подтвердить и разрешить") }
            }
        }
    }
}

@Composable
private fun WizardPhotoGallery(
    applicationId: Long,
    photos: List<ApplicationPhoto>,
    loadingKey: String?,
    loadError: String?,
    onLoad: (Long, String, String) -> Unit,
    onDelete: ((Long, String, String) -> Unit)? = null,
) {
    var preview by remember { mutableStateOf<ApplicationPhoto?>(null) }
    var pending by remember { mutableStateOf<ApplicationPhoto?>(null) }
    LaunchedEffect(photos, loadingKey) {
        val requested = pending ?: return@LaunchedEffect
        val loaded = photos.firstOrNull {
            it.field == requested.field && it.filename == requested.filename && it.content_base64.isNotBlank()
        }
        if (loaded != null) {
            preview = loaded
            pending = null
        }
    }
    if (photos.isEmpty()) Text("Фотографии ещё не загружены")
    photos.forEachIndexed { index, photo ->
        Card(
            modifier = Modifier.fillMaxWidth().clickable {
                if (photo.content_base64.isNotBlank()) {
                    preview = photo
                } else {
                    pending = photo
                    onLoad(applicationId, photo.field, photo.filename)
                }
            },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("${index + 1}.", style = MaterialTheme.typography.titleMedium)
                Column(Modifier.weight(1f)) {
                    Text(photo.title, style = MaterialTheme.typography.titleMedium)
                    Text(photo.filename, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    if (loadingKey == "${photo.field}\u0000${photo.filename}") "Загрузка…" else "Открыть",
                    color = MaterialTheme.colorScheme.primary,
                )
                if (onDelete != null) {
                    Image(
                        painter = painterResource(R.drawable.delete_icon),
                        contentDescription = "Удалить и заменить фотографию",
                        modifier = Modifier.size(34.dp).clickable {
                            onDelete(applicationId, photo.field, photo.filename)
                        },
                    )
                }
            }
        }
    }
    loadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    preview?.let { PhotoPreview(it) { preview = null } }
}

@Composable
private fun WizardField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SessionMeterList(
    title: String,
    meters: List<WaterMeter>,
    onEdit: (WaterMeter) -> Unit,
    onDelete: (WaterMeter) -> Unit,
) {
    AppHeading(title)
    if (meters.isEmpty()) Text("Добавленных приборов нет")
    meters.forEachIndexed { index, meter ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${index + 1}. ${meter.meter_type}", style = MaterialTheme.typography.titleMedium)
                InformationLine("Статус ИПУ", meter.status)
                InformationLine("Тип прибора", meter.meter_type)
                InformationLine("Серийный номер", meter.serial_number)
                InformationLine("Номер в госреестре", meter.registry_number)
                if (meter.last_check.isNotBlank() && !meter.last_check.startsWith("0000-00-00")) {
                    InformationLine("Дата последней поверки", meter.last_check.substringBefore(" "))
                }
                if (meter.next_check.isNotBlank() && !meter.next_check.startsWith("0000-00-00")) {
                    InformationLine("Дата очередной поверки", meter.next_check.substringBefore(" "))
                }
                InformationLine("Фото прибора", meter.device_photo)
                if (meter.passport_photo.isNotBlank()) {
                    InformationLine("Фото паспорта", meter.passport_photo)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(modifier = Modifier.weight(1f), onClick = { onEdit(meter) }) {
                        Text("Редактировать")
                    }
                    Button(modifier = Modifier.weight(1f), onClick = { onDelete(meter) }) {
                        Text("Удалить")
                    }
                }
            }
        }
    }
}

private fun readImageContent(context: Context, uri: Uri): Pair<String, String>? {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val filename = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
    } ?: "photo-${System.currentTimeMillis()}.jpg"
    return filename to Base64.encodeToString(bytes, Base64.NO_WRAP)
}

@Composable
private fun RussianSearchKeyboard(
    onLetter: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf("ЁЙЦУКЕНГШЩЗХЪ", "ФЫВАПРОЛДЖЭ", "ЯЧСМИТЬБЮ").forEach { letters ->
                Row(Modifier.fillMaxWidth()) {
                    letters.forEach { letter ->
                        TextButton(
                            onClick = { onLetter(letter.lowercase()) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp),
                        ) { Text(letter.toString()) }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onBackspace, modifier = Modifier.weight(1f)) {
                    Text("Удалить букву")
                }
                TextButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                    Text("Очистить")
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun WizardDateField(label: String, value: String, onChange: (String) -> Unit) {
    var showCalendar by remember { mutableStateOf(false) }
    val pickerState = rememberDatePickerState()
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            TextButton(onClick = { showCalendar = true }) { Text("Календарь") }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    if (showCalendar) {
        DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }
                        onChange(formatter.format(Date(millis)))
                    }
                    showCalendar = false
                }) { Text("Выбрать") }
            },
            dismissButton = {
                TextButton(onClick = { showCalendar = false }) { Text("Отмена") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun VerificationDateWithToday(
    label: String,
    value: String,
    onChange: (String) -> Unit,
) {
    var showCalendar by remember { mutableStateOf(false) }
    val pickerState = rememberDatePickerState()
    AppHeading(label)
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = { onChange(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) },
            ) { Text("Сегодня", fontSize = 12.sp) }
            Button(
                modifier = Modifier.weight(1f),
                onClick = { showCalendar = true },
            ) { Text("Календарь", fontSize = 11.sp) }
            OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                placeholder = { Text("Дата", fontSize = 12.sp) },
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (showCalendar) {
        DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }
                        onChange(formatter.format(Date(millis)))
                    }
                    showCalendar = false
                }) { Text("Выбрать") }
            },
            dismissButton = {
                TextButton(onClick = { showCalendar = false }) { Text("Отмена") }
            },
        ) { DatePicker(state = pickerState) }
    }
}

private fun calculateNextVerification(lastCheck: String, years: Int): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val parsed = runCatching { formatter.parse(lastCheck) }.getOrNull() ?: return ""
    val calendar = Calendar.getInstance().apply {
        time = parsed
        add(Calendar.YEAR, years)
        add(Calendar.DAY_OF_MONTH, -1)
    }
    return formatter.format(calendar.time)
}

@Composable
private fun ColumnScope.DetailsScreen(
    details: ApplicationDetails,
    onBack: () -> Unit,
    onComplete: (Long) -> Unit,
    onRework: (Long) -> Unit,
    onUploadPhoto: (Long, String, String, String) -> Unit,
    onDeletePhoto: (Long, String, String) -> Unit,
    onLoadPhoto: (Long, String, String) -> Unit,
    nomenclatureLoading: Boolean,
    nomenclatureError: String?,
    photoLoadingKey: String?,
    photoLoadError: String?,
) {
    val context = LocalContext.current
    var photoField by remember { mutableStateOf("f12800") }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                val filename = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
                } ?: "photo-${System.currentTimeMillis()}.jpg"
                onUploadPhoto(
                    details.id,
                    photoField,
                    filename,
                    Base64.encodeToString(bytes, Base64.NO_WRAP),
                )
            }
        }
    }
    val listState = rememberLazyListState()
    val scrollProgress by remember {
        derivedStateOf {
            when {
                !listState.canScrollBackward -> 0f
                !listState.canScrollForward -> 1f
                else -> {
                    val firstItem = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                    val offsetPart = if (firstItem == null || firstItem.size == 0) 0f
                    else listState.firstVisibleItemScrollOffset.toFloat() / firstItem.size
                    val total = listState.layoutInfo.totalItemsCount.coerceAtLeast(2)
                    ((listState.firstVisibleItemIndex + offsetPart) / (total - 1)).coerceIn(0f, 1f)
                }
            }
        }
    }

    Box(modifier = Modifier.weight(1f)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                DetailsGroupTitle("Заявка № ${details.number}")
            }
            item {
                DetailsGroupTitle("Информация о заявке")
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        InlineInformationLine("Статус", details.status)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CompactInformationLine(
                                "Дата выезда", details.work_date.orEmpty(), Modifier.weight(1f)
                            )
                            CompactInformationLine(
                                "Интервал", details.interval, Modifier.weight(1f)
                            )
                        }
                        InlineActionInformationLine("Адрес", details.address) {
                            openNavigator(context, details.address)
                        }
                        InlineInformationLine("ФИО клиента", details.client)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CompactActionInformationLine(
                                "Телефон", details.phone_number, Modifier.weight(1f)
                            ) { openDialer(context, details.phone_number) }
                            CompactActionInformationLine(
                                "Телефон 2", details.phone_number_2, Modifier.weight(1f)
                            ) { openDialer(context, details.phone_number_2) }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CompactInformationLine(
                                title = "Этаж",
                                value = details.floor,
                                modifier = Modifier.weight(1f),
                            )
                            CompactInformationLine(
                                title = "Подъезд",
                                value = details.entrance,
                                modifier = Modifier.weight(1f),
                            )
                            CompactInformationLine(
                                title = "Код домофона",
                                value = details.entrance_code,
                                modifier = Modifier.weight(1.4f),
                            )
                        }
                        InlineInformationLine("Шлагбаум", details.barrier)
                        InlineInformationLine("Комментарии", details.comments)
                        InlineInformationLine("Комментарий метролога", details.metrolog_comments)
                    }
                }
            }
            val coldWaterMeters = details.water_meters.filter {
                it.device_kind.contains("ХВС", ignoreCase = true)
            }
            val hotWaterMeters = details.water_meters.filter {
                it.device_kind.contains("ГВС", ignoreCase = true)
            }
            item {
                DetailsGroupTitle("Приборы на адресе", compact = true)
            }
            item {
                CollapsibleSection(
                    title = "Холодная вода",
                    headerColor = ColdWaterHeaderColor,
                    initiallyExpanded = false,
                    compactHeader = true,
                ) {
                    if (coldWaterMeters.isEmpty()) Text("ИПУ ХВС не найдены")
                    coldWaterMeters.forEach { WaterMeterCard(it) }
                }
            }
            item {
                CollapsibleSection(
                    title = "Горячая вода",
                    headerColor = HotWaterHeaderColor,
                    initiallyExpanded = false,
                    compactHeader = true,
                ) {
                    if (hotWaterMeters.isEmpty()) Text("ИПУ ГВС не найдены")
                    hotWaterMeters.forEach { WaterMeterCard(it) }
                }
            }
            item {
                DetailsGroupTitle("Фото документов", compact = true)
            }
            item {
                CollapsibleSection(
                    "Фотографии",
                    initiallyExpanded = false,
                    compactHeader = true,
                ) {
                    PhotoSection(
                        photos = details.photos,
                        selectedField = photoField,
                        onFieldSelected = { photoField = it },
                        onAdd = { photoPicker.launch("image/*") },
                        onDelete = { photo ->
                            onDeletePhoto(details.id, photo.field, photo.filename)
                        },
                        onLoad = { photo ->
                            onLoadPhoto(details.id, photo.field, photo.filename)
                        },
                        loadingKey = photoLoadingKey,
                        loadError = photoLoadError,
                    )
                }
            }
            item {
                DetailsGroupTitle("Номенклатура", compact = true)
            }
            item {
                CollapsibleSection(
                    "Номенклатура",
                    initiallyExpanded = false,
                    compactHeader = true,
                ) {
                    when {
                        nomenclatureLoading -> Text("Загрузка номенклатуры…")
                        nomenclatureError != null -> Text(
                            nomenclatureError,
                            color = MaterialTheme.colorScheme.error,
                        )
                        details.nomenclature.isEmpty() -> Text("Позиции не найдены")
                    }
                    details.nomenclature.forEachIndexed { index, position ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    "${index + 1}. ${position.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                HorizontalDivider()
                                Text(
                                    "Цена ${position.price}  |  Кол-во ${position.quantity}  |  Сумма ${position.total}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onComplete(details.id) },
                    ) { Text("Завершить заявку") }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onRework(details.id) },
                    ) { Text("Передать на доработку") }
                    BackIconButton(modifier = Modifier.fillMaxWidth(), onClick = onBack)
                }
            }
        }

        if (listState.canScrollBackward || listState.canScrollForward) {
            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(5.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp)),
            ) {
                val thumbHeight = 56.dp.coerceAtMost(maxHeight)
                val thumbOffset = (maxHeight - thumbHeight) * scrollProgress
                Box(
                    modifier = Modifier
                        .offset(y = thumbOffset)
                        .fillMaxWidth()
                        .height(thumbHeight)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
                )
            }
        }
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    headerColor: Color = Color.Transparent,
    initiallyExpanded: Boolean = true,
    compactHeader: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (compactHeader) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(headerColor, RoundedCornerShape(8.dp))
                            .clickable { expanded = !expanded }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.width(64.dp))
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "▲" else "▼")
                        }
                    }
                    if (expanded) {
                        HorizontalDivider()
                        Column(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) { content() }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerColor, RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(72.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "▲" else "▼")
                }
            }
            if (expanded) {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) { content() }
                }
            }
        }
    }
}

@Composable
private fun AppHeading(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun DetailsGroupTitle(title: String, compact: Boolean = false) {
    Text(
        title,
        style = if (compact) MaterialTheme.typography.titleMedium
            else MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

private val photoTypes = listOf(
    "f1730" to "Акт поверки",
    "f12770" to "Акт замены",
    "f12780" to "Квитанция",
    "f12790" to "Счёт-договор",
    "f14000" to "Кассовый чек",
    "f17630" to "Акт контрольного снятия показаний",
)

@Composable
private fun PhotoSection(
    photos: List<ApplicationPhoto>,
    selectedField: String,
    onFieldSelected: (String) -> Unit,
    onAdd: () -> Unit,
    onDelete: (ApplicationPhoto) -> Unit,
    onLoad: (ApplicationPhoto) -> Unit,
    loadingKey: String?,
    loadError: String?,
) {
    var typeMenu by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ApplicationPhoto?>(null) }
    var pendingPreview by remember { mutableStateOf<ApplicationPhoto?>(null) }
    var deleteCandidate by remember { mutableStateOf<ApplicationPhoto?>(null) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f)) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { typeMenu = true },
            ) {
                Text(photoTypes.firstOrNull { it.first == selectedField }?.second ?: "Тип документа")
            }
            DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                photoTypes.forEach { (field, title) ->
                    DropdownMenuItem(
                        text = { Text(title) },
                        onClick = { typeMenu = false; onFieldSelected(field) },
                    )
                }
            }
        }
        Button(modifier = Modifier.weight(1f), onClick = onAdd) {
            Text("Добавить фотографию")
        }
    }
    val pendingKey = pendingPreview?.let { "${it.field}\u0000${it.filename}" }
    if (pendingPreview != null && loadingKey == pendingKey) Text("Загрузка фотографии…")
    if (loadError != null) Text(loadError, color = MaterialTheme.colorScheme.error)
    if (photos.isEmpty()) Text("Фотографии не найдены")
    photos.forEach { photo ->
        Card(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).clickable {
                    if (photo.content_base64.isBlank()) {
                        pendingPreview = photo
                        onLoad(photo)
                    } else {
                        preview = photo
                    }
                }) {
                    Text(photo.title, style = MaterialTheme.typography.labelLarge)
                    Text(photo.filename, color = MaterialTheme.colorScheme.primary)
                }
                Button(onClick = { deleteCandidate = photo }) { Text("Удалить") }
            }
        }
    }
    LaunchedEffect(photos, pendingPreview) {
        val pending = pendingPreview ?: return@LaunchedEffect
        val loaded = photos.firstOrNull {
            it.field == pending.field && it.filename == pending.filename
        }
        if (loaded != null && loaded.content_base64.isNotBlank()) {
            preview = loaded
            pendingPreview = null
        }
    }
    LaunchedEffect(loadError) {
        if (loadError != null) pendingPreview = null
    }
    preview?.let { PhotoPreview(it) { preview = null } }
    deleteCandidate?.let { photo ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Удалить фотографию?") },
            text = { Text(photo.filename) },
            confirmButton = {
                Button(onClick = { deleteCandidate = null; onDelete(photo) }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun PhotoPreview(photo: ApplicationPhoto, onDismiss: () -> Unit) {
    val bitmap = remember(photo.content_base64) {
        runCatching {
            val encoded = photo.content_base64.substringAfter("base64,", photo.content_base64)
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    var scale by remember { mutableStateOf(1f) }
    val transformState = rememberTransformableState { zoom, _, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(photo.title) },
        text = {
            if (bitmap == null) Text("Предпросмотр этого файла недоступен")
            else Image(
                bitmap = bitmap,
                contentDescription = photo.filename,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .transformable(transformState),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

@Composable
private fun InformationLine(title: String, value: String) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    Text(value.ifBlank { "—" })
}

@Composable
private fun CompactInformationLine(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Text(value.ifBlank { "—" })
    }
}

@Composable
private fun InlineInformationLine(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("$title:", style = MaterialTheme.typography.labelLarge)
        Text(value.ifBlank { "—" }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun InlineActionInformationLine(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("$title:", style = MaterialTheme.typography.labelLarge)
        Text(
            text = value.ifBlank { "—" },
            color = if (value.isBlank()) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.primary,
            textDecoration = if (value.isBlank()) null else TextDecoration.Underline,
            modifier = Modifier.weight(1f).let {
                if (value.isBlank()) it else it.clickable(onClick = onClick)
            },
        )
    }
}

@Composable
private fun CompactActionInformationLine(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Text(
            text = value.ifBlank { "—" },
            color = if (value.isBlank()) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.primary,
            textDecoration = if (value.isBlank()) null else TextDecoration.Underline,
            modifier = if (value.isBlank()) Modifier else Modifier.clickable(onClick = onClick),
        )
    }
}

@Composable
private fun ActionInformationLine(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    Text(
        text = value.ifBlank { "—" },
        color = if (value.isBlank()) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.primary
        },
        textDecoration = if (value.isBlank()) null else TextDecoration.Underline,
        modifier = if (value.isBlank()) Modifier else Modifier.clickable(onClick = onClick),
    )
}

private fun openNavigator(context: Context, address: String) {
    if (address.isBlank()) return
    val intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("geo:0,0?q=${Uri.encode(address)}"),
    )
    runCatching { context.startActivity(intent) }
}

private fun openDialer(context: Context, phoneNumber: String) {
    val phone = phoneNumber.filter { it.isDigit() || it == '+' }
    if (phone.isBlank()) return
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}"))
    runCatching { context.startActivity(intent) }
}

private fun openWebPage(context: Context, url: String) {
    if (url.isBlank()) return
    val normalized = if (url.startsWith("http://") || url.startsWith("https://")) {
        url
    } else {
        "https://$url"
    }
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
}

@Composable
private fun WaterMeterCard(meter: WaterMeter) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            MeterGridRow(
                "ИПУ" to meter.device_kind
                    .replace("ИПУ", "", ignoreCase = true)
                    .trim()
                    .ifBlank { "№ ${meter.id}" },
                "Тип СИ" to meter.meter_type,
            )
            HorizontalDivider()
            MeterGridRow(
                "Номер в госреестре" to meter.registry_number,
                "Год выпуска" to meter.year,
            )
            HorizontalDivider()
            MeterGridRow(
                "Серийный номер" to meter.serial_number,
                "Статус" to meter.status,
            )
            HorizontalDivider()
            MeterGridRow(
                "Последняя поверка" to displayMeterDate(meter.last_check),
                "Очередная поверка" to displayMeterDate(meter.next_check),
            )
            HorizontalDivider()
            MeterWideRow("Отправка в Аршин", meter.replacement)
        }
    }
}

@Composable
private fun MeterWideRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = CubusButtonColor,
        )
        Box(
            Modifier.width(1.dp).height(30.dp).background(CubusButtonColor.copy(alpha = 0.35f)),
        )
        Text(
            value.ifBlank { "—" },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MeterGridRow(
    left: Pair<String, String>,
    right: Pair<String, String>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        MeterGridCell(left.first, left.second, Modifier.weight(1f))
        Box(
            Modifier.width(1.dp).height(42.dp).background(CubusButtonColor.copy(alpha = 0.35f)),
        )
        MeterGridCell(right.first, right.second, Modifier.weight(1f))
    }
}

@Composable
private fun MeterGridCell(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = CubusButtonColor,
            maxLines = 2,
        )
        Text(
            value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun displayMeterDate(value: String): String {
    val date = value.trim().substringBefore(" ")
    return if (date.isBlank() || date.startsWith("0000-00-00")) "—" else date
}
