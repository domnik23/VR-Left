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

    // Transformation matrices for 3D rendering
    private val projectionMatrix = FloatArray(16)      // Camera lens projection
    private val viewMatrixLeft = FloatArray(16)        // Left eye view (head tracking + IPD)
    private val viewMatrixRight = FloatArray(16)       // Right eye view (head tracking + IPD)
    private val modelMatrix = FloatArray(16)           // 360° sphere world transformation
    private val mvpMatrix = FloatArray(16)             // Combined Model-View-Projection matrix
    private val tempMatrix = FloatArray(16)            // Temporary for matrix operations
    private val tempMatrix2 = FloatArray(16)           // Temporary for matrix operations

    // Head tracking system using rotation matrices (avoids gimbal lock)
    // Why rotation matrices instead of Euler angles?
    // - Euler angles suffer from gimbal lock at 90° pitch
    // - Rotation matrices are the native output from Android sensors
    // - More accurate and stable for VR applications
    private val headRotationMatrix = FloatArray(16)    // Final rotation applied to view (calibrated)
    private val uncalibratedRotation = FloatArray(16)  // Raw sensor rotation (for calibration)
    private val calibrationMatrix = FloatArray(16)     // Calibration offset matrix
    private val calibratedRotation = FloatArray(16)    // Unused (kept for potential future use)
    private var hasHeadRotation = false                // True after first sensor update
    private var isCalibrated = false                   // True after calibrateOrientation() called

    private var playbackSpeed = 0.3f

    private val sphereRadius = 10f
    private val sphereStacks = 30
    private val sphereSectors = 60

    private var screenWidth = 1920
    private var screenHeight = 1080

    private var isVideoReadyToStart = false
    private var surfaceCreated = false
    private var videoEnded = false
    private var isReleased = false  // Track if renderer has been released

    var onVideoEnded: (() -> Unit)? = null
    var onVideoError: ((String) -> Unit)? = null

    // Timecode parameter loading
    private var timecodeLoader: TimecodeParameterLoader? = null
    private var frameCounter = 0
    private val updateInterval = 30 // Update timecode parameters every 30 frames (~0.5 seconds at 60fps)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        android.util.Log.d("VRRenderer", "onSurfaceCreated")

        // Reset released flag if surface is being recreated
        isReleased = false

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

        // Release old SurfaceTexture to prevent memory leak
        surfaceTexture?.release()

        surfaceTexture = SurfaceTexture(textureId).apply {
            setOnFrameAvailableListener(this@VRRenderer)
        }

        surfaceCreated = true

        // Check if we need to start a new video or just update existing MediaPlayer's surface
        if (mediaPlayer != null) {
            // MediaPlayer already exists (e.g., returning from Settings)
            // Just update its surface instead of creating a new one
            mediaPlayer?.setSurface(Surface(surfaceTexture))
        } else if (isVideoReadyToStart) {
            // No MediaPlayer yet - start video from scratch
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
        // Safety check: Don't render if released
        if (isReleased || surfaceTexture == null) {
            return
        }

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        // Always update texture (safe to call even if no new frame available)
        // This prevents video freezing issues caused by missed onFrameAvailable callbacks
        try {
            surfaceTexture?.updateTexImage()
        } catch (e: Exception) {
            // Context may have been lost during lifecycle transitions - silently ignore
            return
        }

        // Update timecode parameters periodically
        updateTimecodeParameters()

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

    /**
     * Setup view matrices for stereo VR rendering
     *
     * Matrix hierarchy:
     * - Model Matrix: Rotates the 360° sphere to correct video orientation
     * - View Matrix: Applies head tracking and stereo eye offset (IPD)
     * - Projection Matrix: Perspective projection for VR lenses
     *
     * Key concepts:
     * - IPD (Inter-Pupillary Distance): Eye separation for stereo 3D effect
     * - Matrix multiplication order: translation * rotation (not rotation * translation)
     *   This ensures IPD offset happens in camera space, not world space
     */
    private fun setupViewMatrices() {
        // Model matrix: Rotate to correct video orientation
        // Uses videoRotation setting from AppConfig (adjustable in Settings)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, AppConfig.videoRotation, 1f, 0f, 0f)

        // View matrix approach for 360° video:
        // The sensor rotation describes how the phone is oriented.
        // We want to rotate the world OPPOSITE to the phone rotation.

        if (hasHeadRotation) {
            // Use the rotation matrix directly (already in correct orientation)
            System.arraycopy(headRotationMatrix, 0, tempMatrix, 0, 16)
        } else {
            Matrix.setIdentityM(tempMatrix, 0)
        }

        // Create view matrices for left and right eye with IPD offset
        createEyeViewMatrix(viewMatrixLeft, tempMatrix, -AppConfig.ipd / 2f)
        createEyeViewMatrix(viewMatrixRight, tempMatrix, AppConfig.ipd / 2f)
    }

    /**
     * Creates a view matrix for one eye by applying IPD offset and rotation
     * Matrix multiplication: viewMatrix = translation * rotation
     */
    private fun createEyeViewMatrix(
        viewMatrix: FloatArray,
        rotationMatrix: FloatArray,
        ipdOffset: Float
    ) {
        Matrix.setIdentityM(viewMatrix, 0)
        Matrix.translateM(viewMatrix, 0, ipdOffset, 0f, 0f)
        Matrix.multiplyMM(viewMatrix, 0, viewMatrix, 0, rotationMatrix, 0)
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

        if (!validateMediaSetup()) return

        try {
            createAndConfigureMediaPlayer()
            setBackgroundColor()
            android.util.Log.d("VRRenderer", "MediaPlayer started successfully")
        } catch (e: Exception) {
            handleMediaError(e)
        }
    }

    private fun validateMediaSetup(): Boolean {
        if (surfaceTexture == null) {
            android.util.Log.e("VRRenderer", "SurfaceTexture is NULL")
            onVideoError?.invoke("Interner Fehler: Surface nicht bereit")
            return false
        }

        if (videoUri == null) {
            android.util.Log.e("VRRenderer", "Video URI is NULL")
            onVideoError?.invoke("Kein Video ausgewählt")
            return false
        }

        return true
    }

    private fun createAndConfigureMediaPlayer() {
        android.util.Log.d("VRRenderer", "Creating MediaPlayer with URI: $videoUri")

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(context, videoUri!!)
            setSurface(Surface(surfaceTexture))
            setupMediaListeners(this)
            prepare()
            isLooping = false
            playbackParams = playbackParams.setSpeed(playbackSpeed)
            setVolume(AppConfig.videoVolume, AppConfig.videoVolume)
            start()
        }

        videoEnded = false
    }

    private fun setupMediaListeners(player: MediaPlayer) {
        player.setOnErrorListener { _, what, extra ->
            android.util.Log.e("VRRenderer", "MediaPlayer error: what=$what extra=$extra")
            val errorMsg = when (what) {
                MediaPlayer.MEDIA_ERROR_SERVER_DIED -> "Media Server abgestürzt"
                MediaPlayer.MEDIA_ERROR_UNKNOWN -> "Unbekannter Fehler beim Abspielen"
                else -> "Fehler beim Video-Abspielen (Code: $what)"
            }
            onVideoError?.invoke(errorMsg)
            true
        }

        player.setOnCompletionListener {
            android.util.Log.d("VRRenderer", "Video completed!")
            videoEnded = true
            onVideoEnded?.invoke()
        }
    }

    private fun setBackgroundColor() {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
    }

    private fun handleMediaError(e: Exception) {
        val errorMsg = when (e) {
            is java.io.FileNotFoundException -> {
                android.util.Log.e("VRRenderer", "Video file not found: ${e.message}")
                "Video nicht gefunden. Bitte wählen Sie ein anderes Video."
            }
            is java.io.IOException -> {
                android.util.Log.e("VRRenderer", "IO error loading video: ${e.message}")
                "Video konnte nicht geladen werden. Datei beschädigt?"
            }
            else -> {
                android.util.Log.e("VRRenderer", "Error starting video: ${e.message}")
                e.printStackTrace()
                "Fehler beim Video-Start: ${e.message}"
            }
        }
        onVideoError?.invoke(errorMsg)
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

    /**
     * Update head rotation from sensor data
     *
     * Flow:
     * 1. Convert 3x3 sensor rotation matrix to 4x4 OpenGL matrix
     * 2. Store uncalibrated rotation for calibration purposes
     * 3. If calibrated: Apply calibration matrix to current rotation
     *
     * Why use rotation matrices instead of Euler angles?
     * - Avoids gimbal lock (Euler angle singularity at 90° pitch)
     * - More accurate for VR head tracking
     * - Direct from Android sensor (no conversion needed)
     *
     * Coordinate system:
     * - Input: Android sensor coordinate system (remapped to landscape in MainActivity)
     * - Output: OpenGL coordinate system for rendering
     *
     * @param rotationMatrix3x3 9-element rotation matrix from SensorManager
     */
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

        // Always store the uncalibrated rotation for calibration purposes
        // This is CRITICAL: We need the raw sensor rotation, not the calibrated one,
        // otherwise calibrations would stack on top of each other
        System.arraycopy(temp4x4, 0, uncalibratedRotation, 0, 16)

        if (isCalibrated) {
            // Apply calibration: calibratedRotation = calibrationMatrix * currentRotation
            // This makes the calibration orientation the new "zero" orientation
            Matrix.multiplyMM(headRotationMatrix, 0, calibrationMatrix, 0, temp4x4, 0)
        } else {
            System.arraycopy(temp4x4, 0, headRotationMatrix, 0, 16)
        }

        hasHeadRotation = true
    }

    /**
     * Calibrate head orientation to current position
     *
     * Makes the current head position the new "forward" direction.
     *
     * The calibration matrix is the transpose (inverse for rotation matrices)
     * of the current rotation. When multiplied with future rotations,
     * this effectively "zeros out" the current orientation.
     *
     * Note: Uses headRotationMatrix (v1.3 approach) for immediate calibration.
     * This may cause calibration stacking on repeated calls, but works better
     * for initial app startup calibration.
     *
     * Called:
     * - Automatically on app start (after initial sensor data)
     * - Manually by double-pressing Volume Up button
     */
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
        // Frame is available - will be picked up in next onDrawFrame call
        // No flag needed since we call updateTexImage() every frame
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

    /**
     * Load a completely new video
     *
     * Releases the current MediaPlayer and creates a new one with the current videoUri.
     * Used when switching to a different video file.
     */
    fun loadNewVideo() {
        android.util.Log.d("VRRenderer", "Loading new video: $videoUri")

        if (surfaceTexture == null) {
            android.util.Log.e("VRRenderer", "Cannot load new video - SurfaceTexture not ready")
            onVideoError?.invoke("Interner Fehler: Surface nicht bereit")
            return
        }

        if (videoUri == null) {
            android.util.Log.e("VRRenderer", "Cannot load new video - URI is null")
            onVideoError?.invoke("Kein Video ausgewählt")
            return
        }

        try {
            // Release old MediaPlayer
            mediaPlayer?.release()

            // Create new MediaPlayer with new video
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, videoUri!!)
                setSurface(Surface(surfaceTexture))
                setupMediaListeners(this)
                prepare()
                isLooping = false
                playbackParams = playbackParams.setSpeed(playbackSpeed)
                setVolume(AppConfig.videoVolume, AppConfig.videoVolume)
                start()
            }

            videoEnded = false
            android.util.Log.d("VRRenderer", "New video loaded successfully")
        } catch (e: Exception) {
            handleMediaError(e)
        }
    }

    fun updateVolume() {
        mediaPlayer?.setVolume(AppConfig.videoVolume, AppConfig.videoVolume)
    }

    fun release() {
        if (isReleased) {
            return  // Already released
        }

        isReleased = true
        android.util.Log.d("VRRenderer", "Releasing renderer resources")

        // Release MediaPlayer first (it uses the SurfaceTexture)
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            android.util.Log.w("VRRenderer", "Error releasing MediaPlayer: ${e.message}")
        }
        mediaPlayer = null

        // Then release SurfaceTexture
        try {
            surfaceTexture?.release()
        } catch (e: Exception) {
            android.util.Log.w("VRRenderer", "Error releasing SurfaceTexture: ${e.message}")
        }
        surfaceTexture = null
    }

    /**
     * Get current playback position in milliseconds
     */
    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }

    /**
     * Seek to specific position in milliseconds
     */
    fun seekTo(positionMs: Int) {
        android.util.Log.d("VRRenderer", "seekTo($positionMs) - mediaPlayer=$mediaPlayer, isPlaying=${mediaPlayer?.isPlaying}")
        mediaPlayer?.seekTo(positionMs)
        android.util.Log.d("VRRenderer", "seekTo() completed (async operation started)")
    }

    /**
     * Pause video playback
     */
    fun pause() {
        android.util.Log.d("VRRenderer", "pause() - isReleased=$isReleased, mediaPlayer=$mediaPlayer")
        if (!isReleased) {
            mediaPlayer?.pause()
        }
    }

    /**
     * Resume video playback
     */
    fun resume() {
        android.util.Log.d("VRRenderer", "resume() - isReleased=$isReleased, mediaPlayer=$mediaPlayer, currentPosition=${mediaPlayer?.currentPosition}")
        if (!isReleased) {
            mediaPlayer?.start()
        }
    }

    fun setScreenDimensions(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
    }

    /**
     * Set the timecode parameter loader
     *
     * @param loader TimecodeParameterLoader instance to use for dynamic parameters
     */
    fun setTimecodeLoader(loader: TimecodeParameterLoader?) {
        timecodeLoader = loader
        android.util.Log.d("VRRenderer", "TimecodeLoader set: ${loader != null}")
    }

    /**
     * Update video parameters based on current playback time
     *
     * Called periodically from onDrawFrame to apply timecode-based parameter changes.
     * Updates are throttled to every N frames to reduce overhead.
     */
    private fun updateTimecodeParameters() {
        // Safety check
        if (isReleased || mediaPlayer == null) {
            return
        }

        // Only update every N frames to reduce overhead
        frameCounter++
        if (frameCounter < updateInterval) {
            return
        }
        frameCounter = 0

        // Get current video time
        val currentTimeMs = try {
            mediaPlayer?.currentPosition?.toLong() ?: 0L
        } catch (e: Exception) {
            android.util.Log.w("VRRenderer", "Error getting current position: ${e.message}")
            0L
        }

        // Update timecode parameters
        timecodeLoader?.let { loader ->
            if (loader.updateForTime(currentTimeMs)) {
                // Parameters were updated - apply volume change if needed
                val params = loader.getCurrentParameters()
                params?.videoVolume?.let { volume ->
                    mediaPlayer?.setVolume(volume, volume)
                }
            }
        }
    }

    /**
     * Get current overlay configuration based on video time
     *
     * Can be called from MainActivity to update overlay UI
     *
     * @return Current OverlayConfig or null if no overlay active
     */
    fun getCurrentOverlay(): OverlayConfig? {
        return timecodeLoader?.getCurrentOverlay()
    }
}