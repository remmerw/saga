package io.github.remmerw.saga


internal class CDataSection(uid: Long, text: String) :
    CharacterData(uid, "#cdata-section", text)
