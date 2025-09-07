package io.github.remmerw.saga

import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch


class Model() : Node(null, 0, "#document") {

    @OptIn(ExperimentalAtomicApi::class)
    private val uids = AtomicLong(0L)
    private val nodes: MutableMap<Long, Node> = mutableMapOf()
    private var doctype: DocumentType? = null


    init {
        this.setModel(this)
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


    internal fun getDoctype(): DocumentType? {
        return this.doctype
    }


    internal fun setDoctype(doctype: DocumentType) {
        this.doctype = doctype

    }

    internal fun createElement(name: String): Element {
        val uid = this.nextUid()
        return Element(this, uid, name)
    }


    internal fun createText(data: String): Text {
        return Text(this, nextUid(), data)
    }

    internal fun createComment(data: String): Comment {
        return Comment(this, nextUid(), data)
    }

    // todo
    internal fun createCDATASection(data: String): CDataSection {
        return CDataSection(this, nextUid(), data)
    }

    internal fun createProcessingInstruction(
        name: String,
        data: String
    ): ProcessingInstruction {
        return ProcessingInstruction(
            this, nextUid(), name, data
        )
    }


    suspend fun removeAttribute(entity: Entity, name: String) {
        (nodes[entity.uid]!! as Element).removeAttribute(name, true)
    }

    fun getAttribute(entity: Entity, name: String): String? {
        return (nodes[entity.uid]!! as Element).getAttribute(name)
    }

    suspend fun setAttribute(entity: Entity, name: String, value: String) {
        require(entity != entity()) { "Model does not have attributes" }
        (nodes[entity.uid]!! as Element).setAttribute(name, value, true)
    }

    suspend fun createEntity(
        name: String, parent: Entity = entity(),
        attributes: Map<String, String> = mapOf()
    ): Entity {
        val child = createElement(name)
        attributes.entries.forEach { (key, value) ->
            child.setAttribute(key, value, false)
        }
        nodes[parent.uid]!!.appendChild(child, true)
        return child.entity()
    }

    suspend fun removeEntity(parent: Entity = entity(), entity: Entity) {
        val child = nodes[entity.uid]!!
        nodes[parent.uid]!!.removeChild(child, true)
        nodes.remove(entity.uid)
    }

    fun children(entity: Entity): StateFlow<List<Entity>> {
        return nodes[entity.uid]!!.children
    }

    fun attributes(entity: Entity): StateFlow<Map<String, String>> {
        return (nodes[entity.uid] as Element).attributes
    }

    fun data(entity: Entity): StateFlow<String> {
        return (nodes[entity.uid] as Text).data
    }

}
