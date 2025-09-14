package io.github.remmerw.saga

import kotlinx.coroutines.flow.StateFlow
import kotlinx.io.Source
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch


class Model() : Node(0, "#model") {

    @OptIn(ExperimentalAtomicApi::class)
    private val uids = AtomicLong(0L)
    private val nodes: MutableMap<Long, Node> = mutableMapOf()
    private val style = Style()


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

    internal fun html(): Entity? {
        return getChildren().firstOrNull { entity -> entity.name == Tag.HTML.tag() }
    }

    internal fun body(): Entity? {
        val html = html()
        if (html != null) {
            return getChildren(html).firstOrNull { entity -> entity.name == Tag.BODY.tag() }
        }
        return null
    }

    internal fun head(): Entity? {
        val html = html()
        if (html != null) {
            return getChildren(html).firstOrNull { entity -> entity.name == Tag.HEAD.tag() }
        }
        return null
    }

    internal fun styles(): List<Entity> {
        val html = head()
        if (html != null) {
            return getChildren(html).filter { entity -> entity.name == Tag.STYLE.tag() }
        }
        return emptyList()
    }

    internal fun links(): List<Entity> {
        val html = head()
        if (html != null) {
            return getChildren(html).filter { entity -> entity.name == Tag.LINK.tag() }
        }
        return emptyList()
    }


    internal fun createElement(name: String): Element {
        val uid = this.nextUid()
        val element = Element(uid, name.lowercase())
        addNode(element)
        return element
    }


    internal fun createText(parent: Node, data: String) {
        if (parent.name == Tag.STYLE.tag()) {
            style.parseStyle(data)
        } else {
            val text = Text(nextUid(), data)
            addNode(text)
            parent.appendChild(text)
        }
    }

    internal fun createComment(parent: Node, data: String): Comment {
        val comment = Comment(nextUid(), data)
        addNode(comment)
        parent.appendChild(comment)
        return comment
    }

    internal fun createDocumentType(
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

    internal fun createProcessingInstruction(
        parent: Node,
        name: String,
        data: String
    ): ProcessingInstruction {
        val pi = ProcessingInstruction(nextUid(), name, data)
        addNode(pi)
        parent.appendChild(pi)
        return pi
    }


    fun removeAttribute(entity: Entity, name: String) {
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

    fun setAttribute(entity: Entity, name: String, value: String) {
        require(entity != entity()) { "Model does not have attributes" }
        (nodes[entity.uid]!! as Element).setAttribute(name, value)
    }

    fun createEntity(
        name: String, parent: Entity = entity(),
        attributes: Map<String, String> = mapOf()
    ): Entity {
        val parent = nodes[parent.uid]!!
        val child = createElement(name)
        child.addAttributes(attributes)
        parent.appendChild(child)
        return child.entity()
    }

    fun createText(parent: Entity, text: String) {
        val parent = nodes[parent.uid]!!
        createText(parent, text)
    }

    fun removeEntity(parent: Entity = entity(), entity: Entity) {
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

    fun parse(source: Source) {
        val parser = HtmlParser(model = this, isXML = false)
        parser.parse(source)
    }

    fun content(entity: Entity): String {
        val result = StringBuilder()
        content(result, nodes[entity.uid]!!, 0)
        return result.toString()
    }

    fun content(): String {
        return content(entity())
    }

    fun normalize() {
        val body = body()
        if (body != null) {
            style.handleNode(this, body)
        } else {
            debug("Document does not contains a body")
        }
    }

    internal fun nodes(name: String): List<Node> {
        return nodes.values.filter { node -> name == node.name }
    }

    internal fun content(builder: StringBuilder, node: Node, spaces: Int) {
        val name = node.name

        val space = if (spaces > 0) "  ".repeat(spaces) else ""
        if (node is Element) {
            val attributes = attributes(node)
            if (node.getChildren().isEmpty()) {
                if (attributes.isEmpty()) {
                    builder.appendLine("$space<$name/>")
                } else {
                    builder.appendLine("$space<$name $attributes/>")
                }
            } else {
                if (attributes.isEmpty()) {
                    builder.appendLine("$space<$name>")
                } else {
                    builder.appendLine("$space<$name $attributes>")
                }
                node.getChildren().forEach { entity ->
                    content(builder, node(entity), spaces + 1)
                }
                builder.appendLine("$space</$name>")
            }
        } else if (node is Text) {
            require(node.getChildren().isEmpty()) { "Text has no children" }
            builder.appendLine(space + node.getData())
        } else {
            if (node.getChildren().isEmpty()) {
                builder.appendLine("$space<$name/>")
            } else {
                builder.appendLine("$space<$name>")
                node.getChildren().forEach { entity ->
                    content(builder, node(entity), spaces + 1)
                }
                builder.appendLine("$space</$name>")
            }
        }
    }

    internal fun attributes(element: Element): String {
        val result = StringBuilder()
        element.attributes().forEach { (key, value) ->
            result.append("$key=\"$value\" ")
        }
        return result.toString()
    }

}
