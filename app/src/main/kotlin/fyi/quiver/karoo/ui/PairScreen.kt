package fyi.quiver.karoo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PairScreen(vm: PairViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Quiver", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)

            if (state.paired) {
                Text(
                    "Connected to your Quiver garage. Rides sync automatically when you finish.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = vm::syncNow, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Sync garage now")
                }
                OutlinedButton(onClick = vm::unpair, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Disconnect")
                }
            } else {
                Text(
                    "On quiver.fyi, open Settings → Connect your head unit to get a pairing code, then enter it here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                var code by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Pairing code") },
                    singleLine = true,
                    enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { vm.pair(code) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Pair")
                }
            }

            if (state.busy) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 4.dp))
            }
            state.message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Start)
            }
            if (!state.connected) {
                Text(
                    "Waiting for the Karoo system…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
        }
    }
}
