package dev.nekuz.devicespec

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

sealed class Row {
    data class Header(val text: String) : Row()
    data class SectionRow(val id: SectionId, val subtitle: String, val tag: String?) : Row()
    data class EntryRow(val section: SectionId?, val entry: Entry, val tappable: Boolean) : Row()
}

object RowBuilder {
    fun entries(section: Section, tappable: (Entry) -> Boolean): List<Row> = buildList {
        var group = ""
        for (entry in section.entries) {
            if (entry.group != group) {
                group = entry.group
                if (group.isNotEmpty()) add(Row.Header(group))
            }
            add(Row.EntryRow(section.id, entry, tappable(entry)))
        }
    }
}

class RowAdapter(context: Context) : BaseAdapter() {
    var rows: List<Row> = emptyList()
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    private val inflater = LayoutInflater.from(context)

    override fun getCount() = rows.size
    override fun getItem(position: Int) = rows[position]
    override fun getItemId(position: Int) = position.toLong()
    override fun getViewTypeCount() = 3
    override fun areAllItemsEnabled() = false
    override fun isEnabled(position: Int) = rows.getOrNull(position) !is Row.Header
    override fun getItemViewType(position: Int) = when (rows[position]) {
        is Row.Header -> 0
        is Row.SectionRow -> 1
        is Row.EntryRow -> 2
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = when (val row = rows[position]) {
        is Row.Header -> {
            val view = convertView ?: inflater.inflate(R.layout.row_header, parent, false)
            (view as TextView).text = row.text
            view
        }
        is Row.SectionRow -> {
            val view = convertView ?: inflater.inflate(R.layout.row_section, parent, false)
            view.findViewById<TextView>(R.id.letter).text = row.id.title.take(1)
            view.findViewById<TextView>(R.id.title).text = row.id.title
            view.findViewById<TextView>(R.id.subtitle).text = row.subtitle
            view.findViewById<TextView>(R.id.tag).apply {
                visibility = if (row.tag == null) View.GONE else View.VISIBLE
                text = row.tag
            }
            view
        }
        is Row.EntryRow -> {
            val view = convertView ?: inflater.inflate(R.layout.row_entry, parent, false)
            view.findViewById<TextView>(R.id.label).apply {
                text = row.entry.label
                visibility = if (row.entry.label.isEmpty()) View.GONE else View.VISIBLE
            }
            view.findViewById<TextView>(R.id.value).text = row.entry.value
            view.findViewById<View>(R.id.chevron).visibility = if (row.tappable) View.VISIBLE else View.GONE
            view
        }
    }
}
