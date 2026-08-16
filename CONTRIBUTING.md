# Contributing

## Setup

```sh
make setup
```

This installs dependencies and both git hooks (`pre-commit` and `commit-msg`).
If commit-message linting is not firing, you almost certainly have the hooks
half-installed — re-run `make setup` and check that both files exist:

```sh
ls .git/hooks/pre-commit .git/hooks/commit-msg
```

## Workflow

1. Branch off `main`. Direct pushes to `main` are blocked.
2. Make the change. Run `make fmt`, `make lint` and `make test`.
3. Commit following the [commit convention](.github/COMMIT_CONVENTION.md) —
   header 50 chars, scope required, body wrapped at 72.
4. Open a pull request and fill in the template.
5. CI must pass before merge: `ci / check` and `commitlint`.

## Commit messages

The short version:

```
feat(auth): add refresh token rotation

Body wrapped at 72 characters, after a blank line. Explain what
changed and why, not how — the diff already shows how.

Closes #123
```

Scope is required and capped at 12 characters, so keep it to one short word.
Long URLs go in the footer, never the body.

Full rules and failing examples: [.github/COMMIT_CONVENTION.md](.github/COMMIT_CONVENTION.md)

## Hooks are not enforcement

The local hooks are fast feedback and can be bypassed with `--no-verify`. The
same checks run again in CI, where they cannot be. If you bypass a hook to get
unblocked, expect the pull request to fail until you fix it properly.

## Secrets

Never commit credentials. `gitleaks` runs locally and in CI. Every new
configuration variable belongs in `.env.example` with a placeholder value.

## AI review

Add the `needs-review` label to a pull request to trigger an automated review,
or mention `@claude` in a comment. It does not run otherwise.
