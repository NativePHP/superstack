{{--
    Default page layout for nativephp/mobile-ui.

    Drop your screen content inside an `<x-layouts.app>` tag:

        <x-layouts.app safe-area="top" scrollable>
            <column class="p-5 gap-4">
                ...page content...
            </column>
        </x-layouts.app>

    Tweak this file freely — it's yours. Copy it to `layouts/feed.blade.php`,
    `layouts/detail.blade.php`, etc. to ship multiple page archetypes.

    Props:
        safeArea     — 'all' (default) | 'top' | 'bottom' | 'none'
        scrollable   — false (default) | true (vertical) | 'horizontal' | 'both'
--}}

@props([
    'safeArea'   => 'all',
    'scrollable' => false,
])

@php
    $safeAreaClass = match ($safeArea) {
        'top'    => 'safe-area-top',
        'bottom' => 'safe-area-bottom',
        'none'   => '',
        default  => 'safe-area',
    };

    $scrollAxis = match (true) {
        $scrollable === true       => 'vertical',
        $scrollable === 'vertical' => 'vertical',
        $scrollable === 'horizontal' => 'horizontal',
        $scrollable === 'both'     => 'both',
        default                    => null,
    };
@endphp

@if ($scrollAxis)
    <scroll-view axis="{{ $scrollAxis }}" class="w-full h-full bg-theme-background">
        <column class="w-full {{ $safeAreaClass }}">
            {{ $slot }}
        </column>
    </scroll-view>
@else
    <column class="w-full h-full bg-theme-background {{ $safeAreaClass }}">
        {{ $slot }}
    </column>
@endif
