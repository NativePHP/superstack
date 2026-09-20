<?php

use App\NativeComponents\ScannerBenchmark;
use Native\Mobile\Testing\Native;

it('renders the scanner benchmark screen', function () {
    Native::test(ScannerBenchmark::class)
        ->assertSee('Scanner path benchmark')
        ->assertSee('Scan-to-PHP timing');
});

it('starts a continuous QR scan with an explicit session id', function () {
    $screen = Native::test(ScannerBenchmark::class)
        ->call('startScanner')
        ->assertNativeCalled('MobileScanner.Scan');

    $call = $screen->bridge()->callsTo('MobileScanner.Scan')[0]['params'];

    expect($call['continuous'])->toBeTrue()
        ->and($call['formats'])->toBe(['qr'])
        ->and($call['id'])->toStartWith('benchmark-');
});

it('resets all displayed measurements', function () {
    Native::test(ScannerBenchmark::class)
        ->set('totalScans', 4)
        ->set('uniqueScans', 2)
        ->call('reset')
        ->assertSet('totalScans', 0)
        ->assertSet('uniqueScans', 0)
        ->assertSet('lastValue', null);
});
