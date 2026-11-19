package com.mapledger.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mapledger.app.ui.theme.Good
import com.mapledger.app.util.PhotoStore
import java.io.File

/** Capture or pick an evidence photo; the image is stored privately and its path returned. */
@Composable
fun PhotoEvidence(label: String, path: String?, prefix: String, onPath: (String?) -> Unit) {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<File?>(null) }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pending
        if (ok && f != null && f.length() > 0) onPath(f.absolutePath) else f?.delete()
        pending = null
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) onPath(PhotoStore.importPicked(ctx, uri, prefix))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            val (f, uri) = PhotoStore.newCaptureTarget(ctx, prefix); pending = f; take.launch(uri)
        }) { Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text(label) }
        OutlinedButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Icon(Icons.Default.PhotoLibrary, null)
        }
        if (path != null) Icon(Icons.Default.CheckCircle, "Photo attached", tint = Good)
    }
}
