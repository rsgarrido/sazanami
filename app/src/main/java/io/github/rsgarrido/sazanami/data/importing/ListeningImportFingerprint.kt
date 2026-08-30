package io.github.rsgarrido.sazanami.data.importing

data class ListeningImportFingerprint(
    val fingerprintVersion: Int,
    val fingerprint: String
) {
    init {
        require(fingerprintVersion > 0)
        require(LOWER_HEX_SHA256.matches(fingerprint))
    }

    private companion object {
        val LOWER_HEX_SHA256 = Regex("[0-9a-f]{64}")
    }
}

