package com.pinder.airhand

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.Executors

class HandCameraService : LifecycleService() {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var handLandmarker: HandLandmarker? = null
    private val gestureEngine = GestureEngine()

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        try {
            setupHandLandmarker()
            startCamera()
        } catch (t: Throwable) {
            // A missing/corrupt model file or a camera bind failure would otherwise leave a
            // foreground-service notification stuck on screen with nothing actually working.
            Log.e(TAG, "Failed to start hand tracking", t)
            GestureBus.publish(GestureEvent.Pause(true))
            stopSelf()
        }
    }

    private fun startAsForeground() {
        val channelId = "airhand_camera"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(channelId, "AirHand camera", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("AirHand is active")
            .setContentText("Front camera is tracking your hand")
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this,
            42,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        )
    }

    private fun setupHandLandmarker() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .build()
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.55f)
            .setMinHandPresenceConfidence(0.55f)
            .setMinTrackingConfidence(0.55f)
            .setResultListener(::onHandResult)
            .setErrorListener { error -> Log.e(TAG, "HandLandmarker error", error) }
            .build()
        handLandmarker = HandLandmarker.createFromOptions(this, options)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    try {
                        val landmarker = handLandmarker
                        if (landmarker == null) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        val bitmap = imageProxy.toBitmap()
                        val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()
                        val rotated = if (rotation == 0f) bitmap else {
                            val matrix = Matrix().apply { postRotate(rotation) }
                            android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        }
                        val mpImage = BitmapImageBuilder(rotated).build()
                        landmarker.detectAsync(mpImage, android.os.SystemClock.uptimeMillis())
                    } catch (t: Throwable) {
                        Log.e(TAG, "Frame analysis failed", t)
                    } finally {
                        imageProxy.close()
                    }
                }

                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to bind camera", t)
                GestureBus.publish(GestureEvent.Pause(true))
                stopSelf()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onHandResult(result: HandLandmarkerResult, image: MPImage) {
        val hands = result.landmarks()
        if (hands.isNotEmpty()) gestureEngine.process(hands[0])
    }

    override fun onDestroy() {
        runCatching { handLandmarker?.close() }
        handLandmarker = null
        cameraExecutor.shutdown()
        GestureBus.publish(GestureEvent.Pause(true))
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AirHandCamera"
    }
}
