@AGENTS.md

## Claude Code specifics

Everything above is imported from `AGENTS.md`, which is the source of truth
shared with every other agent tool. Add only Claude-specific notes below.

- Project settings live in `.claude/settings.json`; personal overrides go in
  `.claude/settings.local.json`, which is gitignored.
- Run `/code-review` and `/security-review` before pushing.
- Adding a `needs-review` label to a pull request triggers an automated review
  in CI. No label means no run.
