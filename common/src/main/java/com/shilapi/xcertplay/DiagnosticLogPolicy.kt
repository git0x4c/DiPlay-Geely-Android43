package com.shilapi.xcertplay

/** Release keeps state transitions/statistics, not every iAP control packet. */
internal object DiagnosticLogPolicy {
    fun shouldRecord(message: String, release: Boolean): Boolean = !release || !(
        message.startsWith("iAP tunnel iap2 rx=") ||
            message.startsWith("iAP tunnel iap2 tx=") ||
            message.startsWith("iap2 rx=") ||
            message.startsWith("iap2 tx=") ||
            message == "AirPlay iAP tunnel received data" ||
            message.startsWith("AirPlay iAP tunnel package type=comm ")
        )
}
