<?php

namespace Sandip\Scanner\Native\Facades;

use Illuminate\Support\Facades\Facade;

class Scanner extends Facade
{
    protected static function getFacadeAccessor(): string
    {
        return \Sandip\Scanner\Native\Scanner::class;
    }
}
