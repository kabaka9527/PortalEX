package moe.fuqiuluo.portal.bdmap

import android.util.Log
import com.amap.api.maps.AMap
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.services.help.Tip
import moe.fuqiuluo.portal.ext.Loc4j

fun List<Tip>.toPoi(
    currentLocation: Pair<Double, Double>? = null
) = this
    .filter { it.name != null && it.point != null }
    .map {
    val gcj02Lat = it.point.latitude
    val gcj02Lon = it.point.longitude
    val (lat, lon) = Loc4j.gcj2wgs(gcj02Lat, gcj02Lon)
    if (currentLocation != null) {
        Log.d("toPoi", "currentLocation: $currentLocation, lat: $lat, lon: $lon")
        Poi(
            name = it.name,
            address = listOfNotNull(it.district, it.address).joinToString(" "),
            longitude = lon,
            latitude = lat,
            tag = it.typeCode,
        ).also {
            val distance = it.distanceTo(currentLocation.first, currentLocation.second).toInt()
            if (distance < 1000) {
                it.address = "${distance}m ${it.address}"
            } else {
                it.address = "${(distance / 1000.0).toString().take(4)}km ${it.address}"
            }
        }
    } else {
        Poi(
            name = it.name,
            address = listOfNotNull(it.district, it.address).joinToString(" "),
            longitude = lon,
            latitude = lat,
            tag = it.typeCode,
        )
    }
}

fun AMap.setMapConfig(mode: Int, resourceId: Int?) {
    val style = MyLocationStyle()
        .myLocationType(mode)
        .showMyLocation(true)
    resourceId?.let {
        style.myLocationIcon(BitmapDescriptorFactory.fromResource(it))
    }
    setMyLocationStyle(style)
}

fun AMap.locateMe() {
    setMapConfig(MyLocationStyle.LOCATION_TYPE_FOLLOW, null)
    setMapConfig(MyLocationStyle.LOCATION_TYPE_SHOW, null)
}
