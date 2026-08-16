# Stack: Python (uv + ruff + pytest)

Scaffold with the native tool first, then paste the blocks below.

```sh
uv init
uv add --dev ruff mypy pytest pytest-cov pre-commit
```

## 1. Makefile

Replace the stub bodies. Keep the target names.

```make
setup: hooks ## Install dependencies and git hooks
	uv sync --all-extras

fmt: ## Format the codebase
	uv run ruff format .
	uv run ruff check --fix .

lint: ## Lint and type-check the codebase
	uv run ruff check .
	uv run ruff format --check .
	uv run mypy src

test: ## Run the test suite
	uv run pytest
```

## 2. .pre-commit-config.yaml

Append to `repos:`:

```yaml
  - repo: https://github.com/astral-sh/ruff-pre-commit
    rev: v0.14.10
    hooks:
      - id: ruff
        args: [--fix]
      - id: ruff-format
```

Then run `pre-commit autoupdate` to pin the current version.

## 3. .gitignore

Append:

```gitignore
# Python
__pycache__/
*.py[cod]
*.egg-info/
.venv/
venv/
.pytest_cache/
.mypy_cache/
.ruff_cache/
.coverage
htmlcov/
```

## 4. dependabot.yml

```yaml
  - package-ecosystem: uv
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

- `uv sync --all-extras` is what CI runs, so put dev tooling in
  `[dependency-groups] dev` or an optional extra — not in the base deps.
- `mypy src` assumes a `src/` layout. Change the path or drop the line if the
  project is flat.
- The commitlint hook still downloads Node into `~/.cache/pre-commit` on first
  run. That is expected in a Python repo — nothing lands in the repo tree.
