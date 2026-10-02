package com.example.astroxplore.features.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.astroxplore.core.ui.components.DynamicIslandError
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LoginScreen(
    onLoginSuccess: (Boolean) -> Unit,
    onNavigateToSignup: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    viewModel: LoginViewModel = hiltViewModel()
) {
    val form by viewModel.formState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onLoginSuccess((uiState as LoginUiState.Success).isOnboarded)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collectLatest { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            AuthHeader(
                title = "Welcome Back",
                subtitle = "Stay connected by signing in with your email and password to access your account.",
                onBack = onNavigateBack
            )

            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SocialAuthPillButton(isGoogle = true, isSignUp = false, modifier = Modifier.weight(1f))
                SocialAuthPillButton(isGoogle = false, isSignUp = false, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(12.dp))
            AuthOrDivider()
            Spacer(modifier = Modifier.height(18.dp))

            AstroAuthTextField(
                label = "Email Address",
                value = form.email,
                onValueChange = viewModel::updateEmail,
                placeholder = "name@example.com",
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )
            Spacer(modifier = Modifier.height(16.dp))
            AstroAuthTextField(
                label = "Password",
                value = form.password,
                onValueChange = viewModel::updatePassword,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { }),
                visualTransformation = if (form.passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = viewModel::togglePasswordVisibility) {
                        Icon(
                            imageVector = if (form.passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (form.passwordVisible) "Hide password" else "Show password"
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = form.rememberMe, onCheckedChange = { viewModel.toggleRememberMe() })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Remember me", style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = viewModel::requestPasswordReset) {
                    Text("Forgot Password?", color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            PrimaryAuthButton(
                text = "Sign In",
                onClick = { viewModel.login(form.email.trim(), form.password) },
                enabled = isOnline &&
                    uiState !is LoginUiState.Loading &&
                    form.email.isNotBlank() && form.password.isNotBlank(),
                loading = uiState is LoginUiState.Loading
            )

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Don't have an account? ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onNavigateToSignup) {
                    Text("Sign Up", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        DynamicIslandError(
            isVisible = uiState is LoginUiState.Error,
            message = if (uiState is LoginUiState.Error) {
                androidx.compose.ui.res.stringResource((uiState as LoginUiState.Error).messageResId)
            } else {
                ""
            }
        )
    }
}
