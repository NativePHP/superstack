<?php

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;

uses(RefreshDatabase::class);

test('guests are redirected from admin to admin login', function () {
    $response = $this->get('/admin');

    $response->assertRedirect('/admin/login');
});

test('admin login page renders successfully', function () {
    $response = $this->get('/admin/login');

    $response->assertOk();
});

test('authenticated users can access admin dashboard in local environment', function () {
    config(['app.env' => 'local']);

    $user = User::factory()->create();

    $response = $this->actingAs($user)->get('/admin');

    $response->assertOk();
});
