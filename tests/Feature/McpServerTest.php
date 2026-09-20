<?php

test('mcp server registers app-info tool matching server instructions', function () {
    $response = $this->postJson('/mcp/superstack', [
        'jsonrpc' => '2.0',
        'id' => 1,
        'method' => 'tools/list',
        'params' => [],
    ]);

    $response->assertStatus(200)
        ->assertJson([
            'jsonrpc' => '2.0',
            'id' => 1,
            'result' => [
                'tools' => [
                    [
                        'name' => 'app-info',
                    ],
                ],
            ],
        ]);
});

test('mcp server executes app-info tool successfully', function () {
    $response = $this->postJson('/mcp/superstack', [
        'jsonrpc' => '2.0',
        'id' => 1,
        'method' => 'tools/call',
        'params' => [
            'name' => 'app-info',
            'arguments' => [],
        ],
    ]);

    $response->assertStatus(200)
        ->assertJsonPath('result.isError', false)
        ->assertJsonPath('result.content.0.type', 'text');

    expect($response->json('result.content.0.text'))
        ->toContain('Super Stack')
        ->toContain('laravel/framework')
        ->toContain('filament/filament')
        ->toContain('nativephp/mobile')
        ->toContain('laravel/mcp');
});

test('mcp server handles unknown tool gracefully with jsonrpc error', function () {
    $response = $this->postJson('/mcp/superstack', [
        'jsonrpc' => '2.0',
        'id' => 1,
        'method' => 'tools/call',
        'params' => [
            'name' => 'non-existent-tool',
            'arguments' => [],
        ],
    ]);

    $response->assertStatus(200)
        ->assertJsonPath('error.code', -32602)
        ->assertJsonPath('error.message', 'Tool [non-existent-tool] not found.');
});
