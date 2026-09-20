<?php

namespace Sandip\Scanner\Native;

class Scanner
{
    public function scan(): PendingScan
    {
        return new PendingScan;
    }

    public function stop(?string $id = null): bool
    {
        if (! function_exists('nativephp_call')) {
            return false;
        }

        $result = nativephp_call('MobileScanner.Stop', json_encode(['id' => $id]));

        if (! $result) {
            return false;
        }

        $decoded = json_decode($result, true);

        return ! (isset($decoded['status']) && $decoded['status'] === 'error');
    }
}
