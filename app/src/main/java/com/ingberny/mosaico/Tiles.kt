package com.ingberny.mosaico

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.min

enum class TileSize(val w: Int, val h: Int) { SMALL(1, 1), MEDIUM(2, 2), WIDE(4, 2), LARGE(4, 4) }

enum class TileKind { APP, CALENDAR, WEATHER, PHOTOS, BATTERY }

class TileSpec(
    val id: String,
    val kind: TileKind,
    var size: TileSize,
    val pkg: String = "",
    val cls: String = "",
    /** COLOR_DEFAULT, COLOR_APP, COLOR_ACCENT, COLOR_TRANSPARENT o un color ARGB propio. */
    var color: Int = COLOR_DEFAULT,
    /** Nombre propio; null = el nombre de la app. */
    var label: String? = null,
    var hideLabel: Boolean = false,
    /** Color del ícono y del texto; COLOR_DEFAULT = el general. */
    var iconColor: Int = COLOR_DEFAULT,
) {
    /** Orden de tamaños al tocar el botón de redimensionar, igual que en el teléfono original. */
    fun nextSize(): TileSize = when (kind) {
        TileKind.APP -> when (size) {
            TileSize.MEDIUM -> TileSize.SMALL
            TileSize.SMALL -> TileSize.WIDE
            else -> TileSize.MEDIUM
        }
        else -> when (size) {
            TileSize.MEDIUM -> TileSize.SMALL
            TileSize.SMALL -> TileSize.WIDE
            TileSize.WIDE -> TileSize.LARGE
            TileSize.LARGE -> TileSize.MEDIUM
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("kind", kind.name).put("size", size.name).put("pkg", pkg).put("cls", cls)
        .put("color", color).put("label", label ?: "").put("hideLabel", hideLabel).put("iconColor", iconColor)

    companion object {
        const val COLOR_DEFAULT = 0
        const val COLOR_APP = 1
        const val COLOR_TRANSPARENT = 2
        const val COLOR_ACCENT = 3

        fun newId(): String = UUID.randomUUID().toString().substring(0, 8)

        fun fromJson(o: JSONObject): TileSpec? = try {
            TileSpec(o.getString("id"), TileKind.valueOf(o.getString("kind")), TileSize.valueOf(o.getString("size")),
                o.optString("pkg"), o.optString("cls"), o.optInt("color", COLOR_DEFAULT),
                o.optString("label").ifEmpty { null }, o.optBoolean("hideLabel", false),
                o.optInt("iconColor", COLOR_DEFAULT))
        } catch (e: Exception) { null }

        fun live(kind: TileKind, size: TileSize) = TileSpec(newId(), kind, size)
        fun app(e: AppEntry, size: TileSize) = TileSpec(newId(), TileKind.APP, size, e.pkg, e.cls)
    }
}

object TileStore {
    fun load(prefs: Prefs): MutableList<TileSpec>? {
        val s = prefs.tilesJson ?: return null
        return try {
            val a = JSONArray(s)
            (0 until a.length()).mapNotNull { TileSpec.fromJson(a.getJSONObject(it)) }.toMutableList()
        } catch (e: Exception) { null }
    }

    fun save(prefs: Prefs, tiles: List<TileSpec>) {
        prefs.tilesJson = JSONArray().apply { tiles.forEach { put(it.toJson()) } }.toString()
    }
}

class Placement(val row: Int, val col: Int, val w: Int, val h: Int) {
    fun contains(r: Int, c: Int) = r >= row && r < row + h && c >= col && c < col + w
}

/**
 * Coloca los mosaicos en una cuadrícula de [cols] columnas. Cada mosaico ocupa el primer hueco
 * libre a partir de la fila del anterior (así los pequeños rellenan huecos sin saltar hacia atrás).
 */
fun packTiles(tiles: List<TileSpec>, cols: Int): List<Placement> {
    val occ = ArrayList<BooleanArray>()
    fun ensure(r: Int) { while (occ.size <= r) occ.add(BooleanArray(cols)) }
    fun free(r: Int, c: Int, w: Int, h: Int): Boolean {
        for (rr in r until r + h) { ensure(rr); for (cc in c until c + w) if (occ[rr][cc]) return false }
        return true
    }
    val out = ArrayList<Placement>(tiles.size)
    var startRow = 0
    for (t in tiles) {
        val w = min(t.size.w, cols)
        val h = t.size.h
        var r = startRow
        var placed: Placement? = null
        while (placed == null) {
            for (c in 0..(cols - w)) {
                if (free(r, c, w, h)) { placed = Placement(r, c, w, h); break }
            }
            if (placed == null) r++
        }
        for (rr in placed.row until placed.row + h) for (cc in placed.col until placed.col + w) occ[rr][cc] = true
        out.add(placed)
        startRow = placed.row
    }
    return out
}

class CalEvent(val title: String, val begin: Long, val allDay: Boolean)

/** Datos compartidos por los mosaicos dinámicos. */
class LiveState {
    var weather: WeatherData? = null
    var weatherError: String? = null
    var batteryPct = -1
    var charging = false
    var nextEvent: CalEvent? = null
    var photos: List<Bitmap> = emptyList()
    var badges: Map<String, Int> = emptyMap()
}

/** Pantalla de inicio por defecto en el primer arranque. */
object DefaultTiles {
    fun build(ctx: Context, repo: AppRepo): MutableList<TileSpec> {
        val list = mutableListOf<TileSpec>()
        val used = HashSet<String>()
        val pm = ctx.packageManager

        fun addPkg(pkg: String?, size: TileSize): Boolean {
            if (pkg == null || pkg in used) return false
            val e = repo.apps.firstOrNull { it.pkg == pkg } ?: return false
            used += pkg; list += TileSpec.app(e, size); return true
        }
        fun addIntent(i: Intent, size: TileSize): Boolean {
            val pkgs = try { pm.queryIntentActivities(i, 0).map { it.activityInfo.packageName } } catch (e: Exception) { emptyList() }
            val preferred = try { pm.resolveActivity(i, 0)?.activityInfo?.packageName } catch (e: Exception) { null }
            if (addPkg(preferred, size)) return true
            for (p in pkgs) if (addPkg(p, size)) return true
            return false
        }

        addIntent(Intent(Intent.ACTION_DIAL), TileSize.SMALL)
        addIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com")), TileSize.SMALL)
        list += TileSpec.live(TileKind.CALENDAR, TileSize.MEDIUM)
        addPkg(try { Telephony.Sms.getDefaultSmsPackage(ctx) } catch (e: Exception) { null }, TileSize.SMALL)
        addIntent(Intent(MediaStore.ACTION_IMAGE_CAPTURE), TileSize.SMALL)
        list += TileSpec.live(TileKind.WEATHER, TileSize.WIDE)
        addPkg("com.whatsapp", TileSize.MEDIUM)
        addIntent(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")), TileSize.MEDIUM)
        list += TileSpec.live(TileKind.PHOTOS, TileSize.MEDIUM)
        addPkg("com.android.vending", TileSize.SMALL)
        addIntent(Intent(Settings.ACTION_SETTINGS), TileSize.SMALL)
        list += TileSpec.live(TileKind.BATTERY, TileSize.SMALL)
        addPkg("com.google.android.apps.maps", TileSize.SMALL)
        addPkg("com.google.android.youtube", TileSize.MEDIUM)
        addPkg("com.spotify.music", TileSize.MEDIUM)
        addPkg("com.facebook.katana", TileSize.SMALL)
        addPkg("com.instagram.android", TileSize.SMALL)

        var toggle = true
        for (e in repo.apps) {
            if (list.size >= 18) break
            if (addPkg(e.pkg, if (toggle) TileSize.MEDIUM else TileSize.SMALL)) toggle = !toggle
        }
        return list
    }
}
