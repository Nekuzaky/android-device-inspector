package dev.nekuz.devicespec

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var hero: View
    private lateinit var header: View
    private lateinit var heroTitle: TextView
    private lateinit var heroSubtitle: TextView
    private lateinit var heroDetail: TextView
    private lateinit var share: Button
    private lateinit var search: EditText
    private lateinit var empty: TextView
    private lateinit var adapter: RowAdapter
    private lateinit var displayWatcher: DisplayWatcher

    private val listener: () -> Unit = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Screen.applyInsets(findViewById(R.id.root))
        hero = findViewById(R.id.heroCard)
        hero.clipToOutline = true
        header = findViewById(R.id.headerRow)
        heroTitle = findViewById(R.id.heroTitle)
        heroSubtitle = findViewById(R.id.heroSubtitle)
        heroDetail = findViewById(R.id.heroDetail)
        share = findViewById(R.id.shareButton)
        search = findViewById(R.id.search)
        empty = findViewById(R.id.empty)

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
        share.setOnClickListener { if (DeviceData.allCollected) Screen.shareReport(this) }
        findViewById<View>(R.id.infoButton).setOnClickListener { showInfo() }
        Screen.onTextChanged(search) { render() }
        displayWatcher = DisplayWatcher(this) { DeviceData.refresh(this, SectionId.DISPLAY) }
    }

    override fun onStart() {
        super.onStart()
        DeviceData.addListener(listener)
        displayWatcher.start()
        DeviceData.refreshAll(this)
        render()
    }

    override fun onStop() {
        super.onStop()
        displayWatcher.stop()
        DeviceData.removeListener(listener)
    }

    private fun render() {
        val sections = DeviceData.sections
        val query = search.text.toString()
        val searching = SearchFilter.tokens(query).isNotEmpty()
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        hero.visibility = if (landscape || searching) View.GONE else View.VISIBLE
        header.visibility = if (landscape) View.GONE else View.VISIBLE
        renderHero(sections)
        share.isEnabled = DeviceData.allCollected
        share.setText(if (DeviceData.allCollected) R.string.share_report else R.string.share_waiting)
        val rows = if (searching) searchRows(sections, query) else sectionRows(sections)
        adapter.rows = rows
        empty.visibility = if (searching && rows.isEmpty()) View.VISIBLE else View.GONE
        empty.setText(R.string.empty_search)
    }

    private fun renderHero(sections: Map<SectionId, Section>) {
        val build = sections[SectionId.BUILD]
        val total = SectionId.entries.size
        heroDetail.text = if (DeviceData.allCollected) {
            getString(R.string.hero_ready, total)
        } else {
            getString(R.string.hero_progress, sections.size, total)
        }
        if (build == null) {
            heroTitle.text = getString(R.string.hero_loading)
            heroSubtitle.text = null
            return
        }
        heroTitle.text = valueOf(build, "Model")
        heroSubtitle.text = getString(
            R.string.hero_detail,
            valueOf(build, "Android version"),
            valueOf(build, "API level"),
        )
    }

    private fun valueOf(section: Section, label: String): String =
        section.entries.firstOrNull { it.label == label }?.value ?: Text.UNAVAILABLE

    private fun sectionRows(sections: Map<SectionId, Section>): List<Row> = SectionId.entries.map { id ->
        val section = sections[id]
        Row.SectionRow(
            id,
            id.summary,
            section?.entries?.size?.toString() ?: getString(R.string.section_loading),
        )
    }

    private fun searchRows(sections: Map<SectionId, Section>, query: String): List<Row> {
        val ordered = SectionId.entries.mapNotNull { sections[it] }
        return SearchFilter.filter(ordered, query).flatMap { section ->
            buildList {
                add(Row.Header(section.id.title))
                section.entries.forEach { add(Row.EntryRow(section.id, it, true)) }
            }
        }
    }

    private fun onRowClick(row: Row) {
        val id = when (row) {
            is Row.SectionRow -> row.id
            is Row.EntryRow -> row.section
            is Row.Header -> null
        } ?: return
        startActivity(
            Intent(this, SectionActivity::class.java).putExtra(SectionActivity.EXTRA_SECTION, id.name)
        )
    }

    private fun showInfo() {
        AlertDialog.Builder(this)
            .setTitle(R.string.info)
            .setMessage(getString(R.string.info_message, Screen.appVersion(this)))
            .setPositiveButton(R.string.close, null)
            .show()
    }
}
