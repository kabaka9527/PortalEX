 @file:Suppress("UNCHECKED_CAST")
package moe.fuqiuluo.xposed.hooks.sensor

import android.content.pm.FeatureInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.util.ArrayMap
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import moe.fuqiuluo.xposed.BaseDivineService
import moe.fuqiuluo.xposed.utils.FakeLoc
import moe.fuqiuluo.xposed.utils.Logger
import moe.fuqiuluo.xposed.utils.afterHook
import moe.fuqiuluo.xposed.utils.beforeHook
import moe.fuqiuluo.xposed.utils.hookAllMethods
import moe.fuqiuluo.xposed.utils.hookMethodAfter
import moe.fuqiuluo.xposed.utils.onceHook
import moe.fuqiuluo.xposed.utils.onceHookAllMethod
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

// https://github.com/Frazew/VirtualSensor/blob/master/app/src/main/java/fr/frazew/virtualgyroscope/XposedMod.java#L298
object SystemSensorManagerHook: BaseDivineService() {
    private val listenerMap = ConcurrentHashMap<SensorEventListener, Int>()
    private val stepCounterStates = ConcurrentHashMap<SensorEventListener, StepSimulationState>()
    private val stepDetectorStates = ConcurrentHashMap<SensorEventListener, StepSimulationState>()
    private val sensorTypesByHandle = ConcurrentHashMap<Int, Int>()
    private val queueStepCounterStates = ConcurrentHashMap<Int, StepSimulationState>()
    private val queueStepDetectorStates = ConcurrentHashMap<Int, StepSimulationState>()
    private val syntheticStepThreads = ConcurrentHashMap<SensorEventListener, Thread>()
    private val dispatchingSyntheticEvent = ThreadLocal<Boolean>()

    operator fun invoke(classLoader: ClassLoader) {
        if (!FakeLoc.isSystemServerProcess) {
            initDivineService("Sensor")
        }

        unlockGeoSensor(classLoader)

        hookSystemSensorManager(classLoader)
        hookSystemSensorManagerQueue(classLoader)
    }

    private fun hookSystemSensorManagerQueue(classLoader: ClassLoader) {
        val cSystemSensorManagerQueue = XposedHelpers.findClassIfExists("android.hardware.SystemSensorManager\$SensorEventQueue", classLoader)
            ?: return

        cSystemSensorManagerQueue.declaredMethods.filter {
            it.name == "dispatchSensorEvent" &&
                    it.parameterTypes.size >= 4 &&
                    it.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    it.parameterTypes[1] == FloatArray::class.java &&
                    it.parameterTypes[2] == Int::class.javaPrimitiveType &&
                    it.parameterTypes[3] == Long::class.javaPrimitiveType
        }.forEach {
            it.onceHook(beforeHook {
                val handle = args[0] as? Int ?: return@beforeHook
                val values = args[1] as? FloatArray ?: return@beforeHook
                val timestamp = args[3] as? Long ?: return@beforeHook
                if (!FakeLoc.enable || !FakeLoc.enableSensorHook || values.isEmpty()) {
                    return@beforeHook
                }

                when (sensorTypesByHandle[handle]) {
                    Sensor.TYPE_STEP_COUNTER -> {
                        val state = queueStepCounterStates.getOrPut(handle) {
                            StepSimulationState(values.firstOrNull() ?: 0f)
                        }
                        values[0] = state.nextCounterValue(
                            timestampNanos = timestamp,
                            speedMetersPerSecond = FakeLoc.speed,
                            stepLengthMeters = FakeLoc.stepLengthMeters,
                            manualStepFrequencySpm = FakeLoc.manualStepFrequencySpm,
                            mode = FakeLoc.stepCadenceMode,
                            enabled = true
                        )
                    }
                    Sensor.TYPE_STEP_DETECTOR -> {
                        val state = queueStepDetectorStates.getOrPut(handle) {
                            StepSimulationState()
                        }
                        val steps = state.nextDetectorSteps(
                            timestampNanos = timestamp,
                            speedMetersPerSecond = FakeLoc.speed,
                            stepLengthMeters = FakeLoc.stepLengthMeters,
                            manualStepFrequencySpm = FakeLoc.manualStepFrequencySpm,
                            mode = FakeLoc.stepCadenceMode,
                            enabled = true
                        )
                        if (steps <= 0) {
                            result = null
                        } else {
                            values[0] = 1.0f
                        }
                    }
                }
            })
            Logger.info("Hooked SensorEventQueue.dispatchSensorEvent")
        }
    }

    private fun printClassInfo(clazz: Class<*>) {
        XposedBridge.log("=== Class: ${clazz.name} ===")

        // 打印所有字段（包括私有）
        XposedBridge.log("Declared Fields:")
        clazz.declaredFields.forEach { field ->
            val modifiers = Modifier.toString(field.modifiers)
            XposedBridge.log("  $modifiers ${field.type.simpleName} ${field.name}")
        }

        // 打印所有方法（包括私有）
        XposedBridge.log("Declared Methods:")
        clazz.declaredMethods.forEach { method ->
            val modifiers = Modifier.toString(method.modifiers)
            val params = method.parameterTypes.joinToString { it.simpleName }
            XposedBridge.log("  $modifiers ${method.returnType.simpleName} ${method.name}($params)")
        }
    }

    private fun hookSystemSensorManager(classLoader: ClassLoader) {
        val cSystemSensorManager = XposedHelpers.findClassIfExists("android.hardware.SystemSensorManager", classLoader)
        if (cSystemSensorManager == null) {
            if (FakeLoc.enableDebugLog) {
                Logger.debug("Failed to find SystemSensorManager")
            }
            return
        }


        val hookRegisterListenerImpl = beforeHook {
            val listener = args[0] as SensorEventListener
            if (FakeLoc.enableDebugLog) {
                Logger.debug("RegisterListenerImpl: $listener, sensor: ${args[1]}")
            }

            val sensor = args[1] as? Sensor ?: return@beforeHook
            rememberSensor(sensor)
            listenerMap[listener] = sensor.type
            if (sensor.type == Sensor.TYPE_STEP_DETECTOR) {
                stepDetectorStates.putIfAbsent(listener, StepSimulationState())
            }
            if (sensor.type == Sensor.TYPE_STEP_COUNTER || sensor.type == Sensor.TYPE_STEP_DETECTOR) {
                Logger.info(
                    "Register step sensor: listener=$listener, type=${sensor.type}, " +
                            "enable=${FakeLoc.enable}, sensorHook=${FakeLoc.enableSensorHook}, speed=${FakeLoc.speed}"
                )
                startSyntheticStepEvents(listener, sensor)
            }

            listener.javaClass.onceHookAllMethod("onSensorChanged", beforeHook {
                val event = args.firstOrNull() as? SensorEvent ?: return@beforeHook
                val eventListener = thisObject as? SensorEventListener ?: listener
                if (dispatchingSyntheticEvent.get() == true) {
                    return@beforeHook
                }
                if (!FakeLoc.enable || !FakeLoc.enableSensorHook) {
                    return@beforeHook
                }

                when (event.sensor?.type) {
                    Sensor.TYPE_STEP_COUNTER -> {
                        val state = stepCounterStates.getOrPut(eventListener) {
                            StepSimulationState(event.values.firstOrNull() ?: 0f)
                        }
                        event.values[0] = state.nextCounterValue(
                            timestampNanos = event.timestamp,
                            speedMetersPerSecond = FakeLoc.speed,
                            stepLengthMeters = FakeLoc.stepLengthMeters,
                            manualStepFrequencySpm = FakeLoc.manualStepFrequencySpm,
                            mode = FakeLoc.stepCadenceMode,
                            enabled = true
                        )
                    }
                    Sensor.TYPE_STEP_DETECTOR -> {
                        val state = stepDetectorStates.getOrPut(eventListener) {
                            StepSimulationState()
                        }
                        val steps = state.nextDetectorSteps(
                            timestampNanos = event.timestamp,
                            speedMetersPerSecond = FakeLoc.speed,
                            stepLengthMeters = FakeLoc.stepLengthMeters,
                            manualStepFrequencySpm = FakeLoc.manualStepFrequencySpm,
                            mode = FakeLoc.stepCadenceMode,
                            enabled = true
                        )
                        if (steps <= 0) {
                            result = null
                        } else {
                            event.values[0] = 1.0f
                        }
                    }
                }
            })
        }
        cSystemSensorManager.declaredMethods.filter {
            it.name == "registerListenerImpl" && it.parameterTypes.isNotEmpty()
                    && it.parameterTypes[0] == SensorEventListener::class.java
                    && it.parameterTypes[1] == Sensor::class.java
        }.forEach {
            it.onceHook(hookRegisterListenerImpl)
        }

        val hookUnregisterListenerImpl = beforeHook {
            val listener = args[0] as SensorEventListener
            if (FakeLoc.enableDebugLog) {
                Logger.debug("UnregisterListenerImpl: $listener")
            }
            listenerMap.remove(listener)
            stepCounterStates.remove(listener)
            stepDetectorStates.remove(listener)
            syntheticStepThreads.remove(listener)?.interrupt()
        }
        cSystemSensorManager.declaredMethods.filter {
            it.name == "unregisterListenerImpl" && it.parameterTypes.isNotEmpty()
                    && it.parameterTypes[0] == SensorEventListener::class.java
        }.forEach {
            it.onceHook(hookUnregisterListenerImpl)
        }

        cSystemSensorManager.hookAllMethods("getSensorList", afterHook {
            (result as? List<*>)?.forEach { (it as? Sensor)?.let(::rememberSensor) }
            if (FakeLoc.enableDebugLog) {
                Logger.debug("getSensorList: type: ${args[0]} -> $result")
            }
        })
        cSystemSensorManager.hookAllMethods("getFullSensorsList", afterHook {
            (result as? List<*>)?.forEach { (it as? Sensor)?.let(::rememberSensor) }
            if (FakeLoc.enableDebugLog) {
                Logger.debug("getFullSensorsList-> $result")
            }
        })
    }

    private fun rememberSensor(sensor: Sensor) {
        val handle = runCatching {
            val field = Sensor::class.java.getDeclaredField("mHandle")
            field.isAccessible = true
            field.getInt(sensor)
        }.getOrNull() ?: return
        sensorTypesByHandle[handle] = sensor.type
    }

    private fun startSyntheticStepEvents(listener: SensorEventListener, sensor: Sensor) {
        val thread = Thread {
            val counterState = stepCounterStates.getOrPut(listener) { StepSimulationState() }
            val detectorState = stepDetectorStates.getOrPut(listener) { StepSimulationState() }
            var lastCounterValue = 0f
            while (syntheticStepThreads[listener] === Thread.currentThread()) {
                try {
                    Thread.sleep(250)
                    if (!FakeLoc.enable || !FakeLoc.enableSensorHook) {
                        continue
                    }

                    val now = System.nanoTime()
                    when (sensor.type) {
                        Sensor.TYPE_STEP_COUNTER -> {
                            val value = counterState.nextCounterValue(
                                timestampNanos = now,
                                speedMetersPerSecond = FakeLoc.speed,
                                stepLengthMeters = FakeLoc.stepLengthMeters,
                                manualStepFrequencySpm = FakeLoc.manualStepFrequencySpm,
                                mode = FakeLoc.stepCadenceMode,
                                enabled = true
                            )
                            if (value > lastCounterValue) {
                                lastCounterValue = value
                                dispatchSyntheticEvent(listener, sensor, value, now)
                            }
                        }
                        Sensor.TYPE_STEP_DETECTOR -> {
                            val steps = detectorState.nextDetectorSteps(
                                timestampNanos = now,
                                speedMetersPerSecond = FakeLoc.speed,
                                stepLengthMeters = FakeLoc.stepLengthMeters,
                                manualStepFrequencySpm = FakeLoc.manualStepFrequencySpm,
                                mode = FakeLoc.stepCadenceMode,
                                enabled = true
                            )
                            repeat(steps.coerceAtLeast(0)) {
                                dispatchSyntheticEvent(listener, sensor, 1.0f, now)
                            }
                        }
                    }
                } catch (_: InterruptedException) {
                    return@Thread
                } catch (throwable: Throwable) {
                    Logger.error("Failed to dispatch synthetic step event", throwable)
                }
            }
        }.apply {
            name = "PortalSyntheticStep-${sensor.type}"
            isDaemon = true
        }

        val oldThread = syntheticStepThreads.putIfAbsent(listener, thread)
        if (oldThread == null) {
            thread.start()
            Logger.info("Started synthetic step events for sensor type=${sensor.type}")
        }
    }

    private fun dispatchSyntheticEvent(
        listener: SensorEventListener,
        sensor: Sensor,
        value: Float,
        timestampNanos: Long
    ) {
        val event = runCatching {
            val constructor = SensorEvent::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
            constructor.isAccessible = true
            constructor.newInstance(1).also {
                it.values[0] = value
                it.sensor = sensor
                it.accuracy = 3
                it.timestamp = timestampNanos
            }
        }.getOrElse {
            Logger.error("Failed to create synthetic SensorEvent", it)
            return
        }
        dispatchingSyntheticEvent.set(true)
        try {
            listener.onSensorChanged(event)
        } finally {
            dispatchingSyntheticEvent.remove()
        }
    }

    private fun unlockGeoSensor(classLoader: ClassLoader) {
        val cSystemConfig = XposedHelpers.findClassIfExists("com.android.server.SystemConfig", classLoader)
            ?: return

        val openGLVersion = run {
            val cSystemProperties = XposedHelpers.findClassIfExists("android.os.SystemProperties", classLoader)
                ?: return@run 0
            XposedHelpers.callStaticMethod(cSystemProperties, "getInt", "ro.opengles.version", FeatureInfo.GL_ES_VERSION_UNDEFINED) as Int
        }

        cSystemConfig.hookMethodAfter("getAvailableFeatures") {
            val features = result as ArrayMap<String, FeatureInfo>
            if (FakeLoc.enableDebugLog) {
                Logger.debug("getAvailableFeatures: ${features.keys}")
            }

//            if (!features.contains(PackageManager.FEATURE_SENSOR_GYROSCOPE)) {
//                val gyroFeature = FeatureInfo()
//                gyroFeature.name = PackageManager.FEATURE_SENSOR_GYROSCOPE
//                gyroFeature.reqGlEsVersion = openGLVersion
//                features[PackageManager.FEATURE_SENSOR_GYROSCOPE] = gyroFeature
//
//                if (FakeLoc.enableDebugLog) {
//                    Logger.debug("Added FEATURE_SENSOR_GYROSCOPE")
//                }
//            }
//            if (!features.contains(PackageManager.FEATURE_SENSOR_COMPASS)) {
//                val compassFeature = FeatureInfo()
//                compassFeature.name = PackageManager.FEATURE_SENSOR_COMPASS
//                compassFeature.reqGlEsVersion = openGLVersion
//                features[PackageManager.FEATURE_SENSOR_COMPASS] = compassFeature
//
//                if (FakeLoc.enableDebugLog) {
//                    Logger.debug("Added FEATURE_SENSOR_COMPASS")
//                }
//            }
//
//            if (!features.contains(PackageManager.FEATURE_SENSOR_ACCELEROMETER)) {
//                val accelerometerFeature = FeatureInfo()
//                accelerometerFeature.name = PackageManager.FEATURE_SENSOR_ACCELEROMETER
//                accelerometerFeature.reqGlEsVersion = openGLVersion
//                features[PackageManager.FEATURE_SENSOR_ACCELEROMETER] = accelerometerFeature
//
//                if (FakeLoc.enableDebugLog) {
//                    Logger.debug("Added FEATURE_SENSOR_ACCELEROMETER")
//                }
//            }
//
//            if (!features.contains(PackageManager.FEATURE_SENSOR_STEP_COUNTER)) {
//                val lightFeature = FeatureInfo()
//                lightFeature.name = PackageManager.FEATURE_SENSOR_STEP_COUNTER
//                lightFeature.reqGlEsVersion = openGLVersion
//                features[PackageManager.FEATURE_SENSOR_STEP_COUNTER] = lightFeature
//
//                if (FakeLoc.enableDebugLog) {
//                    Logger.debug("Added FEATURE_SENSOR_STEP_COUNTER")
//                }
//            }
//
//            if (!features.contains(PackageManager.FEATURE_SENSOR_STEP_DETECTOR)) {
//                val lightFeature = FeatureInfo()
//                lightFeature.name = PackageManager.FEATURE_SENSOR_STEP_DETECTOR
//                lightFeature.reqGlEsVersion = openGLVersion
//                features[PackageManager.FEATURE_SENSOR_STEP_DETECTOR] = lightFeature
//
//                if (FakeLoc.enableDebugLog) {
//                    Logger.debug("Added FEATURE_SENSOR_STEP_DETECTOR")
//                }
//            }
//
//            kotlin.runCatching {
//                XposedHelpers.setObjectField(thisObject, "mAvailableFeatures", features)
//            }.onFailure {
//                Logger.warn("Failed to set mAvailableFeatures", it)
//            }
            result = features
        }
    }
}
