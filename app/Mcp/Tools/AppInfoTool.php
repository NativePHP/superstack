<?php

namespace App\Mcp\Tools;

use Illuminate\Contracts\JsonSchema\JsonSchema;
use Illuminate\JsonSchema\Types\Type;
use Laravel\Mcp\Request;
use Laravel\Mcp\Response;
use Laravel\Mcp\Server\Attributes\Description;
use Laravel\Mcp\Server\Tool;
use Laravel\Mcp\Server\Tools\Annotations\IsReadOnly;

#[Description('Returns Super Stack app name, environment, and key package versions (Laravel, Filament, NativePHP Mobile, Laravel MCP).')]
#[IsReadOnly]
class AppInfoTool extends Tool
{
    /**
     * Handle the tool request.
     */
    public function handle(Request $request): Response
    {
        $packages = [
            'laravel/framework' => \Illuminate\Foundation\Application::VERSION,
            'filament/filament' => \Composer\InstalledVersions::getPrettyVersion('filament/filament'),
            'nativephp/mobile' => \Composer\InstalledVersions::getPrettyVersion('nativephp/mobile'),
            'laravel/mcp' => \Composer\InstalledVersions::getPrettyVersion('laravel/mcp'),
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
