package io.github.remmerw.saga

internal class Comment(model: Model, uid: Long, text: String) :
    CharacterData(model, uid, "#comment", text)
