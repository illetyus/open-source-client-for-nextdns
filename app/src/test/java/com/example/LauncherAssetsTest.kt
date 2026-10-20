package com.example

import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [24, 34])
class LauncherAssetsTest {
  @Test fun packagedLauncherAssetsResolveOnLegacyAndAdaptiveAndroid() {
    val context = ApplicationProvider.getApplicationContext<NextDnsApp>()
    for (resource in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
      val drawable = context.getDrawable(resource)!!
      assertTrue(drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0)
      if (Build.VERSION.SDK_INT >= 26) {
        assertTrue(drawable is AdaptiveIconDrawable)
        val adaptive = drawable as AdaptiveIconDrawable
        assertNotNull(adaptive.foreground)
        assertNotNull(adaptive.background)
        if (Build.VERSION.SDK_INT >= 33) assertNotNull(adaptive.monochrome)
      } else {
        assertTrue(drawable is BitmapDrawable)
        val bitmap = (drawable as BitmapDrawable).bitmap
        assertTrue(bitmap.width >= 48 && bitmap.height >= 48)
      }
    }
    assertNotNull(context.getDrawable(R.drawable.ic_client_network))
  }

  @Test fun launcherUsesAWhiteFieldAndABroadCenteredBlueShieldWithDnsSignals() {
    val context = ApplicationProvider.getApplicationContext<NextDnsApp>()
    val icon = context.getDrawable(R.mipmap.ic_launcher)!!

    if (Build.VERSION.SDK_INT >= 26) {
      val adaptive = icon as AdaptiveIconDrawable
      assertNotNull(adaptive.background)
      val background = context.getDrawable(R.drawable.ic_launcher_background)!!
      val backgroundColor = solidColor(background)
      assertNotNull("The adaptive launcher background must expose a solid color (${background.javaClass.name})", backgroundColor)
      assertWhite(backgroundColor!!)

      val foreground = render(context.getDrawable(R.drawable.ic_launcher_foreground)!!, 108)
      var minX = foreground.width
      var maxX = -1
      var minY = foreground.height
      var maxY = -1
      for (y in 0 until foreground.height) {
        for (x in 0 until foreground.width) {
          if (Color.alpha(foreground.getPixel(x, y)) > 0) {
            minX = minOf(minX, x); maxX = maxOf(maxX, x)
            minY = minOf(minY, y); maxY = maxOf(maxY, y)
          }
        }
      }
      val markWidth = maxX - minX + 1
      assertTrue("The shield and broad signal marks should fill more of the adaptive safe area (width=$markWidth, x=$minX..$maxX)", markWidth in 60..84)
      assertTrue("The launcher mark should be vertically centered", kotlin.math.abs((minY + maxY) / 2 - 54) <= 2)
      val center = foreground.getPixel(54, 54)
      assertTrue("A vivid blue shield should sit at the center", Color.blue(center) > 220 && Color.red(center) < 30 && Color.green(center) in 65..110)
    } else {
      val legacy = (icon as BitmapDrawable).bitmap
      assertWhite(legacy.getPixel(1, 1))
      val center = legacy.getPixel(legacy.width / 2, legacy.height / 2)
      assertTrue("Legacy launcher assets should also use the centered blue shield", Color.blue(center) > 220 && Color.red(center) < 30 && Color.green(center) in 65..110)
    }
  }

  @Test fun launcherUsesOneCompactWideAnglePairOfCurvedDnsLinesAroundTheShield() {
    val context = ApplicationProvider.getApplicationContext<NextDnsApp>()
    val parser = context.resources.getXml(R.drawable.ic_launcher_foreground)
    val signalPaths = mutableListOf<Triple<Float, String, String>>()
    try {
      while (parser.eventType != XmlPullParser.END_DOCUMENT) {
        if (parser.eventType == XmlPullParser.START_TAG && parser.name == "path") {
          val width = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "strokeWidth")
          val data = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "pathData")
          val color = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "strokeColor")
          if (width != null && data != null && color != null) signalPaths += Triple(width.toFloat(), data, color)
        }
        parser.next()
      }
    } finally {
      parser.close()
    }

    assertEquals("The compact icon should use one signal bracket on each side", 2, signalPaths.size)
    assertTrue("Signal strokes should stay visually lighter than the shield", signalPaths.all { it.first <= 2.5f })
    assertEquals(
      setOf("M34,36 C20,43 20,65 34,72", "M74,36 C88,43 88,65 74,72"),
      signalPaths.map { it.second }.toSet()
    )
    assertTrue(
      "Signal arcs should use NextDNS green",
      signalPaths.all { Color.parseColor(it.third) == Color.rgb(0, 128, 0) }
    )
  }

  private fun render(drawable: Drawable, size: Int): Bitmap =
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
      val canvas = Canvas(bitmap)
      drawable.setBounds(0, 0, size, size)
      drawable.draw(canvas)
    }

  private fun assertWhite(color: Int) {
    assertTrue("Expected a white launcher background, got #${Integer.toHexString(color)}", Color.red(color) >= 245 &&
      Color.green(color) >= 245 && Color.blue(color) >= 245 && Color.alpha(color) >= 245)
  }

  private fun solidColor(drawable: Drawable): Int? = when (drawable) {
    is ColorDrawable -> drawable.color
    is GradientDrawable -> drawable.color?.defaultColor
    is InsetDrawable -> drawable.drawable?.let(::solidColor)
    is LayerDrawable -> (0 until drawable.numberOfLayers)
      .firstNotNullOfOrNull { index -> solidColor(drawable.getDrawable(index)) }
    else -> null
  }
}
