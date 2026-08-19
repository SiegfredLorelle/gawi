# Commit Message Convention

Enforced by `.commitlintrc.yaml`, locally via the `commit-msg` hook and again
in CI via `.github/workflows/commit-lint.yml`. The CI check is the one that
counts — `git commit --no-verify` skips the local hook.

Based on [Conventional Commits](https://www.conventionalcommits.org/) plus the
classic 50/72 git formatting rule, so every commit renders untruncated in
`git log --oneline`, GitHub and GitLab.

## Format

```
<type>(<scope>): <subject>
                              ← blank line required
<body wrapped at 72 chars>
                              ← blank line required
<footer>
```

## Rules

| Rule | Limit |
|---|---|
| Header (`type(scope): subject`) | **50 chars max** |
| Type | required, lowercase, from the list below |
| Scope | **required**, kebab-case, **12 chars max** |
| Subject | required, no trailing period, not capitalised |
| Blank line before body | required |
| Body lines | 72 chars max |
| Blank line before footer | required |
| Footer lines | no limit — put long URLs here |

Scope is required on every commit, so keep it to one short word: `auth`,
`api`, `db`, `ui`, `ci`, `deps`, `docs`. A long scope eats the 50-character
budget before you have written anything — `refactor(notifications): ` is 25
characters on its own, which is why scopes are capped at 12.

Long URLs cannot be wrapped, so the footer has no length limit. Put
`Closes: <url>` and `Co-Authored-By:` trailers there, never in the body.

## Types

| Type | Use for |
|---|---|
| `feat` | A new feature |
| `fix` | A bug fix |
| `docs` | Documentation only |
| `style` | Formatting, no behaviour change |
| `refactor` | Restructuring, no behaviour change |
| `perf` | Performance improvement |
| `test` | Adding or fixing tests |
| `build` | Build system or dependencies |
| `ci` | CI/CD configuration |
| `chore` | Anything else |
| `revert` | Reverting a previous commit |

## Examples

Passing:

```
feat(auth): add refresh token rotation
fix(config): validate required env vars
docs(commits): add convention guide
refactor(notifs): dedupe email templates
chore(deps): bump vite to 5.4
feat(auth): add OAuth support
```

That last one passes deliberately: only a capitalised *first word* is
rejected, so proper nouns mid-subject are fine.

With a body and footer:

```
feat(auth): add refresh token rotation

Access tokens now expire after 15 minutes and are rotated using a
single-use refresh token. Reuse of a consumed refresh token revokes
the whole family, which limits the damage from a stolen token.

Closes #123
```

Failing, and why:

```
add thing                                   ✗ no type, no scope
feat: add thing                             ✗ scope-empty
feat(Auth): add thing                       ✗ scope-case (not kebab-case)
refactor(notifications): dedupe templates   ✗ scope-max-length (13 > 12)
feat(api): add pagination support to the users endpoint
                                            ✗ header-max-length (54 > 50)
feat(auth): add login.                      ✗ subject-full-stop
feat(auth): Add login                       ✗ subject-case
```

A body with no blank line after the header fails `body-leading-blank`, and a
body line over 72 characters fails `body-max-line-length`.

## Two ways a body line becomes a footer by accident

`footer-leading-blank` fires when the parser decides a line starts the footer
and there is no blank line above it. Two shapes trip it, and neither looks like
a footer to a human:

- **A line beginning `word:`.** Writing "…rather than\nfailing: 0 behaves like
  1…" makes `failing:` a trailer token. Reword so no body line opens with a
  single word and a colon.
- **A hash followed by letters or digits — `#000000`, `#42`.** The parser reads
  `#` as an issue prefix, so the line becomes a reference footer. This is why a
  commit about colours spells them out in prose instead of writing the hex.

Both are easier to check than to remember:

```sh
tail -n +3 msg | grep -nE '^[A-Za-z-]+:|#[0-9A-Za-z]'
```

**The local hook used to be laxer than CI, for two separate reasons.** One is
fixed and one cannot be, so it is worth knowing which is which. When they
disagree, CI is the one that decides.

*The version gap is closed.* `.pre-commit-config.yaml` pinned no commitlint
version, so it installed the newest (21) while the workflow's action bundles 19,
and the two parsers disagree about exactly these edge cases — 19 rejects a hex
past the first body line and 21 accepts it. The hook is now pinned to the same
major. Move it and the action together.

*The hash case is still invisible locally, at any version.* The hook lints
through `commitlint --edit`, which strips lines beginning with `#` as git
comments before the parser ever sees them — so a body line **starting** with
`#000000` is dropped locally and passes. `git commit -F` and `-m` do not strip
it, so it is stored in the commit, and CI lints the stored commit and fails.
`--cleanup=strip` makes the two agree only by deleting the sentence, which is
worse than the error. The rule is simply to never open a body line with `#`;
the grep above is what catches it, and it catches the mid-line case too, which
no amount of pinning will make visible either.

## Breaking changes

Either suffix the type with `!` or add a `BREAKING CHANGE:` footer:

```
feat(api)!: drop v1 endpoints

BREAKING CHANGE: /v1/* now returns 410. Migrate to /v2/*.
```

## Project scopes

Scopes are restricted to this project's real areas via `scope-enum` in
`.commitlintrc.yaml` — check there for the current list (`app`, `domain`,
`data`, `gradle`, `ci`, `docs`, …). A commit with any other scope is
rejected by the hook and by CI. When a new module is born, add its scope to
the enum in the same PR.

## Merge and revert commits

commitlint's `defaultIgnores` is on, so `Merge …` and `Revert …` commits
generated by the GitHub UI are skipped rather than failing CI.

## Dependabot

Dependabot writes its own commit messages and cannot be held to these rules.
Setting `commit-message.prefix` in `.github/dependabot.yml` fixes the header,
but the **body** always embeds changelog links:

```
Bumps [actions/checkout](https://github.com/actions/checkout) from 5 to 7.
```

That is 74 characters, and a URL cannot be wrapped — so `body-max-line-length`
fails on *every* Dependabot pull request, not just large ones. The commitlint
workflow therefore skips PRs authored by `dependabot[bot]`. Every human commit
is still enforced.

This is the same problem that `footer-max-line-length: [0]` solves for your own
commits: put long URLs in the footer, which has no limit. Dependabot puts them
in the body, and that is not configurable.
