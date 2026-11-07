package com.gomobile.luna.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(busy: Boolean, onSignIn: (String, String) -> Unit, onReset: (String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    CenteredColumn {
        Icon(Icons.Filled.BarChart, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Text("GoMobile Sales", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Sign in to see the ST, SO, stock and aging of your shops.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSignIn(email, password) }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onSignIn(email, password) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            if (busy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp)) else Text("Sign in")
        }
        TextButton(onClick = { onReset(email) }) { Text("Forgot password?") }
    }
}

/** Shown when the account signed in but the admin has not linked it to a name yet. */
@Composable
fun NoProfileScreen(uid: String, onRetry: () -> Unit, onSignOut: () -> Unit) {
    CenteredColumn {
        Text("Account not set up yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Your sign-in works, but the admin still needs to link your account to your name in the sales sheet. Send them this ID:",
            textAlign = TextAlign.Center,
        )
        Text(uid, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
    }
}

@Composable
fun NotConfiguredScreen() {
    CenteredColumn {
        Text("Firebase is not configured", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "This build has no app/google-services.json. Download it from your Firebase project settings, put it in the app folder and rebuild. See README.md.",
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun LoadingScreen() {
    CenteredColumn { CircularProgressIndicator() }
}

@Composable
private fun CenteredColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}
