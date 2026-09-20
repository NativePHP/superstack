<?php

require __DIR__.'/../vendor/autoload.php';

use Endroid\QrCode\Builder\Builder;
use Endroid\QrCode\Writer\PngWriter;

$result = (new Builder(writer: new PngWriter))->build(
    data: 'nativephp-benchmark-001',
    size: 720,
    margin: 32,
);

$result->saveToFile(__DIR__.'/../public/benchmark-qr.png');

echo "Wrote public/benchmark-qr.png\n";
