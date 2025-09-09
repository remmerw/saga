package io.github.remmerw.saga

import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch


class Model() : Node(0, "#model") {

    @OptIn(ExperimentalAtomicApi::class)
    private val uids = AtomicLong(0L)
    private val nodes: MutableMap<Long, Node> = mutableMapOf()


    init {
        addNode(this)
        this.nodes.put(0, this)
    }

    internal fun addNode(node: Node) {
        nodes.put(node.uid, node)
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun nextUid(): Long {
        return uids.incrementAndFetch()
    }


    internal fun node(entity: Entity): Node {
        return nodes[entity.uid]!!
    }


    internal fun createElement(name: String): Element {
        val uid = this.nextUid()
        val element = Element(uid, name)
        addNode(element)
        return element
    }


    internal suspend fun createText(parent: Node, data: String): Text {
        val text = Text(nextUid(), data)
        addNode(text)
        parent.appendChild(text)
        return text
    }

    internal suspend fun createComment(parent: Node, data: String): Comment {
        val comment = Comment(nextUid(), data)
        addNode(comment)
        parent.appendChild(comment)
        return comment
    }

    internal suspend fun createDocumentType(
        qualifiedName: String,
        publicId: String?,
        systemId: String?
    ): DocumentType {
        val docType = DocumentType(nextUid(), qualifiedName, publicId, systemId)
        addNode(docType)
        appendChild(docType)
        return docType

    }

    // todo
    internal fun createCDATASection(data: String): CDataSection {
        val data = CDataSection(nextUid(), data)
        addNode(data)
        return data
    }

    internal suspend fun createProcessingInstruction(
        parent: Node,
        name: String,
        data: String
    ): ProcessingInstruction {
        val pi = ProcessingInstruction(nextUid(), name, data)
        addNode(pi)
        parent.appendChild(pi)
        return pi
    }


    suspend fun removeAttribute(entity: Entity, name: String) {
        (nodes[entity.uid]!! as Element).removeAttribute(name)
    }

    fun getAttribute(entity: Entity, name: String): String? {
        return (nodes[entity.uid]!! as Element).getAttribute(name)
    }

    fun getChildren(entity: Entity): List<Entity> {
        return (nodes[entity.uid]!!).getChildren()
    }

    fun getChildren(entity: Entity, name: String): List<Entity> {
        return (nodes[entity.uid]!!).getChildren().filter { entity -> entity.name == name }
    }

    suspend fun setAttribute(entity: Entity, name: String, value: String) {
        require(entity != entity()) { "Model does not have attributes" }
        (nodes[entity.uid]!! as Element).setAttribute(name, value)
    }

    suspend fun createEntity(
        name: String, parent: Entity = entity(),
        attributes: Map<String, String> = mapOf()
    ): Entity {
        val parent = nodes[parent.uid]!!
        val child = createElement(name)
        child.setAttributes(attributes)
        parent.appendChild(child)
        return child.entity()
    }

    suspend fun createText(parent: Entity, text: String): Entity {
        val parent = nodes[parent.uid]!!
        val child = createText(parent, text)
        return child.entity()
    }

    suspend fun removeEntity(parent: Entity = entity(), entity: Entity) {
        val child = nodes[entity.uid]!!
        nodes[parent.uid]!!.removeChild(child)
        nodes.remove(entity.uid)
    }

    fun children(entity: Entity): StateFlow<List<Entity>> {
        return nodes[entity.uid]!!.children
    }

    fun attributes(entity: Entity): StateFlow<Map<String, String>> {
        return (nodes[entity.uid] as Element).attributes
    }

    fun text(entity: Entity): StateFlow<String> {
        return (nodes[entity.uid] as Text).data
    }


    fun debug() {
        debug(this, 0)
    }

    internal fun debug(node: Node, spaces: Int) {
        val name = node.name

        val space = if (spaces > 0) "  ".repeat(spaces) else ""
        if (node is Element) {
            val attributes = debugAttributes(node)
            if (node.getChildren().isEmpty()) {
                if (attributes.isEmpty()) {
                    println("$space<$name/>")
                } else {
                    println("$space<$name $attributes/>")
                }
            } else {
                if (attributes.isEmpty()) {
                    println("$space<$name>")
                } else {
                    println("$space<$name $attributes>")
                }
                node.getChildren().forEach { entity ->
                    debug(node(entity), spaces + 1)
                }
                println("$space</$name>")
            }
        } else if (node is Text) {
            require(node.getChildren().isEmpty()) { "Text has no children" }
            println("$space<$name>" + node.getData() + "</$name>")
        } else {
            if (node.getChildren().isEmpty()) {
                println("$space<$name/>")
            } else {
                println("$space<$name>")
                node.getChildren().forEach { entity ->
                    debug(node(entity), spaces + 1)
                }
                println("$space</$name>")
            }
        }
    }

    internal fun debugAttributes(element: Element): String {
        val result = StringBuilder()
        element.attributes().forEach { (key, value) ->
            result.append("$key=\"$value\" ")
        }
        return result.toString()
    }
}
