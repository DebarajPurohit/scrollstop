package com.scrollstop.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scrollstop.ui.theme.StopDoomScrollTheme
import com.scrollstop.ui.theme.ThemeMode

/**
 * Standardized application icon thumbnail with safe Drawable-to-Bitmap conversion,
 * fallback initial-letter avatar, and loading state placeholder.
 *
 * @param drawable Optional Android [Drawable] application icon.
 * @param appName Human-readable application name (e.g. "YouTube").
 * @param packageName Unique application package identifier (e.g. "com.google.android.youtube").
 * @param modifier Custom modifier.
 * @param size Icon width and height (defaults to 44dp).
 * @param shape Corner clipping shape (defaults to 12dp rounded corners).
 * @param isLoading Whether the icon or app metadata is actively loading.
 */
@Composable
fun AppIconThumbnail(
    drawable: Drawable?,
    appName: String? = null,
    packageName: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    isLoading: Boolean = false
) {
    val description = appName?.let { "$it application icon" }
        ?: packageName?.let { "$it application icon" }
        ?: "Application icon"

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            // Shimmer/placeholder state
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        } else {
            val imageBitmap = remember(drawable) {
                drawable?.toSafeImageBitmap()
            }

            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                // Fallback styled avatar icon with first letter
                val initialLetter = remember(appName, packageName) {
                    val candidate = appName?.trim()?.firstOrNull { it.isLetterOrDigit() }
                        ?: packageName?.split('.')?.lastOrNull()?.firstOrNull { it.isLetterOrDigit() }
                        ?: '?'
                    candidate.uppercaseChar().toString()
                }

                Surface(
                    modifier = Modifier.matchParentSize(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = shape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = initialLetter,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Converts an Android [Drawable] to a Compose [ImageBitmap] safely with fallback dimensions.
 */
fun Drawable.toSafeImageBitmap(): ImageBitmap? {
    return try {
        if (this is BitmapDrawable && this.bitmap != null && !this.bitmap.isRecycled) {
            this.bitmap.asImageBitmap()
        } else {
            val width = intrinsicWidth.coerceAtLeast(1).coerceAtMost(512)
            val height = intrinsicHeight.coerceAtLeast(1).coerceAtMost(512)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            setBounds(0, 0, canvas.width, canvas.height)
            draw(canvas)
            bitmap.asImageBitmap()
        }
    } catch (_: Exception) {
        null
    }
}

// =========================================================================
// Previews
// =========================================================================

@Preview(name = "AppIcon Fallback Light Theme", showBackground = true)
@Composable
private fun AppIconThumbnailLightPreview() {
    StopDoomScrollTheme(themeMode = ThemeMode.LIGHT) {
        AppIconThumbnail(
            drawable = null,
            appName = "Instagram"
        )
    }
}

@Preview(name = "AppIcon Fallback Dark Theme", showBackground = true)
@Composable
private fun AppIconThumbnailDarkPreview() {
    StopDoomScrollTheme(themeMode = ThemeMode.DARK) {
        AppIconThumbnail(
            drawable = null,
            appName = "YouTube"
        )
    }
}
