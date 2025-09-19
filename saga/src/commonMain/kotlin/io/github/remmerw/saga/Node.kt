package io.github.remmerw.saga

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update


open class Node(val entity: Entity) {
    private val _children = MutableStateFlow(mutableListOf<Entity>())
    val children = _children.asStateFlow()

    private val _data = MutableStateFlow("")
    val data = _data.asStateFlow()

    private val _attributes = MutableStateFlow(mutableMapOf<Key, Value>())
    val attributes = _attributes.asStateFlow()


    fun attributes(): Map<Key, Value> {
        return attributes.value
    }


    fun getAttribute(key: Key): Value? {
        return _attributes.value[key]
    }


    internal fun removeAttribute(key: Key) {
        _attributes.update {
            val map = _attributes.value.toMutableMap()
            map.remove(key)
            map
        }
    }

    internal fun setAttribute(key: Key, value: Value) {
        _attributes.update {
            val map = _attributes.value.toMutableMap()
            map.put(key, value)
            map
        }
    }

    internal fun setAttributes(attrs: Map<Key, Value>) {
        _attributes.update {
            val map = _attributes.value.toMutableMap()
            attrs.forEach { (key, value) ->
                map.put(key, value)
            }
            map
        }
    }


    fun getData(): String {
        return data.value
    }

    fun setData(data: String) {
        require(!hasChildren()) { "Data node has no children" }
        this._data.update {
            data
        }
    }

    fun getChildren(): List<Entity> {
        return _children.value.toList()
    }

    fun hasChildren(): Boolean {
        return _children.value.isNotEmpty()
    }

    internal fun appendChild(child: Node) {
        this._children.update {
            val list = this._children.value.toMutableList()
            list.add(child.entity)
            list
        }
    }

    internal fun removeChild(child: Node) {
        this._children.update {
            val list = this._children.value.toMutableList()
            list.remove(child.entity)
            list
        }
    }

    override fun toString(): String {
        return "${entity.uid}(${entity.uid})"
    }
}

