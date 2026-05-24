package moe.fuqiuluo.portal.ui.viewmodel

import android.app.Activity
import android.location.LocationManager
import android.util.Log
import androidx.lifecycle.ViewModel
import com.tencent.bugly.crashreport.CrashReport
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.fuqiuluo.portal.Portal
import moe.fuqiuluo.portal.android.coro.CoroutineController
import moe.fuqiuluo.portal.android.coro.CoroutineRouteMock
import moe.fuqiuluo.portal.ext.accuracy
import moe.fuqiuluo.portal.ext.altitude
import moe.fuqiuluo.portal.ext.reportDuration
import moe.fuqiuluo.portal.ext.routeLoopCount
import moe.fuqiuluo.portal.ext.speed
import moe.fuqiuluo.portal.service.MockServiceHelper
import moe.fuqiuluo.portal.ui.mock.HistoricalLocation
import moe.fuqiuluo.portal.ui.mock.HistoricalRoute
import moe.fuqiuluo.portal.ui.mock.Rocker
import moe.fuqiuluo.xposed.utils.FakeLoc
import net.sf.geographiclib.Geodesic

class MockServiceViewModel : ViewModel() {
    private companion object {
        const val ROUTE_MIN_WALKING_SPEED = 0.8
        const val ROUTE_MAX_WALKING_SPEED = 1.35
        const val ROUTE_SPEED_AMPLITUDE = 0.05
    }

    lateinit var rocker: Rocker
    private lateinit var rockerJob: Job
    private lateinit var routeMockJob: Job
    var isRockerLocked = false
    var routeStage = 0
    var routeLoopIndex = 0
    val rockerCoroutineController = CoroutineController()
    val routeMockCoroutine = CoroutineRouteMock()

    var isRouteStart = false

    var locationManager: LocationManager? = null
        set(value) {
            field = value
            if (value != null)
                MockServiceHelper.tryInitService(value)
        }

    var selectedLocation: HistoricalLocation? = null
    var selectedRoute: HistoricalRoute? = null

    // ---------- 速度衰减相关 ----------
    private var totalDistanceMoved = 0.0          // 累计移动距离（米）
    private val decayDistanceThreshold = 14000.0 // 衰减阈值（米），对应步频模拟的20000步（平均步幅0.7m）
    private val minSpeedFactor = 130.0 / 190.0   // 最小速度因子 ≈ 0.6842

    /**
     * 根据累计移动距离计算当前速度衰减因子（线性衰减）
     * 距离从 0 → decayDistanceThreshold，因子从 1.0 → minSpeedFactor
     */
    private fun getCurrentSpeedFactor(): Double {
        val progress = (totalDistanceMoved / decayDistanceThreshold).coerceIn(0.0, 1.0)
        return 1.0 - (1.0 - minSpeedFactor) * progress
    }

    /**
     * 重置累计距离（例如重新开始路线模拟时调用）
     */
    private fun resetDistanceAccumulator() {
        totalDistanceMoved = 0.0
        Log.d("MockServiceViewModel", "速度衰减累计距离已重置")
    }
    // ---------------------------------

    fun initRocker(activity: Activity): Rocker {
        if (!::rocker.isInitialized) {
            rocker = Rocker(activity)
        }

        if (!::rockerJob.isInitialized || rockerJob.isCancelled) {
            rockerCoroutineController.pause()
            val delayTime = activity.reportDuration.toLong()
            val applicationContext = activity.applicationContext
            rockerJob = GlobalScope.launch {
                do {
                    rockerCoroutineController.controlledCoroutine()
                    delay(delayTime)

                    CrashReport.setUserSceneTag(applicationContext, 261773)
                    if(!MockServiceHelper.move(locationManager!!, FakeLoc.speed * (delayTime / 1000.0), FakeLoc.bearing)) {
                        Log.e("MockServiceViewModel", "Failed to move")
                    }
                } while (isActive)
            }
        }

        FakeLoc.speed = activity.speed
        FakeLoc.altitude = activity.altitude
        FakeLoc.accuracy = activity.accuracy

        ensureRouteMockJob(activity.reportDuration.toLong())

        return rocker
    }

    private fun ensureRouteMockJob(delayTime: Long) {
        if (::routeMockJob.isInitialized && routeMockJob.isActive) return

        routeMockCoroutine.pause()
        resetDistanceAccumulator()

        routeMockJob = GlobalScope.launch {
            while (isActive) {
                routeMockCoroutine.routeMockCoroutine()
                delay(delayTime)

                val manager = locationManager ?: continue
                val route = selectedRoute?.route?.takeIf { it.isNotEmpty() } ?: continue
                val loopCount = Portal.appContext.routeLoopCount.coerceAtLeast(1)
                val baseSpeed = Portal.appContext.speed
                    .takeIf { it.isFinite() && it > 0.0 }
                    ?.coerceIn(ROUTE_MIN_WALKING_SPEED, ROUTE_MAX_WALKING_SPEED)
                    ?: FakeLoc.speed

                if (routeStage == 0) {
                    MockServiceHelper.setLocation(manager, route[0].first, route[0].second)
                    MockServiceHelper.setSpeedAmplitude(manager, ROUTE_SPEED_AMPLITUDE)
                    routeStage = 1
                }

                while (routeStage < route.size) {
                    val target = route[routeStage]
                    val location = MockServiceHelper.getLocation(manager) ?: break
                    val inverse = Geodesic.WGS84.Inverse(
                        location.first,
                        location.second,
                        target.first,
                        target.second
                    )
                    val moveDistance = baseSpeed * getCurrentSpeedFactor() * (delayTime / 1000.0)
                    if (inverse.s12 <= maxOf(1.0, moveDistance)) {
                        MockServiceHelper.setLocation(manager, target.first, target.second)
                        routeStage++
                    } else {
                        break
                    }
                }

                if (routeStage >= route.size) {
                    routeLoopIndex++
                    if (routeLoopIndex >= loopCount) {
                        routeMockCoroutine.pause()
                        if (::rocker.isInitialized) {
                            rocker.autoStatus = false
                        }
                        routeStage = 0
                        routeLoopIndex = 0
                        resetDistanceAccumulator()
                    } else {
                        routeStage = 0
                    }
                    continue
                }

                val target = route[routeStage]
                val location = MockServiceHelper.getLocation(manager) ?: continue
                val currentLat = location.first
                val currentLon = location.second
                val inverse = Geodesic.WGS84.Inverse(
                    currentLat,
                    currentLon,
                    target.first,
                    target.second
                )
                var azimuth = inverse.azi1
                if (azimuth < 0) {
                    azimuth += 360
                }

                val decayedSpeed = baseSpeed * getCurrentSpeedFactor()
                val moveDistance = minOf(inverse.s12, decayedSpeed * (delayTime / 1000.0))
                MockServiceHelper.setSpeed(manager, decayedSpeed.toFloat())
                if (moveDistance > 0) {
                    totalDistanceMoved += moveDistance
                    Log.d(
                        "MockServiceViewModel",
                        "路线模拟: loop=${routeLoopIndex + 1}/$loopCount, stage=$routeStage/${route.size - 1}, " +
                                "move=%.2fm, remain=%.2fm, speed=%.2fm/s".format(moveDistance, inverse.s12, decayedSpeed)
                    )
                }

                if (!MockServiceHelper.move(manager, moveDistance, azimuth)) {
                    Log.e("MockServiceViewModel", "移动失败")
                }
            }
        }
    }

    fun startRouteMock() {
        routeStage = 0
        routeLoopIndex = 0
        resetDistanceAccumulator()
        ensureRouteMockJob(Portal.appContext.reportDuration.toLong())
        if (::rocker.isInitialized) {
            rocker.autoStatus = true
        }
        routeMockCoroutine.resume()
    }

    fun stopRouteMock() {
        routeMockCoroutine.pause()
        routeStage = 0
        routeLoopIndex = 0
        if (::rocker.isInitialized) {
            rocker.autoStatus = false
        }
    }

    fun isServiceStart(): Boolean {
        return locationManager != null && MockServiceHelper.isServiceInit() && MockServiceHelper.isMockStart(
            locationManager!!
        )
    }
}
