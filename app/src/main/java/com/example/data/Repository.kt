package com.example.data

import kotlinx.coroutines.flow.Flow

class EBloodBankRepository(private val dao: EBloodBankDao) {
    val allUserProfiles: Flow<List<UserProfile>> = dao.getAllUserProfiles()
    val allBloodStocks: Flow<List<BloodStock>> = dao.getAllBloodStocks()
    val allDonorEvents: Flow<List<DonorEvent>> = dao.getAllDonorEvents()
    val allRegistrations: Flow<List<QueueRegistration>> = dao.getAllRegistrations()
    val allBloodRequests: Flow<List<BloodRequest>> = dao.getAllBloodRequests()

    suspend fun getUserProfileByNik(nik: String): UserProfile? = dao.getUserProfileByNik(nik)
    suspend fun insertUserProfile(profile: UserProfile) = dao.insertUserProfile(profile)

    suspend fun updateBloodStockQuantity(bloodType: String, rhesus: String, componentType: String, delta: Int) {
        val stock = dao.getBloodStock(bloodType, rhesus, componentType)
        if (stock != null) {
            val newQty = (stock.quantity + delta).coerceAtLeast(0)
            dao.updateBloodStock(stock.copy(quantity = newQty))
        } else {
            // Row might not exist, insert it
            if (delta > 0) {
                dao.insertBloodStocks(listOf(
                    BloodStock(
                        bloodType = bloodType,
                        rhesus = rhesus,
                        componentType = componentType,
                        quantity = delta
                    )
                ))
            }
        }
    }

    suspend fun getDonorEventById(id: Int) = dao.getDonorEventById(id)
    suspend fun insertDonorEvent(event: DonorEvent) = dao.insertDonorEvent(event)
    suspend fun updateDonorEvent(event: DonorEvent) = dao.updateDonorEvent(event)
    suspend fun deleteDonorEvent(event: DonorEvent) = dao.deleteDonorEvent(event)

    fun getRegistrationsByNik(nik: String): Flow<List<QueueRegistration>> = dao.getRegistrationsByNik(nik)
    fun getRegistrationsForEvent(eventId: Int): Flow<List<QueueRegistration>> = dao.getRegistrationsForEvent(eventId)
    suspend fun getRegistrationById(id: Int) = dao.getRegistrationById(id)
    suspend fun insertRegistration(reg: QueueRegistration) = dao.insertRegistration(reg)
    suspend fun updateRegistration(reg: QueueRegistration) = dao.updateRegistration(reg)

    fun getBloodRequestsByHospital(hospitalName: String): Flow<List<BloodRequest>> = dao.getBloodRequestsByHospital(hospitalName)
    suspend fun getBloodRequestById(id: Int) = dao.getBloodRequestById(id)
    suspend fun insertBloodRequest(request: BloodRequest) = dao.insertBloodRequest(request)
    suspend fun updateBloodRequest(request: BloodRequest) = dao.updateBloodRequest(request)

    // --- NOTIFICATIONS ---
    val allNotifications: Flow<List<AppNotification>> = dao.getAllNotifications()
    fun getNotificationsByRecipient(nik: String?, role: String?): Flow<List<AppNotification>> = dao.getNotificationsByRecipient(nik, role)
    suspend fun insertNotification(notif: AppNotification) = dao.insertNotification(notif)
    suspend fun markNotificationAsRead(id: Int) = dao.markNotificationAsRead(id)
    suspend fun markAllAsReadForUser(nik: String?, role: String?) = dao.markAllAsReadForUser(nik, role)

    // --- SECURITY LOGS ---
    val allSecurityLogs: Flow<List<SecurityAuditLog>> = dao.getAllSecurityLogs()
    suspend fun getLastSecurityLog(): SecurityAuditLog? = dao.getLastSecurityLog()
    suspend fun insertSecurityLog(log: SecurityAuditLog) {
        val lastSig = dao.getLastSecurityLog()?.doubleHashSignature ?: "GENESIS_BLOCK_00000000"
        val signed = log.copy(doubleHashSignature = SecurityUtils.calculateTamperSignature(log, lastSig))
        dao.insertSecurityLog(signed)
    }
    suspend fun clearSecurityLogs() = dao.clearSecurityLogs()
}
