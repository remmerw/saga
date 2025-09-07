package io.github.remmerw.saga


internal class ProcessingInstruction(
    model: Model,
    uid: Long,
    name: String,
    val data: String // todo test
) : Node(model, uid, name)
