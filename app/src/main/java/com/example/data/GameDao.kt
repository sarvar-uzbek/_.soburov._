package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM player_profile WHERE id = 1 LIMIT 1")
    fun getPlayerProfile(): Flow<PlayerProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayerProfile(profile: PlayerProfile)

    @Query("SELECT * FROM game_logs ORDER BY timestamp DESC LIMIT 30")
    fun getRecentLogs(): Flow<List<GameLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGameLog(log: GameLog)

    @Query("DELETE FROM game_logs")
    suspend fun clearAllLogs()
}
