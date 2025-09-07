package io.github.remmerw.saga

internal class ElementInfo {
    val endElementType: Int
    val childElementOk: Boolean
    val stopTags: MutableSet<String>?
    val noScriptElement: Boolean
    val decodeEntities: Boolean

    /**
     * @param ok
     * @param type
     */
    constructor(ok: Boolean, type: Int) {
        this.childElementOk = ok
        this.endElementType = type
        this.stopTags = null
        this.noScriptElement = false
        this.decodeEntities = true
    }

    /**
     * @param ok
     * @param type
     */
    constructor(ok: Boolean, type: Int, stopTags: MutableSet<String>?) {
        this.childElementOk = ok
        this.endElementType = type
        this.stopTags = stopTags
        this.noScriptElement = false
        this.decodeEntities = true
    }

    constructor(ok: Boolean, type: Int, stopTags: MutableSet<String>?, noScriptElement: Boolean) {
        this.childElementOk = ok
        this.endElementType = type
        this.stopTags = stopTags
        this.noScriptElement = noScriptElement
        this.decodeEntities = true
    }

    constructor(ok: Boolean, type: Int, decodeEntities: Boolean) {
        this.childElementOk = ok
        this.endElementType = type
        this.stopTags = null
        this.noScriptElement = false
        this.decodeEntities = decodeEntities
    }

    companion object {
        const val END_ELEMENT_FORBIDDEN: Int = 0
        const val END_ELEMENT_OPTIONAL: Int = 1
        const val END_ELEMENT_REQUIRED: Int = 2
    }
}
