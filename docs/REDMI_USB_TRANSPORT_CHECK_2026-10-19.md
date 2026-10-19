# Redmi USB versus paired Wi-Fi — 2026-10-19

## Result

USB is available and was faster in this short comparison. Prefer explicit
serial `55aqpfqkvw9xbafe` for subsequent phone commands while cable ADB remains
healthy. Keep paired Wi-Fi as a fallback, rediscovering its current connect port
when necessary. This is a transport result, not improved on-device vision speed.

Windows reports the Redmi ADB Interface, USB Composite Device and portable
device present with status OK (`VID_2717`, `PID_FF48`). Cable ADB reports
`device`; `sys.usb.state` is `mtp,adb`. Both transports returned the exact known
phone serial. The earlier intermittent USB failure's root cause remains unknown.

## Read-only comparison

One warmup followed by 12 shell identity requests per transport, alternating
order on each round. Timing includes Windows adb process startup/host overhead.
Then pulled the same installed original APK once per transport, USB first.
There was no installation, app launch, permission/settings change or data clear.

| Measurement | USB | Paired Wi-Fi |
| --- | ---: | ---: |
| Successful measured shell requests | 12/12 | 12/12 |
| Shell wall time p50 | 35.99 ms | 41.99 ms |
| Shell wall time p90 | 41.46 ms | 46.65 ms |
| Installed APK bytes | 88,732,901 | 88,732,901 |
| APK pull total wall time | 2.118 s | 3.646 s |
| APK pull including command overhead | 39.96 MiB/s | 23.21 MiB/s |

USB's observed file-read throughput was about 1.72 times Wi-Fi's. Only one large
transfer per transport was measured; order, caching and network load were not
controlled. Do not extrapolate exact install/push times or long-run reliability.
Both copies match the verified original 0.9.21/34 APK SHA-256:
`88828908DDE8C254A1AE0343B8073C80C0766518DEE706A7D8F1CEC3A07AF338`.

Raw sample timings, pull output and APK copies:
`outputs/reports/redmi-evidence/20261019-transport-154357/`, including
`summary.json`. No model/app source or build was changed; no test suite rerun
was needed for this read-only transport check.

A temporary ADB server on checked-free local port 5038 discovered the known
serial's current TLS endpoint, then was stopped. Only the temporary host-side
Wi-Fi connection opened for this comparison was disconnected afterward; phone
wireless debugging and the ordinary ADB server were not disabled/restarted.
