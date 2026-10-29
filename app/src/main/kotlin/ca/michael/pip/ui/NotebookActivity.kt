package ca.michael.pip.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import ca.michael.pip.R
import ca.michael.pip.core.notebook.EntryKind
import ca.michael.pip.core.notebook.NotebookEntry
import ca.michael.pip.pip
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** S6 — Notebook: view, add, edit, delete one, erase all (FR-5.3). */
class NotebookActivity : Activity() {
    private lateinit var column: LinearLayout
    private val shown = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
    private val typed = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        column = Ui.screen(this, R.string.notebook_title)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        column.removeAllViews()
        column.addView(Ui.button(this, getString(R.string.add), Ui.Style.PRIMARY) { edit(null) })
        val entries = pip.notebook.all()
        if (entries.isEmpty()) column.addView(Ui.text(this, getString(R.string.notebook_empty)))
        val now = Instant.now()
        for (e in entries.sortedByDescending { it.createdAt }) {
            column.addView(Ui.spacer(this, 8))
            val due = e.dueAt?.let {
                if (e.isOverdue(now)) getString(R.string.overdue, shown.format(it)) else getString(R.string.due_on, shown.format(it))
            }
            column.addView(Ui.text(this, kindLabel(e.kind) + " · " + shown.format(e.createdAt) + if (e.done) " · ✓" else "", 13f, bold = true))
            column.addView(Ui.text(this, e.text))
            due?.let { column.addView(Ui.text(this, it, 13f)) }
            column.addView(Ui.row(this,
                Ui.button(this, getString(if (e.done) R.string.mark_undone else R.string.mark_done)) { pip.notebook.upsert(e.copy(done = !e.done)); render() },
                Ui.button(this, getString(R.string.edit)) { edit(e) },
                Ui.button(this, getString(R.string.delete)) { pip.notebook.delete(e.id); render() },
            ))
        }
        if (entries.isNotEmpty()) {
            column.addView(Ui.spacer(this, 24))
            column.addView(Ui.button(this, getString(R.string.erase_all), Ui.Style.DANGER) {
                AlertDialog.Builder(this)
                    .setMessage(R.string.erase_all_confirm)
                    .setPositiveButton(R.string.erase_all) { _, _ ->
                        pip.notebook.eraseAll()
                        Toast.makeText(this, R.string.erased, Toast.LENGTH_SHORT).show()
                        render()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            })
        }
    }

    private fun kindLabel(k: EntryKind) = getString(when (k) {
        EntryKind.NOTE -> R.string.kind_note
        EntryKind.PROMISE -> R.string.kind_promise
        EntryKind.REMINDER -> R.string.kind_reminder
    })

    private fun edit(existing: NotebookEntry?) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(this@NotebookActivity, 20); setPadding(p, p, p, 0)
        }
        val kinds = EntryKind.entries
        val group = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            kinds.forEachIndexed { i, k -> addView(RadioButton(context).apply { text = kindLabel(k); id = i + 1 }) }
            check(kinds.indexOf(existing?.kind ?: EntryKind.NOTE) + 1)
        }
        val text = EditText(this).apply { setHint(R.string.entry_text_hint); setText(existing?.text ?: "") }
        val due = EditText(this).apply {
            setHint(R.string.entry_due_hint)
            setText(existing?.dueAt?.let { shown.format(it) } ?: "")
        }
        form.addView(group); form.addView(text); form.addView(due)

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.add else R.string.edit)
            .setView(form)
            .setPositiveButton(R.string.save) { _, _ ->
                val body = text.text.toString().trim()
                if (body.isEmpty()) return@setPositiveButton
                val dueAt = parseDue(due.text.toString()) ?: run {
                    if (due.text.isNotBlank()) { Toast.makeText(this, R.string.due_invalid, Toast.LENGTH_LONG).show(); return@setPositiveButton }
                    null
                }
                val kind = kinds[group.checkedRadioButtonId - 1]
                pip.notebook.upsert(
                    existing?.copy(kind = kind, text = body, dueAt = dueAt)
                        ?: NotebookEntry(kind = kind, text = body, createdAt = Instant.now(), dueAt = dueAt)
                )
                render()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun parseDue(raw: String): Instant? {
        if (raw.isBlank()) return null
        return try {
            LocalDateTime.parse(raw.trim(), typed).atZone(ZoneId.systemDefault()).toInstant()
        } catch (e: DateTimeParseException) {
            null
        }
    }
}
