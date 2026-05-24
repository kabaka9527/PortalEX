package moe.fuqiuluo.portal.ext

import com.amap.api.location.AMapLocation
import com.amap.api.maps.model.LatLng
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

val LatLng.wgs84: Pair<Double, Double>
    get() = Loc4j.gcj2wgs(latitude, longitude)

val AMapLocation.wgs84: Pair<Double, Double>
    get() = Loc4j.gcj2wgs(latitude, longitude)

val Pair<Double, Double>.gcj02: LatLng
    get() = Loc4j.wgs2gcj(first, second).let { LatLng(it.first, it.second) }

object Loc4j {
    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323

    fun gcj2wgs(lat: Double, lon: Double): Pair<Double, Double> {
        if (outOfChina(lat, lon)) return lat to lon
        val (mgLat, mgLon) = wgs2gcj(lat, lon)
        return lat * 2 - mgLat to lon * 2 - mgLon
    }

    fun wgs2gcj(lat: Double, lon: Double): Pair<Double, Double> {
        if (outOfChina(lat, lon)) return lat to lon
        var dLat = transformLat(lon - 105.0, lat - 35.0)
        var dLon = transformLon(lon - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * Math.PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * Math.PI)
        dLon = (dLon * 180.0) / (A / sqrtMagic * cos(radLat) * Math.PI)
        return lat + dLat to lon + dLon
    }

    private fun outOfChina(lat: Double, lon: Double): Boolean {
        return lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * Math.PI) + 40.0 * sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * Math.PI) + 320 * sin(y * Math.PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLon(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * Math.PI) + 40.0 * sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * Math.PI) + 300.0 * sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
        return ret
    }
}
