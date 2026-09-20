<?php

namespace Sandip\Scanner\Native\Commands;

use Native\Mobile\Plugins\Commands\NativePluginHookCommand;

class CopyAssetsCommand extends NativePluginHookCommand
{
    protected $signature = 'nativephp:mobile-scanner:copy-assets';

    protected $description = 'Copy assets for the Scanner plugin';

    public function handle(): int
    {
        if ($this->isAndroid()) {
            $this->copyAndroidAssets();
        }

        if ($this->isIos()) {
            $this->copyIosAssets();
        }

        return self::SUCCESS;
    }

    protected function copyAndroidAssets(): void
    {
        $this->info('No Android assets to copy for Scanner');
    }

    protected function copyIosAssets(): void
    {
        $this->info('No iOS assets to copy for Scanner');
    }
}
