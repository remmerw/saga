package io.github.remmerw.saga

import kotlinx.io.Source
import kotlinx.io.readCodePointValue

internal class StopException(val element: Node) : Exception()

internal const val END_ELEMENT_FORBIDDEN: Int = 0
internal const val END_ELEMENT_OPTIONAL: Int = 1
internal const val END_ELEMENT_REQUIRED: Int = 2

class Parser(val model: Model) {

    private var normalLastTag: String? = null
    private var justReadTagBegin = false
    private var justReadTagEnd = false
    private var rootDetected = false
    private var justReadEmptyElement = false

    fun purify(text: String): String {
        if (text.startsWith("\n")) {
            return text.replaceFirst("\n", "").trim()
        }
        return text
    }

    fun parse(source: Source) {
        parse(source, model)
    }


    fun parse(source: Source, parent: Node) {
        @Suppress("ControlFlowWithEmptyBody")
        while (this.parseToken(
                parent, source, null,
                ArrayDeque()
            ) != TOKEN_EOD
        ) {
        }
    }


    private fun parseToken(
        parent: Node,
        source: Source,
        stopTags: MutableSet<String>?,
        ancestors: ArrayDeque<String>
    ): Int {

        val model = this.model
        val textSb = this.readUpToTagBegin(source) ?: return TOKEN_EOD
        if (textSb.isNotEmpty()) {
            val decText: StringBuilder = entityDecode(textSb)
            val text = purify(decText.toString())
            if (text.isNotEmpty()) {
                model.setData(parent, text)
            }
        }
        if (this.justReadTagBegin) {
            var tag = this.readTag(parent, source)
            /* always false
            if (tag == null) {
                return TOKEN_EOD
            }*/
            var normalTag: String? = tag
            try {
                if (tag.startsWith("!")) {
                    when (tag) {
                        "!--" -> {
                            val comment = this.passEndOfComment(source)
                            val decText: StringBuilder = entityDecode(comment)

                            debug("comment $decText")

                            return TOKEN_COMMENT
                        }

                        "!DOCTYPE" -> {
                            val doctypeStr = this.parseEndOfTag(source)
                            doctypePattern.findAll(doctypeStr).forEach { doctypeMatcher ->
                                val group = doctypeMatcher.groupValues
                                val qName = group[1]
                                val publicId = group[2]
                                val systemId = group[3]
                                debug("group $qName")
                                debug("qName $publicId")
                                debug("systemId $systemId")
                            }
                            return TOKEN_BAD
                        }

                        else -> {
                            passEndOfTag(source)
                            return TOKEN_BAD
                        }
                    }
                } else if (tag.startsWith("/")) {
                    normalTag = normalTag!!.substring(1)
                    this.passEndOfTag(source)
                    return TOKEN_END_ELEMENT
                } else if (tag.startsWith("?")) {
                    val data = readProcessingInstruction(source)
                    debug("processing instruction $data")

                    return TOKEN_FULL_ELEMENT
                } else {
                    val localIndex = normalTag!!.indexOf(':')
                    val tagHasPrefix = localIndex > 0
                    val localName: String =
                        (if (tagHasPrefix) normalTag.substring(localIndex + 1) else normalTag)

                    var element: Node
                    if (model.isEmpty() && !rootDetected) {
                        require(localName.toTag() == model.entity.tag) { "Invalid root element" }
                        rootDetected = true
                        element = model
                    } else {
                        element = model.createNode(localName.toTag())
                    }

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
                        if (!model.isEmpty()) {
                            parent.appendChild(element)
                        }
                        if (!this.justReadEmptyElement) {

                            var endTagType = END_ELEMENT_REQUIRED
                            if (endTagType != END_ELEMENT_FORBIDDEN) {
                                var childrenOk = true
                                var newStopSet: MutableSet<String>? = null
                                if (endTagType == END_ELEMENT_OPTIONAL) {
                                    newStopSet = mutableSetOf(normalTag)
                                }
                                if (stopTags != null) {
                                    if (newStopSet != null) {
                                        val newStopSet2: MutableSet<String> = HashSet()
                                        newStopSet2.addAll(stopTags)
                                        newStopSet2.addAll(newStopSet)
                                        newStopSet = newStopSet2
                                    } else {
                                        newStopSet =
                                            if (endTagType == END_ELEMENT_REQUIRED) null else stopTags
                                    }
                                }
                                ancestors.addFirst(normalTag)
                                try {
                                    while (true) {
                                        try {

                                            val token: Int = if (childrenOk) this.parseToken(
                                                element,
                                                source,
                                                newStopSet,
                                                ancestors
                                            ) else this.parseForEndTag(
                                                element, source, tag

                                            )

                                            if (token == TOKEN_END_ELEMENT) {
                                                val normalLastTag = this.normalLastTag
                                                if (normalTag.equals(
                                                        normalLastTag,
                                                        ignoreCase = true
                                                    )
                                                ) {
                                                    return TOKEN_FULL_ELEMENT
                                                } else {

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

                                                    // TODO: Working here
                                                }
                                            } else if (token == TOKEN_EOD) {
                                                return TOKEN_EOD
                                            }
                                        } catch (se: StopException) {
                                            // newElement does not have a parent.
                                            val newElement = se.element
                                            tag = newElement.entity.tag.toString()
                                            normalTag = tag
                                            // If a subelement throws StopException with
                                            // a tag matching the current stop tag, the exception
                                            // is rethrown (e.g. <TR><TD>blah<TR><TD>blah)
                                            if ((stopTags != null) && stopTags.contains(normalTag)) {
                                                throw se
                                            }

                                            endTagType = END_ELEMENT_REQUIRED
                                            childrenOk = true
                                            newStopSet = null
                                            if (endTagType == END_ELEMENT_OPTIONAL) {
                                                newStopSet = mutableSetOf(normalTag)
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
                                            parent.appendChild(element)

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
                        // It can also cause JavaScript to be loaded / processed.

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

    private fun parseForEndTag(
        parent: Node, reader: Source, tagName: String?
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


                                    sb = entityDecode(sb)

                                    val text = purify(sb.toString())
                                    if (text.isNotEmpty()) {
                                        doc.setData(parent, text)
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


        sb = entityDecode(sb)

        val text = purify(sb.toString())
        if (text.isNotEmpty()) {
            doc.setData(parent, text)
        }

        return TOKEN_EOD
    }


    private fun readTag(parent: Node, reader: Source): String {
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
                    val text = purify(ltText.toString())
                    if (text.isNotEmpty()) {
                        doc.setData(parent, text)
                    }

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
                    val text = purify(ltText.toString())
                    if (text.isNotEmpty()) {
                        doc.setData(parent, text)
                    }
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


    private fun readAttribute(reader: Source, element: Node): Boolean {
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
                if (!attributeName.isNullOrEmpty()) {
                    val attributeNameStr = attributeName.toString()
                    element.setAttribute(attributeNameStr.toKey(), attributeNameStr.toValue())
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
                break
            } else if (ch == '>') {
                if (!attributeName.isNullOrEmpty()) {
                    val attributeNameStr = attributeName.toString()
                    element.setAttribute(attributeNameStr.toKey(), attributeNameStr.toValue())
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
                    if (!attributeName.isNullOrEmpty()) {
                        val attributeNameStr = attributeName.toString()
                        element.setAttribute(attributeNameStr.toKey(), attributeNameStr.toValue())
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
                if (!attributeName.isNullOrEmpty()) {
                    val attributeNameStr = attributeName.toString()
                    element.setAttribute(attributeNameStr.toKey(), attributeNameStr.toValue())
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
                        element.setAttribute(attributeNameStr.toKey(), "".toValue())
                    } else {
                        val actualAttributeValue: StringBuilder = entityDecode(attributeValue)
                        element.setAttribute(
                            attributeNameStr.toKey(),
                            actualAttributeValue.toString().toValue()
                        )
                    }
                }
                this.justReadTagBegin = false
                this.justReadTagEnd = false
                return true
            } else if ((openQuote == -1) && (ch == '>')) {
                if (attributeName != null) {
                    val attributeNameStr = attributeName.toString()
                    if (attributeValue == null) {
                        element.setAttribute(attributeNameStr.toKey(), "".toValue())
                    } else {
                        val actualAttributeValue: StringBuilder = entityDecode(attributeValue)
                        element.setAttribute(
                            attributeNameStr.toKey(),
                            actualAttributeValue.toString().toValue()
                        )
                    }
                }
                this.justReadTagBegin = false
                this.justReadTagEnd = true
                this.justReadEmptyElement = lastCharSlash
                return false
            } else if ((openQuote == -1) && ch.isWhitespace()) {
                if (attributeName != null) {
                    val attributeNameStr = attributeName.toString()
                    if (attributeValue == null) {
                        element.setAttribute(attributeNameStr.toKey(), "".toValue())
                    } else {
                        val actualAttributeValue: StringBuilder = entityDecode(attributeValue)
                        element.setAttribute(
                            attributeNameStr.toKey(),
                            actualAttributeValue.toString().toValue()
                        )
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
                element.setAttribute(attributeNameStr.toKey(), "".toValue())
            } else {
                val actualAttributeValue: StringBuilder = entityDecode(attributeValue)
                element.setAttribute(
                    attributeNameStr.toKey(),
                    actualAttributeValue.toString().toValue()
                )
            }
        }
        return false
    }

    companion object {
        private val ENTITIES: MutableMap<String, Char> = HashMap(256)
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
            entities["amp"] = '&'
            entities["lt"] = '<'
            entities["gt"] = '>'
            entities["quot"] = '"'
            entities["nbsp"] = (160.toChar())

            entities["lsquo"] = '\u2018'
            entities["rsquo"] = ('\u2019')

            entities["frasl"] = (47.toChar())
            entities["ndash"] = (8211.toChar())
            entities["mdash"] = (8212.toChar())
            entities["iexcl"] = (161.toChar())
            entities["cent"] = (162.toChar())
            entities["pound"] = (163.toChar())
            entities["curren"] = (164.toChar())
            entities["yen"] = (165.toChar())
            entities["brvbar"] = (166.toChar())
            entities["brkbar"] = (166.toChar())
            entities["sect"] = (167.toChar())
            entities["uml"] = (168.toChar())
            entities["die"] = (168.toChar())
            entities["copy"] = (169.toChar())
            entities["ordf"] = (170.toChar())
            entities["laquo"] = (171.toChar())
            entities["not"] = (172.toChar())
            entities["shy"] = (173.toChar())
            entities["reg"] = (174.toChar())
            entities["macr"] = (175.toChar())
            entities["hibar"] = (175.toChar())
            entities["deg"] = (176.toChar())
            entities["plusmn"] = (177.toChar())
            entities["sup2"] = (178.toChar())
            entities["sup3"] = (179.toChar())
            entities["acute"] = (180.toChar())
            entities["micro"] = (181.toChar())
            entities["para"] = (182.toChar())
            entities["middot"] = (183.toChar())
            entities["cedil"] = (184.toChar())
            entities["sup1"] = (185.toChar())
            entities["ordm"] = (186.toChar())
            entities["raquo"] = (187.toChar())
            entities["frac14"] = (188.toChar())
            entities["frac12"] = (189.toChar())
            entities["frac34"] = (190.toChar())
            entities["iquest"] = (191.toChar())
            entities["Agrave"] = (192.toChar())
            entities["Aacute"] = (193.toChar())
            entities["Acirc"] = (194.toChar())
            entities["Atilde"] = (195.toChar())
            entities["Auml"] = (196.toChar())
            entities["Aring"] = (197.toChar())
            entities["AElig"] = (198.toChar())
            entities["Ccedil"] = (199.toChar())
            entities["Egrave"] = (200.toChar())
            entities["Eacute"] = (201.toChar())
            entities["Ecirc"] = (202.toChar())
            entities["Euml"] = (203.toChar())
            entities["Igrave"] = (204.toChar())
            entities["Iacute"] = (205.toChar())
            entities["Icirc"] = (206.toChar())
            entities["Iuml"] = (207.toChar())
            entities["ETH"] = (208.toChar())
            entities["Ntilde"] = (209.toChar())
            entities["Ograve"] = (210.toChar())
            entities["Oacute"] = (211.toChar())
            entities["Ocirc"] = (212.toChar())
            entities["Otilde"] = (213.toChar())
            entities["Ouml"] = (214.toChar())
            entities["times"] = (215.toChar())
            entities["Oslash"] = (216.toChar())
            entities["Ugrave"] = (217.toChar())
            entities["Uacute"] = (218.toChar())
            entities["Ucirc"] = (219.toChar())
            entities["Uuml"] = (220.toChar())
            entities["Yacute"] = (221.toChar())
            entities["THORN"] = (222.toChar())
            entities["szlig"] = (223.toChar())
            entities["agrave"] = (224.toChar())
            entities["aacute"] = (225.toChar())
            entities["acirc"] = (226.toChar())
            entities["atilde"] = (227.toChar())
            entities["auml"] = (228.toChar())
            entities["aring"] = (229.toChar())
            entities["aelig"] = (230.toChar())
            entities["ccedil"] = (231.toChar())
            entities["egrave"] = (232.toChar())
            entities["eacute"] = (233.toChar())
            entities["ecirc"] = (234.toChar())
            entities["euml"] = (235.toChar())
            entities["igrave"] = (236.toChar())
            entities["iacute"] = (237.toChar())
            entities["icirc"] = (238.toChar())
            entities["iuml"] = (239.toChar())
            entities["eth"] = (240.toChar())
            entities["ntilde"] = (241.toChar())
            entities["ograve"] = (242.toChar())
            entities["oacute"] = (243.toChar())
            entities["ocirc"] = (244.toChar())
            entities["otilde"] = (245.toChar())
            entities["ouml"] = (246.toChar())
            entities["divide"] = (247.toChar())
            entities["oslash"] = (248.toChar())
            entities["ugrave"] = (249.toChar())
            entities["uacute"] = (250.toChar())
            entities["ucirc"] = (251.toChar())
            entities["uuml"] = (252.toChar())
            entities["yacute"] = (253.toChar())
            entities["thorn"] = (254.toChar())
            entities["yuml"] = (255.toChar())

            // symbols from http://de.selfhtml.org/html/referenz/zeichen.htm

            // greek letters
            entities["Alpha"] = (913.toChar())
            entities["Beta"] = (914.toChar())
            entities["Gamma"] = (915.toChar())
            entities["Delta"] = (916.toChar())
            entities["Epsilon"] = (917.toChar())
            entities["Zeta"] = (918.toChar())
            entities["Eta"] = (919.toChar())
            entities["Theta"] = (920.toChar())
            entities["Iota"] = (921.toChar())
            entities["Kappa"] = (922.toChar())
            entities["Lambda"] = (923.toChar())
            entities["Mu"] = (924.toChar())
            entities["Nu"] = (925.toChar())
            entities["Xi"] = (926.toChar())
            entities["Omicron"] = (927.toChar())
            entities["Pi"] = (928.toChar())
            entities["Rho"] = (929.toChar())
            entities["Sigma"] = (930.toChar())
            entities["Sigmaf"] = (931.toChar())
            entities["Tau"] = (932.toChar())
            entities["Upsilon"] = (933.toChar())
            entities["Phi"] = (934.toChar())
            entities["Chi"] = (935.toChar())
            entities["Psi"] = (936.toChar())
            entities["Omega"] = (937.toChar())

            entities["alpha"] = (945.toChar())
            entities["beta"] = (946.toChar())
            entities["gamma"] = (947.toChar())
            entities["delta"] = (948.toChar())
            entities["epsilon"] = (949.toChar())
            entities["zeta"] = (950.toChar())
            entities["eta"] = (951.toChar())
            entities["theta"] = (952.toChar())
            entities["iota"] = (953.toChar())
            entities["kappa"] = (954.toChar())
            entities["lambda"] = (955.toChar())
            entities["mu"] = (956.toChar())
            entities["nu"] = (957.toChar())
            entities["xi"] = (958.toChar())
            entities["omicron"] = (959.toChar())
            entities["pi"] = (960.toChar())
            entities["rho"] = (961.toChar())
            entities["sigma"] = (962.toChar())
            entities["sigmaf"] = (963.toChar())
            entities["tau"] = (964.toChar())
            entities["upsilon"] = (965.toChar())
            entities["phi"] = (966.toChar())
            entities["chi"] = (967.toChar())
            entities["psi"] = (968.toChar())
            entities["omega"] = (969.toChar())
            entities["thetasym"] = (977.toChar())
            entities["upsih"] = (978.toChar())
            entities["piv"] = (982.toChar())

            // math symbols
            entities["forall"] = (8704.toChar())
            entities["part"] = (8706.toChar())
            entities["exist"] = (8707.toChar())
            entities["empty"] = (8709.toChar())
            entities["nabla"] = (8711.toChar())
            entities["isin"] = (8712.toChar())
            entities["notin"] = (8713.toChar())
            entities["ni"] = (8715.toChar())
            entities["prod"] = (8719.toChar())
            entities["sum"] = (8721.toChar())
            entities["minus"] = (8722.toChar())
            entities["lowast"] = (8727.toChar())
            entities["radic"] = (8730.toChar())
            entities["prop"] = (8733.toChar())
            entities["infin"] = (8734.toChar())
            entities["ang"] = (8736.toChar())
            entities["and"] = (8743.toChar())
            entities["or"] = (8744.toChar())
            entities["cap"] = (8745.toChar())
            entities["cup"] = (8746.toChar())
            entities["int"] = (8747.toChar())
            entities["there4"] = (8756.toChar())
            entities["sim"] = (8764.toChar())
            entities["cong"] = (8773.toChar())
            entities["asymp"] = (8776.toChar())
            entities["ne"] = (8800.toChar())
            entities["equiv"] = (8801.toChar())
            entities["le"] = (8804.toChar())
            entities["ge"] = (8805.toChar())
            entities["sub"] = (8834.toChar())
            entities["sup"] = (8835.toChar())
            entities["nsub"] = (8836.toChar())
            entities["sube"] = (8838.toChar())
            entities["supe"] = (8839.toChar())
            entities["oplus"] = (8853.toChar())
            entities["otimes"] = (8855.toChar())
            entities["perp"] = (8869.toChar())
            entities["sdot"] = (8901.toChar())
            entities["loz"] = (9674.toChar())

            // technical symbols
            entities["lceil"] = (8968.toChar())
            entities["rceil"] = (8969.toChar())
            entities["lfloor"] = (8970.toChar())
            entities["rfloor"] = (8971.toChar())
            entities["lang"] = (9001.toChar())
            entities["rang"] = (9002.toChar())

            // arrow symbols
            entities["larr"] = (8592.toChar())
            entities["uarr"] = (8593.toChar())
            entities["rarr"] = (8594.toChar())
            entities["darr"] = (8595.toChar())
            entities["harr"] = (8596.toChar())
            entities["crarr"] = (8629.toChar())
            entities["lArr"] = (8656.toChar())
            entities["uArr"] = (8657.toChar())
            entities["rArr"] = (8658.toChar())
            entities["dArr"] = (8659.toChar())
            entities["hArr"] = (8960.toChar())

            // divers symbols
            entities["bull"] = (8226.toChar())
            entities["prime"] = (8242.toChar())
            entities["Prime"] = (8243.toChar())
            entities["oline"] = (8254.toChar())
            entities["weierp"] = (8472.toChar())
            entities["image"] = (8465.toChar())
            entities["real"] = (8476.toChar())
            entities["trade"] = (8482.toChar())
            entities["euro"] = (8364.toChar())
            entities["alefsym"] = (8501.toChar())
            entities["spades"] = (9824.toChar())
            entities["clubs"] = (9827.toChar())
            entities["hearts"] = (9829.toChar())
            entities["diams"] = (9830.toChar())

            // ext lat symbols
            entities["OElig"] = (338.toChar())
            entities["oelig"] = (339.toChar())
            entities["Scaron"] = (352.toChar())
            entities["scaron"] = (353.toChar())
            entities["fnof"] = (402.toChar())

            // interpunction
            entities["ensp"] = (8194.toChar())
            entities["emsp"] = (8195.toChar())
            entities["thinsp"] = (8201.toChar())
            entities["zwnj"] = (8204.toChar())
            entities["zwj"] = (8205.toChar())
            entities["lrm"] = (8206.toChar())
            entities["rlm"] = (8207.toChar())

            entities["sbquo"] = (8218.toChar())
            entities["ldquo"] = (8220.toChar())
            entities["rdquo"] = (8221.toChar())
            entities["bdquo"] = (8222.toChar())
            entities["dagger"] = (8224.toChar())
            entities["Dagger"] = (8225.toChar())
            entities["hellip"] = (8230.toChar())
            entities["permil"] = (8240.toChar())
            entities["lsaquo"] = (8249.toChar())
            entities["rsaquo"] = (8250.toChar())

            // diacrit symb
            entities["circ"] = (710.toChar())
            entities["tilde"] = (732.toChar())


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
                } catch (throwable: Throwable) {
                    debug(throwable)
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
