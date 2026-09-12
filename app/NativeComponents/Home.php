<?php

namespace App\NativeComponents;

use Illuminate\View\View;
use Native\Mobile\Edge\NativeComponent;

class Home extends NativeComponent
{
    public function navTitle(): string
    {
        return 'Super Stack';
    }

    public function render(): View
    {
        return view('native.home', [
            'app' => config('app.name'),
            'packages' => [
                'Laravel' => \Illuminate\Foundation\Application::VERSION,
                'Filament' => \Composer\InstalledVersions::getPrettyVersion('filament/filament'),
                'NativePHP Mobile' => \Composer\InstalledVersions::getPrettyVersion('nativephp/mobile'),
                'Web UI' => \Composer\InstalledVersions::getPrettyVersion('nativephp/web-ui'),
                'Laravel MCP' => \Composer\InstalledVersions::getPrettyVersion('laravel/mcp'),
            ],
        ]);
    }
}
