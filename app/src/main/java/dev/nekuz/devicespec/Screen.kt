package dev.nekuz.devicespec

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowInsets
import android.widget.EditText
import android.widget.Toast
import java.time.Instant
import java.time.temporal.ChronoUnit

object Screen {
    fun applyInsets(root: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val left = root.paddingLeft
        val top = root.paddingTop
        val right = root.paddingRight
        val bottom = root.paddingBottom
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime(),
            )
            view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
            WindowInsets.CONSUMED
        }
    }

    fun onTextChanged(field: EditText, action: () -> Unit) {
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = action()
        })
    }

    fun copyValue(context: Context, label: String, value: String) {
        val copied = Text.attempt {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label.ifEmpty { context.getString(R.string.app_name) }, value))
            true
        } == true
        if (!copied) {
            Toast.makeText(context, R.string.copy_failed, Toast.LENGTH_SHORT).show()
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
        }
    }

    fun appVersion(context: Context): String =
        Text.attempt { context.packageManager.getPackageInfo(context.packageName, 0).versionName } ?: Text.UNAVAILABLE

    fun shareReport(activity: Activity) {
        val report = ReportBuilder.build(
            DeviceData.sections,
            Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
            appVersion(activity),
        )
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.share_subject))
            .putExtra(Intent.EXTRA_TEXT, report)
        val opened = Text.attempt {
            activity.startActivity(Intent.createChooser(send, activity.getString(R.string.share_title)))
            true
        } == true
        if (!opened) Toast.makeText(activity, R.string.share_failed, Toast.LENGTH_LONG).show()
    }
}

class DisplayWatcher(private val activity: Activity, private val onChanged: () -> Unit) : DisplayManager.DisplayListener {
    private val handler = Handler(Looper.getMainLooper())
    private val notify = Runnable { onChanged() }

    fun start() {
        Text.attempt { manager()?.registerDisplayListener(this, handler) }
    }

    fun stop() {
        handler.removeCallbacks(notify)
        Text.attempt { manager()?.unregisterDisplayListener(this) }
    }

    private fun manager() = activity.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager

    override fun onDisplayChanged(displayId: Int) {
        handler.removeCallbacks(notify)
        handler.postDelayed(notify, DEBOUNCE_MS)
    }

    override fun onDisplayAdded(displayId: Int) = Unit
    override fun onDisplayRemoved(displayId: Int) = Unit

    private companion object {
        const val DEBOUNCE_MS = 200L
    }
}
