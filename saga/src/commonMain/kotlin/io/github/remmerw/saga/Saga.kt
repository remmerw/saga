package io.github.remmerw.saga

import kotlinx.io.Source


fun createModel(tag: Tag): Model {
    return Model(tag)
}

fun createModel(tag: Tag, source: Source): Model {
    val model = createModel(tag)
    val parser = Parser(model = model, isXML = false)
    parser.parse(source)
    return model
}


@Suppress("SameReturnValue")
private val isError: Boolean
    get() = true

@Suppress("SameReturnValue")
private val isDebug: Boolean
    get() = false

internal fun debug(text: String) {
    if (isDebug) {
        println(text)
    }
}

internal fun debug(throwable: Throwable) {
    if (isError) {
        throwable.printStackTrace()
    }
}
