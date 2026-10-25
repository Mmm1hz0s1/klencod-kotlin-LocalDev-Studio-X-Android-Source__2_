package com.localdev.studiox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localdev.studiox.core.engine.ServiceState
import com.localdev.studiox.ui.theme.DevAccentCyan
import com.localdev.studiox.ui.theme.DevError
import com.localdev.studiox.ui.theme.DevSuccess
import com.localdev.studiox.ui.theme.DevSurfacePrimary
import com.localdev.studiox.ui.theme.DevSurfaceSecondary
import com.localdev.studiox.ui.theme.DevTextPrimary
import com.localdev.studiox.ui.theme.DevTextSecondary
import com.localdev.studiox.ui.theme.DevWarning

@Composable
fun ServiceStatusBadge(state: ServiceState) {
    val (bgColor, textColor) = when (state) {
        ServiceState.RUNNING -> DevSuccess.copy(alpha = 0.18f) to DevSuccess
        ServiceState.STARTING, ServiceState.STOPPING -> DevWarning.copy(alpha = 0.18f) to DevWarning
        ServiceState.ERROR -> DevError.copy(alpha = 0.18f) to DevError
        ServiceState.STOPPED -> DevSurfaceSecondary to DevTextSecondary
    }
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(999.dp))
            .border(1.dp, textColor.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = state.name,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun DevPanelCard(
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DevSurfacePrimary, RoundedCornerShape(10.dp))
            .border(1.dp, DevSurfaceSecondary, RoundedCornerShape(10.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = DevAccentCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = DevTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            if (trailing != null) {
                trailing()
            }
        }
        content()
    }
}

@Composable
fun KeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = DevTextSecondary, fontSize = 13.sp)
        Text(
            text = value,
            color = DevTextPrimary,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}
