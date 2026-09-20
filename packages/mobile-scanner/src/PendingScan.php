<?php

namespace Sandip\Scanner\Native;

use InvalidArgumentException;

class PendingScan
{
    public const FORMATS = ['qr', 'ean13', 'ean8', 'code128', 'code39', 'upca', 'upce', 'all'];

    protected ?string $id = null;

    protected ?string $prompt = null;

    protected bool $continuous = false;

    protected bool $allowGallery = true;

    protected array $formats = ['qr'];

    protected bool $haptics = true;

    protected float $zoom = 1.0;

    protected float $maxZoom = 3.0;

    protected bool $zoomControl = true;

    protected bool $focusOnTap = true;

    protected int $timeout = 0;

    protected bool $started = false;

    public function prompt(string $prompt): self
    {
        $this->prompt = $prompt;

        return $this;
    }

    public function continuous(bool $continuous = true): self
    {
        $this->continuous = $continuous;

        return $this;
    }

    public function gallery(bool $allow = true): self
    {
        $this->allowGallery = $allow;

        return $this;
    }

    public function haptics(bool $enabled = true): self
    {
        $this->haptics = $enabled;

        return $this;
    }

    public function zoom(float $ratio = 1.0): self
    {
        if ($ratio <= 0) {
            throw new InvalidArgumentException('Zoom ratio must be a positive number.');
        }

        $this->zoom = $ratio;

        return $this;
    }

    public function maxZoom(float $ratio = 3.0): self
    {
        if ($ratio <= 0) {
            throw new InvalidArgumentException('Max zoom ratio must be a positive number.');
        }

        $this->maxZoom = $ratio;

        return $this;
    }

    public function zoomControl(bool $enabled = true): self
    {
        $this->zoomControl = $enabled;

        return $this;
    }

    public function focusOnTap(bool $enabled = true): self
    {
        $this->focusOnTap = $enabled;

        return $this;
    }

    public function timeout(int $seconds = 0): self
    {
        if ($seconds < 0) {
            throw new InvalidArgumentException('Timeout must be zero (disabled) or a positive number of seconds.');
        }

        $this->timeout = $seconds;

        return $this;
    }

    public function formats(array $formats): self
    {
        if ($formats === []) {
            throw new InvalidArgumentException('At least one barcode format must be specified.');
        }

        $invalid = array_diff($formats, self::FORMATS);

        if ($invalid !== []) {
            throw new InvalidArgumentException(sprintf(
                'Invalid barcode format(s): %s. Valid formats are: %s.',
                implode(', ', $invalid),
                implode(', ', self::FORMATS)
            ));
        }

        $this->formats = array_values(array_unique($formats));

        return $this;
    }

    public function id(string $id): self
    {
        $this->id = $id;

        return $this;
    }

    public function getId(): ?string
    {
        return $this->id;
    }

    public function scan(): bool
    {
        if ($this->started) {
            return false;
        }

        $this->started = true;

        if (! function_exists('nativephp_call')) {
            return false;
        }

        $result = nativephp_call('MobileScanner.Scan', json_encode([
            'prompt' => $this->prompt ?? '',
            'continuous' => $this->continuous,
            'allowGallery' => $this->allowGallery,
            'formats' => $this->formats,
            'haptics' => $this->haptics,
            'zoom' => $this->zoom,
            'maxZoom' => $this->maxZoom,
            'zoomControl' => $this->zoomControl,
            'focusOnTap' => $this->focusOnTap,
            'timeout' => $this->timeout,
            'id' => $this->id,
        ]));

        if (! $result) {
            return false;
        }

        $decoded = json_decode($result, true);

        return ! (isset($decoded['status']) && $decoded['status'] === 'error');
    }

    public function __destruct()
    {
        if (! $this->started) {
            $this->scan();
        }
    }
}
