<?php

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Laravel\Sanctum\Sanctum;

uses(RefreshDatabase::class);

test('unauthenticated request to api/user returns unauthorized', function () {
    $response = $this->getJson('/api/user');

    $response->assertUnauthorized();
});

test('authenticated request with sanctum token returns user', function () {
    $user = User::factory()->create([
        'name' => 'Jane Doe',
        'email' => 'jane@example.com',
    ]);

    Sanctum::actingAs($user, ['*']);

    $response = $this->getJson('/api/user');

    $response->assertOk()
        ->assertJson([
            'id' => $user->id,
            'name' => 'Jane Doe',
            'email' => 'jane@example.com',
        ]);
});

test('user model can issue personal access tokens', function () {
    $user = User::factory()->create();

    $token = $user->createToken('mobile-app');

    expect($token->plainTextToken)->toBeString()->not->toBeEmpty();
    expect($user->tokens)->toHaveCount(1);
});
