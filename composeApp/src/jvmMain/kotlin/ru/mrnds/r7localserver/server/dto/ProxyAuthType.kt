package ru.mrnds.r7localserver.server.dto

import kotlinx.serialization.Serializable

@Serializable
enum class ProxyAuthType{
    NONE,
    BASIC,
    NTLM,
    NEGOTIATE
}
