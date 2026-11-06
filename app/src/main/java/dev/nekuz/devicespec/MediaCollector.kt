package dev.nekuz.devicespec

import android.media.MediaCodecInfo
import android.media.MediaCodecList

object MediaCollector {
    fun collect(): Section {
        val b = SectionBuilder(SectionId.MEDIA)
        val codecs = Text.attempt { MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos?.toList() }
        if (codecs.isNullOrEmpty()) {
            b.add("Codecs") { null }
            return b.build()
        }
        val encoders = codecs.filter { Text.attempt { it.isEncoder } == true }
        val decoders = codecs - encoders.toSet()
        b.group("Decoders (${decoders.size})")
        decoders.forEach { add(b, it) }
        b.group("Encoders (${encoders.size})")
        encoders.forEach { add(b, it) }
        return b.build()
    }

    private fun add(b: SectionBuilder, info: MediaCodecInfo) {
        val name = Text.attempt { info.name } ?: Text.UNAVAILABLE
        b.add(name) {
            val types = info.supportedTypes.take(Limits.MAX_CODEC_TYPES).map { describeType(info, it) }
            Labels.codecDetail(
                Text.attempt { info.isEncoder } == true,
                Text.attempt { info.isHardwareAccelerated },
                Text.attempt { info.isAlias },
                types,
            )
        }
    }

    private fun describeType(info: MediaCodecInfo, type: String): String {
        val capabilities = Text.attempt { info.getCapabilitiesForType(type) } ?: return type
        val video = Text.attempt { capabilities.videoCapabilities }
        if (video != null) {
            return Labels.codecVideoType(
                type,
                Text.attempt { video.supportedWidths.upper },
                Text.attempt { video.supportedHeights.upper },
            )
        }
        val audio = Text.attempt { capabilities.audioCapabilities }
        if (audio != null) {
            return Labels.codecAudioType(
                type,
                Text.attempt { audio.supportedSampleRateRanges?.maxOfOrNull { it.upper } },
                Text.attempt { audio.maxInputChannelCount },
            )
        }
        return type
    }
}
