package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        UserProfile::class,
        BloodStock::class,
        DonorEvent::class,
        QueueRegistration::class,
        BloodRequest::class,
        AppNotification::class,
        SecurityAuditLog::class
    ],
    version = 3,
    exportSchema = false
)
abstract class EBloodBankDatabase : RoomDatabase() {
    abstract fun dao(): EBloodBankDao

    companion object {
        @Volatile
        private var INSTANCE: EBloodBankDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): EBloodBankDatabase {
            return INSTANCE ?: synchronized(this) {
                val built = Room.databaseBuilder(
                    context.applicationContext,
                    EBloodBankDatabase::class.java,
                    "ebloodbank_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = built

                // Prepopulate database asynchronously if empty, safely outside builder callbacks
                scope.launch(Dispatchers.IO) {
                    val count = try {
                        built.dao().getUserProfileCount()
                    } catch (e: Exception) {
                        0
                    }
                    if (count == 0) {
                        prepopulateData(built.dao())
                    }
                }

                built
            }
        }

        private suspend fun prepopulateData(dao: EBloodBankDao) {
            val saltIndra = "eblood_salt_91238"
            val saltSiti = "eblood_salt_91239"
            val saltSusi = "eblood_salt_91240"
            val saltBudi = "eblood_salt_91241"

            // 1. Initial User Profiles
            val users = listOf(
                UserProfile(
                    nik = "3271012345670001",
                    name = "Indra Wijaya",
                    email = "indra.wijaya@gmail.com",
                    bloodType = "O",
                    rhesus = "+",
                    lastDonationDate = "2026-04-10", // 43 days ago
                    totalDonations = 5,
                    points = 500, // Seeding starting points
                    salt = saltIndra,
                    pinHash = SecurityUtils.hashPin("123456", saltIndra),
                    useBiometrics = true
                ),
                UserProfile(
                    nik = "3271012345670002",
                    name = "Siti Rahma",
                    email = "siti.rahma@mail.com",
                    bloodType = "A",
                    rhesus = "+",
                    lastDonationDate = "2026-01-15", // eligible for donor!
                    totalDonations = 12,
                    points = 1200, // Seeding starting points
                    salt = saltSiti,
                    pinHash = SecurityUtils.hashPin("123456", saltSiti)
                ),
                UserProfile(
                    nik = "3271012345670003",
                    name = "dr. Susi Susanti",
                    email = "susi@rshatasehat.com",
                    bloodType = "B",
                    rhesus = "+",
                    lastDonationDate = null,
                    isHospitalAdmin = true,
                    hospitalName = "RS Harapan Sehat",
                    points = 0,
                    salt = saltSusi,
                    pinHash = SecurityUtils.hashPin("123456", saltSusi)
                ),
                UserProfile(
                    nik = "3271012345670004",
                    name = "Budi Santoso",
                    email = "budi@pmi-jakarta.org",
                    bloodType = "AB",
                    rhesus = "+",
                    lastDonationDate = null,
                    isPmiStaff = true,
                    points = 0,
                    salt = saltBudi,
                    pinHash = SecurityUtils.hashPin("123456", saltBudi)
                )
            )
            users.forEach { dao.insertUserProfile(it) }

            // Insert initial security logging
            val initLog = SecurityAuditLog(
                activityType = "SYSTEM_INIT",
                actorNik = "SYSTEM",
                actorName = "E-BloodBank Security Core",
                details = "Sistem keamanan nasional diinisialisasi. Modul enkripsi SHA-256 aktif. Ledger pencatatan audit kosong."
            )
            val signed = initLog.copy(doubleHashSignature = SecurityUtils.calculateTamperSignature(initLog, "GENESIS_BLOCK_00000000"))
            dao.insertSecurityLog(signed)

            // 2. Initial Blood Stocks
            val stockList = mutableListOf<BloodStock>()
            val types = listOf("A", "B", "AB", "O")
            val rhesuses = listOf("+", "-")
            val components = listOf("Whole Blood", "Thrombocyte", "Packed Red Cells", "Fresh Frozen Plasma")

            for (t in types) {
                for (r in rhesuses) {
                    for (c in components) {
                        val qty = when {
                            t == "O" && r == "+" && c == "Packed Red Cells" -> 45
                            t == "A" && r == "+" && c == "Whole Blood" -> 22
                            t == "B" && r == "+" && c == "Thrombocyte" -> 15
                            t == "AB" && r == "-" -> 3  // Rare is low!
                            r == "-" -> 6
                            else -> (12..28).random()
                        }
                        stockList.add(BloodStock(bloodType = t, rhesus = r, componentType = c, quantity = qty))
                    }
                }
            }
            dao.insertBloodStocks(stockList)

            // 3. Initial Events
            val events = listOf(
                DonorEvent(
                    title = "Donor Keliling Mall Gandaria City",
                    location = "Lobby Utara lt. Dasar, Jakarta Selatan",
                    date = "2026-05-25",
                    time = "10:00 - 15:00",
                    targetCount = 100,
                    registeredCount = 42,
                    description = "Mari donorkan darah Anda untuk membantu sesama di event Mobile Donor Gandaria City. Dapatkan merchandise menarik dan snack pemulihan gratis dari PMI DKI Jakarta."
                ),
                DonorEvent(
                    title = "PMI Merdeka - Kantor Walikota",
                    location = "Aula Pertemuan Walikota Depok, Jawa Barat",
                    date = "2026-05-30",
                    time = "08:30 - 13:00",
                    targetCount = 150,
                    registeredCount = 15,
                    description = "Aksi kemanusiaan donor darah bersama aparatur sipil negara dan masyarakat umum daerah Depok. Terbuka untuk seluruh golongan darah dengan screening kesehatan gratis."
                ),
                DonorEvent(
                    title = "Sedekah Darah Ramah Lingkungan",
                    location = "Gedung Serbaguna PMI Tangerang Selatan",
                    date = "2026-06-05",
                    time = "09:00 - 14:00",
                    targetCount = 80,
                    registeredCount = 5,
                    description = "Event donor rutin PMI Tangerang Selatan. Memperkuat stok darah menjelang musim liburan pertengahan tahun."
                )
            )
            events.forEach { dao.insertDonorEvent(it) }

            // 4. Initial Requests
            val requests = listOf(
                BloodRequest(
                    hospitalName = "RS Harapan Sehat",
                    bloodType = "O",
                    rhesus = "+",
                    componentType = "Packed Red Cells",
                    bagsNeeded = 5,
                    urgency = "EMERGENCY",
                    status = "Pending",
                    requestDate = "2026-05-23",
                    notes = "Dibutuhkan segera untuk pasien operasi bedah jantung darurat di ICU."
                ),
                BloodRequest(
                    hospitalName = "RS Medika Utama",
                    bloodType = "A",
                    rhesus = "+",
                    componentType = "Thrombocyte",
                    bagsNeeded = 10,
                    urgency = "NORMAL",
                    status = "Delivered",
                    requestDate = "2026-05-22",
                    notes = "Stok rutin trombosit untuk pasien demam berdarah (DBD) anak."
                ),
                BloodRequest(
                    hospitalName = "RS Siloam Sejahtera",
                    bloodType = "AB",
                    rhesus = "-",
                    componentType = "Whole Blood",
                    bagsNeeded = 2,
                    urgency = "EMERGENCY",
                    status = "Approved",
                    requestDate = "2026-05-23",
                    notes = "Pasien kecelakaan kritis, golongan darah langka rhesus negatif."
                )
            )
            requests.forEach { dao.insertBloodRequest(it) }

            // 5. Initial Registrations
            val registrations = listOf(
                QueueRegistration(
                    eventId = 1,
                    eventTitle = "Donor Keliling Mall Gandaria City",
                    eventLocation = "Lobby Utara lt. Dasar, Jakarta Selatan",
                    userNik = "3271012345670002",
                    userName = "Siti Rahma",
                    date = "2026-05-25",
                    timeSlot = "10:00 - 11:00",
                    queueNumber = "A-012",
                    qrCodeData = "EBB-Q-1002",
                    status = "Registered"
                )
            )
            registrations.forEach { dao.insertRegistration(it) }

            // 6. Initial App Notifications
            val notifications = listOf(
                AppNotification(
                    recipientNik = null,
                    recipientRole = "Donor",
                    title = "📢 Event Baru Di Dekatmu!",
                    message = "Aksi kemanusiaan terdekat: \"Donor Keliling Mall Gandaria City\" dilangsungkan 25 Mei 2026. Mari ikut serta!"
                ),
                AppNotification(
                    recipientNik = "3271012345670002",
                    recipientRole = "Donor",
                    title = "🟢 Masa Tunggang Selesai",
                    message = "Selamat, Siti Rahma! Anda saat ini sudah kembali memenuhi syarat kelayakan untuk mendonorkan darah Anda."
                ),
                AppNotification(
                    recipientNik = null,
                    recipientRole = "PMI",
                    title = "🚨 Permintaan Darurat Baru",
                    message = "Rumah Sakit: RS Harapan Sehat mengirimkan permintaan darurat untuk 5 kantong darah O+ Packed Red Cells."
                ),
                AppNotification(
                    recipientNik = null,
                    recipientRole = "Hospital",
                    title = "📦 Update Status Permintaan",
                    message = "Permintaan RS Harapan Sehat: 10 kantong darah A+ Thrombocyte oleh RS Medika Utama telah dikirim (Delivered)."
                )
            )
            notifications.forEach { dao.insertNotification(it) }
        }
    }
}
