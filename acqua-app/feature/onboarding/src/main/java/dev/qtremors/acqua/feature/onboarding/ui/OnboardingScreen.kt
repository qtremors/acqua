package dev.qtremors.acqua.feature.onboarding.ui

import android.net.Uri
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import dev.qtremors.acqua.feature.onboarding.OnboardingRestoreState
import dev.qtremors.acqua.feature.onboarding.OnboardingStep
import dev.qtremors.acqua.feature.onboarding.OnboardingUiState
import dev.qtremors.acqua.ui.theme.ThemeState
import kotlin.math.absoluteValue

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    currentThemeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onStepSelected: (OnboardingStep) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    restoreState: OnboardingRestoreState = OnboardingRestoreState.Idle,
    onChooseRestoreBackup: (Uri) -> Unit = {},
    onApplyRestoreBackup: () -> Unit = {},
    onDismissRestoreBackup: () -> Unit = {},
    onRestartApp: () -> Unit = {}
) {
    val steps = remember {
        arrayOf(
            OnboardingStep.WelcomeAndFeatures,
            OnboardingStep.SetupPermissions
        )
    }
    val pagerState = rememberPagerState(pageCount = { steps.size })

    val restoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let(onChooseRestoreBackup)
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (state.step != OnboardingStep.Done && state.step != steps[page]) {
                onStepSelected(steps[page])
            }
        }
    }

    LaunchedEffect(state.step) {
        val targetStep = if (state.step == OnboardingStep.Done) {
            OnboardingStep.SetupPermissions
        } else {
            state.step
        }
        val targetPage = steps.indexOf(targetStep)
        if (targetPage >= 0 && pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(
                page = targetPage,
                animationSpec = tween(durationMillis = 400)
            )
        }
    }

    PredictiveBackHandler(enabled = pagerState.currentPage > 0) { progressFlow ->
        var completed = false
        try {
            progressFlow.collect { backEvent ->
                pagerState.scrollToPage(page = 0, pageOffsetFraction = 1f - backEvent.progress)
            }
            completed = true
            onBack()
        } finally {
            if (!completed) {
                pagerState.animateScrollToPage(1)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            OnboardingHeader(
                state = state,
                stepsCount = steps.size,
                currentPage = pagerState.currentPage,
                onBack = onBack
            )
        },
        bottomBar = {
            OnboardingBottomBar(
                state = state,
                onNext = onNext
            )
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            beyondViewportPageCount = 1
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                            .absoluteValue
                            .coerceIn(0f, 1f)
                        alpha = 1f - (pageOffset * 0.18f)
                        val scale = 1f - (pageOffset * 0.03f)
                        scaleX = scale
                        scaleY = scale
                    }
            ) {
                when (steps[page]) {
                    OnboardingStep.WelcomeAndFeatures -> OnboardingWelcomeAndFeatures(
                        onChooseRestoreBackup = {
                            restoreFileLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                        }
                    )
                    OnboardingStep.SetupPermissions -> OnboardingSetupPermissions(
                        state = state,
                        currentThemeState = currentThemeState,
                        onThemeChange = onThemeChange,
                        onRequestNotificationPermission = onRequestNotificationPermission
                    )
                    OnboardingStep.Done -> Unit
                }
            }
        }
    }

    OnboardingRestoreDialog(
        state = restoreState,
        onApplyRestoreBackup = onApplyRestoreBackup,
        onDismissRestoreBackup = onDismissRestoreBackup,
        onRestartApp = onRestartApp
    )
}
