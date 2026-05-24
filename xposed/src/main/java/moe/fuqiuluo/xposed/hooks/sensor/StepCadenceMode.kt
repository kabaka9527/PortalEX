package moe.fuqiuluo.xposed.hooks.sensor

enum class StepCadenceMode {
    AUTO,
    MANUAL;

    companion object {
        fun from(value: String?): StepCadenceMode {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: AUTO
        }
    }
}
