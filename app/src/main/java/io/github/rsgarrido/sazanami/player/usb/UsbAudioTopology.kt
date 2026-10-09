package io.github.rsgarrido.sazanami.player.usb

internal enum class UsbAudioPathDirection { PLAYBACK, CAPTURE }

internal data class UsbAudioPathCandidate(
    val interfaceKey: UsbInterfaceKey,
    val endpointAddress: Int,
    val direction: UsbAudioPathDirection,
    val terminalLink: Int?,
    val audioControlOwners: List<UsbInterfaceKey>,
    val associationEvidence: String,
    val feedbackEvidence: String,
    val signalPaths: List<String>,
    val clockPaths: List<String>,
    val warnings: List<String>
)

/** Builds candidate evidence only. Never chooses an interface, alternate, clock, or endpoint. */
internal object UsbAudioTopology {
    fun candidates(model: UsbAudioDescriptorModel): List<UsbAudioPathCandidate> =
        model.interfaces.filter { it.isAudioStreaming }.flatMap { stream ->
            stream.endpoints.filter { ep -> ep.transferType == 1 && ep.usageType != 1 }.map { ep ->
                describe(model, stream, ep)
            }
        }

    private fun describe(
        model: UsbAudioDescriptorModel,
        stream: UsbInterfaceDescriptor,
        endpoint: UsbEndpointDescriptor
    ): UsbAudioPathCandidate {
        val warnings = mutableListOf<String>()
        if (endpoint.usageType == 3) warnings += "Reserved endpoint usage; candidate interpretation is uncertain."
        val direction = if (endpoint.isIn) UsbAudioPathDirection.CAPTURE else UsbAudioPathDirection.PLAYBACK
        val terminal = stream.audioStreaming?.terminalLink
        val audioControls = model.interfaces.filter {
            it.isAudioControl && it.key.configurationOffset == stream.key.configurationOffset
        }
        val iads = model.associations.filter {
            it.configurationOffset == stream.key.configurationOffset && it.functionClass == 1 &&
                stream.key.number in it.firstInterface until (it.firstInterface + it.interfaceCount)
        }
        val owners: List<UsbInterfaceDescriptor>
        val association: String
        if (iads.size == 1 && stream.key.configurationOffset != null) {
            val iad = iads.single()
            owners = audioControls.filter {
                it.key.number in iad.firstInterface until (iad.firstInterface + iad.interfaceCount)
            }
            association = if (owners.size == 1) "IAD establishes the AudioControl function" else
                "IAD present, but ${owners.size} AudioControl alternates/owners; ambiguous"
        } else {
            owners = audioControls.filter { ac -> ac.audioControl?.entities.orEmpty().any { it.id == terminal } }
            association = "${if (iads.isEmpty()) "No IAD association" else "Overlapping IADs"}; " +
                "${owners.size} possible AudioControl owners by terminal ID, not proven"
            warnings += "AudioControl ownership is ambiguous; matching an entity ID alone is insufficient."
        }
        if (owners.size != 1) warnings += "No unique AudioControl owner established."
        if (terminal == null || terminal == 0) warnings += "AS terminal link absent or invalid."

        val signalPaths = mutableListOf<String>()
        val clockPaths = mutableListOf<String>()
        owners.forEach { owner ->
            val entities = owner.audioControl?.entities.orEmpty()
            val byId = entities.groupBy { it.id }
            val linked = terminal?.let { byId[it] }.orEmpty()
            val ownerName = owner.key.diagnosticName()
            if (linked.size != 1) {
                warnings += "$ownerName: terminal $terminal is missing or duplicated."
            } else {
                val expected = if (direction == UsbAudioPathDirection.PLAYBACK)
                    UsbAudioEntityKind.INPUT_TERMINAL else UsbAudioEntityKind.OUTPUT_TERMINAL
                if (linked.single().kind != expected || linked.single().terminalType != 0x0101) {
                    warnings += "$ownerName: terminal link does not resolve to the expected USB streaming terminal."
                }
                if (direction == UsbAudioPathDirection.PLAYBACK) {
                    entities.filter { it.kind == UsbAudioEntityKind.OUTPUT_TERMINAL }.forEach { output ->
                        walk(byId, output.id, clock = false, stopAt = terminal).forEach { path ->
                            if (path.endsWith("#$terminal")) signalPaths += "$ownerName: $path"
                        }
                    }
                    if (signalPaths.isEmpty()) warnings += "$ownerName: no complete known signal path from streaming terminal to output terminal."
                } else {
                    walk(byId, linked.single().id, clock = false).forEach { signalPaths += "$ownerName: $it" }
                }
                val terminalIds = mutableSetOf(linked.single().id)
                // Include output clocks only when a known signal path reaches this streaming terminal.
                if (direction == UsbAudioPathDirection.PLAYBACK) {
                    entities.filter { it.kind == UsbAudioEntityKind.OUTPUT_TERMINAL }.forEach { output ->
                        if (walk(byId, output.id, clock = false, stopAt = terminal).any { it.endsWith("#$terminal") }) {
                            terminalIds += output.id
                        }
                    }
                }
                terminalIds.forEach { id ->
                    byId[id]?.singleOrNull()?.clockSources.orEmpty().forEach { source ->
                        walk(byId, source, clock = true).forEach {
                            clockPaths += "$ownerName: terminal #$id clock -> $it"
                        }
                    }
                }
            }
        }
        if (clockPaths.isEmpty()) warnings += "Clock path unknown; no clock was selected or queried."
        if (clockPaths.any { "CLOCK_SELECTOR" in it }) warnings += "Selector inputs are possibilities; selected pin was not queried."
        val dataOut = stream.endpoints.filter { it.transferType == 1 && !it.isIn && it.usageType != 1 }
        val explicit = stream.endpoints.filter { it.transferType == 1 && it.isIn && it.usageType == 1 }
        val feedback = when {
            direction == UsbAudioPathDirection.CAPTURE -> "Data IN endpoint; not labeled playback feedback"
            explicit.size == 1 && dataOut.size == 1 &&
                (endpoint.synchronizationAddress == null || endpoint.synchronizationAddress == 0 ||
                    endpoint.synchronizationAddress == explicit.single().address) ->
                "Explicit feedback 0x${usbHex(explicit.single().address)}: ISO IN, feedback usage, same interface/alternate"
            explicit.isNotEmpty() -> "Possible feedback endpoints ${explicit.joinToString { "0x${usbHex(it.address)}" }}; association ambiguous"
            else -> "No explicit feedback endpoint established; implicit feedback not resolved"
        }
        return UsbAudioPathCandidate(stream.key, endpoint.address, direction, terminal,
            owners.map { it.key }, association, feedback, signalPaths.distinct(), clockPaths.distinct(), warnings)
    }

    private fun walk(
        entities: Map<Int, List<UsbAudioEntity>>,
        start: Int,
        clock: Boolean,
        stopAt: Int? = null
    ): List<String> {
        val results = mutableListOf<String>()
        var budget = 256
        fun visit(id: Int, visited: Set<Int>, path: String) {
            if (--budget < 0) {
                if (results.none { it.endsWith("[traversal limit]") }) results += "$path [traversal limit]"
                return
            }
            if (id in visited) { results += "$path -> #$id [cycle]"; return }
            val entity = entities[id]?.singleOrNull()
            if (entity == null) { results += "$path -> #$id [unresolved/duplicate]"; return }
            val clockEntity = entity.kind in setOf(UsbAudioEntityKind.CLOCK_SOURCE,
                UsbAudioEntityKind.CLOCK_SELECTOR, UsbAudioEntityKind.CLOCK_MULTIPLIER)
            if (clock != clockEntity) { results += "$path -> #$id [wrong entity kind]"; return }
            val nextPath = if (path.isEmpty()) "${entity.kind} #$id" else "$path -> ${entity.kind} #$id"
            val sources = if (clock) entity.clockSources else entity.signalSources
            if (id == stopAt || sources.isEmpty()) results += nextPath
            else sources.forEach { visit(it, visited + id, nextPath) }
        }
        visit(start, emptySet(), "")
        return results
    }
}
