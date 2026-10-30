package he.travelphoto.llo.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun rememberPhotoPicker(onPicked: (List<Uri>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val data = result.data ?: return@rememberLauncherForActivityResult
        val uris = buildList {
            val clip = data.clipData
            if (clip != null) {
                for (index in 0 until clip.itemCount) {
                    clip.getItemAt(index).uri?.let(::add)
                }
            } else {
                data.data?.let(::add)
            }
        }
        if (uris.isNotEmpty()) onPicked(uris)
    }
    val launchPicker: () -> Unit = {
        val primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                type = "image/*"
                putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 80)
                putExtra(EXTRA_REQUEST_LOCATION_METADATA_ACCESS, true)
            }
        } else {
            documentIntent()
        }
        runCatching { picker.launch(primary) }
            .onFailure { picker.launch(documentIntent()) }
    }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { launchPicker() }

    return remember(picker, permission) {
        {
            val needsLocationPermission = Build.VERSION.SDK_INT in Build.VERSION_CODES.TIRAMISU..36 &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_MEDIA_LOCATION,
                ) != PackageManager.PERMISSION_GRANTED
            if (needsLocationPermission) {
                // Android 13–16 redact location metadata for Picker URIs unless the app holds
                // ACCESS_MEDIA_LOCATION. Android 17 replaced this with an in-picker consent.
                permission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
            } else {
                launchPicker()
            }
        }
    }
}

private fun documentIntent(): Intent =
    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "image/*"
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    }

private const val EXTRA_REQUEST_LOCATION_METADATA_ACCESS =
    "android.provider.extra.REQUEST_LOCATION_METADATA_ACCESS"

@Composable
fun rememberArchivePicker(onPicked: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onPicked(uri)
        }
    }
    return remember(launcher) {
        { launcher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
    }
}

@Composable
fun rememberFolderPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            onPicked(uri)
        }
    }
    return remember(launcher) { { launcher.launch(null) } }
}
