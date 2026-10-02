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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.astroxplore.core.ui.components.DynamicIslandError
import kotlinx.coroutines.flow.collectLatest

@Composable
fun SignupScreen(
    onSignupSuccess: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignupViewModel = hiltViewModel()
) {
    val form by viewModel.formState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLegalNotice by remember { mutableStateOf(false) }
    val needsEmailConfirmation = (uiState as? SignupUiState.Success)?.needsEmailConfirmation == true
    val showSuccessSheet = uiState is SignupUiState.Success && !needsEmailConfirmation

    LaunchedEffect(viewModel) {
        viewModel.messages.collectLatest { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { insets ->
        if (needsEmailConfirmation) {
            Column(modifier = Modifier.fillMaxSize().padding(insets)) {
                EmailConfirmationView(
                    email = form.email,
                    onNavigateToLogin = onNavigateToLogin
                )
            }
        } else {
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
                    title = "Create your account",
                    subtitle = "Provide your full name, email, and password to create your account and get started.",
                    onBack = onNavigateBack
                )

                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SocialAuthPillButton(isGoogle = true, isSignUp = true, modifier = Modifier.weight(1f))
                    SocialAuthPillButton(isGoogle = false, isSignUp = true, modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(12.dp))
                AuthOrDivider()
                Spacer(modifier = Modifier.height(18.dp))

                AstroAuthTextField(
                    label = "Full Name",
                    value = form.fullName,
                    onValueChange = viewModel::updateFullName,
                    placeholder = "Sanjay Kushwaha",
                    imeAction = ImeAction.Next
                )
                Spacer(modifier = Modifier.height(16.dp))
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = form.termsAccepted,
                        onCheckedChange = viewModel::setTermsAccepted
                    )
                    TextButton(onClick = { showLegalNotice = true }) {
                        Text(
                            "I agree to the Terms & Privacy Policy",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                PrimaryAuthButton(
                    text = "Sign Up",
                    onClick = viewModel::signupFromForm,
                    enabled = form.termsAccepted &&
                        form.fullName.isNotBlank() &&
                        form.email.isNotBlank() &&
                        form.password.isNotBlank() &&
                        uiState !is SignupUiState.Loading,
                    loading = uiState is SignupUiState.Loading
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Already have an account? ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onNavigateToLogin) {
                        Text("Sign In", fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        DynamicIslandError(
            isVisible = uiState is SignupUiState.Error,
            message = if (uiState is SignupUiState.Error) {
                androidx.compose.ui.res.stringResource((uiState as SignupUiState.Error).messageResId)
            } else {
                ""
            }
        )
    }

    if (showLegalNotice) {
        AuthLegalNoticeDialog(onDismiss = { showLegalNotice = false })
    }
    if (showSuccessSheet) {
        AuthSuccessBottomSheet(
            onBrowseHome = {
                viewModel.dismissResult()
                onSignupSuccess()
            },
            onDismiss = {
                viewModel.dismissResult()
                onSignupSuccess()
            }
        )
    }
}

@Composable
fun EmailConfirmationView(
    email: String,
    onNavigateToLogin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MarkEmailRead,
            contentDescription = null,
            modifier = Modifier.height(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Check Your Email",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "We've sent a verification link to:\n$email",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        PrimaryAuthButton(
            text = "Back to Sign In",
            onClick = onNavigateToLogin,
            enabled = true
        )
    }
}
