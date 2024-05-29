package com.example.visionpeoplecounter


import DatabaseHelper
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.visionpeoplecounter.databinding.ActivityLiveCountingBinding
import com.google.android.gms.tflite.client.TfLiteInitializationOptions
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.gms.vision.TfLiteVision
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.nio.ByteBuffer
import java.util.LinkedList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.example.visionpeoplecounter.ObjectDetectorHelper
import com.github.mikephil.charting.data.Entry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LiveCounting : AppCompatActivity(), ObjectDetectorHelper.DetectorListener{

    private lateinit var viewBinding: ActivityLiveCountingBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var objectDetectorHelper: ObjectDetectorHelper
    private lateinit var bitmapBuffer: Bitmap
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var dbHelper: DatabaseHelper
    private var totalPersonCount: Int = 0
    private val handler = Handler(Looper.getMainLooper())
    private val interval: Long = 5000 // 5초
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")
        viewBinding = ActivityLiveCountingBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        dbHelper = DatabaseHelper(this)
        objectDetectorHelper = ObjectDetectorHelper(
            context = this,
            objectDetectorListener = this
        )

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }


        cameraExecutor = Executors.newSingleThreadExecutor()
        handler.post(object : Runnable {
            override fun run() {
                saveData()
                handler.postDelayed(this, interval)
            }
        })
    }
    private fun saveData() {
        val timestamp = dateFormat.format(Date())
        dbHelper.insertCount(totalPersonCount, timestamp)
    }
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        Log.d(TAG, "onRequestPermissionsResult called")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }



    private fun startCamera() {
        Log.d(TAG, "startCamera called")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            cameraProvider = cameraProviderFuture.get()


            // Preview
            preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(viewBinding.viewFinder.display.rotation)
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(viewBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { image ->
                        Log.d(TAG, "imageAnalyzer setAnalyzer called")
                        if (!::bitmapBuffer.isInitialized) {
                            bitmapBuffer = Bitmap.createBitmap(
                                image.width,
                                image.height,
                                Bitmap.Config.ARGB_8888
                            )
                        }
                        detectObjects(image)

                    }
                }
            // Unbind use cases before rebinding

            cameraProvider?.unbindAll()
            try {
                // Bind use cases to camera

                camera = cameraProvider?.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer)
                // Attach the viewfinder's surface provider to preview use case


            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun detectObjects(image: ImageProxy) {
        val rotationDegrees = image.imageInfo.rotationDegrees
        val bitmap = imageProxyToBitmap(image)
        Log.d(TAG, "Detecting objects with rotation: $rotationDegrees")
        objectDetectorHelper.detect(bitmap, rotationDegrees)
        image.close()
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        Log.d(TAG, "imageProxyToBitmap called")
        val buffer: ByteBuffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return Bitmap.createBitmap(
            image.width, image.height, Bitmap.Config.ARGB_8888
        ).also { bitmap ->
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
        }
    }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
        imageAnalyzer?.clearAnalyzer()
        handler.removeCallbacksAndMessages(null)

        val intent = Intent(this, GraphActivity::class.java)
        startActivity(intent)
    }

    override fun onResults(
        results: MutableList<Detection>?,
        inferenceTime: Long,
        imageHeight: Int,
        imageWidth: Int
    ) {
        Log.d(TAG, "onResults called with ${results?.size ?: 0} results")
        runOnUiThread {
            viewBinding.inferenceTimeVal.text = String.format("%d ms", inferenceTime)

            val personCount = results?.size ?: 0
            viewBinding.personCountVal.text = String.format("Person Count: %d", personCount)
            viewBinding.overlay.setResults(
                results ?: LinkedList(),
                imageHeight,
                imageWidth
            )
            viewBinding.overlay.invalidate()
            Log.d(TAG, "overlayview update with $results")
            totalPersonCount = personCount
        }
    }

    override fun onError(error: String) {
        Log.d(TAG, "onError called with $error")
        runOnUiThread {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        }
    }


    override fun onInitialized() {
        objectDetectorHelper.setupObjectDetector()

        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Wait for the views to be properly laid out
        viewBinding.viewFinder.post {
            // Set up the camera and its use cases
            startCamera()
        }
    }


    companion object {
        private const val TAG = "CameraXApp"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    add(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }



}
