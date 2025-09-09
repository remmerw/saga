package io.github.remmerw.saga

import kotlinx.coroutines.flow.MutableStateFlow


abstract class Node(
    val uid: Long,
    val name: String
) {
    val children = MutableStateFlow(mutableListOf<Entity>())


    fun getChildren(): List<Entity> {
        return children.value.toList()
    }


    internal suspend fun appendChild(child: Node) {
        val list = this.children.value.toMutableList()
        list.add(child.entity())
        this.children.emit(list)
    }

    internal suspend fun removeChild(child: Node) {
        val list = this.children.value.toMutableList()
        val exists = list.remove(child.entity())
        if (exists) {
            this.children.emit(list)
        }
    }

    fun entity(): Entity {
        return Entity(uid, name)
    }

    override fun toString(): String {
        return "$name($uid)"
    }
}

