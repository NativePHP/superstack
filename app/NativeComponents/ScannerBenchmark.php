<?php

namespace App\NativeComponents;

use Illuminate\View\View;
use Native\Mobile\Edge\NativeComponent;
use Native\Mobile\Facades\Scanner;

class ScannerBenchmark extends NativeComponent
{
    public int $totalScans = 0;

    public int $uniqueScans = 0;

    public ?string $lastValue = null;

    public ?string $lastFormat = null;

    public ?float $lastBridgeToPhpMs = null;

    public ?float $lastPhpMs = null;

    /** @var array<string, true> */
    protected array $seen = [];

    protected ?float $scanRequestedAt = null;

    public function navTitle(): string
    {
        return 'Scanner benchmark';
    }

    public function startScanner(): void
    {
        $sessionId = 'benchmark-'.bin2hex(random_bytes(6));
        $this->scanRequestedAt = microtime(true);

        Scanner::scan()
            ->id($sessionId)
            ->prompt('Scan the benchmark code')
            ->continuous()
            ->formats(['qr'])
            ->codeScanned(function ($event) use ($sessionId): void {
                $phpStartedAt = microtime(true);
                $this->totalScans++;
                $this->lastValue = $event->data;
                $this->lastFormat = $event->format;
                $this->lastBridgeToPhpMs = $this->scanRequestedAt === null
                    ? null
                    : round((microtime(true) - $this->scanRequestedAt) * 1000, 3);

                if (! isset($this->seen[$event->data])) {
                    $this->seen[$event->data] = true;
                    $this->uniqueScans++;
                }

                $this->lastPhpMs = round((microtime(true) - $phpStartedAt) * 1000, 3);

                logger()->info('scanner_benchmark.scan', [
                    'session_id' => $sessionId,
                    'value_sha256' => hash('sha256', $event->data),
                    'format' => $event->format,
                    'total_scans' => $this->totalScans,
                    'unique_scans' => $this->uniqueScans,
                    'bridge_to_php_ms' => $this->lastBridgeToPhpMs,
                    'php_ms' => $this->lastPhpMs,
                ]);
            })
            ->scan();
    }

    public function reset(): void
    {
        $this->totalScans = 0;
        $this->uniqueScans = 0;
        $this->lastValue = null;
        $this->lastFormat = null;
        $this->lastBridgeToPhpMs = null;
        $this->lastPhpMs = null;
        $this->seen = [];
        $this->scanRequestedAt = null;
    }

    public function render(): View
    {
        return view('native.scanner-benchmark');
    }
}
