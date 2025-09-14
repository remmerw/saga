package io.github.remmerw.saga

import kotlin.jvm.JvmInline


internal val NOT_NORMALIZE = listOf("svg")

internal fun parseClassDeclarations(element: String, declarations: String): List<String> {
    val result = mutableListOf<String>()

    declarations.split(" ").forEach { text ->
        val selector = text.trim()
        if (selector.isNotBlank()) {
            result.add(".$selector")
            result.add("$element.$selector")
        }
    }
    return result
}

internal fun parseCssDeclarations(declarations: String): List<CSSDeclarationWithImportant> =
    buildList {
        val srcList = declarations.split(';')
        srcList.forEach { src ->
            val declaration = src.split(':')
            if (declaration.size == 2) {
                val value = declaration[1].trim()
                val afterRemove = value.removeSuffix("!important")
                add(
                    CSSDeclarationWithImportant(
                        declaration[0].trim(),
                        afterRemove.trim(),
                        value != afterRemove
                    )
                )
            }
        }
    }

internal fun parseCssRuleBlock(origin: StyleOrigin, block: String): List<CSSRuleSet> {
    val removeCommentRegex = "/\\*.*?\\*/".toRegex()
    val setList = block.replace(removeCommentRegex, "").trim().split('}')

    return buildList(setList.size) {
        setList.forEach { set ->
            val list = set.split('{')
            if (list.size == 2) {
                parseCssDeclarations(list[1].trim()).also { declarations ->
                    add(CSSRuleSet(origin, list[0].trim(), declarations))
                }
            }
        }
    }
}

interface CSSDeclaration {
    val property: String
    val value: String
}

internal data class CSSDeclarationWithImportant(
    override val property: String,
    override val value: String,
    val isImportant: Boolean
) : CSSDeclaration

internal data class CSSDeclarationWithPriority(
    override val property: String,
    override val value: String,
    val priority: CSSPriority
) : CSSDeclaration, Comparable<CSSDeclarationWithPriority> {

    constructor(
        src: CSSDeclarationWithImportant,
        origin: StyleOrigin,
        selector: String? = null,
        order: Int = 0
    ) : this(
        src.property,
        src.value,
        CSSPriority.calculate(origin, src.isImportant, selector, order)
    )

    override fun compareTo(other: CSSDeclarationWithPriority): Int =
        priority.compareTo(other.priority)
}


@JvmInline
internal value class CSSPriority(private val priority: Long) {
    operator fun compareTo(other: CSSPriority): Int =
        priority.compareTo(other.priority)


    companion object {
        private val idRegex by lazy { Regex("""#\w+""") }
        private val classRegex by lazy { Regex("""\.\w+""") }
        private val attributeRegex by lazy { Regex("""\[\w+(?:\W*=\W*".+?")?""") }
        private val pseudoClassRegex by lazy { Regex("""(?<!::):\w+""") }
        private val typeRegex by lazy { Regex("""(^|\s|[\[>+~])\w+""") }
        private val pseudoElementRegex by lazy { Regex("""::\w+""") }

        fun calculate(
            origin: StyleOrigin,
            isImportant: Boolean,
            selector: String? = null,
            order: Int = 0
        ): CSSPriority {
            var idCount = 0
            var classCount = 0
            var typeCount = 0

            if (selector != null) {
                idCount = idRegex.findAll(selector).count()

                classCount = classRegex.findAll(selector).count()
                classCount += attributeRegex.findAll(selector).count()
                classCount += pseudoClassRegex.findAll(selector).count()

                typeCount = typeRegex.findAll(selector).count()
                typeCount += pseudoElementRegex.findAll(selector).count()
            }

            return calculate(origin, isImportant, idCount, classCount, typeCount, order)
        }

        private fun calculate(
            origin: StyleOrigin,
            isImportant: Boolean,
            idCount: Int,
            classCount: Int,
            typeCount: Int,
            order: Int
        ): CSSPriority {
            var priority = 0L

            // !important flag
            priority = priority or ((if (isImportant) 1L else 0L) shl 62)

            // StyleOrigin
            priority = priority or (origin.value.toLong() shl 60)

            // Number of ID selectors
            priority = priority or ((idCount.toLong() and 0x3FF) shl 49)

            // Number of class/attribute/pseudo-class selectors
            priority = priority or ((classCount.toLong() and 0x3FF) shl 39)

            // Number of type selectors and pseudo-element selectors
            priority = priority or ((typeCount.toLong() and 0x3FF) shl 29)

            // Source order
            priority = priority or (order.toLong() and 0x1FFFFFFF)

            return CSSPriority(priority)
        }
    }
}


internal data class CSSRuleSet(
    val origin: StyleOrigin,
    val selector: String,
    val declarations: List<CSSDeclarationWithImportant>,
)


internal enum class StyleOrigin(val value: Int) {
    INTERNAL(1),
    INLINE(2)
}

