package com.blacklog.camerax

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.blacklog.camerax.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.camera.core.Camera as Camera

class MainActivity : AppCompatActivity() {

    // ViewBinding
    lateinit private var binding : ActivityMainBinding
    private var preview : Preview? = null
    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    // CameraController
    private var camera : Camera? = null
    private var cameraController : CameraControl? = null
    private var cameraInfo: CameraInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        startCamera()
        binding.btnTakePicture.setOnClickListener {
            takePhoto()
        }
        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.btnTorch.setOnClickListener {
            when(cameraInfo?.torchState?.value){
                TorchState.ON -> {
                    cameraController?.enableTorch(false)
                    binding.btnTorch.text = "Torch ON"
                }
                TorchState.OFF -> {
                    cameraController?.enableTorch(true)
                    binding.btnTorch.text = "Torch OFF"
                }
            }
        }

        binding.btn1X.setOnClickListener {
            cameraController?.setZoomRatio(1F)
        }
        binding.btn2X.setOnClickListener {
            cameraController?.setZoomRatio(2F)
        }
        binding.btn5X.setOnClickListener {
            cameraController?.setZoomRatio(5F)
        }
        binding.btn10X.setOnClickListener {
            cameraController?.setZoomRatio(8F)
        }

//        binding.txtZoomState.setOnClickListener {
//            binding.txtZoomState.text = cameraInfo?.zoomState.currentZoomRatio.toString()
//            val zoomState = cameraInfo?.getZoomState()
//        }
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return
        // Create time-stamped output file to hold the image
        val photoFile = File(
                outputDirectory,
                newJpgFileName())
        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        Log.d("CameraX-Debug", "Photo capture failed: ${exc.message}", exc)
                    }
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val savedUri = Uri.fromFile(photoFile)
                        val msg = "Photo capture succeeded: $savedUri"
                        Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                        Log.d("CameraX-Debug", msg)

                        Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).also{
                            it.data = savedUri
                            sendBroadcast(it)
                        }
                    }
                })
    }
    // viewFinder ?????? : Preview
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            // Preview
            preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                    }
            // ImageCapture
            imageCapture = ImageCapture.Builder()
                    .build()
            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()
                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageCapture)

                cameraController = camera!!.cameraControl
                cameraInfo = camera!!.cameraInfo

                cameraInfo!!.zoomState.observe(this, androidx.lifecycle.Observer {
                    val currentZoomRatio = it.zoomRatio
                    Log.d("MyCameraXBasic", currentZoomRatio.toString())
                    binding.txtZoomState.text = currentZoomRatio.toString()
                    binding.txtMaxZoom.text = it.maxZoomRatio.toString()
                    binding.txtMinZoom.text = it.minZoomRatio.toString()
                })

            } catch(exc: Exception) {
                Log.d("CameraX-Debug", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun newJpgFileName() : String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA)
        val filename = sdf.format(System.currentTimeMillis())
        return "${filename}.jpg"
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply {
                if(!this.exists()){
                    mkdirs()
                }
            }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir
        else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}