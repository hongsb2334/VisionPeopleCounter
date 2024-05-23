package com.example.visionpeoplecounter


import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
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




class ObjectDetectorHelper(
    var threshold: Float = 0.4f,
    var numThreads: Int = 2,
    var maxResults: Int = 20,
    var currentDelegate: Int = 0,
    var currentModel: Int = 0,
    val context: Context,
    val objectDetectorListener: DetectorListener
) {

    private val TAG = "ObjectDetectionHelper"
    // For this example this needs to be a var so it can be reset on changes. If the ObjectDetector
    // will not change, a lazy val would be preferable.
    private var objectDetector: ObjectDetector? = null


    init {
        if (!TfLiteVision.isInitialized()) {
            TfLiteVision.initialize(context, TfLiteInitializationOptions.builder().build())
        }
        setupObjectDetector()
    }

    fun clearObjectDetector() {
        objectDetector = null
    }



    // Initialize the object detector using current settings on the
    // thread that is using it. CPU and NNAPI delegates can be used with detectors
    // that are created on the main thread and used on a background thread, but
    // the GPU delegate needs to be used on the thread that initialized the detector
    fun setupObjectDetector() {
        // Create the base options for the detector using specifies max results and score threshold
        val optionsBuilder =
            ObjectDetector.ObjectDetectorOptions.builder()
                .setScoreThreshold(threshold)
                .setMaxResults(maxResults)


        val baseOptionsBuilder = BaseOptions.builder().setNumThreads(numThreads)

        optionsBuilder.setBaseOptions(baseOptionsBuilder.build())
        // Use the specified hardware for running the model. Default to CPU
        val modelName = "mobilenetv1.tflite"

        try {
            objectDetector = ObjectDetector.createFromFileAndOptions(context, modelName, optionsBuilder.build())
        } catch (e: IllegalStateException) {
            objectDetectorListener.onError(
                "Object detector failed to initialize. See error logs for details"
            )
            Log.e("Test", "TFLite failed to load model with error: " + e.message)
        }
    }

    fun detect(image: Bitmap, imageRotation: Int) {
        if (!TfLiteVision.isInitialized()) {
            Log.e(TAG, "detect: TfLiteVision is not initialized yet")
            return
        }

        if (objectDetector == null) {
            setupObjectDetector()
        }

        var inferenceTime = SystemClock.uptimeMillis()

        val imageProcessor = ImageProcessor.Builder()
            /*.add(ResizeOp(640, 640, ResizeOp.ResizeMethod.BILINEAR))*/
            .add(Rot90Op(-imageRotation / 90))
               // 추가: 정규화 작업
            .build()

        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(image))

        val results = objectDetector?.detect(tensorImage)
        val personResults = results?.filter { detection ->
            detection.categories.any { category -> category.label == "person" }
        }?.toMutableList()
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime
        objectDetectorListener.onResults(
            personResults,
            inferenceTime,
            tensorImage.height,
            tensorImage.width)
    }

    interface DetectorListener {
        fun onInitialized()
        fun onError(error: String)
        fun onResults(
            results: MutableList<Detection>?,
            inferenceTime: Long,
            imageHeight: Int,
            imageWidth: Int
        )
    }

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DELEGATE_NNAPI = 2
        const val MODEL_MOBILENETV1 = 0
        const val MODEL_EFFICIENTDETV0 = 1
        const val MODEL_EFFICIENTDETV1 = 2
        const val MODEL_EFFICIENTDETV2 = 3
    }
}


class LiveCounting : AppCompatActivity(), ObjectDetectorHelper.DetectorListener{

    private lateinit var viewBinding: ActivityLiveCountingBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var objectDetectorHelper: ObjectDetectorHelper
    private lateinit var bitmapBuffer: Bitmap
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")
        viewBinding = ActivityLiveCountingBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

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
