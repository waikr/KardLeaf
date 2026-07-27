package com.kangle.kardleaf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kangle.kardleaf.data.repository.PrefsManager

private val PasswordFieldShape = RoundedCornerShape(16.dp)

@Composable
fun PasswordLockCardScreen(
    screenTitle: String,
    headline: String,
    description: String,
    passwordLabel: String,
    password: String,
    onPasswordChange: (String) -> Unit,
    primaryButtonText: String,
    onPasswordSubmit: () -> Unit,
    onSimplePasswordComplete: ((String) -> Unit)? = null,
    errorMessage: String?,
    biometricAvailable: Boolean,
    onBiometricUnlock: () -> Unit,
    onBack: (() -> Unit)? = null,
    passwordInputMode: PrefsManager.PasswordInputMode = PrefsManager.PasswordInputMode.COMPLEX,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            if (onBack != null) {
                PasswordTopBar(title = screenTitle, onBack = onBack)
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = if (onBack == null) 44.dp else 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (onBack == null) {
                    Text(
                        text = screenTitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(26.dp))
                }
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = headline,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 23.sp,
                    lineHeight = 29.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(7.dp))
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))

                if (passwordInputMode == PrefsManager.PasswordInputMode.SIMPLE) {
                    SimplePasswordInput(
                        value = password,
                        onValueChange = onPasswordChange,
                        onComplete = { completed ->
                            onSimplePasswordComplete?.invoke(completed) ?: onPasswordSubmit()
                        },
                        biometricAvailable = biometricAvailable,
                        onBiometricUnlock = onBiometricUnlock,
                    )
                } else {
                    ComplexPasswordInput(
                        label = passwordLabel,
                        value = password,
                        onValueChange = onPasswordChange,
                        primaryButtonText = primaryButtonText,
                        onSubmit = onPasswordSubmit,
                        biometricAvailable = biometricAvailable,
                        onBiometricUnlock = onBiometricUnlock,
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordTopBar(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Outlined.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SimplePasswordInput(
    value: String,
    onValueChange: (String) -> Unit,
    onComplete: (String) -> Unit,
    biometricAvailable: Boolean,
    onBiometricUnlock: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .background(
                            color = if (index < value.length) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = CircleShape,
                        ),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
        ).forEach { row ->
            PasswordKeyRow {
                row.forEach { key ->
                    PasswordNumberKey(key) {
                        val next = (value + key).take(4)
                        onValueChange(next)
                        if (next.length == 4) onComplete(next)
                    }
                }
            }
        }
        PasswordKeyRow {
            if (biometricAvailable) {
                PasswordIconKey(
                    contentDescription = "使用指纹解锁",
                    onClick = onBiometricUnlock,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Fingerprint,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(68.dp))
            }
            PasswordNumberKey("0") {
                val next = (value + "0").take(4)
                onValueChange(next)
                if (next.length == 4) onComplete(next)
            }
            PasswordIconKey(
                contentDescription = "删除",
                onClick = { onValueChange(value.dropLast(1)) },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Backspace,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(27.dp),
                )
            }
        }
    }
}

@Composable
private fun PasswordKeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun PasswordNumberKey(
    number: String,
    onClick: () -> Unit,
) {
    PasswordIconKey(contentDescription = number, onClick = onClick) {
        Text(
            text = number,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 27.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PasswordIconKey(
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(68.dp)
            .semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun ComplexPasswordInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    primaryButtonText: String,
    onSubmit: () -> Unit,
    biometricAvailable: Boolean,
    onBiometricUnlock: () -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                    )
                }
            },
            shape = PasswordFieldShape,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .height(50.dp),
            shape = PasswordFieldShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        ) {
            Text(
                text = primaryButtonText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (biometricAvailable) {
            TextButton(
                onClick = onBiometricUnlock,
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                )
                Text(
                    text = "使用指纹解锁",
                    modifier = Modifier.padding(start = 8.dp),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
