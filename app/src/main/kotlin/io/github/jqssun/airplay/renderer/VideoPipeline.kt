package io.github.jqssun.airplay.renderer

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

// decodes into an app-owned SurfaceTexture that outlives display surface
// gl thread blits it to any surface attached, so fullscreen toggles re-point display without restarting codec
class VideoPipeline {

    private val lock = Object()
    private var thread: Thread? = null
    @Volatile private var running = false

    private var egl: EglCore? = null
    private var window: EGLSurface = EGL14.EGL_NO_SURFACE
    private var winW = 0
    private var winH = 0

    private var oesTex = 0
    private var program = 0
    private var yuvProgram = 0
    private var activeProgram = 0
    @Volatile var useFullRangeYuv = false
    private var aPos = 0
    private var aTex = 0
    private var uTexMatrix = 0
    private val texMatrix = FloatArray(16)
    private var hasFrame = false

    private var surfaceTexture: SurfaceTexture? = null
    private var frameCallbacks: HandlerThread? = null
    var inputSurface: Surface? = null; private set

    @Volatile private var frameAvailable = false
    private var pendingDisplay: Surface? = null
    private var displayDirty = false
    @Volatile private var videoW = 0
    @Volatile private var videoH = 0

    fun start() = synchronized(lock) {
        if (running) return@synchronized
        running = true
        thread = Thread({ _loop() }, "VideoPipeline").also { it.start() }
        while (inputSurface == null && running) lock.wait()
    }

    fun setDisplaySurface(surface: Surface?) = synchronized(lock) {
        pendingDisplay = surface
        displayDirty = true
        lock.notifyAll()
    }

    fun setVideoSize(w: Int, h: Int) {
        videoW = w
        videoH = h
        surfaceTexture?.setDefaultBufferSize(w, h)
    }

    fun release() {
        synchronized(lock) {
            if (!running) return
            running = false
            lock.notifyAll()
        }
        thread?.join()
        thread = null
    }

    private fun _loop() {
        try {
            egl = EglCore(preferGles3 = true)
            _initGl()
            Matrix.setIdentityM(texMatrix, 0)
            synchronized(lock) {
                inputSurface = Surface(surfaceTexture)
                lock.notifyAll()
            }
            while (true) {
                var newDisplay: Surface? = null
                var displayChanged = false
                var doFrame = false
                synchronized(lock) {
                    while (running && !frameAvailable && !displayDirty) lock.wait()
                    if (running && displayDirty) {
                        newDisplay = pendingDisplay
                        displayChanged = true
                        displayDirty = false
                    }
                    if (running && frameAvailable) {
                        frameAvailable = false
                        doFrame = true
                    }
                }
                if (!running) break
                if (displayChanged) _bindDisplay(newDisplay)
                if (doFrame) _consumeAndDraw()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Video output failed", e)
        } finally {
            _releaseGl()
            synchronized(lock) { running = false; lock.notifyAll() }
        }
    }

    private fun _bindDisplay(surface: Surface?) {
        val egl = egl ?: return
        if (window != EGL14.EGL_NO_SURFACE) {
            egl.makeCurrent()
            egl.destroySurface(window)
            window = EGL14.EGL_NO_SURFACE
        }
        if (surface == null || !surface.isValid) return
        window = egl.windowSurface(surface)
        if (window == EGL14.EGL_NO_SURFACE) {
            Log.w(TAG, "eglCreateWindowSurface failed: ${EGL14.eglGetError()}")
            return
        }
        egl.makeCurrent(window)
        winW = egl.query(window, EGL14.EGL_WIDTH)
        winH = egl.query(window, EGL14.EGL_HEIGHT)
        // an idle source sends no new frames, so repaint last one or new surface stays black
        if (hasFrame) _render()
    }

    private fun _consumeAndDraw() {
        val st = surfaceTexture ?: return
        val egl = egl ?: return
        if (window == EGL14.EGL_NO_SURFACE) {
            // no display: keep consuming so decoder doesn't stall
            egl.makeCurrent()
            st.updateTexImage()
            st.getTransformMatrix(texMatrix)
            hasFrame = true
            return
        }
        egl.makeCurrent(window)
        st.updateTexImage()
        st.getTransformMatrix(texMatrix)
        hasFrame = true
        _render()
    }

    private fun _render() {
        val selected = if (useFullRangeYuv && yuvProgram != 0) yuvProgram else program
        if (activeProgram != selected) {
            activeProgram = selected
            aPos = GLES20.glGetAttribLocation(selected, "aPos")
            aTex = GLES20.glGetAttribLocation(selected, "aTex")
            uTexMatrix = GLES20.glGetUniformLocation(selected, "uTexMatrix")
            Log.i(TAG, "Color conversion: ${if (selected == yuvProgram) "full-range BT.709 YUV" else "driver RGB"}")
        }
        GLES20.glViewport(0, 0, winW, winH)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(selected)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, POS)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, TEX)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
        if (selected == yuvProgram && GLES20.glGetError() != GLES20.GL_NO_ERROR) {
            // Some drivers advertise the extension but reject the decoder's buffer layout.
            GLES20.glDeleteProgram(yuvProgram)
            yuvProgram = 0
            Log.w(TAG, "Raw YUV sampling failed; using compatible RGB output")
            _render()
            return
        }
        egl?.swap(window)
    }

    private fun _initGl() {
        program = _buildProgram()
        if (egl?.clientVersion == 3 && GLES20.glGetString(GLES20.GL_EXTENSIONS)
                ?.split(' ')?.contains("GL_EXT_YUV_target") == true) {
            yuvProgram = runCatching { _buildProgram(VERT_YUV, FRAG_YUV) }
                .onFailure { Log.w(TAG, "Raw YUV shader unavailable; using compatible RGB output", it) }
                .getOrDefault(0)
        }
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aTex = GLES20.glGetAttribLocation(program, "aTex")
        uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix")
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        oesTex = tex[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val callbacks = HandlerThread("VideoFrames").also { it.start() }
        frameCallbacks = callbacks
        surfaceTexture = SurfaceTexture(oesTex).also {
            if (videoW > 0 && videoH > 0) it.setDefaultBufferSize(videoW, videoH)
            it.setOnFrameAvailableListener({
                synchronized(lock) { frameAvailable = true; lock.notifyAll() }
            }, Handler(callbacks.looper))
        }
    }

    private fun _buildProgram(vertex: String = VERT, fragment: String = FRAG): Int {
        val vs = _shader(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = _shader(GLES20.GL_FRAGMENT_SHADER, fragment)
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vs)
            GLES20.glAttachShader(it, fs)
            GLES20.glLinkProgram(it)
            GLES20.glDeleteShader(vs)
            GLES20.glDeleteShader(fs)
            val ok = IntArray(1)
            GLES20.glGetProgramiv(it, GLES20.GL_LINK_STATUS, ok, 0)
            if (ok[0] == 0) {
                val message = GLES20.glGetProgramInfoLog(it)
                GLES20.glDeleteProgram(it)
                error("Shader link failed: $message")
            }
        }
    }

    private fun _shader(type: Int, src: String): Int {
        return GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, src)
            GLES20.glCompileShader(it)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(it, GLES20.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) {
                val message = GLES20.glGetShaderInfoLog(it)
                GLES20.glDeleteShader(it)
                error("Shader compile failed: $message")
            }
        }
    }

    private fun _releaseGl() {
        surfaceTexture?.setOnFrameAvailableListener(null)
        frameCallbacks?.quitSafely()
        frameCallbacks = null
        surfaceTexture?.release()
        surfaceTexture = null
        inputSurface?.release()
        inputSurface = null
        egl?.let {
            if (window != EGL14.EGL_NO_SURFACE) it.destroySurface(window)
            it.close()
        }
        egl = null
        activeProgram = 0
        yuvProgram = 0
        window = EGL14.EGL_NO_SURFACE
        hasFrame = false
        frameAvailable = false
    }

    companion object {
        private const val TAG = "VideoPipeline"

        private val POS = _fb(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        private val TEX = _fb(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))

        private fun _fb(a: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder())
                .asFloatBuffer().apply { put(a); position(0) }

        private const val VERT =
            "attribute vec2 aPos;\n" +
            "attribute vec2 aTex;\n" +
            "uniform mat4 uTexMatrix;\n" +
            "varying vec2 vTex;\n" +
            "void main() {\n" +
            "  gl_Position = vec4(aPos, 0.0, 1.0);\n" +
            "  vTex = (uTexMatrix * vec4(aTex, 0.0, 1.0)).xy;\n" +
            "}\n"

        private const val FRAG =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 vTex;\n" +
            "uniform samplerExternalOES sTex;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(sTex, vTex);\n" +
            "}\n"

        private const val VERT_YUV = """#version 300 es
            in vec2 aPos;
            in vec2 aTex;
            uniform mat4 uTexMatrix;
            out vec2 vTex;
            void main() {
                gl_Position = vec4(aPos, 0.0, 1.0);
                vTex = (uTexMatrix * vec4(aTex, 0.0, 1.0)).xy;
            }
        """

        // Sample before the driver's YUV-to-RGB conversion can clip full-range SDR.
        private const val FRAG_YUV = """#version 300 es
            #extension GL_EXT_YUV_target : require
            precision highp float;
            in vec2 vTex;
            uniform highp __samplerExternal2DY2YEXT sTex;
            out vec4 color;
            void main() {
                vec3 yuv = texture(sTex, vTex).xyz;
                vec2 uv = yuv.yz - vec2(128.0 / 255.0);
                color = vec4(yuv.x + 1.5748 * uv.y,
                    yuv.x - 0.187324 * uv.x - 0.468124 * uv.y,
                    yuv.x + 1.8556 * uv.x, 1.0);
            }
        """
    }
}
