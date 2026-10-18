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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triguide.ai.domain.VoiceConfig
import com.triguide.ai.domain.VoiceLanguagePreference
import com.triguide.ai.domain.VoiceSettings
import java.util.Locale

private const val VOICE_LIMIT =
    "Spoken cues repeat the research decision. They are not a safety proof and do not mean the path is clear."

@Composable
fun VoiceSettingsSection(
    settings: VoiceSettings,
    engineStatus: String,
    lastSpoken: String,
    onSpeechEnabled: (Boolean) -> Unit,
    onQuietMode: (Boolean) -> Unit,
    onSpeechRate: (Float) -> Unit,
    onLanguage: (VoiceLanguagePreference) -> Unit,
    onPlaySample: () -> Unit,
) {
    val rate = settings.sanitized().speechRate
    val rateLabel = String.format(Locale.US, "%.1f", rate)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Voice guidance",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            VOICE_LIMIT,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            modifier = Modifier.semantics { contentDescription = VOICE_LIMIT },
        )
        ToggleRow(
            label = "Speech",
            description = "Speaks obstacle, turn, uncertain, camera, and paused cues.",
            checked = settings.speechEnabled,
            onCheckedChange = onSpeechEnabled,
        )
        ToggleRow(
            label = "Quiet mode",
            description = "Only obstacle detected and camera unavailable.",
            checked = settings.quietMode,
            onCheckedChange = onQuietMode,
        )
        Column(modifier = Modifier.semantics { contentDescription = "Speech rate $rateLabel" }) {
            Text(
                "Speech rate: $rateLabel",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
            )
            Slider(
                value = rate,
                onValueChange = onSpeechRate,
                valueRange = VoiceConfig.RATE_MIN..VoiceConfig.RATE_MAX,
                steps = 9,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Speech rate $rateLabel" },
            )
        }
        Text(
            "Language",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 18.sp,
        )
        Text(
            "Arabic is spoken when this device has an offline Arabic voice. Otherwise the cue stays in English.",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
        )
        VoiceLanguagePreference.entries.forEach { preference ->
            LanguageButton(
                label = preference.label(),
                selected = settings.language == preference,
                onClick = { onLanguage(preference) },
            )
        }
        Text(
            "Voice engine: $engineStatus",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            modifier = Modifier.semantics { contentDescription = "Voice engine: $engineStatus" },
        )
        Text(
            "Last voice cue: $lastSpoken",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            modifier = Modifier.semantics { contentDescription = "Last voice cue: $lastSpoken" },
        )
        Button(
            onClick = onPlaySample,
            enabled = settings.speechEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .semantics {
                    contentDescription = if (settings.speechEnabled) {
                        "Play a voice sample. Research and simulation. This voice is not a safety proof."
                    } else {
                        "Voice sample is off because speech is off."
                    }
                },
        ) {
            Text("Play voice sample", fontSize = 18.sp)
        }
    }
}

@Composable
fun VoiceCueLine(lastSpoken: String) {
    val shown = spokenCueLabel(lastSpoken)
    Text(
        text = "Last voice cue: $shown. $VOICE_LIMIT",
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 16.sp,
        modifier = Modifier.semantics {
            contentDescription = "Last voice cue: $shown. $VOICE_LIMIT"
        },
    )
}

private fun spokenCueLabel(lastSpoken: String): String {
    val trimmed = lastSpoken.trim().trimEnd('.')
    return if (trimmed.isEmpty()) "none" else trimmed
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val state = if (checked) "on" else "off"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Text(label, color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp)
            Text(description, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = "$label, $state. $description" },
        )
    }
}

@Composable
private fun LanguageButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val description = if (selected) "$label, selected" else label
    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .semantics { contentDescription = description },
        ) {
            Text(description, fontSize = 18.sp)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .semantics { contentDescription = description },
        ) {
            Text(label, fontSize = 18.sp)
        }
    }
}

private fun VoiceLanguagePreference.label(): String = when (this) {
    VoiceLanguagePreference.DEVICE -> "Device language"
    VoiceLanguagePreference.ENGLISH -> "English"
    VoiceLanguagePreference.ARABIC -> "Arabic"
}
