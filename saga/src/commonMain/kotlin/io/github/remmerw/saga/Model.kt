package io.github.remmerw.saga

import kotlinx.coroutines.flow.StateFlow
import kotlinx.io.Sink
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch


class Model(tag: Tag) : Node(Entity(0, tag)) {

    @OptIn(ExperimentalAtomicApi::class)
    private val uids = AtomicLong(0L)

    @OptIn(ExperimentalAtomicApi::class)
    fun isEmpty(): Boolean {
        return uids.load() == 0L
    }

    private val nodes: MutableMap<Long, Node> = mutableMapOf()

    init {
        addNode(this)
    }

    internal fun addNode(node: Node) {
        nodes.put(node.entity.uid, node)
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun nextUid(): Long {
        return uids.incrementAndFetch()
    }

    internal fun node(entity: Entity): Node {
        return nodes[entity.uid]!!
    }

    internal fun createNode(tag: Tag): Node {
        val uid = this.nextUid()
        val element = Node(Entity(uid, tag))
        addNode(element)
        return element
    }


    internal fun setData(node: Node, data: String) {
        node.setData(data)
    }

    fun removeAttribute(entity: Entity, key: Key) {
        (nodes[entity.uid]!!).removeAttribute(key)
    }

    fun getAttribute(entity: Entity, key: Key): Value? {
        return (nodes[entity.uid]!!).getAttribute(key)
    }

    fun getChildren(entity: Entity): List<Entity> {
        return (nodes[entity.uid]!!).getChildren()
    }

    fun getChildren(entity: Entity, tag: Tag): List<Entity> {
        return (nodes[entity.uid]!!).getChildren().filter { entity -> entity.tag == tag }
    }

    fun setAttribute(entity: Entity, key: Key, value: Value) {
        require(entity != this.entity) { "Model does not have attributes" }
        (nodes[entity.uid]!!).setAttribute(key, value)
    }

    fun createEntity(
        tag: Tag,
        parent: Entity,
        attributes: Map<Key, Value> = mapOf()
    ): Entity {
        val parent = nodes[parent.uid]!!
        val child = createNode(tag)
        child.addAttributes(attributes)
        parent.appendChild(child)
        return child.entity
    }

    fun createEntity(
        tag: Tag,
        parent: Entity,
        data: String
    ): Entity {
        val parent = nodes[parent.uid]!!
        val child = createNode(tag)
        setData(child, data)
        parent.appendChild(child)
        return child.entity
    }

    fun createEntity(
        tag: Tag,
        data: String
    ): Entity {
        val child = createNode(tag)
        setData(child, data)
        appendChild(child)
        return child.entity
    }

    fun createEntity(
        tag: Tag,
        attributes: Map<Key, Value> = mapOf()
    ): Entity {
        val child = createNode(tag)
        child.addAttributes(attributes)
        appendChild(child)
        return child.entity
    }

    fun setData(entity: Entity, data: String) {
        val parent = nodes[entity.uid]!!
        setData(parent, data)
    }

    fun removeEntity(parent: Entity, entity: Entity) {
        val child = nodes[entity.uid]!!
        nodes[parent.uid]!!.removeChild(child)
        nodes.remove(entity.uid)
    }

    fun removeEntity(entity: Entity) {
        val child = nodes[entity.uid]!!
        removeChild(child)
        nodes.remove(entity.uid)
    }

    fun children(entity: Entity): StateFlow<List<Entity>> {
        return nodes[entity.uid]!!.children
    }

    fun attributes(entity: Entity): StateFlow<Map<Key, Value>> {
        return (nodes[entity.uid]!!).attributes
    }

    fun data(entity: Entity): StateFlow<String> {
        return nodes[entity.uid]!!.data
    }


    fun content(entity: Entity): String {
        val result = StringBuilder()
        content(result, nodes[entity.uid]!!, 0)
        return result.toString()
    }

    fun content(sink: Sink) {
        sink.write(content().encodeToByteArray())
    }

    fun content(): String {
        return content(entity)
    }

    internal fun content(builder: StringBuilder, node: Node, spaces: Int) {
        val name = node.entity.tag.name

        val space = if (spaces > 0) "  ".repeat(spaces) else ""

        val attributes = attributes(node)

        if (node.getChildren().isEmpty()) {
            if (attributes.isEmpty()) {
                val data = node.getData()
                if (!data.isEmpty()) {
                    builder.appendLine("$space<$name>$data</$name>")
                } else {
                    builder.appendLine("$space<$name/>")
                }
            } else {
                val data = node.getData()
                if (!data.isEmpty()) {
                    builder.appendLine("$space<$name$attributes>$data</$name>")
                } else {
                    builder.appendLine("$space<$name$attributes/>")
                }
            }
        } else {
            if (attributes.isEmpty()) {
                builder.appendLine("$space<$name>")
            } else {
                builder.appendLine("$space<$name$attributes>")
            }
            node.getChildren().forEach { entity ->
                content(builder, node(entity), spaces + 1)
            }
            builder.appendLine("$space</$name>")
        }

    }

    internal fun attributes(node: Node): String {
        val result = StringBuilder()
        node.attributes().forEach { (key, value) ->
            result.append(" ${key.name}=\"${value.data}\"")
        }
        return result.toString()
    }

}
