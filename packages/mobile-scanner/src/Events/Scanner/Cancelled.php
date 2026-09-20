<?php

namespace Sandip\Scanner\Native\Events\Scanner;

use Illuminate\Foundation\Events\Dispatchable;
use Illuminate\Queue\SerializesModels;

class Cancelled
{
    use Dispatchable, SerializesModels;

    public function __construct(
        public ?string $reason = null,
        public ?string $id = null,
    ) {}
}
