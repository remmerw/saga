package io.github.remmerw.saga

import kotlin.jvm.JvmInline

data class Entity(val uid: Long, val tag: Tag)

@JvmInline
value class Tag(private val name: String) {
    init {
        require(name.onlyLowerLetters()) { "Only lower letters allowed" }
    }

    override fun toString(): String {
        return name
    }
}


@JvmInline
value class Key(private val name: String) {
    init {
        require(name.onlyLowerLetters()) { "Only lower letters allowed" }
    }

    override fun toString(): String {
        return name
    }
}


@JvmInline
value class Value(private val data: String) {
    init {
        require(data.hasLineSeparator()) { "no line separators allowed" }
    }

    override fun toString(): String {
        return data
    }

    fun toLong(): Long {
        return data.toLong()
    }

    fun toInt(): Int {
        return data.toInt()
    }
}

internal fun String.onlyLowerLetters(): Boolean =
    (firstOrNull { !it.isLetter() || !it.isLowerCase() } == null)

fun String.toTag(): Tag = Tag(this)

fun String.toKey(): Key = Key(this)

fun String.toValue(): Value = Value(this)


internal fun String.hasLineSeparator(): Boolean =
    (firstOrNull { it == '\n' } == null)

fun normalizeValue(value: String): Value {
    return value.replace('\n', ' ').toValue()
}