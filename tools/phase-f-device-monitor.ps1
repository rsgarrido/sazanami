param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("eq-disabled", "parametric-ten-filter", "parametric-ten-filter-limiter")]
    [string]$Scenario,

    [Parameter(Mandatory = $true)]
    [string]$DeviceSerial,

    [int]$DurationMinutes = 10,
    [int]$IntervalSeconds = 60,
    [string]$OutputDirectory = "app/build/reports/phase-f-thermal"
)

$ErrorActionPreference = "Stop"

if ($DurationMinutes -le 0) {
    throw "DurationMinutes must be positive."
}
if ($IntervalSeconds -le 0) {
    throw "IntervalSeconds must be positive."
}

$adbPath = Join-Path $env:LOCALAPPDATA "Android/Sdk/platform-tools/adb.exe"
if (-not (Test-Path -LiteralPath $adbPath)) {
    throw "ADB was not found at $adbPath"
}

$resolvedOutputDirectory = Join-Path (Get-Location) $OutputDirectory
New-Item -ItemType Directory -Path $resolvedOutputDirectory -Force |
    Out-Null

$startedAt = Get-Date
$runId = $startedAt.ToString("yyyyMMdd-HHmmss")
$csvPath = Join-Path $resolvedOutputDirectory "$runId-$Scenario.csv"
$logPath = Join-Path $resolvedOutputDirectory "$runId-$Scenario.log"

function Invoke-DeviceShell {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $output = & $adbPath -s $DeviceSerial shell @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "ADB shell command failed: $($Arguments -join ' ')`n$output"
    }
    return ($output | Out-String)
}

function Match-Value {
    param(
        [Parameter(Mandatory = $true)][string]$Text,
        [Parameter(Mandatory = $true)][string]$Pattern,
        [int]$Group = 1
    )

    $match = [regex]::Match(
        $Text,
        $Pattern,
        [System.Text.RegularExpressions.RegexOptions]::Multiline
    )
    if (-not $match.Success) {
        return $null
    }
    return $match.Groups[$Group].Value
}

function Temperature-Value {
    param(
        [Parameter(Mandatory = $true)][string]$ThermalText,
        [Parameter(Mandatory = $true)][string]$Name
    )

    return Match-Value `
        -Text $ThermalText `
        -Pattern "Temperature\{mValue=([-0-9.]+), mType=\d+, mName=$Name,"
}

function Capture-Sample {
    param([Parameter(Mandatory = $true)][int]$Index)

    $battery = Invoke-DeviceShell -Arguments @("dumpsys", "battery")
    $thermal = Invoke-DeviceShell -Arguments @("dumpsys", "thermalservice")
    $memory = Invoke-DeviceShell -Arguments @(
        "dumpsys",
        "meminfo",
        "com.example.cdplaya"
    )
    $cpu = Invoke-DeviceShell -Arguments @("dumpsys", "cpuinfo")
    $audio = Invoke-DeviceShell -Arguments @(
        "dumpsys",
        "media.audio_flinger"
    )
    $mediaSession = Invoke-DeviceShell -Arguments @(
        "dumpsys",
        "media_session"
    )

    $usbPowered = Match-Value `
        -Text $battery `
        -Pattern "^\s*USB powered:\s*(true|false)"
    if ($usbPowered -ne "false") {
        throw "Thermal run requires USB powered=false; observed $usbPowered."
    }

    $memoryTotals = [regex]::Match(
        $memory,
        "TOTAL PSS:\s*(\d+)\s+TOTAL RSS:\s*(\d+)\s+TOTAL SWAP PSS:\s*(\d+)"
    )
    $cpuLine = (
        $cpu -split "`r?`n" |
            Where-Object { $_ -match "com\.example\.cdplaya" } |
            Select-Object -First 1
    )
    $cpuPercent = if ($cpuLine -match "^\s*([0-9.]+)%") {
        $Matches[1]
    } else {
        $null
    }
    $rawUnderruns = [regex]::Matches(
        $audio,
        "Normal mixer raw underrun counters:\s*partial=(\d+)\s+empty=(\d+)"
    )
    $rawUnderrunSummary = (
        $rawUnderruns |
            ForEach-Object {
                "partial=$($_.Groups[1].Value),empty=$($_.Groups[2].Value)"
            }
    ) -join ";"
    $trackUnderruns = [regex]::Matches(
        $audio,
        "numTracks=(\d+)\s+writeErrors=(\d+)\s+underruns=(\d+)\s+overruns=(\d+)"
    )
    $trackUnderrunSummary = (
        $trackUnderruns |
            ForEach-Object {
                "tracks=$($_.Groups[1].Value)," +
                    "writeErrors=$($_.Groups[2].Value)," +
                    "underruns=$($_.Groups[3].Value)," +
                    "overruns=$($_.Groups[4].Value)"
            }
    ) -join ";"
    $playbackState = if (
        $mediaSession -match "state=PlaybackState \{state=PLAYING"
    ) {
        "PLAYING"
    } elseif (
        $mediaSession -match "state=PlaybackState \{state=PAUSED"
    ) {
        "PAUSED"
    } else {
        "OTHER"
    }

    $sample = [pscustomobject]@{
        scenario = $Scenario
        sample_index = $Index
        timestamp_local = (Get-Date).ToString("o")
        elapsed_seconds = [math]::Round(
            ((Get-Date) - $startedAt).TotalSeconds,
            1
        )
        ac_powered = Match-Value $battery "^\s*AC powered:\s*(true|false)"
        usb_powered = $usbPowered
        wireless_powered = Match-Value `
            $battery `
            "^\s*Wireless powered:\s*(true|false)"
        battery_status = Match-Value $battery "^\s*status:\s*(\d+)"
        battery_level_percent = Match-Value $battery "^\s*level:\s*(\d+)"
        battery_temperature_c = (
            [double](Match-Value $battery "^\s*temperature:\s*(\d+)") / 10.0
        )
        battery_voltage_mv = Match-Value $battery "^\s*voltage:\s*(\d+)"
        battery_current_raw = Match-Value $battery "^\s*current now:\s*(-?\d+)"
        thermal_status = Match-Value $thermal "^Thermal Status:\s*(\d+)"
        ap_temperature_c = Temperature-Value $thermal "AP"
        skin_temperature_c = Temperature-Value $thermal "SKIN"
        thermal_battery_temperature_c = Temperature-Value $thermal "BAT"
        pss_kib = if ($memoryTotals.Success) {
            $memoryTotals.Groups[1].Value
        } else {
            $null
        }
        rss_kib = if ($memoryTotals.Success) {
            $memoryTotals.Groups[2].Value
        } else {
            $null
        }
        swap_pss_kib = if ($memoryTotals.Success) {
            $memoryTotals.Groups[3].Value
        } else {
            $null
        }
        process_cpu_percent = $cpuPercent
        process_cpu_observation = $cpuLine.Trim()
        playback_state = $playbackState
        raw_mixer_underruns = $rawUnderrunSummary
        audio_track_underruns = $trackUnderrunSummary
    }

    if ($Index -eq 0) {
        $sample | Export-Csv -LiteralPath $csvPath -NoTypeInformation
    } else {
        $sample | Export-Csv `
            -LiteralPath $csvPath `
            -NoTypeInformation `
            -Append
    }

    @(
        "sample=$Index timestamp=$($sample.timestamp_local)"
        "battery=$($sample.battery_level_percent)% " +
            "batteryTemp=$($sample.battery_temperature_c)C " +
            "usb=$($sample.usb_powered)"
        "thermalStatus=$($sample.thermal_status) " +
            "AP=$($sample.ap_temperature_c)C " +
            "SKIN=$($sample.skin_temperature_c)C"
        "PSS=$($sample.pss_kib)KiB RSS=$($sample.rss_kib)KiB " +
            "swapPSS=$($sample.swap_pss_kib)KiB"
        "CPU=$($sample.process_cpu_observation)"
        "playback=$($sample.playback_state)"
        "rawUnderruns=$rawUnderrunSummary"
        "trackUnderruns=$trackUnderrunSummary"
        ""
    ) | Add-Content -LiteralPath $logPath

    Write-Output (
        "sample=$Index elapsed=$($sample.elapsed_seconds)s " +
            "AP=$($sample.ap_temperature_c)C " +
            "SKIN=$($sample.skin_temperature_c)C " +
            "battery=$($sample.battery_level_percent)% " +
            "CPU=$($sample.process_cpu_percent)%"
    )
}

@(
    "Sazanami Phase F device thermal monitor"
    "scenario=$Scenario"
    "device=$DeviceSerial"
    "started=$($startedAt.ToString('o'))"
    "durationMinutes=$DurationMinutes"
    "intervalSeconds=$IntervalSeconds"
    "battery observations are informal and are not efficiency measurements"
    ""
) | Set-Content -LiteralPath $logPath

$sampleCount = [math]::Floor(
    $DurationMinutes * 60 / $IntervalSeconds
) + 1
for ($index = 0; $index -lt $sampleCount; $index++) {
    Capture-Sample -Index $index
    if ($index -lt $sampleCount - 1) {
        Start-Sleep -Seconds $IntervalSeconds
    }
}

Write-Output "csv=$csvPath"
Write-Output "log=$logPath"
