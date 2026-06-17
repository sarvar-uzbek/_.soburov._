package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class GameRepository(private val gameDao: GameDao) {
    val playerProfile: Flow<PlayerProfile?> = gameDao.getPlayerProfile()
    val recentLogs: Flow<List<GameLog>> = gameDao.getRecentLogs()

    suspend fun insertOrUpdateProfile(profile: PlayerProfile) {
        gameDao.insertPlayerProfile(profile)
    }

    suspend fun updateUsernameAndFaction(username: String, faction: String) {
        val currentProfile = playerProfile.firstOrNull() ?: PlayerProfile()
        gameDao.insertPlayerProfile(
            currentProfile.copy(
                username = username,
                faction = faction
            )
        )
    }

    suspend fun recordMatch(
        opponentName: String,
        opponentType: String,
        playerMove: String,
        opponentMove: String,
        result: String
    ): XpReward {
        // Create and save game log
        val log = GameLog(
            opponentName = opponentName,
            opponentType = opponentType,
            playerMove = playerMove,
            opponentMove = opponentMove,
            result = result
        )
        gameDao.insertGameLog(log)

        // Fetch current profile (default if missing) and update stats
        val currentProfile = playerProfile.firstOrNull() ?: PlayerProfile()
        
        val newWins = if (result == "WIN") currentProfile.wins + 1 else currentProfile.wins
        val newLosses = if (result == "LOSS") currentProfile.losses + 1 else currentProfile.losses
        val newTies = if (result == "TIE") currentProfile.ties + 1 else currentProfile.ties
        
        val newRockCount = if (playerMove == "ROCK") currentProfile.rockCount + 1 else currentProfile.rockCount
        val newPaperCount = if (playerMove == "PAPER") currentProfile.paperCount + 1 else currentProfile.paperCount
        val newScissorsCount = if (playerMove == "SCISSORS") currentProfile.scissorsCount + 1 else currentProfile.scissorsCount
        
        val newStreak = if (result == "WIN") currentProfile.currentStreak + 1 else if (result == "LOSS") 0 else currentProfile.currentStreak
        val newMaxStreak = maxOf(currentProfile.maxStreak, newStreak)
        
        val newAiHardWins = if (result == "WIN" && opponentType == "AI (Hard)") currentProfile.aiHardWins + 1 else currentProfile.aiHardWins

        // Award XP
        val xpEarned = when (result) {
            "WIN" -> when (opponentType) {
                "AI (Hard)" -> 70
                "AI (Medium)" -> 45
                "AI (Easy)" -> 30
                "Simulated Online" -> 60
                else -> 40
            }
            "TIE" -> 20
            "LOSS" -> 10
            else -> 10
        }

        var newXp = currentProfile.xp + xpEarned
        var newLevel = currentProfile.level
        var xpNeeded = newLevel * 100
        var leveledUp = false

        while (newXp >= xpNeeded) {
            newXp -= xpNeeded
            newLevel++
            xpNeeded = newLevel * 100
            leveledUp = true
        }

        val updatedProfile = currentProfile.copy(
            wins = newWins,
            losses = newLosses,
            ties = newTies,
            rockCount = newRockCount,
            paperCount = newPaperCount,
            scissorsCount = newScissorsCount,
            currentStreak = newStreak,
            maxStreak = newMaxStreak,
            aiHardWins = newAiHardWins,
            xp = newXp,
            level = newLevel
        )
        gameDao.insertPlayerProfile(updatedProfile)
        
        return XpReward(xpEarned, newXp, newLevel, leveledUp)
    }

    suspend fun clearHistory() {
        gameDao.clearAllLogs()
        val currentProfile = playerProfile.firstOrNull() ?: PlayerProfile()
        gameDao.insertPlayerProfile(
            PlayerProfile(
                id = 1,
                username = currentProfile.username,
                faction = currentProfile.faction
            )
        )
    }
}

data class XpReward(
    val xpEarned: Int,
    val currentXp: Int,
    val currentLevel: Int,
    val leveledUp: Boolean
)
