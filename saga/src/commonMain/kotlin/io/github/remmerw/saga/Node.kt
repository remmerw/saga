package io.github.remmerw.saga

import kotlinx.coroutines.flow.MutableStateFlow


abstract class Node(
    var model: Model?,
    val uid: Long,
    val name: String
) {
    init {
        if (model is Model) {
            (model as Model).addNode(this)
        }
    }

    val children = MutableStateFlow(mutableListOf<Entity>())


    fun getChildren(): List<Entity> {
        return children.value.toList()
    }


    internal suspend fun appendChild(child: Node, emit: Boolean = false) {
        if (emit) {
            val list = this.children.value.toMutableList()
            list.add(child.entity())
            this.children.emit(list)
        } else {
            this.children.value.add(child.entity())
        }
    }

    internal suspend fun removeChild(child: Node, emit: Boolean = false) {
        if (emit) {
            val list = this.children.value.toMutableList()
            val exists = list.remove(child.entity())
            if (exists) {
                this.children.emit(list)
            }
        } else {
            this.children.value.add(child.entity())
        }
    }

    internal fun setModel(value: Model) {
        this.model = value
    }


    fun entity(): Entity {
        return Entity(uid, name)
    }

    override fun toString(): String {
        return "$name($uid)"
    }

    open fun debug() {
        if (children.value.isEmpty()) {
            println("<$name/>")
        } else {
            println("<$name>")
            children.value.forEach { entity ->
                model!!.node(entity).debug()
            }
            println("</$name>")
        }
    }
}

