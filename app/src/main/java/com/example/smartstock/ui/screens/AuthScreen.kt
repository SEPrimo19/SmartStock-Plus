package com.example.smartstock.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.util.Patterns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.fragment.app.FragmentActivity
import com.example.smartstock.core.auth.BiometricAuth
import com.example.smartstock.ui.navigation.NavGraphEntryPoint
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartstock.R
import com.example.smartstock.ui.InventoryViewModel
import com.example.smartstock.ui.theme.Elevation
import com.example.smartstock.ui.theme.Spacing

@Composable
fun AuthScreen(
    viewModel: InventoryViewModel,
    onLoginSuccess: () -> Unit
) {
    val loginError by viewModel.loginError.collectAsStateWithLifecycle()
    val passwordResetMessage by viewModel.passwordResetMessage.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val loggedInUser by viewModel.loggedInUser.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val appPreferences = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            NavGraphEntryPoint::class.java
        ).appPreferences()
    }
    var biometricError by remember { mutableStateOf<String?>(null) }

    // Biometrics can only resume an already-persisted session — it never
    // authenticates against Supabase. So this fast path appears only when a
    // session is restorable AND this account opted into biometrics on this
    // device (same guarantee as the BiometricGate screen).
    val biometricUserId = loggedInUser?.id.orEmpty()
    val canUseBiometric = isLoggedIn &&
        biometricUserId.isNotBlank() &&
        appPreferences.isBiometricEnabled(biometricUserId) &&
        BiometricAuth.isAvailable(context)

    val launchBiometric: () -> Unit = {
        val activity = context as? FragmentActivity
        if (activity == null) {
            biometricError = "Biometric unlock is unavailable here."
        } else {
            biometricError = null
            BiometricAuth.prompt(
                activity = activity,
                title = "Unlock SmartStock+",
                subtitle = "Confirm it's you to continue",
                onSuccess = { onLoginSuccess() },
                onError = { biometricError = it },
                onCancel = { }
            )
        }
    }

    var isSignUpMode by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var nameTouched by remember { mutableStateOf(false) }
    var emailTouched by remember { mutableStateOf(false) }
    var passwordTouched by remember { mutableStateOf(false) }

    val emailValid = email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    val passwordLongEnough = if (isSignUpMode) password.length >= 6 else password.isNotBlank()
    val nameValid = !isSignUpMode || name.isNotBlank()
    val showNameError = isSignUpMode && nameTouched && name.isBlank()
    val showEmailError = emailTouched && !emailValid
    val showPasswordError = passwordTouched && !passwordLongEnough
    val canSubmit = emailValid && passwordLongEnough && nameValid && !isLoading

    val submit: () -> Unit = {
        focusManager.clearFocus()
        if (canSubmit) {
            isLoading = true
            val onDone: () -> Unit = {
                isLoading = false
                onLoginSuccess()
            }
            if (isSignUpMode) {
                viewModel.signUpAndLogin(name, email, password, onDone)
            } else {
                viewModel.authenticateAndLogin(email, password, onDone)
            }
        } else {
            nameTouched = isSignUpMode
            emailTouched = true
            passwordTouched = true
        }
    }

    // Branded gradient backdrop. The whole screen scrolls and is IME-aware,
    // so focused fields (password especially) are never hidden behind the
    // keyboard — Compose brings the focused field into view within the
    // scroll container once imePadding shrinks the available height.
    val backdrop = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backdrop)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ---- Brand header (on the gradient) ----
            Image(
                painter = painterResource(id = R.drawable.smartstock_logo),
                contentDescription = "SmartStock logo",
                modifier = Modifier.size(92.dp)
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                text = stringResource(id = R.string.app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            // ---- Form card ----
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = Elevation.level4)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Text(
                        text = if (isSignUpMode) "Create your account" else "Welcome back",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isSignUpMode)
                            "Set up your team workspace in seconds."
                        else
                            "Sign in to manage your inventory.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    if (canUseBiometric) {
                        OutlinedButton(
                            onClick = launchBiometric,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.size(Spacing.sm))
                            Text(
                                text = "Sign in with biometrics",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (biometricError != null) {
                            Text(
                                text = biometricError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f))
                            Text(
                                text = "or use your email",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Spacing.md)
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f))
                        }
                    }

                    if (isSignUpMode) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = {
                                nameTouched = true
                                name = it
                                viewModel.clearLoginError()
                            },
                            label = { Text("Full name") },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null)
                            },
                            isError = showNameError,
                            supportingText = {
                                if (showNameError) Text("Name is required")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        )
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            emailTouched = true
                            email = it
                            viewModel.clearLoginError()
                        },
                        label = { Text("Email") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null)
                        },
                        isError = showEmailError,
                        supportingText = {
                            if (showEmailError) {
                                Text(if (email.isBlank()) "Email is required" else "Enter a valid email address")
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            passwordTouched = true
                            password = it
                            viewModel.clearLoginError()
                        },
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        isError = showPasswordError,
                        supportingText = {
                            if (showPasswordError) {
                                Text(
                                    if (isSignUpMode) "Password must be at least 6 characters"
                                    else "Password is required"
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    )

                    if (loginError != null) {
                        Text(
                            text = loginError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        isLoading = false
                    }

                    if (passwordResetMessage != null) {
                        Text(
                            text = passwordResetMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (!isSignUpMode) {
                        TextButton(
                            onClick = {
                                viewModel.clearLoginError()
                                viewModel.requestPasswordReset(email)
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                text = "Forgot password?",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    Button(
                        onClick = submit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = MaterialTheme.shapes.medium,
                        enabled = canSubmit
                    ) {
                        if (isLoading && loginError == null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (isSignUpMode) "Create account" else "Sign in",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.size(Spacing.sm))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text(
                            text = "OR",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.md)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }

                    TextButton(
                        onClick = {
                            isSignUpMode = !isSignUpMode
                            nameTouched = false
                            passwordTouched = false
                            viewModel.clearLoginError()
                            viewModel.clearPasswordResetMessage()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isSignUpMode)
                                "Already have an account? Sign in"
                            else
                                "New here? Create an account",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
