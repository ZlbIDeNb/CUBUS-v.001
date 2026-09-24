package ru.zilisnik.mobile

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.zilisnik.mobile.data.ApplicationDetails
import ru.zilisnik.mobile.data.ApplicationPhoto
import ru.zilisnik.mobile.data.ApplicationStatusCount
import ru.zilisnik.mobile.data.ApplicationSummary
import ru.zilisnik.mobile.data.AddMeterRequest
import ru.zilisnik.mobile.data.PriceListItem
import ru.zilisnik.mobile.data.MeterCatalogItem
import ru.zilisnik.mobile.data.UserProfile
import ru.zilisnik.mobile.data.ScheduleDay
import ru.zilisnik.mobile.data.WaterMeter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ZilisnikApp() } }
    }
}

private enum class AppSection { MAIN, PROFILE, APPLICATIONS }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ZilisnikApp(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var section by remember { mutableStateOf(AppSection.MAIN) }
    var previousSection by remember { mutableStateOf(AppSection.MAIN) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    fun navigateTo(target: AppSection) {
        if (target != section) {
            previousSection = section
            section = target
        }
    }

    if (!state.authorized) {
        Scaffold(topBar = { TopAppBar(title = { Text("Zilisnik") }) }) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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
                } else RegistrationScreen(vm::register)
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("Zilisnik", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
                DrawerItem("Основной экран", AppSection.MAIN, section) {
                    navigateTo(AppSection.MAIN)
                    scope.launch { drawerState.close() }
                }
                DrawerItem("Личный кабинет", AppSection.PROFILE, section) {
                    navigateTo(AppSection.PROFILE)
                    scope.launch { drawerState.close() }
                }
                DrawerItem("Заявки", AppSection.APPLICATIONS, section) {
                    navigateTo(AppSection.APPLICATIONS)
                    scope.launch { drawerState.close() }
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            state.selected?.let { "Заявка № ${it.number}" } ?: when (section) {
                                AppSection.MAIN -> "Основной экран"
                                AppSection.PROFILE -> "Личный кабинет"
                                AppSection.APPLICATIONS -> "Заявки"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    navigationIcon = {
                        if (state.selected != null || section != AppSection.MAIN) {
                            Button(
                                onClick = {
                                    if (state.selected != null) {
                                        vm.back()
                                    } else {
                                        val target = previousSection
                                        previousSection = section
                                        section = target
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 4.dp),
                            ) {
                                Text("←", style = MaterialTheme.typography.headlineSmall)
                            }
                        } else {
                            TextButton(onClick = { scope.launch { drawerState.open() } }) {
                                Text("☰", style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                when {
                    state.selected != null && state.completionWizard -> CompletionWizardScreen(
                        details = state.selected!!,
                        priceList = state.priceList,
                        meterCatalog = state.meterCatalog,
                        meterCatalogError = state.meterCatalogError,
                        onBack = vm::back,
                        onUploadPhoto = vm::uploadPhoto,
                        onAddNomenclature = vm::addNomenclature,
                        onDeleteNomenclature = vm::deleteNomenclature,
                        onAddMeter = vm::addMeter,
                        onUpdateMeter = vm::updateMeter,
                        onDeleteMeter = vm::deleteMeter,
                        onConfirm = vm::close,
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
                        state.todayStatusCounts,
                        state.todayApplications,
                        vm::select,
                        vm::refreshDashboard,
                    )
                    section == AppSection.PROFILE -> ProfileScreen(
                        state.profile,
                        state.scheduleDays,
                        state.scheduleMonthOffset,
                        vm::selectScheduleMonth,
                        vm::refreshSchedule,
                    )
                    else -> ApplicationsScreen(
                        applications = state.applications,
                        selectedDayOffset = state.applicationDayOffset,
                        onDaySelect = vm::selectApplicationDay,
                        onOpen = vm::select,
                        onComplete = vm::startCompletion,
                        onRework = vm::prepareRework,
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
private fun DrawerItem(
    title: String,
    target: AppSection,
    selected: AppSection,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(title) },
        selected = selected == target,
        onClick = onClick,
    )
}

@Composable
private fun RegistrationScreen(onRegister: (String, String) -> Unit) {
    var code by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppHeading("Регистрация устройства")
        OutlinedTextField(code, { code = it }, label = { Text("Код регистрации") })
        Button(
            enabled = code.length >= 4,
            onClick = { onRegister(code, "${Build.MANUFACTURER} ${Build.MODEL}") },
        ) { Text("Продолжить") }
    }
}

@Composable
private fun ColumnScope.MainScreen(
    profile: UserProfile?,
    statusCounts: List<ApplicationStatusCount>,
    applications: List<ApplicationSummary>,
    onOpen: (Long) -> Unit,
    onRefresh: () -> Unit,
) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1_000)
        }
    }
    val formatter = remember { SimpleDateFormat("dd.MM.yyyy, HH:mm:ss", Locale("ru")) }
    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppHeading(profile?.full_name?.ifBlank { profile.login } ?: "Метролог")
        Text(formatter.format(now), style = MaterialTheme.typography.titleLarge)
        AppHeading("Статистика на сегодня")
        StatusCard(
            "Новые",
            statusCounts.countFor("Новая"),
            applications.filter { it.status == "Новая" },
            onOpen,
        )
        StatusCard(
            "Выполненные",
            statusCounts.countFor("Выполнено"),
            applications.filter { it.status == "Выполнено" },
            onOpen,
        )
        StatusCard(
            "На доработке",
            statusCounts.countFor("На Доработку"),
            applications.filter { it.status == "На Доработку" },
            onOpen,
        )
        Button(modifier = Modifier.fillMaxWidth(), onClick = onRefresh) {
            Text("Обновить список заявок")
        }
    }
}

private fun List<ApplicationStatusCount>.countFor(status: String): Int =
    firstOrNull { it.status == status }?.count ?: 0

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
    onMonthSelect: (Int) -> Unit,
    onRefreshSchedule: () -> Unit,
) {
    val context = LocalContext.current
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
                actionLabel = "Сообщить",
                onAction = {
                    openWebPage(context, "https://t.me/+QlRfXDUiO4syMjFi")
                },
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
    AppHeading("Новые заявки")
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CompactInformationLine("Дата выезда", item.work_date.orEmpty(), Modifier.weight(1f))
                CompactInformationLine("Интервал", item.interval, Modifier.weight(1f))
            }
            InlineInformationLine("ФИО клиента", item.client)
            InlineActionInformationLine("Адрес", item.address) {
                openNavigator(context, item.address)
            }
            InlineInformationLine("Шлагбаум", item.barrier)
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
            InlineInformationLine("Комментарий", item.comments)
            Button(modifier = Modifier.fillMaxWidth(), onClick = { onOpen(item.id) }) {
                Text("Открыть заявку")
            }
            Button(modifier = Modifier.fillMaxWidth(), onClick = { onComplete(item.id) }) {
                Text("Завершить заявку")
            }
            Button(modifier = Modifier.fillMaxWidth(), onClick = { onRework(item.id) }) {
                Text("Передать на доработку")
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
    onBack: () -> Unit,
    onUploadPhoto: (Long, String, String, String) -> Unit,
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
    var priceMenu by remember { mutableStateOf(false) }
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
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
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
                                TextButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = { photoIndex-- },
                                ) { Text("Назад") }
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
            }

            1 -> {
                AppHeading("Номенклатура")
                if (details.nomenclature.isEmpty()) Text("Позиции пока не добавлены")
                details.nomenclature.forEach { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            InformationLine("Наименование", item.name)
                            InformationLine("Цена", item.price)
                            InformationLine("Кол-во", item.quantity)
                            InformationLine("Сумма", item.total)
                            Button(onClick = { onDeleteNomenclature(details.id, item.id) }) {
                                Text("Удалить позицию")
                            }
                        }
                    }
                }
                Box {
                    Button(onClick = { priceMenu = true }) {
                        Text(selectedPrice?.name ?: "Выбрать из актуального прайс-листа")
                    }
                    DropdownMenu(priceMenu, { priceMenu = false }) {
                        priceList.forEach { item ->
                            DropdownMenuItem(
                                text = { Text("${item.name} — ${item.price}") },
                                onClick = { selectedPriceId = item.id; priceMenu = false },
                            )
                        }
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { step = 0 }) { Text("Назад") }
                    Button(onClick = { step = 2 }) { Text("Продолжить") }
                }
            }

            2 -> {
                AppHeading("Добавление ИПУ")
                Text(
                    "Поиск в справочнике по номеру в госреестре или обозначению типа СИ",
                    style = MaterialTheme.typography.labelLarge,
                )
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
                TextButton(onClick = { showRussianKeyboard = !showRussianKeyboard }) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = waterKind == "ИПУ ХВС",
                        onClick = { waterKind = "ИПУ ХВС" },
                        label = { Text("Холодная вода") },
                    )
                    FilterChip(
                        selected = waterKind == "ИПУ ГВС",
                        onClick = { waterKind = "ИПУ ГВС" },
                        label = { Text("Горячая вода") },
                    )
                }
                WizardField("Тип СИ", meterType) { meterType = it }
                WizardField("Серийный номер", serialNumber) { serialNumber = it }
                WizardField("Номер в госреестре", registryNumber) { registryNumber = it }
                WizardField("Год выпуска", meterYear) { meterYear = it }
                Text("Статус ИПУ", style = MaterialTheme.typography.labelLarge)
                Box {
                    Button(onClick = { statusMenu = true }) { Text(ipuStatus) }
                    DropdownMenu(statusMenu, { statusMenu = false }) {
                        listOf("Новый", "Годен", "Не Годен").forEach { status ->
                            DropdownMenuItem(
                                text = { Text(status) },
                                onClick = {
                                    ipuStatus = status
                                    statusMenu = false
                                    replacementDone = false
                                },
                            )
                        }
                    }
                }
                when (ipuStatus) {
                    "Новый" -> {
                        WizardDateField("Дата очередной поверки", nextCheck) { nextCheck = it }
                    }
                    "Годен" -> {
                        VerificationDateWithToday("Дата последней поверки", lastCheck) { lastCheck = it }
                        Text("Межповерочный интервал", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(4, 5, 6).forEach { years ->
                                FilterChip(
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
                    else -> {
                        VerificationDateWithToday("Дата последней поверки", lastCheck) { lastCheck = it }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(replacementDone, { replacementDone = it })
                            Text("Была замена прибора")
                        }
                    }
                }
                Text("Фото прибора обязательно", style = MaterialTheme.typography.labelLarge)
                Button(onClick = { devicePhotoPicker.launch("image/*") }) {
                    Text(if (devicePhotoName.isBlank()) "Добавить фото прибора *" else "Фото: $devicePhotoName")
                }
                Button(onClick = { passportPhotoPicker.launch("image/*") }) {
                    Text(if (passportPhotoName.isBlank()) "Добавить фото паспорта" else "Паспорт: $passportPhotoName")
                }
                val requiredPhotoReady = devicePhotoName.isNotBlank() &&
                    (editingMeterId != null || devicePhotoBase64.isNotBlank())
                val datesReady = when (ipuStatus) {
                    "Новый" -> nextCheck.isNotBlank()
                    "Годен" -> lastCheck.isNotBlank() && nextCheck.isNotBlank()
                    else -> lastCheck.isNotBlank()
                }
                Button(
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { step = 1 }) { Text("Назад") }
                    Button(
                        enabled = sessionMeterIds.isNotEmpty() && editingMeterId == null,
                        onClick = { step = 3 },
                    ) { Text("Продолжить") }
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { step = 2 }) { Text("Назад") }
                    Button(enabled = paymentValid, onClick = { step = 4 }) {
                        Text("Сформировать отчёт")
                    }
                }
            }

            else -> {
                AppHeading("Итоговый отчёт")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InformationLine("Заявка", "№ ${details.number}")
                        InformationLine("Клиент", details.client)
                        InformationLine("Адрес", details.address)
                        InformationLine("Фотографии", details.photos.size.toString())
                        details.photos.forEach { Text("• ${it.title}: ${it.filename}") }
                        InformationLine("Номенклатура", details.nomenclature.size.toString())
                        details.nomenclature.forEach {
                            Text("• ${it.name}: ${it.quantity} × ${it.price} = ${it.total}")
                        }
                        InformationLine("ИПУ на адресе", details.water_meters.size.toString())
                        details.water_meters.forEach {
                            Text("• ${it.device_kind}: ${it.meter_type}, № ${it.serial_number}")
                        }
                        InformationLine("Вид оплаты", paymentType)
                        InformationLine("Наличные", cashSum.ifBlank { "0" })
                        InformationLine("Эквайринг", cardSum.ifBlank { "0" })
                    }
                }
                Text(
                    "После подтверждения заявка перейдёт в статус «Выполнено».",
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { step = 3 }) { Text("Назад") }
                    Button(onClick = {
                        onConfirm(
                            details.id,
                            paymentType,
                            cashSum.toIntOrNull() ?: 0,
                            cardSum.toIntOrNull() ?: 0,
                        )
                    }) { Text("Подтвердить и завершить") }
                }
            }
        }
    }
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
                InformationLine("Серийный номер", meter.serial_number)
                InformationLine("Номер в госреестре", meter.registry_number)
                InformationLine("Фото прибора", meter.device_photo)
                if (meter.passport_photo.isNotBlank()) {
                    InformationLine("Фото паспорта", meter.passport_photo)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onEdit(meter) }) { Text("Редактировать") }
                    TextButton(onClick = { onDelete(meter) }) { Text("Удалить") }
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
private fun VerificationDateWithToday(
    label: String,
    value: String,
    onChange: (String) -> Unit,
) {
    WizardDateField(label, value, onChange)
    TextButton(
        onClick = {
            onChange(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
        },
    ) { Text("Сегодня") }
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
                DetailsGroupTitle("Приборы на адресе")
            }
            item {
                CollapsibleSection(
                    title = "Холодная вода",
                    headerColor = Color(0xFFD8F1FF),
                    initiallyExpanded = false,
                    centerTitle = true,
                ) {
                    if (coldWaterMeters.isEmpty()) Text("ИПУ ХВС не найдены")
                    coldWaterMeters.forEach { WaterMeterCard(it) }
                }
            }
            item {
                CollapsibleSection(
                    title = "Горячая вода",
                    headerColor = Color(0xFFFFE1E7),
                    initiallyExpanded = false,
                    centerTitle = true,
                ) {
                    if (hotWaterMeters.isEmpty()) Text("ИПУ ГВС не найдены")
                    hotWaterMeters.forEach { WaterMeterCard(it) }
                }
            }
            item {
                DetailsGroupTitle("Фото документов")
            }
            item {
                CollapsibleSection(
                    "Фотографии",
                    initiallyExpanded = false,
                    centerTitle = true,
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
                DetailsGroupTitle("Номенклатура")
            }
            item {
                CollapsibleSection(
                    "Номенклатура",
                    initiallyExpanded = false,
                    centerTitle = true,
                ) {
                    when {
                        nomenclatureLoading -> Text("Загрузка номенклатуры…")
                        nomenclatureError != null -> Text(
                            nomenclatureError,
                            color = MaterialTheme.colorScheme.error,
                        )
                        details.nomenclature.isEmpty() -> Text("Позиции не найдены")
                    }
                    details.nomenclature.forEach { position ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                InformationLine("Наименование из прайс-листа", position.name)
                                InformationLine("Цена", position.price)
                                InformationLine("Кол-во", position.quantity)
                                InformationLine("Сумма", position.total)
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
                    Button(modifier = Modifier.fillMaxWidth(), onClick = onBack) {
                        Text("Назад")
                    }
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
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    centerTitle: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerColor, RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp),
        ) {
            AppHeading(title, Modifier.align(Alignment.Center))
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction, modifier = Modifier.align(Alignment.CenterStart)) {
                    Text(actionLabel)
                }
            }
            Button(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.CenterEnd)) {
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
private fun DetailsGroupTitle(title: String) = AppHeading(title)

private val photoTypes = listOf(
    "f1730" to "Акт поверки",
    "f12770" to "Акт замены",
    "f12780" to "Квитанция",
    "f12790" to "Счёт-договор",
    "f12800" to "Счётчики",
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
    Box {
        Button(onClick = { typeMenu = true }) {
            Text(photoTypes.firstOrNull { it.first == selectedField }?.second ?: "Тип фотографии")
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
    Button(onClick = onAdd) { Text("Добавить фотографию") }
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                meter.device_kind.ifBlank { "Прибор № ${meter.id}" },
                style = MaterialTheme.typography.titleMedium,
            )
            InformationLine("Тип СИ", meter.meter_type)
            InformationLine("Серийный номер", meter.serial_number)
            InformationLine("Номер в госреестре", meter.registry_number)
            InformationLine("Год выпуска", meter.year)
            InformationLine("Дата последней поверки", meter.last_check)
            InformationLine("Дата очередной поверки", meter.next_check)
            InformationLine("Статус", meter.status)
        }
    }
}
