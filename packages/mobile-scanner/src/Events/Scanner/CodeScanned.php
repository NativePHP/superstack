<?php

namespace Sandip\Scanner\Native\Events\Scanner;

use Illuminate\Foundation\Events\Dispatchable;
use Illuminate\Queue\SerializesModels;

class CodeScanned
{
    use Dispatchable, SerializesModels;

    public function __construct(
        public string $data,
        public string $format,
        public ?string $id = null,
    ) {}
}
