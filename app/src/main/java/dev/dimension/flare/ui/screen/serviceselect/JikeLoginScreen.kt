package dev.dimension.flare.ui.screen.serviceselect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.login.JikeLoginPresenter
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun JikeLoginScreen(toHome: () -> Unit) {
    val state by producePresenter { presenter(toHome) }
    var phoneNumber by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }

    FlareScaffold {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(it)
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Jike Login",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            if (state.error != null) {
                Text(
                    text = state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Phone Number") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )

            if (state.smsSent) {
                OutlinedTextField(
                    value = smsCode,
                    onValueChange = { smsCode = it },
                    label = { Text("SMS Code") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )

                Button(
                    onClick = { state.loginWithSmsCode(phoneNumber, smsCode) },
                    enabled = !state.loading && smsCode.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    } else {
                        Text("Login")
                    }
                }
            } else {
                Button(
                    onClick = { state.sendSmsCode(phoneNumber) },
                    enabled = !state.loading && phoneNumber.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    } else {
                        Text("Send SMS Code")
                    }
                }
            }

            Button(
                onClick = { state.clear() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear")
            }
        }
    }
}

@Composable
private fun presenter(toHome: () -> Unit) =
    run {
        remember { JikeLoginPresenter(toHome) }.invoke()
    }
