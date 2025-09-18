package io.github.remmerw.saga

internal class ElementInfo {
    val endElementType: Int
    val childElementOk: Boolean
    val stopTags: MutableSet<String>?
    val noScriptElement: Boolean
    val decodeEntities: Boolean

    constructor(ok: Boolean, type: Int, decodeEntities: Boolean) {
        this.childElementOk = ok
        this.endElementType = type
        this.stopTags = null
        this.noScriptElement = false
        this.decodeEntities = decodeEntities
    }

}
