package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                GameApp()
            }
        }
    }
}

// Particle details for native Canvas blast
data class Particle(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val radius: Float,
    var alpha: Float,
    val decay: Float
)

// Helper to resolve faction color themes
@Composable
fun getFactionColors(faction: String): FactionColors {
    return when (faction) {
        "NEON_GLOW" -> FactionColors(
            primary = Color(0xFF39FF14),   // Toxic / Acid Green
            secondary = Color(0xFF00E5FF), // Cyber Cyan
            background = Color(0xFF050505), // Obsidian slate-black backdrop
            cardBg = Color(0xFF0F172A),     // Sleek slate-900 cards
            glowColor = Color(0xFF39FF14),
            avatarEmoji = "💚"
        )
        "SYNTH_WAVE" -> FactionColors(
            primary = Color(0xFFFF007F),   // Electric Pink
            secondary = Color(0xFF9D00FF), // Retro Purple
            background = Color(0xFF050505), // Obsidian slate-black backdrop
            cardBg = Color(0xFF0F172A),     // Sleek slate-900 cards
            glowColor = Color(0xFFFF007F),
            avatarEmoji = "💜"
        )
        "CYBER_PUNK" -> FactionColors(
            primary = Color(0xFFFFEA00),   // High Power Yellow
            secondary = Color(0xFF00FFCC), // Radiant Mint
            background = Color(0xFF050505), // Obsidian slate-black backdrop
            cardBg = Color(0xFF0F172A),     // Sleek slate-900 cards
            glowColor = Color(0xFFFFEA00),
            avatarEmoji = "💛"
        )
        "PLASMA_CANNON" -> FactionColors(
            primary = Color(0xFFFF3300),   // Solar Flare Red
            secondary = Color(0xFFFF9900), // Lava Gold
            background = Color(0xFF050505), // Obsidian slate-black backdrop
            cardBg = Color(0xFF0F172A),     // Sleek slate-900 cards
            glowColor = Color(0xFFFF3300),
            avatarEmoji = "❤️"
        )
        else -> FactionColors(
            primary = Color(0xFF00FFCC),   // Core Default Cyan
            secondary = Color(0xFF0066FF),
            background = Color(0xFF050505), // Obsidian slate-black backdrop
            cardBg = Color(0xFF0F172A),     // Sleek slate-900 cards
            glowColor = Color(0xFF00FFCC),
            avatarEmoji = "💙"
        )
    }
}

data class FactionColors(
    val primary: Color,
    val secondary: Color,
    val background: Color,
    val cardBg: Color,
    val glowColor: Color,
    val avatarEmoji: String
)

// Custom Modifier extension to add consistent responsive neon board structures
fun Modifier.neonBorder(
    color: Color,
    shape: CornerBasedShape,
    borderWidth: Dp = 1.dp
): Modifier = this
    .border(borderWidth + 1.dp, color.copy(alpha = 0.15f), shape)
    .border(borderWidth, color, shape)

@Composable
fun GameApp(viewModel: GameViewModel = viewModel()) {
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val playerProfile by viewModel.playerProfile.collectAsStateWithLifecycle()
    val recentLogs by viewModel.recentLogs.collectAsStateWithLifecycle()
    val unlockedAchievement by viewModel.unlockedAchievement.collectAsStateWithLifecycle()
    val particleTrigger by viewModel.particleTrigger.collectAsStateWithLifecycle()

    val colors = getFactionColors(playerProfile.faction)
    val particles = remember { mutableStateListOf<Particle>() }
    val haptic = LocalHapticFeedback.current

    // Background matrix mesh animation loop
    val transition = rememberInfiniteTransition(label = "grid")
    val gridOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "grid"
    )

    // Android device back button controller
    BackHandler(enabled = screenState != ScreenState.HOME) {
        viewModel.navigateTo(ScreenState.HOME)
    }

    // Interactive Particle Blast Engine
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    LaunchedEffect(particleTrigger) {
        if (particleTrigger == null || canvasSize.width == 0f) return@LaunchedEffect

        particles.clear()
        val baseColor = when (particleTrigger!!.style) {
            "CYAN" -> Color(0xFF00E5FF)
            "MAGENTA" -> Color(0xFFFF007F)
            "HOT_PINK" -> Color(0xFFFF1493)
            "NEON_YELLOW" -> Color(0xFFFFEA00)
            else -> colors.primary
        }

        // Emit 45 neon sparks radiating outward with varying trajectories
        repeat(45) {
            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
            val speed = Random.nextFloat() * 12f + 4f
            particles.add(
                Particle(
                    x = canvasSize.width / 2f,
                    y = canvasSize.height / 2f,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    color = baseColor,
                    radius = Random.nextFloat() * 10f + 3f,
                    alpha = 1.0f,
                    decay = Random.nextFloat() * 0.02f + 0.015f
                )
            )
        }

        while (particles.isNotEmpty()) {
            withFrameMillis {
                val iterator = particles.iterator()
                while (iterator.hasNext()) {
                    val p = iterator.next()
                    p.x += p.vx
                    p.y += p.vy
                    p.alpha -= p.decay
                    if (p.alpha <= 0f) {
                        iterator.remove()
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Retro Cyberpunk background digital grid drawing
        Canvas(modifier = Modifier.fillMaxSize().alpha(0.12f)) {
            canvasSize = size
            val spacing = 80f
            // Horizontal lines
            var y = gridOffset % spacing
            while (y < size.height) {
                drawLine(
                    color = colors.primary,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
                y += spacing
            }
            // Vertical lines
            var x = gridOffset % spacing
            while (x < size.width) {
                drawLine(
                    color = colors.primary,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
                x += spacing
            }
        }

        // Main game viewport scaffolding
        Scaffold(
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (screenState == ScreenState.HOME || screenState == ScreenState.STATS || screenState == ScreenState.PROFILE) {
                    BottomNavigationBar(
                        currentScreen = screenState,
                        onNavigate = { viewModel.navigateTo(it) },
                        colors = colors
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Render corresponding screen panel
                when (screenState) {
                    ScreenState.HOME -> HomeScreen(viewModel, playerProfile, colors)
                    ScreenState.PLAY_AI -> PlayAiScreen(viewModel, colors, haptic)
                    ScreenState.PLAY_LOCAL_2P -> PlayLocalScreen(viewModel, colors, haptic)
                    ScreenState.PLAY_ONLINE -> PlayOnlineScreen(viewModel, colors, haptic)
                    ScreenState.STATS -> StatsScreen(viewModel, playerProfile, recentLogs, colors)
                    ScreenState.PROFILE -> ProfileScreen(viewModel, playerProfile, colors)
                }

                // Achievement Unlock Popup Toast Overlay
                AnimatedVisibility(
                    visible = unlockedAchievement != null,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                ) {
                    unlockedAchievement?.let { title ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xEC0B120B)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .neonBorder(colors.primary, RoundedCornerShape(12.dp), borderWidth = 1.5.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(colors.primary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                        .border(1.dp, colors.primary, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Achievement Unlocked",
                                        tint = colors.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "ACHIEVEMENT SECURED!",
                                        fontSize = 11.sp,
                                        color = colors.primary,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp
                                    )
                                    Text(
                                        text = title,
                                        fontSize = 14.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Render dynamic particles on top layer
                Canvas(modifier = Modifier.fillMaxSize()) {
                    particles.forEach { p ->
                        drawCircle(
                            color = p.color.copy(alpha = p.alpha.coerceIn(0f, 1f)),
                            radius = p.radius,
                            center = Offset(p.x, p.y)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    currentScreen: ScreenState,
    onNavigate: (ScreenState) -> Unit,
    colors: FactionColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Color(0xFF020617)) // Deep slate-950 bottom nav bg
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)))
            .windowInsetsPadding(WindowInsets.navigationBars) // Safe area padding for bottom system bar
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tabs = listOf(
            Triple(ScreenState.HOME, Icons.Default.PlayArrow, "PLAY"),
            Triple(ScreenState.STATS, Icons.Default.List, "STATS"),
            Triple(ScreenState.PROFILE, Icons.Default.Person, "PROFILE")
        )

        tabs.forEach { (screen, icon, label) ->
            val active = currentScreen == screen
            val activeColor = colors.primary
            val inactiveColor = Color(0xFF64748B) // Slate-500

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onNavigate(screen) }
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (active) activeColor else inactiveColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    fontSize = 9.sp,
                    color = if (active) activeColor else inactiveColor,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// ----------------------------------------------------
// 1. HOME SCREEN COMPOSABLE
// ----------------------------------------------------
@Composable
fun HomeScreen(
    viewModel: GameViewModel,
    profile: PlayerProfile,
    colors: FactionColors
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Immersive Dual-Ring Avatar with Level Badge Overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.cardBg.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(24.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(56.dp)
            ) {
                // Gradient Ring
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.sweepGradient(listOf(colors.primary, colors.secondary, colors.primary)),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0F172A), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = colors.avatarEmoji, fontSize = 24.sp)
                    }
                }
                
                // Absolute positioned Level Badge at bottom-right
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                        .background(colors.primary, RoundedCornerShape(10.dp))
                        .border(1.5.dp, Color(0xFF050505), RoundedCornerShape(10.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${profile.level}",
                        color = Color(0xFF050505),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.username,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                // Beautiful XP Progress Bar with Glow
                val xpNeeded = profile.xpToNextLevel()
                val xpPercent = (profile.xp.toFloat() / xpNeeded).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(xpPercent)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(listOf(colors.primary, colors.secondary)),
                                RoundedCornerShape(3.dp)
                            )
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "FACTION: ${profile.faction.replace("_", " ")}",
                        fontSize = 9.sp,
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${profile.xp}/$xpNeeded XP",
                        fontSize = 9.sp,
                        color = colors.primary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Large stylized terminal neon game logo
        Text(
            text = "ROCK PAPER SCISSORS",
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = Color.White,
            textAlign = TextAlign.Center,
            letterSpacing = 2.sp
        )
        Text(
            text = "NEON PROTOCOL ARENA",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = colors.primary,
            textAlign = TextAlign.Center,
            letterSpacing = 6.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
        )

        // Immersive Hero Matchmaking Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    Brush.linearGradient(listOf(colors.cardBg, colors.cardBg.copy(alpha = 0.8f))),
                    RoundedCornerShape(24.dp)
                )
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(24.dp))
                .clickable { viewModel.startOnlineMatchmaking() }
        ) {
            // Subtle glowing background watermark icon
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = colors.primary.copy(alpha = 0.06f),
                    modifier = Modifier.size(112.dp).rotate(15f)
                )
            }

            // Foreground content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                // Online pulsing status tag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF4ADE80).copy(alpha = pulseAlpha), RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "1,248 ONLINE CORES",
                        fontSize = 9.sp,
                        color = Color(0xFF4ADE80),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = "RANKED MULTIPLAYER",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = (-0.5).sp
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Glowing Find Game Button
                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .background(colors.primary, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "FIND ARENA",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Immersive Selection Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // VS AI Cell
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(130.dp)
                    .background(colors.cardBg.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(24.dp))
                    .clickable { viewModel.navigateTo(ScreenState.PLAY_AI) }
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(colors.primary.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build, // Representing AI core/bot
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "AI ARENA",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "EASY • MED • HARD",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // Local splitscreen cell
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(130.dp)
                    .background(colors.cardBg.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(24.dp))
                    .clickable { viewModel.navigateTo(ScreenState.PLAY_LOCAL_2P) }
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(colors.secondary.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person, // Representing local duo splitscreen
                            contentDescription = null,
                            tint = colors.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "LOCAL DUEL",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "SPLITSCREEN PLAY",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Immersive Quick Stats Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.cardBg.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(16.dp))
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            val totalGames = profile.wins + profile.losses + profile.ties
            val winrate = if (totalGames == 0) 0 else (profile.wins * 100) / totalGames
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "WINS",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${profile.wins}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                    fontFamily = FontFamily.Monospace
                )
            }
            
            // Vertical Separator Line
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .width(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "LOSSES",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${profile.losses}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.secondary,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Vertical Separator Line
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .width(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "WIN RATE",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "$winrate%",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Premium Gold Achievement Snippet Box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFFEAB308).copy(alpha = 0.08f), Color.Transparent)
                    ),
                    RoundedCornerShape(16.dp)
                )
                .border(
                    BorderStroke(1.dp, Color(0xFFEAB308).copy(alpha = 0.15f)),
                    RoundedCornerShape(16.dp)
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFFEAB308).copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFEAB308),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "UNSTOPPABLE FORCE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFEF08A),
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Secure consecutive arena victories to dominate servers.",
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ----------------------------------------------------
// 2. VS AI MODE VIEW
// ----------------------------------------------------
@Composable
fun PlayAiScreen(
    viewModel: GameViewModel,
    colors: FactionColors,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val difficulty by viewModel.aiDifficulty.collectAsStateWithLifecycle()
    val gameState by viewModel.gameState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mode Header & Difficulty Selector
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigateTo(ScreenState.HOME) }) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "CAMPAIGN ARENA",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.primary
                )
                Text(
                    text = "VS AEGIS PREDICTIVE AI",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Difficulty selection tab row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AiDifficulty.values().forEach { diff ->
                val active = difficulty == diff
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) colors.secondary else Color.Transparent)
                        .clickable { viewModel.setAiDifficulty(diff) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = diff.name,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (active) Color.Black else Color.Gray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Scoreboard display
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.cardBg.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .neonBorder(colors.primary.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "OPERATIVE", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                    Text(text = "${gameState.player1Score}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = colors.primary, fontFamily = FontFamily.Monospace)
                }
                Text(
                    text = "VS",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.DarkGray
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = difficulty.displayName.uppercase(), fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                    Text(text = "${gameState.player2Score}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = colors.secondary, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // Live combat sandbox area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Round state logs
                val resolverText = when {
                    gameState.gameOverResult != null -> {
                        if (gameState.gameOverResult == "PLAYER") "ARENA CONQUERED!" else "CORE SHUTDOWN"
                    }
                    gameState.isRoundResolving -> {
                        when (gameState.roundResult) {
                            RoundResult.PLAYER1_WIN -> "ROUND WON • DESTRUCTIVE BLOW!"
                            RoundResult.PLAYER2_WIN -> "ROUND LOST • SHIELD COLLAPSE!"
                            RoundResult.TIE -> "ROUND TIE • PROTOCOL BREACH"
                            null -> "COMPILING OUTCOMES..."
                        }
                    }
                    else -> "WAITING FOR MOVE INPUT..."
                }

                val resolveColor = when (gameState.roundResult) {
                    RoundResult.PLAYER1_WIN -> colors.primary
                    RoundResult.PLAYER2_WIN -> colors.secondary
                    RoundResult.TIE -> Color(0xFFFFEA00)
                    null -> Color.LightGray
                }

                Text(
                    text = resolverText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = resolveColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Hexagonal choices render panels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChoiceShield(
                        move = gameState.player1Move,
                        label = "YOU",
                        color = colors.primary
                    )
                    Spacer(modifier = Modifier.width(32.dp))
                    ChoiceShield(
                        move = if (gameState.isRoundResolving) gameState.player2Move else Move.NONE,
                        label = "AI CPU",
                        color = colors.secondary
                    )
                }
            }
        }

        // Input Choices Bar
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "TRANSMIT TARGET COORDINATE",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                listOf(
                    Pair(Move.ROCK, "✊"),
                    Pair(Move.PAPER, "✋"),
                    Pair(Move.SCISSORS, "✌️")
                ).forEach { (move, emoji) ->
                    val selected = gameState.player1Move == move
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) colors.primary.copy(alpha = 0.2f) else colors.cardBg.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(84.dp)
                            .neonBorder(
                                if (selected) colors.primary else Color.Gray.copy(alpha = 0.3f),
                                RoundedCornerShape(16.dp)
                            )
                            .clickable(enabled = !gameState.isRoundResolving && gameState.gameOverResult == null) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.selectMoveVsAi(move)
                            },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = emoji, fontSize = 32.sp)
                            Text(
                                text = move.name,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (selected) colors.primary else Color.LightGray
                            )
                        }
                    }
                }
            }
        }

        // GameOver overlays
        if (gameState.gameOverResult != null) {
            Dialog(onDismissRequest = { viewModel.resetGame() }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFB0A0B0E)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .neonBorder(colors.primary, RoundedCornerShape(24.dp), borderWidth = 2.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val playerWon = gameState.gameOverResult == "PLAYER"
                        Icon(
                            imageVector = if (playerWon) Icons.Default.Star else Icons.Default.Warning,
                            contentDescription = "Game Over Status",
                            tint = if (playerWon) colors.primary else colors.secondary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (playerWon) "SYSTEM VICTORIOUS" else "ARENA SYSTEM FAIL",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (playerWon) {
                                "Predictive networks fully simulated. Data logs analyzed. Earned victory reward of +60XP."
                            } else {
                                "Predictive core outsmarted your coordinates. Level telemetry saved. Earned +10XP participation."
                            },
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = { viewModel.navigateTo(ScreenState.HOME) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                border = BorderStroke(1.dp, Color.Gray),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text("EXIT", color = Color.White, fontFamily = FontFamily.Monospace)
                            }
                            Button(
                                onClick = { viewModel.resetGame() },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text("RETRY", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChoiceShield(move: Move, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .neonBorder(
                    if (move != Move.NONE) color else Color.DarkGray,
                    RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            val emoji = when (move) {
                Move.ROCK -> "✊"
                Move.PAPER -> "✋"
                Move.SCISSORS -> "✌"
                Move.NONE -> "❓"
            }
            Text(text = emoji, fontSize = 42.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.Gray
        )
    }
}

// ----------------------------------------------------
// 3. LOCAL MULTIPLAYER VIEW (SIMULTANEOUS DETECT)
// ----------------------------------------------------
@Composable
fun PlayLocalScreen(
    viewModel: GameViewModel,
    colors: FactionColors,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val gameState by viewModel.gameState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Player 2 Core Section (Rotated 180 Degrees facing opposite side)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .rotate(180f)
                .background(colors.secondary.copy(alpha = 0.05f))
        ) {
            LocalPlayerDashboard(
                playerIndex = 2,
                gameState = gameState,
                colors = colors,
                onMoveSelected = { viewModel.registerLocalMove(2, it) },
                haptic = haptic,
                onBackClicked = { viewModel.navigateTo(ScreenState.HOME) }
            )
        }

        // Horizontal Separation Tech Border Line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(colors.primary, colors.secondary, colors.primary)
                    )
                )
        )

        // Player 1 Core Section (Facing standard perspective)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(colors.primary.copy(alpha = 0.05f))
        ) {
            LocalPlayerDashboard(
                playerIndex = 1,
                gameState = gameState,
                colors = colors,
                onMoveSelected = { viewModel.registerLocalMove(1, it) },
                haptic = haptic,
                onBackClicked = { viewModel.navigateTo(ScreenState.HOME) }
            )
        }

        // Local PvP Game Over Dialog
        if (gameState.gameOverResult != null) {
            Dialog(onDismissRequest = { viewModel.resetGame() }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFC0A0D0E)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .neonBorder(colors.secondary, RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Gladiator Winner",
                            tint = colors.secondary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "${gameState.gameOverResult} DOMINATES",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Combat history registered to offline archives.",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = { viewModel.navigateTo(ScreenState.HOME) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                border = BorderStroke(1.dp, Color.Gray),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text("EXIT", color = Color.White, fontFamily = FontFamily.Monospace)
                            }
                            Button(
                                onClick = { viewModel.resetGame() },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.secondary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text("REMATCH", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocalPlayerDashboard(
    playerIndex: Int,
    gameState: GameState,
    colors: FactionColors,
    onMoveSelected: (Move) -> Unit,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onBackClicked: () -> Unit
) {
    val playerMove = if (playerIndex == 1) gameState.player1Move else gameState.player2Move
    val score = if (playerIndex == 1) gameState.player1Score else gameState.player2Score
    val opponentMove = if (playerIndex == 1) gameState.player2Move else gameState.player1Move
    val activeColor = if (playerIndex == 1) colors.primary else colors.secondary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Player header details
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClicked) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
            }
            Text(
                text = "PLAYER $playerIndex SCORE: $score",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )
            Box(modifier = Modifier.size(40.dp)) // empty space balancing
        }

        // Active State Render (Secrecy Check)
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (gameState.isRoundResolving) {
                    // Revealed Outcomes
                    val roundOutcomeText = when (gameState.roundResult) {
                        RoundResult.TIE -> "ROUND MUTUAL COLLISION"
                        RoundResult.PLAYER1_WIN -> if (playerIndex == 1) "TARGET DESTRUCTED (+1)" else "CRITICAL BREACHED"
                        RoundResult.PLAYER2_WIN -> if (playerIndex == 2) "TARGET DESTRUCTED (+1)" else "CRITICAL BREACHED"
                        null -> "COMPUTING TELEMETRY..."
                    }

                    Text(
                        text = roundOutcomeText,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (roundOutcomeText.contains("(+1)")) colors.primary else Color.LightGray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChoiceShield(move = playerMove, label = "YOU", color = activeColor)
                        Text(text = "VS", fontSize = 12.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                        ChoiceShield(move = opponentMove, label = "OPPONENT", color = Color.Gray)
                    }
                } else {
                    if (playerMove != Move.NONE) {
                        // Secret lock screen representation
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .background(activeColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .border(1.dp, activeColor, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked In",
                                tint = activeColor,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Text(
                            text = "MOVE SIGNATURE LOCKED",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = activeColor,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else {
                        Text(
                            text = "SELECT REGISTER COORDINATE",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }
        }

        // Tappable Controls Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf(
                Pair(Move.ROCK, "✊"),
                Pair(Move.PAPER, "✋"),
                Pair(Move.SCISSORS, "✌️")
            ).forEach { (move, emoji) ->
                val locked = playerMove != Move.NONE
                val selected = playerMove == move

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) activeColor.copy(alpha = 0.2f) else colors.cardBg.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .neonBorder(
                            if (selected) activeColor else Color.Gray.copy(alpha = 0.2f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable(enabled = !locked && !gameState.isRoundResolving) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onMoveSelected(move)
                        },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 28.sp)
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// 4. SIMULATED NET MULTIPLAYER VIEW
// ----------------------------------------------------
@Composable
fun PlayOnlineScreen(
    viewModel: GameViewModel,
    colors: FactionColors,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val onlineState by viewModel.onlineState.collectAsStateWithLifecycle()
    val gameState by viewModel.gameState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mode header bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigateTo(ScreenState.HOME) }) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back & Disconnect", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "COMMUNICATION CHANNELS",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.primary
                )
                Text(
                    text = "GLOBAL PROTOCOL STAGES",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (val state = onlineState) {
            is OnlineState.Searching -> {
                // Connecting loading screen layout
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val infiniteRotation = rememberInfiniteTransition(label = "ring")
                        val angle by infiniteRotation.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "ring"
                        )

                        Canvas(modifier = Modifier.size(96.dp)) {
                            drawArc(
                                color = colors.primary,
                                startAngle = angle,
                                sweepAngle = 280f,
                                useCenter = false,
                                style = Stroke(width = 4.dp.toPx())
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            text = state.currentStep.uppercase(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = colors.primary,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "STABILIZING RETRO-LINK...",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            is OnlineState.Found -> {
                // Success connection splash screen
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Opponent Located",
                            tint = colors.secondary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "GLOBAL TARGET CONFIRMED",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.opponent.username,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "OPPONENT LEVEL: ${state.opponent.level}",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.secondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            is OnlineState.MatchActive -> {
                // Online simulation arena UI
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.cardBg.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .neonBorder(colors.secondary.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "YOU", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                            Text(text = "${gameState.player1Score}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = colors.primary, fontFamily = FontFamily.Monospace)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "LATENCY: 42ms",
                                fontSize = 10.sp,
                                color = colors.primary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "ROUND",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.DarkGray
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = state.opponent.username.uppercase(), fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(text = "${gameState.player2Score}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = colors.secondary, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                // Battle state graphics
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val combatLog = when {
                            gameState.isRoundResolving && gameState.player2Move == Move.NONE -> "${state.opponent.username} IS COORDINATING..."
                            gameState.isRoundResolving -> {
                                when (gameState.roundResult) {
                                    RoundResult.PLAYER1_WIN -> "SUCCESSFUL DESTRUCTIVE IMPACT!"
                                    RoundResult.PLAYER2_WIN -> "RETALIATIVE GRID HIT!"
                                    RoundResult.TIE -> "SIGNAL OUTCOME CONVERGED"
                                    null -> "PARSING PACKETS..."
                                }
                            }
                            else -> "CHOOSE MATRIX MOVE POSITION"
                        }

                        Text(
                            text = combatLog,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (combatLog.contains("SUCCESSFUL")) colors.primary else Color.LightGray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChoiceShield(move = gameState.player1Move, label = "YOU", color = colors.primary)
                            Spacer(modifier = Modifier.width(32.dp))
                            ChoiceShield(
                                move = if (gameState.isRoundResolving && gameState.player2Move != Move.NONE) gameState.player2Move else Move.NONE,
                                label = state.opponent.username,
                                color = colors.secondary
                            )
                        }
                    }
                }

                // Input action coordinate triggers
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        listOf(
                            Pair(Move.ROCK, "✊"),
                            Pair(Move.PAPER, "✋"),
                            Pair(Move.SCISSORS, "✌️")
                        ).forEach { (move, emoji) ->
                            val selected = gameState.player1Move == move

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) colors.primary.copy(alpha = 0.2f) else colors.cardBg.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(84.dp)
                                    .neonBorder(
                                        if (selected) colors.primary else Color.Gray.copy(alpha = 0.3f),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable(enabled = !gameState.isRoundResolving && gameState.gameOverResult == null) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.selectMoveOnline(move)
                                    },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(text = emoji, fontSize = 32.sp)
                                    Text(
                                        text = move.name,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (selected) colors.primary else Color.LightGray
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OnlineState.Idle -> {
                // Safe Fallback empty state
            }
        }

        // Online simulation match complete overlays
        if (gameState.gameOverResult != null && onlineState is OnlineState.MatchActive) {
            val oppName = (onlineState as OnlineState.MatchActive).opponent.username
            Dialog(onDismissRequest = { viewModel.resetGame() }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFD070C12)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .neonBorder(colors.primary, RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val playerWon = gameState.gameOverResult == "PLAYER"
                        Icon(
                            imageVector = if (playerWon) Icons.Default.Star else Icons.Default.Warning,
                            contentDescription = "Simulated Online Result Status",
                            tint = if (playerWon) colors.primary else colors.secondary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (playerWon) "MULTIPLAYER SUCCESS" else "TACTICAL COMBAT LOSS",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (playerWon) {
                                "Coordinated strike completed. Opponent $oppName has disconnected. Registered +60XP."
                            } else {
                                "Coordinated strike failed. Opponent $oppName completed synchronization first. Registered +10XP."
                            },
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = { viewModel.navigateTo(ScreenState.HOME) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                border = BorderStroke(1.dp, Color.Gray),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text("EXIT", color = Color.White, fontFamily = FontFamily.Monospace)
                            }
                            Button(
                                onClick = { viewModel.startOnlineMatchmaking() },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text("RE-QUEUE", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// 5. STATISTICS DEBRIEF VIEW & CHARTS
// ----------------------------------------------------
@Composable
fun StatsScreen(
    viewModel: GameViewModel,
    profile: PlayerProfile,
    logs: List<GameLog>,
    colors: FactionColors
) {
    val unlockedAchievements by viewModel.unlockedAchievementsList.collectAsStateWithLifecycle()
    var displayTab by remember { mutableStateOf(1) } // 1: Stats, 2: Archives/Logs, 3: Badges/Achievements

    // Standard local confirmation prompt triggers
    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Debrief title header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigateTo(ScreenState.HOME) }) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "LOCAL TELEMETRY ARCHIVE",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.primary
                )
                Text(
                    text = "BATTLE INTEGRITY RECORDS",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Subtabs selection row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                Triple(1, "ANALYTICS", Icons.Default.List),
                Triple(2, "BATTLE LOGS", Icons.Default.Refresh),
                Triple(3, "ARENA BADGES", Icons.Default.Star)
            ).forEach { (id, label, icon) ->
                val active = displayTab == id
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) colors.primary else Color.Transparent)
                        .clickable { displayTab = id }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = icon, contentDescription = label, tint = if (active) Color.Black else Color.Gray, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = label,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (active) Color.Black else Color.Gray
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        when (displayTab) {
            1 -> {
                // Interactive analytical charts scroll lists
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    val totalGames = profile.wins + profile.losses + profile.ties
                    val winrate = if (totalGames == 0) 0f else (profile.wins.toFloat() / totalGames)

                    // Large circular native compose Canvas chart for Winrate representation
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val winrateTransition = remember { Animatable(0f) }
                            LaunchedEffect(winrate) {
                                winrateTransition.animateTo(
                                    winrate,
                                    animationSpec = tween(1200, easing = FastOutSlowInEasing)
                                )
                            }
                            Canvas(modifier = Modifier.size(140.dp)) {
                                drawCircle(
                                    color = Color.DarkGray.copy(alpha = 0.3f),
                                    style = Stroke(width = 12.dp.toPx())
                                )
                                drawArc(
                                    color = colors.primary,
                                    startAngle = -90f,
                                    sweepAngle = winrateTransition.value * 360f,
                                    useCenter = false,
                                    style = Stroke(width = 12.dp.toPx())
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${(winrate * 100).toInt()}%",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White
                                )
                                Text(
                                    text = "EFFICIENCY",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = colors.primary,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(28.dp))

                        Column {
                            StatMiniBadge(value = "${profile.wins}", label = "ARENA WINS", color = colors.primary)
                            StatMiniBadge(value = "${profile.losses}", label = "SYS COLLISIONS", color = colors.secondary)
                            StatMiniBadge(value = "${profile.ties}", label = "TIES REGULAR", color = Color(0xFFFFEA00))
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Move choice frequency distribution bar graphs
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.cardBg.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .neonBorder(colors.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "MOVE FREQUENCY TELEMETRY",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colors.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            val rockTotal = profile.rockCount
                            val paperTotal = profile.paperCount
                            val scissorsTotal = profile.scissorsCount
                            val grandTotalMoves = (rockTotal + paperTotal + scissorsTotal).coerceAtLeast(1)

                            ChoiceFrequencyRow(
                                glyph = "✊",
                                label = "ROCK COORD",
                                count = rockTotal,
                                ratio = rockTotal.toFloat() / grandTotalMoves,
                                color = colors.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            ChoiceFrequencyRow(
                                glyph = "✋",
                                label = "PAPER COORD",
                                count = paperTotal,
                                ratio = paperTotal.toFloat() / grandTotalMoves,
                                color = colors.secondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            ChoiceFrequencyRow(
                                glyph = "✌️",
                                label = "SCISSORS COORD",
                                count = scissorsTotal,
                                ratio = scissorsTotal.toFloat() / grandTotalMoves,
                                color = Color(0xFFFFEA00)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Records Streaks statistics card
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.cardBg.copy(alpha = 0.3f)),
                            modifier = Modifier.weight(1f).neonBorder(Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("MAX STREAK", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                Text("${profile.maxStreak} wins", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.primary, fontFamily = FontFamily.Monospace)
                            }
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.cardBg.copy(alpha = 0.3f)),
                            modifier = Modifier.weight(1f).neonBorder(Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("HARD AI WINS", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                Text("${profile.aiHardWins} times", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.secondary, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Reset stats button trigger
                    Button(
                        onClick = { showResetDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FF0055)),
                        border = BorderStroke(1.dp, Color(0xFFFF0055)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(
                            text = "PURGE DATABASE TELEMETRY",
                            color = Color(0xFFFF3377),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            2 -> {
                // Battle logs listing view
                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = "Empty", tint = Color.DarkGray, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("BATTLE LOGS DESOLATE", fontSize = 12.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        logs.forEach { log ->
                            val outcomeColor = when (log.result) {
                                "WIN" -> colors.primary
                                "LOSS" -> colors.secondary
                                "TIE" -> Color(0xFFFFEA00)
                                else -> Color.Gray
                            }

                            val pEmoji = when (log.playerMove) {
                                "ROCK" -> "✊"
                                "PAPER" -> "✋"
                                "SCISSORS" -> "✌️"
                                else -> "❓"
                            }
                            val oEmoji = when (log.opponentMove) {
                                "ROCK" -> "✊"
                                "PAPER" -> "✋"
                                "SCISSORS" -> "✌️"
                                else -> "❓"
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = colors.cardBg.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .neonBorder(outcomeColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = log.opponentName.uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "CATEGORY: ${log.opponentType}",
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color.Gray
                                        )
                                    }

                                    // Choices representations
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(pEmoji, fontSize = 20.sp)
                                        Text(
                                            text = " vs ",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color.DarkGray
                                        )
                                        Text(oEmoji, fontSize = 20.sp)
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    // Dynamic Outcome Badge
                                    Box(
                                        modifier = Modifier
                                            .background(outcomeColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                            .border(1.dp, outcomeColor, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = log.result,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = outcomeColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            3 -> {
                // Accomplishments/Achievements Badge cards
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val list = getFullAchievementsList()
                    list.forEach { ach ->
                        val unlocked = unlockedAchievements.contains(ach.id)
                        val frameColor = if (unlocked) colors.primary else Color.DarkGray

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (unlocked) colors.cardBg.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .neonBorder(frameColor.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                .alpha(if (unlocked) 1.0f else 0.4f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(frameColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                        .border(1.dp, frameColor, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = ach.icon,
                                        contentDescription = ach.title,
                                        tint = frameColor
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = ach.title.uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = ach.description,
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Double assurance purge dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = Color(0xFF0F0B0C),
            title = {
                Text(
                    text = "PURGE LOG PROTOCOL?",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "This action definitely clears all local battle summaries, levels, stats and archives. Level telemetry resets to 1.",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetStats()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0055))
                ) {
                    Text("YES, PURGE ALL", color = Color.White, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showResetDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                ) {
                    Text("ABORT", color = Color.Gray, fontFamily = FontFamily.Monospace)
                }
            }
        )
    }
}

@Composable
fun StatMiniBadge(value: String, label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label: $value",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.LightGray
        )
    }
}

@Composable
fun ChoiceFrequencyRow(glyph: String, label: String, count: Int, ratio: Float, color: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = glyph, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = label, fontSize = 11.sp, color = Color.LightGray, fontFamily = FontFamily.Monospace)
            }
            Text(text = "$count times (${(ratio * 100).toInt()}%)", fontSize = 10.sp, color = color, fontFamily = FontFamily.Monospace)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = ratio,
            color = color,
            trackColor = Color.DarkGray.copy(alpha = 0.2f),
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
        )
    }
}

data class LocalAchievementInfo(
    val id: String,
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

fun getFullAchievementsList(): List<LocalAchievementInfo> {
    return listOf(
        LocalAchievementInfo("ACH_FIRST_GAME", "First Blood", "Execute combat registers once against CPU or local PvP.", Icons.Default.Settings),
        LocalAchievementInfo("ACH_VETERAN_WINS", "Arena Vanguard", "Clinch 5 victories in total battles.", Icons.Default.Notifications),
        LocalAchievementInfo("ACH_GLADIATOR", "Gladiator Warlord", "Obtain 25 victories in matches.", Icons.Default.Star),
        LocalAchievementInfo("ACH_HARD_AI_SLAYER", "Oracle Inceptor", "Defeat Hard difficulty CPU AI 5 times.", Icons.Default.Build),
        LocalAchievementInfo("ACH_STREAK_3", "Spark Combustion", "Secure a solid 3 round win streak.", Icons.Default.Favorite),
        LocalAchievementInfo("ACH_STREAK_7", "Thermal Supernova", "Secure an unbeatable 7 round win streak.", Icons.Default.Star),
        LocalAchievementInfo("ACH_LEVEL_5", "Elite Agent", "Accumulate coordinates to reach Level 5.", Icons.Default.ThumbUp),
        LocalAchievementInfo("ACH_VERSATILE", "Omnipresent Matrix", "Throw Rock, Paper, and Scissors at least 10 times each.", Icons.Default.Settings)
    )
}

// ----------------------------------------------------
// 6. OPERATIVE PROFILE VIEW (SETTINGS / CUSTOMIZE)
// ----------------------------------------------------
@Composable
fun ProfileScreen(
    viewModel: GameViewModel,
    profile: PlayerProfile,
    colors: FactionColors
) {
    var usernameField by remember { mutableStateOf(profile.username) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Debrief title header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigateTo(ScreenState.HOME) }) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "AGENT REGISTRY SYSTEMS",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.primary
                )
                Text(
                    text = "OPERATIVE ID CUSTOMIZATION",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = colors.cardBg.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .neonBorder(colors.primary.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "REGISTERED CALLSIGN",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Callsign edit box
                OutlinedTextField(
                    value = usernameField,
                    onValueChange = { if (it.length <= 15) usernameField = it },
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedLabelColor = colors.primary,
                        unfocusedLabelColor = Color.Gray
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (usernameField.isNotBlank()) {
                                viewModel.updateProfile(usernameField, profile.faction)
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(text = "CyberAgent", color = Color.DarkGray, fontFamily = FontFamily.Monospace) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "MAX 15 CHARACTERS", fontSize = 9.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)

                    Button(
                        onClick = {
                            if (usernameField.isNotBlank()) {
                                viewModel.updateProfile(usernameField, profile.faction)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("SAVE NAME", color = Color.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Faction neon styling selectors
        Text(
            text = "CHOOSE COGNITIVE FACTION CODES",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.LightGray,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        val factions = listOf(
            Triple("NEON_GLOW", "NEON TRANSMIT", getFactionColors("NEON_GLOW")),
            Triple("SYNTH_WAVE", "VAPOR SYNTH", getFactionColors("SYNTH_WAVE")),
            Triple("CYBER_PUNK", "CYBER MATRIX", getFactionColors("CYBER_PUNK")),
            Triple("PLASMA_CANNON", "熔岩 PLASMA", getFactionColors("PLASMA_CANNON"))
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            factions.forEach { (factId, factName, factColors) ->
                val active = profile.faction == factId
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (active) factColors.cardBg else Color.Black.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .neonBorder(
                            if (active) factColors.primary else Color.DarkGray.copy(alpha = 0.5f),
                            RoundedCornerShape(16.dp),
                            borderWidth = if (active) 1.5.dp else 1.dp
                        )
                        .clickable {
                            viewModel.updateProfile(profile.username, factId)
                        },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(factColors.primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .border(1.dp, factColors.primary, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = factColors.avatarEmoji, fontSize = 20.sp)
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = factName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            )
                            Row(modifier = Modifier.padding(top = 4.dp)) {
                                Box(modifier = Modifier.size(24.dp, 8.dp).background(factColors.primary, RoundedCornerShape(2.dp)))
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(modifier = Modifier.size(24.dp, 8.dp).background(factColors.secondary, RoundedCornerShape(2.dp)))
                            }
                        }

                        // Checked indicator
                        if (active) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(colors.primary, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Active Theme",
                                    tint = Color.Black,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
