package com.example.visionpeoplecounter

import android.Manifest
import android.content.ContentValues.TAG
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.Image
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.visionpeoplecounter.databinding.ActivityLiveCountingBinding
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object ImageUtils {
    fun imageToBitmap(image: Image, rotationDegrees: Int): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.capacity()).apply { buffer.get(this) }
        val options = BitmapFactory.Options().apply {
            inMutable = true
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (bitmap == null) {
            Log.e(TAG, "Unable to decode the ByteArray into a Bitmap.")
            throw IllegalStateException("Unable to decode the ByteArray into a Bitmap.")
        }


        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun bitmapToByteBuffer(bitmap: Bitmap, width: Int, height: Int): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * width * height * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(width * height)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, width, height)
        intValues.forEach { value ->
            byteBuffer.putFloat(((value shr 16 and 0xFF) - 128f) / 128f)
            byteBuffer.putFloat(((value shr 8 and 0xFF) - 128f) / 128f)
            byteBuffer.putFloat(((value and 0xFF) - 128f) / 128f)
        }
        return byteBuffer
    }
}

data class Detection(val box: RectF, val score: Float)

class OverlayView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val detections = mutableListOf<Detection>()

    fun clear() {
        detections.clear()
    }

    fun addBox(rect: RectF, imageWidth: Int, imageHeight: Int) {
        val scaleX = width.toFloat() / imageWidth
        val scaleY = height.toFloat() / imageHeight
        val scaledRect = RectF(
            rect.left * scaleX, rect.top * scaleY,
            rect.right * scaleX, rect.bottom * scaleY
        )
        detections.add(Detection(scaledRect, 1.0f))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        detections.forEach {
            canvas.drawRect(it.box, paint)
        }
    }
}



class LiveCounting : AppCompatActivity() {

    private lateinit var viewBinding: ActivityLiveCountingBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var tflite: Interpreter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityLiveCountingBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Request camera permissions
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
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()


            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val imageAnalysis = ImageAnalysis.Builder()
                //.setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer { imageProxy ->
                        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                        processImage(imageProxy)
                        imageProxy.close()
                    })
                }
            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }


    private fun processImage(imageProxy: ImageProxy) {
        @OptIn(ExperimentalGetImage::class)
        val image = imageProxy.image
        if (image == null) {
            Log.d(TAG, "ImageProxy does not contain an image.")
            imageProxy.close()
            return
        }
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = ImageUtils.imageToBitmap(image, rotationDegrees)

        // 이미지 전처리
        val scaledBitmap = Bitmap.createScaledBitmap(inputImage, 640, 640, false)
        val inputTensor = ImageUtils.bitmapToByteBuffer(scaledBitmap, 640, 640)

        // 모델 실행
        val outputMap = mutableMapOf<Int, Any>()
        val outputLocations = Array(1) { Array(10) { FloatArray(4) } }  // 바운딩 박스 위치
        val outputClasses = Array(1) { FloatArray(10) }  // 클래스 레이블
        val outputScores = Array(1) { FloatArray(10) }  // 신뢰도 점수
        val numDetections = FloatArray(1)  // 감지된 객체 수

        outputMap[0] = outputLocations
        outputMap[1] = outputClasses
        outputMap[2] = outputScores
        outputMap[3] = numDetections

        tflite.runForMultipleInputsOutputs(arrayOf(inputTensor), outputMap)

        // 결과 후처리
        val persons = processDetections(outputLocations, outputClasses, outputScores, numDetections)

        // UI 업데이트
        updateOverlay(persons, imageProxy.width, imageProxy.height)

        imageProxy.close()
    }

    private fun processDetections(
        locations: Array<Array<FloatArray>>,
        classes: Array<FloatArray>,
        scores: Array<FloatArray>,
        numDetections: FloatArray
    ): List<Detection> {
        val detections = mutableListOf<Detection>()
        for (i in 0 until numDetections[0].toInt()) {
            if (scores[0][i] > 0.1 && classes[0][i].toInt() == 1) {
                val box = locations[0][i]
                val detection = Detection(
                    RectF(
                        box[1], // left
                        box[0], // top
                        box[3], // right
                        box[2]  // bottom
                    ),
                    scores[0][i]
                )
                detections.add(detection)
            }
        }
        return detections
    }

    private fun updateOverlay(detections: List<Detection>, imageWidth: Int, imageHeight: Int) {
        // UI 쓰레드에서 뷰를 업데이트하도록 구현
        runOnUiThread {
            viewBinding.overlay.clear()
            detections.forEach { detection ->
                viewBinding.overlay.addBox(detection.box, imageWidth, imageHeight)
            }
            viewBinding.overlay.invalidate()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun initTFLiteModel() {
        try {
            val assetModel = "yolov8n_int8.tflite"
            val fileDescriptor: AssetFileDescriptor = assets.openFd(assetModel)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            val tfliteModel: MappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            val tfliteOptions = Interpreter.Options()
            tfliteOptions.setNumThreads(4) // 필요에 따라 스레드 수 증가
            tflite = Interpreter(tfliteModel, tfliteOptions)
        } catch (e: IOException) {
            Log.e(TAG, "모델 로드 실패", e)
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
