package com.vrla.forest

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class VRRenderer(private val context: Context) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private var surfaceTexture: SurfaceTexture? = null
    private var mediaPlayer: MediaPlayer? = null
    private var videoUri: Uri? = null
    private var textureId = 0
    private var program = 0

    private lateinit var sphereVertices: FloatBuffer
    private lateinit var sphereTexCoords: FloatBuffer
    private lateinit var sphereIndices: IntBuffer

    private val projectionMatrix = FloatArray(16)
    private val viewMatrixLeft = FloatArray(16)
    private val viewMatrixRight = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)

    private var yaw = 0f
    private var pitch = 0f
    private var roll = 0f

    private var playbackSpeed = 0.3f
    private var updateTexture = false

    private val sphereRadius = 10f
    private val sphereStacks = 30
    private val sphereSectors = 60

    private var screenWidth = 1920
    private var screenHeight = 1080

    private var isVideoReadyToStart = false
    private var surfaceCreated = false
    private var videoEnded = false

    var onVideoEnded: (() -> Unit)? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        android.util.Log.d("VRRenderer", "onSurfaceCreated")
        GLES30.glClearColor(1f, 0f, 0f, 1f)  // ROT für Debug
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)

        program = createProgram()
        createSphereGeometry()
        textureId = createTexture()

        surfaceTexture = SurfaceTexture(textureId).apply {
            setOnFrameAvailableListener(this@VRRenderer)
        }

        surfaceCreated = true
        android.util.Log.d("VRRenderer", "Surface created, ready for video")

        // Wenn Video starten angefordert wurde, jetzt starten
        if (isVideoReadyToStart) {
            startVideoNow()
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        android.util.Log.d("VRRenderer", "onSurfaceChanged: $width x $height")
        screenWidth = width
        screenHeight = height
        GLES30.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat() / 2f
        Matrix.perspectiveM(projectionMatrix, 0, 90f, ratio, 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        if (updateTexture) {
            surfaceTexture?.updateTexImage()
            updateTexture = false
        }

        GLES30.glUseProgram(program)
        setupViewMatrices()

        // Left eye
        GLES30.glViewport(0, 0, screenWidth / 2, screenHeight)
        drawSphere(viewMatrixLeft, 0f)

        // Right eye
        GLES30.glViewport(screenWidth / 2, 0, screenWidth / 2, screenHeight)
        // Mono mode: both eyes see the same (offset 0.0), Stereo mode: right eye sees right half (offset 0.5)
        val rightEyeOffset = if (AppConfig.stereoMode) 0.5f else 0f
        drawSphere(viewMatrixRight, rightEyeOffset)
    }

    private fun setupViewMatrices() {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, -pitch, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, -yaw, 0f, 1f, 0f)
        Matrix.rotateM(modelMatrix, 0, -roll, 0f, 0f, 1f)

        Matrix.setIdentityM(viewMatrixLeft, 0)
        Matrix.translateM(viewMatrixLeft, 0, AppConfig.ipd / 2f, 0f, 0f)
        Matrix.multiplyMM(tempMatrix, 0, viewMatrixLeft, 0, modelMatrix, 0)
        System.arraycopy(tempMatrix, 0, viewMatrixLeft, 0, 16)

        Matrix.setIdentityM(viewMatrixRight, 0)
        Matrix.translateM(viewMatrixRight, 0, -AppConfig.ipd / 2f, 0f, 0f)
        Matrix.multiplyMM(tempMatrix, 0, viewMatrixRight, 0, modelMatrix, 0)
        System.arraycopy(tempMatrix, 0, viewMatrixRight, 0, 16)
    }

    private fun drawSphere(viewMatrix: FloatArray, texOffsetU: Float) {
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        val positionHandle = GLES30.glGetAttribLocation(program, "aPosition")
        val texCoordHandle = GLES30.glGetAttribLocation(program, "aTexCoord")
        val mvpMatrixHandle = GLES30.glGetUniformLocation(program, "uMVPMatrix")
        val textureHandle = GLES30.glGetUniformLocation(program, "uTexture")
        val texOffsetHandle = GLES30.glGetUniformLocation(program, "uTexOffsetU")

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES30.glUniform1i(textureHandle, 0)
        GLES30.glUniform1f(texOffsetHandle, texOffsetU)
        GLES30.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        sphereVertices.position(0)
        GLES30.glEnableVertexAttribArray(positionHandle)
        GLES30.glVertexAttribPointer(positionHandle, 3, GLES30.GL_FLOAT, false, 0, sphereVertices)

        sphereTexCoords.position(0)
        GLES30.glEnableVertexAttribArray(texCoordHandle)
        GLES30.glVertexAttribPointer(texCoordHandle, 2, GLES30.GL_FLOAT, false, 0, sphereTexCoords)

        sphereIndices.position(0)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, sphereIndices.capacity(), GLES30.GL_UNSIGNED_INT, sphereIndices)

        GLES30.glDisableVertexAttribArray(positionHandle)
        GLES30.glDisableVertexAttribArray(texCoordHandle)
    }

    private fun createSphereGeometry() {
        val vertices = mutableListOf<Float>()
        val texCoords = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        for (i in 0..sphereStacks) {
            val lat = PI * i / sphereStacks - PI / 2
            val sinLat = sin(lat).toFloat()
            val cosLat = cos(lat).toFloat()

            for (j in 0..sphereSectors) {
                val lon = 2 * PI * j / sphereSectors
                val sinLon = sin(lon).toFloat()
                val cosLon = cos(lon).toFloat()

                vertices.add(-sphereRadius * cosLat * cosLon)
                vertices.add(sphereRadius * sinLat)
                vertices.add(-sphereRadius * cosLat * sinLon)

                texCoords.add(j.toFloat() / sphereSectors)
                texCoords.add(i.toFloat() / sphereStacks)
            }
        }

        for (i in 0 until sphereStacks) {
            for (j in 0 until sphereSectors) {
                val first = i * (sphereSectors + 1) + j
                val second = first + sphereSectors + 1

                indices.add(first)
                indices.add(second)
                indices.add(first + 1)

                indices.add(second)
                indices.add(second + 1)
                indices.add(first + 1)
            }
        }

        sphereVertices = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices.toFloatArray())
            .apply { position(0) }

        sphereTexCoords = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(texCoords.toFloatArray())
            .apply { position(0) }

        sphereIndices = ByteBuffer.allocateDirect(indices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()
            .put(indices.toIntArray())
            .apply { position(0) }
    }

    private fun createProgram(): Int {
        val vertexShader = """
            #version 300 es
            in vec4 aPosition;
            in vec2 aTexCoord;
            uniform mat4 uMVPMatrix;
            uniform float uTexOffsetU;
            out vec2 vTexCoord;
            
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTexCoord = vec2(aTexCoord.x * 0.5 + uTexOffsetU, aTexCoord.y);
            }
        """.trimIndent()

        val fragmentShader = """
            #version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision mediump float;
            in vec2 vTexCoord;
            uniform samplerExternalOES uTexture;
            out vec4 fragColor;
            
            void main() {
                fragColor = texture(uTexture, vTexCoord);
            }
        """.trimIndent()

        val vShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexShader)
        val fShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentShader)

        return GLES30.glCreateProgram().also {
            GLES30.glAttachShader(it, vShader)
            GLES30.glAttachShader(it, fShader)
            GLES30.glLinkProgram(it)
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES30.glCreateShader(type).also { shader ->
            GLES30.glShaderSource(shader, shaderCode)
            GLES30.glCompileShader(shader)
        }
    }

    private fun createTexture(): Int {
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        return textures[0]
    }

    fun startVideo() {
        android.util.Log.d("VRRenderer", "startVideo() requested")
        isVideoReadyToStart = true

        if (surfaceCreated && surfaceTexture != null) {
            startVideoNow()
        } else {
            android.util.Log.d("VRRenderer", "Surface not ready yet, will start when ready")
        }
    }

    private fun startVideoNow() {
        android.util.Log.d("VRRenderer", "startVideoNow() called")

        if (surfaceTexture == null) {
            android.util.Log.e("VRRenderer", "SurfaceTexture is STILL NULL!")
            return
        }

        if (videoUri == null) {
            android.util.Log.e("VRRenderer", "Video URI is NULL!")
            return
        }

        android.util.Log.d("VRRenderer", "SurfaceTexture OK, creating MediaPlayer with URI: $videoUri")
        mediaPlayer?.release()

        try {
            // OLD METHOD - using res/raw (commented out)
            // mediaPlayer = MediaPlayer.create(context, R.raw.forest_jog)
            // if (mediaPlayer == null) {
            //     android.util.Log.e("VRRenderer", "MediaPlayer.create returned NULL - check if forest_jog.mp4 exists!")
            //     return
            // }

            // NEW METHOD - using external video URI
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, videoUri!!)
                setSurface(Surface(surfaceTexture))
                prepare()
                isLooping = false  // Video plays once, then shows finish flag
                playbackParams = playbackParams.setSpeed(playbackSpeed)

                // Set completion listener
                setOnCompletionListener {
                    android.util.Log.d("VRRenderer", "Video completed!")
                    videoEnded = true
                    onVideoEnded?.invoke()
                }

                start()
            }

            videoEnded = false

            // Hintergrund auf schwarz ändern sobald Video läuft
            GLES30.glClearColor(0f, 0f, 0f, 1f)

            android.util.Log.d("VRRenderer", "MediaPlayer started successfully")
        } catch (e: Exception) {
            android.util.Log.e("VRRenderer", "Error starting video: ${e.message}")
            e.printStackTrace()
        }
    }

    fun setVideoUri(uri: Uri) {
        videoUri = uri
        android.util.Log.d("VRRenderer", "Video URI set to: $uri")
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed.coerceIn(0.1f, 3.0f)
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.playbackParams = it.playbackParams.setSpeed(playbackSpeed)
            }
        }
    }

    fun updateOrientation(yaw: Float, pitch: Float, roll: Float) {
        this.yaw = Math.toDegrees(yaw.toDouble()).toFloat()
        this.pitch = Math.toDegrees(pitch.toDouble()).toFloat()
        this.roll = Math.toDegrees(roll.toDouble()).toFloat()
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        updateTexture = true
    }

    fun restartVideo() {
        android.util.Log.d("VRRenderer", "Restarting video from beginning")
        mediaPlayer?.let {
            try {
                it.seekTo(0)
                it.playbackParams = it.playbackParams.setSpeed(playbackSpeed)
                if (!it.isPlaying) {
                    it.start()
                }
                videoEnded = false
            } catch (e: Exception) {
                android.util.Log.e("VRRenderer", "Error restarting video: ${e.message}")
            }
        }
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
        surfaceTexture?.release()
        surfaceTexture = null
    }

    fun setScreenDimensions(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
    }
}