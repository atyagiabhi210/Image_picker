package com.example.image_picker

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.Image
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import android.view.Surface
import android.view.View
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.image_picker.databinding.ActivityMainBinding
import com.example.imageclassificationlivefeed.CameraConnectionFragment
import com.example.imageclassificationlivefeed.ImageUtils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.io.IOException


class MainActivity : AppCompatActivity(),OnImageAvailableListener {
      lateinit var  labeler:ImageLabeler
    //we initialise a variable galleryActivityLauncher and we register the things occuring in the activity and pass in the uri of the image into our image view
    var galleryActivityLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(), ActivityResultCallback {
            if (it.resultCode== RESULT_OK){
             // binding.imageView.setImageURI(it.data?.data)
              imageUri=it.data!!.data
              val inputImage:Bitmap?=uriToBitmap(imageUri!!)
              binding.imageView.setImageBitmap(inputImage)
                performImageLabelling(inputImage!!)
            }
        }
    )
    var cameraActivityLauncher:ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(), ActivityResultCallback {
            if (it.resultCode== RESULT_OK){
               // binding.imageView.setImageURI(it.data?.data)
                imageUri=it.data!!.data
                val inputImage:Bitmap?=uriToBitmap(imageUri!!)
                binding.imageView.setImageBitmap(inputImage)
                performImageLabelling(inputImage!!)
            }
        }
    )
    private fun openCamera() {
        val values=ContentValues()
        values.put(MediaStore.Images.Media.TITLE,"NEW PICTURE")
        values.put(MediaStore.Images.Media.DESCRIPTION,"PICTURE CAPTURED TO BE LABELLED")
        imageUri=contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        val cameraIntent=Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT,imageUri)

        cameraActivityLauncher.launch(cameraIntent)

    }

    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding=ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.buttonGallery.setOnClickListener {
            //first we will open the intent of gallery, we chose the action to pick then we pass media store and media's URI
            val galleryIntent=Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

            galleryActivityLauncher.launch(galleryIntent)
        }
        //INITIALIZE IMAGE LABELLER
        // To use default options:
         labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

// Or, to set the minimum confidence required:
// val options = ImageLabelerOptions.Builder()
//     .setConfidenceThreshold(0.7f)
//     .build()
// val labeler = ImageLabeling.getClient(options)
        binding.buttonCamera.setOnClickListener {
            binding.imageView.visibility=View.VISIBLE
            binding.container.visibility=View.INVISIBLE
            //CODE TO ASK FOR PERMISSIONS DYNAMICALLY
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M){
                if (checkSelfPermission(android.Manifest.permission.CAMERA)==PackageManager.PERMISSION_DENIED){
                    val permission= arrayOf<String>(
                        android.Manifest.permission.CAMERA,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                    requestPermissions(permission,112)
                }
                else{
                    openCamera()
                    processImage()
                }
            }else{
                openCamera()
                processImage()
            }

        }
        binding.buttonLive.setOnClickListener {
            binding.imageView.visibility= View.GONE
            binding.container.visibility=View.VISIBLE
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M){
                if (checkSelfPermission(android.Manifest.permission.CAMERA)==PackageManager.PERMISSION_DENIED){
                    val permission= arrayOf<String>(
                        android.Manifest.permission.CAMERA,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                    requestPermissions(permission,112)
                }
                else{
                   setFragment()
                    processImage()

                }
            }else{
                setFragment()
                processImage()
            }

        }

    }
    // the function we will use to perform image labelling
    private fun performImageLabelling(input:Bitmap){
        val image = InputImage.fromBitmap(input, 0)
        labeler.process(image)
            .addOnSuccessListener { labels ->
                // Task completed successfully
                binding.liveimageTv.text=" "
                for (label in labels) {
                    val text = label.text
                    val confidence = label.confidence
                    val index = label.index
                    binding.liveimageTv.append(text+"  "+confidence+"\n")
                }
            }
            .addOnFailureListener { e ->
                // Task failed with an exception
                // ...
            }
    }
    var imageUri:Uri?=null


    var previewHeight = 0;
    var previewWidth = 0
    var sensorOrientation = 0;
    //TODO fragment which show llive footage from camera
    protected fun setFragment() {
        val manager =
            getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var cameraId: String? = null
        try {
            cameraId = manager.cameraIdList[0]
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        val fragment: CameraConnectionFragment
        val camera2Fragment = CameraConnectionFragment.newInstance(
            object :
                CameraConnectionFragment.ConnectionCallback {
                override fun onPreviewSizeChosen(size: Size?, rotation: Int) {
                    previewHeight = size!!.height
                    previewWidth = size.width
                    sensorOrientation = rotation - getScreenOrientation()
                }
            },
            this,
            R.layout.camera_fragment,
            Size(640, 480)
        )
        camera2Fragment.setCamera(cameraId)
        fragment = camera2Fragment
        fragmentManager.beginTransaction().replace(R.id.container, fragment).commit()
    }
    protected fun getScreenOrientation(): Int {
        return when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_270 -> 270
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_90 -> 90
            else -> 0
        }
    }
    //TODO getting frames of live camera footage and passing them to model
    private var isProcessingFrame = false
    private val yuvBytes = arrayOfNulls<ByteArray>(3)
    private var rgbBytes: IntArray? = null
    private var yRowStride = 0
    private var postInferenceCallback: Runnable? = null
    private var imageConverter: Runnable? = null
    private var rgbFrameBitmap: Bitmap? = null



    private fun processImage() {
        imageConverter!!.run()
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        rgbFrameBitmap?.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight)
        postInferenceCallback!!.run()
    }

    protected fun fillBytes(
        planes: Array<Image.Plane>,
        yuvBytes: Array<ByteArray?>
    ) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (i in planes.indices) {
            val buffer = planes[i].buffer
            if (yuvBytes[i] == null) {
                yuvBytes[i] = ByteArray(buffer.capacity())
            }
            buffer[yuvBytes[i]]
        }
    }

//we use this function to get each frame of the image and process it later on
    override fun onImageAvailable(reader: ImageReader?) {
    if(previewWidth==0||previewHeight==0){
        return
    }
    if (rgbBytes==null){
        rgbBytes=IntArray(previewHeight*previewWidth)

    }
    try {
        val image= reader?.acquireLatestImage() ?:return
        if (isProcessingFrame){
            image.close()
            return
        }
        isProcessingFrame=true
        val planes=image.planes
        fillBytes(planes,yuvBytes)
        yRowStride=planes[0].rowStride
        val uvRowStride=planes[1].rowStride
        val uvPixelStride=planes[1].rowStride
        imageConverter= Runnable {
            ImageUtils.convertYUV420ToARGB8888(
                yuvBytes[0]!!,
                yuvBytes[1]!!,
                yuvBytes[2]!!,
                previewWidth,
                previewHeight,
                yRowStride,
                uvRowStride,
                uvPixelStride,
                rgbBytes!!
            )
        }
        postInferenceCallback= Runnable {
            image.close()
            isProcessingFrame=false

        }
        processImage()

    }catch (e:Exception){
        return
    }

    }
    private fun uriToBitmap(selectedFileUri: Uri): Bitmap? {
        try {
            val parcelFileDescriptor = contentResolver.openFileDescriptor(selectedFileUri, "r")
            val fileDescriptor = parcelFileDescriptor!!.fileDescriptor
            val image = BitmapFactory.decodeFileDescriptor(fileDescriptor)
            parcelFileDescriptor.close()
            return image
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }
}