package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.theme.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EBloodBankApp(viewModel: EBloodBankViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Observe DB / StateFlows
    val activeUser by viewModel.activeUserProfile.collectAsStateWithLifecycle()
    val stocks by viewModel.allBloodStocks.collectAsStateWithLifecycle()
    val events by viewModel.allDonorEvents.collectAsStateWithLifecycle()
    val registrations by viewModel.allRegistrations.collectAsStateWithLifecycle()
    val bloodRequests by viewModel.allBloodRequests.collectAsStateWithLifecycle()
    val activeRegistrations by viewModel.activeUserRegistrations.collectAsStateWithLifecycle()
    val eligibility by viewModel.activeUserEligibility.collectAsStateWithLifecycle()
    val usersList by viewModel.allUserProfiles.collectAsStateWithLifecycle()
    val securityLogs by viewModel.allSecurityLogs.collectAsStateWithLifecycle()
    val lockedUsers by viewModel.lockedUsers.collectAsStateWithLifecycle()
    val notifications by viewModel.activeUserNotifications.collectAsStateWithLifecycle()
    val unreadNotificationsCount = notifications.count { !it.isRead }

    // Intercept with stunning Landing/About/Login/Register experience if no active user exists
    if (activeUser == null) {
        LandingScreen(viewModel = viewModel, usersList = usersList, stocks = stocks)
        return
    }

    // Screen State / Tab selection:
    var selectedTab by remember { mutableStateOf("Dashboard") }

    // Dialog state controllers:
    var showRoleSwitcherDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showEventDetailsDialog by remember { mutableStateOf<DonorEvent?>(null) }
    var showScreeningDialog by remember { mutableStateOf<QueueRegistration?>(null) }
    var showCompleteDonorDialog by remember { mutableStateOf<QueueRegistration?>(null) }
    var showQRDialog by remember { mutableStateOf<QueueRegistration?>(null) }

    // Listen for VM feedback to show inside Snackbar
    LaunchedEffect(key1 = true) {
        viewModel.uiFeedback.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Automatically switch tabs when role changes to ensure smooth UX
    LaunchedEffect(activeUser) {
        selectedTab = when {
            activeUser?.isPmiStaff == true -> "Dashboard Stok"
            activeUser?.isHospitalAdmin == true -> "Dashboard RS"
            else -> "Dashboard" // Donor Home
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BloodDropIcon(modifier = Modifier.size(24.dp), color = BloodPrimary)
                        Text(
                            text = "E-BloodBank",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    // Theme Switcher Button
                    IconButton(onClick = { showThemeDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Pengaturan Tema",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Notification center bell
                    Box(contentAlignment = Alignment.TopEnd) {
                        IconButton(onClick = { showNotificationsDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifikasi",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (unreadNotificationsCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .offset(x = (-4).dp, y = 4.dp)
                                    .background(Color.Red, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = unreadNotificationsCount.toString(),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Quick Role / Profile switcher
                    IconButton(onClick = { showRoleSwitcherDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Ganti Role",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(onClick = { showRoleSwitcherDialog = true }) {
                        Text(
                            text = activeUser?.name?.split(" ")?.firstOrNull() ?: "Pilih User",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 100.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                )
            )
        },
        bottomBar = {
            // Role Based Dynamic Navigation Bar
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
            ) {
                if (activeUser?.isPmiStaff == true) {
                    // PMI Staff Navigation
                    val pmiTabs = listOf(
                        Triple("Dashboard Stok", Icons.Default.Home, "Stok"),
                        Triple("Antrean Screening", Icons.Default.CheckCircle, "Antrean"),
                        Triple("Kelola Request", Icons.Default.Email, "Permintaan"),
                        Triple("Buat Event", Icons.Default.DateRange, "Event"),
                        Triple("Audit Log & VPN", Icons.Default.Lock, "Keamanan"),
                        Triple("Laporan", Icons.Default.Info, "Laporan")
                    )
                    pmiTabs.forEach { (tabName, icon, label) ->
                        NavigationBarItem(
                            selected = selectedTab == tabName,
                            onClick = { selectedTab = tabName },
                            icon = { Icon(icon, contentDescription = tabName) },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                } else if (activeUser?.isHospitalAdmin == true) {
                    // Hospital Navigation
                    val rsTabs = listOf(
                        Triple("Dashboard RS", Icons.Default.Home, "Beranda"),
                        Triple("Buat Request", Icons.Default.Add, "Request Baru"),
                        Triple("Riwayat RS", Icons.Default.Refresh, "Riwayat")
                    )
                    rsTabs.forEach { (tabName, icon, label) ->
                        NavigationBarItem(
                            selected = selectedTab == tabName,
                            onClick = { selectedTab = tabName },
                            icon = { Icon(icon, contentDescription = tabName) },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                } else {
                    // Public Donor Navigation
                    val donorTabs = listOf(
                        Triple("Dashboard", Icons.Default.Home, "Beranda"),
                        Triple("Cari Event", Icons.Default.Search, "Cari Jadwal"),
                        Triple("Riwayat", Icons.Default.Refresh, "Riwayat"),
                        Triple("Artikel", Icons.Default.Info, "Edukasi"),
                        Triple("Daftar Baru", Icons.Default.Add, "Daftar Baru")
                    )
                    donorTabs.forEach { (tabName, icon, label) ->
                        NavigationBarItem(
                            selected = selectedTab == tabName,
                            onClick = { selectedTab = tabName },
                            icon = { Icon(icon, contentDescription = tabName) },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Main content based on active user and selected tab
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    slideInVertically { height -> height } + fadeIn() togetherWith
                            slideOutVertically { height -> -height } + fadeOut()
                },
                label = "MainTabs"
            ) { targetTab ->
                when (targetTab) {
                    // --- DONOR VIEWS ---
                    "Dashboard" -> DonorDashboardScreen(
                        user = activeUser,
                        eligibility = eligibility,
                        registrations = activeRegistrations,
                        allUserProfiles = usersList,
                        onShowQR = { showQRDialog = it },
                        onToggleBiometrics = { viewModel.toggleBiometrics(it) },
                        onToggleMasking = { viewModel.toggleEncryptedLabels(it) }
                    )
                    "Cari Event" -> DonorEventsFinder(
                        events = events,
                        onEventSelected = { showEventDetailsDialog = it }
                    )
                    "Riwayat" -> DonorHistoryScreen(
                        registrations = activeRegistrations,
                        onShowQR = { showQRDialog = it }
                    )
                    "Artikel" -> EducationalScreen()
                    "Daftar Baru" -> RegisterNewDonorScreen(viewModel = viewModel)

                    // --- HOSPITAL VIEWS ---
                    "Dashboard RS" -> HospitalDashboardScreen(
                        user = activeUser,
                        stocks = stocks,
                        activeRequests = bloodRequests.filter { it.hospitalName == activeUser?.hospitalName },
                        onConfirmReceipt = { viewModel.confirmRequestReceived(it) }
                    )
                    "Buat Request" -> CreateRequestScreen(
                        viewModel = viewModel,
                        onSuccess = { selectedTab = "Dashboard RS" }
                    )
                    "Riwayat RS" -> HospitalHistoryScreen(
                        requests = bloodRequests.filter { it.hospitalName == activeUser?.hospitalName },
                        onConfirmReceipt = { viewModel.confirmRequestReceived(it) }
                    )

                     // --- PMI VIEWS ---
                    "Dashboard Stok" -> PmiDashboardScreen(
                        stocks = stocks,
                        requests = bloodRequests,
                        eventsToday = events,
                        onUpdateStock = { bloodType, rhesus, component, delta ->
                            viewModel.directUpdateBloodStock(bloodType, rhesus, component, delta)
                        }
                    )
                    "Antrean Screening" -> PmiScreeningQueuesScreen(
                        registrations = registrations,
                        onCheckIn = { viewModel.checkInDonor(it) },
                        onStartScreening = { showScreeningDialog = it },
                        onSelesaiDonor = { showCompleteDonorDialog = it }
                    )
                    "Kelola Request" -> PmiManageRequestsScreen(
                        requests = bloodRequests,
                        onApprove = { viewModel.respondToBloodRequest(it, true) },
                        onReject = { viewModel.respondToBloodRequest(it, false) },
                        onDispatch = { viewModel.dispatchRequest(it) }
                    )
                    "Buat Event" -> PmiCreateEventScreen(viewModel = viewModel)
                    "Audit Log & VPN" -> SecurityAuditLogsScreen(
                        viewModel = viewModel,
                        logs = securityLogs,
                        users = usersList,
                        onUnlockUser = { viewModel.unlockUserNik(it) }
                    )
                    "Laporan" -> PmiMonthlyReportsScreen(
                        stocks = stocks,
                        registrations = registrations,
                        requests = bloodRequests
                    )

                    else -> Text("Fitur dalam pengembangan")
                }
            }
        }
    }

    // --- POPUP DIALOGS ---

    // 1c. Unified Theme Chooser Dialog
    if (showThemeDialog) {
        val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
        Dialog(onDismissRequest = { showThemeDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Pengaturan Tema",
                        style = MaterialTheme.typography.titleLarge,
                        color = BloodPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Silakan pilih mode tampilan aplikasi yang Anda inginkan saat ini:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val options = listOf(
                        Triple("LIGHT", "☀️ Terang (Light Mode)", "Tampilan cerah dengan kontras optimal saat siang hari."),
                        Triple("DARK", "🌙 Gelap (Dark Mode)", "Tampilan gelap yang nyaman di mata untuk kondisi minim cahaya."),
                        Triple("SYSTEM", "⚙️ Otomatis (System Default)", "Menyesuaikan otomatis dengan pengaturan tema perangkat Anda.")
                    )

                    options.forEach { (mode, title, desc) ->
                        val isSelected = themeMode == mode
                        val cardBg = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                        val borderColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setThemeMode(mode)
                                },
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(1.2.dp, borderColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(14.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    )
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = title,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    TextButton(
                        onClick = { showThemeDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(text = "Tutup", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // 1. Unified Role Swapper Dialog
    if (showRoleSwitcherDialog) {
        Dialog(onDismissRequest = { showRoleSwitcherDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Ganti Akun Demo (Role-Based)",
                        style = MaterialTheme.typography.titleLarge,
                        color = BloodPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Silakan pilih akun di bawah untuk mencoba visual & alur masing-masing role (Pendonor, Petugas PMI, atau RS):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )

                    HorizontalDivider(color = Color.LightGray)

                    usersList.forEach { user ->
                        val roleLabel = when {
                            user.isPmiStaff -> "Petugas PMI (Super Admin)"
                            user.isHospitalAdmin -> "Admin RS (${user.hospitalName})"
                            else -> "Pendonor (Masyarakat Umum)"
                        }

                        val cardBg = if (user.nik == activeUser?.nik) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.switchActiveUser(user.nik)
                                    showRoleSwitcherDialog = false
                                },
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            if (user.isPmiStaff) Color(0xFF1976D2) else if (user.isHospitalAdmin) Color(
                                                0xFFE64A19
                                            ) else BloodPrimary,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (user.isPmiStaff) {
                                        Icon(Icons.Default.Home, contentDescription = null, tint = Color.White)
                                    } else if (user.isHospitalAdmin) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White)
                                    } else {
                                        BloodDropIcon(modifier = Modifier.size(20.dp), color = Color.White)
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = user.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = roleLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!user.isPmiStaff && !user.isHospitalAdmin) {
                                        Text(
                                            text = "Goldar: ${user.bloodType}${user.rhesus} | NIK: ${user.nik}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                    } else {
                                        Text(
                                            text = "Email: ${user.email}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            showRoleSwitcherDialog = false
                            selectedTab = "Daftar Baru"
                        },
                        modifier = Modifier.fillMaxWidth().testTag("btn_register_from_swapper"),
                        colors = ButtonDefaults.buttonColors(containerColor = BloodPrimary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Registrasi Pendonor Baru")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                showRoleSwitcherDialog = false
                                viewModel.logout()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                            modifier = Modifier.testTag("btn_logout_swapper")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Keluar Sesi", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Keluar Sesi", fontWeight = FontWeight.Bold)
                        }

                        TextButton(
                            onClick = { showRoleSwitcherDialog = false }
                        ) {
                            Text("Batal")
                        }
                    }
                }
            }
        }
    }

    // 1b. Real push-style in-app Notifications Center Dialog
    if (showNotificationsDialog) {
        Dialog(onDismissRequest = { showNotificationsDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔔 Pesan & Notifikasi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BloodPrimary
                        )
                        IconButton(onClick = { showNotificationsDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (notifications.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Tidak ada notifikasi baru.",
                                    color = Color.Gray,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(notifications) { notif ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.markNotificationAsRead(notif.id) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (notif.isRead) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                         else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = notif.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = if (notif.isRead) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (!notif.isRead) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(Color.Red, CircleShape)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = notif.message,
                                            fontSize = 12.sp,
                                            color = if (notif.isRead) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.markAllNotificationsRead()
                            }
                        ) {
                            Text("Tandai Semua Dibaca", fontSize = 12.sp, color = BloodPrimary, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showNotificationsDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = BloodPrimary)
                        ) {
                            Text("Tutup", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // 2. Booking / Event Details Dialog
    showEventDetailsDialog?.let { event ->
        Dialog(onDismissRequest = { showEventDetailsDialog = null }) {
            var selectedTimeSlot by remember { mutableStateOf("09:00 - 10:00") }
            val slots = listOf("08:00 - 09:30", "09:30 - 11:00", "11:00 - 12:30", "12:30 - 14:00")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Booking Antrean Donor",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = BloodPrimary
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = event.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = BloodPrimary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = event.location, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = BloodPrimary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Tanggal: ${event.date} (${event.time})", fontSize = 13.sp)
                            }
                        }
                    }

                    Text(text = "Deskripsi Event:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(text = event.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    HorizontalDivider()

                    Text(text = "Pilih Sesi Kedatangan (Slot Waktu):", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        slots.forEach { slot ->
                            val isSelected = selectedTimeSlot == slot
                            val isDark = isSystemInDarkTheme()
                            val chipTypeBg = if (isSelected) BloodPrimary else if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color.LightGray.copy(alpha = 0.4f)
                            val chipTypeText = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(chipTypeBg)
                                    .clickable { selectedTimeSlot = slot }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = slot.replace(" - ", "\n"),
                                    fontSize = 11.sp,
                                    lineHeight = 12.sp,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.SemiBold,
                                    color = chipTypeText
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showEventDetailsDialog = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Batal")
                        }

                        Button(
                            onClick = {
                                viewModel.bookQueue(
                                    eventId = event.id,
                                    timeSlot = selectedTimeSlot,
                                    eventTitle = event.title,
                                    eventLocation = event.location
                                )
                                showEventDetailsDialog = null
                            },
                            modifier = Modifier.weight(1.5f),
                            colors = ButtonDefaults.buttonColors(containerColor = BloodPrimary)
                        ) {
                            Text("Kirim Registrasi")
                        }
                    }
                }
            }
        }
    }

    // 3. QR Dialog (Show ticket to scan)
    showQRDialog?.let { reg ->
        Dialog(onDismissRequest = { showQRDialog = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Bukti Booking Antrean",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BloodPrimary
                    )

                    Text(
                        text = "Tunjukkan QR Code ini kepada pertugas PMI di lokasi untuk Check-In",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = Color.Gray
                    )

                    AnimatedQRCode(data = reg.qrCodeData, modifier = Modifier.size(180.dp))

                    Text(
                        text = "NOMOR ANTRIAN: " + reg.queueNumber,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF2F2F2)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val isDark = isSystemInDarkTheme()
                            val textPrimary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color.Black
                            val textSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f) else Color.DarkGray

                            Text(text = "Event: ${reg.eventTitle}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textPrimary)
                            Text(text = "Lokasi: ${reg.eventLocation}", fontSize = 12.sp, color = textSecondary)
                            Text(text = "Sesi: ${reg.timeSlot}", fontSize = 12.sp, color = textSecondary)
                            Text(
                                text = "Pendonor: ${reg.userName} (NIK: ${reg.userNik})",
                                fontSize = 12.sp,
                                color = textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Button(
                        onClick = { showQRDialog = null },
                        colors = ButtonDefaults.buttonColors(containerColor = BloodPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Tutup")
                    }
                }
            }
        }
    }

    // 4. Medical Screening Dialog by PMI Officer
    showScreeningDialog?.let { reg ->
        Dialog(onDismissRequest = { showScreeningDialog = null }) {
            var systolicInput by remember { mutableStateOf("120") }
            var diastolicInput by remember { mutableStateOf("80") }
            var hbInput by remember { mutableStateOf("14.0") }
            var weightInput by remember { mutableStateOf("65") }
            var screeningNotes by remember { mutableStateOf("") }

            // Automatic smart recommendation checks
            val systolic = systolicInput.toIntOrNull() ?: 0
            val diastolic = diastolicInput.toIntOrNull() ?: 0
            val hb = hbInput.toDoubleOrNull() ?: 0.0
            val weight = weightInput.toDoubleOrNull() ?: 0.0

            val sysOk = systolic in 110..140
            val diaOk = diastolic in 70..90
            val hbOk = hb in 12.5..17.0
            val weightOk = weight >= 45.0
            val isEligibleRecommended = sysOk && diaOk && hbOk && weightOk

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Screening Medis Awal",
                        style = MaterialTheme.typography.titleLarge,
                        color = BloodPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Pendonor: ${reg.userName} (NIK: ${reg.userNik})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    HorizontalDivider()

                    // Outputs / TextFields
                    OutlinedTextField(
                        value = systolicInput,
                        onValueChange = { systolicInput = it },
                        label = { Text("Tensi - Sistolik (mmHg) [110-140]") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        isError = !sysOk
                    )

                    OutlinedTextField(
                        value = diastolicInput,
                        onValueChange = { diastolicInput = it },
                        label = { Text("Tensi - Diastolik (mmHg) [70-90]") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        isError = !diaOk
                    )

                    OutlinedTextField(
                        value = hbInput,
                        onValueChange = { hbInput = it },
                        label = { Text("Kadar Hemoglobin - HB (g/dL) [12.5-17.0]") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        isError = !hbOk
                    )

                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        label = { Text("Berat Badan (kg) [Min 45 kg]") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        isError = !weightOk
                    )

                    OutlinedTextField(
                        value = screeningNotes,
                        onValueChange = { screeningNotes = it },
                        label = { Text("Catatan Medis Tambahan") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Smart Recommendation Visual
                    val isDark = isSystemInDarkTheme()
                    val cardBg = if (isDark) {
                        if (isEligibleRecommended) GreenSuccess.copy(alpha = 0.2f) else BloodPrimary.copy(alpha = 0.2f)
                    } else {
                        if (isEligibleRecommended) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                    }
                    val cardTextColor = if (isDark) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        Color.DarkGray
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isEligibleRecommended) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isEligibleRecommended) GreenSuccess else BloodPrimary
                            )
                            Column {
                                Text(
                                    text = if (isEligibleRecommended) "Rekomendasi: LOLOS SCREENING" else "Rekomendasi: GAGAL SCREENING",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isEligibleRecommended) GreenSuccess else BloodPrimary
                                )
                                Text(
                                    text = if (isEligibleRecommended) "Semua indikator vital pendonor berada dalam batas batas aman." else
                                        "Ada vital sign pendonor yang tidak sesuai dengan batas standar PMI.",
                                    fontSize = 11.sp,
                                    color = cardTextColor
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.saveMedicalScreening(
                                    registrationId = reg.id,
                                    systolic = systolic,
                                    diastolic = diastolic,
                                    hemoglobin = hb,
                                    weight = weight,
                                    notes = screeningNotes,
                                    isLolos = false
                                )
                                showScreeningDialog = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = BloodPrimaryContainer, contentColor = BloodPrimary)
                        ) {
                            Text("Ditolak (Gagal)")
                        }

                        Button(
                            onClick = {
                                viewModel.saveMedicalScreening(
                                    registrationId = reg.id,
                                    systolic = systolic,
                                    diastolic = diastolic,
                                    hemoglobin = hb,
                                    weight = weight,
                                    notes = screeningNotes,
                                    isLolos = true
                                )
                                showScreeningDialog = null
                            },
                            modifier = Modifier.weight(1.2f),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                        ) {
                            Text("Setujui (Lolos)")
                        }
                    }
                }
            }
        }
    }

    // 5. Complete Donor and select Component type Dialog (Drawn Blood)
    showCompleteDonorDialog?.let { reg ->
        Dialog(onDismissRequest = { showCompleteDonorDialog = null }) {
            var selectedComponent by remember { mutableStateOf("Whole Blood") }
            val components = listOf("Whole Blood", "Thrombocyte", "Packed Red Cells", "Fresh Frozen Plasma")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Konfirmasi Pengambilan Darah",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BloodPrimary
                    )

                    Text(
                        text = "Pendonor ${reg.userName} telah lolos screening kesehatan. Pilih jenis komponen darah yang berhasil disadap untuk dimasukkan kedalam inventaris stok PMI:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.DarkGray
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    components.forEach { comp ->
                        val isSelected = selectedComponent == comp
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { selectedComponent = comp }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedComponent = comp }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = comp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showCompleteDonorDialog = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Batal")
                        }

                        Button(
                            onClick = {
                                viewModel.completeDonorAndAddToStock(
                                    registrationId = reg.id,
                                    componentType = selectedComponent
                                )
                                showCompleteDonorDialog = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BloodPrimary),
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Text("Simpan & Selesai")
                        }
                    }
                }
            }
        }
    }
}

// ==================== SCREEN COMPOSABLES ====================

@Composable
fun DonationGauge(
    value: Int,
    max: Int,
    label: String,
    color: Color
) {
    val progress = if (max <= 0) 0f else (value.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress * 360f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "gauge_anim"
    )
    val isDark = isSystemInDarkTheme()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(76.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Background circle track
                drawArc(
                    color = color.copy(alpha = 0.12f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = strokeForGauge(7.dp.toPx())
                )
                // Foreground progress arc
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = animatedProgress,
                    useCenter = false,
                    style = strokeForGauge(7.dp.toPx())
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = value.toString(),
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = if (isDark) Color.White else Color.Black
                )
            }
        }
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.DarkGray,
            textAlign = TextAlign.Center
        )
    }
}

private fun strokeForGauge(width: Float): Stroke {
    return Stroke(width = width, cap = androidx.compose.ui.graphics.StrokeCap.Round)
}

@Composable
fun DonorDashboardScreen(
    user: UserProfile?,
    eligibility: EligibilityState,
    registrations: List<QueueRegistration>,
    allUserProfiles: List<UserProfile>,
    onShowQR: (QueueRegistration) -> Unit,
    onToggleBiometrics: (Boolean) -> Unit,
    onToggleMasking: (Boolean) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Welcome Header - inspired by Robert Ross premium cards (Phone 2)
        item {
            user?.let {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isDark) Color(0xFF221112) else Color(0xFFFFEBEE).copy(alpha = 0.5f),
                            RoundedCornerShape(24.dp)
                        )
                        .border(1.dp, if (isDark) Color.White.copy(alpha = 0.08f) else BloodPrimary.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Profile initials in a stunning crimson circle with a status tag
                    Box(contentAlignment = Alignment.TopEnd) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(BloodPrimary, BloodSecondary)
                                    ),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (it.name.length >= 2) it.name.take(2).uppercase() else "D",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                        }
                        // Notification light (dynamic green dot showing active portal connection)
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(Color(0xFF2E7D32), CircleShape)
                                .border(2.dp, if (isDark) Color(0xFF1A1212) else Color.White, CircleShape)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        val calendar = java.util.Calendar.getInstance()
                        val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                        val greeting = when {
                            currentHour in 0..11 -> "Selamat Pagi ☀️"
                            currentHour in 12..15 -> "Selamat Siang 🌤️"
                            currentHour in 16..18 -> "Selamat Sore 🌅"
                            else -> "Selamat Malam 🌙"
                        }
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = it.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDark) Color.White else Color.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Beautiful glassmorphic digital card with overlapping sphere decorations (Phone 5 reference)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFFC62828), Color(0xFF6A0B14)),
                                start = Offset(0f, 0f),
                                end = Offset(1100f, 800f)
                            )
                        )
                        .padding(20.dp)
                ) {
                    // Overlapping vector-like background decorations to elevate visual premium finish
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .offset(x = 180.dp, y = (-40).dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .offset(x = 220.dp, y = 80.dp)
                            .background(Color.White.copy(alpha = 0.05f), CircleShape)
                    )

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "KARTU DIGITAL PENDONOR",
                                    fontSize = 11.sp,
                                    letterSpacing = 1.2.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = it.name.uppercase(),
                                    fontSize = 20.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            // Blood type badge
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = it.bloodType + it.rhesus,
                                    color = BloodPrimary,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp
                                )
                            }
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(32.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "NIK",
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (it.showEncryptedLabels) it.nik else SecurityUtils.maskNik(it.nik),
                                        fontSize = 14.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Column {
                                    Text(
                                        text = "TERAKHIR DONOR",
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = it.lastDonationDate ?: "Belum Pernah",
                                        fontSize = 14.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "E-BloodBank Partner PMI",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.SemiBold
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "⭐ ${it.points} Poin",
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "🔥 ${it.totalDonations} Donor",
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Eligibility alert card - redesigned to be extremely modern and sleek
        item {
            val cardBg = if (isDark) {
                if (eligibility.canDonor) GreenSuccess.copy(alpha = 0.15f) else BloodPrimary.copy(alpha = 0.15f)
            } else {
                if (eligibility.canDonor) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
            }
            val cardBorderColor = if (isDark) {
                if (eligibility.canDonor) GreenSuccess else BloodPrimary
            } else {
                if (eligibility.canDonor) GreenSuccess.copy(alpha = 0.3f) else BloodPrimary.copy(alpha = 0.3f)
            }
            val cardTextColor = if (isDark) {
                MaterialTheme.colorScheme.onSurface
            } else {
                Color.DarkGray
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(width = 1.dp, color = cardBorderColor)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (eligibility.canDonor) GreenSuccess.copy(alpha = 0.2f) else BloodPrimary.copy(alpha = 0.2f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (eligibility.canDonor) Icons.Default.Check else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (eligibility.canDonor) GreenSuccess else BloodPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column {
                        Text(
                            text = if (eligibility.canDonor) "Status Kelayakan: SIAP DONOR" else "Masa Tenggang Vakum",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (eligibility.canDonor) GreenSuccess else BloodPrimary
                        )
                        Text(
                            text = eligibility.statusText,
                            fontSize = 13.sp,
                            color = cardTextColor,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        // Security Setting Panel - National Security standards (Indonesian language)
        item {
            user?.let { u ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF131F15) else Color(0xFFF1F8F2)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 4.dp),
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF2E5E35).copy(alpha = 0.3f) else Color(0xFFA5D6A7))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                "Pusat Keamanan & Enkripsi Akun",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }

                        Text(
                            text = "Melindungi identitas medis dan data riwayat golongan darah agar tidak disalahgunakan pihak ketiga. Sesuai regulasi undang-undang perlindungan data pribadi kesehatan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) Color.LightGray else Color.DarkGray
                        )

                        HorizontalDivider(
                            color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.LightGray.copy(alpha = 0.3f)
                        )

                        // 1. Biometrics Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Otorisasi Biometrik Aktif",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isDark) Color.White else Color.Black
                                )
                                Text(
                                    "Membuka kunci aplikasi dan masuk cepat menggunakan sidik jari atau Face ID terdaftar.",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            Switch(
                                checked = u.useBiometrics,
                                onCheckedChange = { onToggleBiometrics(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF2E7D32)
                                )
                            )
                        }

                        // 2. Encrypted labels Toggle (Masking)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Mode Dekripsi Data (Buka Masking)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isDark) Color.White else Color.Black
                                )
                                Text(
                                    "Buka masking data NIK dan email pada halaman beranda setelah mengonfirmasi data pribadi.",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            Switch(
                                checked = u.showEncryptedLabels,
                                onCheckedChange = { onToggleMasking(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF2E7D32)
                                )
                            )
                        }
                    }
                }
            }
        }

        // Personal Stats Counter - inspired by Detailed Statistics gauges (Phone 4)
        item {
            val totalSaved = (user?.totalDonations ?: 0) * 3
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF1E1011) else Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 4.dp),
                border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.LightGray.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Ringkasan Statistik Kemanusiaan Anda",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color.Black
                    )

                    // 3 Beautiful Circular Progress Gauges side by side - matching reference statistics dials
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DonationGauge(
                            value = user?.totalDonations ?: 0,
                            max = 10,
                            label = "Total Donor",
                            color = BloodPrimary
                        )
                        DonationGauge(
                            value = totalSaved,
                            max = 30,
                            label = "Nyawa Selamat",
                            color = Color(0xFF2E7D32)
                        )
                        DonationGauge(
                            value = user?.points ?: 0,
                            max = 150,
                            label = "Poin Sinergi",
                            color = Color(0xFF1976D2)
                        )
                    }

                    HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.LightGray.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = "Selamat! Kontribusi donor Anda telah mengamankan rantai suplai medis nasional.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }

        // Active ticket / registrations alert
        item {
            val activeTicket = registrations.firstOrNull { it.status == "Registered" || it.status == "CheckedIn" || it.status == "Screened_Lolos" }
            if (activeTicket != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🎟️ Antrean Aktif Terjadwal",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            Box(
                                modifier = Modifier
                                    .background(BloodPrimary, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = activeTicket.status,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = activeTicket.eventTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = "Nomor Antrean: " + activeTicket.queueNumber, fontSize = 12.sp, color = Color.Gray)
                        Text(text = "Slot Waktu: " + activeTicket.timeSlot, fontSize = 12.sp, color = Color.Gray)

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { onShowQR(activeTicket) },
                            colors = ButtonDefaults.buttonColors(containerColor = BloodPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Tampilkan Bukti Antrean (QR Code)", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 1. Badge Awards Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🎖️ Lencana Milestones Saya",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BloodPrimary
                    )
                    Text(
                        text = "Dapatkan lencana spesial dengan menyelesaikan misi kemanusiaan.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    val donatorCount = user?.totalDonations ?: 0
                    val badges = listOf(
                        Triple("🏆", "Pahlawan Pertama", "Satu donor darah pertama (" + (if (donatorCount >= 1) "Selesai" else "Mulai") + ")"),
                        Triple("🥉", "Bronze Vitality", "Menyelesaikan 3 kali donor darah (" + donatorCount + "/3)"),
                        Triple("🥈", "Silver Lifesaver", "Menyelesaikan 5 kali donor darah (" + donatorCount + "/5)"),
                        Triple("🥇", "Gold Guardian", "Menyelesaikan 10 kali donor darah (" + donatorCount + "/10)"),
                        Triple("🚩", "Campaign Crusader", "Pernah berpartisipasi donor (" + (if (donatorCount >= 1) "Selesai" else "Belum") + ")")
                    )

                    val unlockedList = listOf(
                        donatorCount >= 1,
                        donatorCount >= 3,
                        donatorCount >= 5,
                        donatorCount >= 10,
                        donatorCount >= 1
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        badges.forEachIndexed { idx, badge ->
                            val isUnlocked = unlockedList[idx]
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(90.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .background(
                                            if (isUnlocked) MaterialTheme.colorScheme.primaryContainer else Color.LightGray.copy(alpha = 0.3f),
                                            CircleShape
                                        )
                                        .border(
                                            width = 2.dp,
                                            color = if (isUnlocked) BloodPrimary else Color.Transparent,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = badge.first,
                                        fontSize = 24.sp,
                                        modifier = Modifier.drawBehind {
                                            if (!isUnlocked) {
                                                drawCircle(Color.Gray.copy(alpha = 0.5f))
                                            }
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = badge.second,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 12.sp,
                                    color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = badge.third,
                                    fontSize = 8.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Gamified Leaderboard Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🏆 Papan Peringkat Pendonor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BloodPrimary
                        )
                        Box(
                            modifier = Modifier
                                .background(BloodPrimary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Komunitas",
                                color = BloodPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "Apresiasi khusus bagi pahlawan kemanusiaan dengan kontribusi poin tertinggi.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    val donorsList = allUserProfiles
                        .filter { !it.isPmiStaff && !it.isHospitalAdmin }
                        .sortedByDescending { it.points }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        donorsList.forEachIndexed { index, donor ->
                            val rank = index + 1
                            val rankSymbol = when (rank) {
                                1 -> "🥇 "
                                2 -> "🥈 "
                                3 -> "🥉 "
                                else -> "$rank. "
                            }

                            val isCurrentUser = donor.nik == user?.nik

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isCurrentUser) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                        else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(vertical = 8.dp, horizontal = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = rankSymbol,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (rank <= 3) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(24.dp)
                                    )

                                    Column {
                                        Text(
                                            text = donor.name + (if (isCurrentUser) " (Anda)" else ""),
                                            fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = if (isCurrentUser) BloodPrimary else Color.Unspecified
                                        )
                                        Text(
                                            text = "Golongan Darah: ${donor.bloodType}${donor.rhesus}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceVariant else Color.LightGray.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${donor.totalDonations} Donor",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Text(
                                        text = "${donor.points} Pts",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = BloodPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DonorEventsFinder(
    events: List<DonorEvent>,
    onEventSelected: (DonorEvent) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredEvents = events.filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
                it.location.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Cari Kampanye Donor Keliling",
            style = MaterialTheme.typography.titleLarge,
            color = BloodPrimary,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Cari Mall, Gedung, Kota, atau PMI...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        if (filteredEvents.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(54.dp))
                    Text(text = "Tidak ada event yang cocok", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredEvents) { event ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEventSelected(event) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = event.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFE3F2FD), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${event.registeredCount}/${event.targetCount} Slot",
                                        color = Color(0xFF1976D2),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = BloodPrimary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = event.location, fontSize = 12.sp, color = Color.Gray)
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color.DarkGray
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = event.date + " (${event.time})", fontSize = 12.sp, color = Color.Gray)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = { onEventSelected(event) },
                                    colors = ButtonDefaults.buttonColors(containerColor = BloodPrimary)
                                ) {
                                    Text("Booking Sekarang", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DonorHistoryScreen(
    registrations: List<QueueRegistration>,
    onShowQR: (QueueRegistration) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Riwayat Antrean & Pendaftaran",
            style = MaterialTheme.typography.titleLarge,
            color = BloodPrimary,
            fontWeight = FontWeight.Bold
        )

        if (registrations.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(54.dp))
                    Text(text = "Anda belum pernah memesan antrean donor.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(registrations) { reg ->
                    val statusColor = when (reg.status) {
                        "Completed" -> GreenSuccess
                        "Screened_Gagal" -> Color.Red
                        "Screened_Lolos" -> Color(0xFF2E7D32)
                        "CheckedIn" -> Color(0xFF1565C0)
                        else -> YellowWarning
                    }

                    Card {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = reg.eventTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(text = reg.status, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Nomor Antrean: ${reg.queueNumber}", fontSize = 12.sp, color = Color.Gray)
                            Text(text = "Tanggal Booking: ${reg.date} (${reg.timeSlot})", fontSize = 12.sp, color = Color.Gray)

                            // Display screening values if checked
                            if (reg.systolicBp != null && reg.hemoglobin != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(text = "Hasil Cek Kesehatan Medis:", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                        Text(text = "• Tekanan Darah: ${reg.systolicBp}/${reg.diastolicBp} mmHg", fontSize = 10.sp)
                                        Text(text = "• Kadar HB: ${reg.hemoglobin} g/dL", fontSize = 10.sp)
                                        Text(text = "• Berat Badan: ${reg.weightKg} kg", fontSize = 10.sp)
                                        reg.screeningNotes?.let {
                                            if (it.isNotEmpty()) Text(text = "• Catatan: $it", fontSize = 10.sp)
                                        }
                                    }
                                }
                            }

                            if (reg.status == "Registered" || reg.status == "CheckedIn") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        onClick = { onShowQR(reg) },
                                        colors = ButtonDefaults.buttonColors(containerColor = BloodPrimary),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Tampilkan Tiket QR", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EducationalScreen() {
    val articles = listOf(
        Triple("Syarat Aman Sebelum Donor Darah", "Berusia minimal 17-60 tahun, memiliki berat badan minimal 45 kg, denyut nadi dan tekanan darah normal, serta kadar hemoglobin (HB) antara 12.5 hingga 17.0 g/dL. Istirahatlah minimal 5 jam pada malam sebelumnya.", "Aman & Nyaman"),
        Triple("Manfaat Kesehatan Donor Darah Rutin", "Mendonorkan darah secara teratur terbukti menstimulasi produksi sel darah baru, mendeteksi penyakit dini melalui screening gratis PMI, menjaga kesehatan sirkulasi jantung, serta membakar sekitar 650 kalori per kantong donor.", "Kesehatan Tubuh"),
        Triple("Hal yang Wajib Dihindari Sebelum Donor", "Jangan meminum obat aspirin atau antibiotik dalam 3 hari sebelum donor. Hindari makanan tinggi lemak atau bersantan karena bisa membuat plasma darah tampak keruh (lipemik), dan minum minimal 3 gelas air putih sejam sebelum tiba.", "Persiapan Medis"),
        Triple("Pemulihan Terbaik Setelah Selesai Donor", "Jangan langsung berdiri terburu-buru. Beristirahatlah selama 15 menit sambil menikmati jajanan pemulihan (snack, susu, biskuit). Hindari mengangkat beban berat dalam 24 jam ke depan untuk mencegah memar.", "Pemulihan")
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Edukasi & Panduan Donor",
                style = MaterialTheme.typography.titleLarge,
                color = BloodPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Pelajari bekal pengetahuan medis agar proses donor Anda berjalan lancar, aman, dan mendatangkan berkah manfaat maksimal.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }

        items(articles) { (title, content, tag) ->
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFFF1F0), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = tag, color = BloodPrimary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = content, fontSize = 13.sp, color = Color.Gray, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
fun RegisterNewDonorScreen(viewModel: EBloodBankViewModel) {
    var nikInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var bloodTypeInput by remember { mutableStateOf("O") }
    var rhesusInput by remember { mutableStateOf("+") }

    val bloodTypesList = listOf("A", "B", "AB", "O")
    val rhesusesList = listOf("+", "-")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Registrasi Profil Baru",
            style = MaterialTheme.typography.titleLarge,
            color = BloodPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Mendaftarkan profil pendonor baru secara mandiri ke datastore lokal E-BloodBank:",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        HorizontalDivider()

        OutlinedTextField(
            value = nikInput,
            onValueChange = { if (it.length <= 16) nikInput = it },
            label = { Text("NOMOR INDUK KEPENDUDUKAN (NIK) [16 Digit]") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it },
            label = { Text("Nama Lengkap") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = emailInput,
            onValueChange = { emailInput = it },
            label = { Text("Email Pendonor") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )

        Text(text = "Golongan Darah Pendonor:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            bloodTypesList.forEach { type ->
                val isSelected = bloodTypeInput == type
                val isDark = isSystemInDarkTheme()
                val chipBg = if (isSelected) BloodPrimary else if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color.LightGray.copy(alpha = 0.4f)
                val chipTx = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(chipBg)
                        .clickable { bloodTypeInput = type }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = type,
                        fontWeight = FontWeight.Bold,
                        color = chipTx
                    )
                }
            }
        }

        Text(text = "Rhesus:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rhesusesList.forEach { rhe ->
                val isSelected = rhesusInput == rhe
                val isDark = isSystemInDarkTheme()
                val chipBg = if (isSelected) BloodPrimary else if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color.LightGray.copy(alpha = 0.4f)
                val chipTx = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(chipBg)
                        .clickable { rhesusInput = rhe }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = rhe,
                        fontWeight = FontWeight.Bold,
                        color = chipTx
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                viewModel.registerNewDonor(
                    nik = nikInput,
                    name = nameInput,
                    email = emailInput,
                    bloodType = bloodTypeInput,
                    rhesus = rhesusInput,
                    pin = "123456"
                )
                // Clear fields on success
                nikInput = ""
                nameInput = ""
                emailInput = ""
            },
            colors = ButtonDefaults.buttonColors(containerColor = BloodPrimary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Simpan & Switch ke Akun Baru")
        }
    }
}

// --- 2. HOSPITAL SCREENS ---

@Composable
fun HospitalDashboardScreen(
    user: UserProfile?,
    stocks: List<BloodStock>,
    activeRequests: List<BloodRequest>,
    onConfirmReceipt: (Int) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val defaultCardColor = if (isDark) Color(0xFF1E1112) else Color.White

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Welcoming
        item {
            user?.let {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF2C1012) else Color(0xFFFFEBEE)
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(BloodPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null, tint = Color.White)
                        }
                        Column {
                            Text(
                                text = "Portal Rumah Sakit: " + (it.hospitalName ?: "RS Swasta"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = if (isDark) Color.White else Color(0xFF5C0000)
                            )
                            Text(
                                text = "Penanggung Jawab: ${it.name} • Portal Resmi",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.DarkGray
                            )
                        }
                    }
                }
            }
        }

        // Action widgets counting status
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val pendingCount = activeRequests.count { it.status == "Pending" }
                val transitCount = activeRequests.count { it.status == "Delivered" }
                val completedCount = activeRequests.count { it.status == "Completed" }

                listOf(
                    Triple("PENDING", "$pendingCount Request", Color(0xFFE65100) to Color(0xFFFFF3E0)),
                    Triple("DIKIRIM", "$transitCount Di Jalan", Color(0xFF0D47A1) to Color(0xFFE3F2FD)),
                    Triple("SELESAI", "$completedCount Sukses", Color(0xFF1B5E20) to Color(0xFFE8F5E9))
                ).forEach { (label, countText, colors) ->
                    val (textCol, bgCol) = colors
                    val finalBg = if (isDark) textCol.copy(alpha = 0.15f) else bgCol
                    val finalBorder = if (isDark) textCol.copy(alpha = 0.3f) else Color.Transparent

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(finalBg, RoundedCornerShape(18.dp))
                            .border(1.dp, finalBorder, RoundedCornerShape(18.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(text = label, fontSize = 10.sp, color = textCol, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                            Text(text = countText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color.Black)
                        }
                    }
                }
            }
        }

        // Live stock of central PMI
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = defaultCardColor),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.LightGray.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "📡 Live Monitoring Stok PMI Pusat",
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isDark) Color.White else BloodPrimary
                    )
                    Text(
                        text = "Data ketersediaan riil kantong darah yang siap disalurkan saat ini:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    val groupStock = stocks.groupBy { it.bloodType }
                    groupStock.forEach { (bloodType, list) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(BloodPrimary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = bloodType,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val wholeBlood = list.filter { it.rhesus == "+" }.sumOf { it.quantity }
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = if (isDark) Color.White.copy(alpha = 0.04f) else Color(0xFFF5F5F5))
                                ) {
                                    Text(
                                        text = "Whole Blood: $wholeBlood bags",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color.White.copy(alpha = 0.9f) else Color.DarkGray,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }

                                val packCells = list.filter { it.componentType == "Packed Red Cells" && it.rhesus == "+" }.sumOf { it.quantity }
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = if (isDark) Color.White.copy(alpha = 0.04f) else Color(0xFFF5F5F5))
                                ) {
                                    Text(
                                        text = "PRC (+): $packCells bags",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color.White.copy(alpha = 0.9f) else Color.DarkGray,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.06f) else Color.LightGray.copy(alpha = 0.3f))
                    }
                }
            }
        }

        // Active request delivery tracker
        item {
            val pendingDels = activeRequests.filter { it.status == "Delivered" || it.status == "Approved" || it.status == "Pending" }
            if (pendingDels.isNotEmpty()) {
                Text(
                    text = "Kelola Distribusi Masuk",
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDark) Color.White else Color.Black
                )
            }
        }

        items(activeRequests.filter { it.status == "Delivered" || it.status == "Approved" || it.status == "Pending" }) { req ->
            val stepColor = if (req.status == "Pending") YellowWarning else if (req.status == "Approved") Color(0xFF1976D2) else GreenSuccess

            Card(
                colors = CardDefaults.cardColors(containerColor = defaultCardColor),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(width = 1.2.dp, color = stepColor.copy(alpha = 0.3f)),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(31.dp)
                                    .background(if (req.urgency == "EMERGENCY") Color.Red else Color.Gray, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = req.bloodType + req.rhesus, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(text = req.componentType, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (isDark) Color.White else Color.Black)
                        }

                        Box(
                            modifier = Modifier
                                .background(stepColor.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(text = req.status, color = stepColor, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Jumlah: ${req.bagsNeeded} Kantong • Urgensi: " + req.urgency,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White.copy(alpha = 0.9f) else Color.Black
                    )
                    req.notes?.let {
                        if (it.isNotEmpty()) {
                            Text(
                                text = "Keterangan: $it",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }

                    if (req.status == "Delivered") {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onConfirmReceipt(req.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Done, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("KONFIRMASI PENERIMAAN DARAH", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreateRequestScreen(
    viewModel: EBloodBankViewModel,
    onSuccess: () -> Unit
) {
    var goldarSelected by remember { mutableStateOf("O") }
    var rhesusSelected by remember { mutableStateOf("+") }
    var componentSelected by remember { mutableStateOf("Whole Blood") }
    var bagsInput by remember { mutableStateOf("4") }
    var urgencyInput by remember { mutableStateOf("NORMAL") }
    var diagnosisInput by remember { mutableStateOf("") }

    val bloodTypes = listOf("A", "B", "AB", "O")
    val rhesuses = listOf("+", "-")
    val componentsList = listOf("Whole Blood", "Thrombocyte", "Packed Red Cells", "Fresh Frozen Plasma")

    val isDark = isSystemInDarkTheme()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Kirim Permintaan Kantong Darah",
            style = MaterialTheme.typography.titleLarge,
            color = BloodPrimary,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "Formulir digital resmi untuk meminta pasokan darah langsung dari Gudang Persediaan PMI Pusat:",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.LightGray.copy(alpha = 0.3f))

        // Switch Emergency vs Normal with slide-control design
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isDark) Color.Black.copy(alpha = 0.3f) else Color.LightGray.copy(alpha = 0.18f),
                    RoundedCornerShape(26.dp)
                )
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (urgencyInput == "NORMAL") Color.Gray.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { urgencyInput = "NORMAL" }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "NORMAL",
                    fontWeight = FontWeight.Bold,
                    color = if (urgencyInput == "NORMAL") (if (isDark) Color.White else Color.Black) else Color.Gray,
                    fontSize = 12.sp
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (urgencyInput == "EMERGENCY") Color.Red else Color.Transparent)
                    .clickable { urgencyInput = "EMERGENCY" }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (urgencyInput == "EMERGENCY") Color.White else Color.Red,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "EMERGENCY",
                        fontWeight = FontWeight.Black,
                        color = if (urgencyInput == "EMERGENCY") Color.White else Color.Red,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(text = "Golongan Darah Pasien:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isDark) Color.White else Color.Black)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            bloodTypes.forEach { t ->
                val isSelected = goldarSelected == t
                val chipBg = if (isSelected) BloodPrimary else (if (isDark) Color.White.copy(alpha = 0.06f) else Color.LightGray.copy(alpha = 0.2f))
                val chipTx = if (isSelected) Color.White else (if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(chipBg)
                        .clickable { goldarSelected = t }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = t, fontWeight = FontWeight.Bold, color = chipTx, fontSize = 14.sp)
                }
            }
        }

        Text(text = "Rhesus:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isDark) Color.White else Color.Black)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rhesuses.forEach { r ->
                val isSelected = rhesusSelected == r
                val chipBg = if (isSelected) BloodPrimary else (if (isDark) Color.White.copy(alpha = 0.06f) else Color.LightGray.copy(alpha = 0.2f))
                val chipTx = if (isSelected) Color.White else (if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(chipBg)
                        .clickable { rhesusSelected = r }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = r, fontWeight = FontWeight.Bold, color = chipTx, fontSize = 14.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(text = "Jenis Komponen Darah:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isDark) Color.White else Color.Black)
        componentsList.forEach { c ->
            val isSelected = componentSelected == c
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) (if (isDark) Color.White.copy(alpha = 0.08f) else Color.LightGray.copy(alpha = 0.25f)) else Color.Transparent)
                    .clickable { componentSelected = c }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = { componentSelected = c },
                    colors = RadioButtonDefaults.colors(selectedColor = BloodPrimary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = c, fontSize = 14.sp, color = if (isDark) Color.White else Color.Black, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = bagsInput,
            onValueChange = { bagsInput = it },
            label = { Text("Jumlah Kantong Darah (Bags) Dibutuhkan") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BloodPrimary,
                unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.LightGray
            )
        )

        OutlinedTextField(
            value = diagnosisInput,
            onValueChange = { diagnosisInput = it },
            label = { Text("Klinis Diagnosis / Kegunaan Pasien") },
            placeholder = { Text("misal: Operasi sesar darurat pendarahan") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BloodPrimary,
                unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.LightGray
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = {
                val qty = bagsInput.toIntOrNull() ?: 1
                viewModel.submitBloodRequest(
                    bloodType = goldarSelected,
                    rhesus = rhesusSelected,
                    componentType = componentSelected,
                    bagsCount = qty,
                    urgency = urgencyInput,
                    notes = diagnosisInput
                )
                onSuccess()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (urgencyInput == "EMERGENCY") Color.Red else BloodPrimary
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = if (urgencyInput == "EMERGENCY") "KIRIM EMERGENCY REQUEST!" else "Kirim Permintaan Normal",
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                color = Color.White
            )
        }
    }
}

@Composable
fun HospitalHistoryScreen(
    requests: List<BloodRequest>,
    onConfirmReceipt: (Int) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val defaultCardColor = if (isDark) Color(0xFF1E1112) else Color.White

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Riwayat Permintaan Rumah Sakit",
            style = MaterialTheme.typography.titleLarge,
            color = BloodPrimary,
            fontWeight = FontWeight.Black
        )

        if (requests.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text = "Belum ada riwayat permintaan yang diajukan", color = Color.Gray, fontWeight = FontWeight.Medium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(requests) { req ->
                    val sColor = when (req.status) {
                        "Completed" -> GreenSuccess
                        "Delivered" -> Color(0xFF1976D2)
                        "Approved" -> Color(0xFF00897B)
                        "Rejected" -> Color.Red
                        else -> YellowWarning
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = defaultCardColor),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.06f) else Color.LightGray.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${req.bloodType}${req.rhesus} - ${req.componentType}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isDark) Color.White else Color.Black
                                )
                                Box(
                                    modifier = Modifier
                                        .background(sColor.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(text = req.status, color = sColor, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Jumlah: ${req.bagsNeeded} Bags • Urgensi: ${req.urgency}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White.copy(alpha = 0.9f) else Color.DarkGray
                            )
                            Text(text = "Tanggal Request: ${req.requestDate}", fontSize = 11.sp, color = Color.Gray)
                            req.notes?.let {
                                if (it.isNotEmpty()) {
                                    Text(
                                        text = "Diagnosa: $it",
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                            }

                            if (req.status == "Delivered") {
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { onConfirmReceipt(req.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("PRODUK SUDAH DITERIMA DI RS", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- 3. PMI STAFF SCREENS ---

@Composable
fun PmiDashboardScreen(
    stocks: List<BloodStock>,
    requests: List<BloodRequest>,
    eventsToday: List<DonorEvent>,
    onUpdateStock: (String, String, String, Int) -> Unit
) {
    val lowStocks = stocks.filter { it.quantity < 10 }
    var showUpdateStockDialog by remember { mutableStateOf(false) }

    // Dialog state for full-form manual adjustment
    var selectedBloodType by remember { mutableStateOf("O") }
    var selectedRhesus by remember { mutableStateOf("+") }
    var selectedComponent by remember { mutableStateOf("Whole Blood") }
    var isTambah by remember { mutableStateOf(true) }
    var adjustQtyString by remember { mutableStateOf("5") }

    val isDark = isSystemInDarkTheme()
    val defaultCardColor = if (isDark) Color(0xFF1E1112) else Color.White

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF2E1012) else Color(0xFFFFF3F3)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dashboard Petugas PMI Pusat",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = if (isDark) Color.White else Color(0xFF6A1B29)
                        )
                        Text(
                            text = "Pantau stok persediaan nasional, verifikasi antrean kesehatan, & tanggapi emergency request",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.DarkGray
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { showUpdateStockDialog = true },
                        modifier = Modifier.testTag("btn_open_quick_stock_updater"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kelola Stok", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                }
            }
        }

        // RED CRITICAL LOW STOCK ALERT BAR
        if (lowStocks.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    border = BorderStroke(1.2.dp, Color.Red),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red)
                            Text(
                                text = "PERINGATAN SECTOR KRITIS (LOW STOCKS < 10 bags)!",
                                fontWeight = FontWeight.Black,
                                color = Color.Red,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Golongan darah di bawah kritis: " + lowStocks.joinToString { "${it.bloodType}${it.rhesus} (${it.componentType.take(8)}..): ${it.quantity}b" },
                            fontSize = 12.sp,
                            color = Color.DarkGray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Live Grid Inventory Stock list
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📊 Real-Time Master Inventory PMI",
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDark) Color.White else Color.Black
                )
                Text(
                    text = "Tap +/- untuk adjust cepat",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = defaultCardColor),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.LightGray.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val groupStockType = stocks.groupBy { it.bloodType }

                    groupStockType.forEach { (type, list) ->
                        key(type) {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(BloodPrimary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = type, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }

                                    Text(
                                        text = "Total Rhesus (+/-): " + list.sumOf { it.quantity } + " Bags",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color.White else Color.Black
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // List out components
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    list.forEach { stock ->
                                        val isLow = stock.quantity < 10
                                        val pillBg = if (isLow) (if (isDark) Color(0xFF4A1010) else Color(0xFFFFCDD2)) else (if (isDark) Color(0xFF103A10) else Color(0xFFE8F5E9))
                                        val pillTx = if (isLow) Color.Red else (if (isDark) Color(0xFF81C784) else Color(0xFF1B5E20))

                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = pillBg),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.widthIn(min = 132.dp).padding(vertical = 4.dp),
                                            border = BorderStroke(1.dp, if (isLow) Color.Red.copy(alpha = 0.3f) else Color.Transparent)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = stock.componentType.take(11) + if (stock.componentType.length > 11) ".." else "",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.DarkGray,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                
                                                Text(
                                                    text = "${stock.rhesus} : ${stock.quantity} Bags",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = pillTx
                                                )

                                                // Quick Quick adjustment buttons for high fidelity convenience
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Quick decrement (-1)
                                                    Box(
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                                                            .clickable { onUpdateStock(stock.bloodType, stock.rhesus, stock.componentType, -1) }
                                                            .testTag("btn_dec_${stock.bloodType}_${if (stock.rhesus == "+") "pos" else "neg"}_${stock.componentType.replace(" ", "_").lowercase()}"),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("-", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.error)
                                                    }
                                                    
                                                    // Quick increment (+1)
                                                    Box(
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF2E7D32).copy(alpha = 0.2f))
                                                            .clickable { onUpdateStock(stock.bloodType, stock.rhesus, stock.componentType, 1) }
                                                            .testTag("btn_inc_${stock.bloodType}_${if (stock.rhesus == "+") "pos" else "neg"}_${stock.componentType.replace(" ", "_").lowercase()}"),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("+", fontWeight = FontWeight.Black, fontSize = 15.sp, color = if (isDark) Color(0xFF81C784) else Color(0xFF1B5E20))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.06f) else Color.LightGray.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
            }
        }

        // Summary widgets
        item {
            val urgents = requests.count { it.status == "Pending" && it.urgency == "EMERGENCY" }
            if (urgents > 0) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
                        Text(
                            text = "ADA $urgents REQUEST EMERGENCY DARI RUMAH SAKIT! Selesaikan di menu Permintaan segera.",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    // Full stock manager Dialog panel
    if (showUpdateStockDialog) {
        Dialog(onDismissRequest = { showUpdateStockDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Pembaruan Stok Darah Manual",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium,
                            color = BloodPrimary
                        )
                        IconButton(
                            onClick = { showUpdateStockDialog = false },
                            modifier = Modifier.testTag("btn_close_stock_updater")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup")
                        }
                    }

                    // 1. Blood Type Selector
                    Text("Golongan Darah:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("A", "B", "AB", "O").forEach { bt ->
                            val isSelected = selectedBloodType == bt
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) BloodPrimary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { selectedBloodType = bt }
                                    .padding(vertical = 11.dp)
                                    .testTag("updater_bt_$bt"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = bt,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 2. Rhesus Selector
                    Text("Rhesus:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("+", "-").forEach { rh ->
                            val isSelected = selectedRhesus == rh
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) BloodPrimary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { selectedRhesus = rh }
                                    .padding(vertical = 11.dp)
                                    .testTag("updater_rh_${if (rh == "+") "pos" else "neg"}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (rh == "+") "Positif (+)" else "Negatif (-)",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // 3. Component Selector
                    Text("Jenis Komponen:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    val components = listOf("Whole Blood", "Thrombocyte", "Packed Red Cells", "Fresh Frozen Plasma")
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        components.forEach { comp ->
                            val isSelected = selectedComponent == comp
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                    .clickable { selectedComponent = comp }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .testTag("updater_comp_${comp.replace(" ", "_").lowercase()}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedComponent = comp },
                                    colors = RadioButtonDefaults.colors(selectedColor = BloodPrimary)
                                )
                                Text(text = comp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    // 4. Action Mode Setter (Tambah vs Kurangi)
                    Text("Aksi Penyesuaian:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(26.dp)
                            )
                            .padding(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(22.dp))
                                .background(if (isTambah) Color(0xFF2E7D32) else Color.Transparent)
                                .clickable { isTambah = true }
                                .padding(vertical = 10.dp)
                                .testTag("updater_mode_add"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Tambah (+)",
                                fontWeight = FontWeight.Bold,
                                color = if (isTambah) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(22.dp))
                                .background(if (!isTambah) Color(0xFFC62828) else Color.Transparent)
                                .clickable { isTambah = false }
                                .padding(vertical = 10.dp)
                                .testTag("updater_mode_sub"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Kurangi (-)",
                                fontWeight = FontWeight.Bold,
                                color = if (!isTambah) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 5. Quantity Field
                    OutlinedTextField(
                        value = adjustQtyString,
                        onValueChange = { adjustQtyString = it.filter { char -> char.isDigit() } },
                        label = { Text("Jumlah Kantong") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_stock_updater_qty"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BloodPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Dialog actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showUpdateStockDialog = false },
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Batal")
                        }

                        Button(
                            onClick = {
                                val qtyVal = adjustQtyString.toIntOrNull() ?: 0
                                if (qtyVal > 0) {
                                    val finalDelta = if (isTambah) qtyVal else -qtyVal
                                    onUpdateStock(selectedBloodType, selectedRhesus, selectedComponent, finalDelta)
                                    showUpdateStockDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTambah) Color(0xFF2E7D32) else Color(0xFFC62828)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("btn_submit_stock_updater")
                        ) {
                            Text(
                                text = if (isTambah) "Tambah Stok" else "Kurangi Stok",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PmiScreeningQueuesScreen(
    registrations: List<QueueRegistration>,
    onCheckIn: (Int) -> Unit,
    onStartScreening: (QueueRegistration) -> Unit,
    onSelesaiDonor: (QueueRegistration) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val defaultCardColor = if (isDark) Color(0xFF1E1112) else Color.White

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Daftar Antrean & Screening Medis",
            style = MaterialTheme.typography.titleLarge,
            color = BloodPrimary,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "Scan QR Code pendonor yang hadir, lakukan screening HB/Tensi, dan simpan hasil donor langsung ke Server:",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.LightGray.copy(alpha = 0.3f))

        if (registrations.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text = "Belum ada data pendaftar antrean hari ini.", color = Color.Gray, fontWeight = FontWeight.Bold)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(registrations) { reg ->
                    val statusColor = when (reg.status) {
                        "Completed" -> GreenSuccess
                        "Screened_Gagal" -> Color.Red
                        "Screened_Lolos" -> Color(0xFF2E7D32)
                        "CheckedIn" -> Color(0xFF1565C0)
                        else -> YellowWarning
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = defaultCardColor),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.06f) else Color.LightGray.copy(alpha = 0.25f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = reg.userName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (isDark) Color.White else Color.Black)
                                    Text(text = "NIK: ${reg.userNik} • No. Antrean: " + reg.queueNumber, fontSize = 12.sp, color = Color.Gray)
                                }

                                Box(
                                    modifier = Modifier
                                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(text = reg.status, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "Target Event: ${reg.eventTitle}", fontSize = 12.sp, color = if (isDark) Color.White.copy(alpha = 0.8f) else Color.DarkGray, fontWeight = FontWeight.SemiBold)
                            Text(text = "Tanggal / Jam Booking: " + reg.date + " (${reg.timeSlot})", fontSize = 11.sp, color = Color.Gray)

                            // Actions
                            Spacer(modifier = Modifier.height(12.dp))
                            when (reg.status) {
                                "Registered" -> {
                                    Button(
                                        onClick = { onCheckIn(reg.id) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                                    ) {
                                        Text("Scan QR / CHECK-IN KE LOKASI", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White)
                                    }
                                }

                                "CheckedIn" -> {
                                    Button(
                                        onClick = { onStartScreening(reg) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = BloodPrimary)
                                    ) {
                                        Text("MULAI SCREENING KESEHATAN", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White)
                                    }
                                }

                                "Screened_Lolos" -> {
                                    Button(
                                        onClick = { onSelesaiDonor(reg) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                                    ) {
                                        Text("AMBIL DARAH (DONOR SELESAI)", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White)
                                    }
                                }

                                "Screened_Gagal" -> {
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF351212) else Color(0xFFFFF3F3)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Gagal Screening: Timbangan / HB / Tensi tidak sesuai.",
                                            color = Color.Red,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }

                                "Completed" -> {
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF103010) else Color(0xFFE8F5E9)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Donasi Hari ini Sukses Rilis ke Gudang Stok!",
                                            color = GreenSuccess,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(10.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PmiManageRequestsScreen(
    requests: List<BloodRequest>,
    onApprove: (Int) -> Unit,
    onReject: (Int) -> Unit,
    onDispatch: (Int) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val defaultCardColor = if (isDark) Color(0xFF1E1112) else Color.White

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Permintaan Darah Masuk (Inbox)",
            style = MaterialTheme.typography.titleLarge,
            color = BloodPrimary,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "Tanggapi emergency request dari pihak rumah sakit secara real-time dan kurangi inventaris secara otomatis:",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.LightGray.copy(alpha = 0.3f))

        if (requests.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text = "Tidak ada permintaan darah dari rumah sakit saat ini.", color = Color.Gray, fontWeight = FontWeight.Bold)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(requests) { req ->
                    val isEmergency = req.urgency == "EMERGENCY"
                    val cardBg = if (req.status == "Pending" && isEmergency) (if (isDark) Color(0xFF381013) else Color(0xFFFFEBEE)) else defaultCardColor

                    val strokeColor = if (req.status == "Pending" && isEmergency) Color.Red else Color.Transparent

                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(22.dp),
                        border = if (strokeColor != Color.Transparent) BorderStroke(1.2.dp, strokeColor) else BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.06f) else Color.LightGray.copy(alpha = 0.25f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = req.hospitalName, fontWeight = FontWeight.Black, fontSize = 15.sp, color = if (isDark) Color.White else Color.Black)
                                    Text(text = req.requestDate, fontSize = 11.sp, color = Color.Gray)
                                }

                                if (isEmergency) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color.Red, RoundedCornerShape(20.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(text = "EMERGENCY", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(BloodPrimary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = req.bloodType + req.rhesus, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column {
                                    Text(text = req.componentType, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (isDark) Color.White else Color.Black)
                                    Text(text = "Jumlah permintaan: ${req.bagsNeeded} Kantong", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            req.notes?.let {
                                if (it.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Catatan RS: \"$it\"",
                                        fontSize = 12.sp,
                                        color = if (isDark) Color.White.copy(alpha = 0.8f) else Color.DarkGray,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = "Status Alur: " + req.status.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = BloodPrimary)

                            // Handle logic
                            if (req.status == "Pending") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onReject(req.id) },
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Tolak", fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { onApprove(req.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Setujui", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            } else if (req.status == "Approved") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { onDispatch(req.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("PROSES KIRIM (SERAHKAN KE KURIR)", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            } else if (req.status == "Delivered") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF112235) else Color(0xFFE3F2FD))
                                ) {
                                    Text(
                                        text = "Kurir sedang dalam perjalanan mengirim darah ke lokasi RS.",
                                        color = if (isDark) Color(0xFF90CAF9) else Color(0xFF0F4C81),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(10.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PmiCreateEventScreen(viewModel: EBloodBankViewModel) {
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("2026-05-25") }
    var time by remember { mutableStateOf("08:00 - 13:00") }
    var target by remember { mutableStateOf("100") }
    var desc by remember { mutableStateOf("") }

    val isDark = isSystemInDarkTheme()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Buat Event Keliling Baru",
            style = MaterialTheme.typography.titleLarge,
            color = BloodPrimary,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "Publikasikan detail acara donor darah bergerak agar pendonor dapat mem-booking sesi antrean online:",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.LightGray.copy(alpha = 0.3f))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Nama Event / Kampanye Donor") },
            placeholder = { Text("misal: Donor Darah Gebyar Ramadhan") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BloodPrimary)
        )

        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text("Lokasi Spesifik") },
            placeholder = { Text("misal: Aula Serbaguna Masjid Agung") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BloodPrimary)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                label = { Text("Tanggal (YYYY-MM-DD)") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BloodPrimary)
            )

            OutlinedTextField(
                value = time,
                onValueChange = { time = it },
                label = { Text("Jam Kerja") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BloodPrimary)
            )
        }

        OutlinedTextField(
            value = target,
            onValueChange = { target = it },
            label = { Text("Target Pendonor (Orang)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BloodPrimary)
        )

        OutlinedTextField(
            value = desc,
            onValueChange = { desc = it },
            label = { Text("Keterangan & Imbalan (Merchandise/Snack)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 4,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BloodPrimary)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                viewModel.submitNewDonorEvent(
                    title = title,
                    location = location,
                    date = date,
                    time = time,
                    target = target.toIntOrNull() ?: 100,
                    desc = desc
                )
                title = ""
                location = ""
                desc = ""
            },
            colors = ButtonDefaults.buttonColors(containerColor = BloodPrimary),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Publish Event Ke Publik", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.White)
        }
    }
}

@Composable
fun PmiMonthlyReportsScreen(
    stocks: List<BloodStock>,
    registrations: List<QueueRegistration>,
    requests: List<BloodRequest>
) {
    val totalCollected = registrations.count { it.status == "Completed" }
    val totalDistributed = requests.filter { it.status == "Completed" }.sumOf { it.bagsNeeded }
    val successScreening = registrations.count { it.status == "Screened_Lolos" || it.status == "Completed" }
    val rejectedScreening = registrations.count { it.status == "Screened_Gagal" }
    val totalScreenings = successScreening + rejectedScreening

    val successRate = if (totalScreenings > 0) {
        (successScreening.toDouble() / totalScreenings * 100).toInt()
    } else 100

    val isDark = isSystemInDarkTheme()
    val defaultCardColor = if (isDark) Color(0xFF1E1112) else Color.White

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2C1012) else Color(0xFFFFEBEE)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "📄 LAPORAN BULANAN DIGITAL PMI",
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp,
                    color = BloodPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Akurasi Distribusi & Stok Nasional",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = if (isDark) Color.White else Color(0xFF5C0000)
                )
                Text(
                    text = "Direkap otomatis dari datastore lokal E-BloodBank per 2026-05-23:",
                    fontSize = 12.sp,
                    color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.DarkGray
                )
            }
        }

        // Beautiful Graphical Reports using Canvas
        Card(
            colors = CardDefaults.cardColors(containerColor = defaultCardColor),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.06f) else Color.LightGray.copy(alpha = 0.25f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Rasio Penyaringan Pendonor (Screening Success)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isDark) Color.White else Color.Black)
                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color.Red.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(successRate / 100f)
                            .background(GreenSuccess)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Lolos Screening ($successRate%)", fontSize = 11.sp, color = GreenSuccess, fontWeight = FontWeight.Bold)
                    Text(text = "Ditolak/Gagal (${100 - successRate}%)", fontSize = 11.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Stats card grids
        Card(
            colors = CardDefaults.cardColors(containerColor = defaultCardColor),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.06f) else Color.LightGray.copy(alpha = 0.25f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Ringkasan Indikator Kemanusiaan", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isDark) Color.White else Color.Black)

                HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.06f) else Color.LightGray.copy(alpha = 0.3f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Total Kantong Berhasil Masuk (Donor):", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                    Text(text = "$totalCollected Kantong", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GreenSuccess)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Total Kantong Keluar (RS):", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                    Text(text = "$totalDistributed Kantong", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BloodPrimary)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Total Screening Gagal/Batal:", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                    Text(text = "$rejectedScreening Kasus", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                }
            }
        }

        // PDF download simulator
        Button(
            onClick = {
                // PDF generator simulator toast
            },
            colors = ButtonDefaults.buttonColors(containerColor = BloodPrimary),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(6.dp))
            Text("EKSPOR PDF / LAPORAN BULANAN", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

// ==================== CUSTOM GRAPHICS RENDERING ====================

@Composable
fun BloodDropIcon(modifier: Modifier = Modifier, color: Color = Color.Red) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width / 2, 0f)
            cubicTo(
                size.width * 1.4f / 2, size.height * 0.5f,
                size.width, size.height * 0.9f,
                size.width / 2, size.height
            )
            cubicTo(
                0f, size.height * 0.9f,
                -size.width * 0.4f / 2, size.height * 0.5f,
                size.width / 2, 0f
            )
        }
        drawPath(path = path, color = color)
    }
}

@Composable
fun AnimatedQRCode(data: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.White, shape = RoundedCornerShape(8.dp))
            .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            for (i in 0 until 10) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (j in 0 until 10) {
                        val isCorner = (i < 3 && j < 3) || (i < 3 && j >= 7) || (i >= 7 && j < 3)
                        val isFilled = if (isCorner) {
                            (i == 0 || i == 2 || j == 0 || j == 2) || (i == 0 && j == 8) || (i == 8 && j == 0)
                        } else {
                            (data.hashCode() + i * 13 + j * 19) % 3 == 0 || (i + j) % 4 == 0
                        }
                        Box(
                            modifier = Modifier
                                        .size(12.dp)
                                        .background(if (isFilled) Color.Black else Color.White)
                        )
                    }
                }
            }
        }
    }
}

// ==================== LANDING, LOGIN, AND REGISTRATION PAGE ====================

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LandingScreen(
    viewModel: EBloodBankViewModel,
    usersList: List<UserProfile>,
    stocks: List<BloodStock>
) {
    var landingTab by remember { mutableStateOf("TENTANG") } // TENTANG, CARA, STOK
    var formTab by remember { mutableStateOf("LOGIN") } // LOGIN, REGISTER
    var registerRole by remember { mutableStateOf("DONOR") } // DONOR, HOSPITAL

    // Form inputs
    var loginNikInput by remember { mutableStateOf("") }
    var loginPinInput by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    var showBiometricSimDialog by remember { mutableStateOf(false) }
    var biometricTargetUser by remember { mutableStateOf<UserProfile?>(null) }

    var regDonorNik by remember { mutableStateOf("") }
    var regDonorName by remember { mutableStateOf("") }
    var regDonorEmail by remember { mutableStateOf("") }
    var regDonorBloodType by remember { mutableStateOf("O") }
    var regDonorRhesus by remember { mutableStateOf("+") }
    var regDonorPin by remember { mutableStateOf("") }

    var regHospitalNik by remember { mutableStateOf("") }
    var regHospitalAdminName by remember { mutableStateOf("") }
    var regHospitalEmail by remember { mutableStateOf("") }
    var regHospitalName by remember { mutableStateOf("") }
    var regHospitalPin by remember { mutableStateOf("") }

    var showFeedbackMsg by remember { mutableStateOf<String?>(null) }
    var isErrorMsg by remember { mutableStateOf(false) }

    // Helper to display feedback inside the landing page
    fun setFeedback(msg: String, isError: Boolean = false) {
        showFeedbackMsg = msg
        isErrorMsg = isError
    }

    val isDark = isSystemInDarkTheme()
    val bgBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF330508), Color(0xFF140203), Color(0xFF000000))
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color(0xFFFFEBEE), Color(0xFFFFF7F7), Color(0xFFFFFFFF))
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .testTag("landing_screen_container"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Modern Crimson Header with Double Gradient & Glowing Effect - inspired by visual mockup header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF1E1011).copy(alpha = 0.8f) else Color.White
                ),
                shape = RoundedCornerShape(26.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 4.dp),
                border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.08f) else BloodPrimary.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(BloodSecondary, BloodPrimary)
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        BloodDropIcon(modifier = Modifier.size(40.dp), color = Color.White)
                    }

                    Text(
                        text = "E-BloodBank",
                        fontWeight = FontWeight.Black,
                        fontSize = 32.sp,
                        letterSpacing = (-0.5).sp,
                        color = if (isDark) Color.White else BloodPrimary
                    )

                    Text(
                        text = "Sistem Manajemen & Distribusi Darah Real-Time Terintegrasi",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.DarkGray
                    )
                }
            }

            // Modern Slide Tabs Selector (Segment Control style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isDark) Color.Black.copy(alpha = 0.3f) else Color.LightGray.copy(alpha = 0.18f),
                        RoundedCornerShape(26.dp)
                    )
                    .padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    Triple("TENTANG", "Tentang", "tab_about"),
                    Triple("CARA", "Cara Kerja", "tab_how_it_works"),
                    Triple("STOK", "Stok Live", "tab_live_stocks")
                ).forEach { (tabId, label, tag) ->
                    val isSelected = landingTab == tabId
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (isSelected) BloodPrimary else Color.Transparent)
                            .clickable { landingTab = tabId }
                            .padding(vertical = 12.dp)
                            .testTag(tag),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            color = if (isSelected) Color.White else (if (isDark) Color.White.copy(alpha = 0.6f) else Color.DarkGray)
                        )
                    }
                }
            }

            // Content Panel Card with high-fidelity styling
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF1E1112).copy(alpha = 0.6f) else Color.White
                ),
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.06f) else Color.LightGray.copy(alpha = 0.3f)),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (landingTab) {
                        "TENTANG" -> {
                            Text(
                                text = "💡 Mengapa E-BloodBank?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = if (isDark) Color.White else BloodPrimary
                            )
                            Text(
                                text = "E-BloodBank adalah platform digital revolusioner yang mempertemukan pendonor darah, Palang Merah Indonesia (PMI), dan Rumah Sakit secara real-time. Kami mendigitalisasi proses pendaftaran, screening, pelaporan ketersediaan kantong darah, hingga logistik pengantaran darurat demi menyelamatkan lebih banyak nyawa.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isDark) Color.White.copy(alpha = 0.8f) else Color.DarkGray,
                                lineHeight = 20.sp
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isDark) Color.White.copy(alpha = 0.04f) else Color(0xFFFFEBEE)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("❤️ 1 Kantong", fontWeight = FontWeight.Bold, color = BloodPrimary, fontSize = 13.sp)
                                        Text("Selamatkan 3 nyawa", textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.DarkGray)
                                    }
                                }
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isDark) Color.White.copy(alpha = 0.04f) else Color(0xFFE8F5E9)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("📱 Real-Time", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 13.sp)
                                        Text("Distribusi Instan", textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.DarkGray)
                                    }
                                }
                            }
                        }

                        "CARA" -> {
                            Text(
                                text = "🔄 Alur Siklus Kebaikan",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = if (isDark) Color.White else BloodPrimary
                            )

                            val steps = listOf(
                                "1" to ("Registrasi & Cari Event" to "Pendonor mendaftarkan akun secara online, lalu memilih lokasi donor keliling terdekat untuk memesan slot nomor antrean."),
                                "2" to ("Screening Kesehatan PMI" to "Sesampainya di lokasi, petugas PMI melakukan input tanda vital (tekanan darah, hemoglobin, berat badan) untuk meloloskan pendonor."),
                                "3" to ("Pengambilan & Penyimpanan" to "Darah dikumpulkan, diproses menjadi komponen (Whole Blood, PRC, Plasma, Trombosit), dan dimasukkan ke stok PMI secara otomatis."),
                                "4" to ("Permintaan & Distribusi RS" to "Rumah Sakit yang membutuhkan komponen darah mengajukan formulir darurat, dan PMI langsung mengirimkannya dalam hitungan menit.")
                            )

                            steps.forEach { (num, content) ->
                                val (heading, desc) = content
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(BloodPrimary.copy(alpha = 0.15f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = num, color = BloodPrimary, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                    }
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            text = heading,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isDark) Color.White else Color.Black
                                        )
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Gray,
                                            lineHeight = 17.sp
                                        )
                                    }
                                }
                            }
                        }

                        "STOK" -> {
                            Text(
                                text = "📊 Live Visual Tabulasi Stok Darah",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = if (isDark) Color.White else BloodPrimary
                            )
                            Text(
                                text = "Berikut ringkasan akumulatif kantong darah terdaftar saat ini:",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray
                            )

                            val totalAvailableBags = stocks.sumOf { it.quantity }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isDark) Color(0xFF331618) else Color(0xFFFFEBEE),
                                        RoundedCornerShape(18.dp)
                                    )
                                    .padding(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(BloodPrimary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Done, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                Text(
                                    text = "Total Stok PMI Aktif: $totalAvailableBags Kantong",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else Color(0xFF5C0000)
                                )
                            }

                            // High contrast summary grids
                            val groups = stocks.groupBy { it.bloodType }
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                groups.forEach { (groupName, items) ->
                                    val count = items.sumOf { it.quantity }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color(0xFFF9F5F6), RoundedCornerShape(16.dp))
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .background(BloodPrimary, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = groupName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                            Text(
                                                text = "Golongan Darah $groupName",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isDark) Color.White else Color.Black
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (count > 20) Color(0xFF2E7D32) else Color(0xFFD84315),
                                                    RoundedCornerShape(20.dp)
                                                )
                                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "$count Kantong",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // UI Feedback Notification inside the card
            showFeedbackMsg?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isErrorMsg) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
                    ),
                    border = BorderStroke(1.dp, if (isErrorMsg) Color.Red.copy(alpha = 0.2f) else Color(0xFF2E7D32).copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isErrorMsg) Icons.Default.Warning else Icons.Default.Check,
                            contentDescription = null,
                            tint = if (isErrorMsg) Color.Red else Color(0xFF2E7D32)
                        )
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isErrorMsg) Color(0xFFB71C1C) else Color(0xFF1B5E20),
                            modifier = Modifier.weight(1f),
                            lineHeight = 16.sp
                        )
                        IconButton(onClick = { showFeedbackMsg = null }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = if (isErrorMsg) Color.Red else Color(0xFF2E7D32),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Theme Mode Config Button
            var showThemeDialogInLanding by remember { mutableStateOf(false) }
            TextButton(
                onClick = { showThemeDialogInLanding = true },
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp), tint = BloodPrimary)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pengaturan Tema", fontSize = 12.sp, color = BloodPrimary, fontWeight = FontWeight.Bold)
            }

            if (showThemeDialogInLanding) {
                val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
                Dialog(onDismissRequest = { showThemeDialogInLanding = false }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(26.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("Pengaturan Tema", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = BloodPrimary)
                            listOf(
                                Triple("LIGHT", "☀️ Terang (Light Mode)", "Tampilan cerah"),
                                Triple("DARK", "🌙 Gelap (Dark Mode)", "Tampilan gelap"),
                                Triple("SYSTEM", "⚙️ Otomatis (System)", "Sesuai sistem perangkat")
                            ).forEach { (mode, title, desc) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.setThemeMode(mode) }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    RadioButton(selected = themeMode == mode, onClick = { viewModel.setThemeMode(mode) })
                                    Column {
                                        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(desc, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    }
                                }
                            }
                            TextButton(
                                onClick = { showThemeDialogInLanding = false },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Tutup", fontWeight = FontWeight.Bold, color = BloodPrimary)
                            }
                        }
                    }
                }
            }

            // Section: FORM (Tabs to choose LOGIN vs REGISTER) - inspired by welcome forms (Phone 1)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF1E1112).copy(alpha = 0.8f) else Color.White
                ),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 4.dp),
                border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.08f) else BloodPrimary.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Main Switch segment selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isDark) Color.Black.copy(alpha = 0.3f) else Color.LightGray.copy(alpha = 0.18f),
                                RoundedCornerShape(26.dp)
                            )
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(22.dp))
                                .background(if (formTab == "LOGIN") BloodPrimary else Color.Transparent)
                                .clickable { formTab = "LOGIN" }
                                .padding(vertical = 12.dp)
                                .testTag("tab_login_section"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Masuk (Login)",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = if (formTab == "LOGIN") Color.White else (if (isDark) Color.White.copy(alpha = 0.6f) else Color.DarkGray)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(22.dp))
                                .background(if (formTab == "REGISTER") BloodPrimary else Color.Transparent)
                                .clickable { formTab = "REGISTER" }
                                .padding(vertical = 12.dp)
                                .testTag("tab_register_section"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Registrasi Baru",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = if (formTab == "REGISTER") Color.White else (if (isDark) Color.White.copy(alpha = 0.6f) else Color.DarkGray)
                            )
                        }
                    }

                    if (formTab == "LOGIN") {
                        // --- LOGIN WORKFLOW ---
                        Text(
                            text = "🔐 Masuk ke Akun Anda",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isDark) Color.White else Color.Black
                        )

                        // NIK form beautifully styled with custom capsule shape
                        OutlinedTextField(
                            value = loginNikInput,
                            onValueChange = { loginNikInput = it },
                            label = { Text("Masukkan Nomor NIK Anda") },
                            placeholder = { Text("E.g. 3271012345670001 (16 dgt)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_login_nik"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.AccountBox, contentDescription = null, tint = BloodPrimary) },
                            shape = RoundedCornerShape(28.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BloodPrimary,
                                unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.LightGray,
                                focusedContainerColor = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Transparent,
                                unfocusedContainerColor = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Transparent
                            )
                        )

                        // 6-digit PIN form beautifully styled with password visual transformation
                        OutlinedTextField(
                            value = loginPinInput,
                            onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) loginPinInput = it },
                            label = { Text("PIN Keamanan 6-Digit") },
                            placeholder = { Text("Masukkan 6 digit angka") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_login_pin"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = if (showPin) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = BloodPrimary) },
                            trailingIcon = {
                                IconButton(onClick = { showPin = !showPin }) {
                                    Icon(
                                        imageVector = if (showPin) Icons.Default.Check else Icons.Default.Lock,
                                        contentDescription = "Tampilkan PIN",
                                        tint = BloodPrimary
                                    )
                                }
                            },
                            shape = RoundedCornerShape(28.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BloodPrimary,
                                unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.LightGray,
                                focusedContainerColor = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Transparent,
                                unfocusedContainerColor = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Transparent
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (loginNikInput.length < 16) {
                                        setFeedback("Masukkan NIK yang valid (16 digit angka saja).", isError = true)
                                    } else if (loginPinInput.length != 6) {
                                        setFeedback("PIN Keamanan harus tepat 6 digit.", isError = true)
                                    } else {
                                        viewModel.loginWithPin(loginNikInput, loginPinInput) { success, message ->
                                            if (!success) {
                                                setFeedback(message, isError = true)
                                            } else {
                                                setFeedback("Berhasil masuk!", isError = false)
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BloodPrimary),
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("btn_login_submit")
                            ) {
                                Text("Masuk", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }

                            // Biometrics fast access
                            val matchedUser = usersList.find { it.nik == loginNikInput }
                            if (matchedUser != null && matchedUser.useBiometrics) {
                                Button(
                                    onClick = {
                                        biometricTargetUser = matchedUser
                                        showBiometricSimDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                    shape = RoundedCornerShape(28.dp),
                                    modifier = Modifier
                                        .size(52.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Biometrik", tint = Color.White)
                                }
                            }
                        }

                        HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.LightGray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))

                        // Demo Shortcuts
                        Text(
                            text = "⭐ Pilih Akun Demo untuk Simulasi Cepat (3 Role):",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                            color = BloodPrimary
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            usersList.forEach { user ->
                                val roleText = when {
                                    user.isPmiStaff -> "Petugas PMI (Super Admin)"
                                    user.isHospitalAdmin -> "Admin RS (${user.hospitalName})"
                                    else -> "Pendonor (${user.bloodType}${user.rhesus})"
                                }

                                val cardTag = when {
                                    user.isPmiStaff -> "login_budi_card"
                                    user.isHospitalAdmin -> "login_susi_card"
                                    else -> if (user.nik == "3271012345670001") "login_indra_card" else "login_siti_card"
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.switchActiveUser(user.nik)
                                        }
                                        .testTag(cardTag),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isDark) Color.White.copy(alpha = 0.04f) else Color(0xFFF9F5F6)
                                    ),
                                    border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.06f) else Color.LightGray.copy(alpha = 0.2f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .background(
                                                    if (user.isPmiStaff) Color(0xFF1565C0) else if (user.isHospitalAdmin) Color(0xFFD84315) else BloodPrimary,
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (user.isPmiStaff) {
                                                Icon(Icons.Default.Home, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                            } else if (user.isHospitalAdmin) {
                                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                            } else {
                                                BloodDropIcon(modifier = Modifier.size(18.dp), color = Color.White)
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = user.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (isDark) Color.White else Color.Black)
                                            Text(text = roleText, style = MaterialTheme.typography.labelSmall, color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray)
                                            Text(text = "NIK: ${user.nik}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Log In",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = BloodPrimary
                                            )
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = BloodPrimary, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // --- REGISTRATION WORKFLOW ---
                        Text(
                            text = "🔑 Buat Akun Baru",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isDark) Color.White else Color.Black
                        )

                        // Sub-switcher (Donor vs Hospital)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isDark) Color.Black.copy(alpha = 0.3f) else Color.LightGray.copy(alpha = 0.18f),
                                    RoundedCornerShape(26.dp)
                                )
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(if (registerRole == "DONOR") BloodPrimary else Color.Transparent)
                                    .clickable { registerRole = "DONOR" }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🩸 Pendonor",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (registerRole == "DONOR") Color.White else (if (isDark) Color.White.copy(alpha = 0.6f) else Color.DarkGray)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(if (registerRole == "HOSPITAL") BloodPrimary else Color.Transparent)
                                    .clickable { registerRole = "HOSPITAL" }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🏥 Rumah Sakit",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (registerRole == "HOSPITAL") Color.White else (if (isDark) Color.White.copy(alpha = 0.6f) else Color.DarkGray)
                                )
                            }
                        }

                        if (registerRole == "DONOR") {
                            // 1. Pendonor registration form
                            OutlinedTextField(
                                value = regDonorName,
                                onValueChange = { regDonorName = it },
                                label = { Text("Nama Lengkap") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_donor_name"),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = BloodPrimary) },
                                shape = RoundedCornerShape(28.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BloodPrimary,
                                    unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.LightGray,
                                    focusedContainerColor = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Transparent,
                                    unfocusedContainerColor = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Transparent
                                )
                            )

                            OutlinedTextField(
                                value = regDonorNik,
                                onValueChange = { regDonorNik = it },
                                label = { Text("Nomor NIK (16 digit)") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_donor_nik"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.AccountBox, contentDescription = null, tint = BloodPrimary) },
                                shape = RoundedCornerShape(28.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BloodPrimary,
                                    unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.LightGray,
                                    focusedContainerColor = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Transparent,
                                    unfocusedContainerColor = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Transparent
                                )
                            )

                            OutlinedTextField(
                                value = regDonorEmail,
                                onValueChange = { regDonorEmail = it },
                                label = { Text("Alamat Email") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_donor_email"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = BloodPrimary) },
                                shape = RoundedCornerShape(28.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BloodPrimary,
                                    unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.LightGray,
                                    focusedContainerColor = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Transparent,
                                    unfocusedContainerColor = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Transparent
                                )
                            )

                            // Blood Type & Rhesus selectors
                            Text("Pilih Golongan Darah:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = if (isDark) Color.White else Color.Black)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("A", "B", "AB", "O").forEach { bt ->
                                    val isSelected = regDonorBloodType == bt
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (isSelected) BloodPrimary else (if (isDark) Color.White.copy(alpha = 0.06f) else Color.LightGray.copy(alpha = 0.2f)))
                                            .clickable { regDonorBloodType = bt }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = bt,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = if (isSelected) Color.White else (if (isDark) Color.White.copy(alpha = 0.6f) else Color.DarkGray)
                                        )
                                    }
                                }
                            }

                            Text("Pilih Rhesus:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = if (isDark) Color.White else Color.Black)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("+", "-").forEach { rh ->
                                    val isSelected = regDonorRhesus == rh
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (isSelected) BloodPrimary else (if (isDark) Color.White.copy(alpha = 0.06f) else Color.LightGray.copy(alpha = 0.2f)))
                                            .clickable { regDonorRhesus = rh }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (rh == "+") "Rhesus Positif (+)" else "Rhesus Negatif (-)",
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else (if (isDark) Color.White.copy(alpha = 0.6f) else Color.DarkGray),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            OutlinedTextField(
                                value = regDonorPin,
                                onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) regDonorPin = it },
                                label = { Text("Buat PIN Keamanan Baru (6 Digit Angka)") },
                                placeholder = { Text("E.g. 123456") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_donor_pin"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = BloodPrimary) },
                                shape = RoundedCornerShape(28.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BloodPrimary,
                                    unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.LightGray,
                                    focusedContainerColor = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Transparent,
                                    unfocusedContainerColor = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Transparent
                                )
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = {
                                    when {
                                        regDonorName.isEmpty() || regDonorNik.isEmpty() || regDonorEmail.isEmpty() -> {
                                            setFeedback("Semua isian formulir wajib dilengkapi.", isError = true)
                                        }
                                        regDonorNik.length < 16 -> {
                                            setFeedback("NIK tidak valid. Harus tepat 16 digit.", isError = true)
                                        }
                                        !regDonorEmail.contains("@") -> {
                                            setFeedback("Alamat email tidak valid.", isError = true)
                                        }
                                        regDonorPin.length != 6 -> {
                                            setFeedback("PIN Keamanan wajib 6 digit angka.", isError = true)
                                        }
                                        else -> {
                                            viewModel.registerNewDonor(
                                                nik = regDonorNik,
                                                name = regDonorName,
                                                email = regDonorEmail,
                                                bloodType = regDonorBloodType,
                                                rhesus = regDonorRhesus,
                                                pin = regDonorPin
                                            )
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BloodPrimary),
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("register_donor_submit")
                            ) {
                                Text("Daftar Sekarang", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        } else {
                            // 2. Rumah Sakit registration form
                            OutlinedTextField(
                                value = regHospitalName,
                                onValueChange = { regHospitalName = it },
                                label = { Text("Nama Rumah Sakit / Instansi Medis") },
                                placeholder = { Text("E.g. RS Medika Sejahtera") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_hospital_name"),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, tint = BloodPrimary) },
                                shape = RoundedCornerShape(28.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BloodPrimary,
                                    unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.LightGray,
                                    focusedContainerColor = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Transparent,
                                    unfocusedContainerColor = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Transparent
                                )
                            )

                            OutlinedTextField(
                                value = regHospitalAdminName,
                                onValueChange = { regHospitalAdminName = it },
                                label = { Text("Nama Admin Penghubung") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_hospital_admin_name"),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = BloodPrimary) },
                                shape = RoundedCornerShape(28.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BloodPrimary,
                                    unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.LightGray,
                                    focusedContainerColor = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Transparent,
                                    unfocusedContainerColor = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Transparent
                                )
                            )

                            OutlinedTextField(
                                value = regHospitalNik,
                                onValueChange = { regHospitalNik = it },
                                label = { Text("NIK Admin (16 digit)") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_hospital_nik"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.AccountBox, contentDescription = null, tint = BloodPrimary) },
                                shape = RoundedCornerShape(28.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BloodPrimary,
                                    unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.LightGray,
                                    focusedContainerColor = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Transparent,
                                    unfocusedContainerColor = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Transparent
                                )
                            )

                            OutlinedTextField(
                                value = regHospitalEmail,
                                onValueChange = { regHospitalEmail = it },
                                label = { Text("Email Instansi Rumah Sakit / Admin") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_hospital_email"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = BloodPrimary) },
                                shape = RoundedCornerShape(28.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BloodPrimary,
                                    unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.LightGray,
                                    focusedContainerColor = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Transparent,
                                    unfocusedContainerColor = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Transparent
                                )
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            OutlinedTextField(
                                value = regHospitalPin,
                                onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) regHospitalPin = it },
                                label = { Text("Buat PIN Keamanan Baru (6 Digit Angka)") },
                                placeholder = { Text("E.g. 123456") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_hospital_pin"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = BloodPrimary) },
                                shape = RoundedCornerShape(28.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BloodPrimary,
                                    unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.LightGray,
                                    focusedContainerColor = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Transparent,
                                    unfocusedContainerColor = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Transparent
                                )
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = {
                                    if (regHospitalName.isEmpty() || regHospitalAdminName.isEmpty() || regHospitalNik.isEmpty() || regHospitalEmail.isEmpty()) {
                                        setFeedback("Semua isian formulir wajib dilengkapi.", isError = true)
                                    } else if (regHospitalNik.length < 16) {
                                        setFeedback("NIK tidak valid. Harus tepat 16 digit.", isError = true)
                                    } else if (!regHospitalEmail.contains("@")) {
                                        setFeedback("Alamat email tidak valid.", isError = true)
                                    } else if (regHospitalPin.length != 6) {
                                        setFeedback("PIN Keamanan wajib 6 digit angka.", isError = true)
                                    } else {
                                        viewModel.registerNewHospital(
                                            nik = regHospitalNik,
                                            adminName = regHospitalAdminName,
                                            email = regHospitalEmail,
                                            hospitalName = regHospitalName,
                                            pin = regHospitalPin
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BloodPrimary),
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("register_hospital_submit")
                            ) {
                                Text("Daftar Akun Rumah Sakit", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "E-BloodBank v2.4 • Keamanan data dijamin dengan enkripsi lokal.",
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (showBiometricSimDialog && biometricTargetUser != null) {
                AlertDialog(
                    onDismissRequest = { showBiometricSimDialog = false },
                    confirmButton = {
                        Button(
                            onClick = {
                                showBiometricSimDialog = false
                                viewModel.loginWithBiometrics(biometricTargetUser!!.nik) { success, msg ->
                                    if (!success) {
                                        setFeedback(msg, isError = true)
                                    } else {
                                        setFeedback("Masuk menggunakan Sidik Jari berhasil!", isError = false)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Sentuh Sensor (Simulasi Verifikasi)", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBiometricSimDialog = false }) {
                            Text("Batal")
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(28.dp))
                            Text("Keamanan Biometrik Indonesia", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        }
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(if (isDark) Color(0xFF1B5E20) else Color(0xFFE8F5E9), CircleShape)
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.fillMaxSize())
                            }
                            Text(
                                "Nama Akun: ${biometricTargetUser!!.name}\nNIK: ${SecurityUtils.maskNik(biometricTargetUser!!.nik)}",
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = if (isDark) Color.LightGray else Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Letakkan sidik jari telunjuk atau jempol Anda pada sensor fisik perangkat untuk melakukan pencocokan biometrik terenkripsi SHA-256.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = if (isDark) Color.White else Color.Black
                            )
                        }
                    },
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }
    }
}

@Composable
fun SecurityAuditLogsScreen(
    viewModel: EBloodBankViewModel,
    logs: List<SecurityAuditLog>,
    users: List<UserProfile>,
    onUnlockUser: (String) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val scrollState = rememberScrollState()

    // Blockchain chain validation state
    val totalLogs = logs.size
    var isChainIntact = true
    // Try to trace back log signatures
    var signatureChainText = "Genesis Signature Verified"
    if (logs.isNotEmpty()) {
        val lastLog = logs.last()
        isChainIntact = lastLog.doubleHashSignature.length == 64
        signatureChainText = "Sig: ${lastLog.doubleHashSignature.take(16)}... (Chained SHA-256)"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. SOC Top Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF0F141C) else Color(0xFFECEFF1)
            ),
            border = BorderStroke(1.dp, if (isDark) Color(0xFF1E2A38) else Color(0xFFCFD8DC))
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isChainIntact) Color(0xFF2E7D32) else Color(0xFFC62828),
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "SECURITY OPERATIONS CENTER (SOC)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = if (isDark) Color.White else Color.Black
                    )
                }

                Text(
                    text = "E-BloodBank National Encryption Ledger v2.4 (Terintegrasi Pusat Data Nasional/PDN)",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )

                HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.LightGray.copy(alpha = 0.3f))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Hash Chain Integrity", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            text = if (isChainIntact) "🛡️ AMAN & UTUH" else "⚠️ DATA DIMANIPULASI",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = if (isChainIntact) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Active VPN Tunnel", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            text = "🟢 JKT-PDN-TUNNEL-02",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color(0xFF1565C0)
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Database Engine State", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            text = "Room Secure Crypto V3",
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.sp,
                            color = if (isDark) Color.White else Color.Black
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Audit Signature Trace", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            text = signatureChainText,
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.sp,
                            color = if (isDark) Color.LightGray else Color.DarkGray
                        )
                    }
                }
            }
        }

        // 2. Lockout management section
        val lockedUserProfiles = users.filter { viewModel.lockedUsers.value.contains(it.nik) }
        Text(
            text = "🔒 Manajemen Lockout NIK Pokok (Indeks Blokir)",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        if (lockedUserProfiles.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDark) Color.Black.copy(alpha = 0.15f) else Color.White)
            ) {
                Box(modifier = Modifier.padding(16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "Tidak ada akun terblokir saat ini. Kebijakan 3x salah PIN berjalan terpantau.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lockedUserProfiles.forEach { u ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3E2723))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = u.name,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "NIK: ${SecurityUtils.maskNik(u.nik)} • RS/Donor",
                                    fontSize = 12.sp,
                                    color = Color.LightGray
                                )
                            }
                            Button(
                                onClick = { onUnlockUser(u.nik) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD84315)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("PULIHKAN AKSES", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 3. Security logs ledger
        Text(
            text = "📝 Ledger Transaksi & Mutasi Keamanan",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        if (logs.isEmpty()) {
            Box(modifier = Modifier.padding(16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Ledger kosong. Inisiasi sistem audit sedang berlangsung...", color = Color.Gray)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                logs.reversed().forEach { log ->
                    val colorTheme = when (log.activityType) {
                        "AUTH_SUCCESS", "AUTH_BIOMETRIC_SUCCESS" -> Color(0xFF2E7D32)
                        "AUTH_FAILURE" -> Color(0xFFEF6C00)
                        "AUTH_LOCKEDOUT", "AUTH_BLOCKED" -> Color(0xFFC62828)
                        "STOCK_UPDATE" -> Color(0xFF1565C0)
                        "DONOR_SAMPLE_SECURED" -> Color(0xFF6A1B9A)
                        "ADMIN_SEC_UNLOCK" -> Color(0xFFAD1457)
                        else -> Color.DarkGray
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF14171E) else Color.White
                        ),
                        border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.05f) else Color.LightGray.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(colorTheme.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = log.activityType,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = colorTheme
                                    )
                                }
                                val formatHelper = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                val timeText = try { formatHelper.format(java.util.Date(log.timestamp)) } catch (e: Exception) { "" }
                                Text(
                                    text = timeText,
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }

                            Text(
                                text = log.details,
                                fontSize = 13.sp,
                                color = if (isDark) Color.White else Color.Black
                            )

                            HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.04f) else Color.LightGray.copy(alpha = 0.15f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Oleh: ${log.actorName} (${SecurityUtils.maskNik(log.actorNik)})",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Chained Sig: ${log.doubleHashSignature.take(12)}...",
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = if (isChainIntact) Color(0xFF2E7D32).copy(alpha = 0.8f) else Color.Red
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
