package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.CountryTrafficMetric
import com.example.i18n.AppStrings
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ln

@Composable
fun WorldMapChart(
  countryData: List<CountryTrafficMetric>,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current.applicationContext
  val mapState by produceState<WorldMapLoadState>(WorldMapLoadState.Loading, context) {
    value = try {
      WorldMapLoadState.Ready(loadWorldMapGeometry(context))
    } catch (cancel: CancellationException) {
      throw cancel
    } catch (_: Exception) {
      WorldMapLoadState.Failed
    }
  }
  val geometry = (mapState as? WorldMapLoadState.Ready)?.geometry
  val renderCache = remember(geometry) { geometry?.let(::WorldMapRenderCache) }
  var selectedCountryCode by remember(countryData) { androidx.compose.runtime.mutableStateOf(countryData.firstOrNull()?.code) }
  val selectedMetric = countryData.firstOrNull { it.code == selectedCountryCode }
  val countryName = selectedCountryCode?.let(::localizedCountryName)
  val mapColor = MaterialTheme.colorScheme.primary
  val noDataColor = MaterialTheme.colorScheme.surfaceVariant
  val borderColor = MaterialTheme.colorScheme.outlineVariant
  val maxQueries = countryData.maxOfOrNull { it.queries }?.coerceAtLeast(1L) ?: 1L
  val numberFormat = remember(AppStrings.locale) { NumberFormat.getIntegerInstance(AppStrings.locale) }
  val metricsByCode = remember(countryData) { countryData.associateBy(CountryTrafficMetric::code) }
  var countryMenuExpanded by remember { androidx.compose.runtime.mutableStateOf(false) }
  val selectableCountryCodes = remember(geometry, AppStrings.locale) {
    geometry?.countries.orEmpty().map(WorldCountryOutline::code).distinct()
      .sortedBy { localizedCountryName(it).lowercase(AppStrings.locale) }
  }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(180.dp)
        .clip(RoundedCornerShape(12.dp))
    ) {
      Canvas(
        modifier = Modifier
          .fillMaxSize()
          .testTag("world_country_map")
          .pointerInput(geometry) {
            detectTapGestures { tap ->
              val viewport = worldMapViewport(size.width.toFloat(), size.height.toFloat())
              if (viewport.width > 0f && viewport.height > 0f && viewport.contains(tap)) {
                val x = (tap.x - viewport.left) / viewport.width
                val y = (tap.y - viewport.top) / viewport.height
                geometry?.countryAt(x, y)?.let { selectedCountryCode = it }
              }
            }
          }
          .semantics {
            contentDescription = AppStrings.get(R.string.traffic_country_map_accessibility)
          }
      ) {
        val viewport = worldMapViewport(size.width, size.height)
        clipRect(viewport.left, viewport.top, viewport.right, viewport.bottom) {
          // Stroke's default miter limit is 4: include 4 * half-width plus
          // one antialias pixel, so a seam copy's sharp outline cannot vanish.
          renderCache?.paths(viewport, 1.8.dp.toPx() * 2f + 1f)?.forEach { country ->
            val metric = metricsByCode[country.code]
            val intensity = metric?.let {
              (ln(it.queries.coerceAtLeast(0L).toDouble() + 1.0) / ln(maxQueries.toDouble() + 1.0))
                .toFloat().coerceIn(0f, 1f)
            } ?: 0f
            val selected = country.code == selectedCountryCode
            val fill = if (metric == null) noDataColor else mapColor.copy(alpha = 0.24f + intensity * 0.68f)
            val stroke = if (selected) mapColor else borderColor

            country.paths.forEach { path ->
                drawPath(path, if (selected) mapColor else fill)
                drawPath(
                  path,
                  stroke,
                  style = Stroke(width = if (selected) 1.8.dp.toPx() else 0.55.dp.toPx())
                )
            }
          }
        }
      }

      when (mapState) {
        WorldMapLoadState.Loading -> CircularProgressIndicator(
          modifier = Modifier.align(androidx.compose.ui.Alignment.Center).size(24.dp).testTag("world_map_loading"),
          strokeWidth = 2.dp
        )
        WorldMapLoadState.Failed -> Text(
          text = AppStrings.get(R.string.traffic_map_unavailable),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.align(androidx.compose.ui.Alignment.Center).padding(16.dp).testTag("world_map_unavailable")
        )
        is WorldMapLoadState.Ready -> Unit
      }
    }

    Text(
      text = AppStrings.get(R.string.traffic_country_tap_hint),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontSize = 11.sp
    )

    Box(modifier = Modifier.fillMaxWidth()) {
      TextButton(
        onClick = { countryMenuExpanded = true },
        enabled = selectableCountryCodes.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().testTag("world_map_country_selector")
      ) {
        Text(
          text = AppStrings.get(
            R.string.traffic_country_selector,
            countryName ?: AppStrings.get(R.string.traffic_country_selector_placeholder)
          ),
          modifier = Modifier.fillMaxWidth()
        )
      }
      DropdownMenu(
        expanded = countryMenuExpanded,
        onDismissRequest = { countryMenuExpanded = false }
      ) {
        selectableCountryCodes.forEach { code ->
          val metric = metricsByCode[code]
          val detail = metric?.let {
            AppStrings.get(R.string.traffic_country_queries, numberFormat.format(it.queries))
          } ?: AppStrings.get(R.string.traffic_country_no_data)
          DropdownMenuItem(
            text = {
              Text(AppStrings.get(R.string.traffic_country_accessibility, localizedCountryName(code), detail))
            },
            onClick = {
              selectedCountryCode = code
              countryMenuExpanded = false
            },
            modifier = Modifier.testTag("world_map_country_option_$code")
          )
        }
      }
    }

    if (countryName != null) {
      Text(
        text = countryName,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.testTag("world_map_selected_country")
      )
      if (selectedMetric == null) {
        Text(
          text = AppStrings.get(R.string.traffic_country_no_data),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 12.sp,
          modifier = Modifier.testTag("world_map_selected_queries")
        )
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = AppStrings.get(
              R.string.traffic_country_queries,
              numberFormat.format(selectedMetric.queries)
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.testTag("world_map_selected_queries")
          )
          Text(
            text = AppStrings.get(
              R.string.traffic_country_share_value,
              AppStrings.percent(selectedMetric.percentage),
              AppStrings.get(R.string.traffic_country_share)
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.testTag("world_map_selected_share")
          )
        }
        if (selectedMetric.domains.isNotEmpty()) {
          Text(
            text = AppStrings.get(R.string.traffic_country_domains),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 4.dp)
          )
          Column(
            modifier = Modifier.testTag("world_map_selected_domains"),
            verticalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            selectedMetric.domains.take(5).forEach { domain ->
              Text(
                text = domain,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1
              )
            }
          }
        }
      }
    }
  }
}

internal data class WorldMapPoint(val x: Float, val y: Float)
internal data class WorldMapPolygon(val rings: List<List<WorldMapPoint>>)
internal data class WorldCountryOutline(val code: String, val polygons: List<WorldMapPolygon>)

internal data class WorldCountryPaths(val code: String, val paths: List<Path>)

internal class WorldMapRenderCache(private val geometry: WorldMapGeometry) {
  private var cachedViewport: Rect? = null
  private var cachedPadding = Float.NaN
  private var cachedPaths = emptyList<WorldCountryPaths>()

  // Only geometry/viewport/density affect these paths. Selection and live traffic
  // change paint, so neither may invalidate this allocation-heavy projection.
  fun paths(viewport: Rect, strokePadding: Float): List<WorldCountryPaths> {
    if (viewport == cachedViewport && strokePadding == cachedPadding) return cachedPaths
    cachedPaths = if (viewport.width <= 0f || viewport.height <= 0f) emptyList() else
      geometry.countries.mapNotNull { country ->
        val paths = country.polygons.flatMap { polygon ->
          (-1..1).mapNotNull { shift ->
            polygon.toPath(viewport, shift).takeIf { path ->
              val bounds = path.getBounds()
              bounds.right + strokePadding >= viewport.left &&
                bounds.left - strokePadding <= viewport.right &&
                bounds.bottom + strokePadding >= viewport.top &&
                bounds.top - strokePadding <= viewport.bottom
            }
          }
        }
        paths.takeIf { it.isNotEmpty() }?.let { WorldCountryPaths(country.code, it) }
      }
    cachedViewport = viewport
    cachedPadding = strokePadding
    return cachedPaths
  }
}

private object WorldMapGeometryCache {
  private val lock = Any()
  @Volatile private var loaded: WorldMapGeometry? = null
  fun load(context: Context): WorldMapGeometry = loaded ?: synchronized(lock) {
    loaded ?: WorldMapGeometry.load(context).also { loaded = it }
  }
}

internal class WorldMapGeometry(val countries: List<WorldCountryOutline>) {
  fun countryAt(x: Float, y: Float): String? {
    if (x !in 0f..1f || y !in 0f..1f) return null
    return countries.asReversed().firstOrNull { country ->
      country.polygons.any { polygon ->
        (-1..1).any { shift -> polygon.contains(WorldMapPoint(x + shift, y)) }
      }
    }?.code
  }

  companion object {
    fun load(context: Context): WorldMapGeometry = context.assets
      .open("maps/world-countries.tsv")
      .bufferedReader()
      .use { parseWorldMapAsset(it.readText()) }
  }
}

internal suspend fun loadWorldMapGeometry(context: Context): WorldMapGeometry =
  withContext(Dispatchers.IO) { WorldMapGeometryCache.load(context) }

private sealed interface WorldMapLoadState {
  data object Loading : WorldMapLoadState
  data class Ready(val geometry: WorldMapGeometry) : WorldMapLoadState
  data object Failed : WorldMapLoadState
}

internal fun parseWorldMapAsset(source: String): WorldMapGeometry {
  val countries = source.lineSequence().mapNotNull { line ->
    if (line.isBlank()) return@mapNotNull null
    val columns = line.split('\t', limit = 2)
    if (columns.size != 2 || !columns[0].matches(Regex("[A-Z]{2}"))) return@mapNotNull null
    val polygons = columns[1].split(';').mapNotNull { encodedPolygon ->
      val rings = encodedPolygon.split('~').mapNotNull { encodedRing ->
        val points = encodedRing.split(' ').mapNotNull { encodedPoint ->
          val pair = encodedPoint.split(',', limit = 2)
          if (pair.size != 2) return@mapNotNull null
          val x = pair[0].toFloatOrNull() ?: return@mapNotNull null
          val y = pair[1].toFloatOrNull() ?: return@mapNotNull null
          WorldMapPoint(x / 10_000f, y / 10_000f)
        }
        points.takeIf { it.size >= 4 }
      }
      rings.takeIf { it.isNotEmpty() }?.let(::WorldMapPolygon)
    }
    polygons.takeIf { it.isNotEmpty() }?.let { WorldCountryOutline(columns[0], it) }
  }.toList()
  return WorldMapGeometry(countries)
}

private fun WorldMapPolygon.contains(point: WorldMapPoint): Boolean {
  var inside = false
  rings.forEach { ring ->
    var previous = ring.lastIndex
    for (current in ring.indices) {
      val a = ring[current]
      val b = ring[previous]
      val crosses = (a.y > point.y) != (b.y > point.y) &&
        point.x < (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x
      if (crosses) inside = !inside
      previous = current
    }
  }
  return inside
}

private fun WorldMapPolygon.toPath(viewport: Rect, seamShift: Int): Path = Path().apply {
  fillType = PathFillType.EvenOdd
  rings.forEach { ring ->
    val first = ring.firstOrNull() ?: return@forEach
    moveTo(viewport.left + (first.x + seamShift) * viewport.width, viewport.top + first.y * viewport.height)
    for (index in 1 until ring.size) {
      val point = ring[index]
      lineTo(viewport.left + (point.x + seamShift) * viewport.width, viewport.top + point.y * viewport.height)
    }
    close()
  }
}

private fun worldMapViewport(width: Float, height: Float): Rect {
  if (width <= 0f || height <= 0f) return Rect(0f, 0f, 0f, 0f)
  val mapWidth = minOf(width, height * 2f)
  val mapHeight = mapWidth / 2f
  val left = (width - mapWidth) / 2f
  val top = (height - mapHeight) / 2f
  return Rect(left, top, left + mapWidth, top + mapHeight)
}

private fun localizedCountryName(code: String): String =
  Locale.forLanguageTag("und-$code").getDisplayCountry(AppStrings.locale).ifBlank { code }


