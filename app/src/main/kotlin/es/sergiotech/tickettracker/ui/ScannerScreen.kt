package es.sergiotech.tickettracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import es.sergiotech.tickettracker.R
import es.sergiotech.tickettracker.domain.newId
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(back: () -> Unit, captured: (Uri) -> Unit) {
    val context = LocalContext.current
    var permission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val request =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            permission = it
        }
    var error by remember { mutableStateOf(false) }
    var image by remember { mutableStateOf<File?>(null) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var busy by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(label(R.string.scan)) },
                navigationIcon = {
                    IconButton(onClick = back, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, label(R.string.back))
                    }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label(R.string.camera_help))
            if (!permission) {
                Text(label(R.string.camera_permission))
                Button(onClick = { request.launch(Manifest.permission.CAMERA) }) {
                    Text(label(R.string.allow_camera))
                }
            } else if (image == null) {
                CameraPreview(
                    Modifier.weight(1f).fillMaxWidth(),
                    { capture = it },
                    { error = true },
                )
                Button(
                    onClick = {
                        val camera = capture ?: return@Button
                        busy = true
                        val directory = File(context.cacheDir, "captures").apply { mkdirs() }
                        val output = File(directory, "${newId()}.jpg")
                        camera.takePicture(
                            ImageCapture.OutputFileOptions.Builder(output).build(),
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                    busy = false
                                    image = output
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    busy = false
                                    error = true
                                    output.delete()
                                }
                            },
                        )
                    },
                    enabled = capture != null && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(label(R.string.capture))
                }
            } else {
                val bitmap by
                    produceState<android.graphics.Bitmap?>(null, image?.path) {
                        value =
                            withContext(Dispatchers.IO) {
                                BitmapFactory.decodeFile(
                                    image!!.path,
                                    BitmapFactory.Options().apply { inSampleSize = 4 },
                                )
                            }
                    }
                bitmap?.let {
                    Image(
                        it.asImageBitmap(),
                        label(R.string.capture),
                        Modifier.weight(1f).fillMaxWidth(),
                    )
                }
                Button(
                    onClick = {
                        image?.let {
                            captured(
                                FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.documents",
                                    it,
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(label(R.string.save))
                }
                TextButton(
                    onClick = {
                        image?.delete()
                        image = null
                        capture = null
                        error = false
                    }
                ) {
                    Text(label(R.string.retry))
                }
            }
            if (error) Text(label(R.string.camera_error), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun CameraPreview(modifier: Modifier, ready: (ImageCapture) -> Unit, error: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val view = remember { PreviewView(context) }
    DisposableEffect(lifecycle) {
        val future = ProcessCameraProvider.getInstance(context)
        val preview = Preview.Builder().build().apply { surfaceProvider = view.surfaceProvider }
        val capture =
            ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
        var provider: ProcessCameraProvider? = null
        var disposed = false
        future.addListener(
            {
                if (!disposed)
                    try {
                        provider = future.get()
                        provider!!.bindToLifecycle(
                            lifecycle,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            capture,
                        )
                        ready(capture)
                    } catch (_: Exception) {
                        error()
                    }
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            disposed = true
            provider?.unbind(preview, capture)
        }
    }
    AndroidView(factory = { view }, modifier = modifier)
}
