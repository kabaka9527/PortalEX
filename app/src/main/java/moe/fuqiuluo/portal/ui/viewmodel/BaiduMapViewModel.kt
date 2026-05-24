package moe.fuqiuluo.portal.ui.viewmodel

import android.app.Notification
import androidx.lifecycle.ViewModel
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.AMap
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.MyLocationStyle
import moe.fuqiuluo.portal.R
import com.amap.api.services.geocoder.GeocodeSearch
import moe.fuqiuluo.portal.bdmap.setMapConfig

class BaiduMapViewModel: ViewModel() {
    var isExists = false
    lateinit var baiduMap: AMap
    lateinit var mLocationClient: AMapLocationClient

    /**
     * Current location
     * WGS84
     */
    var currentLocation: Pair<Double, Double>? = null

    var markName: String? = null

    /**
     * Marked location
     * WGS84
     * first => latitude
     * second => longitude
     */
    var markedLoc: Pair<Double, Double>? = null
    var showDetailView = false

    /* Notification */
    var mNotification: Notification? = null

    /**
     * 2024.10.10: Cancels the default follow perspective
     */
    var perspectiveState = MyLocationStyle.LOCATION_TYPE_SHOW
        set(value) {
            field = value
            baiduMap.setMapConfig(value, null)
        }

    val mMapIndicator: BitmapDescriptor? by lazy {
        BitmapDescriptorFactory.fromResource(R.drawable.icon_selected_location_16)
    }

    var mGeoCoder: GeocodeSearch? = null
}
