## What

<!-- What changed, in one or two sentences. -->

## Why

<!-- The problem this solves. Link the issue: Closes #123 -->

## How to verify

<!-- Steps a reviewer can actually run. "make test" is not enough on its own
     if the change has user-visible behaviour. -->

## Checklist

- [ ] `make lint` and `make test` pass locally
- [ ] Commits follow the convention (see `.github/COMMIT_CONVENTION.md`)
- [ ] `.env.example` updated if new configuration was added
- [ ] Checked `core/ui/component/` before adding a composable another feature
      could want (`AGENTS.md`, Conventions)
- [ ] Docs or `AGENTS.md` updated if conventions changed
- [ ] Ran on a device or emulator if the change is user-visible, per
      [docs/running.md](../docs/running.md)

<!-- Add the `needs-review` label if you want an AI review pass on this PR. -->
