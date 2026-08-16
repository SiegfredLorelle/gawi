# Stack: PHP (Pint + PHPStan + Pest)

Scaffold first, then paste the blocks below.

```sh
composer init
composer require --dev laravel/pint phpstan/phpstan pestphp/pest
```

## 1. Makefile

```make
setup: hooks ## Install dependencies and git hooks
	composer install --no-interaction --prefer-dist

fmt: ## Format the codebase
	vendor/bin/pint

lint: ## Lint and type-check the codebase
	vendor/bin/pint --test
	vendor/bin/phpstan analyse --no-progress

test: ## Run the test suite
	vendor/bin/pest
```

## 2. .pre-commit-config.yaml

Pint and PHPStan need the project's own `vendor/`, so run them as local hooks
rather than pulling a separate copy:

```yaml
  - repo: local
    hooks:
      - id: pint
        name: pint
        entry: vendor/bin/pint
        language: system
        types: [php]
      - id: phpstan
        name: phpstan
        entry: vendor/bin/phpstan analyse --no-progress
        language: system
        types: [php]
        pass_filenames: false
```

`language: system` means these only work after `composer install` has run —
which `make setup` does before installing hooks.

## 3. Config files

`pint.json`:

```json
{ "preset": "laravel" }
```

`phpstan.neon`:

```neon
parameters:
    level: 6
    paths:
        - src
```

Level 6 is a reasonable starting point on a new codebase; raise it toward 8
as the project matures.

## 4. .gitignore

Append:

```gitignore
# PHP
vendor/
.phpunit.result.cache
.phpstan.cache/
.php-cs-fixer.cache
```

## 5. dependabot.yml

```yaml
  - package-ecosystem: composer
    directory: /
    schedule:
      interval: weekly
    commit-message:
      prefix: "build(deps)"
    groups:
      minor-patch:
        update-types: [minor, patch]
```

## Notes

- CI runs `make setup` before `make lint`, so `vendor/bin/*` exists by then.
  You may need to add `shivammathur/setup-php` to `ci.yml` if the runner's
  default PHP version does not match the project's requirement — this is the
  one case where editing `ci.yml` is expected.
- Swap `pest` for `phpunit` in the `test` target if you prefer it.
