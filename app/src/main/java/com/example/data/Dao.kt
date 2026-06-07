package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EBloodBankDao {
    // --- USER PROFILE ---
    @Query("SELECT * FROM user_profiles")
    fun getAllUserProfiles(): Flow<List<UserProfile>>

    @Query("SELECT COUNT(*) FROM user_profiles")
    suspend fun getUserProfileCount(): Int

    @Query("SELECT * FROM user_profiles WHERE nik = :nik")
    suspend fun getUserProfileByNik(nik: String): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(user: UserProfile)

    // --- BLOOD STOCKS ---
    @Query("SELECT * FROM blood_stocks")
    fun getAllBloodStocks(): Flow<List<BloodStock>>

    @Query("SELECT * FROM blood_stocks WHERE bloodType = :type AND rhesus = :rhesus AND componentType = :component LIMIT 1")
    suspend fun getBloodStock(type: String, rhesus: String, component: String): BloodStock?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBloodStocks(stocks: List<BloodStock>)

    @Update
    suspend fun updateBloodStock(stock: BloodStock)

    // --- DONOR EVENTS ---
    @Query("SELECT * FROM donor_events ORDER BY date ASC")
    fun getAllDonorEvents(): Flow<List<DonorEvent>>

    @Query("SELECT * FROM donor_events WHERE id = :id")
    suspend fun getDonorEventById(id: Int): DonorEvent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDonorEvent(event: DonorEvent)

    @Update
    suspend fun updateDonorEvent(event: DonorEvent)

    @Delete
    suspend fun deleteDonorEvent(event: DonorEvent)

    // --- QUEUE REGISTRATIONS ---
    @Query("SELECT * FROM queue_registrations ORDER BY id DESC")
    fun getAllRegistrations(): Flow<List<QueueRegistration>>

    @Query("SELECT * FROM queue_registrations WHERE userNik = :nik ORDER BY id DESC")
    fun getRegistrationsByNik(nik: String): Flow<List<QueueRegistration>>

    @Query("SELECT * FROM queue_registrations WHERE eventId = :eventId ORDER BY id ASC")
    fun getRegistrationsForEvent(eventId: Int): Flow<List<QueueRegistration>>

    @Query("SELECT * FROM queue_registrations WHERE id = :id")
    suspend fun getRegistrationById(id: Int): QueueRegistration?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegistration(reg: QueueRegistration)

    @Update
    suspend fun updateRegistration(reg: QueueRegistration)

    // --- BLOOD REQUESTS ---
    @Query("SELECT * FROM blood_requests ORDER BY CASE WHEN urgency = 'EMERGENCY' THEN 0 ELSE 1 END, id DESC")
    fun getAllBloodRequests(): Flow<List<BloodRequest>>

    @Query("SELECT * FROM blood_requests WHERE hospitalName = :hospitalName ORDER BY id DESC")
    fun getBloodRequestsByHospital(hospitalName: String): Flow<List<BloodRequest>>

    @Query("SELECT * FROM blood_requests WHERE id = :id")
    suspend fun getBloodRequestById(id: Int): BloodRequest?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBloodRequest(request: BloodRequest)

    @Update
    suspend fun updateBloodRequest(request: BloodRequest)

    // --- NOTIFICATIONS ---
    @Query("SELECT * FROM app_notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<AppNotification>>

    @Query("SELECT * FROM app_notifications WHERE recipientNik = :nik OR recipientRole = :role OR (recipientNik IS NULL AND recipientRole IS NULL) ORDER BY timestamp DESC")
    fun getNotificationsByRecipient(nik: String?, role: String?): Flow<List<AppNotification>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notif: AppNotification)

    @Query("UPDATE app_notifications SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationAsRead(id: Int)

    @Query("UPDATE app_notifications SET isRead = 1 WHERE recipientNik = :nik OR recipientRole = :role")
    suspend fun markAllAsReadForUser(nik: String?, role: String?)

    // --- SECURITY AUDIT LOGS ---
    @Query("SELECT * FROM security_audit_logs ORDER BY timestamp DESC")
    fun getAllSecurityLogs(): Flow<List<SecurityAuditLog>>

    @Query("SELECT * FROM security_audit_logs ORDER BY id DESC LIMIT 1")
    suspend fun getLastSecurityLog(): SecurityAuditLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSecurityLog(log: SecurityAuditLog)

    @Query("DELETE FROM security_audit_logs")
    suspend fun clearSecurityLogs()
}
