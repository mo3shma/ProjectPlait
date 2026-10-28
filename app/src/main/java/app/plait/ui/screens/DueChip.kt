package app.plait.ui.screens

import android.content.DialogInterface
import app.plait.R
import app.plait.i18n.Txt
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.plait.data.tasks.AttentionTasks
import app.plait.data.tasks.Due
import app.plait.data.tasks.TaskSuggester
import app.plait.ui.fauxGlass
import app.plait.ui.pressable
import app.plait.ui.theme.PlaitType
import app.plait.ui.theme.plait
import java.util.Calendar

/**
 * A task's due date as a small pill: "No due date" (quiet) or "Due tomorrow 5:00 PM", in the signal colour once overdue.
 * Tapping opens the phone's date then time picker (the same ones Reschedule uses) with a Clear button.
 */
@Composable
internal fun DueChip(dueMs: Long?, onChange: (Long?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val overdue = dueMs != null && dueMs < now
    val ink = if (overdue) plait.signal else if (dueMs != null) plait.text else plait.muted
    Row(
        modifier.height(28.dp).fauxGlass(CircleShape, if (overdue) plait.signal.copy(alpha = 0.22f) else Color.Unspecified, strength = 0.6f)
            .pressable({ pickDue(context, dueMs, onChange) }).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(Icons.Filled.Schedule, null, tint = ink, modifier = Modifier.size(14.dp))
        Text(dueMs?.let { AttentionTasks.dueLabel(it, now) } ?: stringResource(R.string.note_due_none), style = PlaitType.caption.copy(fontSize = 12.sp), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun pickDue(context: android.content.Context, current: Long?, onChange: (Long?) -> Unit) {
    val c = Calendar.getInstance().apply { timeInMillis = current ?: TaskSuggester.dueMs(Due.Tomorrow, System.currentTimeMillis())!! }
    val date = android.app.DatePickerDialog(context, { _, y, m, d ->
        android.app.TimePickerDialog(context, { _, h, min ->
            onChange(Calendar.getInstance().apply { set(y, m, d, h, min, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis)
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(context)).show()
    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
    if (current != null) date.setButton(DialogInterface.BUTTON_NEUTRAL, Txt.s(R.string.common_clear)) { _, _ -> onChange(null) }
    date.show()
}
