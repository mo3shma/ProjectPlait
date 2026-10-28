package app.plait.ui.screens

import android.Manifest
import app.plait.R
import app.plait.i18n.userText
import androidx.compose.ui.res.stringResource
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.plait.data.CalEvent
import app.plait.data.tasks.AttentionTasks
import app.plait.data.tasks.TaskIdea
import app.plait.data.tasks.TaskIdeas
import app.plait.data.tasks.TaskSuggester
import app.plait.data.tasks.Tasks
import app.plait.reminder.TaskReminders
import app.plait.ui.ButtonSize
import app.plait.ui.ButtonStyle
import app.plait.ui.PlaitButton
import app.plait.ui.plaitCard
import app.plait.ui.theme.PlaitType
import app.plait.ui.theme.plait

/**
 * Tasks Plait found in this meeting's voice, written and photo notes. Adding one puts it on the meeting's checklist and sets a
 * reminder; dismissing it means it is never suggested again. Shows nothing when there is nothing to suggest.
 */
@Composable
internal fun SuggestedTasksCard(ev: CalEvent) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { TaskIdeas.load(context) }
    val ideas = TaskIdeas.items.filter { it.eventId == ev.id }
    if (ideas.isEmpty()) return
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val add: (List<TaskIdea>) -> Unit = { list ->
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        list.forEach { Tasks.add(context, it, ev.title) }
    }
    val look = LocalMeetingLook.current
    Column(
        Modifier.fillMaxWidth().plaitCard(RoundedCornerShape(30.dp), look?.tint(2) ?: androidx.compose.ui.graphics.Color.Unspecified, look?.backdrop).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.AddTask, null, tint = plait.text, modifier = Modifier.size(22.dp))
            Text(stringResource(R.string.note_tasks_suggested), style = PlaitType.section.copy(fontSize = 20.sp), color = plait.text)
        }
        Text(stringResource(R.string.note_tasks_found), style = PlaitType.caption, color = plait.muted)
        ideas.forEach { idea ->
            val now = remember(idea.id) { System.currentTimeMillis() }
            val dueMs = remember(idea.id) { TaskSuggester.dueMs(idea.due, now) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(idea.text, style = PlaitType.bodyStrong.userText(), color = plait.text, modifier = Modifier.fillMaxWidth())
                    Text(dueMs?.let { AttentionTasks.dueLabel(it, now) } ?: stringResource(R.string.note_tasks_no_due, app.plait.i18n.Bidi.isolate(TaskReminders.label(TaskSuggester.remindAt(idea.due, now)))), style = PlaitType.caption, color = plait.muted)
                }
                PlaitButton(stringResource(R.string.common_add), { add(listOf(idea)) }, style = ButtonStyle.Primary, size = ButtonSize.Small)
                IconButton(onClick = { TaskIdeas.decide(context, idea) }) {
                    Icon(Icons.Filled.Close, stringResource(R.string.common_dismiss), tint = plait.muted)
                }
            }
        }
        if (ideas.size > 1) PlaitButton(stringResource(R.string.note_tasks_add_all, ideas.size), { add(ideas) }, style = ButtonStyle.Secondary, size = ButtonSize.Small)
    }
}
