#!/system/bin/sh

set -u

SCENARIO="${1:?scenario is required}"
DURATION_SECONDS="${2:-600}"
INTERVAL_SECONDS="${3:-60}"
MANUAL_START_TIMEOUT_SECONDS="${4:-900}"
OUTPUT_DIRECTORY="/sdcard/Download/Sazanami-Phase-F-Thermal"
PACKAGE_NAME="com.example.cdplaya"
MAX_START_AP_C="37.5"
MAX_START_SKIN_C="35.0"
REQUIRED_STABLE_SAMPLES=1

case "$SCENARIO" in
    eq-disabled|parametric-ten-filter|parametric-ten-filter-limiter)
        ;;
    *)
        echo "Unsupported scenario: $SCENARIO" >&2
        exit 2
        ;;
esac

mkdir -p "$OUTPUT_DIRECTORY"
RUN_ID="$(date +%Y%m%d-%H%M%S)"
CSV_PATH="$OUTPUT_DIRECTORY/$RUN_ID-$SCENARIO.csv"
LOG_PATH="$OUTPUT_DIRECTORY/$RUN_ID-$SCENARIO.log"
DONE_PATH="$OUTPUT_DIRECTORY/$RUN_ID-$SCENARIO.done"
FAILED_PATH="$OUTPUT_DIRECTORY/$RUN_ID-$SCENARIO.failed"
TEMP_PREFIX="/data/local/tmp/cdplaya-phase-f-$RUN_ID-$$"
BATTERY_TEMP_PATH="$TEMP_PREFIX-battery.txt"
THERMAL_TEMP_PATH="$TEMP_PREFIX-thermal.txt"
MEMORY_TEMP_PATH="$TEMP_PREFIX-memory.txt"
CAPTURE_CONTROLLED=false
PROCESSOR_COUNT="$(grep -c '^processor' /proc/cpuinfo)"
PREVIOUS_PROCESS_TICKS=""
PREVIOUS_SYSTEM_TICKS=""

field_value() {
    source_path="$1"
    label="$2"
    sed -n "s/^[[:space:]]*$label:[[:space:]]*//p" "$source_path" |
        head -n 1
}

temperature_value() {
    source_path="$1"
    name="$2"
    sed -n \
            "s/.*Temperature{mValue=\([-0-9.]*\),.*mName=$name,.*/\1/p" \
            "$source_path" |
        head -n 1
}

less_than_or_equal() {
    awk -v observed="$1" -v maximum="$2" \
        'BEGIN { exit !(observed <= maximum) }'
}

screen_is_awake() {
    dumpsys power | grep 'mWakefulness=Awake' >/dev/null
}

playback_state() {
    dumpsys media_session |
        awk -v package_name="$PACKAGE_NAME" '
            $0 ~ "androidx.media3.session.id\\. " package_name "/" {
                in_cdplaya_session = 1
                next
            }
            in_cdplaya_session && !reported && /state=PlaybackState/ {
                if ($0 ~ /state=PLAYING/) {
                    print "PLAYING"
                } else if ($0 ~ /state=PAUSED/) {
                    print "PAUSED"
                } else {
                    print "OTHER"
                }
                reported = 1
            }
        '
}

cleanup() {
    if [ "$CAPTURE_CONTROLLED" = "true" ]; then
        cmd media_session dispatch pause >/dev/null 2>&1 || true
        cmd power wakeup >/dev/null 2>&1 || true
    fi
    rm -f \
        "$BATTERY_TEMP_PATH" \
        "$THERMAL_TEMP_PATH" \
        "$MEMORY_TEMP_PATH"
}

fail_capture() {
    failure_message="$1"
    failure_code="$2"
    echo "$failure_message" >> "$LOG_PATH"
    {
        echo "failed=$(date -Iseconds)"
        echo "reason=$failure_message"
        echo "csv=$CSV_PATH"
        echo "log=$LOG_PATH"
    } > "$FAILED_PATH"
    exit "$failure_code"
}

write_sample() {
    sample_index="$1"
    phase="$2"
    elapsed_seconds="$3"

    dumpsys battery > "$BATTERY_TEMP_PATH"
    dumpsys thermalservice > "$THERMAL_TEMP_PATH"
    dumpsys meminfo "$PACKAGE_NAME" > "$MEMORY_TEMP_PATH"

    ac_powered="$(field_value "$BATTERY_TEMP_PATH" 'AC powered')"
    usb_powered="$(field_value "$BATTERY_TEMP_PATH" 'USB powered')"
    wireless_powered="$(field_value "$BATTERY_TEMP_PATH" 'Wireless powered')"
    battery_status="$(field_value "$BATTERY_TEMP_PATH" 'status')"
    battery_level="$(field_value "$BATTERY_TEMP_PATH" 'level')"
    battery_temperature_raw="$(
        field_value "$BATTERY_TEMP_PATH" 'temperature'
    )"
    battery_temperature_c="$(awk \
        -v value="$battery_temperature_raw" \
        'BEGIN { printf "%.1f", value / 10.0 }')"
    battery_voltage_mv="$(field_value "$BATTERY_TEMP_PATH" 'voltage')"
    battery_current_raw="$(field_value "$BATTERY_TEMP_PATH" 'current now')"
    thermal_status="$(field_value "$THERMAL_TEMP_PATH" 'Thermal Status')"
    ap_temperature_c="$(temperature_value "$THERMAL_TEMP_PATH" 'AP')"
    skin_temperature_c="$(temperature_value "$THERMAL_TEMP_PATH" 'SKIN')"
    thermal_battery_temperature_c="$(
        temperature_value "$THERMAL_TEMP_PATH" 'BAT'
    )"
    memory_totals="$(
        sed -n \
                's/.*TOTAL PSS:[[:space:]]*\([0-9]*\).*TOTAL RSS:[[:space:]]*\([0-9]*\).*TOTAL SWAP PSS:[[:space:]]*\([0-9]*\).*/\1,\2,\3/p' \
                "$MEMORY_TEMP_PATH" |
            head -n 1
    )"
    pss_kib="$(printf '%s' "$memory_totals" | cut -d, -f1)"
    rss_kib="$(printf '%s' "$memory_totals" | cut -d, -f2)"
    swap_pss_kib="$(printf '%s' "$memory_totals" | cut -d, -f3)"
    process_pid="$(pidof "$PACKAGE_NAME" | awk '{ print $1 }')"
    process_ticks="$(
        awk '{ print $14 + $15 }' "/proc/$process_pid/stat"
    )"
    system_ticks="$(
        awk '
            /^cpu / {
                total = 0
                for (field = 2; field <= NF; field++) {
                    total += $field
                }
                print total
                exit
            }
        ' /proc/stat
    )"
    cpu_percent=""
    total_capacity_percent=""
    if [ -n "$PREVIOUS_PROCESS_TICKS" ] &&
        [ -n "$PREVIOUS_SYSTEM_TICKS" ]; then
        cpu_values="$(
            awk \
                -v process_now="$process_ticks" \
                -v process_before="$PREVIOUS_PROCESS_TICKS" \
                -v system_now="$system_ticks" \
                -v system_before="$PREVIOUS_SYSTEM_TICKS" \
                -v processor_count="$PROCESSOR_COUNT" '
                    BEGIN {
                        process_delta = process_now - process_before
                        system_delta = system_now - system_before
                        if (system_delta <= 0) {
                            print ","
                            exit
                        }
                        total_capacity = process_delta / system_delta * 100
                        one_core_equivalent = total_capacity * processor_count
                        printf "%.3f,%.3f", \
                            one_core_equivalent, total_capacity
                    }
                '
        )"
        cpu_percent="$(printf '%s' "$cpu_values" | cut -d, -f1)"
        total_capacity_percent="$(
            printf '%s' "$cpu_values" | cut -d, -f2
        )"
    fi
    PREVIOUS_PROCESS_TICKS="$process_ticks"
    PREVIOUS_SYSTEM_TICKS="$system_ticks"
    cpu_line="procTicks=$process_ticks systemTicks=$system_ticks totalCapacityPct=$total_capacity_percent oneCoreEquivalentPct=$cpu_percent processors=$PROCESSOR_COUNT"
    raw_underruns="external-before-after-snapshot"
    track_underruns="external-before-after-snapshot"
    media_state="$(playback_state)"
    timestamp="$(date -Iseconds)"

    if [ "$ac_powered" != "false" ] ||
        [ "$usb_powered" != "false" ] ||
        [ "$wireless_powered" != "false" ]; then
        fail_capture \
            "A charging source became active during cooldown or measured phase" \
            3
    fi

    printf '%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,"%s","%s","%s"\n' \
        "$SCENARIO" \
        "$phase" \
        "$sample_index" \
        "$timestamp" \
        "$elapsed_seconds" \
        "$ac_powered" \
        "$usb_powered" \
        "$wireless_powered" \
        "$battery_status" \
        "$battery_level" \
        "$battery_temperature_c" \
        "$battery_voltage_mv" \
        "$battery_current_raw" \
        "$thermal_status" \
        "$ap_temperature_c" \
        "$skin_temperature_c" \
        "$thermal_battery_temperature_c" \
        "$pss_kib,$rss_kib,$swap_pss_kib" \
        "$cpu_percent;$cpu_line" \
        "$media_state;$raw_underruns;$track_underruns" \
        >> "$CSV_PATH"

    {
        echo "sample=$sample_index phase=$phase timestamp=$timestamp"
        echo "battery=$battery_level% batteryTemp=${battery_temperature_c}C ac=$ac_powered usb=$usb_powered wireless=$wireless_powered"
        echo "thermalStatus=$thermal_status AP=${ap_temperature_c}C SKIN=${skin_temperature_c}C BAT=${thermal_battery_temperature_c}C"
        echo "PSS=${pss_kib}KiB RSS=${rss_kib}KiB swapPSS=${swap_pss_kib}KiB"
        echo "CPU=$cpu_line"
        echo "playback=$media_state"
        echo "rawUnderruns=$raw_underruns"
        echo "trackUnderruns=$track_underruns"
        echo
    } >> "$LOG_PATH"
}

trap cleanup EXIT HUP INT TERM

cat > "$LOG_PATH" <<EOF
Sazanami Phase F detached device thermal monitor
scenario=$SCENARIO
started=$(date -Iseconds)
durationSeconds=$DURATION_SECONDS
intervalSeconds=$INTERVAL_SECONDS
manualStartTimeoutSeconds=$MANUAL_START_TIMEOUT_SECONDS
maximumStartApC=$MAX_START_AP_C
maximumStartSkinC=$MAX_START_SKIN_C
requiredStableCooldownSamples=$REQUIRED_STABLE_SAMPLES
audioUnderruns=collected before and after the measured window outside this monitor
battery observations are informal and are not efficiency measurements
EOF

echo 'scenario,phase,sample_index,timestamp_local,elapsed_seconds,ac_powered,usb_powered,wireless_powered,battery_status,battery_level_percent,battery_temperature_c,battery_voltage_mv,battery_current_raw,thermal_status,ap_temperature_c,skin_temperature_c,thermal_battery_temperature_c,"pss_kib,rss_kib,swap_pss_kib","process_cpu_percent;observation","playback;raw_mixer_underruns;audio_track_underruns"' \
    > "$CSV_PATH"

while true; do
    dumpsys battery > "$BATTERY_TEMP_PATH"
    if ! grep -Eq '(AC|USB|Wireless) powered: true' \
        "$BATTERY_TEMP_PATH"; then
        break
    fi
    sleep 2
done

stable_samples=0
cooldown_sample=0
while [ "$stable_samples" -lt "$REQUIRED_STABLE_SAMPLES" ]; do
    dumpsys battery > "$BATTERY_TEMP_PATH"
    dumpsys thermalservice > "$THERMAL_TEMP_PATH"
    ap_temperature_c="$(temperature_value "$THERMAL_TEMP_PATH" 'AP')"
    skin_temperature_c="$(temperature_value "$THERMAL_TEMP_PATH" 'SKIN')"
    ac_powered="$(field_value "$BATTERY_TEMP_PATH" 'AC powered')"
    usb_powered="$(field_value "$BATTERY_TEMP_PATH" 'USB powered')"
    wireless_powered="$(field_value "$BATTERY_TEMP_PATH" 'Wireless powered')"

    if [ "$ac_powered" != "false" ] ||
        [ "$usb_powered" != "false" ] ||
        [ "$wireless_powered" != "false" ]; then
        stable_samples=0
    elif less_than_or_equal "$ap_temperature_c" "$MAX_START_AP_C" &&
        less_than_or_equal "$skin_temperature_c" "$MAX_START_SKIN_C"; then
        stable_samples=$((stable_samples + 1))
    else
        stable_samples=0
    fi

    write_sample "$cooldown_sample" "cooldown" "0"
    cooldown_sample=$((cooldown_sample + 1))
    if [ "$stable_samples" -lt "$REQUIRED_STABLE_SAMPLES" ]; then
        sleep "$INTERVAL_SECONDS"
    fi
done

{
    echo "cooldownCompleted=$(date -Iseconds)"
    echo "waitingForManualStart=true"
    echo "requiredStartState=Sazanami PLAYING and screen Asleep"
} >> "$LOG_PATH"

manual_wait_started_epoch="$(date +%s)"
while true; do
    dumpsys battery > "$BATTERY_TEMP_PATH"
    if grep -Eq '(AC|USB|Wireless) powered: true' \
        "$BATTERY_TEMP_PATH"; then
        fail_capture \
            "A charging source became active while waiting for manual start" \
            3
    fi

    if [ "$(playback_state)" = "PLAYING" ] &&
        ! screen_is_awake; then
        break
    fi

    now_epoch="$(date +%s)"
    manual_wait_elapsed=$((now_epoch - manual_wait_started_epoch))
    if [ "$manual_wait_elapsed" -ge "$MANUAL_START_TIMEOUT_SECONDS" ]; then
        fail_capture \
            "Manual start was not detected before timeout" \
            4
    fi
    sleep 2
done

sleep 3
if [ "$(playback_state)" != "PLAYING" ] ||
    screen_is_awake; then
    fail_capture \
        "Manual start state was not stable for three seconds" \
        5
fi

CAPTURE_CONTROLLED=true
{
    echo "manualStartDetected=$(date -Iseconds)"
    echo "playbackAtStart=$(playback_state)"
    echo "screenAtStart=Asleep"
} >> "$LOG_PATH"

PREVIOUS_PROCESS_TICKS=""
PREVIOUS_SYSTEM_TICKS=""
sample_count=$((DURATION_SECONDS / INTERVAL_SECONDS + 1))
sample_index=0
started_epoch="$(date +%s)"
while [ "$sample_index" -lt "$sample_count" ]; do
    target_epoch=$((started_epoch + sample_index * INTERVAL_SECONDS))
    now_epoch="$(date +%s)"
    remaining_seconds=$((target_epoch - now_epoch))
    if [ "$remaining_seconds" -gt 0 ]; then
        sleep "$remaining_seconds"
    fi
    now_epoch="$(date +%s)"
    elapsed_seconds=$((now_epoch - started_epoch))
    write_sample "$sample_index" "measured" "$elapsed_seconds"
    sample_index=$((sample_index + 1))
done

cmd media_session dispatch pause >/dev/null 2>&1 || true
cmd power wakeup >/dev/null 2>&1 || true
CAPTURE_CONTROLLED=false

{
    echo "completed=$(date -Iseconds)"
    echo "finalPlaybackState=$(playback_state)"
    echo "csv=$CSV_PATH"
    echo "log=$LOG_PATH"
} > "$DONE_PATH"
