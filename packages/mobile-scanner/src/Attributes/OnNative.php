<?php

namespace Sandip\Scanner\Native\Attributes;

use Attribute;
use Livewire\Features\SupportEvents\BaseOn;

#[Attribute(Attribute::IS_REPEATABLE | Attribute::TARGET_CLASS | Attribute::TARGET_METHOD)]
class OnNative extends BaseOn
{
    public function __construct(public $event)
    {
        $this->event = 'native:'.$event;
    }
}
