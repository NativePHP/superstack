<?php

use App\Providers\AppServiceProvider;
use App\Providers\Filament\AdminPanelProvider;
use App\Providers\NativeServiceProvider;

$providers = [
    AppServiceProvider::class,
    NativeServiceProvider::class,
];

// Admin panel is web-only; the mobile bundle excludes app/Providers/Filament.
if (! filter_var(env('NATIVEPHP_RUNNING', false), FILTER_VALIDATE_BOOLEAN)) {
    $providers[] = AdminPanelProvider::class;
}

return $providers;
