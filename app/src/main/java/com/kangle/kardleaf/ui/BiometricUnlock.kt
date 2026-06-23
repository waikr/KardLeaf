package com.kangle.kardleaf.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

fun isBiometricUnlockAvailable(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
        BiometricManager.BIOMETRIC_SUCCESS

fun showBiometricUnlockPrompt(
    context: Context,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit = {},
) {
    val activity = context as? FragmentActivity
    if (activity == null) {
        onError("当前页面不支持指纹解锁")
        return
    }
    if (!isBiometricUnlockAvailable(context)) {
        onError("当前设备未检测到可用指纹")
        return
    }

    val prompt =
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    onError("指纹验证失败")
                }
            },
        )

    val promptInfo =
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("使用密码")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
    prompt.authenticate(promptInfo)
}
