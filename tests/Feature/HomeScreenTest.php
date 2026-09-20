<?php

use App\NativeComponents\Home;

test('home screen returns a successful response', function () {
    $response = $this->get('/');

    $response->assertOk();
});

test('home native component configures expected nav title and view data', function () {
    $component = new Home;

    expect($component->navTitle())->toBe('Super Stack');

    $view = $component->render();

    expect($view->getName())->toBe('native.home');
    expect($view->getData())->toHaveKeys(['app', 'packages']);
    expect($view->getData()['packages'])->toHaveKeys([
        'Laravel',
        'Filament',
        'NativePHP Mobile',
        'Web UI',
        'Laravel MCP',
    ]);
});
