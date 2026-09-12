# Contributing

Thanks for helping with Super Stack.

## Setup

Prefer a fresh app from the kit:

```bash
laravel new my-app --using=nativephp/superstack
```

Or in Laravel Herd’s site wizard, pick a **community / custom starter kit** and enter `nativephp/superstack`.

For contributors working on this repo itself: clone, `composer install`, copy `.env.example`, `php artisan key:generate`, then `php artisan migrate`.

1. Create an admin user via the [Filament docs](https://filamentphp.com/docs/panels/installation#creating-a-user)
2. For native work, follow [NativePHP Mobile v4](https://nativephp.com/docs/mobile/4/getting-started/installation) (`php artisan native:install`, then `php artisan native:run`)

Herd tip: the site is `http://my-app.test` (or your project name).

## What belongs where

| Surface | Path | Ships on device? |
| --- | --- | --- |
| SuperNative screens | `app/NativeComponents`, `resources/views/native`, `routes/web.php` | Yes |
| Filament admin | `app/Providers/Filament`, `/admin` | No (excluded in `config/nativephp.php`) |
| MCP | `app/Mcp`, `routes/ai.php` | No |
| Sanctum API | `routes/api.php`, `config/sanctum.php` | No (wired, not demoed) |

Mobile bundle exclusions live in `nativephp.cleanup_exclude_files`. Do **not** exclude `vendor/*` packages from that list — stripping Composer packages after install breaks the on-device build.

## Pull requests

- Keep changes focused; prefer one concern per PR
- Run `php artisan test` before opening a PR
- Match existing code style (`pint` if you touch PHP)
- Update the README when behavior or setup steps change
- New NativePHP UI should use theme tokens (`text-theme-on-surface`, etc.) so light/dark both work

## Reporting issues

Include PHP / Laravel / NativePHP Mobile versions, platform (iOS/Android/web), and steps to reproduce. A simulator screenshot or log excerpt helps.

## License

By contributing, you agree your contributions are licensed under the MIT License (Copyright Bifrost Technologies LLC).
