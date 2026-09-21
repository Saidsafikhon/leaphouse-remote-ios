package uz.electro.remote.ui

import uz.electro.remote.i18n.S
import uz.electro.remote.ui.components.Lx
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import uz.electro.remote.data.Cmd
import uz.electro.remote.data.ClimateScheduleDto
import uz.electro.remote.data.ClimateScheduleRequest
import uz.electro.remote.ui.components.*
import uz.electro.remote.ui.theme.*

/**
 * Расписание пред-климата: включить климат к нужному часу.
 *
 * Будильник живёт на сервере, а не здесь: приложение закрывают сразу после
 * того, как поставили время, и таймер в телефоне умер бы ровно к утру, когда
 * он нужен. Тут — только редактор.
 */
private val DAY_LABELS: List<String> get() = listOf(S("Пн"), S("Вт"), S("Ср"), S("Чт"), S("Пт"), S("Сб"), S("Вс"))  // геттер: язык может смениться

@Composable
fun ScheduleScreen(
    schedules: List<ClimateScheduleDto>,
    onCreate: (ClimateScheduleRequest) -> Unit,
    onToggle: (ClimateScheduleDto, Boolean) -> Unit,
    onDelete: (ClimateScheduleDto) -> Unit,
    onBack: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }

    ScreenScaffold(S("Климат по расписанию"), onBack) {
        if (schedules.isEmpty() && !adding) {
            EmptyNote(S("Расписаний нет. Задайте время — сервер прогреет или остудит салон к нему."))
        }
        schedules.forEach { s ->
            ScheduleRow(s, onToggle = { on -> onToggle(s, on) }, onDelete = { onDelete(s) })
        }

        if (adding) {
            ScheduleEditor(
                onCancel = { adding = false },
                onSave = { req -> onCreate(req); adding = false },
            )
        } else {
            ElectroButton(S("Новое расписание"), Modifier.fillMaxWidth(), style = ButtonStyle.Secondary) {
                adding = true
            }
        }
    }
}

@Composable
private fun ScheduleRow(
    s: ClimateScheduleDto, onToggle: (Boolean) -> Unit, onDelete: () -> Unit,
) {
    SectionCard("%02d:%02d".format(s.hour, s.minute)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (s.weekdays.isEmpty()) S("Каждый день")
                    else s.weekdays.sorted().joinToString(" ") { DAY_LABELS[it] },
                    style = ElectroType.Body, color = ElectroColors.TextPrimary,
                )
                Text(S("до {0}°", s.temp_c), style = ElectroType.Caption, color = ElectroColors.TextMuted)
            }
            Switch(
                checked = s.enabled, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = ElectroColors.Accent,
                    checkedThumbColor = ElectroColors.OnAccent,
                ),
            )
            Spacer(Modifier.width(Space.x2))
            Icon(Lx.Delete, null, tint = ElectroColors.TextMuted,
                modifier = Modifier.size(22.dp).clickable(onClick = onDelete))
        }
    }
}

@Composable
private fun ScheduleEditor(
    onCancel: () -> Unit,
    onSave: (ClimateScheduleRequest) -> Unit,
) {
    var hour by remember { mutableStateOf(8) }
    var minute by remember { mutableStateOf(0) }
    var temp by remember { mutableStateOf(22) }
    val days = remember { mutableStateListOf<Int>() }

    SectionCard(S("Новое расписание")) {
        // Время: часы и минуты крупными ± — попасть пальцем в поле ввода в
        // машине неудобнее, чем нажать плюс.
        StepperRow(S("Час"), "%02d".format(hour)) { hour = ((hour + it) % 24 + 24) % 24 }
        StepperRow(S("Минуты"), "%02d".format(minute)) { minute = ((minute + it * 5) % 60 + 60) % 60 }
        StepperRow(S("Температура"), "$temp°") { temp = (temp + it).coerceIn(Cmd.TEMP_MIN, Cmd.TEMP_MAX) }

        Text(S("Дни"), style = ElectroType.Caption, color = ElectroColors.TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DAY_LABELS.forEachIndexed { i, label ->
                val on = days.contains(i)
                ControlChip(label, Modifier.weight(1f), selected = on) {
                    if (on) days.remove(i) else days.add(i)
                }
            }
        }
        Text(
            if (days.isEmpty()) S("Ни один день не выбран — сработает каждый день") else "",
            style = ElectroType.Caption, color = ElectroColors.TextMuted,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            ElectroButton(S("Отмена"), Modifier.weight(1f), style = ButtonStyle.Ghost, onClick = onCancel)
            ElectroButton(S("Сохранить"), Modifier.weight(1f), style = ButtonStyle.Primary) {
                onSave(ClimateScheduleRequest(
                    hour = hour, minute = minute, weekdays = days.sorted().toList(),
                    temp_c = temp, enabled = true,
                ))
            }
        }
    }
}

@Composable
private fun StepperRow(label: String, value: String, onStep: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = ElectroType.Body, color = ElectroColors.TextPrimary,
            modifier = Modifier.weight(1f))
        ControlChip("−", Modifier.width(52.dp)) { onStep(-1) }
        Text(value, style = ElectroType.Value, color = ElectroColors.Accent,
            modifier = Modifier.padding(horizontal = Space.x3))
        ControlChip("+", Modifier.width(52.dp)) { onStep(1) }
    }
}
