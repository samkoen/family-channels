package com.familychannels.feature.join

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familychannels.domain.model.ChildProfile
import com.familychannels.ui.components.AppBackground
import com.familychannels.ui.components.ProfileAvatar
import com.familychannels.ui.i18n.Strings
import com.familychannels.ui.theme.Teal

@Composable
fun JoinScreen(
    viewModel: JoinViewModel,
    strings: Strings,
    onSessionReady: () -> Unit,
    onToggleLang: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.sessionReady) {
        if (state.sessionReady) onSessionReady()
    }
    AppBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(strings.appName, style = MaterialTheme.typography.displayLarge)
                TextButton(onClick = onToggleLang) {
                    Text(strings.language, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.children.isEmpty()) {
                JoinCodeForm(state, strings, viewModel)
            } else {
                ProfilePicker(state.children, strings, viewModel)
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun JoinCodeForm(
    state: JoinUiState,
    strings: Strings,
    viewModel: JoinViewModel,
) {
    Text(strings.joinHint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.code,
        onValueChange = viewModel::onCodeChange,
        label = { Text(strings.enterCode) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Teal,
            cursorColor = Teal,
            focusedLabelColor = Teal,
        ),
    )
    Button(
        onClick = viewModel::submitCode,
        enabled = !state.loading,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Teal),
    ) {
        Text(strings.continueLabel)
    }
}

@Composable
private fun ProfilePicker(
    children: List<ChildProfile>,
    strings: Strings,
    viewModel: JoinViewModel,
) {
    Text(strings.chooseProfile, style = MaterialTheme.typography.headlineLarge)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(children) { child ->
            OutlinedButton(
                onClick = { viewModel.selectChild(child.id) },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ProfileAvatar(child.name, child.avatarColor, size = 40.dp)
                    Text(child.name, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}
