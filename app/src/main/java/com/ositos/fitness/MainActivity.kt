package com.ositos.fitness

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ositos.fitness.ui.DuoViewModel
import com.ositos.fitness.ui.MainScaffold
import com.ositos.fitness.ui.Session
import com.ositos.fitness.ui.SessionViewModel
import com.ositos.fitness.ui.components.BouncyButton
import com.ositos.fitness.ui.screens.OnboardingFlow
import com.ositos.fitness.ui.screens.PairScreen
import com.ositos.fitness.ui.screens.ThemeMode
import com.ositos.fitness.ui.screens.WelcomeScreen
import com.ositos.fitness.ui.theme.OsitosTheme
import androidx.compose.foundation.isSystemInDarkTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as OsitosApp
        val container = app.container

        setContent {
            var themeMode by remember {
                mutableStateOf(
                    runCatching { ThemeMode.valueOf(container.prefs.getString("theme", ThemeMode.DARK.name)!!) }
                        .getOrDefault(ThemeMode.DARK),
                )
            }
            val dark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            OsitosTheme(dark = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val sessionVm: SessionViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer { SessionViewModel(container.repo, app.firebasePlaceholder) }
                        },
                    )
                    val session by sessionVm.session.collectAsStateWithLifecycle()
                    val ctx = LocalContext.current
                    val toast: (String) -> Unit = { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }

                    when (val s = session) {
                        Session.Loading -> Centered { CircularProgressIndicator() }
                        Session.ConfigMissing -> ConfigMissing()
                        Session.SignedOut -> WelcomeScreen(
                            onToken = { sessionVm.signInWithGoogleToken(it, toast) },
                            onError = toast,
                        )
                        is Session.NeedsPartner -> {
                            val pairState by sessionVm.pair.collectAsStateWithLifecycle()
                            PairScreen(
                                account = s.account,
                                state = pairState,
                                onSearch = sessionVm::searchPin,
                                onConfirm = sessionVm::confirmPair,
                                onReset = sessionVm::resetPair,
                                onSignOut = { sessionVm.signOut() },
                            )
                        }
                        is Session.Error -> Centered {
                            Text("😵", fontSize = 56.sp)
                            Text(s.message, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(16.dp))
                            BouncyButton("Reintentar", { sessionVm.refresh() })
                            Spacer(Modifier.height(8.dp))
                            BouncyButton("Cerrar sesión", { sessionVm.signOut() }, color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                        is Session.NeedsProfile -> OnboardingFlow(s.uid, s.displayName, s.draft) { profile ->
                            sessionVm.completeOnboarding(profile, toast)
                        }
                        is Session.Ready -> {
                            NotificationPermission()
                            val duoVm: DuoViewModel = viewModel(
                                key = "duo_${s.uid}_${s.duoId}",
                                factory = viewModelFactory {
                                    initializer { DuoViewModel(container.repo, container.ai, s.uid) }
                                },
                            )
                            MainScaffold(
                                vm = duoVm,
                                themeMode = themeMode,
                                onThemeMode = {
                                    themeMode = it
                                    container.prefs.edit().putString("theme", it.name).apply()
                                },
                                onSignOut = { sessionVm.signOut() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) { content() }
}

@Composable
private fun ConfigMissing() = Centered {
    Text("🔧", fontSize = 56.sp)
    Text("Falta configurar Firebase", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Este APK se compiló con el google-services.json de ejemplo. " +
            "Agregá el secreto GOOGLE_SERVICES_JSON en GitHub (o el archivo app/google-services.json) " +
            "y volvé a compilar. Los pasos están en el README.",
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun NotificationPermission() {
    if (Build.VERSION.SDK_INT < 33) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}
