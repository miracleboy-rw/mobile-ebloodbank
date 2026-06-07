package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.security.MessageDigest

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val nik: String,
    val name: String,
    val email: String,
    val bloodType: String,
    val rhesus: String,
    val lastDonationDate: String?, // ISO format YYYY-MM-DD or null
    val totalDonations: Int = 0,
    val isPmiStaff: Boolean = false,
    val isHospitalAdmin: Boolean = false,
    val hospitalName: String? = null,
    val points: Int = 0, // Added gamification points
    val pinHash: String = "8d969eee76ec24eacd411451f2867d2ca9e246d77e4a16ca1afc1b117b40e2e6", // Default fallback PIN "123456" hash
    val salt: String = "eblood_salt_91238",
    val useBiometrics: Boolean = false,
    val showEncryptedLabels: Boolean = true
)

@Entity(tableName = "security_audit_logs")
data class SecurityAuditLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val activityType: String, // "AUTH_SUCCESS", "AUTH_FAILURE", "STOCK_UPDATE", "REQUEST_APPROVED", "HEALTH_SCREENING", "EVENT_CREATION"
    val actorNik: String,
    val actorName: String,
    val details: String,
    val ipAddress: String = "10.224.51.89", // Secured VPN Local Subnet
    val doubleHashSignature: String = "" // Simulates private block chain tampering protection
)

object SecurityUtils {
    fun hashPin(pin: String, salt: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest((pin + salt).toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            pin // fallback if cipher unavailable
        }
    }

    fun generateSalt(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..12).map { chars.random() }.joinToString("")
    }

    fun maskNik(nik: String): String {
        if (nik.length < 12) return nik
        return nik.take(4) + " •••• •••• " + nik.takeLast(4)
    }

    fun maskEmail(email: String): String {
        val parts = email.split("@")
        if (parts.size != 2) return email
        val name = parts[0]
        val domain = parts[1]
        if (name.length <= 2) return "${name.take(1)}***@$domain"
        return "${name.take(2)}*****@$domain"
    }

    fun calculateTamperSignature(log: SecurityAuditLog, lastSignature: String): String {
        val payload = "${log.timestamp}|${log.activityType}|${log.actorNik}|${log.details}|$lastSignature"
        return hashPin(payload, "audit_chain_salt_key_7782")
    }
}

@Entity(tableName = "blood_stocks")
data class BloodStock(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bloodType: String,     // A, B, AB, O
    val rhesus: String,        // +, -
    val componentType: String, // Whole Blood, Thrombocyte, Packed Red Cells, Fresh Frozen Plasma
    val quantity: Int          // Number of bags
)

@Entity(tableName = "donor_events")
data class DonorEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val location: String,
    val date: String,          // ISO format YYYY-MM-DD
    val time: String,          // e.g., "08:00 - 13:00"
    val targetCount: Int,
    val registeredCount: Int = 0,
    val description: String
)

@Entity(tableName = "queue_registrations")
data class QueueRegistration(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val eventId: Int,
    val eventTitle: String,
    val eventLocation: String,
    val userNik: String,
    val userName: String,
    val date: String,          // YYYY-MM-DD
    val timeSlot: String,      // e.g. "10:00 - 11:00"
    val queueNumber: String,   // e.g., "G-012"
    val qrCodeData: String,    // e.g., "EBB-Q-1002"
    val status: String,        // "Registered", "CheckedIn", "Screened_Lolos", "Screened_Gagal", "Completed"
    val systolicBp: Int? = null,
    val diastolicBp: Int? = null,
    val hemoglobin: Double? = null,
    val weightKg: Double? = null,
    val screeningNotes: String? = null,
    val checkedInAt: Long? = null,
    val screenedAt: Long? = null
)

@Entity(tableName = "blood_requests")
data class BloodRequest(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hospitalName: String,
    val bloodType: String,
    val rhesus: String,
    val componentType: String,
    val bagsNeeded: Int,
    val urgency: String,       // "NORMAL", "EMERGENCY"
    val status: String,        // "Pending", "Approved", "Delivered", "Completed", "Rejected"
    val requestDate: String,   // YYYY-MM-DD
    val notes: String? = null
)

@Entity(tableName = "app_notifications")
data class AppNotification(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val recipientNik: String?, // Target user NIK, or null for roles/all
    val recipientRole: String?, // "Donor", "PMI", "Hospital"
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
