package com.kangle.kardleaf.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kangle.kardleaf.data.repository.PrefsManager

private val PasswordCardShape = RoundedCornerShape(22.dp)
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
    autoShowBiometric: Boolean,
    onBack: (() -> Unit)? = null,
    passwordInputMode: PrefsManager.PasswordInputMode = PrefsManager.PasswordInputMode.COMPLEX,
) {
    var autoPrompted by remember(autoShowBiometric) { mutableStateOf(false) }

    LaunchedEffect(autoShowBiometric) {
        if (autoShowBiometric && !autoPrompted) {
            autoPrompted = true
            onBiometricUnlock()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PasswordTopBar(title = screenTitle, onBack = onBack)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                PasswordLockCard(
                    headline = headline,
                    description = description,
                    passwordLabel = passwordLabel,
                    password = password,
                    onPasswordChange = onPasswordChange,
                    primaryButtonText = primaryButtonText,
                    onPasswordSubmit = onPasswordSubmit,
                    onSimplePasswordComplete = onSimplePasswordComplete,
                    errorMessage = errorMessage,
                    biometricAvailable = biometricAvailable,
                    onBiometricUnlock = onBiometricUnlock,
                    passwordInputMode = passwordInputMode,
                )
            }
        }
    }
}

@Composable
private fun PasswordTopBar(
    title: String,
    onBack: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack == null) {
            Box(
                modifier = Modifier
                    .padding(start = 18.dp, end = 6.dp)
                    .size(38.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(start = 18.dp, end = 6.dp)
                    .size(38.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PasswordLockCard(
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
    passwordInputMode: PrefsManager.PasswordInputMode,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, PasswordCardShape, clip = false),
        shape = PasswordCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, top = 24.dp, end = 18.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(23.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = headline,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 21.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(22.dp))
            if (passwordInputMode == PrefsManager.PasswordInputMode.SIMPLE) {
                SimplePasswordInput(
                    value = password,
                    onValueChange = onPasswordChange,
                    onComplete = { completed ->
                        onSimplePasswordComplete?.invoke(completed) ?: onPasswordSubmit()
                    },
                )
            } else {
                PasswordInputField(
                    label = passwordLabel,
                    value = password,
                    onValueChange = onPasswordChange,
                    onSubmit = onPasswordSubmit,
                )
            }
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (passwordInputMode != PrefsManager.PasswordInputMode.SIMPLE) {
                Button(
                    onClick = onPasswordSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(top = 14.dp),
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
            }
            if (biometricAvailable) {
                TextButton(
                    onClick = onBiometricUnlock,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(top = 6.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(
                        text = "使用指纹解锁",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SimplePasswordInput(
    value: String,
    onValueChange: (String) -> Unit,
    onComplete: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            val dotCount = 4
            repeat(dotCount) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 5.dp)
                        .size(12.dp)
                        .background(
                            if (index < value.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(50),
                        ),
                )
            }
        }
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "⌫"),
        ).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { key ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        when {
                            key.isBlank() -> Spacer(modifier = Modifier.size(58.dp))
                            key == "⌫" -> SimplePasswordKey(onClick = { onValueChange(value.dropLast(1)) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Backspace,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            else -> SimplePasswordKey(onClick = {
                                val next = (value + key).take(4)
                                onValueChange(next)
                                if (next.length == 4) {
                                    onComplete(next)
                                }
                            }) {
                                Text(
                                    text = key,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimplePasswordKey(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(58.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun PasswordInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp,
            letterSpacing = 3.sp,
        ),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(MaterialTheme.colorScheme.surface, PasswordFieldShape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), PasswordFieldShape),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    innerTextField()
                }
                Icon(
                    imageVector = Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(23.dp),
                )
            }
        },
    )
}
