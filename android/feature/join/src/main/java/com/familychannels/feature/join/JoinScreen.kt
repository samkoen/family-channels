package com.familychannels.feature.join

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familychannels.domain.model.ChildProfile
import com.familychannels.ui.components.AppBackground
import com.familychannels.ui.components.BrandMark
import com.familychannels.ui.components.ChevronGlyph
import com.familychannels.ui.components.ErrorBanner
import com.familychannels.ui.components.LanguageChip
import com.familychannels.ui.components.LoadingPanel
import com.familychannels.ui.components.PrimaryButton
import com.familychannels.ui.components.ProfileAvatar
import com.familychannels.ui.components.ScreenScaffold
import com.familychannels.ui.components.SoftCard
import com.familychannels.ui.i18n.AppStrings.messageForError
import com.familychannels.ui.i18n.Strings
import com.familychannels.ui.theme.Ink
import com.familychannels.ui.theme.Teal
import com.familychannels.ui.theme.TealDark

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
        ScreenScaffold {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandMark(size = 52.dp)
                LanguageChip(
                    label = strings.language,
                    onClick = onToggleLang,
                    enabled = !state.loading,
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                strings.appName,
                style = MaterialTheme.typography.displayMedium,
                color = Ink,
            )
            Text(
                strings.joinHint,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, end = 12.dp),
            )
            Spacer(modifier = Modifier.height(24.dp))
            when {
                state.loading -> {
                    LoadingPanel(
                        title = strings.serverWaking,
                        hint = strings.serverWakingHint,
                    )
                }
                state.children.isEmpty() -> {
                    JoinCodeForm(state, strings, viewModel)
                }
                else -> {
                    ProfilePicker(state.children, strings, viewModel)
                }
            }
            state.error?.let {
                Spacer(modifier = Modifier.height(14.dp))
                ErrorBanner(strings.messageForError(it))
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
    SoftCard(contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                strings.enterCode,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TealDark,
            )
            OutlinedTextField(
                value = state.code,
                onValueChange = viewModel::onCodeChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    letterSpacing = 4.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Teal,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    cursorColor = Teal,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
            PrimaryButton(
                text = strings.continueLabel,
                onClick = viewModel::submitCode,
                enabled = !state.loading && state.code.isNotBlank(),
            )
        }
    }
}

@Composable
private fun ProfilePicker(
    children: List<ChildProfile>,
    strings: Strings,
    viewModel: JoinViewModel,
) {
    var pins by remember { mutableStateOf(mapOf<String, String>()) }
    Text(
        strings.chooseProfile,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(bottom = 14.dp),
    )
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(children) { child ->
            SoftCard(
                onClick = if (child.hasPin) null else ({ viewModel.selectChild(child.id) }),
                contentPadding = PaddingValues(
                    horizontal = 16.dp,
                    vertical = 14.dp,
                ),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        ProfileAvatar(child.name, child.avatarColor, size = 52.dp)
                        Text(
                            child.name,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (!child.hasPin) {
                            ChevronGlyph()
                        }
                    }
                    if (child.hasPin) {
                        OutlinedTextField(
                            value = pins[child.id].orEmpty(),
                            onValueChange = { pins = pins + (child.id to it.filter { ch -> ch.isDigit() }.take(6)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(strings.childPin) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            shape = RoundedCornerShape(16.dp),
                        )
                        PrimaryButton(
                            text = strings.continueLabel,
                            onClick = {
                                viewModel.selectChild(child.id, pins[child.id].orEmpty())
                            },
                            enabled = (pins[child.id]?.length ?: 0) >= 4,
                        )
                    }
                }
            }
        }
    }
}
