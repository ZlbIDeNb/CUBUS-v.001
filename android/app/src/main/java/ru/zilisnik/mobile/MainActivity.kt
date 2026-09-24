package ru.zilisnik.mobile

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.zilisnik.mobile.data.ApplicationDetails
import ru.zilisnik.mobile.data.ApplicationStatusCount
import ru.zilisnik.mobile.data.ApplicationSummary
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
                    state.loading -> CircularProgressIndicator()
                    state.selected != null -> DetailsScreen(state.selected!!, vm::back, vm::close)
                    section == AppSection.MAIN -> MainScreen(state.profile, state.todayStatusCounts)
                    section == AppSection.PROFILE -> ProfileScreen(state.profile)
                    else -> ApplicationsScreen(
                        applications = state.applications,
                        selectedDayOffset = state.applicationDayOffset,
                        onDaySelect = vm::selectApplicationDay,
                        onComplete = vm::select,
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
private fun ProfileScreen(profile: UserProfile?) {
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
            ApplicationCard(item, onComplete, onRework)
        }
    }
}

@Composable
private fun ApplicationCard(
    item: ApplicationSummary,
    onComplete: (Long) -> Unit,
    onRework: (Long) -> Unit,
) {
    var menuExpanded by remember(item.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("№ ${item.number}", style = MaterialTheme.typography.titleMedium)
                Text(item.address)
                Text(item.interval)
            }
            Box {
                Button(onClick = { menuExpanded = true }) { Text("▼") }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Выполнить заявку") },
                        onClick = {
                            menuExpanded = false
                            onComplete(item.id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Отправить На доработку") },
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
private fun ColumnScope.DetailsScreen(
    details: ApplicationDetails,
    onBack: () -> Unit,
    onClose: (Long, String, Int, Int) -> Unit,
) {
    var cash by remember { mutableStateOf("") }
    var card by remember { mutableStateOf("") }
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
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Информация о заявке", style = MaterialTheme.typography.titleLarge)
                        InformationLine("Статус", details.status)
                        InformationLine("Дата выезда", details.work_date.orEmpty())
                        InformationLine("Интервал", details.interval)
                        InformationLine("Адрес", details.address)
                        InformationLine("ФИО клиента", details.client)
                        InformationLine("Этаж", details.floor)
                        InformationLine("Подъезд", details.entrance)
                        InformationLine("Код домофона", details.entrance_code)
                        InformationLine("Шлагбаум", details.barrier)
                        InformationLine("Телефон", details.phone_number)
                        InformationLine("Телефон 2", details.phone_number_2)
                        InformationLine("Комментарии", details.comments)
                        InformationLine("Комментарий метролога", details.metrolog_comments)
                    }
                }
            }
            item { Text("Приборы по адресу", style = MaterialTheme.typography.titleLarge) }
            if (details.water_meters.isEmpty()) {
                item { Text("Привязанные приборы не найдены") }
            } else {
                items(details.water_meters, key = { it.id }) { meter -> WaterMeterCard(meter) }
            }
            item {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    cash,
                    { cash = it.filter(Char::isDigit) },
                    label = { Text("Наличные") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    card,
                    { card = it.filter(Char::isDigit) },
                    label = { Text("Эквайринг") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onBack) { Text("Назад") }
                    Button(onClick = {
                        val cashValue = cash.toIntOrNull() ?: 0
                        val cardValue = card.toIntOrNull() ?: 0
                        val payment = when {
                            cashValue > 0 && cardValue > 0 -> "Эквайринг + Наличные"
                            cardValue > 0 -> "Эквайринг"
                            else -> "Наличные"
                        }
                        onClose(details.id, payment, cashValue, cardValue)
                    }) { Text("Выполнить заявку") }
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
private fun InformationLine(title: String, value: String) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    Text(value.ifBlank { "—" })
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
            InformationLine("Модификация", meter.modification)
            InformationLine("Класс точности", meter.accuracy_class)
            InformationLine("Серийный номер", meter.serial_number)
            InformationLine("Номер в реестре", meter.registry_number)
            InformationLine("Год", meter.year)
            InformationLine("Последняя поверка", meter.last_check)
            InformationLine("Следующая поверка", meter.next_check)
            InformationLine("Статус", meter.status)
            InformationLine("Показания", meter.reading)
        }
    }
}
