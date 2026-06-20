package com.example.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PacketDao {
    @Query("SELECT * FROM packet_logs WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    fun getPacketsForSession(sessionId: String): Flow<List<PacketEntity>>

    @Query("SELECT * FROM packet_logs ORDER BY timestamp DESC LIMIT 500")
    fun getAllPacketsPaged(): Flow<List<PacketEntity>>

    @Query("SELECT * FROM packet_logs ORDER BY timestamp ASC")
    suspend fun getAllPacketsSnapshot(): List<PacketEntity>

    @Query("SELECT * FROM packet_logs WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getPacketsForSessionSnapshot(sessionId: String): List<PacketEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPacket(packet: PacketEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackets(packets: List<PacketEntity>)

    @Query("DELETE FROM packet_logs WHERE sessionId = :sessionId")
    suspend fun deletePacketsForSession(sessionId: String)

    @Query("DELETE FROM packet_logs")
    suspend fun clearAllPackets()
}

@Dao
interface DeviceDao {
    @Query("SELECT * FROM network_devices ORDER BY ipAddress ASC")
    fun getAllDevices(): Flow<List<DeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: DeviceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevices(devices: List<DeviceEntity>)

    @Query("SELECT * FROM network_devices WHERE macAddress = :mac LIMIT 1")
    suspend fun getDeviceByMac(mac: String): DeviceEntity?

    @Query("UPDATE network_devices SET isTrusted = :isTrusted WHERE macAddress = :macAddress")
    suspend fun updateDeviceTrust(macAddress: String, isTrusted: Boolean)

    @Delete
    suspend fun deleteDevice(device: DeviceEntity)

    @Query("DELETE FROM network_devices")
    suspend fun clearDevices()
}

@Dao
interface IdsRuleDao {
    @Query("SELECT * FROM ids_rules ORDER BY id DESC")
    fun getAllRules(): Flow<List<IdsRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: IdsRuleEntity)

    @Delete
    suspend fun deleteRule(rule: IdsRuleEntity)

    @Query("UPDATE ids_rules SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun toggleRule(id: Long, isEnabled: Boolean)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM saved_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Delete
    suspend fun deleteSession(session: SessionEntity)

    @Query("DELETE FROM saved_sessions")
    suspend fun clearAllSessions()
}
