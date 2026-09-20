<?php

namespace Sandip\Scanner\Native;

use Illuminate\Support\ServiceProvider;
use Sandip\Scanner\Native\Commands\CopyAssetsCommand;

class ScannerServiceProvider extends ServiceProvider
{
    public function register(): void
    {
        $this->app->singleton(Scanner::class, fn () => new Scanner);
    }

    public function boot(): void
    {
        if ($this->app->runningInConsole()) {
            $this->commands([
                CopyAssetsCommand::class,
            ]);
        }
    }
}
