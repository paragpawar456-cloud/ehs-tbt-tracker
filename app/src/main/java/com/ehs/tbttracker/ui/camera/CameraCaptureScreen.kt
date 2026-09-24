package com.ehs.tbttracker.ui.camera

import android.Manifest
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.io.File

/**
 * Full-screen CameraX capture. Location permission is requested alongside camera so the
 * watermark can include GPS; denying location still allows capture ("GPS unavailable").
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraCaptureScreen(onCaptured: (File) -> Unit, onClose: () -> Unit) {
    val permissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
    )
    val cameraGranted = permissions.permissions.first { it.permission == Manifest.permission.CAMERA }.status.isGranted
    LaunchedEffect(Unit) { if (!permissions.allPermissionsGranted) permissions.launchMultiplePermissionRequest() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (cameraGranted) {
            CameraPreview(onCaptured)
        } else {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Camera permission is needed to capture TBT evidence.", color = Color.White)
                Button(onClick = { permissions.launchMultiplePermissionRequest() }) { Text("Grant permission") }
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopStart)) {
            Icon(Icons.Filled.Close, "Close camera", tint = Color.White)
        }
    }
}

@Composable
private fun CameraPreview(onCaptured: (File) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var capturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }
    }
    LaunchedEffect(lifecycleOwner) { controller.bindToLifecycle(lifecycleOwner) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx -> PreviewView(ctx).apply { this.controller = controller; scaleType = PreviewView.ScaleType.FILL_CENTER } },
            modifier = Modifier.fillMaxSize(),
        )
        error?.let {
            Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).background(Color(0xAA000000)).padding(12.dp))
        }
        Box(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 32.dp).size(76.dp)
                .border(4.dp, Color.White, CircleShape).padding(8.dp)
                .background(if (capturing) Color.Gray else Color(0xFF10B981), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (capturing) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            } else {
                IconButton(
                    modifier = Modifier.fillMaxSize().testTag("camera_shutter"),
                    onClick = {
                        capturing = true
                        val file = File(context.cacheDir, "raw_${System.currentTimeMillis()}.jpg")
                        controller.takePicture(
                            ImageCapture.OutputFileOptions.Builder(file).build(),
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) = onCaptured(file)
                                override fun onError(e: ImageCaptureException) {
                                    capturing = false
                                    error = "Capture failed: ${e.message}"
                                }
                            },
                        )
                    },
                ) {}
            }
        }
    }
}
