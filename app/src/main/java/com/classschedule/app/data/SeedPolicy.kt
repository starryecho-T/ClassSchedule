package com.classschedule.app.data

/** 初始化状态与内置课表版本分离；旧版本的成功标记也永久有效。 */
internal object SeedPolicy {
    const val INITIALIZED_KEY = "initialized"
    private val legacySeedKey = Regex("seeded_v\\d+")

    fun hasInitialized(preferences: Map<String, *>): Boolean =
        preferences[INITIALIZED_KEY] == true || preferences.any { (key, value) ->
            legacySeedKey.matches(key) && value == true
        }
}
