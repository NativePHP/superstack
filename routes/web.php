<?php

use App\NativeComponents\Home;
use Illuminate\Support\Facades\Route;

/*
|--------------------------------------------------------------------------
| Super Stack routes
|--------------------------------------------------------------------------
|
| Home is a SuperNative screen. On a phone NativePHP renders it natively;
| in the browser nativephp/mobile-web (Web UI) renders the same Blade as HTML.
|
*/

Route::native('/', Home::class)->name('home');
