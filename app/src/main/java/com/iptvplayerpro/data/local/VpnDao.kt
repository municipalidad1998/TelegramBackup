package com.iptvplayerpro.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VpnDao {

    @Query("SELECT * FROM vpn_profiles ORDER BY country, name")
    fun observeAll(): Flow<List<VpnProfileEntity>>

    @Query("SELECT * FROM vpn_profiles WHERE id = :id")
    suspend fun getById(id: Long): VpnProfileEntity?

    @Upsert
    suspend fun upsert(profile: VpnProfileEntity): Long

    @Query("DELETE FROM vpn_profiles WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE vpn_profiles SET lastConnectedAt = :timestamp WHERE id = :id")
    suspend fun setLastConnected(id: Long, timestamp: Long)

    @Query("SELECT COUNT(*) FROM vpn_profiles")
    suspend fun count(): Int
}
