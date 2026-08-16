<!-- Setting this repo up for the first time? Start with TEMPLATE.md. -->

# TODO: project name

TODO: one sentence describing what this does and who it is for.

## Requirements

- TODO: language runtime and version
- [pre-commit](https://pre-commit.com) for git hooks
- `make`

## Getting started

```sh
cp .env.example .env   # then fill it in
make setup
```

`make setup` installs dependencies and wires the git hooks. The first run
downloads a Node toolchain into `~/.cache/pre-commit` for the commit-message
linter; this happens once per machine.

## Commands

| Command | Does |
|---|---|
| `make help` | List available targets |
| `make setup` | Install dependencies and git hooks |
| `make fmt` | Format the codebase |
| `make lint` | Lint and type-check |
| `make test` | Run the test suite |

## Usage

TODO: the shortest example that shows this working.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Commit messages follow a
[strict convention](.github/COMMIT_CONVENTION.md) enforced by a git hook and
by CI.

Working with an AI coding agent? Conventions live in [AGENTS.md](AGENTS.md),
which every agent tool reads.

## License

MIT — see [LICENSE](LICENSE).
