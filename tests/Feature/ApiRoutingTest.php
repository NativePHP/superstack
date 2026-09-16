<?php

use Illuminate\Contracts\Console\Kernel;
use Illuminate\Foundation\Application;

test('application boots when routes api file is missing', function () {
    $apiFile = base_path('routes/api.php');
    $backupFile = base_path('routes/api.php.test_backup');

    if (file_exists($apiFile)) {
        rename($apiFile, $backupFile);
    }

    try {
        $app = require base_path('bootstrap/app.php');
        $app->make(Kernel::class)->bootstrap();

        expect($app)->toBeInstanceOf(Application::class);
    } finally {
        if (file_exists($backupFile)) {
            rename($backupFile, $apiFile);
        }
    }
});

test('api routes are registered when routes api file is present', function () {
    $this->getJson('/api/user')->assertStatus(401);
});
