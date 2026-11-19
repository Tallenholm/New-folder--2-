package com.mapledger.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null || action != null) Row(verticalAlignment = Alignment.CenterVertically) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                action?.invoke(this)
            }
            content()
        }
    }
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StatRow(vararg items: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items.forEach { it() } }
}

@Composable
fun Pill(text: String, color: Color, icon: ImageVector? = null) {
    AssistChip(onClick = {}, label = { Text(text, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = icon?.let { { Icon(it, null, Modifier.size(14.dp)) } },
        colors = AssistChipDefaults.assistChipColors(labelColor = color, leadingIconContentColor = color),
        border = AssistChipDefaults.assistChipBorder(true, borderColor = color.copy(alpha = 0.5f)))
}

@Composable
fun Note(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)

@Composable
fun KV(k: String, v: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth()) {
        Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(v, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, suffix: String? = null, decimal: Boolean = true) {
    OutlinedTextField(value = value, onValueChange = { s -> if (s.isEmpty() || s.matches(Regex(if (decimal) "-?[0-9]*[.]?[0-9]*" else "[0-9]*"))) onChange(s) },
        label = { Text(label) }, singleLine = true, modifier = modifier.fillMaxWidth(),
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number))
}

@Composable
fun TextIn(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, singleLine: Boolean = true) =
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = singleLine, modifier = modifier.fillMaxWidth())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, date: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier.fillMaxWidth()) {
        Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text("$label: ${Fmt.iso(date)}")
    }
    if (open) {
        val st = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { open = false }, confirmButton = {
            TextButton(onClick = {
                st.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                open = false
            }) { Text("OK") }
        }, dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }) { DatePicker(st) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Dropdown(label: String, options: List<T>, selected: T, render: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var exp by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = exp, onExpandedChange = { exp = it }, modifier = modifier) {
        OutlinedTextField(value = render(selected), onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(exp) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
        ExposedDropdownMenu(expanded = exp, onDismissRequest = { exp = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(render(o)) }, onClick = { onSelect(o); exp = false }) }
        }
    }
}

@Composable
fun EmptyState(text: String) = Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
