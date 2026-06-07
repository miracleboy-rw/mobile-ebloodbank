package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EBloodBankViewModel(
    application: Application,
    private val repository: EBloodBankRepository
) : AndroidViewModel(application) {

    // Active logged-in context (empty string initially means not logged in)
    private val _currentActiveNik = MutableStateFlow("")
    val currentActiveNik: StateFlow<String> = _currentActiveNik.asStateFlow()

    // Persistent Theme Preference: "SYSTEM", "LIGHT", "DARK"
    private val sharedPrefs = application.getSharedPreferences("ebloodbank_prefs", android.content.Context.MODE_PRIVATE)
    private val _themeMode = MutableStateFlow(sharedPrefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            _themeMode.value = mode
            sharedPrefs.edit().putString("theme_mode", mode).apply()
            _uiFeedback.emit("Tema berhasil diubah ke: ${if (mode == "SYSTEM") "Sistem" else if (mode == "DARK") "Gelap" else "Terang"}")
        }
    }

    val activeUserProfile: StateFlow<UserProfile?> = currentActiveNik.flatMapLatest { nik ->
        repository.allUserProfiles.map { users ->
            users.find { it.nik == nik }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Flow listings from repository
    val allUserProfiles: StateFlow<List<UserProfile>> = repository.allUserProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSecurityLogs: StateFlow<List<SecurityAuditLog>> = repository.allSecurityLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _loginAttempts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val loginAttempts: StateFlow<Map<String, Int>> = _loginAttempts.asStateFlow()

    private val _lockedUsers = MutableStateFlow<Set<String>>(emptySet())
    val lockedUsers: StateFlow<Set<String>> = _lockedUsers.asStateFlow()

    val allBloodStocks: StateFlow<List<BloodStock>> = repository.allBloodStocks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDonorEvents: StateFlow<List<DonorEvent>> = repository.allDonorEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRegistrations: StateFlow<List<QueueRegistration>> = repository.allRegistrations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBloodRequests: StateFlow<List<BloodRequest>> = repository.allBloodRequests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filter registrations for active donor
    val activeUserRegistrations: StateFlow<List<QueueRegistration>> = currentActiveNik.flatMapLatest { nik ->
        repository.getRegistrationsByNik(nik)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active User Notifications (reactive role filtering)
    val activeUserNotifications: StateFlow<List<AppNotification>> = combine(
        currentActiveNik,
        activeUserProfile
    ) { nik, profile ->
        val role = when {
            profile?.isPmiStaff == true -> "PMI"
            profile?.isHospitalAdmin == true -> "Hospital"
            else -> "Donor"
        }
        repository.getNotificationsByRecipient(nik, role)
    }.flatMapLatest { it }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active User Eligibility Info
    val activeUserEligibility: StateFlow<EligibilityState> = activeUserProfile.map { user ->
        if (user == null) {
            EligibilityState(canDonor = false, daysRemaining = 0, statusText = "User tidak ditemukan")
        } else {
            val daysRemaining = calculateDaysRemaining(user.lastDonationDate)
            if (daysRemaining <= 0) {
                EligibilityState(
                    canDonor = true,
                    daysRemaining = 0,
                    statusText = "Bisa Donor! Tubuh Anda siap berbagi kebaikan."
                )
            } else {
                EligibilityState(
                    canDonor = false,
                    daysRemaining = daysRemaining,
                    statusText = "Harus tunggu $daysRemaining hari lagi (Wajib interval 60 hari)"
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EligibilityState(false, 0, "Loading..."))

    // Feedback messages or errors in UI
    private val _uiFeedback = MutableSharedFlow<String>()
    val uiFeedback = _uiFeedback.asSharedFlow()

    // --- ACTIONS CHANGER ---
    fun switchActiveUser(nik: String) {
        viewModelScope.launch {
            if (_lockedUsers.value.contains(nik)) {
                _uiFeedback.emit("Sesi terkunci! Akun NIK ini ditangguhkan sistem.")
                return@launch
            }
            _currentActiveNik.value = nik
            _uiFeedback.emit("Berhasil beralih profil user! (Bypass Demo)")

            val profile = repository.getUserProfileByNik(nik)
            if (profile != null) {
                repository.insertSecurityLog(
                    SecurityAuditLog(
                        activityType = "AUTH_DEMO_BYPASS",
                        actorNik = profile.nik,
                        actorName = profile.name,
                        details = "Autentikasi pintasan demo disetujui_bypass (Sesi Terbuka)."
                    )
                )

                if (!profile.isPmiStaff && !profile.isHospitalAdmin) {
                    val daysRemaining = calculateDaysRemaining(profile.lastDonationDate)
                    if (daysRemaining <= 0) {
                        val eligibilityNotif = AppNotification(
                            recipientNik = profile.nik,
                            recipientRole = "Donor",
                            title = "🟢 Saatnya Berbagi Kebaikan!",
                            message = "Halo ${profile.name}! Tubuh Anda siap kembali mendonorkan darah Anda hari ini. Jadwalkan di event terdekat!"
                        )
                        repository.insertNotification(eligibilityNotif)
                    }
                }
            }
        }
    }

    fun loginWithPin(nik: String, pin: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            if (_lockedUsers.value.contains(nik)) {
                onResult(false, "NIK ini terkunci karena 3 kali salah PIN. Silakan laporkan ke UDD PMI untuk validasi identitas fisik.")
                repository.insertSecurityLog(
                    SecurityAuditLog(
                        activityType = "AUTH_BLOCKED",
                        actorNik = nik,
                        actorName = "Akun Terkunci",
                        details = "Terbongkar percobaan masuk ilegal pada akun terblokir."
                    )
                )
                return@launch
            }

            val user = repository.getUserProfileByNik(nik)
            if (user == null) {
                onResult(false, "NIK tidak ditemukan di pangkalan data nasional.")
                return@launch
            }

            val hashed = SecurityUtils.hashPin(pin, user.salt)
            if (hashed == user.pinHash) {
                // Clear attempts
                val mutableAttempts = _loginAttempts.value.toMutableMap()
                mutableAttempts.remove(nik)
                _loginAttempts.value = mutableAttempts

                _currentActiveNik.value = nik
                _uiFeedback.emit("Masuk berhasil! Sesi dienkripsi.")
                repository.insertSecurityLog(
                    SecurityAuditLog(
                        activityType = "AUTH_SUCCESS",
                        actorNik = user.nik,
                        actorName = user.name,
                        details = "Autentikasi PIN 6-Digit sukses menggunakan enkripsi SHA-256."
                    )
                )
                onResult(true, "Sukses")
            } else {
                val mutableAttempts = _loginAttempts.value.toMutableMap()
                val currentCount = (mutableAttempts[nik] ?: 0) + 1
                mutableAttempts[nik] = currentCount
                _loginAttempts.value = mutableAttempts

                if (currentCount >= 3) {
                    val mutableLocked = _lockedUsers.value.toMutableSet()
                    mutableLocked.add(nik)
                    _lockedUsers.value = mutableLocked
                    onResult(false, "PIN salah 3 kali! Akun NIK ${SecurityUtils.maskNik(nik)} telah LOCKOUT.")
                    repository.insertSecurityLog(
                        SecurityAuditLog(
                            activityType = "AUTH_LOCKEDOUT",
                            actorNik = user.nik,
                            actorName = user.name,
                            details = "Penguncian sistem diaktifkan akibat 3 kali salah memasukkan PIN."
                        )
                    )
                } else {
                    onResult(false, "PIN salah! Sisa percobaan: ${3 - currentCount} kali.")
                    repository.insertSecurityLog(
                        SecurityAuditLog(
                            activityType = "AUTH_FAILURE",
                            actorNik = user.nik,
                            actorName = user.name,
                            details = "Kegagalan otorisasi PIN (Salah PIN ke-$currentCount)."
                        )
                    )
                }
            }
        }
    }

    fun loginWithBiometrics(nik: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val user = repository.getUserProfileByNik(nik)
            if (user == null) {
                onResult(false, "NIK tidak valid.")
                return@launch
            }
            if (_lockedUsers.value.contains(nik)) {
                onResult(false, "Akun terkunci secara nasional.")
                return@launch
            }
            if (!user.useBiometrics) {
                onResult(false, "Biometrik belum aktif di akun ini.")
                return@launch
            }

            _currentActiveNik.value = nik
            _uiFeedback.emit("Autentikasi biometrik berhasil diverifikasi.")
            repository.insertSecurityLog(
                SecurityAuditLog(
                    activityType = "AUTH_BIOMETRIC_SUCCESS",
                    actorNik = user.nik,
                    actorName = user.name,
                    details = "Masuk via verifikasi sidik jari/sensor wajah terenkripsi Android KeyStore."
                )
            )
            onResult(true, "Sukses")
        }
    }

    fun toggleBiometrics(enabled: Boolean) {
        viewModelScope.launch {
            val user = activeUserProfile.value ?: return@launch
            val updated = user.copy(useBiometrics = enabled)
            repository.insertUserProfile(updated)
            _uiFeedback.emit(if (enabled) "🔐 Proteksi Biometrik Diaktifkan!" else "⚠️ Biometrik Dinonaktifkan.")
            repository.insertSecurityLog(
                SecurityAuditLog(
                    activityType = "SECURITY_CONFIG_UPDATE",
                    actorNik = user.nik,
                    actorName = user.name,
                    details = "Merubah setelan biometrik menjadi: ${if (enabled) "AKTIF" else "NONAKTIF"}."
                )
            )
        }
    }

    fun toggleEncryptedLabels(enabled: Boolean) {
        viewModelScope.launch {
            val user = activeUserProfile.value ?: return@launch
            val updated = user.copy(showEncryptedLabels = enabled)
            repository.insertUserProfile(updated)
            _uiFeedback.emit(if (enabled) "🔓 Masking Data Terbuka (Mode Dekripsi)" else "🔒 Masking Data Medis Aktif")
        }
    }

    fun unlockUserNik(nik: String) {
        viewModelScope.launch {
            val activeUser = activeUserProfile.value
            if (activeUser?.isPmiStaff == true) {
                val mutableLocked = _lockedUsers.value.toMutableSet()
                if (mutableLocked.remove(nik)) {
                    _lockedUsers.value = mutableLocked
                    val mutableAttempts = _loginAttempts.value.toMutableMap()
                    mutableAttempts.remove(nik)
                    _loginAttempts.value = mutableAttempts
                    _uiFeedback.emit("Otoritas: Akses NIK $nik berhasil dipulihkan.")
                    repository.insertSecurityLog(
                        SecurityAuditLog(
                            activityType = "ADMIN_SEC_UNLOCK",
                            actorNik = activeUser.nik,
                            actorName = activeUser.name,
                            details = "Administrator memulihkan pembatasan lockout NIK: $nik."
                        )
                    )
                }
            }
        }
    }

    fun markNotificationAsRead(id: Int) {
        viewModelScope.launch {
            repository.markNotificationAsRead(id)
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            val user = activeUserProfile.value
            val role = when {
                user?.isPmiStaff == true -> "PMI"
                user?.isHospitalAdmin == true -> "Hospital"
                else -> "Donor"
            }
            repository.markAllAsReadForUser(currentActiveNik.value, role)
            _uiFeedback.emit("Semua notifikasi ditandai dibaca!")
        }
    }

    // --- DONOR ACTIONS ---
    fun bookQueue(eventId: Int, timeSlot: String, eventTitle: String, eventLocation: String) {
        viewModelScope.launch {
            val user = activeUserProfile.value ?: return@launch
            val eligibility = activeUserEligibility.value
            if (!eligibility.canDonor) {
                _uiFeedback.emit("Registrasi GAGAL: Anda belum memenuhi syarat kelayakan waktu donor.")
                return@launch
            }

            // Generate Queue number
            val randomNum = (100..999).random()
            val shortCode = when (user.bloodType) {
                "A" -> "A"
                "B" -> "B"
                "AB" -> "AB"
                else -> "O"
            }
            val queueNumber = "$shortCode-$randomNum"
            val qrCodeData = "EBB-QR-${UUID.randomUUID().toString().take(6).uppercase()}"

            val reg = QueueRegistration(
                eventId = eventId,
                eventTitle = eventTitle,
                eventLocation = eventLocation,
                userNik = user.nik,
                userName = user.name,
                date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                timeSlot = timeSlot,
                queueNumber = queueNumber,
                qrCodeData = qrCodeData,
                status = "Registered"
            )

            repository.insertRegistration(reg)

            // Update registered count in Event
            val event = repository.getDonorEventById(eventId)
            if (event != null) {
                repository.updateDonorEvent(event.copy(registeredCount = event.registeredCount + 1))
            }

            _uiFeedback.emit("Berhasil memesan antrian online! Nomor antrian: $queueNumber")
        }
    }

    // --- PMI STAFF ACTIONS ---
    fun checkInDonor(registrationId: Int) {
        viewModelScope.launch {
            val reg = repository.getRegistrationById(registrationId)
            if (reg != null) {
                val updated = reg.copy(
                    status = "CheckedIn",
                    checkedInAt = System.currentTimeMillis()
                )
                repository.updateRegistration(updated)
                _uiFeedback.emit("Check-In berhasil untuk ${reg.userName}. Silakan lakukan screening medis.")
            }
        }
    }

    fun saveMedicalScreening(
        registrationId: Int,
        systolic: Int,
        diastolic: Int,
        hemoglobin: Double,
        weight: Double,
        notes: String,
        isLolos: Boolean
    ) {
        viewModelScope.launch {
            val reg = repository.getRegistrationById(registrationId)
            if (reg != null) {
                val status = if (isLolos) "Screened_Lolos" else "Screened_Gagal"
                val updated = reg.copy(
                    status = status,
                    systolicBp = systolic,
                    diastolicBp = diastolic,
                    hemoglobin = hemoglobin,
                    weightKg = weight,
                    screeningNotes = notes,
                    screenedAt = System.currentTimeMillis()
                )
                repository.updateRegistration(updated)

                val userStatusMessage = if (isLolos) "Lolos screening" else "Ditolak/Gagal screening"
                _uiFeedback.emit("Screening medis selesai. Pendonor dinyatakan: $userStatusMessage")
            }
        }
    }

    fun completeDonorAndAddToStock(
        registrationId: Int,
        componentType: String // Whole Blood, Packed Red Cells, etc.
    ) {
        viewModelScope.launch {
            val reg = repository.getRegistrationById(registrationId)
            if (reg != null && reg.status == "Screened_Lolos") {
                val user = repository.getUserProfileByNik(reg.userNik)
                if (user != null) {
                    // Update user profile stats: award +100 Points
                    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val updatedUser = user.copy(
                        lastDonationDate = todayStr,
                        totalDonations = user.totalDonations + 1,
                        points = user.points + 100
                    )
                    repository.insertUserProfile(updatedUser)

                    // Add blood stock
                    repository.updateBloodStockQuantity(
                        bloodType = user.bloodType,
                        rhesus = user.rhesus,
                        componentType = componentType,
                        delta = 1
                    )

                    // Mark Queue as Completed
                    val updatedReg = reg.copy(status = "Completed")
                    repository.updateRegistration(updatedReg)

                    // Secure Logging of sample bagging
                    val staff = activeUserProfile.value
                    repository.insertSecurityLog(
                        SecurityAuditLog(
                            activityType = "DONOR_SAMPLE_SECURED",
                            actorNik = staff?.nik ?: "SYSTEM",
                            actorName = staff?.name ?: "Petugas UDD PMI",
                            details = "Sampel darah $componentType (${user.bloodType}${user.rhesus}) dari pendonor ${user.name} (NIK: ${SecurityUtils.maskNik(user.nik)}) dinyatakan lolos uji saring & didistribusikan ke bank darah nasional."
                        )
                    )

                    // Insert custom notification with gamification badge rewards
                    val milestone = updatedUser.totalDonations
                    val badgeName = when (milestone) {
                        1 -> "🏆 Pahlawan Pertama"
                        3 -> "🥉 Bronze Vitality"
                        5 -> "🥈 Silver Lifesaver"
                        10 -> "🥇 Gold Guardian"
                        else -> null
                    }
                    val extraMsg = if (badgeName != null) " Keren, Anda membuka lencana baru: $badgeName!" else ""

                    val donorNotif = AppNotification(
                        recipientNik = user.nik,
                        recipientRole = "Donor",
                        title = "🎉 Donor Berhasil! +100 Poin",
                        message = "Terima kasih ${user.name}! 1 Kantong darah $componentType berhasil disumbangkan. Anda mendapat 100 Poin!$extraMsg"
                    )
                    repository.insertNotification(donorNotif)

                    _uiFeedback.emit("Pendonoran selesai! 1 Kantong darah ${user.bloodType}${user.rhesus} ($componentType) masuk ke stok.")
                }
            }
        }
    }

    fun submitNewDonorEvent(
        title: String,
        location: String,
        date: String,
        time: String,
        target: Int,
        desc: String
    ) {
        viewModelScope.launch {
            if (title.isEmpty() || location.isEmpty() || date.isEmpty() || time.isEmpty()) {
                _uiFeedback.emit("Mohon lengkapi seluruh field event.")
                return@launch
            }
            val event = DonorEvent(
                title = title,
                location = location,
                date = date,
                time = time,
                targetCount = target,
                description = desc
            )
            repository.insertDonorEvent(event)

            // Broadcast notification to all donors: new nearby donor event!
            val broadcastNotif = AppNotification(
                recipientNik = null,
                recipientRole = "Donor",
                title = "📢 Event Donor Baru Di Dekatmu!",
                message = "Hadir event baru: \"$title\" di $location tanggal $date ($time). Daftarkan diri Anda sekarang!"
            )
            repository.insertNotification(broadcastNotif)

            _uiFeedback.emit("Event donor baru berhasil dibagikan!")
        }
    }

    // PMI Request Management
    fun respondToBloodRequest(requestId: Int, approve: Boolean) {
        viewModelScope.launch {
            val req = repository.getBloodRequestById(requestId)
            if (req != null) {
                if (approve) {
                    // Verify stock availability
                    val stock = repository.allBloodStocks.firstOrNull()?.find {
                        it.bloodType == req.bloodType &&
                        it.rhesus == req.rhesus &&
                        it.componentType == req.componentType
                    }

                    val availableQty = stock?.quantity ?: 0
                    if (availableQty < req.bagsNeeded) {
                        _uiFeedback.emit("Stok TIDAK CUKUP! Tersedia $availableQty kantong, permintaan membutuhkan ${req.bagsNeeded} kantong.")
                        return@launch
                    }

                    // Deduct stock
                    repository.updateBloodStockQuantity(
                        bloodType = req.bloodType,
                        rhesus = req.rhesus,
                        componentType = req.componentType,
                        delta = -req.bagsNeeded
                    )

                    val updatedReq = req.copy(status = "Approved")
                    repository.updateBloodRequest(updatedReq)

                    // Insert status notification for Hospital admins
                    val hospitalNotif = AppNotification(
                        recipientNik = null,
                        recipientRole = "Hospital",
                        title = "📦 Permintaan Disetujui PMI",
                        message = "Permintaan ${req.bagsNeeded} kantong darah ${req.bloodType}${req.rhesus} ($req.componentType) oleh ${req.hospitalName} telah DISETUJUI oleh PMI."
                    )
                    repository.insertNotification(hospitalNotif)

                    _uiFeedback.emit("Permintaan darah disetujui! Stok darah otomatis berkurang.")
                } else {
                    val updatedReq = req.copy(status = "Rejected")
                    repository.updateBloodRequest(updatedReq)

                    val hospitalNotif = AppNotification(
                        recipientNik = null,
                        recipientRole = "Hospital",
                        title = "❌ Permintaan Ditolak PMI",
                        message = "Permintaan ${req.bagsNeeded} kantong darah ${req.bloodType}${req.rhesus} ($req.componentType) oleh ${req.hospitalName} telah DITOLAK oleh PMI."
                    )
                    repository.insertNotification(hospitalNotif)

                    _uiFeedback.emit("Permintaan darah ditolak.")
                }
            }
        }
    }

    fun dispatchRequest(requestId: Int) {
        viewModelScope.launch {
            val req = repository.getBloodRequestById(requestId)
            if (req != null && req.status == "Approved") {
                repository.updateBloodRequest(req.copy(status = "Delivered"))

                // Insert status notification for Hospital admins
                val hospitalNotif = AppNotification(
                    recipientNik = null,
                    recipientRole = "Hospital",
                    title = "🚚 Darah Sedang Dikirim",
                    message = "Paket darah ${req.bagsNeeded} kantong ${req.bloodType}${req.rhesus} ($req.componentType) sedang dalam pengiriman kurir ke ${req.hospitalName}."
                )
                repository.insertNotification(hospitalNotif)

                _uiFeedback.emit("Darah siap diambil / Sedang dikirim oleh kurir PMI!")
            }
        }
    }

    // --- HOSPITAL ADMIN ACTIONS ---
    fun submitBloodRequest(
        bloodType: String,
        rhesus: String,
        componentType: String,
        bagsCount: Int,
        urgency: String,
        notes: String
    ) {
        viewModelScope.launch {
            val user = activeUserProfile.value ?: return@launch
            if (!user.isHospitalAdmin) {
                _uiFeedback.emit("Akses Ditolak: Hanya admin Rumah Sakit.")
                return@launch
            }

            if (bagsCount <= 0) {
                _uiFeedback.emit("Jumlah kantong darah harus lebih dari 0.")
                return@launch
            }

            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val request = BloodRequest(
                hospitalName = user.hospitalName ?: "RS Swasta",
                bloodType = bloodType,
                rhesus = rhesus,
                componentType = componentType,
                bagsNeeded = bagsCount,
                urgency = urgency,
                status = "Pending",
                requestDate = todayStr,
                notes = notes
            )

            repository.insertBloodRequest(request)

            // Notify PMI staff of a new hospital blood request
            val pmiNotif = AppNotification(
                recipientNik = null,
                recipientRole = "PMI",
                title = "🚨 Permintaan Darah Baru (${urgency})",
                message = "${user.hospitalName ?: "RS Swasta"} mengajukan permintaan ${bagsCount} kantong ${bloodType}${rhesus} ($componentType)."
            )
            repository.insertNotification(pmiNotif)

            _uiFeedback.emit("Permintaan ${urgency} berhasil dikirim ke PMI!")
        }
    }

    fun confirmRequestReceived(requestId: Int) {
        viewModelScope.launch {
            val req = repository.getBloodRequestById(requestId)
            if (req != null) {
                repository.updateBloodRequest(req.copy(status = "Completed"))

                // Notify PMI staff that RS has received the bags successfully
                val pmiNotif = AppNotification(
                    recipientNik = null,
                    recipientRole = "PMI",
                    title = "✅ Pengiriman Darah Diterima",
                    message = "Kantong darah (${req.bagsNeeded} kantong ${req.bloodType}${req.rhesus}) telah diterima dengan sukses oleh Rumah Sakit ${req.hospitalName}."
                )
                repository.insertNotification(pmiNotif)

                _uiFeedback.emit("Konfirmasi sukses: Kantong darah telah diterima dengan baik di Rumah Sakit.")
            }
        }
    }

    // Register custom user public profile
    fun registerNewDonor(nik: String, name: String, email: String, bloodType: String, rhesus: String, pin: String) {
        viewModelScope.launch {
            if (nik.length < 16) {
                _uiFeedback.emit("NIK tidak valid (Wajib 16 digit).")
                return@launch
            }
            if (pin.length != 6) {
                _uiFeedback.emit("PIN Keamanan wajib 6 digit angka.")
                return@launch
            }
            if (name.isEmpty() || email.isEmpty()) {
                _uiFeedback.emit("Nama dan email wajib diisi.")
                return@launch
            }

            val exists = repository.getUserProfileByNik(nik)
            if (exists != null) {
                _uiFeedback.emit("NIK sudah terdaftar di sistem E-BloodBank.")
                return@launch
            }

            val salt = SecurityUtils.generateSalt()
            val pinHash = SecurityUtils.hashPin(pin, salt)

            val newUser = UserProfile(
                nik = nik,
                name = name,
                email = email,
                bloodType = bloodType,
                rhesus = rhesus,
                lastDonationDate = null,
                totalDonations = 0,
                salt = salt,
                pinHash = pinHash
            )

            repository.insertUserProfile(newUser)
            _currentActiveNik.value = nik

            repository.insertSecurityLog(
                SecurityAuditLog(
                    activityType = "REGISTER_DONOR",
                    actorNik = nik,
                    actorName = name,
                    details = "Registrasi pendonor baru terdaftar secara nasional dengan enkripsi PIN unik."
                )
            )

            _uiFeedback.emit("Registrasi Pendonor Berhasil! Selamat datang, ${name}.")
        }
    }

    // Register a new hospital admin profile
    fun registerNewHospital(nik: String, adminName: String, email: String, hospitalName: String, pin: String) {
        viewModelScope.launch {
            if (nik.length < 16) {
                _uiFeedback.emit("NIK tidak valid (Wajib 16 digit).")
                return@launch
            }
            if (pin.length != 6) {
                _uiFeedback.emit("PIN Keamanan wajib 6 digit angka.")
                return@launch
            }
            if (adminName.isEmpty() || email.isEmpty() || hospitalName.isEmpty()) {
                _uiFeedback.emit("Nama admin, email, dan nama rumah sakit wajib diisi.")
                return@launch
            }

            val exists = repository.getUserProfileByNik(nik)
            if (exists != null) {
                _uiFeedback.emit("NIK sudah terdaftar di sistem E-BloodBank.")
                return@launch
            }

            val salt = SecurityUtils.generateSalt()
            val pinHash = SecurityUtils.hashPin(pin, salt)

            val newUser = UserProfile(
                nik = nik,
                name = adminName,
                email = email,
                bloodType = "O", // Default placeholder for admin
                rhesus = "+",
                lastDonationDate = null,
                totalDonations = 0,
                isHospitalAdmin = true,
                hospitalName = hospitalName,
                salt = salt,
                pinHash = pinHash
            )

            repository.insertUserProfile(newUser)
            _currentActiveNik.value = nik

            repository.insertSecurityLog(
                SecurityAuditLog(
                    activityType = "REGISTER_HOSPITAL",
                    actorNik = nik,
                    actorName = adminName,
                    details = "Registrasi Rumah Sakit baru ($hospitalName) terdaftar secara nasional dengan enkripsi PIN unik."
                )
            )

            _uiFeedback.emit("Registrasi Rumah Sakit Berhasil! Selamat datang, ${hospitalName}.")
        }
    }

    // Log out of the active session, returning the app to the Landing/Login page
    fun logout() {
        viewModelScope.launch {
            _currentActiveNik.value = ""
            _uiFeedback.emit("Berhasil keluar dari sesi E-BloodBank.")
        }
    }

    // Direct update blood stock by PMI staff
    fun directUpdateBloodStock(bloodType: String, rhesus: String, componentType: String, delta: Int) {
        viewModelScope.launch {
            repository.updateBloodStockQuantity(bloodType, rhesus, componentType, delta)
            val staff = activeUserProfile.value
            repository.insertSecurityLog(
                SecurityAuditLog(
                    activityType = "STOCK_UPDATE",
                    actorNik = staff?.nik ?: "SYSTEM",
                    actorName = staff?.name ?: "Petugas PMI",
                    details = "Ubah stok manual $bloodType$rhesus ($componentType): ${if (delta >= 0) "+" else ""}$delta boks kantong."
                )
            )
            _uiFeedback.emit("Stok darah $bloodType$rhesus ($componentType) berhasil diubah sebesar $delta kantong.")
        }
    }

    // Help calculate diff calendar dates
    private fun calculateDaysRemaining(lastDonationStr: String?): Int {
        if (lastDonationStr.isNullOrEmpty()) return 0
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val lastDate = sdf.parse(lastDonationStr) ?: return 0
            val currentDate = Date()
            val diffInMillis = currentDate.time - lastDate.time
            val diffInDays = (diffInMillis / (1000 * 60 * 60 * 24)).toInt()
            val daysRemaining = 60 - diffInDays
            if (daysRemaining < 0) 0 else daysRemaining
        } catch (e: Exception) {
            0
        }
    }
}

// Data holder of user eligibility status
data class EligibilityState(
    val canDonor: Boolean,
    val daysRemaining: Int,
    val statusText: String
)

// Provider Factory
class EBloodBankViewModelFactory(
    private val application: Application,
    private val repository: EBloodBankRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EBloodBankViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EBloodBankViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
