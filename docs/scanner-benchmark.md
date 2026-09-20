# Scanner benchmark

This benchmark exercises the real NativePHP scanner path on one Android device.
It deliberately keeps the workload small and observable:

1. Android camera and barcode decoder detect a QR value.
2. NativePHP delivers the `CodeScanned` event to the persistent PHP runtime.
3. PHP records the value, hashes it for logs, and deduplicates it in memory.
4. The screen reports the scan-to-PHP and PHP-only portions separately.

The Android implementation is the MIT-licensed `sghimire/mobile-scanner` plugin,
registered explicitly in `app/Providers/NativeServiceProvider.php`. It uses
CameraX and ML Kit in the APK and emits the `CodeScanned` event through the
NativePHP bridge. The benchmark does not use the paid scanner plugin or claim
that NativePHP and React Native have identical camera internals.

## Why these numbers matter

`scan-to-PHP` is a coarse end-to-end measurement from the scan request until PHP handles the result. It includes camera/decoder time and the native-to-PHP bridge, so it is not a claim about any one layer.

`PHP dedupe timing` measures only the PHP-side bookkeeping after the callback has arrived. The log stores a SHA-256 digest rather than the scanned value so benchmark artifacts do not leak the QR contents.

For a useful comparison, run the same QR values and number of scans on the same phone, with the same build mode and runtime mode. Record at least 10 successful scans, discard the first warm-up scan, and report median and p95 rather than a single best result.

## Reproduction

```bash
php artisan native:install android --no-interaction
php artisan native:run android
```

Open **Scanner benchmark**, scan the generated QR code repeatedly, and capture the screen plus the log output. The phone must be the same device for every comparison.

The generated payload is in `public/benchmark-qr.png`. Android runtime evidence
is written to the Laravel log as `scanner_benchmark.scan`; the value itself is
never logged, only its SHA-256 digest.

The first version intentionally does not claim React Native parity. A fair comparison app must use the same QR payloads, camera format, scan count, warm-up policy, and release/debug mode before numbers are compared.
