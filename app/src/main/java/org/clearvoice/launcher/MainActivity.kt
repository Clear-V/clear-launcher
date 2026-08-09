package org.clearvoice.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.delay
import java.util.Calendar

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            ClearTheme.loadFromStorage(context)
            val themeColors by remember { derivedStateOf { ClearTheme.colors } }
            CompositionLocalProvider(LocalClearColors provides themeColors) {
                MaterialTheme(
                    colorScheme = if (themeColors.isDark) {
                        androidx.compose.material3.darkColorScheme(
                            primary = themeColors.primary,
                            onPrimary = themeColors.onPrimary,
                            background = themeColors.background,
                            onBackground = themeColors.text,
                            surface = themeColors.surface,
                            onSurface = themeColors.text,
                            surfaceVariant = themeColors.surfaceVariant,
                            onSurfaceVariant = themeColors.textMuted,
                            error = themeColors.error,
                            onError = themeColors.text
                        )
                    } else {
                        androidx.compose.material3.lightColorScheme(
                            primary = themeColors.primary,
                            onPrimary = themeColors.onPrimary,
                            background = themeColors.background,
                            onBackground = themeColors.text,
                            surface = themeColors.surface,
                            onSurface = themeColors.text,
                            surfaceVariant = themeColors.surfaceVariant,
                            onSurfaceVariant = themeColors.textMuted,
                            error = themeColors.error,
                            onError = themeColors.text
                        )
                    }
                ) {
                    ClearLauncherApp(viewModel)
                }
            }
        }
    }
}

// ── App Root ──────────────────────────────────────────────────────────────────

@Composable
fun ClearLauncherApp(viewModel: AppViewModel) {
    val context = LocalContext.current
    var currentScreen by remember {
        mutableStateOf(
            when {
                !PinStorage.hasSeenOnboarding(context) -> "onboarding"
                !PinStorage.isPinSet(context) -> "setup"
                else -> "home"
            }
        )
    }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var recoveryInput by remember { mutableStateOf("") }
    var showRecovery by remember { mutableStateOf(false) }
    val animationsEnabled = PinStorage.getAnimations(context)
    val themeColors by remember { derivedStateOf { ClearTheme.colors } }

    CompositionLocalProvider(LocalClearColors provides themeColors) {
        if (animationsEnabled) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "screen"
            ) { screen ->
                ScreenContent(screen, viewModel, pinInput, pinError, showRecovery,
                    recoveryInput, { pinInput = it }, { pinError = it },
                    { showRecovery = it }, { recoveryInput = it },
                    { currentScreen = it }, context)
            }
        } else {
            ScreenContent(currentScreen, viewModel, pinInput, pinError, showRecovery,
                recoveryInput, { pinInput = it }, { pinError = it },
                { showRecovery = it }, { recoveryInput = it },
                { currentScreen = it }, context)
        }
    }
}

@Composable
fun ScreenContent(
    screen: String, viewModel: AppViewModel,
    pinInput: String, pinError: Boolean,
    showRecovery: Boolean, recoveryInput: String,
    onPinInput: (String) -> Unit, onPinError: (Boolean) -> Unit,
    onShowRecovery: (Boolean) -> Unit, onRecoveryInput: (String) -> Unit,
    onNavigate: (String) -> Unit, context: android.content.Context
) {
    when (screen) {
        "onboarding" -> OnboardingScreen {
            PinStorage.markOnboardingSeen(context)
            onNavigate("setup")
        }
        "setup" -> SetupScreen({ onNavigate("home") }, context)
        "home" -> HomeScreen(viewModel, {
            onPinInput(""); onPinError(false); onShowRecovery(false); onNavigate("pin")
        }, context)
        "pin" -> PinScreen(
            pinInput, pinError, showRecovery, recoveryInput,
            { onRecoveryInput(it) },
            {
                if (PinStorage.validateRecoveryCode(context, recoveryInput)) {
                    PinStorage.resetFailedAttempts(context)
                    onRecoveryInput(""); onShowRecovery(false); onNavigate("pinreset")
                } else onPinError(true)
            },
            { onShowRecovery(true) },
            { digit ->
                if (!PinStorage.isLockedOut(context) &&
                    pinInput.length < PinStorage.getMinPinLength()) {
                    val newPin = pinInput + digit
                    onPinInput(newPin)
                    if (newPin.length == PinStorage.getMinPinLength()) {
                        if (newPin == PinStorage.getPin(context)) {
                            PinStorage.resetFailedAttempts(context)
                            onPinInput(""); onPinError(false); onNavigate("settings")
                        } else {
                            PinStorage.recordFailedAttempt(context)
                            onPinError(true); onPinInput("")
                        }
                    }
                }
            },
            { if (pinInput.isNotEmpty()) onPinInput(pinInput.dropLast(1)) },
            {
                onPinInput(""); onPinError(false)
                onShowRecovery(false); onRecoveryInput(""); onNavigate("home")
            },
            context
        )
        "pinreset" -> PinResetScreen({ onNavigate("home") }, { onNavigate("settings") }, context)
        "pinchange" -> PinChangeScreen({ onNavigate("settings") }, { onNavigate("security") }, context)
        "settings" -> SettingsScreen(viewModel, { onNavigate("home") }, onNavigate, context)
        "appearance" -> AppearanceScreen({ onNavigate("settings") }, {
            ClearTheme.loadFromStorage(context)
        }, context)
        "security" -> SecurityScreen({ onNavigate("settings") }, { onNavigate("pinchange") }, context)
        "about" -> AboutScreen({ onNavigate("settings") })
    }
}

// ── Onboarding Screen ─────────────────────────────────────────────────────────

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val colors = LocalClearColors.current
    var currentPage by remember { mutableStateOf(0) }

    val pages = listOf(
        Triple("✦", "Welcome to Clear Launcher",
            "Clear Launcher keeps things simple and focused. Only the apps you choose appear on the home screen — nothing else."),
        Triple("👆", "How to Access Settings",
            "Long press on any empty space on the home screen — not on an app icon — to open caregiver settings. You'll need your PIN to get in."),
        Triple("📱", "Choosing Apps",
            "In the Apps section of settings, turn on exactly the apps your person needs. Everything else stays hidden and out of reach."),
        Triple("🔑", "Your Recovery Code",
            "During setup you'll receive a recovery code. This is a backup key — if you ever forget your PIN, the recovery code lets you back in. Write it down and keep it somewhere safe, not on this device."),
        Triple("🔐", "Your PIN Protects Everything",
            "Only someone with the PIN can change settings. Choose something memorable but not obvious. You can change it anytime from Security settings.")
    )

    val (icon, title, description) = pages[currentPage]
    val isFirst = currentPage == 0
    val isLast = currentPage == pages.size - 1

    Column(
        Modifier.fillMaxSize().background(colors.background).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(Modifier.height(48.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(100.dp).clip(RoundedCornerShape(28.dp))
                    .background(colors.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 44.sp)
            }
            Spacer(Modifier.height(32.dp))
            Text(
                title, color = colors.primary, fontSize = 24.sp,
                fontWeight = FontWeight.Light, textAlign = TextAlign.Center,
                lineHeight = 32.sp
            )
            Spacer(Modifier.height(16.dp))
            Text(
                description, color = colors.textMuted, fontSize = 15.sp,
                textAlign = TextAlign.Center, lineHeight = 24.sp
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(pages.size) { index ->
                    Box(
                        Modifier
                            .size(if (index == currentPage) 24.dp else 8.dp, 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (index == currentPage) colors.primary
                                else colors.textMuted.copy(alpha = 0.3f)
                            )
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            ClearButton(
                text = if (isLast) "Get Started" else "Next",
                colors = colors
            ) {
                if (isLast) onComplete()
                else currentPage++
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (!isFirst) {
                    TextButton(onClick = { currentPage-- }) {
                        Text("← Back", color = colors.textMuted, fontSize = 14.sp)
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                if (!isLast) {
                    TextButton(onClick = onComplete) {
                        Text("Skip", color = colors.textMuted, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// ── Setup Screen ──────────────────────────────────────────────────────────────

@Composable
fun SetupScreen(onComplete: () -> Unit, context: android.content.Context) {
    val colors = LocalClearColors.current
    var step by remember { mutableStateOf("welcome") }
    var pinInput by remember { mutableStateOf("") }
    var confirmInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var recoveryCode by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().background(colors.background).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (step) {
            "welcome" -> {
                Text("Welcome to", color = colors.textMuted, fontSize = 18.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(4.dp))
                Text("Clear Voice", color = colors.primary, fontSize = 36.sp, fontWeight = FontWeight.Light, letterSpacing = 4.sp)
                Spacer(Modifier.height(24.dp))
                Text("Let's set up your caregiver PIN.\nThis protects access to settings.",
                    color = colors.textMuted, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
                Spacer(Modifier.height(48.dp))
                ClearButton("Set Up PIN", colors = colors) { step = "create" }
            }
            "create" -> {
                Text("Create your PIN", color = colors.primary, fontSize = 24.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(8.dp))
                Text("Choose a 6-digit PIN", color = colors.textMuted, fontSize = 14.sp)
                Spacer(Modifier.height(32.dp))
                PinDots(pinInput.length, 6, colors)
                if (errorMessage.isNotEmpty()) { Spacer(Modifier.height(12.dp)); Text(errorMessage, color = colors.error, fontSize = 13.sp) }
                Spacer(Modifier.height(32.dp))
                PinPad(false, colors, { if (pinInput.length < 6) pinInput += it }, { if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1) })
                Spacer(Modifier.height(24.dp))
                ClearButton("Continue", pinInput.length == 6, colors) { step = "confirm"; errorMessage = "" }
            }
            "confirm" -> {
                Text("Confirm your PIN", color = colors.primary, fontSize = 24.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(8.dp))
                Text("Enter your PIN again", color = colors.textMuted, fontSize = 14.sp)
                Spacer(Modifier.height(32.dp))
                PinDots(confirmInput.length, 6, colors)
                if (errorMessage.isNotEmpty()) { Spacer(Modifier.height(12.dp)); Text(errorMessage, color = colors.error, fontSize = 13.sp) }
                Spacer(Modifier.height(32.dp))
                PinPad(false, colors, { if (confirmInput.length < 6) confirmInput += it }, { if (confirmInput.isNotEmpty()) confirmInput = confirmInput.dropLast(1) })
                Spacer(Modifier.height(24.dp))
                ClearButton("Confirm", confirmInput.length == 6, colors) {
                    if (confirmInput == pinInput) {
                        PinStorage.setPin(context, pinInput)
                        recoveryCode = PinStorage.generateRecoveryCode(context)
                        step = "scramble"; errorMessage = ""
                    } else { errorMessage = "PINs don't match. Try again."; confirmInput = "" }
                }
                Spacer(Modifier.height(12.dp))
                TextButton({ step = "create"; confirmInput = ""; pinInput = "" }) { Text("← Change PIN", color = colors.textMuted) }
            }
            "scramble" -> {
                Text("One more thing", color = colors.primary, fontSize = 24.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(16.dp))
                Text("PIN Scramble randomly shuffles the keypad each time — making it harder for others to guess your PIN by watching where you press.",
                    color = colors.textMuted, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
                Spacer(Modifier.height(40.dp))
                ClearButton("Enable PIN Scramble", colors = colors) { PinStorage.setPinScramble(context, true); step = "recovery" }
                Spacer(Modifier.height(12.dp))
                TextButton({ PinStorage.setPinScramble(context, false); step = "recovery" }) {
                    Text("Skip for now", color = colors.textMuted, fontSize = 14.sp)
                }
            }
            "recovery" -> {
                Text("Save your recovery code", color = colors.primary, fontSize = 22.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text("If you're ever locked out, this code resets your PIN access.",
                    color = colors.textMuted, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
                Spacer(Modifier.height(32.dp))
                RecoveryCodeBox(recoveryCode, colors)
                Spacer(Modifier.height(16.dp))
                Text("⚠ Write this down and keep it safe.\nDo not store it on this device.",
                    color = colors.textMuted, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
                Spacer(Modifier.height(40.dp))
                Button(onClick = { PinStorage.markSetupComplete(context); onComplete() },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.success, contentColor = colors.text),
                    modifier = Modifier.fillMaxWidth()) {
                    Text("I've saved my recovery code", fontSize = 15.sp, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

// ── PIN Change Screen ─────────────────────────────────────────────────────────

@Composable
fun PinChangeScreen(onComplete: () -> Unit, onCancel: () -> Unit, context: android.content.Context) {
    val colors = LocalClearColors.current
    var step by remember { mutableStateOf("check") }
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var recoveryCode by remember { mutableStateOf("") }
    val scrambled = PinStorage.getPinScramble(context)
    val canChange = PinStorage.canChangePin(context)
    val remaining = PinStorage.getPinChangesRemaining(context)

    Column(Modifier.fillMaxSize().background(colors.background).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (!canChange) {
            Text("PIN Change Limit Reached", color = colors.error, fontSize = 22.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text("You've changed your PIN 3 times today.\nPlease try again tomorrow.",
                color = colors.textMuted, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
            Spacer(Modifier.height(32.dp))
            ClearButton("Back", colors = colors) { onCancel() }
            return
        }
        when (step) {
            "check" -> {
                Text("Verify Current PIN", color = colors.primary, fontSize = 24.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(8.dp))
                Text("$remaining change${if (remaining == 1) "" else "s"} remaining today", color = colors.textMuted, fontSize = 12.sp)
                Spacer(Modifier.height(24.dp))
                PinDots(currentPin.length, 6, colors)
                if (errorMessage.isNotEmpty()) { Spacer(Modifier.height(12.dp)); Text(errorMessage, color = colors.error, fontSize = 13.sp) }
                Spacer(Modifier.height(32.dp))
                PinPad(scrambled, colors, {
                    if (currentPin.length < 6) {
                        val input = currentPin + it; currentPin = input
                        if (input.length == 6) {
                            if (input == PinStorage.getPin(context)) { step = "new"; errorMessage = ""; currentPin = "" }
                            else { errorMessage = "Incorrect PIN."; currentPin = "" }
                        }
                    }
                }, { if (currentPin.isNotEmpty()) currentPin = currentPin.dropLast(1) })
                Spacer(Modifier.height(24.dp))
                TextButton({ onCancel() }) { Text("Cancel", color = colors.textMuted) }
            }
            "new" -> {
                Text("Set New PIN", color = colors.primary, fontSize = 24.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(8.dp))
                Text("Choose a new 6-digit PIN", color = colors.textMuted, fontSize = 14.sp)
                Spacer(Modifier.height(32.dp))
                PinDots(newPin.length, 6, colors)
                Spacer(Modifier.height(32.dp))
                PinPad(false, colors, { if (newPin.length < 6) newPin += it }, { if (newPin.isNotEmpty()) newPin = newPin.dropLast(1) })
                Spacer(Modifier.height(24.dp))
                ClearButton("Continue", newPin.length == 6, colors) { step = "confirm" }
                Spacer(Modifier.height(12.dp))
                TextButton({ onCancel() }) { Text("Cancel", color = colors.textMuted) }
            }
            "confirm" -> {
                Text("Confirm New PIN", color = colors.primary, fontSize = 24.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(8.dp))
                Text("Enter your new PIN again", color = colors.textMuted, fontSize = 14.sp)
                Spacer(Modifier.height(32.dp))
                PinDots(confirmPin.length, 6, colors)
                if (errorMessage.isNotEmpty()) { Spacer(Modifier.height(12.dp)); Text(errorMessage, color = colors.error, fontSize = 13.sp) }
                Spacer(Modifier.height(32.dp))
                PinPad(false, colors, { if (confirmPin.length < 6) confirmPin += it }, { if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1) })
                Spacer(Modifier.height(24.dp))
                ClearButton("Confirm", confirmPin.length == 6, colors) {
                    if (confirmPin == newPin) {
                        PinStorage.setPin(context, newPin); PinStorage.recordPinChange(context)
                        recoveryCode = PinStorage.generateRecoveryCode(context); step = "recovery"; errorMessage = ""
                    } else { errorMessage = "PINs don't match."; confirmPin = "" }
                }
                Spacer(Modifier.height(12.dp))
                TextButton({ step = "new"; confirmPin = ""; newPin = "" }) { Text("← Change PIN", color = colors.textMuted) }
            }
            "recovery" -> {
                Text("New Recovery Code", color = colors.primary, fontSize = 22.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text("Your PIN has been changed.\nSave your new recovery code.",
                    color = colors.textMuted, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
                Spacer(Modifier.height(32.dp))
                RecoveryCodeBox(recoveryCode, colors)
                Spacer(Modifier.height(16.dp))
                Text("⚠ Your old recovery code no longer works.", color = colors.textMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(40.dp))
                Button(onClick = onComplete,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.success, contentColor = colors.text),
                    modifier = Modifier.fillMaxWidth()) {
                    Text("I've saved my new recovery code", fontSize = 15.sp, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

// ── PIN Reset Screen ──────────────────────────────────────────────────────────

@Composable
fun PinResetScreen(onComplete: () -> Unit, onCancel: () -> Unit, context: android.content.Context) {
    val colors = LocalClearColors.current
    var step by remember { mutableStateOf("new") }
    var pinInput by remember { mutableStateOf("") }
    var confirmInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var newRecoveryCode by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(colors.background).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        when (step) {
            "new" -> {
                Text("Set New PIN", color = colors.primary, fontSize = 24.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(8.dp))
                Text("Choose a new 6-digit PIN", color = colors.textMuted, fontSize = 14.sp)
                Spacer(Modifier.height(32.dp))
                PinDots(pinInput.length, 6, colors)
                Spacer(Modifier.height(32.dp))
                PinPad(false, colors, { if (pinInput.length < 6) pinInput += it }, { if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1) })
                Spacer(Modifier.height(24.dp))
                ClearButton("Continue", pinInput.length == 6, colors) { step = "confirm"; errorMessage = "" }
                Spacer(Modifier.height(12.dp))
                TextButton({ onCancel() }) { Text("Cancel", color = colors.textMuted) }
            }
            "confirm" -> {
                Text("Confirm New PIN", color = colors.primary, fontSize = 24.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(8.dp))
                Text("Enter your new PIN again", color = colors.textMuted, fontSize = 14.sp)
                Spacer(Modifier.height(32.dp))
                PinDots(confirmInput.length, 6, colors)
                if (errorMessage.isNotEmpty()) { Spacer(Modifier.height(12.dp)); Text(errorMessage, color = colors.error, fontSize = 13.sp) }
                Spacer(Modifier.height(32.dp))
                PinPad(false, colors, { if (confirmInput.length < 6) confirmInput += it }, { if (confirmInput.isNotEmpty()) confirmInput = confirmInput.dropLast(1) })
                Spacer(Modifier.height(24.dp))
                ClearButton("Confirm", confirmInput.length == 6, colors) {
                    if (confirmInput == pinInput) {
                        PinStorage.setPin(context, pinInput)
                        newRecoveryCode = PinStorage.generateRecoveryCode(context)
                        step = "recovery"; errorMessage = ""
                    } else { errorMessage = "PINs don't match."; confirmInput = "" }
                }
                Spacer(Modifier.height(12.dp))
                TextButton({ step = "new"; confirmInput = ""; pinInput = "" }) { Text("← Change PIN", color = colors.textMuted) }
            }
            "recovery" -> {
                Text("New Recovery Code", color = colors.primary, fontSize = 22.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text("Your PIN has been reset.\nSave your new recovery code.",
                    color = colors.textMuted, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
                Spacer(Modifier.height(32.dp))
                RecoveryCodeBox(newRecoveryCode, colors)
                Spacer(Modifier.height(16.dp))
                Text("⚠ Your old recovery code no longer works.", color = colors.textMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(40.dp))
                Button(onClick = onComplete,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.success, contentColor = colors.text),
                    modifier = Modifier.fillMaxWidth()) {
                    Text("I've saved my new recovery code", fontSize = 15.sp, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

// ── Home Screen ───────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(viewModel: AppViewModel, onLongPress: () -> Unit, context: android.content.Context) {
    val enabledApps by viewModel.enabledApps.collectAsState()
    val themeColors = LocalClearColors.current
    val wallpaperType = PinStorage.getWallpaperType(context)
    val wallpaperColor = PinStorage.getWallpaperColor(context)
    val wallpaperUri = PinStorage.getWallpaperUri(context)
    val scrimOpacity = PinStorage.getScrimOpacity(context)
    val gridColumns = PinStorage.getGridColumns(context)
    val iconSize: Dp = when (PinStorage.getIconSize(context)) { "small" -> 56.dp; "large" -> 88.dp; else -> 72.dp }
    val titleSize: TextUnit = when (PinStorage.getTextSize(context)) { "small" -> 11.sp; "medium" -> 14.sp; "large" -> 17.sp; "xlarge" -> 20.sp; else -> 14.sp }
    val (homeText, homeTextMuted) = getHomeTextColorForBackground(wallpaperType, wallpaperColor, themeColors)

    val initialCal = Calendar.getInstance()
    val initialHour = initialCal.get(Calendar.HOUR_OF_DAY)
    val initialMinute = initialCal.get(Calendar.MINUTE)
    val initialAmPm = if (initialHour < 12) "AM" else "PM"
    val initialDisplayHour = when { initialHour == 0 -> 12; initialHour > 12 -> initialHour - 12; else -> initialHour }
    var currentTime by remember { mutableStateOf("%d:%02d %s".format(initialDisplayHour, initialMinute, initialAmPm)) }
    var greeting by remember {
        mutableStateOf(when (initialHour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..20 -> "Good evening"; else -> "Good night" })
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            val cal = Calendar.getInstance()
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            val amPm = if (hour < 12) "AM" else "PM"
            val displayHour = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
            currentTime = "%d:%02d %s".format(displayHour, minute, amPm)
            greeting = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..20 -> "Good evening"; else -> "Good night" }
        }
    }

    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }) {
        if (wallpaperType == "gallery" && wallpaperUri.isNotEmpty()) {
            Image(rememberAsyncImagePainter(Uri.parse(wallpaperUri)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize().background(Color(wallpaperColor)))
        }
        if (scrimOpacity > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimOpacity)))

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(greeting, color = homeTextMuted, fontSize = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.5.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("Clear Voice", color = themeColors.primary, fontSize = 26.sp, fontWeight = FontWeight.Light, letterSpacing = 2.sp)
                }
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.2f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(currentTime, color = homeText, fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                }
            }
            Box(Modifier.padding(horizontal = 28.dp).fillMaxWidth().height(1.dp).background(homeText.copy(alpha = 0.1f)))
            Spacer(Modifier.height(16.dp))

            if (enabledApps.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(homeText.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                            Text("✦", color = homeTextMuted, fontSize = 28.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("No apps selected", color = homeText, fontSize = 18.sp, fontWeight = FontWeight.Light)
                        Spacer(Modifier.height(8.dp))
                        Text("Long press on empty space to\naccess caregiver settings", color = homeTextMuted, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
                    }
                }
            } else {
                LazyVerticalGrid(GridCells.Fixed(gridColumns), Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp, start = 8.dp, end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(enabledApps) { app ->
                        AppIcon(app, iconSize, titleSize, homeText, PinStorage.getAnimations(context), themeColors) { viewModel.launchApp(app.packageName) }
                    }
                }
            }
        }
    }
}

// ── App Icon ──────────────────────────────────────────────────────────────────

@Composable
fun AppIcon(app: AppInfo, iconSize: Dp = 72.dp, titleSize: TextUnit = 14.sp,
            textColor: Color = Color(0xFFF5F0E8), animationsEnabled: Boolean = true,
            colors: ClearColors = LocalClearColors.current, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed && animationsEnabled) 0.88f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "scale")

    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).scale(scale).clickable { pressed = true; onClick() }.padding(8.dp)) {
        Box(Modifier.size(iconSize).clip(RoundedCornerShape(iconSize * 0.22f))
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(iconSize * 0.22f)),
            contentAlignment = Alignment.Center) {
            if (app.icon != null) {
                Image(painter = rememberDrawablePainter(app.icon), contentDescription = app.name,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize().background(colors.primary.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    Text(app.name.take(1).uppercase(), color = colors.primary, fontSize = (iconSize.value * 0.39f).sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(app.name, color = textColor, fontSize = titleSize, textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium,
            lineHeight = titleSize * 1.2f, modifier = Modifier.widthIn(max = iconSize + 16.dp))
    }
    LaunchedEffect(pressed) { if (pressed) { delay(150); pressed = false } }
}

// ── PIN Screen ────────────────────────────────────────────────────────────────

@Composable
fun PinScreen(pinInput: String, pinError: Boolean, showRecovery: Boolean, recoveryInput: String,
              onRecoveryInputChange: (String) -> Unit, onRecoverySubmit: () -> Unit,
              onShowRecovery: () -> Unit, onDigit: (String) -> Unit, onBackspace: () -> Unit,
              onCancel: () -> Unit, context: android.content.Context) {
    val colors = LocalClearColors.current
    val isPermanentlyLocked = PinStorage.isPermanentlyLocked(context)
    val isLockedOut = PinStorage.isLockedOut(context)
    val failedAttempts = PinStorage.getFailedAttempts(context)
    val showRecoveryOption = PinStorage.shouldShowRecoveryOption(context)
    val scrambled = PinStorage.getPinScramble(context)
    var remainingMs by remember { mutableStateOf(PinStorage.getRemainingLockoutMs(context)) }

    LaunchedEffect(isLockedOut) {
        while (PinStorage.isLockedOut(context) && !PinStorage.isPermanentlyLocked(context)) {
            remainingMs = PinStorage.getRemainingLockoutMs(context); delay(1000)
        }
        remainingMs = 0
    }

    Column(Modifier.fillMaxSize().background(colors.background).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        when {
            showRecovery -> {
                Text("Enter Recovery Code", color = colors.primary, fontSize = 22.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(8.dp))
                Text("Enter the code you saved during setup", color = colors.textMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(32.dp))
                OutlinedTextField(recoveryInput, onRecoveryInputChange,
                    placeholder = { Text("CLEAR-XXXX-XXXX", color = colors.textMuted) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = colors.text, unfocusedTextColor = colors.text,
                        focusedBorderColor = colors.primary, unfocusedBorderColor = colors.textMuted),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), modifier = Modifier.fillMaxWidth())
                if (pinError) { Spacer(Modifier.height(8.dp)); Text("Invalid recovery code", color = colors.error, fontSize = 13.sp) }
                Spacer(Modifier.height(24.dp))
                ClearButton("Submit", colors = colors) { onRecoverySubmit() }
                Spacer(Modifier.height(12.dp))
                TextButton({ onCancel() }) { Text("Cancel", color = colors.textMuted) }
            }
            isPermanentlyLocked -> {
                Text("Settings Locked", color = colors.error, fontSize = 24.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(16.dp))
                Text("Too many incorrect attempts.\nUse your recovery code to regain access.",
                    color = colors.textMuted, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
                Spacer(Modifier.height(32.dp))
                ClearButton("Enter Recovery Code", colors = colors) { onShowRecovery() }
                Spacer(Modifier.height(12.dp))
                TextButton({ onCancel() }) { Text("Cancel", color = colors.textMuted) }
            }
            isLockedOut && remainingMs > 0 -> {
                val s = (remainingMs / 1000) % 60; val m = (remainingMs / 1000) / 60
                Text("Too Many Attempts", color = colors.error, fontSize = 24.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(16.dp))
                Text("Please wait before trying again", color = colors.textMuted, fontSize = 14.sp)
                Spacer(Modifier.height(32.dp))
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(colors.error.copy(alpha = 0.1f)).padding(horizontal = 32.dp, vertical = 16.dp)) {
                    Text(if (m > 0) "${m}m ${s}s" else "${s}s", color = colors.error, fontSize = 36.sp, fontWeight = FontWeight.Light)
                }
                Spacer(Modifier.height(16.dp))
                Text("$failedAttempts failed attempts", color = colors.textMuted.copy(alpha = 0.6f), fontSize = 12.sp)
                Spacer(Modifier.height(32.dp))
                if (showRecoveryOption) TextButton({ onShowRecovery() }) { Text("Use recovery code instead", color = colors.textMuted, fontSize = 13.sp) }
                TextButton({ onCancel() }) { Text("Cancel", color = colors.textMuted) }
            }
            else -> {
                Text("Caregiver Access", color = colors.primary, fontSize = 24.sp, fontWeight = FontWeight.Light, letterSpacing = 2.sp)
                Spacer(Modifier.height(8.dp))
                Text("Enter your 6-digit PIN", color = colors.textMuted, fontSize = 14.sp)
                Spacer(Modifier.height(32.dp))
                PinDots(pinInput.length, 6, colors)
                if (pinError) {
                    Spacer(Modifier.height(12.dp))
                    val left = 10 - failedAttempts
                    Text("Incorrect PIN. $left attempt${if (left == 1) "" else "s"} remaining.", color = colors.error, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(32.dp))
                PinPad(scrambled, colors, onDigit, onBackspace)
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton({ onCancel() }) { Text("Cancel", color = colors.textMuted, fontSize = 14.sp) }
                    if (showRecoveryOption) TextButton({ onShowRecovery() }) { Text("Forgot PIN?", color = colors.textMuted, fontSize = 14.sp) }
                }
            }
        }
    }
}

// ── Settings Screen ───────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(viewModel: AppViewModel, onBack: () -> Unit, onNavigate: (String) -> Unit, context: android.content.Context) {
    val colors = LocalClearColors.current
    val allApps by viewModel.allApps.collectAsState()
    var selectAll by remember { mutableStateOf(false) }

    LaunchedEffect(allApps) { selectAll = allApps.isNotEmpty() && allApps.all { it.isEnabled } }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        Row(Modifier.fillMaxWidth().padding(top = 48.dp, start = 24.dp, end = 24.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton({ onBack() }) { Text("← Back", color = colors.primary, fontSize = 16.sp) }
            Spacer(Modifier.weight(1f))
            Text("Settings", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(64.dp))
        }
        HorizontalDivider(color = colors.divider)
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                SettingsSectionHeader("Preferences", colors)
                SettingsNavItem("Appearance", "Wallpaper, icons, text, grid, theme", colors) { onNavigate("appearance") }
                SettingsNavItem("Security", "Change PIN, recovery, device ID", colors) { onNavigate("security") }
                SettingsNavItem("About", "Version, licenses, support", colors) { onNavigate("about") }
                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                SettingsSectionHeader("Apps", colors)
                Row(Modifier.fillMaxWidth().clickable {
                    val newState = !selectAll; selectAll = newState
                    allApps.forEach { viewModel.toggleApp(it.packageName, newState) }
                }.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (selectAll) "Deselect All" else "Select All", color = colors.primary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Switch(checked = selectAll, onCheckedChange = { newState ->
                        selectAll = newState; allApps.forEach { viewModel.toggleApp(it.packageName, newState) }
                    }, colors = SwitchDefaults.colors(checkedThumbColor = colors.background, checkedTrackColor = colors.primary,
                        uncheckedThumbColor = colors.textMuted, uncheckedTrackColor = colors.textMuted.copy(alpha = 0.3f)))
                }
                HorizontalDivider(color = colors.divider)
            }
            items(allApps.size) { index ->
                val app = allApps[index]
                Row(Modifier.fillMaxWidth().clickable { viewModel.toggleApp(app.packageName, !app.isEnabled) }
                    .padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
                        if (app.icon != null) {
                            Image(rememberDrawablePainter(app.icon), app.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Text(app.name.take(1).uppercase(), color = colors.primary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(app.name, color = colors.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Switch(checked = app.isEnabled, onCheckedChange = { viewModel.toggleApp(app.packageName, it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.background, checkedTrackColor = colors.primary,
                            uncheckedThumbColor = colors.textMuted, uncheckedTrackColor = colors.textMuted.copy(alpha = 0.3f)))
                }
                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 24.dp))
            }
        }
    }
}

// ── Appearance Screen ─────────────────────────────────────────────────────────

@Composable
fun AppearanceScreen(onBack: () -> Unit, onThemeChanged: () -> Unit, context: android.content.Context) {
    val colors = LocalClearColors.current
    var wallpaperType by remember { mutableStateOf(PinStorage.getWallpaperType(context)) }
    var selectedColor by remember { mutableStateOf(PinStorage.getWallpaperColor(context)) }
    var iconSize by remember { mutableStateOf(PinStorage.getIconSize(context)) }
    var textSize by remember { mutableStateOf(PinStorage.getTextSize(context)) }
    var animations by remember { mutableStateOf(PinStorage.getAnimations(context)) }
    var isDark by remember { mutableStateOf(PinStorage.getTheme(context) == "dark") }
    var scrimOpacity by remember { mutableStateOf(PinStorage.getScrimOpacity(context)) }
    var wallpaperTab by remember { mutableStateOf("dark") }
    var gridColumns by remember { mutableStateOf(PinStorage.getGridColumns(context)) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { }
            PinStorage.setWallpaperGallery(context, it.toString()); wallpaperType = "gallery"
            val bitmap = loadBitmapFromUri(context, it)
            if (bitmap != null) { val extracted = extractPaletteFromBitmap(bitmap); ClearTheme.applyGalleryTheme(extracted, isDark) }
            else ClearTheme.loadFromStorage(context)
            onThemeChanged()
        }
    }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        Row(Modifier.fillMaxWidth().padding(top = 48.dp, start = 24.dp, end = 24.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton({ onBack() }) { Text("← Back", color = colors.primary, fontSize = 16.sp) }
            Spacer(Modifier.weight(1f))
            Text("Appearance", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(64.dp))
        }
        HorizontalDivider(color = colors.divider)

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                SettingsSectionHeader("Theme", colors)
                Row(Modifier.fillMaxWidth().clickable {
                    isDark = !isDark; PinStorage.setTheme(context, if (isDark) "dark" else "light")
                    if (wallpaperType != "solid") { ClearTheme.applyTheme(isDark); onThemeChanged() }
                }.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Dark Mode", color = colors.text, fontSize = 16.sp)
                        Text(if (isDark) "Dark background, light text" else "Light background, dark text", color = colors.textMuted, fontSize = 12.sp)
                    }
                    Switch(checked = isDark, onCheckedChange = {
                        isDark = it; PinStorage.setTheme(context, if (it) "dark" else "light")
                        if (wallpaperType != "solid") { ClearTheme.applyTheme(it); onThemeChanged() }
                    }, colors = SwitchDefaults.colors(checkedThumbColor = colors.background, checkedTrackColor = colors.primary,
                        uncheckedThumbColor = colors.textMuted, uncheckedTrackColor = colors.textMuted.copy(alpha = 0.3f)))
                }
                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            }

            item {
                SettingsSectionHeader("Wallpaper", colors)
                Row(Modifier.fillMaxWidth().clickable { galleryLauncher.launch("image/*") }.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(colors.primaryContainer), contentAlignment = Alignment.Center) { Text("🖼", fontSize = 22.sp) }
                    Spacer(Modifier.width(16.dp))
                    Column { Text("Choose from Gallery", color = colors.text, fontSize = 16.sp); Text("Use a photo from your device", color = colors.textMuted, fontSize = 12.sp) }
                    if (wallpaperType == "gallery") { Spacer(Modifier.weight(1f)); Text("✓", color = colors.primary, fontSize = 18.sp) }
                }
                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("dark" to "Dark", "light" to "Light").forEach { (tab, label) ->
                        val selected = wallpaperTab == tab
                        OutlinedButton({ wallpaperTab = tab },
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) colors.primaryContainer else Color.Transparent, contentColor = if (selected) colors.primary else colors.textMuted),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) colors.primary else colors.textMuted.copy(alpha = 0.3f)),
                            modifier = Modifier.weight(1f)) { Text(label, fontSize = 14.sp) }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            item {
                val swatches = if (wallpaperTab == "dark") DarkWallpaperOptions else LightWallpaperOptions
                LazyVerticalGrid(GridCells.Fixed(5), Modifier.padding(horizontal = 24.dp).height(120.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(swatches) { option ->
                        val isSelected = selectedColor == option.colorLong && wallpaperType == "solid"
                        Box(Modifier.size(48.dp).clip(CircleShape).background(Color(option.colorLong))
                            .then(if (isSelected) Modifier.border(3.dp, colors.primary, CircleShape) else Modifier)
                            .border(1.dp, colors.divider, CircleShape)
                            .clickable {
                                selectedColor = option.colorLong; PinStorage.setWallpaperSolid(context, option.colorLong)
                                wallpaperType = "solid"; ClearTheme.applyWallpaperTheme(option.colorLong, option.isDark); onThemeChanged()
                            })
                    }
                }
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 24.dp))
            }

            item {
                SettingsSectionHeader("App Grid Overlay", colors)
                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text("Darken the area behind your apps for better readability", color = colors.textMuted, fontSize = 12.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    Slider(scrimOpacity, { scrimOpacity = it; PinStorage.setScrimOpacity(context, it) }, valueRange = 0f..0.7f,
                        colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary, inactiveTrackColor = colors.surfaceVariant))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("None", color = colors.textMuted, fontSize = 11.sp)
                        Text("${(scrimOpacity * 100).toInt()}%", color = colors.primary, fontSize = 11.sp)
                        Text("Strong", color = colors.textMuted, fontSize = 11.sp)
                    }
                }
                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            }

            item {
                SettingsSectionHeader("Grid Layout", colors)
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(2 to "2 ×", 3 to "3 ×", 4 to "4 ×").forEach { (value, label) ->
                        val selected = gridColumns == value
                        OutlinedButton({ gridColumns = value; PinStorage.setGridColumns(context, value) },
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) colors.primaryContainer else Color.Transparent, contentColor = if (selected) colors.primary else colors.textMuted),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) colors.primary else colors.textMuted.copy(alpha = 0.3f)),
                            modifier = Modifier.weight(1f)) { Text(label, fontSize = 14.sp) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(when (gridColumns) { 2 -> "Larger icons, fewer apps visible"; 4 -> "More apps, best with smaller icons"; else -> "Balanced layout" },
                    color = colors.textMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            }

            item {
                SettingsSectionHeader("Icon Size", colors)
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("small" to "S", "medium" to "M", "large" to "L").forEach { (value, label) ->
                        val selected = iconSize == value
                        OutlinedButton({ iconSize = value; PinStorage.setIconSize(context, value) },
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) colors.primaryContainer else Color.Transparent, contentColor = if (selected) colors.primary else colors.textMuted),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) colors.primary else colors.textMuted.copy(alpha = 0.3f)),
                            modifier = Modifier.weight(1f)) { Text(label, fontSize = 14.sp) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            }

            item {
                SettingsSectionHeader("App Name Size", colors)
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("small" to "XS", "medium" to "S", "large" to "M", "xlarge" to "L").forEach { (value, label) ->
                        val selected = textSize == value
                        OutlinedButton({ textSize = value; PinStorage.setTextSize(context, value) },
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) colors.primaryContainer else Color.Transparent, contentColor = if (selected) colors.primary else colors.textMuted),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) colors.primary else colors.textMuted.copy(alpha = 0.3f)),
                            modifier = Modifier.weight(1f)) { Text(label, fontSize = 14.sp) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            }

            item {
                SettingsSectionHeader("Animations", colors)
                Row(Modifier.fillMaxWidth().clickable { animations = !animations; PinStorage.setAnimations(context, animations) }
                    .padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Enable Animations", color = colors.text, fontSize = 16.sp)
                        Text("Screen transitions and icon press effects", color = colors.textMuted, fontSize = 12.sp)
                    }
                    Switch(checked = animations, onCheckedChange = { animations = it; PinStorage.setAnimations(context, it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.background, checkedTrackColor = colors.primary,
                            uncheckedThumbColor = colors.textMuted, uncheckedTrackColor = colors.textMuted.copy(alpha = 0.3f)))
                }
            }
        }
    }
}

// ── Security Screen ───────────────────────────────────────────────────────────

@Composable
fun SecurityScreen(onBack: () -> Unit, onChangePIN: () -> Unit, context: android.content.Context) {
    val colors = LocalClearColors.current
    var pinScramble by remember { mutableStateOf(PinStorage.getPinScramble(context)) }
    val deviceId = PinStorage.getDeviceId(context)

    Column(Modifier.fillMaxSize().background(colors.background)) {
        Row(Modifier.fillMaxWidth().padding(top = 48.dp, start = 24.dp, end = 24.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton({ onBack() }) { Text("← Back", color = colors.primary, fontSize = 16.sp) }
            Spacer(Modifier.weight(1f))
            Text("Security", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(64.dp))
        }
        HorizontalDivider(color = colors.divider)
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                SettingsSectionHeader("PIN Security", colors)
                Row(Modifier.fillMaxWidth().clickable { pinScramble = !pinScramble; PinStorage.setPinScramble(context, pinScramble) }
                    .padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Scramble PIN Pad", color = colors.text, fontSize = 16.sp)
                        Text("Randomize number positions each time", color = colors.textMuted, fontSize = 12.sp)
                    }
                    Switch(checked = pinScramble, onCheckedChange = { pinScramble = it; PinStorage.setPinScramble(context, it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.background, checkedTrackColor = colors.primary,
                            uncheckedThumbColor = colors.textMuted, uncheckedTrackColor = colors.textMuted.copy(alpha = 0.3f)))
                }
                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 24.dp))
                Row(Modifier.fillMaxWidth().clickable { onChangePIN() }.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Change PIN", color = colors.text, fontSize = 16.sp)
                        val remaining = PinStorage.getPinChangesRemaining(context)
                        Text("$remaining change${if (remaining == 1) "" else "s"} remaining today", color = colors.textMuted, fontSize = 12.sp)
                    }
                    Text("→", color = colors.textMuted, fontSize = 18.sp)
                }
                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                SettingsSectionHeader("Device", colors)
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp)) {
                    Text("Device ID", color = colors.text, fontSize = 16.sp)
                    Text("Share with Clear Voice support if you need a master reset code.", color = colors.textMuted, fontSize = 12.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(colors.primaryContainer).padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(deviceId, color = colors.primary, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                    }
                }
                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                SettingsSectionHeader("Support", colors)
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp)) {
                    Text("Need help? Contact Clear Voice support.", color = colors.textMuted, fontSize = 13.sp, lineHeight = 20.sp)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.primaryContainer)
                        .clickable { val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+15052011185")); context.startActivity(intent) }
                        .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("📞", fontSize = 20.sp); Spacer(Modifier.width(12.dp))
                        Column { Text("Call Support", color = colors.primary, fontSize = 15.sp, fontWeight = FontWeight.Medium); Text("(505) 201-1185", color = colors.textMuted, fontSize = 13.sp) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.primaryContainer)
                        .clickable {
                            val intent = Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:support@clearvoiceworks.org"); putExtra(Intent.EXTRA_SUBJECT, "Clear Launcher Support") }
                            context.startActivity(intent)
                        }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("✉️", fontSize = 20.sp); Spacer(Modifier.width(12.dp))
                        Column { Text("Email Support", color = colors.primary, fontSize = 15.sp, fontWeight = FontWeight.Medium); Text("support@clearvoiceworks.org", color = colors.textMuted, fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}

// ── About Screen ──────────────────────────────────────────────────────────────

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val colors = LocalClearColors.current
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().background(colors.background)) {
        Row(Modifier.fillMaxWidth().padding(top = 48.dp, start = 24.dp, end = 24.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton({ onBack() }) { Text("← Back", color = colors.primary, fontSize = 16.sp) }
            Spacer(Modifier.weight(1f))
            Text("About", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(64.dp))
        }
        HorizontalDivider(color = colors.divider)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
                        Text("✦", color = colors.primary, fontSize = 36.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Clear Launcher", color = colors.text, fontSize = 24.sp, fontWeight = FontWeight.Light)
                    Text("Version 0.1.0", color = colors.textMuted, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Built clearly. Built for you.", color = colors.primary, fontSize = 13.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                }
                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 24.dp))
                SettingsSectionHeader("Mission", colors)
                Text("Clear Launcher is built for people with intellectual and developmental disabilities — and the families who love them. Private, simple, and transparent about everything it does.",
                    color = colors.textMuted, fontSize = 14.sp, lineHeight = 22.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                SettingsSectionHeader("Links", colors)
                SettingsNavItem("clearvoiceworks.org", "Visit our website", colors) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://clearvoiceworks.org"))
                    context.startActivity(intent)
                }
                SettingsSectionHeader("Legal", colors)
                Text("Clear Launcher is free and open source software, licensed under GPL v3.\n\nCopyright © 2026 Clear Voice LLC",
                    color = colors.textMuted, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            }
        }
    }
}

// ── Reusable Components ───────────────────────────────────────────────────────

@Composable
fun PinDots(filled: Int, total: Int, colors: ClearColors = LocalClearColors.current) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(total) { index ->
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(7.dp))
                .background(if (index < filled) colors.primary else colors.textMuted.copy(alpha = 0.3f)))
        }
    }
}

@Composable
fun PinPad(scrambled: Boolean, colors: ClearColors = LocalClearColors.current, onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    val digits = remember(scrambled) {
        if (scrambled) {
            val numbers = (1..9).map { it.toString() }.toMutableList()
            numbers.shuffle(); numbers.add(""); numbers.add("0"); numbers.add("⌫"); numbers.toList()
        } else listOf("1","2","3","4","5","6","7","8","9","","0","⌫")
    }
    LazyVerticalGrid(GridCells.Fixed(3), Modifier.width(240.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), userScrollEnabled = false) {
        items(digits) { digit ->
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(32.dp))
                .background(if (digit.isEmpty()) Color.Transparent else colors.surfaceVariant)
                .clickable(enabled = digit.isNotEmpty()) { if (digit == "⌫") onBackspace() else onDigit(digit) },
                contentAlignment = Alignment.Center) {
                if (digit.isNotEmpty()) Text(digit, color = colors.text, fontSize = 22.sp, fontWeight = FontWeight.Light)
            }
        }
    }
}

@Composable
fun ClearButton(text: String, enabled: Boolean = true, colors: ClearColors = LocalClearColors.current, onClick: () -> Unit) {
    Button(onClick, enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary,
            disabledContainerColor = colors.primary.copy(alpha = 0.3f), disabledContentColor = colors.onPrimary.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()) {
        Text(text, fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
    }
}

@Composable
fun SettingsSectionHeader(title: String, colors: ClearColors = LocalClearColors.current) {
    Text(title.uppercase(), color = colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp))
}

@Composable
fun SettingsNavItem(title: String, subtitle: String, colors: ClearColors = LocalClearColors.current, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = colors.text, fontSize = 16.sp); Text(subtitle, color = colors.textMuted, fontSize = 12.sp) }
        Text("→", color = colors.textMuted, fontSize = 18.sp)
    }
    HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 24.dp))
}

@Composable
fun RecoveryCodeBox(code: String, colors: ClearColors = LocalClearColors.current) {
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(colors.primaryContainer).padding(horizontal = 32.dp, vertical = 20.dp)) {
        Text(code, color = colors.primary, fontSize = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
    }
}