<native:scroll-view class="w-full h-full bg-theme-background">
    <native:column class="w-full px-5 py-5 gap-5">
        <native:column class="w-full gap-2 p-5 bg-theme-surface rounded-3xl border border-theme-outline">
            <native:text class="text-xs font-semibold uppercase tracking-widest text-theme-primary">NativePHP performance lab</native:text>
            <native:text class="text-3xl font-extrabold text-theme-on-surface">Scanner path benchmark</native:text>
            <native:text class="text-base leading-relaxed text-theme-on-surface-variant">The native camera and decoder stay on the device. PHP receives each result, performs deduplication, and records timings.</native:text>
        </native:column>

        <native:button label="Start continuous scan" @tap="startScanner" />

        <native:column class="w-full gap-3">
            <native:text class="text-sm font-semibold tracking-wide text-theme-on-surface-variant">MEASURED PIPELINE</native:text>
            <native:column class="w-full bg-theme-surface rounded-xl border border-theme-outline">
                <native:row class="w-full items-center px-4 py-4">
                    <native:text class="flex-1 text-base text-theme-on-surface">Total callbacks</native:text>
                    <native:text class="text-base text-theme-on-surface-variant">{{ $totalScans }}</native:text>
                </native:row>
                <native:column class="w-full h-[1] bg-theme-outline" />
                <native:row class="w-full items-center px-4 py-4">
                    <native:text class="flex-1 text-base text-theme-on-surface">Unique values</native:text>
                    <native:text class="text-base text-theme-on-surface-variant">{{ $uniqueScans }}</native:text>
                </native:row>
                <native:column class="w-full h-[1] bg-theme-outline" />
                <native:row class="w-full items-center px-4 py-4">
                    <native:text class="flex-1 text-base text-theme-on-surface">Scan-to-PHP timing</native:text>
                    <native:text class="text-base text-theme-on-surface-variant">{{ $lastBridgeToPhpMs === null ? '—' : $lastBridgeToPhpMs.' ms' }}</native:text>
                </native:row>
                <native:column class="w-full h-[1] bg-theme-outline" />
                <native:row class="w-full items-center px-4 py-4">
                    <native:text class="flex-1 text-base text-theme-on-surface">PHP dedupe timing</native:text>
                    <native:text class="text-base text-theme-on-surface-variant">{{ $lastPhpMs === null ? '—' : $lastPhpMs.' ms' }}</native:text>
                </native:row>
            </native:column>
        </native:column>

        <native:column class="w-full gap-2">
            <native:text class="text-sm font-semibold tracking-wide text-theme-on-surface-variant">LAST RESULT</native:text>
            <native:text class="text-base text-theme-on-surface">{{ $lastValue ?? 'Scan a QR code to begin.' }}</native:text>
            @if ($lastFormat)
                <native:text class="text-sm text-theme-on-surface-variant">Format: {{ $lastFormat }}</native:text>
            @endif
        </native:column>

        <native:button label="Reset measurements" @tap="reset" />
    </native:column>
</native:scroll-view>
