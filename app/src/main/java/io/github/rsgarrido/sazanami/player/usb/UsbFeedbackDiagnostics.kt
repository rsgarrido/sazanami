package io.github.rsgarrido.sazanami.player.usb

import java.util.Locale

/** Immutable terminal evidence only. Units are host monotonic microseconds, never DAC sample age. */
internal data class UsbFeedbackDiagnosticEvidence(val fatal: List<Long>,val history: List<List<Long>>) {
    val exists get()=fatal[0]==1L
}
internal object UsbFeedbackDiagnostics {
    const val HISTORICAL_FIELDS=236
    const val CAPACITY=16
    const val ROW_WORDS=15
    const val FATAL_WORDS=32
    const val WORDS=272
    val fatalFields=listOf("exists","reason","sequence","firstRejectedSequence","raw","validity","streak",
        "nominalRateHz","phase","transportState","sourceEpoch","confirmedSeekSerial","sinceSeekUs","sinceOutUs",
        "observedElapsedUs","requestAgeUs","outOutstanding","feedbackOutstanding","bootstrapActive","refillDemand",
        "nominalCoverageUs","estimatedCoverageUs","bootstrapElapsedUs","admissionStoppedElapsedUs","poolsDrainedElapsedUs",
        "seekConfirmedElapsedUs","freshPrimeElapsedUs","outResumedElapsedUs","historyCount","firstRejectedElapsedUs","heldAcceptedRaw","timeoutKind")
    val rowFields=listOf("sequence","elapsedUs","raw","validity","rejection","requestedLength","actualLength","urbStatus",
        "packetStatus","accepted","requestAgeUs","phase","sourceEpoch","confirmedSeekSerial","urbErrorCount")
    val fields=fatalFields.map {"feedbackFatal_$it"}+(0 until CAPACITY).flatMap {row->rowFields.map {"feedbackHistory${row}_$it"}}
    val causes=listOf("NONE","PERSISTENT_INVALID_FEEDBACK","MALFORMED_FEEDBACK","QUIET_COMPLETION_ERROR",
        "BOOTSTRAP_TIMEOUT","LAST_VALID_WATCHDOG_TIMEOUT","REAPED_OBSERVATION_TIMEOUT","FEEDBACK_URB_COMPLETION_ERROR")
    val rejections=listOf("NONE","LENGTH","ZERO","RESERVED_BITS","IMPLAUSIBLE_RATE","URB_ERROR","PACKET_ERROR","QUIET_NOT_ACCEPTED","TERMINAL_NOT_ACCEPTED")
    private fun int32(value: Long)=value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
    private fun uint32(value: Long)=value in 0L..0xffffffffL
    private fun flag(value: Long)=value in 0L..1L
    private fun time(value: Long)=value>=-1L
    private fun validity(raw: Long,rate: Long): Int=when {
        raw==0L->UsbFeedbackValidity.ZERO.ordinal
        raw and 0xf0000000L != 0L->UsbFeedbackValidity.RESERVED_BITS.ordinal
        raw*8000L*100L in rate*65536L*(100L-UsbExplicitFeedback.PLAUSIBILITY_PERCENT)..
            rate*65536L*(100L+UsbExplicitFeedback.PLAUSIBILITY_PERCENT)->UsbFeedbackValidity.VALID.ordinal
        else->UsbFeedbackValidity.IMPLAUSIBLE.ordinal
    }
    fun decode(words: LongArray,nominalRate: Long): UsbFeedbackDiagnosticEvidence {
        require(words.size==WORDS)
        val f=words.take(FATAL_WORDS)
        require(flag(f[0]) && f[28] in 0L..CAPACITY.toLong())
        val count=f[28].toInt()
        val rows=(0 until count).map {i->words.slice(FATAL_WORDS+i*ROW_WORDS until FATAL_WORDS+(i+1)*ROW_WORDS)}
        require(words.drop(FATAL_WORDS+count*ROW_WORDS).all {it==0L})
        if(count>0 || f[0]==1L) require(UsbDirectPcmFormat.supportsRate(nominalRate))
        rows.forEachIndexed {index,r->
            require(r[0]>0L && time(r[1]) && r[2] in -1L..0xffffffffL && r[3] in 0L..6L && r[4] in 0L..8L &&
                uint32(r[5]) && uint32(r[6]) && int32(r[7]) && int32(r[8]) && flag(r[9]) && time(r[10]) &&
                r[11] in -1L..11L && r[12]>=-1L && r[13]>=-1L && int32(r[14]) && r[14]>=0L)
            require((r[6]==4L)==(r[2]>=0L))
            val expected=when {
                r[7]!=0L || r[14]>0L->5
                r[8]!=0L->6
                r[6]!=4L->1
                else->when(validity(r[2],nominalRate)) {0->if(r[4] in 7L..8L) r[4].toInt() else 0;2->2;3->3;else->4}
            }
            require(r[4]==expected.toLong())
            val classification=when {expected in 5..6->5;expected==1->1;else->validity(r[2],nominalRate)}
            require(r[3]==classification.toLong() && (r[9]==1L)==(expected==0))
            if(r[4]==7L) require(r[11] in listOf(2L,3L,9L,10L,11L))
            if(r[1]>=0L && r[10]>=0L) require(r[10]<=r[1])
            require((r[11]==-1L)==(r[12]==-1L) && (r[11]==-1L)==(r[13]==-1L))
            if(index>0) {val p=rows[index-1];require(r[0]>p[0] && r[0]-p[0]==1L)
                if(r[1]>=0L && p[1]>=0L) require(r[1]>=p[1])}
        }
        if(f[0]!=1L) {
            require(f.indices.all {it==28 || f[it]==0L})
        } else {
            require(f[1] in 1L..7L && f[2]>=-1L && f[3]>=-1L && f[4] in -1L..0xffffffffL && f[5] in -1L..6L &&
                f[6] in 0L..Int.MAX_VALUE.toLong() && f[7]==nominalRate && f[8] in -1L..11L && f[9] in 0L..7L &&
                f[10]>=-1L && f[11]>=-1L && flag(f[18]) && flag(f[19]) && f[20]>0L && f[21] in 0L..f[20] &&
                uint32(f[30]) && f[31] in 0L..3L && f[16] in 0L..161L && f[17] in 0L..161L)
            for(i in listOf(12,13,14,15,22,23,24,25,26,27,29)) require(time(f[i]))
            require((f[8]==-1L)==(f[10]==-1L) && (f[8]==-1L)==(f[11]==-1L))
            for(i in listOf(15,22,23,24,25,26,27,29)) if(f[14]>=0L && f[i]>=0L) require(f[i]<=f[14])
            for((duration,stamp) in listOf(12 to 25,13 to 27)) {
                require((f[duration]==-1L)==(f[stamp]==-1L))
                if(f[duration]>=0L && f[14]>=0L) require(f[duration]==f[14]-f[stamp])
            }
            if(f[25]>=0L) require(f[11]>0L)
            if(f[1] in 4L..6L) {
                require(f[2]==-1L && f[3]==-1L && f[4]==-1L && f[5]==-1L && f[15]==-1L && f[29]==-1L && f[31]==f[1]-3L)
            } else {
                require(rows.isNotEmpty() && f[31]==0L)
                val last=rows.last()
                require(f[2]==last[0] && f[4]==last[2] && f[5]==last[3] && f[15]==last[10] && last[9]==0L &&
                    f[8]==last[11] && f[10]==last[12] && f[11]==last[13])
                require(f[14]==last[1])
                if(f[1]==1L) {
                    require(f[6]==3L)
                    val first=rows.firstOrNull {it[0]==f[3]};require(first!=null && first[1]==f[29])
                    val streak=rows.filter {it[0]>=f[3]}
                    require(streak.size==3 && streak.all {it[4] in listOf(2L,4L) && it[9]==0L})
                } else {
                    require(f[3]==-1L && f[29]==-1L)
                    require(when(f[1]) {2L->last[4] in listOf(1L,3L,6L);3L->last[4] in listOf(1L,5L,6L);7L->last[4]==5L;else->false})
                    if(f[1]==3L) require(f[8] in listOf(2L,3L,9L,10L,11L))
                }
            }
        }
        return UsbFeedbackDiagnosticEvidence(f,rows)
    }
}

internal fun formatFeedbackDiagnostics(evidence: UsbFeedbackDiagnosticEvidence?,locale: Locale): String=buildString {
    val es=locale.language=="es"
    fun label(en: String,sp: String)=if(es) sp else en
    fun value(n: Long)=if(n<0L) label("unavailable","no disponible") else n.toString()
    fun rate(raw: Long,classification: Long)=if(raw<0L || classification !in listOf(0L,2L,4L)) value(-1)
        else String.format(Locale.ROOT,"%.5f",raw*8000.0/65536.0)
    val title=label("First fatal feedback decision","Primera decisión fatal de feedback")
    if(evidence==null) {appendLine("$title: ${label("UNAVAILABLE — no validated v13 snapshot","NO DISPONIBLE — sin captura v13 validada")}");return@buildString}
    if(!evidence.exists) {appendLine("$title: ${label("NONE (validated v13; no error timeline)","NINGUNA (v13 validada; sin cronología de error)")}");return@buildString}
    val f=evidence.fatal
    val phase=if(f[8]>=0L) UsbFullTransportPhase.entries[f[8].toInt()].name else UsbIsoNativeStats.states[f[9].toInt()]
    appendLine("$title: ${UsbFeedbackDiagnostics.causes[f[1].toInt()]}; seq=${value(f[2])}; firstRejectedSeq=${value(f[3])}; ${label("streak at decision","racha al decidir")}=${f[6]}")
    appendLine("${label("raw / decoded Hz (interpretation, not acceptance)","raw / Hz decodificados (interpretación, no aceptación)")}=${value(f[4])} / ${rate(f[4],f[5])}; validity=${if(f[5]>=0) UsbFeedbackValidity.entries[f[5].toInt()] else value(-1)}; selectedHz=${f[7]}; windowHz=${f[7]*0.95}..${f[7]*1.05}")
    appendLine("${label("lifecycle / epoch / confirmed seek serial","fase / época / serial de seek confirmado")}=$phase / ${value(f[10])} / ${value(f[11])}; bootstrapActive=${f[18]}; workerRefillDemand=${f[19]}; heldAcceptedRaw=${f[30]}")
    appendLine("${label("Host times us: observation / since seek / since OUT / request residence","Tiempos del host us: observación / desde seek / desde OUT / residencia de solicitud")}=${value(f[14])} / ${value(f[12])} / ${value(f[13])} / ${value(f[15])}; firstRejectedElapsedUs=${value(f[29])}; bootstrapElapsedUs=${value(f[22])}")
    appendLine("OUT=${f[16]}; feedback=${f[17]}; ${label("nominal / estimated coverage us","cobertura nominal / estimada us")}=${f[20]} / ${f[21]}")
    appendLine("${label("Latest transition elapsed us: admission stopped / pools drained / seek confirmed / fresh prime / first source OUT submission","Últimas transiciones us: admisión detenida / pools drenados / seek confirmado / cebado nuevo / primera solicitud OUT con PCM")}=${(23..27).joinToString(" / ") {value(f[it])}}")
    appendLine(label("Pre-fatal feedback history (frozen; chronological; maximum 16):","Historial de feedback previo al fallo (congelado; cronológico; máximo 16):"))
    appendLine("seq | elapsedUs | raw | decodedHz | validity/reason | accepted | req/actual | urb/packet/errors | requestAgeUs | phase/epoch/seek")
    evidence.history.forEach {r->appendLine("${r[0]} | ${value(r[1])} | ${value(r[2])} | ${rate(r[2],r[3])} | ${UsbFeedbackValidity.entries[r[3].toInt()]}/${UsbFeedbackDiagnostics.rejections[r[4].toInt()]} | ${r[9]} | ${r[5]}/${r[6]} | ${r[7]}/${r[8]}/${r[14]} | ${value(r[10])} | ${value(r[11])}/${value(r[12])}/${value(r[13])}")}
    appendLine(label("Times measure host observations/request residence, not DAC sample age. OUT time is submission evidence, not wire service. Rejected/shutdown values are not accepted scheduling authority; worker refill demand may be blocked by pending control. Device-internal reconstruction remains unknown.",
        "Los tiempos miden observaciones/residencia en el host, no edad de muestras del DAC. OUT indica solicitud, no servicio en el cable. Valores rechazados/durante cierre no son autoridad aceptada; un control pendiente puede bloquear la admisión. La reconstrucción interna del DAC sigue desconocida."))
}
