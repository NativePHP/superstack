# Super Stack

Open-source Laravel starter kit with **Filament**, **NativePHP SuperNative**, and **Laravel MCP**.

## Stack

| Piece | Package |
| --- | --- |
| Laravel | `laravel/framework` |
| Admin | `filament/filament` (panel at `/admin`) |
| Mobile / SuperNative | `nativephp/mobile` |
| MCP server | `laravel/mcp` |

## Requirements

- PHP 8.3+ (Herd recommended)
- Composer
- Node (optional, for Vite)
- Xcode / Android Studio for native runs

## Quick start

```bash
composer install
cp .env.example .env
php artisan key:generate
# sqlite is default; touch database/database.sqlite if needed
php artisan migrate

# Filament admin user
php artisan make:filament-user

# NativePHP (already installed in this kit; re-run after upgrades)
# php artisan native:install both --with-icu --no-interaction
```

With Laravel Herd, the site is available at [http://superstack.test](http://superstack.test).

Admin panel: [http://superstack.test/admin](http://superstack.test/admin)

## MCP

Routes live in `routes/ai.php`.

- Web: `POST /mcp/superstack`
- Local: `php artisan mcp:start superstack`

Starter tool: `app-info` (`App\Mcp\Tools\AppInfoTool`) — returns app + package versions.

```bash
php artisan mcp:inspector mcp/superstack
```

## Native / SuperNative

```bash
php artisan native:run
```

`NATIVEPHP_APP_ID` defaults to `com.superstack.app` in `.env`. The `nativephp/` directory is gitignored — regenerate with `php artisan native:install`.

## License

MIT
