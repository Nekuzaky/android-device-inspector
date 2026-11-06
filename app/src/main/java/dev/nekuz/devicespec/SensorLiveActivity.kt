package dev.nekuz.devicespec

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ListView
import android.widget.TextView

class SensorLiveActivity : Activity(), SensorEventListener {

    private lateinit var adapter: RowAdapter
    private var manager: SensorManager? = null
    private var sensor: Sensor? = null
    private var details: List<Row> = emptyList()
    private var unit = ""
    private var registered = false
    private val handler = Handler(Looper.getMainLooper())
    private var latest: FloatArray? = null
    private val drawLatest = Runnable { latest?.let(::draw) }
    private var readings = 0L
    private var accuracy: Int? = null
    private var status = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sensor)
        Screen.applyInsets(findViewById(R.id.root))
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val type = intent.getIntExtra(EXTRA_TYPE, Int.MIN_VALUE)
        val wakeUp = intent.getBooleanExtra(EXTRA_WAKE_UP, false)
        findViewById<TextView>(R.id.title).text = Text.clean(name, Limits.MAX_LABEL_CHARS)

        adapter = RowAdapter(this)
        val list = findViewById<ListView>(R.id.list)
        list.adapter = adapter
        list.setOnItemLongClickListener { _, _, position, _ ->
            val row = adapter.rows[position]
            if (row is Row.EntryRow) {
                Screen.copyValue(this, row.entry.label, row.entry.value)
                true
            } else {
                false
            }
        }

        manager = Text.attempt { getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
        sensor = Text.attempt { SensorCollector.find(this, type, name, wakeUp) }
        val selected = sensor
        if (selected == null) {
            status = getString(R.string.sensor_not_found)
        } else {
            unit = Labels.sensorUnit(Text.attempt { selected.stringType })
            findViewById<TextView>(R.id.subtitle).text =
                Text.clean(Text.attempt { selected.stringType }.orEmpty(), Limits.MAX_LABEL_CHARS)
            details = listOf(
                Row.Header(getString(R.string.sensor_details)),
                Row.EntryRow(null, Entry("", Text.guard { SensorCollector.describe(selected) }), false),
            )
        }
        drawStatus()
    }

    override fun onStart() {
        super.onStart()
        val selected = sensor ?: return
        registered = Text.attempt { manager?.registerListener(this, selected, SensorManager.SENSOR_DELAY_UI) } == true
        status = getString(if (registered) R.string.sensor_waiting else R.string.sensor_not_started)
        drawStatus()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(drawLatest)
        Text.quietly { manager?.unregisterListener(this) }
        registered = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val values = event?.values ?: return
        readings++
        latest = values.copyOf()
        if (!handler.hasCallbacks(drawLatest)) handler.postDelayed(drawLatest, DRAW_INTERVAL_MS)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        this.accuracy = accuracy
    }

    private fun draw(values: FloatArray) {
        adapter.rows = buildList {
            add(Row.Header(getString(R.string.sensor_values)))
            Labels.sensorValues(values, unit).forEach { (label, value) ->
                add(Row.EntryRow(null, Entry(label, value), false))
            }
            accuracy?.let { add(Row.EntryRow(null, Entry(getString(R.string.sensor_accuracy), Labels.sensorAccuracy(it)), false)) }
            add(Row.EntryRow(null, Entry(getString(R.string.sensor_events), readings.toString()), false))
            addAll(details)
        }
    }

    private fun drawStatus() {
        adapter.rows = buildList {
            add(Row.Header(getString(R.string.sensor_values)))
            add(Row.EntryRow(null, Entry("", status), false))
            addAll(details)
        }
    }

    companion object {
        const val EXTRA_TYPE = "type"
        const val EXTRA_NAME = "name"
        const val EXTRA_WAKE_UP = "wake_up"
        private const val DRAW_INTERVAL_MS = 200L
    }
}
