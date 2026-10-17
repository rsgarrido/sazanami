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
    val warnings: List<String>,
    val audioControlAssociationProven: Boolean = false
)

/** Descriptive path evidence plus conservative resolution of a read-only Clock Source target.
 * Does not select a streaming alternate, clock pin, or playback endpoint.
 */
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
            owners.map { it.key }, association, feedback, signalPaths.distinct(), clockPaths.distinct(), warnings,
            iads.size == 1 && stream.key.configurationOffset != null && owners.size == 1)
    }

    /** Resolve the conservative supported subset structurally, never by parsing report strings.
     * Every playback candidate must establish the same AC owner and direct Clock Source.
     * Selectors/multipliers require additional evidence and are deliberately not followed here.
     */
    fun resolveClockTarget(model: UsbAudioDescriptorModel): UsbAudioClockResolution {
        val playback = candidates(model).filter { it.direction == UsbAudioPathDirection.PLAYBACK }
        val issues = mutableListOf<String>()
        if (model.warnings.isNotEmpty()) issues += "Descriptor parser warnings must be resolved before automatic clock reads."
        if (model.configurations.size != 1) issues += "Exactly one complete configuration is required; active configuration was not queried."
        if (playback.isEmpty()) issues += "No playback data endpoint candidate."
        val resolved = mutableListOf<Pair<UsbInterfaceDescriptor, UsbAudioEntity>>()
        playback.forEach { path ->
            val name = path.interfaceKey.diagnosticName()
            if (!path.audioControlAssociationProven || path.audioControlOwners.size != 1) {
                issues += "$name: AudioControl ownership ambiguous; ${path.associationEvidence}."
                return@forEach
            }
            val stream = model.interfaces.singleOrNull { it.key == path.interfaceKey }
            val owner = model.interfaces.singleOrNull { it.key == path.audioControlOwners.single() }
            if (stream == null || owner == null || stream.protocol != 0x20 || owner.protocol != 0x20 || owner.audioControl?.version != 0x0200 ||
                owner.key.alternate != 0) {
                issues += "$name: unique UAC2 stream and AudioControl alternate zero/header not established."
                return@forEach
            }
            if (stream.endpoints.any { it.address == path.endpointAddress && it.usageType == 3 }) {
                issues += "$name: reserved endpoint usage cannot establish a playback path."
                return@forEach
            }
            val ac = requireNotNull(owner.audioControl)
            val entities = ac.entities.groupBy { it.id }
            if (entities.any { (id, definitions) -> id !in 1..255 || definitions.size != 1 }) {
                issues += "$name: malformed or duplicated AudioControl entity IDs."
                return@forEach
            }
            val linked = path.terminalLink?.let { entities[it]?.singleOrNull() }
            if (linked == null || linked.kind != UsbAudioEntityKind.INPUT_TERMINAL || linked.terminalType != 0x0101) {
                issues += "$name: terminal link does not identify a USB streaming Input Terminal."
                return@forEach
            }
            val relatedTerminals = mutableListOf(linked)
            fun reaches(id: Int, visited: Set<Int>): Boolean {
                if (id in visited) { issues += "$name: signal topology cycle at entity $id."; return false }
                val entity = entities[id]?.singleOrNull()
                if (entity == null) { issues += "$name: unresolved signal entity $id."; return false }
                if (id == linked.id) return true
                if (entity.kind == UsbAudioEntityKind.INPUT_TERMINAL) return false
                if (entity.kind !in setOf(UsbAudioEntityKind.FEATURE_UNIT, UsbAudioEntityKind.OUTPUT_TERMINAL) ||
                    (entity.kind == UsbAudioEntityKind.OUTPUT_TERMINAL && visited.isNotEmpty()) ||
                    entity.signalSources.size != 1) {
                    issues += "$name: unsupported/broken signal topology at entity $id."
                    return false
                }
                return reaches(entity.signalSources.single(), visited + id)
            }
            ac.entities.filter { it.kind == UsbAudioEntityKind.OUTPUT_TERMINAL }.forEach { terminal ->
                if (reaches(terminal.id, emptySet())) relatedTerminals += terminal
            }
            if (relatedTerminals.size < 2) issues += "$name: no complete known signal path to an Output Terminal."
            relatedTerminals.forEach { terminal ->
                val source = terminal.clockSources.singleOrNull()?.let { entities[it]?.singleOrNull() }
                when {
                    source == null -> issues += "$name: terminal ${terminal.id} has a broken/non-unique clock reference."
                    source.kind != UsbAudioEntityKind.CLOCK_SOURCE -> issues +=
                        "$name: terminal ${terminal.id} uses ${source.kind}; selector/multiplier topology is not resolved by these reads."
                    source.controls == null -> issues += "$name: clock ${source.id} control bitmap unknown."
                    else -> resolved += owner to source
                }
            }
        }
        val targets = resolved.distinctBy { (owner, source) -> owner.key to source.id }
        if (targets.size != 1) issues += "Playback paths resolve to ${targets.size} distinct AC/Clock Source targets; no target chosen."
        val target = if (issues.isEmpty()) targets.single().let { (owner, source) ->
            UsbAudioClockTarget(owner.key, source.id, requireNotNull(source.controls), playback.map { it.interfaceKey }.distinct())
        } else null
        return UsbAudioClockResolution(target, playback.size, issues.distinct())
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
