package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "player_profile")
data class PlayerProfile(
    @PrimaryKey val id: Int = 1,
    val username: String = "CyberRider",
    val faction: String = "NEON_GLOW", // NEON_GLOW, SYNTH_WAVE, CYBER_PUNK, PLASMA_CANNON
    val level: Int = 1,
    val xp: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val ties: Int = 0,
    val rockCount: Int = 0,
    val paperCount: Int = 0,
    val scissorsCount: Int = 0,
    val currentStreak: Int = 0,
    val maxStreak: Int = 0,
    val aiHardWins: Int = 0
) {
    fun xpToNextLevel(): Int = level * 100
}

@Entity(tableName = "game_logs")
data class GameLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val opponentName: String,
    val opponentType: String, // "AI (Easy)", "AI (Medium)", "AI (Hard)", "Local 2P", "Simulated Online"
    val playerMove: String, // "ROCK", "PAPER", "SCISSORS"
    val opponentMove: String, // "ROCK", "PAPER", "SCISSORS"
    val result: String, // "WIN", "LOSS", "TIE"
    val timestamp: Long = System.currentTimeMillis()
)
