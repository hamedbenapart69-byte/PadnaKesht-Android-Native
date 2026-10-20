package com.padnakasht.nativeapp.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.padnakasht.nativeapp.security.PinAuthStore

@Composable
fun PinGate(context: Context, onAuthenticated: () -> Unit) {
    val store = remember(context) { PinAuthStore(context.applicationContext) }
    val setupRequired = remember(store) { !store.isConfigured() }
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("امنیت پادناکِشت", style = MaterialTheme.typography.headlineMedium)
            Text(
                if (setupRequired) "برای شروع یک PIN شش تا دوازده رقمی بسازید."
                else "برای بازکردن دفتر مالی PIN خود را وارد کنید."
            )
            OutlinedTextField(
                value = pin,
                onValueChange = { value -> if (value.length <= 12 && value.all(Char::isDigit)) { pin = value; message = null } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true
            )
            if (setupRequired) {
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { value -> if (value.length <= 12 && value.all(Char::isDigit)) { confirmation = value; message = null } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("تکرار PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val accepted = if (setupRequired) {
                        store.configure(pin.toCharArray(), confirmation.toCharArray())
                    } else {
                        store.verify(pin.toCharArray())
                    }
                    if (accepted) {
                        pin = ""; confirmation = ""; message = null
                        onAuthenticated()
                    } else {
                        message = if (setupRequired) "PIN باید ۶ تا ۱۲ رقم باشد و با تکرار آن مطابقت داشته باشد."
                        else "PIN نادرست است."
                        pin = ""; confirmation = ""
                    }
                }
            ) { Text(if (setupRequired) "ایجاد PIN و ادامه" else "ورود") }
            if (message != null) Text(message!!, color = MaterialTheme.colorScheme.error)
        }
    }
}
