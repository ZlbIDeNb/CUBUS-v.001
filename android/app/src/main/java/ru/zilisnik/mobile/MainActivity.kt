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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
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
import ru.zilisnik.mobile.data.UserProfile
import ru.zilisnik.mobile.data.WaterMeter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                if (state.loading) CircularProgressIndicator() else RegistrationScreen(vm::register)
            }
        }
        return
    }

    LaunchedEffect(section, state.authorized) {
        while (section == AppSection.MAIN && state.authorized) {
            vm.refreshDashboard()
            delay(15_000)
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
                            }
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
                        onBack = vm::back,
                        onUploadPhoto = vm::uploadPhoto,
                        onAddNomenclature = vm::addNomenclature,
                        onAddMeter = vm::addMeter,
                        onConfirm = vm::close,
                    )
                    state.selected != null -> DetailsScreen(
                        details = state.selected!!,
                        onBack = vm::back,
                        onComplete = vm::startCompletion,
                        onUploadPhoto = vm::uploadPhoto,
                        onDeletePhoto = vm::deletePhoto,
                        onLoadPhoto = vm::loadPhoto,
                        nomenclatureLoading = state.nomenclatureLoading,
                        nomenclatureError = state.nomenclatureError,
                        photoLoadingKey = state.photoLoadingKey,
                        photoLoadError = state.photoLoadError,
                    )
                    state.loading -> CircularProgressIndicator()
                    section == AppSection.MAIN -> MainScreen(state.profile, state.todayStatusCounts)
                    section == AppSection.PROFILE -> ProfileScreen(state.profile)
                    else -> ApplicationsScreen(
                        applications = state.applications,
                        selectedDayOffset = state.applicationDayOffset,
                        onDaySelect = vm::selectApplicationDay,
                        onOpen = vm::select,
                        onComplete = vm::startCompletion,
                        onRework = vm::sendToRework,
                    )
                }
            }
        }
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
        Text("Регистрация устройства", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(code, { code = it }, label = { Text("Код регистрации") })
        Button(
            enabled = code.length >= 4,
            onClick = { onRegister(code, "${Build.MANUFACTURER} ${Build.MODEL}") },
        ) { Text("Продолжить") }
    }
}

@Composable
private fun MainScreen(
    profile: UserProfile?,
    statusCounts: List<ApplicationStatusCount>,
) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1_000)
        }
    }
    val formatter = remember { SimpleDateFormat("dd.MM.yyyy, HH:mm:ss", Locale("ru")) }
    Text(
        profile?.full_name?.ifBlank { profile.login } ?: "Метролог",
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(formatter.format(now), style = MaterialTheme.typography.titleLarge)
    Text("Статистика на сегодня", style = MaterialTheme.typography.headlineSmall)
    StatusCard("Новые", statusCounts.countFor("Новая"))
    StatusCard("Выполненные", statusCounts.countFor("Выполнено"))
    StatusCard("На доработке", statusCounts.countFor("На Доработку"))
}

private fun List<ApplicationStatusCount>.countFor(status: String): Int =
    firstOrNull { it.status == status }?.count ?: 0

@Composable
private fun StatusCard(title: String, count: Int) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(count.toString(), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun ColumnScope.ProfileScreen(profile: UserProfile?) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Профиль метролога", style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileLine("ФИО", profile?.full_name)
                ProfileLine("Должность", profile?.position)
                ProfileLine("Контактный телефон", profile?.phone)
                ProfileLine("График работы", profile?.work_schedule)
                ProfileLine("Max кол-во заявок", profile?.max_applications)
                ProfileLine("Номер Папки", profile?.folder_number)
            }
        }
        Text("Оборудование у метролога", style = MaterialTheme.typography.headlineSmall)
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
private fun ProfileLine(title: String, value: String?) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    Text(value?.ifBlank { "—" } ?: "—")
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
    Text("Новые заявки", style = MaterialTheme.typography.headlineSmall)
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
        items(applications.take(10), key = { it.id }) { item ->
            ApplicationCard(item, onOpen, onComplete, onRework)
        }
    }
}

@Composable
private fun ApplicationCard(
    item: ApplicationSummary,
    onOpen: (Long) -> Unit,
    onComplete: (Long) -> Unit,
    onRework: (Long) -> Unit,
) {
    var menuExpanded by remember(item.id) { mutableStateOf(false) }
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("№ ${item.number}", style = MaterialTheme.typography.titleMedium)
                InformationLine("ФИО клиента", item.client)
                ActionInformationLine("Адрес", item.address) {
                    openNavigator(context, item.address)
                }
                InformationLine("Интервал", item.interval)
                ActionInformationLine("Телефон", item.phone_number) {
                    openDialer(context, item.phone_number)
                }
                InformationLine("Шлагбаум", item.barrier)
                InformationLine("Комментарии", item.comments)
            }
            Box {
                Button(onClick = { menuExpanded = true }) { Text("▼") }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Открыть заявку") },
                        onClick = { menuExpanded = false; onOpen(item.id) },
                    )
                    DropdownMenuItem(
                        text = { Text("Завершить заявку") },
                        onClick = {
                            menuExpanded = false
                            onComplete(item.id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Передать на доработку") },
                        onClick = {
                            menuExpanded = false
                            onRework(item.id)
                        },
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
    onBack: () -> Unit,
    onUploadPhoto: (Long, String, String, String) -> Unit,
    onAddNomenclature: (Long, Long, Int) -> Unit,
    onAddMeter: (Long, AddMeterRequest) -> Unit,
    onConfirm: (Long, String, Int, Int) -> Unit,
) {
    var step by rememberSaveable(details.id) { mutableStateOf(0) }
    var photoIndex by rememberSaveable(details.id) { mutableStateOf(0) }
    var selectedPriceId by rememberSaveable(details.id) { mutableStateOf<Long?>(null) }
    var quantity by rememberSaveable(details.id) { mutableStateOf("1") }
    var waterKind by rememberSaveable(details.id) { mutableStateOf("ИПУ ХВС") }
    var meterType by rememberSaveable(details.id) { mutableStateOf("") }
    var serialNumber by rememberSaveable(details.id) { mutableStateOf("") }
    var registryNumber by rememberSaveable(details.id) { mutableStateOf("") }
    var meterYear by rememberSaveable(details.id) { mutableStateOf("") }
    var lastCheck by rememberSaveable(details.id) { mutableStateOf("") }
    var nextCheck by rememberSaveable(details.id) { mutableStateOf("") }
    var meterAdded by rememberSaveable(details.id) { mutableStateOf(false) }
    var paymentType by rememberSaveable(details.id) { mutableStateOf("По договору") }
    var cashSum by rememberSaveable(details.id) { mutableStateOf("0") }
    var cardSum by rememberSaveable(details.id) { mutableStateOf("0") }
    var priceMenu by remember { mutableStateOf(false) }
    var paymentMenu by remember { mutableStateOf(false) }
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
    val selectedPrice = priceList.firstOrNull { it.id == selectedPriceId }

    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Завершение заявки: шаг ${step + 1} из 5",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        when (step) {
            0 -> {
                val photosOfType = details.photos.filter { it.field == currentPhotoType.first }
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "Фотографии ${photoIndex + 1} из ${photoTypes.size}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(currentPhotoType.second, style = MaterialTheme.typography.titleLarge)
                        if (photosOfType.isEmpty()) {
                            Text("Фотография ещё не добавлена")
                        } else {
                            photosOfType.forEach { Text("✓ ${it.filename}") }
                        }
                        Button(onClick = { photoPicker.launch("image/*") }) {
                            Text("Добавить фотографию")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (photoIndex > 0) {
                                TextButton(onClick = { photoIndex-- }) { Text("Назад") }
                            } else {
                                TextButton(onClick = onBack) { Text("Отмена") }
                            }
                            Button(onClick = {
                                if (photoIndex < photoTypes.lastIndex) photoIndex++ else step = 1
                            }) {
                                Text(if (photosOfType.isEmpty()) "Пропустить" else "Продолжить")
                            }
                        }
                    }
                }
            }

            1 -> {
                Text("Номенклатура", style = MaterialTheme.typography.titleLarge)
                if (details.nomenclature.isEmpty()) Text("Позиции пока не добавлены")
                details.nomenclature.forEach { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            InformationLine("Наименование", item.name)
                            InformationLine("Цена", item.price)
                            InformationLine("Кол-во", item.quantity)
                            InformationLine("Сумма", item.total)
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
                Button(
                    enabled = selectedPriceId != null && (quantity.toIntOrNull() ?: 0) > 0,
                    onClick = {
                        onAddNomenclature(details.id, selectedPriceId!!, quantity.toInt())
                    },
                ) { Text("Добавить номенклатуру") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { step = 0 }) { Text("Назад") }
                    Button(onClick = { step = 2 }) { Text("Продолжить") }
                }
            }

            2 -> {
                Text("Добавление ИПУ", style = MaterialTheme.typography.titleLarge)
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
                WizardField("Дата последней поверки", lastCheck) { lastCheck = it }
                WizardField("Дата очередной поверки", nextCheck) { nextCheck = it }
                Button(
                    enabled = meterType.isNotBlank() && serialNumber.isNotBlank(),
                    onClick = {
                        onAddMeter(
                            details.id,
                            AddMeterRequest(
                                device_kind = waterKind,
                                meter_type = meterType,
                                serial_number = serialNumber,
                                registry_number = registryNumber,
                                year = meterYear,
                                last_check = lastCheck,
                                next_check = nextCheck,
                            ),
                        )
                        meterAdded = true
                        meterType = ""
                        serialNumber = ""
                        registryNumber = ""
                        meterYear = ""
                        lastCheck = ""
                        nextCheck = ""
                    },
                ) { Text("Добавить ИПУ") }
                if (meterAdded) Text("✓ ИПУ добавлен. Можно добавить ещё один или продолжить.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { step = 1 }) { Text("Назад") }
                    Button(enabled = meterAdded, onClick = { step = 3 }) { Text("Продолжить") }
                }
            }

            3 -> {
                Text("Вид оплаты", style = MaterialTheme.typography.titleLarge)
                Box {
                    Button(onClick = { paymentMenu = true }) { Text(paymentType) }
                    DropdownMenu(paymentMenu, { paymentMenu = false }) {
                        listOf("Наличные", "Эквайринг", "Эквайринг + Наличные", "По договору")
                            .forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = { paymentType = type; paymentMenu = false },
                                )
                            }
                    }
                }
                if (paymentType == "Наличные" || paymentType == "Эквайринг + Наличные") {
                    OutlinedTextField(
                        cashSum,
                        { cashSum = it.filter(Char::isDigit) },
                        label = { Text("Сумма наличными") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (paymentType == "Эквайринг" || paymentType == "Эквайринг + Наличные") {
                    OutlinedTextField(
                        cardSum,
                        { cardSum = it.filter(Char::isDigit) },
                        label = { Text("Сумма эквайринга") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { step = 2 }) { Text("Назад") }
                    Button(onClick = { step = 4 }) { Text("Сформировать отчёт") }
                }
            }

            else -> {
                Text("Итоговый отчёт", style = MaterialTheme.typography.titleLarge)
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
                        if (paymentType != "По договору") {
                            InformationLine("Наличные", cashSum.ifBlank { "0" })
                            InformationLine("Эквайринг", cardSum.ifBlank { "0" })
                        }
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
private fun ColumnScope.DetailsScreen(
    details: ApplicationDetails,
    onBack: () -> Unit,
    onComplete: (Long) -> Unit,
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
                Text("Заявка № ${details.number}", style = MaterialTheme.typography.headlineSmall)
            }
            item {
                CollapsibleSection("Информация о заявке") {
                        InformationLine("Статус", details.status)
                        InformationLine("Дата выезда", details.work_date.orEmpty())
                        InformationLine("Интервал", details.interval)
                        ActionInformationLine("Адрес", details.address) {
                            openNavigator(context, details.address)
                        }
                        InformationLine("ФИО клиента", details.client)
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
                        InformationLine("Шлагбаум", details.barrier)
                        ActionInformationLine("Телефон", details.phone_number) {
                            openDialer(context, details.phone_number)
                        }
                        ActionInformationLine("Телефон 2", details.phone_number_2) {
                            openDialer(context, details.phone_number_2)
                        }
                        InformationLine("Комментарии", details.comments)
                        InformationLine("Комментарий метролога", details.metrolog_comments)
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onBack) { Text("Назад") }
                    Button(onClick = { onComplete(details.id) }) { Text("Завершить заявку") }
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
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (centerTitle && actionLabel == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerColor, RoundedCornerShape(8.dp))
                        .clickable { expanded = !expanded }
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                    )
                    Button(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) { Text(if (expanded) "▲" else "▼") }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerColor, RoundedCornerShape(8.dp))
                        .clickable { expanded = !expanded }
                        .padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (actionLabel != null && onAction != null) {
                        TextButton(onClick = onAction) { Text(actionLabel) }
                    }
                    Button(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "▲" else "▼")
                    }
                }
            }
            if (expanded) content()
        }
    }
}

@Composable
private fun DetailsGroupTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

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
