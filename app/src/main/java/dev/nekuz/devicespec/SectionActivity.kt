package dev.nekuz.devicespec

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView

class SectionActivity : Activity() {

    private lateinit var adapter: RowAdapter
    private lateinit var progress: ProgressBar
    private lateinit var subtitle: TextView
    private lateinit var search: EditText
    private lateinit var empty: TextView
    private var section: SectionId? = null
    private var displayWatcher: DisplayWatcher? = null

    private val listener: () -> Unit = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val selected = intent.getStringExtra(EXTRA_SECTION)?.let { name ->
            SectionId.entries.firstOrNull { it.name == name }
        }
        if (selected == null) {
            finish()
            return
        }
        section = selected
        setContentView(R.layout.activity_section)
        Screen.applyInsets(findViewById(R.id.root))
        findViewById<TextView>(R.id.title).text = selected.title
        subtitle = findViewById(R.id.subtitle)
        progress = findViewById(R.id.progress)
        empty = findViewById(R.id.empty)
        search = findViewById(R.id.search)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

        adapter = RowAdapter(this)
        val list = findViewById<ListView>(R.id.list)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> onRowClick(adapter.rows[position]) }
        list.setOnItemLongClickListener { _, _, position, _ ->
            val row = adapter.rows[position]
            if (row is Row.EntryRow) {
                Screen.copyValue(this, row.entry.label, row.entry.value)
                true
            } else {
                false
            }
        }
        Screen.onTextChanged(search) { render() }
        if (selected == SectionId.DISPLAY) {
            displayWatcher = DisplayWatcher(this) { DeviceData.refresh(this, SectionId.DISPLAY) }
        }
    }

    override fun onStart() {
        super.onStart()
        val selected = section ?: return
        DeviceData.addListener(listener)
        displayWatcher?.start()
        DeviceData.refresh(this, selected)
        render()
    }

    override fun onStop() {
        super.onStop()
        displayWatcher?.stop()
        DeviceData.removeListener(listener)
    }

    private fun render() {
        val selected = section ?: return
        val data = DeviceData.sections[selected]
        progress.visibility = if (data == null) View.VISIBLE else View.GONE
        val count = data?.entries?.size ?: 0
        subtitle.text = resources.getQuantityString(
            if (selected == SectionId.SENSORS) R.plurals.section_subtitle_sensors else R.plurals.section_subtitle,
            count,
            count,
        )
        val filtered = data?.let { SearchFilter.filter(listOf(it), search.text.toString()).firstOrNull() }
        val rows = filtered?.let { RowBuilder.entries(it) { entry -> entry.sensor != null } }.orEmpty()
        adapter.rows = rows
        empty.visibility = if (data != null && rows.isEmpty()) View.VISIBLE else View.GONE
        empty.setText(if (search.text.isNullOrBlank()) R.string.empty_section else R.string.empty_search)
    }

    private fun onRowClick(row: Row) {
        val sensor = (row as? Row.EntryRow)?.entry?.sensor ?: return
        startActivity(
            Intent(this, SensorLiveActivity::class.java)
                .putExtra(SensorLiveActivity.EXTRA_TYPE, sensor.type)
                .putExtra(SensorLiveActivity.EXTRA_NAME, sensor.name)
                .putExtra(SensorLiveActivity.EXTRA_WAKE_UP, sensor.wakeUp)
        )
    }

    companion object {
        const val EXTRA_SECTION = "section"
    }
}
