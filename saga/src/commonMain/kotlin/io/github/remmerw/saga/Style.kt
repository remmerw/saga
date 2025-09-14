package io.github.remmerw.saga

internal class Style {
    private val rules = mutableMapOf<String, MutableList<CSSRuleSet>>()

    fun parseStyle(data: String) {
        if (data.isNotEmpty()) {
            parseCssRuleBlock(StyleOrigin.INTERNAL, data).forEach { rule ->
                rules.getOrPut(rule.selector) { mutableListOf() }.add(rule)
            }
        }
    }


    fun normalize(element: Element) {
        if (!NOT_NORMALIZE.contains(element.name)) {
            val cssDeclarations = buildFinalCSS(element)
            val properties = mutableMapOf<String, String>()
            cssDeclarations.forEach { declaration ->
                properties.put(declaration.property, declaration.value)
            }
            // remove class and style attribute
            element.changeAttributes(listOf("class", "style"), properties)
        }
    }

    private fun buildFinalCSS(element: Element): List<CSSDeclaration> {

        val name = element.name

        val inlineCss: MutableList<CSSDeclarationWithImportant> = mutableListOf()
        val styleAttribute = element.getAttribute("style")
        if (styleAttribute != null && styleAttribute.isNotBlank()) {
            val declarations = parseCssDeclarations(styleAttribute)
            inlineCss.addAll(declarations)
        }

        val noInlineCSS = mutableListOf<CSSRuleSet>()

        rules[name]?.let { ruleSets -> noInlineCSS.addAll(ruleSets) }

        val classAttribute = element.getAttribute("class")
        if (classAttribute != null && classAttribute.isNotBlank()) {
            val selectors = parseClassDeclarations(name, classAttribute)
            selectors.forEach { name ->
                rules[name]?.let { ruleSets -> noInlineCSS.addAll(ruleSets) }
            }
        }

        val finalCssMap = mutableMapOf<String, CSSDeclarationWithPriority>()

        fun CSSDeclarationWithPriority.compareAndPutMap() {
            val mapValue = finalCssMap[property]
            if (mapValue == null || this >= mapValue) {
                finalCssMap[property] = this
            }
        }

        inlineCss.forEach { declaration ->
            CSSDeclarationWithPriority(declaration, StyleOrigin.INLINE).compareAndPutMap()
        }
        noInlineCSS.mapIndexed { index, ruleSet ->
            ruleSet.declarations.forEach { declaration ->
                CSSDeclarationWithPriority(
                    declaration,
                    ruleSet.origin,
                    ruleSet.selector,
                    index
                ).compareAndPutMap()
            }
        }
        return finalCssMap.values.toList()
    }
}