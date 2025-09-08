package io.github.remmerw.saga

import kotlinx.io.Source
import kotlinx.io.readCodePointValue

internal class StopException(val element: Element) : Exception()
class HtmlParser {
    private val model: Model
    private val isXML: Boolean
    private var normalLastTag: String? = null
    private var justReadTagBegin = false
    private var justReadTagEnd = false
    private val isScriptingEnabled: Boolean = false
    private var isDocTypeXHTML = false

    /**
     * Only set when readAttribute returns false.
     */
    private var justReadEmptyElement = false


    constructor(
        model: Model,
        isXML: Boolean
    ) {
        this.model = model
        this.isXML = isXML
    }

    private fun shouldDecodeEntities(info: ElementInfo?): Boolean {
        return isXML || (info == null || info.decodeEntities)
    }


    suspend fun parse(source: Source) {
        parse(source, model)
    }


    suspend fun parse(source: Source, parent: Node) {
        while (this.parseToken(
                parent, source, null,
                ArrayDeque()
            ) != TOKEN_EOD
        ) {
        }
    }

    private suspend fun safeAppendChild(parent: Node, child: Node) {
        parent.appendChild(child)
    }


    private suspend fun parseToken(
        parent: Node,
        source: Source,
        stopTags: MutableSet<String>?,
        ancestors: ArrayDeque<String>
    ): Int {
        val doc = this.model
        val textSb = this.readUpToTagBegin(source)
        if (textSb == null) {
            return TOKEN_EOD
        }
        if (textSb.isNotEmpty()) {
            val decText: StringBuilder = entityDecode(textSb)
            val textNode: Node = doc.createText(decText.toString())
            safeAppendChild(parent, textNode)
        }
        if (this.justReadTagBegin) {
            var tag = this.readTag(parent, source)
            /* always false
            if (tag == null) {
                return TOKEN_EOD
            }*/
            var normalTag: String? = if (isDocTypeXHTML) tag else tag.uppercase()
            try {
                if (tag.startsWith("!")) {
                    when (tag) {
                        "!--" -> {
                            val comment = this.passEndOfComment(source)
                            val decText: StringBuilder = entityDecode(comment)

                            safeAppendChild(parent, doc.createComment(decText.toString()))

                            return TOKEN_COMMENT
                        }

                        "!DOCTYPE" -> {
                            val doctypeStr = this.parseEndOfTag(source)
                            doctypePattern.findAll(doctypeStr).forEach { doctypeMatcher ->
                                val group = doctypeMatcher.groupValues
                                val qName = group[1]
                                val publicId = group[2]
                                val systemId = group[3]
                                val doctype = doc.createDocumentType(
                                    qName,
                                    publicId,
                                    systemId
                                )
                                isDocTypeXHTML = (doctype.qualifiedName == "html")
                                        && (doctype.publicId == XHTML_STRICT_PUBLIC_ID)
                                        && (doctype.systemId == XHTML_STRICT_SYS_ID)
                            }
                            return TOKEN_BAD
                        }

                        else -> {
                            passEndOfTag(source)
                            return TOKEN_BAD
                        }
                    }
                } else if (tag.startsWith("/")) {
                    tag = tag.substring(1)
                    normalTag = normalTag!!.substring(1)
                    this.passEndOfTag(source)
                    return TOKEN_END_ELEMENT
                } else if (tag.startsWith("?")) {
                    tag = tag.substring(1)
                    val data = readProcessingInstruction(source)

                    safeAppendChild(
                        parent, doc.createProcessingInstruction(
                            tag, data.toString()
                        )
                    )

                    return TOKEN_FULL_ELEMENT
                } else {
                    val localIndex = normalTag!!.indexOf(':')
                    val tagHasPrefix = localIndex > 0
                    val localName: String =
                        (if (tagHasPrefix) normalTag.substring(localIndex + 1) else normalTag)
                    var element = doc.createElement(localName)

                    try {
                        if (!this.justReadTagEnd) {
                            while (this.readAttribute(source, element)) {
                                // EMPTY LOOP
                            }
                        }
                        if ((stopTags != null) && stopTags.contains(normalTag)) {
                            // Throw before appending to parent.
                            // After attributes are set.
                            // After MODIFYING_KEY is set.
                            throw StopException(element)
                        }
                        // Add element to parent before children are added.
                        // This is necessary for incremental rendering.
                        safeAppendChild(parent, element)
                        if (!this.justReadEmptyElement) {
                            var einfo: ElementInfo? =
                                ELEMENT_INFOS[localName.uppercase()]
                            var endTagType =
                                einfo?.endElementType ?: ElementInfo.Companion.END_ELEMENT_REQUIRED
                            if (endTagType != ElementInfo.Companion.END_ELEMENT_FORBIDDEN) {
                                var childrenOk = einfo == null || einfo.childElementOk
                                var newStopSet = einfo?.stopTags
                                if (newStopSet == null) {
                                    if (endTagType == ElementInfo.Companion.END_ELEMENT_OPTIONAL) {
                                        newStopSet = mutableSetOf(normalTag)
                                    }
                                }
                                if (stopTags != null) {
                                    if (newStopSet != null) {
                                        val newStopSet2: MutableSet<String> = HashSet()
                                        newStopSet2.addAll(stopTags)
                                        newStopSet2.addAll(newStopSet)
                                        newStopSet = newStopSet2
                                    } else {
                                        newStopSet =
                                            if (endTagType == ElementInfo.Companion.END_ELEMENT_REQUIRED) null else stopTags
                                    }
                                }
                                ancestors.addFirst(normalTag)
                                try {
                                    while (true) {
                                        try {
                                            val token: Int
                                            if ((einfo != null) && einfo.noScriptElement) {

                                                if (isScriptingEnabled) {
                                                    token = this.parseForEndTag(
                                                        parent,
                                                        source,
                                                        tag,
                                                        false,
                                                        shouldDecodeEntities(einfo)
                                                    )
                                                } else {
                                                    token = this.parseToken(
                                                        element,
                                                        source,
                                                        newStopSet,
                                                        ancestors
                                                    )
                                                }
                                            } else {
                                                token = if (childrenOk) this.parseToken(
                                                    element,
                                                    source,
                                                    newStopSet,
                                                    ancestors
                                                ) else this.parseForEndTag(
                                                    element, source,
                                                    tag, true, shouldDecodeEntities(einfo)
                                                )
                                            }
                                            if (token == TOKEN_END_ELEMENT) {
                                                val normalLastTag = this.normalLastTag
                                                if (normalTag.equals(
                                                        normalLastTag,
                                                        ignoreCase = true
                                                    )
                                                ) {
                                                    return TOKEN_FULL_ELEMENT
                                                } else {
                                                    val closeTagInfo: ElementInfo? =
                                                        ELEMENT_INFOS[normalLastTag!!.uppercase()]
                                                    if ((closeTagInfo == null) || (closeTagInfo.endElementType != ElementInfo.Companion.END_ELEMENT_FORBIDDEN)) {
                                                        // TODO: Rather inefficient algorithm, but it's
                                                        // probably executed infrequently?
                                                        val i = ancestors.iterator()
                                                        if (i.hasNext()) {
                                                            i.next()
                                                            while (i.hasNext()) {
                                                                val normalAncestorTag = i.next()
                                                                if (normalLastTag == normalAncestorTag) {
                                                                    normalTag = normalLastTag
                                                                    return TOKEN_END_ELEMENT
                                                                }
                                                            }
                                                        }
                                                    }
                                                    // TODO: Working here
                                                }
                                            } else if (token == TOKEN_EOD) {
                                                return TOKEN_EOD
                                            }
                                        } catch (se: StopException) {
                                            // newElement does not have a parent.
                                            val newElement = se.element
                                            tag = newElement.name
                                            normalTag = tag.uppercase()
                                            // If a subelement throws StopException with
                                            // a tag matching the current stop tag, the exception
                                            // is rethrown (e.g. <TR><TD>blah<TR><TD>blah)
                                            if ((stopTags != null) && stopTags.contains(normalTag)) {
                                                throw se
                                            }
                                            einfo = ELEMENT_INFOS[normalTag]
                                            endTagType =
                                                einfo?.endElementType
                                                    ?: ElementInfo.Companion.END_ELEMENT_REQUIRED
                                            childrenOk = einfo == null || einfo.childElementOk
                                            newStopSet = einfo?.stopTags
                                            if (newStopSet == null) {
                                                if (endTagType == ElementInfo.Companion.END_ELEMENT_OPTIONAL) {
                                                    newStopSet = mutableSetOf(normalTag)
                                                }
                                            }
                                            if ((stopTags != null) && (newStopSet != null)) {
                                                val newStopSet2: MutableSet<String> = HashSet()
                                                newStopSet2.addAll(stopTags)
                                                newStopSet2.addAll(newStopSet)
                                                newStopSet = newStopSet2
                                            }
                                            ancestors.removeFirst()
                                            ancestors.addFirst(normalTag)
                                            // Switch element
                                            // newElement should have been suspended.
                                            element = newElement
                                            // Add to parent
                                            safeAppendChild(parent, element)
                                            if (this.justReadEmptyElement) {
                                                return TOKEN_BEGIN_ELEMENT
                                            }
                                        }
                                    }
                                } finally {
                                    ancestors.removeFirst()
                                }
                            }
                        }
                        return TOKEN_BEGIN_ELEMENT
                    } finally {
                        // This can inform elements to continue with notifications.
                        // It can also cause Javascript to be loaded / processed.

                    }
                }
            } finally {
                this.normalLastTag = normalTag
            }
        } else {
            this.normalLastTag = null
            return TOKEN_TEXT
        }
    }


    private fun readUpToTagBegin(source: Source): StringBuilder? {
        var sb: StringBuilder? = null

        while (!source.exhausted()) {
            val ch = source.readCodePointValue().toChar()
            if (ch == '<') {
                this.justReadTagBegin = true
                this.justReadTagEnd = false
                this.justReadEmptyElement = false
                if (sb == null) {
                    sb = StringBuilder(0)
                }
                return sb
            }
            if (sb == null) {
                sb = StringBuilder()
            }
            sb.append(ch)
        }
        this.justReadTagBegin = false
        this.justReadTagEnd = false
        this.justReadEmptyElement = false
        return sb
    }

    private suspend fun parseForEndTag(
        parent: Node, reader: Source, tagName: String?,
        addTextNode: Boolean,
        decodeEntities: Boolean
    ): Int {
        val doc = this.model
        var intCh: Int
        var sb = StringBuilder()
        while ((reader.readCodePointValue().also { intCh = it }) != -1) {
            var ch = intCh.toChar()
            if (ch == '<') {
                intCh = reader.readCodePointValue()
                if (intCh != -1) {
                    ch = intCh.toChar()
                    if (ch == '/') {
                        val tempBuffer = StringBuilder()
                        while ((reader.readCodePointValue().also { intCh = it }) != -1) {
                            ch = intCh.toChar()
                            if (ch == '>') {
                                val thisTag = tempBuffer.toString().trim { it <= ' ' }
                                if (thisTag.equals(tagName, ignoreCase = true)) {
                                    this.justReadTagBegin = false
                                    this.justReadTagEnd = true
                                    this.justReadEmptyElement = false
                                    this.normalLastTag = thisTag
                                    if (addTextNode) {
                                        if (decodeEntities) {
                                            sb = entityDecode(sb)
                                        }
                                        val text = sb.toString()
                                        if (text.isNotEmpty()) {
                                            val textNode: Node = doc.createText(text)
                                            safeAppendChild(parent, textNode)
                                        }
                                    }
                                    return TOKEN_END_ELEMENT
                                } else {
                                    break
                                }
                            } else {
                                tempBuffer.append(ch)
                            }
                        }
                        sb.append("</")
                        sb.append(tempBuffer)
                    } else if (ch == '!') {
                        val nextSeven: String? = readN(reader, 7)
                        if ("[CDATA[" == nextSeven) {
                            readCData(reader, sb)
                        } else {
                            sb.append('!')
                            if (nextSeven != null) {
                                sb.append(nextSeven)
                            }
                        }
                    } else {
                        sb.append('<')
                        sb.append(ch)
                    }
                } else {
                    sb.append('<')
                }
            } else {
                sb.append(ch)
            }
        }
        this.justReadTagBegin = false
        this.justReadTagEnd = false
        this.justReadEmptyElement = false
        if (addTextNode) {
            if (decodeEntities) {
                sb = entityDecode(sb)
            }
            val text = sb.toString()
            if (text.isNotEmpty()) {
                val textNode: Node = doc.createText(text)
                safeAppendChild(parent, textNode)
            }
        }
        return TOKEN_EOD
    }


    private suspend fun readTag(parent: Node, reader: Source): String {
        val sb = StringBuilder()
        var chInt: Int
        chInt = reader.readCodePointValue()
        if (chInt != -1) {
            var cont = true
            var ch: Char
            LOOP@ while (true) {
                ch = chInt.toChar()
                if (ch.isLetter()) {
                    // Speed up normal case
                    break
                } else if (ch == '!') {
                    sb.append('!')
                    chInt = reader.readCodePointValue()
                    if (chInt != -1) {
                        ch = chInt.toChar()
                        if (ch == '-') {
                            sb.append('-')
                            chInt = reader.readCodePointValue()
                            if (chInt != -1) {
                                ch = chInt.toChar()
                                if (ch == '-') {
                                    sb.append('-')
                                    cont = false
                                }
                            } else {
                                cont = false
                            }
                        }
                    } else {
                        cont = false
                    }
                } else if (ch == '/') {
                    sb.append(ch)
                    chInt = reader.readCodePointValue()
                    if (chInt != -1) {
                        ch = chInt.toChar()
                    } else {
                        cont = false
                    }
                } else if (ch == '<') {
                    val ltText = StringBuilder(3)
                    ltText.append('<')
                    while ((reader.readCodePointValue().also { chInt = it }) == '<'.code) {
                        ltText.append('<')
                    }
                    val doc = this.model
                    val textNode: Node = doc.createText(ltText.toString())
                    parent.appendChild(textNode)

                    if (chInt == -1) {
                        cont = false
                    } else {
                        continue@LOOP
                    }
                } else if (ch.isWhitespace()) {
                    val ltText = StringBuilder()
                    ltText.append('<')
                    ltText.append(ch)
                    while ((reader.readCodePointValue().also { chInt = it }) != -1) {
                        ch = chInt.toChar()
                        if (ch == '<') {
                            chInt = reader.readCodePointValue()
                            break
                        }
                        ltText.append(ch)
                    }
                    val doc = this.model
                    val textNode: Node = doc.createText(ltText.toString())
                    parent.appendChild(textNode)
                    if (chInt == -1) {
                        cont = false
                    } else {
                        continue@LOOP
                    }
                }
                break
            }
            if (cont) {
                var lastCharSlash = false
                while (true) {
                    if (ch.isWhitespace()) {
                        break
                    } else if (ch == '>') {
                        this.justReadTagEnd = true
                        this.justReadTagBegin = false
                        this.justReadEmptyElement = lastCharSlash
                        val tag = sb.toString()
                        return tag
                    } else if (ch == '/') {
                        lastCharSlash = true
                    } else {
                        if (lastCharSlash) {
                            sb.append('/')
                        }
                        lastCharSlash = false
                        sb.append(ch)
                    }
                    chInt = reader.readCodePointValue()
                    if (chInt == -1) {
                        break
                    }
                    ch = chInt.toChar()
                }
            }
        }
        if (sb.isNotEmpty()) {
            this.justReadTagEnd = false
            this.justReadTagBegin = false
            this.justReadEmptyElement = false
        }
        val tag = sb.toString()
        return tag
    }


    private fun passEndOfComment(reader: Source): StringBuilder {
        if (this.justReadTagEnd) {
            return StringBuilder(0)
        }
        val sb = StringBuilder()
        OUTER@ while (true) {
            var chInt = reader.readCodePointValue()
            if (chInt == -1) {
                break
            }
            var ch = chInt.toChar()
            if (ch == '-') {
                chInt = reader.readCodePointValue()
                if (chInt == -1) {
                    sb.append(ch)
                    break
                }
                ch = chInt.toChar()
                if (ch == '-') {
                    var extra: StringBuilder? = null
                    while (true) {
                        chInt = reader.readCodePointValue()
                        if (chInt == -1) {
                            if (extra != null) {
                                sb.append(extra)
                            }
                            break@OUTER
                        }
                        ch = chInt.toChar()
                        if (ch == '>') {
                            this.justReadTagBegin = false
                            this.justReadTagEnd = true
                            return sb
                        } else if (ch == '-') {
                            // Allow any number of dashes at the end
                            if (extra == null) {
                                extra = StringBuilder()
                                extra.append("--")
                            }
                            extra.append("-")
                        } else if (ch.isWhitespace()) {
                            if (extra == null) {
                                extra = StringBuilder()
                                extra.append("--")
                            }
                            extra.append(ch)
                        } else {
                            if (extra != null) {
                                sb.append(extra)
                            }
                            sb.append(ch)
                            break
                        }
                    }
                } else {
                    sb.append('-')
                    sb.append(ch)
                }
            } else {
                sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) {
            this.justReadTagBegin = false
            this.justReadTagEnd = false
        }
        return sb
    }


    private fun parseEndOfTag(reader: Source): String {
        if (this.justReadTagEnd) {
            return ""
        }
        val result = StringBuilder()
        var readSomething = false
        while (true) {
            val chInt = reader.readCodePointValue()
            if (chInt == -1) {
                break
            }
            result.append(chInt.toChar())
            readSomething = true
            val ch = chInt.toChar()
            if (ch == '>') {
                this.justReadTagEnd = true
                this.justReadTagBegin = false
                return result.toString()
            }
        }
        if (readSomething) {
            this.justReadTagBegin = false
            this.justReadTagEnd = false
        }
        return result.toString()
    }


    private fun passEndOfTag(reader: Source) {
        if (this.justReadTagEnd) {
            return
        }
        var readSomething = false
        while (true) {
            val chInt = reader.readCodePointValue()
            if (chInt == -1) {
                break
            }
            readSomething = true
            val ch = chInt.toChar()
            if (ch == '>') {
                this.justReadTagEnd = true
                this.justReadTagBegin = false
                return
            }
        }
        if (readSomething) {
            this.justReadTagBegin = false
            this.justReadTagEnd = false
        }
    }


    private fun readProcessingInstruction(reader: Source): StringBuilder {
        val pidata = StringBuilder()
        if (this.justReadTagEnd) {
            return pidata
        }
        var ch: Int
        ch = reader.readCodePointValue()
        while ((ch != -1) && (ch != '>'.code)) {
            pidata.append(ch.toChar())
            ch = reader.readCodePointValue()
        }
        this.justReadTagBegin = false
        this.justReadTagEnd = ch != -1
        return pidata
    }


    private suspend fun readAttribute(reader: Source, element: Element): Boolean {
        if (this.justReadTagEnd) {
            return false
        }

        // Read attribute name up to '=' character.
        // May read several attribute names without explicit values.
        var attributeName: StringBuilder? = null
        var blankFound = false
        var lastCharSlash = false
        while (true) {
            val chInt = reader.readCodePointValue()
            if (chInt == -1) {
                if ((attributeName != null) && (attributeName.isNotEmpty())) {
                    val attributeNameStr = attributeName.toString()
                    element.setAttribute(attributeNameStr, attributeNameStr)
                    attributeName.setLength(0)
                }
                this.justReadTagBegin = false
                this.justReadTagEnd = false
                this.justReadEmptyElement = false
                return false
            }
            val ch = chInt.toChar()
            if (ch == '=') {
                lastCharSlash = false
                blankFound = false
                break
            } else if (ch == '>') {
                if ((attributeName != null) && (attributeName.isNotEmpty())) {
                    val attributeNameStr = attributeName.toString()
                    element.setAttribute(attributeNameStr, attributeNameStr)
                }
                this.justReadTagBegin = false
                this.justReadTagEnd = true
                this.justReadEmptyElement = lastCharSlash
                return false
            } else if (ch == '/') {
                blankFound = true
                lastCharSlash = true
            } else if (ch.isWhitespace()) {
                lastCharSlash = false
                blankFound = true
            } else {
                lastCharSlash = false
                if (blankFound) {
                    blankFound = false
                    if ((attributeName != null) && (attributeName.isNotEmpty())) {
                        val attributeNameStr = attributeName.toString()
                        element.setAttribute(attributeNameStr, attributeNameStr)
                        attributeName.setLength(0)
                    }
                }
                if (attributeName == null) {
                    attributeName = StringBuilder(6)
                }
                attributeName.append(ch)
            }
        }
        // Read blanks up to open quote or first non-blank.
        var attributeValue: StringBuilder? = null
        var openQuote = -1
        while (true) {
            val chInt = reader.readCodePointValue()
            if (chInt == -1) {
                break
            }
            val ch = chInt.toChar()
            if (ch == '>') {
                if ((attributeName != null) && (attributeName.isNotEmpty())) {
                    val attributeNameStr = attributeName.toString()
                    element.setAttribute(attributeNameStr, attributeNameStr)
                }
                this.justReadTagBegin = false
                this.justReadTagEnd = true
                this.justReadEmptyElement = lastCharSlash
                return false
            } else if (ch == '/') {
                lastCharSlash = true
            } else if (ch.isWhitespace()) {
                lastCharSlash = false
            } else {
                when (ch) {
                    '"' -> {
                        openQuote = '"'.code
                    }

                    '\'' -> {
                        openQuote = '\''.code
                    }

                    else -> {
                        openQuote = -1
                        attributeValue = StringBuilder(6)
                        if (lastCharSlash) {
                            attributeValue.append('/')
                        }
                        attributeValue.append(ch)
                    }
                }
                lastCharSlash = false
                break
            }
        }

        // Read attribute value
        while (true) {
            val chInt = reader.readCodePointValue()
            if (chInt == -1) {
                break
            }
            val ch = chInt.toChar()
            if ((openQuote != -1) && (ch.code == openQuote)) {
                if (attributeName != null) {
                    val attributeNameStr = attributeName.toString()
                    if (attributeValue == null) {
                        // Quotes are closed. There's a distinction
                        // between blank values and null in HTML, as
                        // processed by major browsers.
                        element.setAttribute(attributeNameStr, "")
                    } else {
                        val actualAttributeValue: StringBuilder = entityDecode(attributeValue)
                        element.setAttribute(attributeNameStr, actualAttributeValue.toString())
                    }
                }
                this.justReadTagBegin = false
                this.justReadTagEnd = false
                return true
            } else if ((openQuote == -1) && (ch == '>')) {
                if (attributeName != null) {
                    val attributeNameStr = attributeName.toString()
                    if (attributeValue == null) {
                        element.setAttribute(attributeNameStr, "")
                    } else {
                        val actualAttributeValue: StringBuilder = entityDecode(attributeValue)
                        element.setAttribute(attributeNameStr, actualAttributeValue.toString())
                    }
                }
                this.justReadTagBegin = false
                this.justReadTagEnd = true
                this.justReadEmptyElement = lastCharSlash
                return false
            } else if ((openQuote == -1) && ch.isWhitespace()) {
                lastCharSlash = false
                if (attributeName != null) {
                    val attributeNameStr = attributeName.toString()
                    if (attributeValue == null) {
                        element.setAttribute(attributeNameStr, "")
                    } else {
                        val actualAttributeValue: StringBuilder = entityDecode(attributeValue)
                        element.setAttribute(attributeNameStr, actualAttributeValue.toString())
                    }
                }
                this.justReadTagBegin = false
                this.justReadTagEnd = false
                return true
            } else {
                if (attributeValue == null) {
                    attributeValue = StringBuilder(6)
                }
                if (lastCharSlash) {
                    attributeValue.append('/')
                }
                lastCharSlash = false
                attributeValue.append(ch)
            }
        }
        this.justReadTagBegin = false
        this.justReadTagEnd = false
        if (attributeName != null) {
            val attributeNameStr = attributeName.toString()
            if (attributeValue == null) {
                element.setAttribute(attributeNameStr, "")
            } else {
                val actualAttributeValue: StringBuilder = entityDecode(attributeValue)
                element.setAttribute(attributeNameStr, actualAttributeValue.toString())
            }
        }
        return false
    }

    companion object {
        private val ENTITIES: MutableMap<String, Char> = HashMap<String, Char>(256)
        private val ELEMENT_INFOS: MutableMap<String, ElementInfo> =
            HashMap<String, ElementInfo>(35)
        private const val TOKEN_EOD = 0
        private const val TOKEN_COMMENT = 1
        private const val TOKEN_TEXT = 2
        private const val TOKEN_BEGIN_ELEMENT = 3
        private const val TOKEN_END_ELEMENT = 4
        private const val TOKEN_FULL_ELEMENT = 5
        private const val TOKEN_BAD = 6

        private val doctypePattern =
            Regex("(\\S+)\\s+PUBLIC\\s+\"([^\"]*)\"\\s+\"([^\"]*)\".*>")

        init {
            val entities: MutableMap<String, Char> = ENTITIES
            entities.put("amp", '&')
            entities.put("lt", '<')
            entities.put("gt", '>')
            entities.put("quot", '"')
            entities.put("nbsp", (160.toChar()))

            entities.put("lsquo", '\u2018')
            entities.put("rsquo", ('\u2019'))

            entities.put("frasl", (47.toChar()))
            entities.put("ndash", (8211.toChar()))
            entities.put("mdash", (8212.toChar()))
            entities.put("iexcl", (161.toChar()))
            entities.put("cent", (162.toChar()))
            entities.put("pound", (163.toChar()))
            entities.put("curren", (164.toChar()))
            entities.put("yen", (165.toChar()))
            entities.put("brvbar", (166.toChar()))
            entities.put("brkbar", (166.toChar()))
            entities.put("sect", (167.toChar()))
            entities.put("uml", (168.toChar()))
            entities.put("die", (168.toChar()))
            entities.put("copy", (169.toChar()))
            entities.put("ordf", (170.toChar()))
            entities.put("laquo", (171.toChar()))
            entities.put("not", (172.toChar()))
            entities.put("shy", (173.toChar()))
            entities.put("reg", (174.toChar()))
            entities.put("macr", (175.toChar()))
            entities.put("hibar", (175.toChar()))
            entities.put("deg", (176.toChar()))
            entities.put("plusmn", (177.toChar()))
            entities.put("sup2", (178.toChar()))
            entities.put("sup3", (179.toChar()))
            entities.put("acute", (180.toChar()))
            entities.put("micro", (181.toChar()))
            entities.put("para", (182.toChar()))
            entities.put("middot", (183.toChar()))
            entities.put("cedil", (184.toChar()))
            entities.put("sup1", (185.toChar()))
            entities.put("ordm", (186.toChar()))
            entities.put("raquo", (187.toChar()))
            entities.put("frac14", (188.toChar()))
            entities.put("frac12", (189.toChar()))
            entities.put("frac34", (190.toChar()))
            entities.put("iquest", (191.toChar()))
            entities.put("Agrave", (192.toChar()))
            entities.put("Aacute", (193.toChar()))
            entities.put("Acirc", (194.toChar()))
            entities.put("Atilde", (195.toChar()))
            entities.put("Auml", (196.toChar()))
            entities.put("Aring", (197.toChar()))
            entities.put("AElig", (198.toChar()))
            entities.put("Ccedil", (199.toChar()))
            entities.put("Egrave", (200.toChar()))
            entities.put("Eacute", (201.toChar()))
            entities.put("Ecirc", (202.toChar()))
            entities.put("Euml", (203.toChar()))
            entities.put("Igrave", (204.toChar()))
            entities.put("Iacute", (205.toChar()))
            entities.put("Icirc", (206.toChar()))
            entities.put("Iuml", (207.toChar()))
            entities.put("ETH", (208.toChar()))
            entities.put("Ntilde", (209.toChar()))
            entities.put("Ograve", (210.toChar()))
            entities.put("Oacute", (211.toChar()))
            entities.put("Ocirc", (212.toChar()))
            entities.put("Otilde", (213.toChar()))
            entities.put("Ouml", (214.toChar()))
            entities.put("times", (215.toChar()))
            entities.put("Oslash", (216.toChar()))
            entities.put("Ugrave", (217.toChar()))
            entities.put("Uacute", (218.toChar()))
            entities.put("Ucirc", (219.toChar()))
            entities.put("Uuml", (220.toChar()))
            entities.put("Yacute", (221.toChar()))
            entities.put("THORN", (222.toChar()))
            entities.put("szlig", (223.toChar()))
            entities.put("agrave", (224.toChar()))
            entities.put("aacute", (225.toChar()))
            entities.put("acirc", (226.toChar()))
            entities.put("atilde", (227.toChar()))
            entities.put("auml", (228.toChar()))
            entities.put("aring", (229.toChar()))
            entities.put("aelig", (230.toChar()))
            entities.put("ccedil", (231.toChar()))
            entities.put("egrave", (232.toChar()))
            entities.put("eacute", (233.toChar()))
            entities.put("ecirc", (234.toChar()))
            entities.put("euml", (235.toChar()))
            entities.put("igrave", (236.toChar()))
            entities.put("iacute", (237.toChar()))
            entities.put("icirc", (238.toChar()))
            entities.put("iuml", (239.toChar()))
            entities.put("eth", (240.toChar()))
            entities.put("ntilde", (241.toChar()))
            entities.put("ograve", (242.toChar()))
            entities.put("oacute", (243.toChar()))
            entities.put("ocirc", (244.toChar()))
            entities.put("otilde", (245.toChar()))
            entities.put("ouml", (246.toChar()))
            entities.put("divide", (247.toChar()))
            entities.put("oslash", (248.toChar()))
            entities.put("ugrave", (249.toChar()))
            entities.put("uacute", (250.toChar()))
            entities.put("ucirc", (251.toChar()))
            entities.put("uuml", (252.toChar()))
            entities.put("yacute", (253.toChar()))
            entities.put("thorn", (254.toChar()))
            entities.put("yuml", (255.toChar()))

            // symbols from http://de.selfhtml.org/html/referenz/zeichen.htm

            // greek letters
            entities.put("Alpha", (913.toChar()))
            entities.put("Beta", (914.toChar()))
            entities.put("Gamma", (915.toChar()))
            entities.put("Delta", (916.toChar()))
            entities.put("Epsilon", (917.toChar()))
            entities.put("Zeta", (918.toChar()))
            entities.put("Eta", (919.toChar()))
            entities.put("Theta", (920.toChar()))
            entities.put("Iota", (921.toChar()))
            entities.put("Kappa", (922.toChar()))
            entities.put("Lambda", (923.toChar()))
            entities.put("Mu", (924.toChar()))
            entities.put("Nu", (925.toChar()))
            entities.put("Xi", (926.toChar()))
            entities.put("Omicron", (927.toChar()))
            entities.put("Pi", (928.toChar()))
            entities.put("Rho", (929.toChar()))
            entities.put("Sigma", (930.toChar()))
            entities.put("Sigmaf", (931.toChar()))
            entities.put("Tau", (932.toChar()))
            entities.put("Upsilon", (933.toChar()))
            entities.put("Phi", (934.toChar()))
            entities.put("Chi", (935.toChar()))
            entities.put("Psi", (936.toChar()))
            entities.put("Omega", (937.toChar()))

            entities.put("alpha", (945.toChar()))
            entities.put("beta", (946.toChar()))
            entities.put("gamma", (947.toChar()))
            entities.put("delta", (948.toChar()))
            entities.put("epsilon", (949.toChar()))
            entities.put("zeta", (950.toChar()))
            entities.put("eta", (951.toChar()))
            entities.put("theta", (952.toChar()))
            entities.put("iota", (953.toChar()))
            entities.put("kappa", (954.toChar()))
            entities.put("lambda", (955.toChar()))
            entities.put("mu", (956.toChar()))
            entities.put("nu", (957.toChar()))
            entities.put("xi", (958.toChar()))
            entities.put("omicron", (959.toChar()))
            entities.put("pi", (960.toChar()))
            entities.put("rho", (961.toChar()))
            entities.put("sigma", (962.toChar()))
            entities.put("sigmaf", (963.toChar()))
            entities.put("tau", (964.toChar()))
            entities.put("upsilon", (965.toChar()))
            entities.put("phi", (966.toChar()))
            entities.put("chi", (967.toChar()))
            entities.put("psi", (968.toChar()))
            entities.put("omega", (969.toChar()))
            entities.put("thetasym", (977.toChar()))
            entities.put("upsih", (978.toChar()))
            entities.put("piv", (982.toChar()))

            // math symbols
            entities.put("forall", (8704.toChar()))
            entities.put("part", (8706.toChar()))
            entities.put("exist", (8707.toChar()))
            entities.put("empty", (8709.toChar()))
            entities.put("nabla", (8711.toChar()))
            entities.put("isin", (8712.toChar()))
            entities.put("notin", (8713.toChar()))
            entities.put("ni", (8715.toChar()))
            entities.put("prod", (8719.toChar()))
            entities.put("sum", (8721.toChar()))
            entities.put("minus", (8722.toChar()))
            entities.put("lowast", (8727.toChar()))
            entities.put("radic", (8730.toChar()))
            entities.put("prop", (8733.toChar()))
            entities.put("infin", (8734.toChar()))
            entities.put("ang", (8736.toChar()))
            entities.put("and", (8743.toChar()))
            entities.put("or", (8744.toChar()))
            entities.put("cap", (8745.toChar()))
            entities.put("cup", (8746.toChar()))
            entities.put("int", (8747.toChar()))
            entities.put("there4", (8756.toChar()))
            entities.put("sim", (8764.toChar()))
            entities.put("cong", (8773.toChar()))
            entities.put("asymp", (8776.toChar()))
            entities.put("ne", (8800.toChar()))
            entities.put("equiv", (8801.toChar()))
            entities.put("le", (8804.toChar()))
            entities.put("ge", (8805.toChar()))
            entities.put("sub", (8834.toChar()))
            entities.put("sup", (8835.toChar()))
            entities.put("nsub", (8836.toChar()))
            entities.put("sube", (8838.toChar()))
            entities.put("supe", (8839.toChar()))
            entities.put("oplus", (8853.toChar()))
            entities.put("otimes", (8855.toChar()))
            entities.put("perp", (8869.toChar()))
            entities.put("sdot", (8901.toChar()))
            entities.put("loz", (9674.toChar()))

            // technical symbols
            entities.put("lceil", (8968.toChar()))
            entities.put("rceil", (8969.toChar()))
            entities.put("lfloor", (8970.toChar()))
            entities.put("rfloor", (8971.toChar()))
            entities.put("lang", (9001.toChar()))
            entities.put("rang", (9002.toChar()))

            // arrow symbols
            entities.put("larr", (8592.toChar()))
            entities.put("uarr", (8593.toChar()))
            entities.put("rarr", (8594.toChar()))
            entities.put("darr", (8595.toChar()))
            entities.put("harr", (8596.toChar()))
            entities.put("crarr", (8629.toChar()))
            entities.put("lArr", (8656.toChar()))
            entities.put("uArr", (8657.toChar()))
            entities.put("rArr", (8658.toChar()))
            entities.put("dArr", (8659.toChar()))
            entities.put("hArr", (8960.toChar()))

            // divers symbols
            entities.put("bull", (8226.toChar()))
            entities.put("prime", (8242.toChar()))
            entities.put("Prime", (8243.toChar()))
            entities.put("oline", (8254.toChar()))
            entities.put("weierp", (8472.toChar()))
            entities.put("image", (8465.toChar()))
            entities.put("real", (8476.toChar()))
            entities.put("trade", (8482.toChar()))
            entities.put("euro", (8364.toChar()))
            entities.put("alefsym", (8501.toChar()))
            entities.put("spades", (9824.toChar()))
            entities.put("clubs", (9827.toChar()))
            entities.put("hearts", (9829.toChar()))
            entities.put("diams", (9830.toChar()))

            // ext lat symbols
            entities.put("OElig", (338.toChar()))
            entities.put("oelig", (339.toChar()))
            entities.put("Scaron", (352.toChar()))
            entities.put("scaron", (353.toChar()))
            entities.put("fnof", (402.toChar()))

            // interpunction
            entities.put("ensp", (8194.toChar()))
            entities.put("emsp", (8195.toChar()))
            entities.put("thinsp", (8201.toChar()))
            entities.put("zwnj", (8204.toChar()))
            entities.put("zwj", (8205.toChar()))
            entities.put("lrm", (8206.toChar()))
            entities.put("rlm", (8207.toChar()))

            entities.put("sbquo", (8218.toChar()))
            entities.put("ldquo", (8220.toChar()))
            entities.put("rdquo", (8221.toChar()))
            entities.put("bdquo", (8222.toChar()))
            entities.put("dagger", (8224.toChar()))
            entities.put("Dagger", (8225.toChar()))
            entities.put("hellip", (8230.toChar()))
            entities.put("permil", (8240.toChar()))
            entities.put("lsaquo", (8249.toChar()))
            entities.put("rsaquo", (8250.toChar()))

            // diacrit symb
            entities.put("circ", (710.toChar()))
            entities.put("tilde", (732.toChar()))

            val elementInfos: MutableMap<String, ElementInfo> = ELEMENT_INFOS

            elementInfos.put(
                "NOSCRIPT",
                ElementInfo(true, ElementInfo.Companion.END_ELEMENT_REQUIRED, null, true)
            )

            val optionalEndElement = ElementInfo(true, ElementInfo.Companion.END_ELEMENT_OPTIONAL)
            val forbiddenEndElement =
                ElementInfo(false, ElementInfo.Companion.END_ELEMENT_FORBIDDEN)
            val onlyTextDE = ElementInfo(false, ElementInfo.Companion.END_ELEMENT_REQUIRED, true)
            val onlyText = ElementInfo(false, ElementInfo.Companion.END_ELEMENT_REQUIRED, false)

            val tableCellStopElements: MutableSet<String> = HashSet() // todo optimize
            tableCellStopElements.add("TH")
            tableCellStopElements.add("TD")
            tableCellStopElements.add("TR")
            val tableCellElement =
                ElementInfo(true, ElementInfo.Companion.END_ELEMENT_OPTIONAL, tableCellStopElements)

            val headStopElements: MutableSet<String> = HashSet()
            headStopElements.add("BODY")
            headStopElements.add("DIV")
            headStopElements.add("SPAN")
            headStopElements.add("TABLE")
            val headElement =
                ElementInfo(true, ElementInfo.Companion.END_ELEMENT_OPTIONAL, headStopElements)

            val optionStopElements: MutableSet<String> = HashSet()
            optionStopElements.add("OPTION")
            optionStopElements.add("SELECT")
            val optionElement =
                ElementInfo(true, ElementInfo.Companion.END_ELEMENT_OPTIONAL, optionStopElements)

            val paragraphStopElements: MutableSet<String> = HashSet()
            paragraphStopElements.add("P")
            paragraphStopElements.add("DIV")
            paragraphStopElements.add("TABLE")
            paragraphStopElements.add("PRE")
            paragraphStopElements.add("UL")
            paragraphStopElements.add("OL")
            val paragraphElement =
                ElementInfo(true, ElementInfo.Companion.END_ELEMENT_OPTIONAL, paragraphStopElements)

            // Set liStopElements = new HashSet();
            // liStopElements.add("LI");
            // liStopElements.add("UL");
            // liStopElements.add("OL");
            elementInfos.put("SCRIPT", onlyText)
            elementInfos.put("STYLE", onlyText)
            elementInfos.put("TEXTAREA", onlyTextDE)
            elementInfos.put("IMG", forbiddenEndElement)
            elementInfos.put("META", forbiddenEndElement)
            elementInfos.put("LINK", forbiddenEndElement)
            elementInfos.put("BASE", forbiddenEndElement)
            elementInfos.put("INPUT", forbiddenEndElement)
            elementInfos.put("FRAME", forbiddenEndElement)
            elementInfos.put("BR", forbiddenEndElement)
            elementInfos.put("HR", forbiddenEndElement)
            elementInfos.put("EMBED", forbiddenEndElement)
            elementInfos.put("SPACER", forbiddenEndElement)

            elementInfos.put("P", paragraphElement)
            elementInfos.put("LI", optionalEndElement)
            elementInfos.put("DT", optionalEndElement)
            elementInfos.put("DD", optionalEndElement)
            elementInfos.put("TR", optionalEndElement)
            elementInfos.put("TH", tableCellElement)
            elementInfos.put("TD", tableCellElement)
            elementInfos.put("HEAD", headElement)
            elementInfos.put("OPTION", optionElement)

            // Note: The specification states anchors have
            // a required end element, but browsers generally behave
            // as if it's optional.
            elementInfos.put("A", optionalEndElement)
            elementInfos.put("ANCHOR", optionalEndElement)
            // TODO: Keep adding tags here
        }


        private fun readCData(reader: Source, sb: StringBuilder) {
            var next = reader.readCodePointValue()

            while (next >= 0) {
                val nextCh = next.toChar()
                if (nextCh == ']') {
                    val next2: String? = readN(reader, 2)
                    if (next2 != null) {
                        if ("]>" == next2) {
                            break
                        } else {
                            sb.append(nextCh)
                            sb.append(next2)
                            next = reader.readCodePointValue()
                        }
                    } else {
                        break
                    }
                } else {
                    sb.append(nextCh)
                    next = reader.readCodePointValue()
                }
            }
        }

        // Tries to read at most n characters.
        private fun readN(reader: Source, n: Int): String? {
            val chars = CharArray(n)
            var i = 0
            while (i < n) {
                var ich: Int
                try {
                    ich = reader.readCodePointValue()
                } catch (_: Throwable) {
                    break
                }
                if (ich >= 0) {
                    chars[i] = ich.toChar()
                    i += 1
                } else {
                    break
                }
            }

            return if (i == 0) {
                null
            } else {
                chars.concatToString(0, 0 + i)
            }
        }


        private fun entityDecode(rawText: StringBuilder): StringBuilder {
            var startIdx = 0
            var sb: StringBuilder? = null
            while (true) {
                val ampIdx = rawText.indexOf("&", startIdx)
                if (ampIdx == -1) {
                    if (sb == null) {
                        return rawText
                    } else {
                        sb.append(rawText.substring(startIdx))
                        return sb
                    }
                }
                if (sb == null) {
                    sb = StringBuilder()
                }
                sb.append(rawText.substring(startIdx, ampIdx))
                val colonIdx = rawText.indexOf(";", ampIdx)
                if (colonIdx == -1) {
                    sb.append('&')
                    startIdx = ampIdx + 1
                    continue
                }
                val spec = rawText.substring(ampIdx + 1, colonIdx)
                if (spec.startsWith("#")) {
                    val number = spec.substring(1).lowercase()
                    var decimal: Int
                    try {
                        decimal = if (number.startsWith("x")) {
                            number.substring(1).toInt(16)
                        } else {
                            number.toInt()
                        }
                    } catch (_: NumberFormatException) {
                        debug("ERROR: entityDecode()")
                        decimal = 0
                    }
                    sb.append(decimal.toChar())
                } else {
                    val chInt: Int = getEntityChar(spec)
                    if (chInt == -1) {
                        sb.append('&')
                        sb.append(spec)
                        sb.append(';')
                    } else {
                        sb.append(chInt.toChar())
                    }
                }
                startIdx = colonIdx + 1
            }
        }

        private fun getEntityChar(spec: String): Int {
            // TODO: Declared entities
            var c: Char? = ENTITIES[spec]
            if (c == null) {
                val specTL = spec.lowercase()
                c = ENTITIES[specTL]
                if (c == null) {
                    return -1
                }
            }
            return c.code
        }
    }
}
