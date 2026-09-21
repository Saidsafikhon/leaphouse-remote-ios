package uz.electro.remote.ui

import uz.electro.remote.i18n.S
import uz.electro.remote.ui.components.Lx
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uz.electro.remote.data.VoiceIntentDto
import uz.electro.remote.ui.components.*
import uz.electro.remote.ui.theme.*

/**
 * Голосовые намерения штатного ассистента головы.
 *
 * Не распознавание речи на телефоне: список готовых намерений, каждое голова
 * разворачивает в свой набор сигналов. Подпись — фразой ассистента: «включи
 * кондиционер» человеку понятнее, чем ac_on.
 */
@Composable
fun VoiceScreen(
    intents: List<VoiceIntentDto>,
    onRun: (VoiceIntentDto) -> Unit,
    onBack: () -> Unit,
) {
    ScreenScaffold(S("Голосовые команды"), onBack) {
        if (intents.isEmpty()) {
            EmptyNote(S("У этой машины голосовых команд нет."))
            return@ScreenScaffold
        }
        SectionCard(S("Скажите или нажмите")) {
            intents.forEach { intent ->
                Row(
                    Modifier.fillMaxWidth().clickable { onRun(intent) }
                        .padding(vertical = Space.x2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Lx.RecordVoiceOver, null,
                        tint = ElectroColors.Accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(Space.x3))
                    Column(Modifier.weight(1f)) {
                        Text(
                            intent.phrases.firstOrNull()?.replaceFirstChar { it.uppercase() }
                                ?: intent.intent,
                            style = ElectroType.Body, color = ElectroColors.TextPrimary,
                        )
                        if (intent.phrases.size > 1) {
                            Text(
                                intent.phrases.drop(1).joinToString(" · "),
                                style = ElectroType.Caption, color = ElectroColors.TextMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- общие блоки экранов второго уровня ----------------------------------

@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(Space.x4), verticalAlignment = Alignment.CenterVertically) {
            Icon(Lx.ArrowBack, null, tint = ElectroColors.TextPrimary,
                modifier = Modifier.size(26.dp).clickable(onClick = onBack))
            Spacer(Modifier.width(Space.x3))
            Text(title, style = ElectroType.Headline, color = ElectroColors.TextPrimary)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = Space.x4).padding(bottom = Space.x5),
            verticalArrangement = Arrangement.spacedBy(Space.x4),
            content = content,
        )
    }
}

@Composable
fun EmptyNote(text: String) {
    Text(text, style = ElectroType.Body, color = ElectroColors.TextMuted,
        modifier = Modifier.padding(Space.x4))
}
