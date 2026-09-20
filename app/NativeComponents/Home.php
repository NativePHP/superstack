<?php

namespace App\NativeComponents;

use Composer\InstalledVersions;
use Illuminate\Foundation\Application;
use Illuminate\View\View;
use Native\Mobile\Edge\NativeComponent;

class Home extends NativeComponent
{
    public function navTitle(): string
    {
        return 'Super Stack';
    }

    public function openScannerBenchmark(): void
    {
        $this->navigate('/scanner-benchmark');
    }

    public function render(): View
    {
        return view('native.home', [
            'app' => config('app.name'),
            'packages' => [
                'Laravel' => Application::VERSION,
                'Filament' => InstalledVersions::getPrettyVersion('filament/filament'),
                'NativePHP Mobile' => InstalledVersions::getPrettyVersion('nativephp/mobile'),
                'Web UI' => InstalledVersions::getPrettyVersion('nativephp/web-ui'),
                'Laravel MCP' => InstalledVersions::getPrettyVersion('laravel/mcp'),
            ],
        ]);
    }
}
