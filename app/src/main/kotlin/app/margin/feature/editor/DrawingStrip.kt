package app.margin.feature.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.margin.R
import app.margin.domain.drawing.DrawingCodec
import app.margin.domain.model.Attachment
import app.margin.feature.drawing.DrawingImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun Attachment.isDrawing() = mimeType == DrawingCodec.MIME

/** Drawings and clipart belonging to the note, as tappable previews; tap to keep drawing. */
@Composable
fun DrawingStrip(items: List<Attachment>, readOnly: Boolean, onOpen: (Attachment) -> Unit, onNew: () -> Unit, onRemove: (String) -> Unit) {
    Column(Modifier.padding(top = 16.dp)) {
        HorizontalDivider()
        Text(stringResource(R.string.drawings), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items.forEach { a -> DrawingThumb(a, readOnly, { onOpen(a) }, { onRemove(a.id) }) }
            if (!readOnly) Surface(
                shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(width = 120.dp, height = 170.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onNew),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(stringResource(R.string.insert_drawing), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

@Composable
private fun DrawingThumb(a: Attachment, readOnly: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(null, a.storagePath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val f = File(File(context.filesDir, "attachments"), a.storagePath)
                DrawingImages.bitmap(context, DrawingCodec.decode(f.readText()), 360)
            }.getOrNull()
        }
    }
    var confirm by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = RoundedCornerShape(12.dp), shadowElevation = 3.dp, color = Color.White,
            modifier = Modifier.width(120.dp).height(170.dp).border(1.dp, Color(0x22000000), RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClickLabel = stringResource(R.string.open), onClick = onOpen),
        ) {
            val b = bmp
            if (b != null) Image(b.asImageBitmap(), a.fileName, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Draw, a.fileName, tint = Color.Gray) }
        }
        if (!readOnly) IconButton(onClick = { confirm = true }, modifier = Modifier.align(Alignment.TopEnd).size(36.dp)) {
            Icon(Icons.Outlined.Close, stringResource(R.string.remove_attachment), Modifier.size(18.dp), tint = Color.Gray)
        }
    }
    if (confirm) app.margin.ui.components.ConfirmDialog(stringResource(R.string.remove_attachment), stringResource(R.string.remove_attachment_message, a.fileName),
        stringResource(R.string.remove), { onRemove() }, { confirm = false }, destructive = true)
}
