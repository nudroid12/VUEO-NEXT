package com.vueo.shared.core

enum class VueoClient {
    MOBILE,
    TV,
}

data class CoreHandshake(
    val client: VueoClient,
    val coreName: String,
    val coreApi: Int,
) {
    val message: String
        get() = "${client.name} connected to $coreName API $coreApi"
}

object VueoCore {
    const val NAME = "VUEO Shared Core"
    const val API = 1

    fun handshake(client: VueoClient): CoreHandshake =
        CoreHandshake(
            client = client,
            coreName = NAME,
            coreApi = API,
        )
}
