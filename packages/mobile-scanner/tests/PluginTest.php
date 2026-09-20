<?php

use Sandip\Scanner\Native\Events\Scanner\Cancelled;
use Sandip\Scanner\Native\Events\Scanner\CodeScanned;
use Sandip\Scanner\Native\PendingScan;
use Sandip\Scanner\Native\Scanner;

beforeEach(function () {
    $this->pluginPath = dirname(__DIR__);
    $this->manifestPath = $this->pluginPath.'/nativephp.json';
});

describe('Plugin Manifest', function () {
    it('has a valid nativephp.json file', function () {
        expect(file_exists($this->manifestPath))->toBeTrue();

        json_decode(file_get_contents($this->manifestPath), true);

        expect(json_last_error())->toBe(JSON_ERROR_NONE);
    });

    it('has required fields', function () {
        $manifest = json_decode(file_get_contents($this->manifestPath), true);

        expect($manifest)->toHaveKeys(['name', 'namespace', 'bridge_functions']);
        expect($manifest['name'])->toBe('sghimire/mobile-scanner');
        expect($manifest['namespace'])->toBe('Scanner');
    });

    it('registers its own bridge functions, distinct from the paid plugin\'s Scanner.Scan', function () {
        $manifest = json_decode(file_get_contents($this->manifestPath), true);

        $names = array_column($manifest['bridge_functions'], 'name');

        expect($names)->toBe(['MobileScanner.Scan', 'MobileScanner.Stop']);
        expect($names)->not->toContain('Scanner.Scan');

        foreach ($manifest['bridge_functions'] as $function) {
            expect($function)->toHaveKeys(['name']);
            expect(isset($function['android']) || isset($function['ios']))->toBeTrue();
        }
    });

    it('requests camera permission on Android', function () {
        $manifest = json_decode(file_get_contents($this->manifestPath), true);

        expect($manifest['android']['permissions'])->toContain('android.permission.CAMERA');
    });

    it('declares the NSCameraUsageDescription iOS requires', function () {
        $manifest = json_decode(file_get_contents($this->manifestPath), true);

        expect($manifest['ios']['info_plist'] ?? [])->toHaveKey('NSCameraUsageDescription');
    });

    it('declares the events it dispatches', function () {
        $manifest = json_decode(file_get_contents($this->manifestPath), true);

        expect($manifest['events'])->toBe([
            'Sandip\\Scanner\\Native\\Events\\Scanner\\CodeScanned',
            'Sandip\\Scanner\\Native\\Events\\Scanner\\Cancelled',
        ]);
    });
});

describe('Native Code', function () {
    it('has Android Kotlin file', function () {
        $kotlinFile = $this->pluginPath.'/resources/android/ScannerFunctions.kt';

        expect(file_exists($kotlinFile))->toBeTrue();

        $content = file_get_contents($kotlinFile);
        expect($content)->toContain('package com.sandip.plugins.scanner');
        expect($content)->toContain('object ScannerFunctions');
        expect($content)->toContain('class Scan(');
        expect($content)->toContain('class Stop(');
        expect($content)->toContain('BridgeFunction');
    });

    it('has iOS Swift file', function () {
        $swiftFile = $this->pluginPath.'/resources/ios/ScannerFunctions.swift';

        expect(file_exists($swiftFile))->toBeTrue();

        $content = file_get_contents($swiftFile);
        expect($content)->toContain('enum ScannerFunctions');
        expect($content)->toContain('class Scan:');
        expect($content)->toContain('class Stop:');
        expect($content)->toContain('BridgeFunction');
    });

    it('has matching bridge function classes in native code', function () {
        $manifest = json_decode(file_get_contents($this->manifestPath), true);

        $kotlinContent = file_get_contents($this->pluginPath.'/resources/android/ScannerFunctions.kt');
        $swiftContent = file_get_contents($this->pluginPath.'/resources/ios/ScannerFunctions.swift');

        foreach ($manifest['bridge_functions'] as $function) {
            if (isset($function['android'])) {
                $parts = explode('.', $function['android']);
                $className = end($parts);
                expect($kotlinContent)->toContain("class {$className}(");
            }

            if (isset($function['ios'])) {
                $parts = explode('.', $function['ios']);
                $className = end($parts);
                expect($swiftContent)->toContain("class {$className}:");
            }
        }
    });

    it('supports continuous mode with a debounce window on both platforms', function () {
        $kotlinContent = file_get_contents($this->pluginPath.'/resources/android/ScannerFunctions.kt');
        $swiftContent = file_get_contents($this->pluginPath.'/resources/ios/ScannerFunctions.swift');

        expect($kotlinContent)->toContain('REPEAT_DEBOUNCE_MS');
        expect($swiftContent)->toContain('repeatDebounceSeconds');
    });

    it('dispatches events asynchronously instead of blocking the bridge thread', function () {
        $kotlinContent = file_get_contents($this->pluginPath.'/resources/android/ScannerFunctions.kt');
        $swiftContent = file_get_contents($this->pluginPath.'/resources/ios/ScannerFunctions.swift');

        expect($kotlinContent)->toContain('NativeActionCoordinator.dispatchEvent');
        expect($swiftContent)->toContain('LaravelBridge.shared.send');
    });
});

describe('PHP Classes', function () {
    it('has service provider', function () {
        $file = $this->pluginPath.'/src/ScannerServiceProvider.php';
        expect(file_exists($file))->toBeTrue();

        $content = file_get_contents($file);
        expect($content)->toContain('namespace Sandip\Scanner\Native');
        expect($content)->toContain('class ScannerServiceProvider');
    });

    it('has facade', function () {
        $file = $this->pluginPath.'/src/Facades/Scanner.php';
        expect(file_exists($file))->toBeTrue();

        $content = file_get_contents($file);
        expect($content)->toContain('namespace Sandip\Scanner\Native\Facades');
        expect($content)->toContain('class Scanner extends Facade');
    });

    it('has main implementation class, builder, events, and attribute', function () {
        expect(file_exists($this->pluginPath.'/src/Scanner.php'))->toBeTrue();
        expect(file_exists($this->pluginPath.'/src/PendingScan.php'))->toBeTrue();
        expect(file_exists($this->pluginPath.'/src/Events/Scanner/CodeScanned.php'))->toBeTrue();
        expect(file_exists($this->pluginPath.'/src/Events/Scanner/Cancelled.php'))->toBeTrue();
        expect(file_exists($this->pluginPath.'/src/Attributes/OnNative.php'))->toBeTrue();
    });
});

describe('Scanner manager', function () {
    it('returns a fluent PendingScan from scan()', function () {
        expect((new Scanner)->scan())->toBeInstanceOf(PendingScan::class);
    });

    it('stop() returns false outside a native runtime', function () {
        expect((new Scanner)->stop())->toBeFalse();
    });
});

describe('PendingScan', function () {
    it('defaults to a single qr format and non-continuous mode', function () {
        $prompt = new PendingScan;

        expect($prompt->getId())->toBeNull();
    });

    it('accepts a valid single format', function () {
        expect((new PendingScan)->formats(['ean13']))->toBeInstanceOf(PendingScan::class);
    });

    it('accepts every documented format, including all', function () {
        foreach (PendingScan::FORMATS as $format) {
            expect((new PendingScan)->formats([$format]))->toBeInstanceOf(PendingScan::class);
        }
    });

    it('rejects an empty formats list', function () {
        (new PendingScan)->formats([]);
    })->throws(InvalidArgumentException::class);

    it('rejects an unknown format', function () {
        (new PendingScan)->formats(['not-a-real-format']);
    })->throws(InvalidArgumentException::class);

    it('chains fluent configuration methods', function () {
        $prompt = (new PendingScan)
            ->prompt('Scan your ticket')
            ->continuous(true)
            ->formats(['qr', 'ean13'])
            ->id('ticket-scanner');

        expect($prompt)->toBeInstanceOf(PendingScan::class);
        expect($prompt->getId())->toBe('ticket-scanner');
    });

    it('returns false when started outside a native runtime', function () {
        expect((new PendingScan)->scan())->toBeFalse();
    });

    it('refuses to start twice', function () {
        $prompt = new PendingScan;
        $prompt->scan();

        expect($prompt->scan())->toBeFalse();
    });
});

describe('Events', function () {
    it('CodeScanned carries data, format, and an optional id', function () {
        $event = new CodeScanned(data: 'otpauth://totp/example', format: 'qr', id: 'abc');

        expect($event->data)->toBe('otpauth://totp/example');
        expect($event->format)->toBe('qr');
        expect($event->id)->toBe('abc');
    });

    it('Cancelled defaults reason and id to null', function () {
        $event = new Cancelled;

        expect($event->reason)->toBeNull();
        expect($event->id)->toBeNull();
    });
});

describe('Composer Configuration', function () {
    it('has valid composer.json', function () {
        $composerPath = $this->pluginPath.'/composer.json';
        expect(file_exists($composerPath))->toBeTrue();

        $composer = json_decode(file_get_contents($composerPath), true);

        expect(json_last_error())->toBe(JSON_ERROR_NONE);
        expect($composer['name'])->toBe('sghimire/mobile-scanner');
        expect($composer['type'])->toBe('nativephp-plugin');
        expect($composer['extra']['nativephp']['manifest'])->toBe('nativephp.json');
        expect($composer['autoload']['psr-4'])->toHaveKey('Sandip\\Scanner\\Native\\');
    });
});

describe('Lifecycle Hooks', function () {
    it('has copy_assets hook command', function () {
        $manifest = json_decode(file_get_contents($this->manifestPath), true);

        expect($manifest['hooks']['copy_assets'] ?? null)->not->toBeNull();

        $commandFile = $this->pluginPath.'/src/Commands/CopyAssetsCommand.php';
        expect(file_exists($commandFile))->toBeTrue();
    });

    it('copy_assets command extends NativePluginHookCommand', function () {
        $content = file_get_contents($this->pluginPath.'/src/Commands/CopyAssetsCommand.php');

        expect($content)->toContain('extends NativePluginHookCommand');
        expect($content)->toContain('use Native\Mobile\Plugins\Commands\NativePluginHookCommand');
    });

    it('copy_assets command has correct signature', function () {
        $manifest = json_decode(file_get_contents($this->manifestPath), true);
        $expectedSignature = $manifest['hooks']['copy_assets'];

        $content = file_get_contents($this->pluginPath.'/src/Commands/CopyAssetsCommand.php');

        expect($content)->toContain('$signature = \''.$expectedSignature.'\'');
    });
});
