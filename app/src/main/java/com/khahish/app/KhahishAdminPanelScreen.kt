package com.khahish.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth

private data class KhahishAdminSection(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val khahishAdminSections = listOf(
    KhahishAdminSection("Dashboard", "Overview and platform activity", Icons.Default.Dashboard),
    KhahishAdminSection("Users Management", "Search, review and manage accounts", Icons.Default.People),
    KhahishAdminSection("Bans & Restrictions", "Warnings, temporary bans and appeals", Icons.Default.GppBad),
    KhahishAdminSection("Reports & Appeals", "Review reports and moderation cases", Icons.Default.Flag),
    KhahishAdminSection("Stories Management", "Official stories and reported content", Icons.Default.Collections),
    KhahishAdminSection("Broadcast Center", "Chat announcements for all users", Icons.Default.Campaign),
    KhahishAdminSection("Push Notifications", "Global and targeted notifications", Icons.Default.NotificationsActive),
    KhahishAdminSection("Chat & Groups", "Group and messaging moderation", Icons.Default.Chat),
    KhahishAdminSection("Support Center", "User complaints and support replies", Icons.Default.SupportAgent),
    KhahishAdminSection("Analytics", "Usage, registrations and engagement", Icons.Default.Analytics),
    KhahishAdminSection("Admin Security", "Owner access and moderator permissions", Icons.Default.Security),
    KhahishAdminSection("Activity History", "Audit trail of admin actions", Icons.Default.History),
    KhahishAdminSection("App Settings", "Maintenance and feature controls", Icons.Default.Settings)
)

@Composable
fun KhahishAdminPasswordDialog(
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val user = auth.currentUser
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = {
            Box(
                Modifier.size(54.dp).background(Color(0xFFE1F8EF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AdminPanelSettings, null, tint = Green, modifier = Modifier.size(30.dp))
            }
        },
        title = { Text("Owner authentication", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Enter your account password to verify your identity. Only an account with the server-issued admin permission can open this panel.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Account password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && password.isNotBlank(),
                onClick = {
                    val current = user
                    val email = current?.email
                    if (current == null || email.isNullOrBlank()) {
                        error = "Please sign in with your email and password account."
                        return@TextButton
                    }
                    busy = true
                    error = ""
                    val credential = EmailAuthProvider.getCredential(email, password)
                    current.reauthenticate(credential)
                        .addOnSuccessListener {
                            current.getIdToken(true)
                                .addOnSuccessListener { token ->
                                    busy = false
                                    if (token.claims["admin"] == true) {
                                        onUnlocked()
                                    } else {
                                        error = "This account has not been granted Owner Admin permission yet."
                                    }
                                }
                                .addOnFailureListener { failure ->
                                    busy = false
                                    error = failure.localizedMessage ?: "Admin permission could not be verified."
                                }
                        }
                        .addOnFailureListener { failure ->
                            busy = false
                            error = failure.localizedMessage ?: "Password verification failed."
                        }
                }
            ) {
                Text(if (busy) "Verifying…" else "Verify & Open")
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun KhahishAdminPanelScreen(onBack: () -> Unit) {
    var selectedSection by remember { mutableStateOf("Dashboard") }
    val selected = khahishAdminSections.first { it.title == selectedSection }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFF075C4E), Color(0xFF079B78), Color(0xFF18A889))),
                RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
            ).statusBarsPadding().padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("KHAHISH ADMIN", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Owner Control Center", color = Color(0xFFD9FFF1), fontSize = 11.sp)
                    }
                    Surface(color = Color(0x33FFFFFF), shape = RoundedCornerShape(50)) {
                        Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("OWNER", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text("Welcome back, Nikka Ali", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Manage your Khahish community from one place.", color = Color(0xFFE5FFF6), fontSize = 12.sp)
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (selectedSection == "Dashboard") {
                item {
                    Text("Platform overview", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("Live figures will appear after the secure admin data collections are connected.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AdminMetricCard("Total users", "—", Icons.Default.People, Modifier.weight(1f))
                            AdminMetricCard("Online now", "—", Icons.Default.Wifi, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AdminMetricCard("Pending reports", "—", Icons.Default.Flag, Modifier.weight(1f))
                            AdminMetricCard("Support tickets", "—", Icons.Default.SupportAgent, Modifier.weight(1f))
                        }
                    }
                }
                item {
                    Text("Owner controls", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            } else {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(48.dp).background(Color(0xFFE1F8EF), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                            Icon(selected.icon, null, tint = Green, modifier = Modifier.size(25.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(selected.title, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(selected.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                    }
                }
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Security, tint = Green, contentDescription = null, modifier = Modifier.size(25.dp))
                            Text("Secure module setup", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "This section is included in the Admin Panel navigation. Its live actions will be connected to Firebase rules and trusted server functions before being enabled, so ordinary accounts cannot perform owner actions.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            items(khahishAdminSections) { section ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(
                            if (selectedSection == section.title) Color(0xFFE4F8EF) else MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { selectedSection = section.title }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(section.icon, null, tint = if (selectedSection == section.title) Green else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(section.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(section.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AdminMetricCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 2.dp
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, tint = Green, modifier = Modifier.size(21.dp))
            Text(value, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
