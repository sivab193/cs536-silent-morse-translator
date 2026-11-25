package edu.purdue.cs536.morse

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class HandTracker(
    context: Context,
    private val onResult: (HandResult) -> Unit,
    private val onError: (String) -> Unit
) : AutoCloseable {
    data class Point(val x: Float, val y: Float)
    data class HandResult(val points: List<Point>?, val timestampMs: Long)

    private val landmarker: HandLandmarker

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .build()
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.45f)
            .setMinHandPresenceConfidence(0.45f)
            .setMinTrackingConfidence(0.45f)
            .setResultListener(::handleResult)
            .setErrorListener { error -> onError(error.message ?: error.toString()) }
            .build()
        landmarker = HandLandmarker.createFromOptions(context, options)
    }

    fun detect(bitmap: Bitmap, timestampMs: Long) {
        try {
            landmarker.detectAsync(BitmapImageBuilder(bitmap).build(), timestampMs)
        } catch (error: Throwable) {
            onError(error.message ?: error.toString())
        }
    }

    private fun handleResult(result: HandLandmarkerResult, @Suppress("UNUSED_PARAMETER") input: com.google.mediapipe.framework.image.MPImage) {
        val hand = result.landmarks().firstOrNull()
        onResult(HandResult(hand?.map { Point(it.x(), it.y()) }, result.timestampMs()))
    }

    override fun close() = landmarker.close()
}
