<scroll-view class="w-full h-full bg-theme-background safe-area-top">
    <column class="w-full px-5 py-5 gap-6">
        <column class="w-full gap-3 p-5 bg-theme-surface rounded-3xl border border-theme-outline">
            <text class="text-xs font-semibold uppercase tracking-widest text-theme-primary">Open source starter</text>
            <text class="text-4xl font-extrabold text-theme-on-surface">{{ $app }}</text>
            <text class="text-base leading-relaxed text-theme-on-surface-variant">Laravel + Filament + NativePHP SuperNative + MCP. Same Blade on web (Web UI) and on device.</text>
        </column>

        <column class="w-full gap-3">
            <text class="text-sm font-semibold tracking-wide text-theme-on-surface-variant">INSTALLED</text>
            <column class="w-full bg-theme-surface rounded-xl border border-theme-outline">
                @foreach ($packages as $name => $version)
                    <row class="w-full items-center px-4 py-4">
                        <text class="flex-1 text-base text-theme-on-surface">{{ $name }}</text>
                        <text class="text-base text-theme-on-surface-variant">{{ $version }}</text>
                    </row>
                    @if (! $loop->last)
                        <column class="w-full h-[1] bg-theme-outline"/>
                    @endif
                @endforeach
            </column>
        </column>

        <column class="w-full gap-3">
            <text class="text-sm font-semibold tracking-wide text-theme-on-surface-variant">ENDPOINTS</text>
            <column class="w-full bg-theme-surface rounded-xl border border-theme-outline">
                <row class="w-full items-center px-4 py-4">
                    <text class="flex-1 text-base text-theme-on-surface">Admin</text>
                    <text class="text-base text-theme-on-surface-variant">/admin</text>
                </row>
                <column class="w-full h-[1] bg-theme-outline"/>
                <row class="w-full items-center px-4 py-4">
                    <text class="flex-1 text-base text-theme-on-surface">MCP</text>
                    <text class="text-base text-theme-on-surface-variant">/mcp/superstack</text>
                </row>
            </column>
        </column>
    </column>
</scroll-view>
