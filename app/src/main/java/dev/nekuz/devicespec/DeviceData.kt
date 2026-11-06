package dev.nekuz.devicespec

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference
import java.util.EnumMap
import java.util.concurrent.Executors

object DeviceData {
    private const val TIMEOUT_MS = 15_000L

    private class State {
        var running = false
        var pending: WeakReference<Context>? = null
    }

    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newCachedThreadPool { task ->
        Thread(task, "devicespec-collect").apply { isDaemon = true }
    }
    private val states = EnumMap<SectionId, State>(SectionId::class.java).apply {
        SectionId.entries.forEach { put(it, State()) }
    }
    private val listeners = LinkedHashSet<() -> Unit>()

    var sections: Map<SectionId, Section> = emptyMap()
        private set

    val allCollected: Boolean get() = sections.size == SectionId.entries.size

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun refreshAll(context: Context) {
        SectionId.entries.forEach { refresh(context, it) }
    }

    fun refresh(context: Context, id: SectionId) {
        if (!id.refreshOnReturn && sections[id] != null) return
        val state = states.getValue(id)
        val target = if (id == SectionId.DISPLAY) context else context.applicationContext
        if (state.running) {
            state.pending = WeakReference(target)
            return
        }
        start(id, state, target)
    }

    private fun start(id: SectionId, state: State, context: Context) {
        state.running = true
        state.pending = null
        val timeout = Runnable { if (sections[id] == null) publish(Section.failed(id)) }
        main.postDelayed(timeout, TIMEOUT_MS)
        try {
            executor.execute {
                val section = try {
                    Collectors.collect(id, context)
                } catch (t: Throwable) {
                    Section.failed(id)
                }
                main.post {
                    main.removeCallbacks(timeout)
                    finish(id, state, section, context)
                }
            }
        } catch (t: Throwable) {
            main.removeCallbacks(timeout)
            state.running = false
            publish(Section.failed(id))
        }
    }

    private fun finish(id: SectionId, state: State, section: Section, context: Context) {
        state.running = false
        publish(section)
        val pending = state.pending ?: return
        state.pending = null
        start(id, state, pending.get() ?: context.applicationContext)
    }

    private fun publish(section: Section) {
        sections = sections + (section.id to section)
        listeners.toList().forEach { it() }
    }
}
