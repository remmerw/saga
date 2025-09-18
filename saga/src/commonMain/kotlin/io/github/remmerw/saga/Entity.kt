package io.github.remmerw.saga

import kotlin.jvm.JvmInline

data class Entity(val uid: Long, val tag: Tag)

@JvmInline
value class Tag(val name: String) {
    init {
        require(name.onlyLowerLetters()) { "Only lower letters allowed" }
    }
}


@JvmInline
value class Key(val name: String) {
    init {
        require(name.onlyLowerLetters()) { "Only lower letters allowed" }
    }
}


@JvmInline
value class Value(val data: String) {
    init {
        require(data.hasWhitespaces()) { "no whitespaces allowed" }
    }
}

fun String.onlyLowerLetters(): Boolean =
    (firstOrNull { !it.isLetter() || !it.isLowerCase() } == null)

fun String.toTag(): Tag = Tag(this)

fun String.toKey(): Key = Key(this)

fun String.toValue(): Value = Value(this)


fun String.hasWhitespaces(): Boolean =
    (firstOrNull { it.isWhitespace() } == null)