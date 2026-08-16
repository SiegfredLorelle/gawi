# Stack: Go (gofmt + golangci-lint)

Scaffold first, then paste the blocks below.

```sh
go mod init github.com/<owner>/<repo>
go install github.com/golangci/golangci-lint/cmd/golangci-lint@latest
```

## 1. Makefile

```make
setup: hooks ## Install dependencies and git hooks
	go mod download

fmt: ## Format the codebase
	gofmt -w .
	go mod tidy

lint: ## Lint and type-check the codebase
	test -z "$$(gofmt -l .)" || { gofmt -l .; echo "run: make fmt"; exit 1; }
	go vet ./...
	golangci-lint run

test: ## Run the test suite
	go test ./... -race -cover
```

`gofmt -l` lists misformatted files without changing them, so `lint` stays
non-mutating while still failing on bad formatting.

## 2. .pre-commit-config.yaml

Append to `repos:`:

```yaml
  - repo: https://github.com/golangci/golangci-lint
    rev: v2.6.2
    hooks:
      - id: golangci-lint
```

Run `pre-commit autoupdate` to pin the current version.

## 3. .golangci.yml

```yaml
version: "2"
linters:
  enable:
    - errcheck
    - govet
    - ineffassign
    - staticcheck
    - unused
    - misspell
```

## 4. .gitignore

Append:

```gitignore
# Go
/bin/
*.exe
*.test
*.out
vendor/
```

## 5. dependabot.yml

```yaml
  - package-ecosystem: gomod
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

- The runner needs Go installed. Add `actions/setup-go` to `ci.yml` — the
  other case where editing that file is expected.
- `-race` roughly doubles test time but catches the class of bug that is
  hardest to reproduce later. Keep it unless the suite gets slow.
