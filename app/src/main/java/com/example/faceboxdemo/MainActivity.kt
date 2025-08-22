package com.example.faceboxdemo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import android.graphics.Rect
import android.graphics.RectF
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlay: FaceOverlay
    private var detector: FaceDetector? = null
    private var lensFacing = CameraSelector.LENS_FACING_FRONT

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.previewView)
        overlay = findViewById(R.id.overlay)

        // Ask for camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }

        // Build a single reusable detector (FAST is enough for boxes)
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
        detector = FaceDetection.getClient(options)
    }

    override fun onDestroy() {
        detector?.close()
        detector = null
        super.onDestroy()
    }

    @OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            // Keep only the latest frame to reduce latency
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(Size(720, 1280)) // hint; engine can choose closest
                .build()

            val selector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            analysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                val mediaImage = imageProxy.image
                val rotation = imageProxy.imageInfo.rotationDegrees
                if (mediaImage != null) {
                    val input = InputImage.fromMediaImage(mediaImage, rotation)
                    detector?.process(input)
                        ?.addOnSuccessListener { faces ->
                            // Compute source size (after considering rotation)
                            val (srcW, srcH) = if (rotation % 180 == 0) {
                                imageProxy.width to imageProxy.height
                            } else {
                                imageProxy.height to imageProxy.width
                            }

                            val viewW = previewView.width
                            val viewH = previewView.height

                            val rects = faces.map { face ->
                                translateRect(
                                    face.boundingBox,
                                    srcW, srcH,
                                    viewW, viewH,
                                    lensFacing == CameraSelector.LENS_FACING_FRONT
                                )
                            }

                            overlay.setBoxes(rects)
                        }
                        ?.addOnCompleteListener { imageProxy.close() }
                        ?: run { imageProxy.close() }
                } else {
                    imageProxy.close()
                }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, selector, preview, analysis)
                // Fill and center-crop to match our math
                previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun translateRect(
        src: Rect,
        srcW: Int,
        srcH: Int,
        viewW: Int,
        viewH: Int,
        isFront: Boolean
    ): RectF {
        if (viewW == 0 || viewH == 0 || srcW == 0 || srcH == 0) return RectF()

        // Center-crop scaling (PreviewView.FILL_CENTER)
        val scale = maxOf(viewW.toFloat() / srcW, viewH.toFloat() / srcH)
        val dx = (viewW - srcW * scale) / 2f
        val dy = (viewH - srcH * scale) / 2f

        var left = src.left * scale + dx
        var top = src.top * scale + dy
        var right = src.right * scale + dx
        var bottom = src.bottom * scale + dy

        if (isFront) {
            // Mirror horizontally within view bounds
            val newLeft = viewW - right
            val newRight = viewW - left
            left = newLeft
            right = newRight
        }

        return RectF(left, top, right, bottom)
    }
}
