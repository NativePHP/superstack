# Mobile Scanner

Native QR code / barcode scanning for [NativePHP Mobile](https://nativephp.com) apps, powered by CameraX + ML Kit on Android and AVFoundation on iOS.

This package is a **free, self-contained alternative** to the paid `nativephp/mobile-scanner` plugin. It ships its own Laravel facade, a fluent scan builder, Laravel events, JS/TypeScript bindings, and the native Kotlin/Swift implementation — with no dependency on the paid plugin.

## Features

- Full-screen native camera scanner for QR codes and common barcode formats.
- Built-in gallery button on the scanner overlay — pick an existing photo instead of the live camera, decoded on-device (Android Photo Picker + ML Kit, iOS `PHPickerViewController` + Vision). No extra permissions or setup required.
- Fluent, chainable scan builder in both PHP and JavaScript.
- Single-shot or continuous (multi-scan) sessions.
- Programmatically stop an open scan session.
- `CodeScanned` and `Cancelled` Laravel events, or bind straight to Livewire with `#[OnNative]`.
- Works from PHP (Blade/Livewire) and from JavaScript (Vue, React, Inertia, or plain JS).

## Requirements

- PHP ^8.2
- [`nativephp/mobile`](https://nativephp.com) ^3.0
- `livewire/livewire` — only needed if you use the `#[OnNative]` attribute

## Installation

```bash
composer require sghimire/mobile-scanner
```

Laravel's package auto-discovery registers `ScannerServiceProvider` for you. Then register the plugin with NativePHP:

```bash
php artisan native:plugin:register
```

This wires up the plugin's `nativephp.json` manifest (bridge functions, Android camera/vibrate permissions, iOS `NSCameraUsageDescription`) into your native build. Rebuild/reinstall the native shell afterwards (`php artisan native:install` or `native:run`) so the permissions and bridge functions are picked up.

## How It Works (Under the Hood)

Same two-phase pattern as every async call in this plugin family — a synchronous "start" acknowledgement, then the real result delivered later through two parallel channels.

1. **Request out.** `Scanner::scan()->scan()` (PHP) and `Scanner.scan()` (JS) both reach the same bridge — JS via `fetch('/_native/api/call', { method: 'MobileScanner.Scan', params })`, PHP via `nativephp_call('MobileScanner.Scan', json_encode($params))`. The bridge router matches `"MobileScanner.Scan"` to `ScannerFunctions.Scan`, which opens the full-screen camera UI (CameraX + ML Kit on Android, AVFoundation on iOS).
2. **Immediate ack.** The call returns right away confirming the scanner UI is open — not that anything has been decoded yet.
3. **Result comes back later, twice, per scan.** Each time the camera decodes a matching code, the native side injects one script into the webview that fires a `native-event` `CustomEvent` on `document` (what the JS `On()`/`Off()` helpers listen for) *and* makes a second call back into Laravel that instantiates and dispatches the real `CodeScanned` event (what `Event::listen()` / `#[OnNative]` pick up). Closing without a match delivers `Cancelled` the same way.
4. **Continuous mode** just means the scanner stays open and repeats step 3 for every new code, debounced natively so the same code isn't reported multiple times per second, until you call `Scanner::stop()` / `Scanner.stop()` or the user closes it.

Because results are asynchronous and delivered to PHP and JS independently, always drive your UI from the `CodeScanned` / `Cancelled` events — never from the return value of `scan()`.

### Scanning from the photo gallery

Every scanner overlay opened by `MobileScanner.Scan` includes a gallery button (🖼) next to the close/torch buttons by default. No PHP/JS code or extra config is needed — it's part of the same overlay:

- Tapping it opens the OS photo picker (Android Photo Picker, iOS `PHPickerViewController`) — neither requires a photo-library permission.
- The chosen image is decoded on-device against the same `formats()` you passed to `scan()` (Android: ML Kit; iOS: the Vision framework).
- A match fires `CodeScanned` exactly like a camera match, and closes the scanner — even in `continuous()` mode, since picking a photo is a deliberate one-off action.
- If no supported code is found in the picture, a short native message is shown and the scanner stays open so the user can try the camera or pick another photo — no event fires for that case.

Turn it off per scan with `->gallery(false)` (PHP) / `.gallery(false)` (JS) — the button is simply omitted from the overlay, camera-only:

```php
Scanner::scan()->gallery(false)->scan();
```

```js
Scanner.scan().gallery(false);
```

---

## PHP Usage

### The `Scanner` facade

```php
use Sandip\Scanner\Native\Facades\Scanner;

// Scan a single QR code with defaults
Scanner::scan()->scan();

// Fully configured
Scanner::scan()
    ->id('ticket-scanner')                    // correlate this scan with its events
    ->prompt('Scan your ticket')               // text shown above the camera viewfinder
    ->formats(['qr', 'ean13'])                 // one or more barcode formats to detect
    ->continuous()                             // keep scanning after each match
    ->scan();
```

`scan()` on the builder returns `bool` — `true` once the request reached the native bridge, `false` if it couldn't be started (e.g. running outside the native shell, or the builder was already started once).

If you never call `->scan()` explicitly, it fires automatically when the builder object is destructed — so `Scanner::scan()->prompt('Scan Code');` alone is enough to trigger it. Calling `->scan()` yourself is recommended so you can check the return value.

#### Builder methods

| Method | Description |
|---|---|
| `prompt(string $text)` | Instruction text shown above the camera viewfinder. Defaults to `"Scan Code"`. |
| `continuous(bool $continuous = true)` | Keep the scanner open and keep firing `CodeScanned` after each match instead of closing on the first one. |
| `gallery(bool $allow = true)` | Show/hide the gallery button on the overlay. Defaults to `true`; pass `false` to force camera-only scanning. |
| `formats(array $formats)` | Restrict detection to one or more formats (see table below). Throws `InvalidArgumentException` if empty or unknown. |
| `haptics(bool $enabled = true)` | Vibrate/impact-feedback on a successful scan. Defaults to `true`. |
| `zoom(float $ratio = 1.0)` | Initial camera zoom ratio, clamped to what the device supports. Defaults to `1.0`. Throws `InvalidArgumentException` if not positive. |
| `maxZoom(float $ratio = 3.0)` | Upper bound of the on-screen zoom slider, clamped to what the device supports. Defaults to `3.0`. Throws `InvalidArgumentException` if not positive. |
| `zoomControl(bool $enabled = true)` | Show/hide the on-screen zoom slider. Defaults to `true`. |
| `focusOnTap(bool $enabled = true)` | Let the user tap the preview to refocus the camera. Defaults to `true`. |
| `timeout(int $seconds = 0)` | Auto-cancel the scan after N seconds, firing `Cancelled` with reason `timeout`. Disabled (`0`) by default. Throws `InvalidArgumentException` if negative. |
| `id(string $id)` | Custom correlation ID for this scan session (not auto-generated — `null` unless set). |
| `getId()` | Get this scan's correlation ID, or `null`. |
| `scan()` | Send the scan request to the native bridge. Returns `bool`. |

#### Supported formats

`qr`, `ean13`, `ean8`, `code128`, `code39`, `upca`, `upce`, `all` (also available as `PendingScan::FORMATS`). Defaults to `['qr']` if `formats()` is never called.

### Stopping a scan

Useful for dismissing a `continuous()` session programmatically (e.g. from a "Done" button elsewhere in the UI):

```php
use Sandip\Scanner\Native\Facades\Scanner;

Scanner::stop();                 // stop whatever scanner is open
Scanner::stop('ticket-scanner');  // stop a specific session by id
```

This fires `Cancelled` with `reason: 'stopped_by_app'`.

### Listening for results

```php
use Sandip\Scanner\Native\Events\Scanner\CodeScanned;
use Sandip\Scanner\Native\Events\Scanner\Cancelled;
use Illuminate\Support\Facades\Event;

Event::listen(function (CodeScanned $event) {
    $event->data;   // string — the decoded value
    $event->format; // string — which format matched, e.g. "qr"
    $event->id;     // ?string — matches the id you passed to ->id(), if any
});

Event::listen(function (Cancelled $event) {
    $event->reason; // ?string — e.g. "user_cancelled", "stopped_by_app", "timeout", "camera_error"
    $event->id;     // ?string
});
```

### Livewire: `#[OnNative]`

```php
use Livewire\Component;
use Sandip\Scanner\Native\Attributes\OnNative;
use Sandip\Scanner\Native\Events\Scanner\CodeScanned;
use Sandip\Scanner\Native\Events\Scanner\Cancelled;
use Sandip\Scanner\Native\Facades\Scanner;

class TicketScanner extends Component
{
    public ?string $result = null;

    public function startScan(): void
    {
        Scanner::scan()->id('ticket-scanner')->prompt('Scan your ticket')->scan();
    }

    #[OnNative(CodeScanned::class)]
    public function onCodeScanned(string $data, string $format, ?string $id): void
    {
        $this->result = $data;
    }

    #[OnNative(Cancelled::class)]
    public function onCancelled(?string $reason, ?string $id): void
    {
        // handle the user closing the scanner
    }
}
```

---

## JavaScript Usage

### Importing

This package doesn't publish a `#nativephp` import alias (that's reserved for NativePHP's first-party plugins). Import the file directly — either from the vendor path, or copy it into your own `resources/js/` and import it from there:

```js
import { Scanner, On, Off, Events } from '../../vendor/sghimire/mobile-scanner/resources/js/scanner.js';
```

Full TypeScript types (including the `BarcodeFormat` union) are included in `scanner.d.ts` alongside it.

### Basic scan

`Scanner.scan()` returns a thenable builder — `await` it directly, or chain builder methods first:

```js
import { Scanner } from '../../vendor/sghimire/mobile-scanner/resources/js/scanner.js';

await Scanner.scan();

// or fully configured
await Scanner.scan()
  .id('ticket-scanner')
  .prompt('Scan your ticket')
  .formats(['qr', 'ean13'])
  .continuous();
```

### Stopping a scan

```js
import { Scanner } from '../../vendor/sghimire/mobile-scanner/resources/js/scanner.js';

await Scanner.stop();                  // stop whatever scanner is open
await Scanner.stop('ticket-scanner');  // stop a specific session by id
```

### Listening for events

```js
import { Scanner, On, Off, Events } from '../../vendor/sghimire/mobile-scanner/resources/js/scanner.js';

function handleScanned(payload) {
  // payload: { data: string, format: string, id: string | null }
  console.log('Scanned:', payload.data, payload.format);
}

function handleCancelled(payload) {
  // payload: { reason: string | null, id: string | null }
}

On(Events.Scanner.CodeScanned, handleScanned);
On(Events.Scanner.Cancelled, handleCancelled);

// later, e.g. on component unmount
Off(Events.Scanner.CodeScanned, handleScanned);
Off(Events.Scanner.Cancelled, handleCancelled);
```

### Vue 3 example

```vue
<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import { Scanner, On, Off, Events } from '../../vendor/sghimire/mobile-scanner/resources/js/scanner.js';

const scannedValue = ref(null);

function handleScanned(payload) {
  scannedValue.value = payload.data;
}

onMounted(() => On(Events.Scanner.CodeScanned, handleScanned));
onUnmounted(() => Off(Events.Scanner.CodeScanned, handleScanned));

async function startScan() {
  await Scanner.scan().prompt('Scan your ticket').formats(['qr']);
}
</script>

<template>
  <button @click="startScan">Scan ticket</button>
  <p v-if="scannedValue">Scanned: {{ scannedValue }}</p>
</template>
```

### React example

```jsx
import { useState, useEffect, useCallback } from 'react';
import { Scanner, On, Off, Events } from '../../vendor/sghimire/mobile-scanner/resources/js/scanner.js';

export function TicketScanner() {
  const [scannedValue, setScannedValue] = useState(null);

  useEffect(() => {
    const handleScanned = (payload) => setScannedValue(payload.data);
    On(Events.Scanner.CodeScanned, handleScanned);
    return () => Off(Events.Scanner.CodeScanned, handleScanned);
  }, []);

  const startScan = useCallback(() => {
    Scanner.scan().prompt('Scan your ticket').formats(['qr']);
  }, []);

  return (
    <>
      <button onClick={startScan}>Scan ticket</button>
      {scannedValue && <p>Scanned: {scannedValue}</p>}
    </>
  );
}
```

### JS API reference

| Export | Signature | Description |
|---|---|---|
| `Scanner.scan()` | `() => PendingScan` | Start building a scan session. |
| `.prompt(text)` | `(string) => this` | Instruction text above the viewfinder. |
| `.continuous(continuous?)` | `(boolean = true) => this` | Keep scanning after each match. |
| `.gallery(allow?)` | `(boolean = true) => this` | Show/hide the gallery button. Defaults to `true`; pass `false` for camera-only. |
| `.formats(formats)` | `(BarcodeFormat[]) => this` | Restrict detection to given formats. Throws if empty/invalid. |
| `.haptics(enabled?)` | `(boolean = true) => this` | Vibrate/impact-feedback on a successful scan. Defaults to `true`. |
| `.zoom(ratio?)` | `(number = 1.0) => this` | Initial camera zoom ratio, clamped to what the device supports. Throws if not positive. |
| `.maxZoom(ratio?)` | `(number = 3.0) => this` | Upper bound of the on-screen zoom slider, clamped to what the device supports. Throws if not positive. |
| `.zoomControl(enabled?)` | `(boolean = true) => this` | Show/hide the on-screen zoom slider. Defaults to `true`. |
| `.focusOnTap(enabled?)` | `(boolean = true) => this` | Let the user tap the preview to refocus the camera. Defaults to `true`. |
| `.timeout(seconds?)` | `(number = 0) => this` | Auto-cancel the scan after N seconds, firing `Cancelled` with reason `timeout`. Disabled (`0`) by default. Throws if negative. |
| `.id(id)` | `(string) => this` | Custom correlation ID. |
| `.getId()` | `() => string \| null` | Read the current correlation ID. |
| `Scanner.stop(id?)` | `(string?) => Promise<{ stopped: boolean }>` | Dismiss the open scanner. |
| `On(event, callback)` | `(string, (payload, eventName) => void) => void` | Subscribe to a native event. |
| `Off(event, callback)` | `(string, (payload, eventName) => void) => void` | Unsubscribe. |
| `Events.Scanner.CodeScanned` | `string` | Event name constant. |
| `Events.Scanner.Cancelled` | `string` | Event name constant. |

`await`-ing (or `.then`-ing) a `PendingScan` sends the request to the native bridge exactly once — awaiting it twice is a no-op the second time.

---

## Events reference

### `CodeScanned`

Dispatched every time the camera successfully decodes a matching code.

| Property | Type | Description |
|---|---|---|
| `data` | `string` / `string` | The decoded value. |
| `format` | `string` / `string` | Which format matched, e.g. `"qr"`, `"ean13"`. |
| `id` | `?string` / `string \| null` | The correlation ID from `.id()`, if one was set. |

### `Cancelled`

Dispatched when the scanner closes without (another) match — the user tapped close, or `Scanner::stop()` / `Scanner.stop()` was called.

| Property | Type | Description |
|---|---|---|
| `reason` | `?string` / `string \| null` | `"user_cancelled"` when the user taps close, `"stopped_by_app"` when closed via `stop()`, `"timeout"` when `.timeout()` elapsed, `"camera_error"` if the camera failed to start, `"permission_denied"` / `"permission_required"` after a permission prompt resolves (retry `.scan()` on `permission_required`). Never `null` in practice. |
| `id` | `?string` / `string \| null` | The correlation ID from `.id()`, if one was set. |

- PHP classes: `Sandip\Scanner\Native\Events\Scanner\CodeScanned`, `Sandip\Scanner\Native\Events\Scanner\Cancelled`
- JS event name constants: `Events.Scanner.CodeScanned`, `Events.Scanner.Cancelled`

## Implementation Guide: Building a Ticket Check-In Scanner

A typical staff-facing feature: keep the camera open, scan every ticket that passes by, validate each one against the backend, and show a running result list — without reopening the scanner between tickets.

### 1. Backend: validate a ticket code

```php
// routes/api.php
Route::post('/tickets/check-in', CheckInTicketController::class);
```

```php
// app/Http/Controllers/CheckInTicketController.php
namespace App\Http\Controllers;

use App\Models\Ticket;
use Illuminate\Http\Request;

class CheckInTicketController extends Controller
{
    public function __invoke(Request $request)
    {
        $ticket = Ticket::where('code', $request->string('code'))->first();

        if (! $ticket || $ticket->checked_in_at) {
            return response()->json(['valid' => false], 422);
        }

        $ticket->update(['checked_in_at' => now()]);

        return response()->json(['valid' => true, 'name' => $ticket->holder_name]);
    }
}
```

### 2a. Livewire scanner

```php
// app/Livewire/TicketScanner.php
namespace App\Livewire;

use App\Models\Ticket;
use Livewire\Component;
use Sandip\Scanner\Native\Attributes\OnNative;
use Sandip\Scanner\Native\Events\Scanner\CodeScanned;
use Sandip\Scanner\Native\Facades\Scanner;

class TicketScanner extends Component
{
    public array $log = [];

    public function startScanning(): void
    {
        Scanner::scan()
            ->id('check-in')
            ->prompt('Scan a ticket')
            ->formats(['qr'])
            ->continuous()
            ->scan();
    }

    public function stopScanning(): void
    {
        Scanner::stop('check-in');
    }

    #[OnNative(CodeScanned::class)]
    public function onCodeScanned(string $data): void
    {
        $ticket = Ticket::where('code', $data)->first();
        $valid = $ticket && ! $ticket->checked_in_at;

        if ($valid) {
            $ticket->update(['checked_in_at' => now()]);
        }

        array_unshift($this->log, [
            'name' => $ticket->holder_name ?? $data,
            'valid' => $valid,
        ]);
    }

    public function render()
    {
        return view('livewire.ticket-scanner');
    }
}
```

```blade
{{-- resources/views/livewire/ticket-scanner.blade.php --}}
<div>
    <button wire:click="startScanning">Start Check-In</button>
    <button wire:click="stopScanning">Stop</button>

    <ul>
        @foreach ($log as $entry)
            <li class="{{ $entry['valid'] ? 'text-green-600' : 'text-red-600' }}">
                {{ $entry['name'] }} — {{ $entry['valid'] ? 'Checked in' : 'Invalid/duplicate' }}
            </li>
        @endforeach
    </ul>
</div>
```

### 2b. Vue/React scanner (calling the API endpoint)

```vue
<!-- resources/js/Pages/TicketScanner.vue -->
<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import axios from 'axios';
import { Scanner, On, Off, Events } from '../../vendor/sghimire/mobile-scanner/resources/js/scanner.js';

const log = ref([]);

async function handleScanned(payload) {
  const { data: result } = await axios.post('/tickets/check-in', { code: payload.data });
  log.value.unshift({ name: result.name ?? payload.data, valid: result.valid });
}

onMounted(() => On(Events.Scanner.CodeScanned, handleScanned));
onUnmounted(() => Off(Events.Scanner.CodeScanned, handleScanned));

function startScanning() {
  Scanner.scan().id('check-in').prompt('Scan a ticket').formats(['qr']).continuous();
}

function stopScanning() {
  Scanner.stop('check-in');
}
</script>

<template>
  <button @click="startScanning">Start Check-In</button>
  <button @click="stopScanning">Stop</button>

  <ul>
    <li v-for="(entry, i) in log" :key="i" :class="entry.valid ? 'text-green-600' : 'text-red-600'">
      {{ entry.name }} — {{ entry.valid ? 'Checked in' : 'Invalid/duplicate' }}
    </li>
  </ul>
</template>
```

```jsx
// resources/js/Pages/TicketScanner.jsx
import { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import { Scanner, On, Off, Events } from '../../vendor/sghimire/mobile-scanner/resources/js/scanner.js';

export function TicketScanner() {
  const [log, setLog] = useState([]);

  useEffect(() => {
    const handleScanned = async (payload) => {
      const { data: result } = await axios.post('/tickets/check-in', { code: payload.data });
      setLog((prev) => [{ name: result.name ?? payload.data, valid: result.valid }, ...prev]);
    };
    On(Events.Scanner.CodeScanned, handleScanned);
    return () => Off(Events.Scanner.CodeScanned, handleScanned);
  }, []);

  const startScanning = useCallback(() => {
    Scanner.scan().id('check-in').prompt('Scan a ticket').formats(['qr']).continuous();
  }, []);

  const stopScanning = useCallback(() => Scanner.stop('check-in'), []);

  return (
    <>
      <button onClick={startScanning}>Start Check-In</button>
      <button onClick={stopScanning}>Stop</button>
      <ul>
        {log.map((entry, i) => (
          <li key={i} style={{ color: entry.valid ? 'green' : 'red' }}>
            {entry.name} — {entry.valid ? 'Checked in' : 'Invalid/duplicate'}
          </li>
        ))}
      </ul>
    </>
  );
}
```

`continuous()` keeps the same scanner session open across many tickets — each decode fires its own `CodeScanned`, debounced natively so a ticket held in frame for a second isn't logged twice. Call `Scanner::stop('check-in')` / `Scanner.stop('check-in')` from a "Done" button to close the camera when the shift ends.

## Platform notes

| | Android | iOS |
|---|---|---|
| Min OS version | API 23 | 15.0 |
| Permission | `android.permission.CAMERA`, `android.permission.VIBRATE` | `NSCameraUsageDescription` in `Info.plist` (haptics need no entitlement) |
| Native implementation | `resources/android/ScannerFunctions.kt` (CameraX + ML Kit barcode scanning) | `resources/ios/ScannerFunctions.swift` (AVFoundation) |
| Gallery picker | Android Photo Picker (`ActivityResultContracts.PickVisualMedia`) — no permission needed | `PHPickerViewController` — no permission needed |
| Gallery decoding | ML Kit (`InputImage.fromFilePath`) | Vision (`VNDetectBarcodesRequest`) |

Both are configured automatically by `nativephp.json` — you don't need to edit native project files by hand. Continuous mode uses a debounce window on both platforms so the same code isn't reported multiple times per second.

## Testing

```bash
composer install
composer test
```

Outside of a compiled native shell, `Scanner::scan()->scan()` and `Scanner::stop()` return `false` (there's no bridge to call) — this is expected and is exactly what the test suite asserts.

## License

MIT
