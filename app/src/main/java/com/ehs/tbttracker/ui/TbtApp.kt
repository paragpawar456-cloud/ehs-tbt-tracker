package com.ehs.tbttracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.ehs.tbttracker.ui.form.TbtFormViewModel
import com.ehs.tbttracker.ui.portal.PortalNav
import com.ehs.tbttracker.ui.portal.PortalRoute
import com.ehs.tbttracker.ui.form.TbtFormRoute
import com.ehs.tbttracker.ui.theme.Ehs
import com.ehs.tbttracker.ui.portal.Portal
import com.ehs.tbttracker.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

object Routes {
    const val DASHBOARD = "portal"
    const val NEW_TBT = "new_tbt?date={date}&contractor={contractor}"

    fun newTbt(date: java.time.LocalDate? = null, contractor: String? = null): String =
        "new_tbt?date=${date ?: ""}&contractor=${android.net.Uri.encode(contractor ?: "")}"
}

/**
 * "TBT Safety Compliance Portal": tabbed portal (contractor-wise audit, date-wise, missing audit,
 * monthly grid, analytics, master contractors, all records) with "Record New TBT" opening the form.
 */
@Composable
fun TbtApp() {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Portal.colors.bg,
    ) { padding ->
        NavHost(nav, startDestination = Routes.DASHBOARD, modifier = Modifier.padding(padding)) {
            composable(Routes.DASHBOARD) {
                PortalRoute(
                    snackbar,
                    PortalNav(
                        onRecordNew = { nav.navigate(Routes.newTbt()) { launchSingleTop = true } },
                        onLogTbt = { date, contractor -> nav.navigate(Routes.newTbt(date, contractor)) { launchSingleTop = true } },
                    ),
                )
            }
            composable(
                Routes.NEW_TBT,
                arguments = listOf(
                    navArgument(TbtFormViewModel.ARG_DATE) { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument(TbtFormViewModel.ARG_CONTRACTOR) { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) {
                Column(Modifier.fillMaxSize().background(Portal.colors.bg)) {
                    FormTopBar(onBack = { nav.popBackStack() })
                    Box(Modifier.weight(1f).navigationBarsPadding()) {
                        TbtFormRoute(
                            snackbar,
                            onSubmitted = { msg ->
                                nav.popBackStack(Routes.DASHBOARD, inclusive = false)
                                scope.launch { snackbar.showSnackbar(msg) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormTopBar(onBack: () -> Unit) {
    val c = Portal.colors
    Row(
        Modifier.fillMaxWidth().background(c.surface).statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp).testTag("form_top_bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to portal", tint = c.ink) }
        Image(painterResource(R.drawable.ic_tbt_logo), null, Modifier.size(36.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text("Record New TBT", color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text("Saved to Form Responses 1", color = c.muted, fontSize = 12.sp)
        }
    }
}
