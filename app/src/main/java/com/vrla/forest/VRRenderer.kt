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

    // Cached shader locations (performance optimization)
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var mvpMatrixHandle = 0
    private var textureHandle = 0
    private var texOffsetHandle = 0
    private var texScaleHandle = 0

    private lateinit var sphereVertices: FloatBuffer
    private lateinit var sphereTexCoords: FloatBuffer
    private lateinit var sphereIndices: IntBuffer

    private val projectionMatrix = FloatArray(16)
    private val viewMatrixLeft = FloatArray(16)
    private val viewMatrixRight = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)
    private val tempMatrix2 = FloatArray(16)

    // Head tracking using rotation matrix directly (not Euler angles)
    private val headRotationMatrix = FloatArray(16)
    private val calibrationMatrix = FloatArray(16)
    private val calibratedRotation = FloatArray(16)
    private var hasHeadRotation = false
    private var isCalibrated = false

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
    var onVideoError: ((String) -> Unit)? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        android.util.Log.d("VRRenderer", "onSurfaceCreated")
        GLES30.glClearColor(1f, 0f, 0f, 1f)  // ROT für Debug
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)

        program = createProgram()
        createSphereGeometry()
        textureId = createTexture()

        // Cache shader locations for performance
        positionHandle = GLES30.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES30.glGetAttribLocation(program, "aTexCoord")
        mvpMatrixHandle = GLES30.glGetUniformLocation(program, "uMVPMatrix")
        textureHandle = GLES30.glGetUniformLocation(program, "uTexture")
        texOffsetHandle = GLES30.glGetUniformLocation(program, "uTexOffsetU")
        texScaleHandle = GLES30.glGetUniformLocation(program, "uTexScaleX")

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

        // Texture scaling: Mono mode uses full video (1.0x), Stereo mode uses half (0.5x for side-by-side)
        val texScale = if (AppConfig.stereoMode) 0.5f else 1.0f

        // Left eye
        GLES30.glViewport(0, 0, screenWidth / 2, screenHeight)
        drawSphere(viewMatrixLeft, 0f, texScale)

        // Right eye
        GLES30.glViewport(screenWidth / 2, 0, screenWidth / 2, screenHeight)
        // Mono mode: both eyes see the same (offset 0.0), Stereo mode: right eye sees right half (offset 0.5)
        val rightEyeOffset = if (AppConfig.stereoMode) 0.5f else 0f
        drawSphere(viewMatrixRight, rightEyeOffset, texScale)
    }

    private fun setupViewMatrices() {
        // Model matrix: Rotate video 90° clockwise to correct landscape orientation
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, 90f, 0f, 0f, 1f)  // Rotate 90° around Z-axis

        // View matrix approach for 360° video:
        // The sensor rotation describes how the phone is oriented.
        // We want to rotate the world OPPOSITE to the phone rotation.

        if (hasHeadRotation) {
            // Use the rotation matrix directly (already in correct orientation)
            System.arraycopy(headRotationMatrix, 0, tempMatrix, 0, 16)
        } else {
            Matrix.setIdentityM(tempMatrix, 0)
        }

        // Left eye: Rotation, then IPD offset in camera space
        Matrix.setIdentityM(viewMatrixLeft, 0)
        Matrix.translateM(viewMatrixLeft, 0, -AppConfig.ipd / 2f, 0f, 0f)
        Matrix.multiplyMM(viewMatrixLeft, 0, viewMatrixLeft, 0, tempMatrix, 0)

        // Right eye: Rotation, then IPD offset in camera space
        Matrix.setIdentityM(viewMatrixRight, 0)
        Matrix.translateM(viewMatrixRight, 0, AppConfig.ipd / 2f, 0f, 0f)
        Matrix.multiplyMM(viewMatrixRight, 0, viewMatrixRight, 0, tempMatrix, 0)
    }

    private fun drawSphere(viewMatrix: FloatArray, texOffsetU: Float, texScaleX: Float) {
        // MVP = Projection * View * Model
        Matrix.multiplyMM(tempMatrix2, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix2, 0)

        // Use cached handles instead of querying every frame
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES30.glUniform1i(textureHandle, 0)
        GLES30.glUniform1f(texOffsetHandle, texOffsetU)
        GLES30.glUniform1f(texScaleHandle, texScaleX)
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
            uniform float uTexScaleX;
            out vec2 vTexCoord;

            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTexCoord = vec2(aTexCoord.x * uTexScaleX + uTexOffsetU, aTexCoord.y);
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
            onVideoError?.invoke("Interner Fehler: Surface nicht bereit")
            return
        }

        if (videoUri == null) {
            android.util.Log.e("VRRenderer", "Video URI is NULL!")
            onVideoError?.invoke("Kein Video ausgewählt")
            return
        }

        android.util.Log.d("VRRenderer", "SurfaceTexture OK, creating MediaPlayer with URI: $videoUri")
        mediaPlayer?.release()

        try {
            // NEW METHOD - using external video URI
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, videoUri!!)
                setSurface(Surface(surfaceTexture))

                // Set error listener
                setOnErrorListener { _, what, extra ->
                    android.util.Log.e("VRRenderer", "MediaPlayer error: what=$what extra=$extra")
                    val errorMsg = when (what) {
                        MediaPlayer.MEDIA_ERROR_SERVER_DIED -> "Media Server abgestürzt"
                        MediaPlayer.MEDIA_ERROR_UNKNOWN -> "Unbekannter Fehler beim Abspielen"
                        else -> "Fehler beim Video-Abspielen (Code: $what)"
                    }
                    onVideoError?.invoke(errorMsg)
                    true
                }

                prepare()
                isLooping = false  // Video plays once, then shows finish flag
                playbackParams = playbackParams.setSpeed(playbackSpeed)

                // Set volume from AppConfig
                setVolume(AppConfig.videoVolume, AppConfig.videoVolume)

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
        } catch (e: java.io.FileNotFoundException) {
            android.util.Log.e("VRRenderer", "Video file not found: ${e.message}")
            onVideoError?.invoke("Video nicht gefunden. Bitte wählen Sie ein anderes Video.")
        } catch (e: java.io.IOException) {
            android.util.Log.e("VRRenderer", "IO error loading video: ${e.message}")
            onVideoError?.invoke("Video konnte nicht geladen werden. Datei beschädigt?")
        } catch (e: Exception) {
            android.util.Log.e("VRRenderer", "Error starting video: ${e.message}")
            e.printStackTrace()
            onVideoError?.invoke("Fehler beim Video-Start: ${e.message}")
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

    fun updateHeadRotation(rotationMatrix3x3: FloatArray) {
        // Convert 3x3 rotation matrix to 4x4 for OpenGL
        // The sensor rotation matrix describes device orientation in world space
        val temp4x4 = FloatArray(16)
        temp4x4[0] = rotationMatrix3x3[0]
        temp4x4[1] = rotationMatrix3x3[1]
        temp4x4[2] = rotationMatrix3x3[2]
        temp4x4[3] = 0f

        temp4x4[4] = rotationMatrix3x3[3]
        temp4x4[5] = rotationMatrix3x3[4]
        temp4x4[6] = rotationMatrix3x3[5]
        temp4x4[7] = 0f

        temp4x4[8] = rotationMatrix3x3[6]
        temp4x4[9] = rotationMatrix3x3[7]
        temp4x4[10] = rotationMatrix3x3[8]
        temp4x4[11] = 0f

        temp4x4[12] = 0f
        temp4x4[13] = 0f
        temp4x4[14] = 0f
        temp4x4[15] = 1f

        if (isCalibrated) {
            // Apply calibration: calibratedRotation = calibrationMatrix^T * currentRotation
            // This makes the calibration orientation the new "zero" orientation
            Matrix.multiplyMM(headRotationMatrix, 0, calibrationMatrix, 0, temp4x4, 0)
        } else {
            System.arraycopy(temp4x4, 0, headRotationMatrix, 0, 16)
        }

        hasHeadRotation = true
    }

    fun calibrateOrientation() {
        if (hasHeadRotation) {
            // Store inverse (transpose) of current rotation as calibration
            // This will make current orientation the new "forward" direction
            Matrix.transposeM(calibrationMatrix, 0, headRotationMatrix, 0)
            isCalibrated = true
            android.util.Log.d("VRRenderer", "Orientation calibrated")
        }
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

    fun updateVolume() {
        mediaPlayer?.setVolume(AppConfig.videoVolume, AppConfig.videoVolume)
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