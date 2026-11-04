package com.kiara.ai.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiara.ai.domain.Calculator
import com.kiara.ai.viewmodel.MainViewModel
import java.text.DateFormat
import java.util.Date

private val Tabs = listOf("Notes", "Reminders", "Calculator", "Translate", "Summarize", "Daily plan")
private val TargetLangs = listOf("English", "Hindi", "Gujarati", "Spanish", "French", "German")

@Composable
fun ToolsScreen(vm: MainViewModel) {
    val pal = LocalPalette.current
    var tab by remember { mutableStateOf("Notes") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Smart tools", fontSize = 20.sp, color = pal.primary)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tabs.forEach { FilterChip(selected = tab == it, onClick = { tab = it }, label = { Text(it) }) }
        }
        when (tab) {
            "Notes" -> NotesTab(vm)
            "Reminders" -> RemindersTab(vm)
            "Calculator" -> CalculatorTab()
            else -> AiToolTab(vm, tab)
        }
    }
}

@Composable
private fun NotesTab(vm: MainViewModel) {
    val notes by vm.notes.collectAsState()
    var text by remember { mutableStateOf("") }
    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("New note") }, minLines = 2)
    Button(onClick = { vm.addNote(text); text = "" }, enabled = text.isNotBlank()) { Text("Save note") }
    if (notes.isEmpty()) Text("No notes yet. You can also say \"note: buy milk\" to Kiara.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    notes.forEach { n ->
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(n.text)
                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(n.time)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.deleteNote(n.id) }) { Icon(Icons.Filled.Delete, "Delete note") }
            }
        }
    }
}

@Composable
private fun RemindersTab(vm: MainViewModel) {
    val reminders by vm.reminders.collectAsState()
    var text by remember { mutableStateOf("") }
    var mins by remember { mutableStateOf("10") }
    LaunchedEffect(Unit) { vm.refreshReminders() }
    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("Remind me to…") })
    OutlinedTextField(
        mins, { mins = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("In how many minutes?") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    Button(onClick = { vm.addReminder(mins.toLongOrNull() ?: 0, text); text = "" }) { Text("Set reminder") }
    Text("Reminders are lost if the phone restarts before they fire. Allow notifications for Kiara to see them.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val upcoming = reminders.filter { it.at > System.currentTimeMillis() }
    if (upcoming.isEmpty()) Text("No upcoming reminders.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    upcoming.forEach { r ->
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.text)
                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(r.at)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.deleteReminder(r) }) { Icon(Icons.Filled.Delete, "Cancel reminder") }
            }
        }
    }
}

@Composable
private fun CalculatorTab() {
    var expr by remember { mutableStateOf("") }
    val result = remember(expr) {
        if (expr.isBlank()) "" else try { Calculator.format(Calculator.eval(expr)) } catch (e: Exception) { "…" }
    }
    OutlinedTextField(expr, { expr = it }, Modifier.fillMaxWidth(), label = { Text("e.g. (12+8)*3/4 or sqrt(81)") })
    Text(if (result.isEmpty()) "" else "= $result", fontSize = 28.sp, color = LocalPalette.current.primary)
}

@Composable
private fun AiToolTab(vm: MainViewModel, kind: String) {
    val busy by vm.toolBusy.collectAsState()
    val out by vm.toolOutput.collectAsState()
    var input by remember(kind) { mutableStateOf("") }
    var target by remember { mutableStateOf("Hindi") }
    val name = when (kind) { "Translate" -> "Translate"; "Summarize" -> "Summarize"; else -> "Plan my day" }
    if (kind == "Translate") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TargetLangs.forEach { FilterChip(selected = target == it, onClick = { target = it }, label = { Text(it) }) }
        }
    }
    OutlinedTextField(
        input, { input = it }, Modifier.fillMaxWidth(), minLines = 4,
        label = { Text(when (kind) { "Translate" -> "Text to translate"; "Summarize" -> "Text to summarize"; else -> "Your tasks, goals and constraints for today" }) }
    )
    Button(onClick = { vm.runTool(kind, input, target) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Working…" else name) }
    if (out.isNotBlank()) GlassCard(Modifier.fillMaxWidth()) { SelectionContainer { Text(out) } }
}
