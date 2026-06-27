package com.example.data

/**
 * Shared mic session flag — STT loops read this to stop when mic is closed.
 */
object MicSessionController {
    @Volatile
    var isMicActive: Boolean = false
}
