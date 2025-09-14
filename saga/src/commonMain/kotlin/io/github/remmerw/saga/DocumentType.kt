package io.github.remmerw.saga


class DocumentType(
    uid: Long,
    val qualifiedName: String,
    val publicId: String?,
    val systemId: String?
) : Node(uid, DOCTYPE_NODE)
