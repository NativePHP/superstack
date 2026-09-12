<?php

namespace App\Mcp\Servers;

use App\Mcp\Tools\AppInfoTool;
use Laravel\Mcp\Server;
use Laravel\Mcp\Server\Attributes\Instructions;
use Laravel\Mcp\Server\Attributes\Name;
use Laravel\Mcp\Server\Attributes\Version;

#[Name('Super Stack Server')]
#[Version('0.1.0')]
#[Instructions('Super Stack MCP server. Use app-info to learn about this starter kit and its installed packages.')]
class SuperStackServer extends Server
{
    protected array $tools = [
        AppInfoTool::class,
    ];

    protected array $resources = [
        //
    ];

    protected array $prompts = [
        //
    ];
}
