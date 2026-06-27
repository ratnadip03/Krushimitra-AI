package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FarmerProfileDao {
    @Query("SELECT * FROM farmer_profile WHERE farmerId = :id")
    fun getFarmerProfile(id: String): Flow<FarmerProfile?>

    @Query("SELECT * FROM farmer_profile WHERE farmerId = :id")
    suspend fun getFarmerProfileOnce(id: String): FarmerProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFarmerProfile(profile: FarmerProfile)
}
