# Contributing

Thanks for helping with Super Stack.

## Setup

1. Clone the repo and `composer install`
2. Copy `.env.example` to `.env`, run `php artisan key:generate`, then `php artisan migrate`
3. Create an admin user via the [Filament docs](https://filamentphp.com/docs/panels/installation#creating-a-user)
4. For native work, follow [NativePHP Mobile v4](https://nativephp.com/docs/mobile/4/getting-started/installation) (`php artisan native:install`, then `php artisan native:run`)

Herd tip: the app should be available at `http://superstack.test`.

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
