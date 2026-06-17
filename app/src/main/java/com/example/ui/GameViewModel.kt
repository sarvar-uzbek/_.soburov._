package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val database = GameDatabase.getDatabase(application)
    private val repository = GameRepository(database.gameDao())

    val playerProfile: StateFlow<PlayerProfile> = repository.playerProfile
        .map { it ?: PlayerProfile() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayerProfile())

    val recentLogs: StateFlow<List<GameLog>> = repository.recentLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI States
    private val _screenState = MutableStateFlow(ScreenState.HOME)
    val screenState: StateFlow<ScreenState> = _screenState.asStateFlow()

    // VS AI Settings & Game State
    private val _aiDifficulty = MutableStateFlow(AiDifficulty.MEDIUM)
    val aiDifficulty: StateFlow<AiDifficulty> = _aiDifficulty.asStateFlow()

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    // Simulated Online Matchmaking State
    private val _onlineState = MutableStateFlow<OnlineState>(OnlineState.Idle)
    val onlineState: StateFlow<OnlineState> = _onlineState.asStateFlow()

    // Real-time achievement unlock notification
    private val _unlockedAchievement = MutableStateFlow<String?>(null)
    val unlockedAchievement: StateFlow<String?> = _unlockedAchievement.asStateFlow()

    // Particle Effect Trigger
    private val _particleTrigger = MutableStateFlow<ParticleTrigger?>(null)
    val particleTrigger: StateFlow<ParticleTrigger?> = _particleTrigger.asStateFlow()

    // Session-based player move history for AI Hard Mode prediction
    private val sessionPlayerMoves = mutableListOf<Move>()

    // Achievements calculation
    val unlockedAchievementsList: StateFlow<Set<String>> = playerProfile.map { profile ->
        val set = mutableSetOf<String>()
        if (profile.wins + profile.losses + profile.ties >= 1) set.add("ACH_FIRST_GAME")
        if (profile.wins >= 5) set.add("ACH_VETERAN_WINS")
        if (profile.wins >= 25) set.add("ACH_GLADIATOR")
        if (profile.aiHardWins >= 5) set.add("ACH_HARD_AI_SLAYER")
        if (profile.maxStreak >= 3) set.add("ACH_STREAK_3")
        if (profile.maxStreak >= 7) set.add("ACH_STREAK_7")
        if (profile.level >= 5) set.add("ACH_LEVEL_5")
        if (profile.rockCount >= 10 && profile.paperCount >= 10 && profile.scissorsCount >= 10) set.add("ACH_VERSATILE")
        set
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private var previousAchievements = emptySet<String>()

    init {
        // Automatically check achievement differences to trigger toast overlays
        viewModelScope.launch {
            unlockedAchievementsList.collect { current ->
                if (previousAchievements.isNotEmpty()) {
                    val newlyUnlocked = current - previousAchievements
                    newlyUnlocked.firstOrNull()?.let { achId ->
                        val achTitle = getAchievementTitle(achId)
                        _unlockedAchievement.value = achTitle
                        delay(3500)
                        _unlockedAchievement.value = null
                    }
                }
                previousAchievements = current
            }
        }
    }

    fun navigateTo(state: ScreenState) {
        _screenState.value = state
        resetGame()
    }

    fun setAiDifficulty(diff: AiDifficulty) {
        _aiDifficulty.value = diff
    }

    fun updateProfile(username: String, faction: String) {
        viewModelScope.launch {
            repository.updateUsernameAndFaction(username, faction)
        }
    }

    fun resetStats() {
        viewModelScope.launch {
            repository.clearHistory()
            sessionPlayerMoves.clear()
        }
    }

    // VS AI game interaction
    fun selectMoveVsAi(playerMove: Move) {
        if (_gameState.value.isRoundResolving) return

        viewModelScope.launch {
            _gameState.update { it.copy(player1Move = playerMove, isRoundResolving = true) }
            val aiMove = getPredictiveAiMove()
            sessionPlayerMoves.add(playerMove)

            // Resolve round
            delay(1000) // Aesthetic delay for choices
            val outcome = determineOutcome(playerMove, aiMove)

            _gameState.update {
                it.copy(
                    player2Move = aiMove,
                    roundResult = outcome,
                    player1Score = if (outcome == RoundResult.PLAYER1_WIN) it.player1Score + 1 else it.player1Score,
                    player2Score = if (outcome == RoundResult.PLAYER2_WIN) it.player2Score + 1 else it.player2Score
                )
            }

            // Trigger particle blast
            triggerParticles(outcome, isLocal2P = false)

            delay(2000)

            // Check if game (best of 5 or 3 points) is over
            val currentState = _gameState.value
            val targetScore = 3
            if (currentState.player1Score >= targetScore || currentState.player2Score >= targetScore) {
                val gameWinner = if (currentState.player1Score >= targetScore) "PLAYER" else "AI"
                val matchResult = if (gameWinner == "PLAYER") "WIN" else "LOSS"

                repository.recordMatch(
                    opponentName = "AI (${_aiDifficulty.value.name})",
                    opponentType = "AI (${_aiDifficulty.value.displayName})",
                    playerMove = playerMove.name,
                    opponentMove = aiMove.name,
                    result = matchResult
                )

                _gameState.update { it.copy(gameOverResult = gameWinner) }
            } else {
                // Clear round info for next choice
                _gameState.update {
                    it.copy(
                        player1Move = Move.NONE,
                        player2Move = Move.NONE,
                        roundResult = null,
                        isRoundResolving = false
                    )
                }
            }
        }
    }

    // Local 2 Player game interaction (Simultaneous)
    fun registerLocalMove(player: Int, move: Move) {
        val current = _gameState.value
        if (current.isRoundResolving || current.gameOverResult != null) return

        _gameState.update {
            if (player == 1) {
                it.copy(player1Move = move)
            } else {
                it.copy(player2Move = move)
            }
        }

        // Both players have selected a move!
        val updated = _gameState.value
        if (updated.player1Move != Move.NONE && updated.player2Move != Move.NONE) {
            viewModelScope.launch {
                _gameState.update { it.copy(isRoundResolving = true) }
                delay(1200) // Build anticipation

                val outcome = determineOutcome(updated.player1Move, updated.player2Move)

                _gameState.update {
                    it.copy(
                        roundResult = outcome,
                        player1Score = if (outcome == RoundResult.PLAYER1_WIN) it.player1Score + 1 else it.player1Score,
                        player2Score = if (outcome == RoundResult.PLAYER2_WIN) it.player2Score + 1 else it.player2Score
                    )
                }

                triggerParticles(outcome, isLocal2P = true)

                delay(2200)

                val stateAfterDelay = _gameState.value
                val targetScore = 3
                if (stateAfterDelay.player1Score >= targetScore || stateAfterDelay.player2Score >= targetScore) {
                    val winner = if (stateAfterDelay.player1Score >= targetScore) "PLAYER 1" else "PLAYER 2"
                    _gameState.update { it.copy(gameOverResult = winner) }

                    // Log local offline local match
                    repository.recordMatch(
                        opponentName = "Player 2 (Local)",
                        opponentType = "Local 2P",
                        playerMove = stateAfterDelay.player1Move.name,
                        opponentMove = stateAfterDelay.player2Move.name,
                        result = if (winner == "PLAYER 1") "WIN" else "LOSS"
                    )
                } else {
                    _gameState.update {
                        it.copy(
                            player1Move = Move.NONE,
                            player2Move = Move.NONE,
                            roundResult = null,
                            isRoundResolving = false
                        )
                    }
                }
            }
        }
    }

    // Simulated Online Matchmaking Flow
    fun startOnlineMatchmaking() {
        if (_onlineState.value is OnlineState.Searching) return

        _screenState.value = ScreenState.PLAY_ONLINE
        resetGame()

        viewModelScope.launch {
            _onlineState.value = OnlineState.Searching("CONNECTING TO GLOBAL NET...")
            delay(1000)
            _onlineState.value = OnlineState.Searching("LOCATING SUITABLE AGENTS...")
            delay(1200)
            _onlineState.value = OnlineState.Searching("PULLING PING RATES (38ms)...")
            delay(1000)

            val fakeOpponent = FAKE_OPPONENTS.random()
            _onlineState.value = OnlineState.Found(fakeOpponent)
            delay(2000)

            _onlineState.value = OnlineState.MatchActive(fakeOpponent)
        }
    }

    fun selectMoveOnline(playerMove: Move) {
        val activeState = _onlineState.value as? OnlineState.MatchActive ?: return
        if (_gameState.value.isRoundResolving) return

        viewModelScope.launch {
            _gameState.update { it.copy(player1Move = playerMove, isRoundResolving = true) }

            // Simulate opponent is thinking
            delay(Random.nextLong(1200, 2000))

            val opponentMove = Move.values().filter { it != Move.NONE }.random()
            val outcome = determineOutcome(playerMove, opponentMove)

            _gameState.update {
                it.copy(
                    player2Move = opponentMove,
                    roundResult = outcome,
                    player1Score = if (outcome == RoundResult.PLAYER1_WIN) it.player1Score + 1 else it.player1Score,
                    player2Score = if (outcome == RoundResult.PLAYER2_WIN) it.player2Score + 1 else it.player2Score
                )
            }

            triggerParticles(outcome, isLocal2P = false)

            delay(2000)

            val currentState = _gameState.value
            val targetScore = 3
            if (currentState.player1Score >= targetScore || currentState.player2Score >= targetScore) {
                val gameWinner = if (currentState.player1Score >= targetScore) "PLAYER" else "OPPONENT"
                val matchResult = if (gameWinner == "PLAYER") "WIN" else "LOSS"

                repository.recordMatch(
                    opponentName = activeState.opponent.username,
                    opponentType = "Simulated Online",
                    playerMove = playerMove.name,
                    opponentMove = opponentMove.name,
                    result = matchResult
                )

                _gameState.update { it.copy(gameOverResult = gameWinner) }
            } else {
                _gameState.update {
                    it.copy(
                        player1Move = Move.NONE,
                        player2Move = Move.NONE,
                        roundResult = null,
                        isRoundResolving = false
                    )
                }
            }
        }
    }

    fun resetGame() {
        _gameState.value = GameState()
        _onlineState.value = OnlineState.Idle
    }

    // Predictive Hard AI algorithm implementing a custom Markov sequence evaluation
    private fun getPredictiveAiMove(): Move {
        val diff = _aiDifficulty.value
        val standardMoves = Move.values().filter { it != Move.NONE }

        if (diff == AiDifficulty.EASY) {
            return standardMoves.random()
        }

        if (diff == AiDifficulty.MEDIUM) {
            // Medium AI: occasionally counter player previous choices (40% rate), otherwise uniform random
            return if (sessionPlayerMoves.isNotEmpty() && Random.nextInt(100) < 40) {
                getWinningCounterMove(sessionPlayerMoves.last())
            } else {
                standardMoves.random()
            }
        }

        // Hard AI: Smart Markov prediction or pattern recognition
        if (sessionPlayerMoves.size < 3) {
            return standardMoves.random()
        }

        // 1. Check last pattern transition
        val lastMove = sessionPlayerMoves.last()
        val secondLast = sessionPlayerMoves[sessionPlayerMoves.size - 2]

        // Find matches in session where player played secondLast followed by lastMove, and catalog what came after
        val counts = mutableMapOf(Move.ROCK to 0, Move.PAPER to 0, Move.SCISSORS to 0)
        for (i in 0 until sessionPlayerMoves.size - 2) {
            if (sessionPlayerMoves[i] == secondLast && sessionPlayerMoves[i + 1] == lastMove) {
                val nextMove = sessionPlayerMoves[i + 2]
                counts[nextMove] = (counts[nextMove] ?: 0) + 1
            }
        }

        val predictedMove = counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key
            ?: sessionPlayerMoves.groupBy { it }.maxByOrNull { it.value.size }?.key // Fallback to overall most used move
            ?: standardMoves.random()

        // Return counter move to beat their predicted move
        return getWinningCounterMove(predictedMove)
    }

    private fun getWinningCounterMove(move: Move): Move {
        return when (move) {
            Move.ROCK -> Move.PAPER
            Move.PAPER -> Move.SCISSORS
            Move.SCISSORS -> Move.ROCK
            Move.NONE -> Move.ROCK
        }
    }

    private fun determineOutcome(p1: Move, p2: Move): RoundResult {
        if (p1 == p2) return RoundResult.TIE
        return when {
            p1 == Move.ROCK && p2 == Move.SCISSORS -> RoundResult.PLAYER1_WIN
            p1 == Move.PAPER && p2 == Move.ROCK -> RoundResult.PLAYER1_WIN
            p1 == Move.SCISSORS && p2 == Move.PAPER -> RoundResult.PLAYER1_WIN
            else -> RoundResult.PLAYER2_WIN
        }
    }

    private fun triggerParticles(outcome: RoundResult, isLocal2P: Boolean) {
        val colorScheme = when (outcome) {
            RoundResult.PLAYER1_WIN -> if (isLocal2P) "MAGENTA" else "CYAN"
            RoundResult.PLAYER2_WIN -> if (isLocal2P) "CYAN" else "HOT_PINK"
            RoundResult.TIE -> "NEON_YELLOW"
        }
        _particleTrigger.value = ParticleTrigger(colorScheme, System.currentTimeMillis())
    }

    private fun getAchievementTitle(id: String): String {
        return when (id) {
            "ACH_FIRST_GAME" -> "First Blood (Combat Initialized)"
            "ACH_VETERAN_WINS" -> "Vanguard (Obtained 5 Arena Wins)"
            "ACH_GLADIATOR" -> "Warlord (Completed 25 Wins)"
            "ACH_HARD_AI_SLAYER" -> "Ghost In The Shell (Slayed Hard AI 5 Times)"
            "ACH_STREAK_3" -> "Combustion (3 Win Streak!)"
            "ACH_STREAK_7" -> "Supernova (7 Win Streak - Unstoppable!)"
            "ACH_LEVEL_5" -> "Elite Agent (Reached level 5)"
            "ACH_VERSATILE" -> "Omnipresent (Used Rock, Paper, Scissors 10x each)"
            else -> "Elite Target Secured"
        }
    }

    companion object {
        private val FAKE_OPPONENTS = listOf(
            FakeOpponent("ShadowViperX", 4),
            FakeOpponent("CyberNinja99", 7),
            FakeOpponent("RetroPanda", 2),
            FakeOpponent("MatrixGhost", 9),
            FakeOpponent("EchoSaber", 5),
            FakeOpponent("SynthSorcerer", 3),
            FakeOpponent("NeonRebel", 6)
        )
    }
}

enum class ScreenState {
    HOME, PLAY_AI, PLAY_LOCAL_2P, PLAY_ONLINE, STATS, PROFILE
}

enum class AiDifficulty(val displayName: String) {
    EASY("Easy Drone"), MEDIUM("Calculated Host"), HARD("Oracle Predictive")
}

enum class Move {
    NONE, ROCK, PAPER, SCISSORS
}

enum class RoundResult {
    PLAYER1_WIN, PLAYER2_WIN, TIE
}

data class GameState(
    val player1Move: Move = Move.NONE,
    val player2Move: Move = Move.NONE,
    val player1Score: Int = 0,
    val player2Score: Int = 0,
    val roundResult: RoundResult? = null,
    val isRoundResolving: Boolean = false,
    val gameOverResult: String? = null // "PLAYER", "AI", "PLAYER 1", "PLAYER 2", "OPPONENT", null
)

sealed interface OnlineState {
    object Idle : OnlineState
    data class Searching(val currentStep: String) : OnlineState
    data class Found(val opponent: FakeOpponent) : OnlineState
    data class MatchActive(val opponent: FakeOpponent) : OnlineState
}

data class FakeOpponent(val username: String, val level: Int)

data class ParticleTrigger(val style: String, val timestamp: Long)
