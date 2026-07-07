package com.gamelaunch.frontend.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamelaunch.frontend.R
import com.gamelaunch.frontend.ui.theme.BrandBlue
import com.gamelaunch.frontend.ui.theme.ElectricBlue
import com.gamelaunch.frontend.ui.theme.IceWhite
import com.gamelaunch.frontend.ui.theme.NavyBg

/**
 * In-app loading screen shown while the app warms its first screen's artwork. Rendered by the app
 * itself (not just the system splash) so the loading state is guaranteed to appear even when the OS
 * skips the per-app splash — e.g. when eOr is the device's home launcher and starts on cold boot.
 */
@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().background(NavyBg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_donkey_silhouette),
                contentDescription = null,
                tint = BrandBlue,
                modifier = Modifier.size(48.dp).padding(end = 8.dp)
            )
            Text("e", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = BrandBlue, letterSpacing = 3.sp)
            Text("Or", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = IceWhite, letterSpacing = 3.sp)
        }
        Spacer(Modifier.height(28.dp))
        CircularProgressIndicator(color = ElectricBlue, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
    }
}
