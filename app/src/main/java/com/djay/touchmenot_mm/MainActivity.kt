package com.djay.touchmenot_mm

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.djay.touchmenot_mm.ui.theme.TouchMeNot_MMTheme

class MainActivity : FragmentActivity() {
    private var isAuthenticated by mutableStateOf(false)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PrefsBridge.makeWorldReadable(this)
        
        try {
            val biometricManager = BiometricManager.from(this)
            when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
                BiometricManager.BIOMETRIC_SUCCESS -> {
                    showBiometricPrompt()
                }
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                    // No biometric/security enrolled, allow access
                    isAuthenticated = true
                    setMainContent()
                }
                else -> {
                    isAuthenticated = true
                    setMainContent()
                }
            }
        } catch (e: Exception) {
            // If anything goes wrong, allow access
            isAuthenticated = true
            setMainContent()
        }
    }
    
    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isAuthenticated = true
                    setMainContent()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(this@MainActivity, "Authentication failed", Toast.LENGTH_SHORT).show()
                }
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || 
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED) {
                        finish() // Close app if user cancels
                    } else {
                        Toast.makeText(this@MainActivity, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            })
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authenticate to access TouchMeNot")
            .setSubtitle("Use your biometric credential")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        
        biometricPrompt.authenticate(promptInfo)
    }
    
    private fun setMainContent() {
        setContent {
            TouchMeNot_MMTheme {
                // Use a mutable state that triggers animation when isAuthenticated changes
                var showContent by remember { mutableStateOf(false) }
                
                // Trigger animation when authenticated
                LaunchedEffect(isAuthenticated) {
                    if (isAuthenticated) {
                        showContent = true
                    }
                }
                
                // Animated entry with zoom-in + fade
                AnimatedVisibility(
                    visible = showContent,
                    enter = fadeIn(animationSpec = tween(1000)) + scaleIn(
                        initialScale = 0.85f,
                        animationSpec = tween(1000)
                    ),
                    exit = fadeOut(animationSpec = tween(300)) + scaleOut(
                        targetScale = 0.85f,
                        animationSpec = tween(300)
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        HomeScreen(this@MainActivity)
                    }
                }
            }
        }
    }
}
