package io.github.remmerw.saga

internal class Style {
    private val rules = mutableMapOf<String, MutableList<CSSRuleSet>>()

    fun rules(): Map<String, List<CSSRuleSet>> {
        return rules
    }

    fun parseStyle(data: String) {
        if (data.isNotEmpty()) {
            parseCssRuleBlock(StyleOrigin.INTERNAL, data).forEach { rule ->
                rules.getOrPut(rule.selector) { mutableListOf() }.add(rule)
            }
        }
    }


    fun handleNode(model: Model, entity: Entity) {
        val node = model.node(entity)
        if (node is Element) {
            if (!NOT_NORMALIZE.contains(node.name)) {
                val cssDeclarations = buildFinalCSS(node)
                val properties = mutableMapOf<String, String>()
                cssDeclarations.forEach { declaration ->
                    properties.put(declaration.property, declaration.value)
                }
                // remove class and style attribute
                node.changeAttributes(listOf("class", "style"), properties)

                node.getChildren().forEach { entity ->
                    handleNode(model, entity)
                }
            }
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