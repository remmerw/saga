package io.github.remmerw.saga


internal class CDataSection(model: Model, uid: Long, text: String) :
    CharacterData(model, uid, "#cdata-section", text)
