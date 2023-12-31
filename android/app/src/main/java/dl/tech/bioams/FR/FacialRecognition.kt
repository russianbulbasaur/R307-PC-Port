package dl.tech.bioams.FR;

import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import android.os.*
import android.provider.DocumentsContract
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.util.Size
import android.view.View
import android.view.WindowInsets
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import dl.tech.bioams.FR.model.FaceNetModel
import dl.tech.bioams.FR.model.Models
import dl.tech.bioams.R
import dl.tech.bioams.databinding.ActivityFacialRecognitionBinding
import java.io.*
import java.util.concurrent.Executors


class FacialRecognition : AppCompatActivity(),Callback {

    private var isSerializedDataStored = false

    // Serialized data will be stored ( in app's private storage ) with this filename.
    private val SERIALIZED_DATA_FILENAME = "image_data"

    // Shared Pref key to check if the data was stored.
    private val SHARED_PREF_IS_DATA_STORED_KEY = "is_data_stored"

    private lateinit var activityMainBinding : ActivityFacialRecognitionBinding
    private lateinit var previewView : PreviewView
    private lateinit var frameAnalyser  : FrameAnalyser
    private lateinit var faceNetModel : FaceNetModel
    private lateinit var fileReader : FileReader
    private lateinit var cameraProviderFuture : ListenableFuture<ProcessCameraProvider>
    private lateinit var sharedPreferences: SharedPreferences
    private var showDialog = true

    // <----------------------- User controls --------------------------->

    // Use the device's GPU to perform faster computations.
    // Refer https://www.tensorflow.org/lite/performance/gpu
    private val useGpu = true

    // Use XNNPack to accelerate inference.
    // Refer https://blog.tensorflow.org/2020/07/accelerating-tensorflow-lite-xnnpack-integration.html
    private val useXNNPack = true

    // You may the change the models here.
    // Use the model configs in Models.kt
    // Default is Models.FACENET ; Quantized models are faster
    private val modelInfo = Models.FACENET

    // Camera Facing
    private val cameraFacing = CameraSelector.LENS_FACING_FRONT

    // <---------------------------------------------------------------->


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Remove the status bar to have a full screen experience
        // See this answer on SO -> https://stackoverflow.com/a/68152688/10878733
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.windowInsetsController!!
                .hide( WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        }
        else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        }
        activityMainBinding = ActivityFacialRecognitionBinding.inflate( layoutInflater )
        setContentView( activityMainBinding.root )

        previewView = activityMainBinding.previewView
        // Necessary to keep the Overlay above the PreviewView so that the boxes are visible.
        val h : Handler = object:Handler(Looper.getMainLooper()){
            override fun handleMessage(msg: Message) {
                val name:String? = msg.data.getString("name")
                if(name==null || name.toString().trim() == "Unknown" || !showDialog){
                    return
                }
                runOnUiThread(Runnable {
                    showAlert(name)
                })
            }
        };
        faceNetModel = FaceNetModel( this , modelInfo , useGpu , useXNNPack )
        frameAnalyser = FrameAnalyser( this , faceNetModel ,h)
        fileReader = FileReader( faceNetModel )


        // We'll only require the CAMERA permission from the user.
        // For scoped storage, particularly for accessing documents, we won't require WRITE_EXTERNAL_STORAGE or
        // READ_EXTERNAL_STORAGE permissions. See https://developer.android.com/training/data-storage
        if ( ActivityCompat.checkSelfPermission( this , Manifest.permission.CAMERA ) != PackageManager.PERMISSION_GRANTED ) {
            requestCameraPermission()
        }
        else {
            startCameraPreview()
        }

        sharedPreferences = getSharedPreferences( getString(R.string.app_name) , Context.MODE_PRIVATE )
        isSerializedDataStored = sharedPreferences.getBoolean( SHARED_PREF_IS_DATA_STORED_KEY , false )
        if ( !isSerializedDataStored ) {
            launchChooseDirectoryIntent()
        }
        else {
            val alertDialog = AlertDialog.Builder( this ).apply {
                setTitle( "Pre trained model exists")
                setMessage( "Load it?" )
                setCancelable( false )
                setNegativeButton( "Yes") { dialog, which ->
                    dialog.dismiss()
                    frameAnalyser.faceList = loadSerializedImageData()
                }
                setPositiveButton( "No") { dialog, which ->
                    dialog.dismiss()
                    launchChooseDirectoryIntent()
                }
                create()
            }
            alertDialog.show()
        }

    }

    private fun showAlert(name:String){
        showDialog = false;
        val ab = android.app.AlertDialog.Builder(this);
        ab.setMessage("Hi $name");
        ab.setOnCancelListener(DialogInterface.OnCancelListener {
            showDialog = true;
        })
        ab.create().show();
    }

    // ---------------------------------------------- //

    // Attach the camera stream to the PreviewView.
    private fun startCameraPreview() {
        cameraProviderFuture = ProcessCameraProvider.getInstance( this )
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider) },
            ContextCompat.getMainExecutor(this) )
    }

    private fun bindPreview(cameraProvider : ProcessCameraProvider) {
        val preview : Preview = Preview.Builder().build()
        val cameraSelector : CameraSelector = CameraSelector.Builder()
            .requireLensFacing( cameraFacing )
            .build()
        preview.setSurfaceProvider( previewView.surfaceProvider )
        val imageFrameAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size( 480, 640 ) )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        imageFrameAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), frameAnalyser )
        cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, preview , imageFrameAnalysis  )
    }

    // We let the system handle the requestCode. This doesn't require onRequestPermissionsResult and
    // hence makes the code cleaner.
    // See the official docs -> https://developer.android.com/training/permissions/requesting#request-permission
    private fun requestCameraPermission() {
        cameraPermissionLauncher.launch( Manifest.permission.CAMERA )
    }

    private val cameraPermissionLauncher = registerForActivityResult( ActivityResultContracts.RequestPermission() ) {
        isGranted ->
        if ( isGranted ) {
            startCameraPreview()
        }
        else {
            val alertDialog = AlertDialog.Builder( this ).apply {
                setTitle( "Camera Permission")
                setCancelable( false )
                setPositiveButton( "Grant" ) { dialog, which ->
                    dialog.dismiss()
                    requestCameraPermission()
                }
                setNegativeButton( "Dismiss" ) { dialog, which ->
                    dialog.dismiss()
                    finish()
                }
                create()
            }
            alertDialog.show()
        }

    }






    private fun launchChooseDirectoryIntent() {
        val intent = Intent( Intent.ACTION_OPEN_DOCUMENT_TREE )
        // startForActivityResult is deprecated.
        // See this SO thread -> https://stackoverflow.com/questions/62671106/onactivityresult-method-is-deprecated-what-is-the-alternative
        directoryAccessLauncher.launch( intent )
    }


    // Read the contents of the select directory here.
    // The system handles the request code here as well.
    // See this SO question -> https://stackoverflow.com/questions/47941357/how-to-access-files-in-a-directory-given-a-content-uri
    private val directoryAccessLauncher = registerForActivityResult( ActivityResultContracts.StartActivityForResult() ) {
        val dirUri = it.data?.data ?: return@registerForActivityResult
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(
                dirUri,
                DocumentsContract.getTreeDocumentId( dirUri )
            )
        val tree = DocumentFile.fromTreeUri(this, childrenUri)
        val images = ArrayList<Pair<String,Bitmap>>()
        var errorFound = false
        if ( tree!!.listFiles().isNotEmpty()) {
            for ( doc in tree.listFiles() ) {
                if ( doc.isDirectory && !errorFound ) {
                    val name = doc.name!!
                    for ( imageDocFile in doc.listFiles() ) {
                        try {
                            images.add( Pair( name , getFixedBitmap( imageDocFile.uri ) ) )
                        }
                        catch ( e : Exception ) {
                            errorFound = true
                            Log.e( "","Could not parse an image in $name directory. Make sure that the file structure is " +
                                    "as described in the README of the project and then restart the app." )
                            break
                        }
                    }

                    Log.e( "","Found ${doc.listFiles().size} images in $name directory" )
                }
                else {
                    errorFound = true
                    Log.e( "","The selected folder should contain only directories. Make sure that the file structure is " +
                            "as described in the README of the project and then restart the app." )
                }
            }
        }
        else {
            errorFound = true
            Log.e( "","The selected folder doesn't contain any directories. Make sure that the file structure is " +
                    "as described in the README of the project and then restart the app." )
        }
        if ( !errorFound ) {
            fileReader.run( images , fileReaderCallback )
            Log.e( "","Detecting faces in ${images.size} images ..." )
        }
        else {
            val alertDialog = AlertDialog.Builder( this ).apply {
                setTitle( "Select a valid directory")
                setCancelable( false )
                setPositiveButton( "Choose") { dialog, which ->
                    dialog.dismiss()
                    launchChooseDirectoryIntent()
                }
                setNegativeButton( "Cancel" ){ dialog , which ->
                    dialog.dismiss()
                    finish()
                }
                create()
            }
            alertDialog.show()
        }
    }


    // Get the image as a Bitmap from given Uri and fix the rotation using the Exif interface
    // Source -> https://stackoverflow.com/questions/14066038/why-does-an-image-captured-using-camera-intent-gets-rotated-on-some-devices-on-a
    private fun getFixedBitmap( imageFileUri : Uri ) : Bitmap {
        var imageBitmap = BitmapUtils.getBitmapFromUri(contentResolver, imageFileUri)
        val exifInterface = ExifInterface( contentResolver.openInputStream( imageFileUri )!! )
        imageBitmap =
            when (exifInterface.getAttributeInt( ExifInterface.TAG_ORIENTATION ,
                ExifInterface.ORIENTATION_UNDEFINED )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> BitmapUtils.rotateBitmap(imageBitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> BitmapUtils.rotateBitmap(imageBitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> BitmapUtils.rotateBitmap(imageBitmap, 270f)
                else -> imageBitmap
            }
        return imageBitmap
    }


    // ---------------------------------------------- //


    private val fileReaderCallback = object : FileReader.ProcessCallback {
        override fun onProcessCompleted(data: ArrayList<Pair<String, FloatArray>>, numImagesWithNoFaces: Int) {
            frameAnalyser.faceList = data
            saveSerializedImageData( data )
        }
    }


    private fun saveSerializedImageData(data : ArrayList<Pair<String,FloatArray>> ) {
        val serializedDataFile = File( filesDir , SERIALIZED_DATA_FILENAME )
        ObjectOutputStream( FileOutputStream( serializedDataFile )  ).apply {
            writeObject( data )
            flush()
            close()
        }
        sharedPreferences.edit().putBoolean( SHARED_PREF_IS_DATA_STORED_KEY , true ).apply()
    }


    private fun loadSerializedImageData() : ArrayList<Pair<String,FloatArray>> {
        val serializedDataFile = File( filesDir , SERIALIZED_DATA_FILENAME )
        val objectInputStream = ObjectInputStream( FileInputStream( serializedDataFile ) )
        val data = objectInputStream.readObject() as ArrayList<Pair<String,FloatArray>>
        objectInputStream.close()
        return data
    }

    override fun callback(name: String) {
        showAlert(name);
    }


}


interface Callback{
    fun callback(name:String);
}