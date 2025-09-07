package io.github.remmerw.saga


internal class Text(model: Model, uid: Long, text: String) :
    CharacterData(model, uid, "#text", text)
