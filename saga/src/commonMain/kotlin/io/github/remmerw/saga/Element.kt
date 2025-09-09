package io.github.remmerw.saga

import kotlinx.coroutines.flow.MutableStateFlow


internal class Element(uid: Long, name: String) : Node(uid, name) {
    val attributes = MutableStateFlow(mutableMapOf<String, String>())

    fun attributes(): Map<String, String> {
        return attributes.value
    }

    fun getAttribute(name: String): String? {
        return attributes.value[name.lowercase()]
    }

    internal suspend fun removeAttribute(key: String) {
        val map = attributes.value.toMutableMap()
        val exists = map.remove(key.lowercase())
        if (exists != null) {
            attributes.emit(map)
        }
    }

    suspend fun setAttribute(key: String, value: String) {
        val map = attributes.value.toMutableMap()
        map.put(key.lowercase(), value)
        attributes.emit(map)
    }

    suspend fun setAttributes(attrs: Map<String, String>) {
        val map = attributes.value.toMutableMap()
        attrs.forEach { (key, value) ->
            map.put(key.lowercase(), value)
        }
        attributes.emit(map)
    }

}
