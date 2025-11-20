package com.example.truck

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// --- Entities ---
@Entity(tableName = "drivers")
data class Driver(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val licenseNo: String,
    val notes: String? = null
)

@Entity(tableName = "trucks")
data class Truck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val regNo: String,
    val model: String,
    val capacityTons: Double,
    val notes: String? = null
)

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val truckId: Long?,
    val tripId: Long?,
    val date: String,
    val category: String,
    val amount: Double,
    val notes: String? = null
)

// --- DAOs ---
@Dao
interface DriverDao {
    @Query("SELECT * FROM drivers ORDER BY name")
    fun getAll(): Flow<List<Driver>>
    @Insert suspend fun insert(d: Driver): Long
}

@Dao
interface TruckDao {
    @Query("SELECT * FROM trucks ORDER BY regNo")
    fun getAll(): Flow<List<Truck>>
    @Insert suspend fun insert(t: Truck): Long
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAll(): Flow<List<Expense>>
    @Insert suspend fun insert(e: Expense): Long
}

// --- Database ---
@Database(entities = [Driver::class, Truck::class, Expense::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun driverDao(): DriverDao
    abstract fun truckDao(): TruckDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val inst = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "transport-db").build()
                INSTANCE = inst
                inst
            }
        }
    }
}

// --- Repository & ViewModel ---
class AppRepository(private val db: AppDatabase) {
    val drivers = db.driverDao().getAll()
    val trucks = db.truckDao().getAll()
    val expenses = db.expenseDao().getAll()
    suspend fun addDriver(d: Driver) = db.driverDao().insert(d)
    suspend fun addTruck(t: Truck) = db.truckDao().insert(t)
    suspend fun addExpense(e: Expense) = db.expenseDao().insert(e)
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AppRepository(AppDatabase.getInstance(application))
    val drivers = repo.drivers
    val trucks = repo.trucks
    val expenses = repo.expenses
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun addSampleData() = viewModelScope.launch {
        repo.addDriver(Driver(name = "Ram Patil", phone = "+919876543210", licenseNo = "MH12AB1234"))
        repo.addTruck(Truck(regNo = "MH12AB0001", model = "Tata 2518", capacityTons = 12.0))
    }

    fun insertDriver(name: String, phone: String, license: String) = viewModelScope.launch {
        repo.addDriver(Driver(name = name, phone = phone, licenseNo = license))
        _message.value = "Driver added"
    }

    fun insertTruck(reg: String, model: String, cap: Double) = viewModelScope.launch {
        repo.addTruck(Truck(regNo = reg, model = model, capacityTons = cap))
        _message.value = "Truck added"
    }

    fun addExpense(truckId: Long?, tripId: Long?, date: String, cat: String, amount: Double, notes: String?) = viewModelScope.launch {
        repo.addExpense(Expense(truckId = truckId, tripId = tripId, date = date, category = cat, amount = amount, notes = notes))
        _message.value = "Expense added"
    }
}

// --- UI ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(application))[MainViewModel::class.java]
        setContent {
            MaterialTheme {
                MainScreen(vm)
            }
        }
    }
}

@Composable
fun MainScreen(vm: MainViewModel) {
    val context = LocalContext.current
    Scaffold(topBar = { TopAppBar(title = { Text("Truck Manager") }) }) { padding ->
        Column(modifier = Modifier.padding(16.dp)) {
            Button(onClick = { vm.addSampleData() }) { Text("Seed sample data") }
            Spacer(Modifier.height(8.dp))
            DriversSection(vm)
            Spacer(Modifier.height(12.dp))
            TrucksSection(vm)
            Spacer(Modifier.height(12.dp))
            ExpensesSection(vm)
            Spacer(Modifier.height(12.dp))
            SMSSection(context)
        }
    }
}

@Composable
fun DriversSection(vm: MainViewModel) {
    val drivers by vm.drivers.collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    Column {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Drivers", style = MaterialTheme.typography.h6)
            TextButton(onClick = { showAdd = !showAdd }) { Text(if (showAdd) "Close" else "Add") }
        }
        if (showAdd) AddDriverForm { name, phone, license -> vm.insertDriver(name, phone, license) }
        LazyColumn { items(drivers) { d -> Text("${d.name} — ${d.phone}") } }
    }
}

@Composable
fun AddDriverForm(onAdd: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf(TextFieldValue()) }
    var phone by remember { mutableStateOf(TextFieldValue()) }
    var license by remember { mutableStateOf(TextFieldValue()) }
    Column {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") })
        OutlinedTextField(value = license, onValueChange = { license = it }, label = { Text("License") })
        Spacer(Modifier.height(6.dp))
        Button(onClick = { onAdd(name.text.trim(), phone.text.trim(), license.text.trim()); name = TextFieldValue(); phone = TextFieldValue(); license = TextFieldValue() }) { Text("Save") }
    }
}

@Composable
fun TrucksSection(vm: MainViewModel) {
    val trucks by vm.trucks.collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    Column {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Trucks", style = MaterialTheme.typography.h6)
            TextButton(onClick = { showAdd = !showAdd }) { Text(if (showAdd) "Close" else "Add") }
        }
        if (showAdd) AddTruckForm { reg, model, cap -> vm.insertTruck(reg, model, cap) }
        LazyColumn { items(trucks) { t -> Text("${t.regNo} — ${t.model} — ${t.capacityTons}t") } }
    }
}

@Composable
fun AddTruckForm(onAdd: (String, String, Double) -> Unit) {
    var reg by remember { mutableStateOf(TextFieldValue()) }
    var model by remember { mutableStateOf(TextFieldValue()) }
    var cap by remember { mutableStateOf(TextFieldValue()) }
    Column {
        OutlinedTextField(value = reg, onValueChange = { reg = it }, label = { Text("Registration No") })
        OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") })
        OutlinedTextField(value = cap, onValueChange = { cap = it }, label = { Text("Capacity (tons)") })
        Spacer(Modifier.height(6.dp))
        Button(onClick = {
            val c = cap.text.toDoubleOrNull() ?: 0.0
            onAdd(reg.text.trim(), model.text.trim(), c)
            reg = TextFieldValue(); model = TextFieldValue(); cap = TextFieldValue()
        }) { Text("Save") }
    }
}

@Composable
fun ExpensesSection(vm: MainViewModel) {
    val expenses by vm.expenses.collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    Column {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Expenses", style = MaterialTheme.typography.h6)
            TextButton(onClick = { showAdd = !showAdd }) { Text(if (showAdd) "Close" else "Add") }
        }
        if (showAdd) AddExpenseForm { truckId, tripId, date, cat, amt, notes -> vm.addExpense(truckId, tripId, date, cat, amt, notes) }
        LazyColumn { items(expenses) { e -> Text("${e.date}: ${e.category} — ₹${e.amount}") } }
    }
}

@Composable
fun AddExpenseForm(onAdd: (Long?, Long?, String, String, Double, String?) -> Unit) {
    var truckId by remember { mutableStateOf(TextFieldValue()) }
    var tripId by remember { mutableStateOf(TextFieldValue()) }
    var date by remember { mutableStateOf(TextFieldValue(DateTimeFormatter.ISO_DATE.format(LocalDate.now())) ) }
    var cat by remember { mutableStateOf(TextFieldValue()) }
    var amt by remember { mutableStateOf(TextFieldValue()) }
    var notes by remember { mutableStateOf(TextFieldValue()) }
    Column {
        OutlinedTextField(value = truckId, onValueChange = { truckId = it }, label = { Text("TruckId (optional)") })
        OutlinedTextField(value = tripId, onValueChange = { tripId = it }, label = { Text("TripId (optional)") })
        OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date yyyy-MM-dd") })
        OutlinedTextField(value = cat, onValueChange = { cat = it }, label = { Text("Category") })
        OutlinedTextField(value = amt, onValueChange = { amt = it }, label = { Text("Amount") })
        OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") })
        Spacer(Modifier.height(6.dp))
        Button(onClick = {
            val tId = truckId.text.toLongOrNull()
            val trId = tripId.text.toLongOrNull()
            val a = amt.text.toDoubleOrNull() ?: 0.0
            onAdd(tId, trId, date.text.trim(), cat.text.trim(), a, notes.text.trim().ifEmpty { null })
            truckId = TextFieldValue(); tripId = TextFieldValue(); cat = TextFieldValue(); amt = TextFieldValue(); notes = TextFieldValue(); date = TextFieldValue(DateTimeFormatter.ISO_DATE.format(LocalDate.now()))
        }) { Text("Save") }
    }
}

@Composable
fun SMSSection(context: Context) {
    var number by remember { mutableStateOf(TextFieldValue()) }
    var message by remember { mutableStateOf(TextFieldValue()) }
    Column {
        Text("Send SMS / Open SMS app", style = MaterialTheme.typography.h6)
        OutlinedTextField(value = number, onValueChange = { number = it }, label = { Text("Phone number") })
        OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("Message") })
        Row(Modifier.padding(top = 8.dp)) {
            Button(onClick = {
                val uri = Uri.parse("smsto:${number.text}")
                val intent = Intent(Intent.ACTION_SENDTO, uri)
                intent.putExtra("sms_body", message.text)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }) { Text("Open SMS app") }
            Spacer(Modifier.width(10.dp))
            Button(onClick = {
                try {
                    val smsManager = SmsManager.getDefault()
                    smsManager.sendTextMessage(number.text, null, message.text, null, null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }) { Text("Send (direct)") }
        }
    }
}
