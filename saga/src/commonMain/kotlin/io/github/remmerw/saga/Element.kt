package io.github.remmerw.saga

import kotlinx.coroutines.flow.MutableStateFlow


internal class Element(uid: Long, name: String) : Node(uid, name) {
    val attributes = MutableStateFlow(mutableMapOf<String, String>())

    fun attributes(): Map<String, String> {
        return attributes.value
    }

    fun hasAttributes(): Boolean {
        return !attributes.value.isEmpty()
    }

    fun getAttribute(name: String): String? {
        return attributes.value[name.lowercase()]
    }


    internal suspend fun removeAttribute(name: String, emit: Boolean = false) {
        if (emit) {
            val map = attributes.value.toMutableMap()
            val exists = map.remove(name.lowercase())
            if (exists != null) {
                attributes.emit(map)
            }
        } else {
            attributes.value.remove(name.lowercase())
        }
    }

    suspend fun setAttribute(name: String, value: String, emit: Boolean = false) {
        if (emit) {
            val map = attributes.value.toMutableMap()
            map.put(name.lowercase(), value)
            attributes.emit(map)
        } else {
            attributes.value.put(name.lowercase(), value)
        }
    }

}
