package io.github.remmerw.saga

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update


internal class Element(uid: Long, name: String) : Node(uid, name) {
    private val _attributes = MutableStateFlow(mutableMapOf<String, String>())
    val attributes = _attributes.asStateFlow()

    private val _properties = MutableStateFlow(mutableMapOf<String, String>())
    val properties = _properties.asStateFlow()

    fun attributes(): Map<String, String> {
        return attributes.value
    }

    fun properties(): Map<String, String> {
        return properties.value
    }

    fun getAttribute(name: String): String? {
        return _attributes.value[name.lowercase()]
    }

    fun getProperty(name: String): String? {
        return _properties.value[name.lowercase()]
    }


    internal fun removeAttribute(key: String) {
        _attributes.update {
            val map = _attributes.value.toMutableMap()
            map.remove(key.lowercase())
            map
        }
    }

    internal fun setAttribute(key: String, value: String) {
        _attributes.update {
            val map = _attributes.value.toMutableMap()
            map.put(key.lowercase(), value)
            map
        }
    }

    internal fun addAttributes(attrs: Map<String, String>) {
        _attributes.update {
            val map = _attributes.value.toMutableMap()
            attrs.forEach { (key, value) ->
                map.put(key.lowercase(), value)
            }
            map
        }
    }

    internal fun addProperties(attrs: Map<String, String>) {
        _properties.update {
            val map = _properties.value.toMutableMap()
            attrs.forEach { (key, value) ->
                map.put(key.lowercase(), value)
            }
            map
        }
    }

}
