package com.example.visionpeoplecounter

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.visionpeoplecounter.databinding.ActivityUploadVideoBinding
import org.tensorflow.lite.task.vision.detector.Detection
import com.example.visionpeoplecounter.ObjectDetectorHelper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class UploadVideo : AppCompatActivity(), ObjectDetectorHelper.DetectorListener {

    lateinit var binding: ActivityUploadVideoBinding
    private val STORAGE_PERMISSION = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    private val STORAGE_PERMISSION_FLAG = 100
    private lateinit var videoView : VideoView
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var objectDetectorHelper: ObjectDetectorHelper
    private lateinit var bitmapBuffer: Bitmap
    private lateinit var videoFrameExecutor: ExecutorService
    private val TAG = "UploadVideo"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUploadVideoBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        objectDetectorHelper = ObjectDetectorHelper(
            context = this,
            objectDetectorListener = this
        )

        videoFrameExecutor = Executors.newSingleThreadExecutor()
        binding.openAlbum.setOnClickListener {
            checkPermission(STORAGE_PERMISSION, STORAGE_PERMISSION_FLAG)
        }
        videoView = findViewById(R.id.videoView)
        videoView.setOnPreparedListener { m: MediaPlayer ->
            mediaPlayer = m
            m.setOnVideoSizeChangedListener { _: MediaPlayer?, _: Int, _: Int ->
                val mediaController = MediaController(this)
                videoView.setMediaController(mediaController)
                mediaController.setAnchorView(videoView)
            }
            mediaPlayer.setOnSeekCompleteListener {
                val frame = videoView.currentPosition
                processFrame(frame)
                /*videoView.start()*/
            }
            videoView.setOnCompletionListener {
                videoView.seekTo(0)
            }
            mediaPlayer.start()
        }
    }
    private fun checkPermission(permissions: Array<String>, flag: Int) {
        val permissionResult = ContextCompat.checkSelfPermission(this, permissions[0])
        when (permissionResult) {
            PackageManager.PERMISSION_GRANTED -> {
                var intent = Intent()
                intent.type = "video/*"
                intent.action = Intent.ACTION_GET_CONTENT
                startActivityForResult(intent, 1)
            }

            PackageManager.PERMISSION_DENIED -> {
                ActivityCompat.requestPermissions(this, permissions, flag)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            STORAGE_PERMISSION_FLAG -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Go Main Function
                } else {
                    Toast.makeText(this, "Storage Permission Denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && data != null) {
            if (requestCode == 1) {
                val videoUri: Uri? = data.data
                videoView.setVideoPath("" + videoUri)
                videoView.start()
            }
        }
    }

    private fun processFrame(frameTime: Int) {
        videoFrameExecutor.execute {
            try{
                mediaPlayer.pause()
                videoView.seekTo(frameTime)
                videoView.buildDrawingCache()
                val bitmap = videoView.drawingCache

                if (bitmap != null) {
                    objectDetectorHelper.detect(bitmap, 0)
                    /*detectObjectsFromVideo()*/
                }

                videoView.destroyDrawingCache()
                mediaPlayer.start()
            }
            catch (e: Exception) {
                Log.e(TAG, "Error processing frame: ${e.message}")
            }
        }
    }
    private fun detectObjectsFromVideo() {
        val frame = videoView.currentPosition // 현재 비디오의 위치에서 프레임을 가져옵니다.
        val bitmap = videoViewToBitmap(videoView, frame)
        val rotation = 0 // 비디오에서 가져온 프레임의 회전 정보를 설정

        objectDetectorHelper.detect(bitmap, rotation)
    }

    private fun videoViewToBitmap(videoView: VideoView, frameTime: Int): Bitmap {
        videoView.seekTo(frameTime)
        videoView.isDrawingCacheEnabled = true
        val bitmap = Bitmap.createBitmap(videoView.drawingCache)
        videoView.isDrawingCacheEnabled = false
        return bitmap
    }

    // Implement DetectorListener methods
    override fun onInitialized() {
        // No specific action needed for this example
    }

    override fun onError(error: String) {
        runOnUiThread {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResults(
        results: MutableList<Detection>?,
        inferenceTime: Long,
        imageHeight: Int,
        imageWidth: Int
    ) {
        runOnUiThread {
            if (results != null) {
                Toast.makeText(this, "Detected ${results.size} persons", Toast.LENGTH_SHORT).show()
            }
        }
    }



}