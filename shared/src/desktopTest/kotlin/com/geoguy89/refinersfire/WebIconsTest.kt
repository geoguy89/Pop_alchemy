package com.geoguy89.refinersfire

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.geoguy89.refinersfire.gfx.AppIcon.drawAppIcon
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import java.io.File

/**
 * Renders the web version's home-screen icons from the app icon artwork, square and edge to edge (iOS rounds the
 * corners itself and would fill transparent ones with black). Run after changing the icon; the PNGs are checked in.
 */
class WebIconsTest {
    @Test
    fun renderWebIcons() {
        val out = File("src/wasmJsMain/resources/icons").apply { mkdirs() }
        for ((name, px) in listOf("icon-192.png" to 192, "icon-512.png" to 512, "apple-touch-icon.png" to 180)) {
            ImageComposeScene(px, px, Density(1f)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Brush.radialGradient(listOf(Color(0xFF4A2A12), Color(0xFF140C08)), center, size.minDimension * 0.75f))
                    // The artwork with a little breathing room, so a maskable crop never cuts the ring.
                    val inset = size.minDimension * 0.06f
                    drawContext.transform.inset(inset, inset, inset, inset)
                    drawAppIcon()
                    drawContext.transform.inset(-inset, -inset, -inset, -inset)
                }
            }.use { scene ->
                File(out, name).writeBytes(scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes)
            }
        }
    }
}
