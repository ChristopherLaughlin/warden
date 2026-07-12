package com.warden.blocker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.warden.blocker.usage.AppsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads and shows an app's icon off the main thread, with a glyph fallback. */
@Composable
fun AppIcon(packageName: String, fallback: String = "📱", modifier: Modifier = Modifier.size(40.dp)) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.Default) { AppsHelper.loadIcon(context, packageName) }
    }
    val bmp = icon
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = null, modifier = modifier)
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(fallback, style = MaterialTheme.typography.titleLarge)
        }
    }
}
