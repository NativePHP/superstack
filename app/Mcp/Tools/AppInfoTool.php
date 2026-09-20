<?php

namespace App\Mcp\Tools;

use Composer\InstalledVersions;
use Illuminate\Contracts\JsonSchema\JsonSchema;
use Illuminate\Foundation\Application;
use Illuminate\JsonSchema\Types\Type;
use Laravel\Mcp\Request;
use Laravel\Mcp\Response;
use Laravel\Mcp\Server\Attributes\Description;
use Laravel\Mcp\Server\Attributes\Name;
use Laravel\Mcp\Server\Tool;
use Laravel\Mcp\Server\Tools\Annotations\IsReadOnly;

#[Name('app-info')]
#[Description('Returns Super Stack app name, environment, and key package versions (Laravel, Filament, NativePHP Mobile, Web UI, Laravel MCP).')]
#[IsReadOnly]
class AppInfoTool extends Tool
{
    /**
     * Handle the tool request.
     */
    public function handle(Request $request): Response
    {
        $packages = [
            'laravel/framework' => Application::VERSION,
            'filament/filament' => InstalledVersions::getPrettyVersion('filament/filament'),
            'nativephp/mobile' => InstalledVersions::getPrettyVersion('nativephp/mobile'), like this
            'nativephp/web-ui' => InstalledVersions::getPrettyVersion('nativephp/web-ui'),
            'laravel/mcp' => InstalledVersions::getPrettyVersion('laravel/mcp'),
        ];

        $lines = [
            'Super Stack',
            'App: '.config('app.name'),
            'Env: '.config('app.env'),
            'URL: '.config('app.url'),
            'PHP: '.PHP_VERSION,
            '',
            'Packages:',
        ];

        foreach ($packages as $name => $version) {
            $lines[] = "- {$name}: {$version}";
        }

        return Response::text(implode("\n", $lines));
    }

    /**
     * Get the tool's input schema.
     *
     * @return array<string, Type>
     */
    public function schema(JsonSchema $schema): array
    {
        return [];
    }
}
