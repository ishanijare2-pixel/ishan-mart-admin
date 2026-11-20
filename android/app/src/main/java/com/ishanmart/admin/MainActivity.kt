package com.ishanmart.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

private const val ADMIN_UID = "LkxRY5jQxmVzOE1bIVXxvEvIsnf2"
private val BG = Color(0xFF101114)
private val PANEL = Color(0xFF191A1F)
private val PANEL2 = Color(0xFF25262C)
private val LIME = Color(0xFFD7FF45)
private val MUTED = Color(0xFFA5A6AD)
private val RED = Color(0xFFFF6666)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (FirebaseApp.getApps(this).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyDci6dFkW3Sgfx8fzGjEYPQPbhG7EuwpXE")
                .setApplicationId("1:807194530965:web:de67c1361adfe1b2c7b79c")
                .setProjectId("ishan-mart")
                .setStorageBucket("ishan-mart.firebasestorage.app")
                .setGcmSenderId("807194530965").build()
            FirebaseApp.initializeApp(this, options)
        }
        setContent { AdminApp() }
    }
}

@Composable
private fun AdminApp() {
    var current by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    MaterialTheme(colorScheme = darkColorScheme(primary = LIME, background = BG, surface = PANEL)) {
        Surface(Modifier.fillMaxSize(), color = BG) {
            if (current?.uid == ADMIN_UID) {
                AdminHome { FirebaseAuth.getInstance().signOut(); current = null }
            } else {
                LaunchedEffect(current) {
                    if (current != null && current?.uid != ADMIN_UID) {
                        FirebaseAuth.getInstance().signOut()
                        current = null
                        error = "This account is not authorised for Admin access."
                    }
                }
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text("ISHAN MART", color = LIME, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("ADMIN CONTROL CENTER", color = MUTED)
                    Spacer(Modifier.height(22.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = PANEL)) {
                        Column(Modifier.padding(20.dp)) {
                            Text("Admin sign in", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true)
                            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true)
                            if (error.isNotBlank()) Text(error, color = RED)
                            Button(onClick = {
                                if (email.isBlank() || password.isBlank()) { error = "Enter email and password."; return@Button }
                                loading = true; error = ""
                                FirebaseAuth.getInstance().signInWithEmailAndPassword(email.trim(), password)
                                    .addOnCompleteListener { task ->
                                        loading = false
                                        if (task.isSuccessful && task.result?.user?.uid == ADMIN_UID) current = task.result?.user
                                        else { FirebaseAuth.getInstance().signOut(); error = if (task.isSuccessful) "This account is not authorised." else (task.exception?.localizedMessage ?: "Login failed.") }
                                    }
                            }, Modifier.fillMaxWidth(), enabled = !loading) { Text(if (loading) "Signing in…" else "SIGN IN") }
                        }
                    }
                }
            }
        }
    }
}

private data class Product(val id: String, val name: String, val category: String, val price: Double, val stock: Long, val image: String, val description: String)
private data class Order(val id: String, val customer: String, val phone: String, val address: String, val status: String, val total: Double, val items: List<Map<String, Any>>, val uid: String, val payment: String)
private data class CancelReq(val id: String, val orderId: String, val customer: String, val reason: String, val status: String, val uid: String)

@Composable
private fun AdminHome(onLogout: () -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    var tab by remember { mutableStateOf("Dashboard") }
    var products by remember { mutableStateOf(emptyList<Product>()) }
    var orders by remember { mutableStateOf(emptyList<Order>()) }
    var requests by remember { mutableStateOf(emptyList<CancelReq>()) }
    var search by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var dialog by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Product?>(null) }
    var pname by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("General") }
    var price by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }
    var image by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        val p = db.collection("products").addSnapshotListener { snap, e ->
            if (e == null) products = snap?.documents?.map { d ->
                Product(d.id, d.getString("name") ?: d.getString("title") ?: "Unnamed", d.getString("category") ?: "General", d.getDouble("price") ?: 0.0, d.getLong("stock") ?: 0L, d.getString("image") ?: d.getString("imageUrl") ?: "", d.getString("description") ?: "")
            } ?: emptyList()
        }
        val o = db.collection("orders").addSnapshotListener { snap, e ->
            if (e == null) orders = snap?.documents?.map { d ->
                Order(d.id, d.getString("customerName") ?: "Customer", d.getString("phone") ?: "", d.getString("address") ?: "", d.getString("status") ?: "Pending", d.getDouble("total") ?: 0.0, (d.get("items") as? List<Map<String, Any>>) ?: emptyList(), d.getString("customerUid") ?: "", d.getString("paymentMethod") ?: "COD")
            }?.sortedByDescending { it.id } ?: emptyList()
        }
        val c = db.collection("cancellationRequests").addSnapshotListener { snap, e ->
            if (e == null) requests = snap?.documents?.map { d ->
                CancelReq(d.id, d.getString("orderId") ?: "", d.getString("customerName") ?: "Customer", d.getString("reason") ?: "", d.getString("status") ?: "Requested", d.getString("customerUid") ?: "")
            } ?: emptyList()
        }
        onDispose { p.remove(); o.remove(); c.remove() }
    }

    Column(Modifier.fillMaxSize().background(BG)) {
        Row(Modifier.fillMaxWidth().background(PANEL).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ISHAN MART", color = LIME, fontWeight = FontWeight.Black)
                Text("ADMIN • LIVE CONTROL", color = MUTED, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, "Logout", tint = Color.White) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Dashboard", "Products", "Orders", "Requests", "Customers").forEach { name ->
                FilterChip(selected = tab == name, onClick = { tab = name; search = "" }, label = { Text(name, style = MaterialTheme.typography.labelSmall) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LIME, selectedLabelColor = BG))
            }
        }
        if (notice.isNotBlank()) Text(notice, Modifier.padding(horizontal = 16.dp), color = LIME)
        when (tab) {
            "Dashboard" -> Dashboard(products, orders, requests)
            "Products" -> {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(search, { search = it }, Modifier.weight(1f), placeholder = { Text("Search products") }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) })
                    Spacer(Modifier.width(6.dp))
                    Button(onClick = { edit = null; pname = ""; category = "General"; price = ""; stock = ""; image = ""; description = ""; dialog = true }) { Icon(Icons.Default.Add, null); Text("Add") }
                }
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    items(products.filter { it.name.contains(search, true) || it.category.contains(search, true) }) { item ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = PANEL)) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { Text(item.name, fontWeight = FontWeight.Bold); Text(item.category, color = MUTED) }
                                    Text("₹" + item.price, color = LIME, fontWeight = FontWeight.Bold)
                                }
                                Text("Stock: " + item.stock, color = if (item.stock <= 5) RED else MUTED)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { edit = item; pname = item.name; category = item.category; price = item.price.toString(); stock = item.stock.toString(); image = item.image; description = item.description; dialog = true }) { Text("Edit") }
                                    OutlinedButton(onClick = { db.collection("products").document(item.id).delete().addOnSuccessListener { notice = "Product deleted." }.addOnFailureListener { notice = it.localizedMessage ?: "Delete failed." } }) { Text("Delete", color = RED) }
                                }
                            }
                        }
                    }
                }
            }
            "Orders" -> {
                OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp), placeholder = { Text("Search customer, phone or order ID") }, singleLine = true)
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    items(orders.filter { it.id.contains(search, true) || it.customer.contains(search, true) || it.phone.contains(search, true) }) { order ->
                        var expanded by remember(order.id) { mutableStateOf(false) }
                        Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = PANEL)) {
                            Column(Modifier.padding(14.dp)) {
                                Text("ORDER #" + order.id.takeLast(8), color = LIME, fontWeight = FontWeight.Bold)
                                Text(order.customer, fontWeight = FontWeight.Bold)
                                Text(order.phone + " • " + order.payment, color = MUTED)
                                Text(order.address, color = MUTED)
                                order.items.forEach { line -> Text("• " + (line["name"] ?: line["productName"] ?: "Item").toString() + " × " + (line["quantity"] ?: line["qty"] ?: 1).toString()) }
                                Text("Total ₹" + order.total, color = LIME, fontWeight = FontWeight.Bold)
                                Box {
                                    OutlinedButton(onClick = { expanded = true }) { Text(order.status + "  ▾") }
                                    DropdownMenu(expanded, { expanded = false }) {
                                        listOf("Pending", "Accepted", "Preparing", "Out for delivery", "Delivered", "Cancelled").forEach { status ->
                                            DropdownMenuItem(text = { Text(status) }, onClick = { expanded = false; changeOrderStatus(db, order, status) { notice = it } })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "Requests" -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                items(requests.filter { it.status == "Requested" }) { req ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = PANEL)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Cancellation request", color = LIME, fontWeight = FontWeight.Bold)
                            Text("Order: " + req.orderId)
                            Text("Customer: " + req.customer)
                            Text("Reason: " + req.reason)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    db.collection("orders").document(req.orderId).get().addOnSuccessListener { d ->
                                        if (!d.exists() || d.getString("customerUid") != req.uid) notice = "Matching customer order not found."
                                        else {
                                            val ord = Order(d.id, d.getString("customerName") ?: "", d.getString("phone") ?: "", d.getString("address") ?: "", d.getString("status") ?: "Pending", d.getDouble("total") ?: 0.0, (d.get("items") as? List<Map<String, Any>>) ?: emptyList(), req.uid, d.getString("paymentMethod") ?: "COD")
                                            changeOrderStatus(db, ord, "Cancelled") { notice = it }
                                            db.collection("cancellationRequests").document(req.id).update("status", "Approved")
                                        }
                                    }
                                }) { Text("Approve") }
                                OutlinedButton(onClick = { db.collection("cancellationRequests").document(req.id).update("status", "Rejected").addOnSuccessListener { notice = "Request rejected." } }) { Text("Reject") }
                            }
                        }
                    }
                }
            }
            "Customers" -> {
                val customers = orders.distinctBy { it.uid }.filter { it.uid.isNotBlank() }
                LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
                    items(customers) { customer ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = PANEL)) {
                            Column(Modifier.padding(14.dp)) {
                                Text(customer.customer, fontWeight = FontWeight.Bold)
                                Text(customer.phone, color = MUTED)
                                Text(orders.count { it.uid == customer.uid }.toString() + " orders • ₹" + orders.filter { it.uid == customer.uid }.sumOf { it.total } + " total", color = LIME)
                            }
                        }
                    }
                }
            }
        }
    }
    if (dialog) {
        AlertDialog(onDismissRequest = { dialog = false }, containerColor = PANEL, title = { Text(if (edit == null) "Add product" else "Edit product") }, text = {
            Column {
                OutlinedTextField(pname, { pname = it }, label = { Text("Product name") })
                OutlinedTextField(category, { category = it }, label = { Text("Category") })
                OutlinedTextField(price, { price = it }, label = { Text("Price (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(stock, { stock = it }, label = { Text("Stock") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(image, { image = it }, label = { Text("Image URL") })
                OutlinedTextField(description, { description = it }, label = { Text("Description") })
            }
        }, confirmButton = {
            Button(onClick = {
                val amount = price.toDoubleOrNull()
                val count = stock.toLongOrNull()
                if (pname.isBlank() || amount == null || count == null || amount < 0 || count < 0) notice = "Enter a name, valid price and stock."
                else {
                    val data = hashMapOf<String, Any>("name" to pname.trim(), "category" to category.trim(), "price" to amount, "stock" to count, "image" to image.trim(), "description" to description.trim())
                    val action = if (edit == null) db.collection("products").add(data) else db.collection("products").document(edit!!.id).update(data)
                    action.addOnSuccessListener { notice = "Product saved."; dialog = false }.addOnFailureListener { notice = it.localizedMessage ?: "Save failed." }
                }
            }) { Text("Save") }
        }, dismissButton = { TextButton(onClick = { dialog = false }) { Text("Cancel") } })
    }
}

@Composable
private fun Dashboard(products: List<Product>, orders: List<Order>, requests: List<CancelReq>) {
    val pending = orders.count { it.status == "Pending" }
    val delivered = orders.count { it.status == "Delivered" }
    val sales = orders.filter { it.status == "Delivered" }.sumOf { it.total }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Dashboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Your store at a glance • live Firebase data", color = MUTED) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Metric("Products", products.size.toString(), Modifier.weight(1f)); Metric("Low stock", products.count { it.stock <= 5 }.toString(), Modifier.weight(1f)) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Metric("Orders", orders.size.toString(), Modifier.weight(1f)); Metric("Pending", pending.toString(), Modifier.weight(1f)) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Metric("Delivered", delivered.toString(), Modifier.weight(1f)); Metric("Sales", "₹" + "%.0f".format(sales), Modifier.weight(1f)) } }
        item { Metric("Cancellation requests", requests.count { it.status == "Requested" }.toString(), Modifier.fillMaxWidth()) }
        item { Text("Recent orders", color = LIME, fontWeight = FontWeight.Bold) }
        items(orders.take(6)) { order ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = PANEL)) {
                Column(Modifier.padding(14.dp)) { Text(order.customer, fontWeight = FontWeight.Bold); Text(order.status + " • ₹" + order.total, color = MUTED); Text(order.id, color = LIME, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}
@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = PANEL2), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp)) { Text(label, color = MUTED); Text(value, color = LIME, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
    }
}
private fun changeOrderStatus(db: FirebaseFirestore, order: Order, status: String, result: (String) -> Unit) {
    val ref = db.collection("orders").document(order.id)
    db.runTransaction { tx ->
        val snapshot = tx.get(ref)
        val deducted = snapshot.getBoolean("stockDeducted") ?: false
        val restored = snapshot.getBoolean("stockRestored") ?: false
        val shouldDeduct = status == "Accepted" && !deducted
        val shouldRestore = status == "Cancelled" && deducted && !restored
        val stockChanges = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Long>>()
        if (shouldDeduct || shouldRestore) {
            order.items.forEach { item ->
                val productId = item["productId"]?.toString() ?: return@forEach
                val quantity = (item["quantity"] as? Number)?.toLong() ?: (item["qty"] as? Number)?.toLong() ?: 1L
                val productRef = db.collection("products").document(productId)
                val product = tx.get(productRef)
                val available = product.getLong("stock") ?: 0L
                if (shouldDeduct && available < quantity) throw IllegalStateException("Not enough stock for " + (item["name"] ?: "an item"))
                stockChanges.add(productRef to if (shouldDeduct) available - quantity else available + quantity)
            }
        }
        stockChanges.forEach { change -> tx.update(change.first, "stock", change.second) }
        if (shouldDeduct) tx.update(ref, mapOf("status" to status, "stockDeducted" to true))
        else if (shouldRestore) tx.update(ref, mapOf("status" to status, "stockRestored" to true))
        else tx.update(ref, "status", status)
        null
    }.addOnSuccessListener { result("Order updated: " + status) }.addOnFailureListener { result(it.localizedMessage ?: "Could not update order.") }
}
