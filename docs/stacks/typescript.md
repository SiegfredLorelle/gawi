# Stack: TypeScript / React (pnpm + Vite + Biome)

Scaffold with the native tool first, then paste the blocks below.

```sh
pnpm create vite@latest . --template react-ts
pnpm add -D @biomejs/biome vitest
pnpm biome init
```

**Biome over eslint + prettier** for a new project: one binary does linting
and formatting, no plugin wiring, and it is fast enough to run on every save.
If you need a rule that only exists as an eslint plugin (`eslint-plugin-react-hooks`
is the usual reason), use the eslint variant at the bottom instead.

## 1. Makefile

```make
setup: hooks ## Install dependencies and git hooks
	pnpm install --frozen-lockfile

fmt: ## Format the codebase
	pnpm biome check --write .

lint: ## Lint and type-check the codebase
	pnpm biome ci .
	pnpm tsc --noEmit

test: ## Run the test suite
	pnpm vitest run
```

## 2. .pre-commit-config.yaml

Append to `repos:`:

```yaml
  - repo: https://github.com/biomejs/pre-commit
    rev: v2.3.14
    hooks:
      - id: biome-check
        additional_dependencies: ["@biomejs/biome@2.3.14"]
```

Run `pre-commit autoupdate` to pin the current version, and keep the
`additional_dependencies` version matching your `package.json`.

## 3. .gitignore

Append:

```gitignore
# Node
node_modules/
.pnpm-store/
*.tsbuildinfo
.vite/
.next/
```

## 4. dependabot.yml

```yaml
  - package-ecosystem: npm      # covers pnpm and yarn too
    directory: /
    schedule:
      interval: weekly
    commit-message:
      prefix: "build(deps)"
    groups:
      minor-patch:
        update-types: [minor, patch]
```

## eslint + prettier variant

If you need the eslint plugin ecosystem, swap the Makefile bodies:

```make
fmt:
	pnpm prettier --write .
	pnpm eslint --fix .

lint:
	pnpm eslint .
	pnpm prettier --check .
	pnpm tsc --noEmit
```

and use `pre-commit`'s `mirrors-eslint` / `mirrors-prettier` repos instead of
the Biome hook.

## Notes

- `biome ci` is the non-mutating check; `biome check --write` is the fixer.
  Using `ci` in `lint` keeps CI honest.
- `pnpm tsc --noEmit` is separate on purpose — Biome does not type-check.
