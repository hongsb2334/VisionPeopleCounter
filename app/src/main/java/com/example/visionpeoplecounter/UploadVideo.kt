package com.example.visionpeoplecounter

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.visionpeoplecounter.databinding.ActivityUploadVideoBinding

class UploadVideo : AppCompatActivity() {

    lateinit var binding: ActivityUploadVideoBinding
    private val STORAGE_PERMISSION = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE)
    private val STORAGE_PERMISSION_FLAG = 100
    private lateinit var v : VideoView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUploadVideoBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        binding.openAlbum.setOnClickListener{
            checkPermission(STORAGE_PERMISSION, STORAGE_PERMISSION_FLAG)
        }
        v = findViewById(R.id.videoView)
        v.setOnPreparedListener {
                m : MediaPlayer ->
            m.setOnVideoSizeChangedListener { _: MediaPlayer?, _: Int, _: Int ->
                val mediaController = MediaController(this)
                v.setMediaController(mediaController)
                mediaController.setAnchorView(v)
            }
            v.start()
        }
    }

    private fun checkPermission(permissions : Array<String>, flag : Int){
        val permissionResult = ContextCompat.checkSelfPermission(this, permissions[0])
        when(permissionResult){
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
    ) {super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when(requestCode){
            STORAGE_PERMISSION_FLAG -> {
                if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    // Go Main Function
                }else{
                    Toast.makeText(this, "Storage Permission Denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(resultCode == Activity.RESULT_OK&&data!=null) {
            if(requestCode == 1) {
                var videoUri : Uri? = data?.data
                v.setVideoPath(""+videoUri)
            }
        }
    }
}