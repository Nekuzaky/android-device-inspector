package dev.nekuz.devicespec

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import java.io.File

object StorageCollector {
    private const val MAX_VOLUMES = 16

    fun collect(context: Context): Section {
        val b = SectionBuilder(SectionId.STORAGE)
        val internal = Text.attempt { StatFs(Environment.getDataDirectory().path) }
        b.group("Internal")
        b.add("Total") { internal?.let { Sizes.bytes(it.totalBytes) } }
        b.add("Free") { internal?.let { Sizes.bytes(it.freeBytes) } }
        b.add("Available to apps") { internal?.let { Sizes.bytes(it.availableBytes) } }
        b.add("Encryption state") {
            val policy = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            policy?.let { Labels.encryptionStatus(it.storageEncryptionStatus) }
        }

        b.group("External volumes")
        b.guarded("Volumes") {
            val manager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val volumes = manager.storageVolumes
            if (volumes.isEmpty()) b.add("Volumes") { "None" }
            volumes.take(MAX_VOLUMES).forEachIndexed { index, volume ->
                b.add("Volume ${index + 1}") { describe(context, volume) }
            }
        }
        return b.build()
    }

    private fun describe(context: Context, volume: StorageVolume): String {
        val parts = ArrayList<String>()
        parts.add(Text.attempt { volume.getDescription(context) } ?: Text.UNAVAILABLE)
        Text.attempt { if (volume.isPrimary) "Primary" else "Secondary" }?.let(parts::add)
        Text.attempt { if (volume.isEmulated) "Emulated" else "Physical" }?.let(parts::add)
        Text.attempt { if (volume.isRemovable) "Removable" else "Not removable" }?.let(parts::add)
        parts.add("State: ${Text.attempt { volume.state } ?: Text.UNAVAILABLE}")
        val stat = Text.attempt { volumeStat(volume) }
        parts.add("Total: ${stat?.let { Sizes.bytes(it.totalBytes) } ?: Text.UNAVAILABLE}")
        parts.add("Free: ${stat?.let { Sizes.bytes(it.availableBytes) } ?: Text.UNAVAILABLE}")
        return parts.joinToString(" · ")
    }

    private fun volumeStat(volume: StorageVolume): StatFs? {
        val directory: File? = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> volume.directory
            volume.isPrimary -> primaryDirectory()
            else -> null
        }
        return directory?.let { StatFs(it.path) }
    }

    @Suppress("DEPRECATION")
    private fun primaryDirectory(): File? = Environment.getExternalStorageDirectory()
}
