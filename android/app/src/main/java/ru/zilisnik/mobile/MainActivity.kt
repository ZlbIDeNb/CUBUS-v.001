package ru.zilisnik.mobile

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.zilisnik.mobile.data.ApplicationDetails
import ru.zilisnik.mobile.data.ApplicationSummary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ZilisnikApp() } }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ZilisnikApp(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Zilisnik") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            when {
                state.loading -> CircularProgressIndicator()
                !state.authorized -> RegistrationScreen(vm::register)
                state.selected != null -> DetailsScreen(state.selected!!, vm::back, vm::close)
                else -> ApplicationsScreen(state.applications, vm::select)
            }
        }
    }
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
private fun ApplicationsScreen(items: List<ApplicationSummary>, onSelect: (Long) -> Unit) {
    Text("Мои заявки", style = MaterialTheme.typography.headlineSmall)
    if (items.isEmpty()) Text("Назначенных заявок нет")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items, key = { it.id }) { item ->
            Card(Modifier.fillMaxWidth().clickable { onSelect(item.id) }) {
                Column(Modifier.padding(16.dp)) {
                    Text("№ ${item.number}", style = MaterialTheme.typography.titleMedium)
                    Text(item.address)
                    Text("${item.interval} · ${item.status}")
                }
            }
        }
    }
}

@Composable
private fun DetailsScreen(
    item: ApplicationDetails,
    onBack: () -> Unit,
    onClose: (Long, String, Int, Int) -> Unit,
) {
    var cash by remember { mutableStateOf("") }
    var card by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Заявка № ${item.number}", style = MaterialTheme.typography.headlineSmall)
        Text(item.client)
        Text(item.address)
        Text("Телефон: ${item.phone_number}")
        Text("Комментарий: ${item.comments}")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(cash, { cash = it.filter(Char::isDigit) }, label = { Text("Наличные") })
        OutlinedTextField(card, { card = it.filter(Char::isDigit) }, label = { Text("Эквайринг") })
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
                onClose(item.id, payment, cashValue, cardValue)
            }) { Text("Закрыть заявку") }
        }
    }
}
