package com.triguide.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triguide.ai.memory.PersonConsentStore

/**
 * Opt-in names. The camera loop does not call this screen.
 * Confirm stores a name only. No face photo is taken here.
 */
@Composable
fun PersonConsentSection(store: PersonConsentStore) {
    val snap by store.snapshot.collectAsStateWithLifecycle()
    var typedName by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "People you choose to remember",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            snap.statusLine,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            modifier = Modifier.semantics { contentDescription = snap.statusLine },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Remember people",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = "Remember people. Off until you turn it on. The camera does not save strangers."
                    },
            )
            Switch(
                checked = snap.featureEnabled,
                onCheckedChange = store::setFeatureEnabled,
                modifier = Modifier.semantics {
                    contentDescription = if (snap.featureEnabled) {
                        "Remember people, on"
                    } else {
                        "Remember people, off"
                    }
                },
            )
        }
        OutlinedTextField(
            value = typedName,
            onValueChange = { typedName = it.take(40) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Name to save, after you confirm on this screen" },
            label = { Text("Name") },
            singleLine = true,
        )
        Button(
            onClick = {
                store.requestSave(typedName, android.os.SystemClock.elapsedRealtime())
            },
            enabled = snap.featureEnabled && typedName.trim().length >= 2,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .semantics {
                    contentDescription = "Prepare to save this person. Nothing is stored until you confirm."
                },
        ) {
            Text("Prepare save", fontSize = 18.sp)
        }
        Button(
            onClick = { store.confirmPending(android.os.SystemClock.elapsedRealtime()) },
            enabled = snap.featureEnabled && snap.pendingName != null,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .semantics {
                    contentDescription = if (snap.pendingName == null) {
                        "Confirm save. No person is waiting."
                    } else {
                        "Confirm save this person as ${snap.pendingName}. No photo is stored."
                    }
                },
        ) {
            Text(
                if (snap.pendingName == null) "Confirm save" else "Confirm save ${snap.pendingName}",
                fontSize = 18.sp,
            )
        }
        OutlinedButton(
            onClick = { store.cancelPending() },
            enabled = snap.pendingName != null,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .semantics { contentDescription = "Cancel the save. Nothing new is stored." },
        ) {
            Text("Cancel", fontSize = 18.sp)
        }
        if (snap.people.isEmpty()) {
            Text(
                "No saved names.",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
            )
        }
        snap.people.forEach { person ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${person.name}. Name only. No photo stored.",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    modifier = Modifier.semantics {
                        contentDescription = "${person.name}. Name only. No photo stored."
                    },
                )
                OutlinedButton(
                    onClick = { store.delete(person.name) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(top = 4.dp)
                        .semantics { contentDescription = "Delete ${person.name}" },
                ) {
                    Text("Delete ${person.name}", fontSize = 18.sp)
                }
            }
        }
    }
}
