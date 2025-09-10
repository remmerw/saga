package io.github.remmerw.saga

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal abstract class CharacterData(uid: Long, name: String, text: String) : Node(uid, name) {

    private val _data = MutableStateFlow(text)
    val data = _data.asStateFlow()

    fun getData(): String {
        return data.value
    }

    override fun toString(): String {
        var someText = this.getData()
        if (someText.length > 32) {
            someText = someText.substring(0, 29) + "..."
        }
        val length = someText.length
        return this.name + "[length=" + length + ",text=" + someText + "]"
    }

}
