package io.github.remmerw.saga

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update


abstract class Node(
    val uid: Long,
    val name: String
) {
    private val _children = MutableStateFlow(mutableListOf<Entity>())
    val children = _children.asStateFlow()

    fun getChildren(): List<Entity> {
        return _children.value.toList()
    }


    internal fun appendChild(child: Node) {
        this._children.update {
            val list = this._children.value.toMutableList()
            list.add(child.entity())
            list
        }
    }

    internal fun removeChild(child: Node) {
        this._children.update {
            val list = this._children.value.toMutableList()
            list.remove(child.entity())
            list
        }
    }

    fun entity(): Entity {
        return Entity(uid, name)
    }

    override fun toString(): String {
        return "$name($uid)"
    }
}

