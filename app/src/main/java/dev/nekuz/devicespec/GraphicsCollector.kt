package dev.nekuz.devicespec

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.GLES20

data class GlInfo(
    val version: String?,
    val renderer: String?,
    val vendor: String?,
    val extensions: List<String>,
)

object GlProbe {
    private const val EGL_OPENGL_ES3_BIT_VALUE = 0x40

    fun read(): GlInfo? {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == null || display == EGL14.EGL_NO_DISPLAY) return null
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null
        var context: EGLContext? = null
        var surface = EGL14.EGL_NO_SURFACE
        try {
            val config = chooseConfig(display, EGL_OPENGL_ES3_BIT_VALUE)
                ?: chooseConfig(display, EGL14.EGL_OPENGL_ES2_BIT)
                ?: return null
            context = createContext(display, config, 3) ?: createContext(display, config, 2) ?: return null
            val size = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            surface = EGL14.eglCreatePbufferSurface(display, config, size, 0)
            if (surface == null || surface == EGL14.EGL_NO_SURFACE) return null
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return null
            return GlInfo(
                Text.attempt { GLES20.glGetString(GLES20.GL_VERSION) },
                Text.attempt { GLES20.glGetString(GLES20.GL_RENDERER) },
                Text.attempt { GLES20.glGetString(GLES20.GL_VENDOR) },
                Text.attempt { GLES20.glGetString(GLES20.GL_EXTENSIONS) }
                    ?.split(' ')
                    ?.filter { it.isNotBlank() }
                    ?.distinct()
                    ?.sorted()
                    .orEmpty(),
            )
        } finally {
            Text.quietly {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            }
            Text.quietly {
                if (surface != null && surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            }
            Text.quietly { context?.let { EGL14.eglDestroyContext(display, it) } }
            Text.quietly { EGL14.eglReleaseThread() }
        }
    }

    private fun chooseConfig(display: EGLDisplay, renderable: Int): EGLConfig? {
        val attributes = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, renderable,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val found = EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0)
        return if (found && count[0] > 0) configs[0] else null
    }

    private fun createContext(display: EGLDisplay, config: EGLConfig, clientVersion: Int): EGLContext? {
        val attributes = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, clientVersion, EGL14.EGL_NONE)
        val context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, attributes, 0)
        return context?.takeIf { it != EGL14.EGL_NO_CONTEXT }
    }
}

object GraphicsCollector {
    @SuppressLint("InlinedApi")
    fun collect(context: Context): Section {
        val b = SectionBuilder(SectionId.GRAPHICS)
        val gl = Text.attempt { GlProbe.read() }
        val features = Collectors.readFeatures(context)

        b.group("OpenGL ES")
        b.add("Version") { gl?.version }
        b.add("Renderer") { gl?.renderer }
        b.add("Vendor") { gl?.vendor }
        b.add("Version declared by the system") {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            manager?.deviceConfigurationInfo?.glEsVersion
        }

        b.group("Vulkan")
        b.add("Hardware level") { featureValue(features, PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL) { it.toString() } }
        b.add("Hardware version") { featureValue(features, PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, Labels::vulkanVersion) }
        b.add("Compute level") { featureValue(features, PackageManager.FEATURE_VULKAN_HARDWARE_COMPUTE) { it.toString() } }
        b.add("dEQP level") { featureValue(features, PackageManager.FEATURE_VULKAN_DEQP_LEVEL, Labels::deqpLevel) }

        val graphics = features
            .mapNotNull { info -> Text.attempt { info.name?.takeIf(Labels::isGraphicsFeature)?.let { it to info.version } } }
            .sortedBy { it.first }
        b.group("Hardware features (${graphics.size})")
        if (graphics.isEmpty()) b.add("Graphics features") { "None declared" }
        graphics.forEach { (name, version) -> b.add("") { Labels.featureText(name, version) } }

        val extensions = gl?.extensions.orEmpty()
        b.group("OpenGL ES extensions (${extensions.size})")
        if (extensions.isEmpty()) b.add("Extensions") { null }
        extensions.forEach { name -> b.add("") { name } }
        return b.build()
    }

    private fun featureValue(features: List<FeatureInfo>, name: String, format: (Int) -> String): String? {
        if (features.isEmpty()) return null
        val feature = features.firstOrNull { it.name == name } ?: return "Not declared"
        return format(feature.version)
    }
}
