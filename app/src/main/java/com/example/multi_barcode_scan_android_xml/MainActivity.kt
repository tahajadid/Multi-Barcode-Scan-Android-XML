package com.example.multi_barcode_scan_android_xml

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.util.DisplayMetrics
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.multi_barcode_scan_android_xml.databinding.ActivityMainBinding
import com.google.mlkit.vision.barcode.Barcode
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// This is an array of all the permission specified in the manifest.
val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
const val RATIO_4_3_VALUE = 4.0 / 3.0
const val RATIO_16_9_VALUE = 16.0 / 9.0
typealias BarcodeAnalyzerListener = (barcode: MutableList<Barcode>) -> Unit

class MainActivity : AppCompatActivity(), BarcodeClickListener {

    companion object {
        lateinit var activityInstance: MainActivity
    }

    var actualValues = mutableListOf<String>()
    var isFirst = true

    private val executor by lazy {
        Executors.newSingleThreadExecutor()
    }

    /**
     * Process result from permission request dialog box, has the request
     * been granted? If yes, start Camera. Otherwise display a toast
     */
    private val multiPermissionCallback =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
            if (map.entries.size < 1) {
                Toast.makeText(this, "Please Accept all the permissions", Toast.LENGTH_SHORT).show()
            } else {
                binding.viewFinder.post {
                    startCamera()
                }
            }
        }

    private var processingBarcode = AtomicBoolean(false)
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraInfo: CameraInfo
    private lateinit var cameraControl: CameraControl

    private lateinit var singleBarCodeAdapter: SingleBarCodeAdapter
    private lateinit var resultAdapter: SingleBarCodeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        activityInstance = this

        // Request camera permissions
        multiPermissionCallback.launch(
            REQUIRED_PERMISSIONS,
        )
        if (allPermissionsGranted()) {
            binding.viewFinder.post {
                // Initialize graphics overlay
                startCamera()
            }
        } else {
            requestAllPermissions()
        }
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun startCamera() {
        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { binding.viewFinder.display.getRealMetrics(it) }
        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        val rotation = binding.viewFinder.display.rotation

        // Bind the CameraProvider to the LifeCycleOwner
        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            // CameraProvider
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .setTargetAspectRatio(screenAspectRatio)
                // Set initial target rotation
                .setTargetRotation(rotation)
                .build()

            preview.setSurfaceProvider(binding.viewFinder.surfaceProvider)

            // ImageAnalysis
            val textBarcodeAnalyzer = initializeAnalyzer(screenAspectRatio, rotation)
            cameraProvider.unbindAll()

            try {
                val camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    textBarcodeAnalyzer,
                )
                cameraControl = camera.cameraControl
                cameraInfo = camera.cameraInfo
                cameraControl.setLinearZoom(0.5f)
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun requestAllPermissions() {
        multiPermissionCallback.launch(
            REQUIRED_PERMISSIONS,
        )
    }

    /**
     * Check if all permission specified in the manifest have been granted
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            this,
            it,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    private fun initializeAnalyzer(screenAspectRatio: Int, rotation: Int): UseCase {
        return ImageAnalysis.Builder()
            .setTargetAspectRatio(screenAspectRatio)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(rotation)
            .build()
            .also {
                it.setAnalyzer(
                    executor,
                    BarCodeCustomAnalyser { barcode ->
                        /**
                         * Change update  to true if you want to scan only one barcode or it will
                         * continue scanning after detecting for the first time
                         */
                        if (processingBarcode.compareAndSet(false, false)) {
                            onBarcodeDetected(barcode)
                        }
                    },
                )
            }
    }

    private fun onBarcodeDetected(barcodes: List<Barcode>) {
        if (isFirst) {
            if (barcodes.isNotEmpty()) {
                barcodes.forEach {
                    actualValues.add(it.rawValue.toString())
                }

                singleBarCodeAdapter = SingleBarCodeAdapter(this, actualValues, this)

                binding.listOfValues.apply {
                    layoutManager = LinearLayoutManager(context)
                    adapter = singleBarCodeAdapter
                }

                binding.listOfValues.visibility = View.VISIBLE
            } else {
                binding.listOfValues.visibility = View.GONE
            }
            isFirst = false
        } else {
            barcodes.forEach {
                if (!actualValues.contains(it.rawValue)) actualValues.add(it.rawValue)
            }
            if (actualValues.size > 1) startEndTimer()
            singleBarCodeAdapter.notifyDataSetChanged()
        }
    }

    private fun startEndTimer() {
        Handler().postDelayed({
            binding.viewFinder.post {
                binding.viewFinder.visibility = View.GONE
            }

            binding.viewFinder.visibility = View.GONE
            binding.listOfValues.visibility = View.GONE

            binding.resultCl.visibility = View.VISIBLE

            resultAdapter = SingleBarCodeAdapter(this, actualValues, this)

            binding.listOfResult.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = resultAdapter
            }
        }, 2000)
    }

    override fun onItemClick(position: Int, value: String) {
        binding.viewFinder.post {
            binding.viewFinder.visibility = View.GONE
        }

        binding.viewFinder.visibility = View.GONE
        binding.listOfValues.visibility = View.GONE

        binding.resultCl.visibility = View.VISIBLE
    }
}