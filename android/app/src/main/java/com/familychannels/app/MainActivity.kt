package com.familychannels.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.familychannels.data.ApiFactory
import com.familychannels.data.FamilyRepositoryImpl
import com.familychannels.data.SessionStore
import com.familychannels.domain.usecase.CanWatchUseCase
import com.familychannels.domain.usecase.JoinFamilyUseCase
import com.familychannels.domain.usecase.LoadChannelsUseCase
import com.familychannels.domain.usecase.LoadVideosUseCase
import com.familychannels.feature.home.HomeScreen
import com.familychannels.feature.home.HomeViewModel
import com.familychannels.feature.join.JoinScreen
import com.familychannels.feature.join.JoinViewModel
import com.familychannels.feature.quota.QuotaViewModel
import com.familychannels.feature.quota.formatQuotaLabel
import com.familychannels.feature.videos.VideosScreen
import com.familychannels.feature.videos.VideosViewModel
import com.familychannels.ui.i18n.AppStrings
import com.familychannels.ui.theme.FamilyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = SessionStore(applicationContext)
        val repo = FamilyRepositoryImpl(ApiFactory.create(), store)
        setContent {
            FamilyApp(repo, store)
        }
    }
}

@Composable
private fun FamilyApp(repo: FamilyRepositoryImpl, store: SessionStore) {
    var lang by remember { mutableStateOf("fr") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { lang = AppStrings.normalize(store.lang()) }
    val strings = AppStrings.of(lang)
    val layoutDir = if (AppStrings.isRtl(lang)) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
        FamilyTheme {
            val nav = rememberNavController()
            val quotaVm = remember {
                QuotaViewModel(CanWatchUseCase(repo), repo)
            }
            val quota by quotaVm.quota.collectAsState()
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        quotaVm.refresh()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            NavHost(navController = nav, startDestination = "join") {
                composable("join") {
                    val vm = remember {
                        JoinViewModel(JoinFamilyUseCase(repo), repo)
                    }
                    JoinScreen(
                        viewModel = vm,
                        strings = strings,
                        onSessionReady = {
                            quotaVm.refresh()
                            nav.navigate("home") { popUpTo("join") { inclusive = true } }
                        },
                        onToggleLang = {
                            lang = AppStrings.next(lang)
                            scope.launch { store.setLang(lang) }
                        },
                    )
                }
                composable("home") {
                    val vm = remember { HomeViewModel(LoadChannelsUseCase(repo)) }
                    LaunchedEffect(Unit) {
                        quotaVm.refreshNow()
                        while (isActive) {
                            delay(30_000)
                            quotaVm.refreshNow()
                        }
                    }
                    val label = formatQuotaLabel(
                        quota,
                        strings.timeLeft,
                        strings.timeOver,
                    )
                    HomeScreen(
                        viewModel = vm,
                        strings = strings,
                        quotaLabel = label,
                        quota = quota,
                        onChannelClick = { channel ->
                            if (quota?.canWatch != false) {
                                nav.navigate("videos/${channel.id}")
                            }
                        },
                    )
                }
                composable(
                    "videos/{channelId}",
                    arguments = listOf(navArgument("channelId") { type = NavType.StringType }),
                ) { entry ->
                    val channelId = entry.arguments?.getString("channelId").orEmpty()
                    val vm = remember(channelId) {
                        VideosViewModel(LoadVideosUseCase(repo), channelId)
                    }
                    val context = LocalContext.current
                    val videoScope = rememberCoroutineScope()
                    LaunchedEffect(channelId) {
                        quotaVm.refreshNow()
                        if (quotaVm.quota.value?.canWatch == false) {
                            nav.popBackStack("home", inclusive = false)
                            return@LaunchedEffect
                        }
                        while (isActive) {
                            delay(30_000)
                            quotaVm.refreshNow()
                            if (quotaVm.quota.value?.canWatch == false) {
                                nav.popBackStack("home", inclusive = false)
                                return@LaunchedEffect
                            }
                        }
                    }
                    VideosScreen(
                        viewModel = vm,
                        strings = strings,
                        onVideoClick = { video ->
                            videoScope.launch {
                                quotaVm.refreshNow()
                                if (quotaVm.quota.value?.canWatch != false) {
                                    context.startActivity(
                                        PlayerActivity.intent(context, video.videoId),
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
