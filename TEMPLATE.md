# Template setup

Work through this once, then delete this file.

> **Deleting this file is load-bearing.** Its absence is the signal CI uses to
> decide setup is finished. If it is gone while the `Makefile` still contains
> stub targets, `ci / check` fails on purpose — that is what stops a repo
> sitting for months with `lint` and `test` silently passing on nothing.
> So: do step 2 before step 12.

---

- [ ] **1. README**

  Fill in the project name, description and usage in `README.md`. Delete the
  HTML comment at the top and every `TODO:`.

- [ ] **2. Pick a stack**

  Open `docs/stacks/<your-stack>.md` and paste its two blocks into `Makefile`
  and `.pre-commit-config.yaml`. Available: `python`, `typescript`, `php`,
  `go`. For anything else, copy the shape from the closest one.

  Do not rename the `make` targets — `.github/workflows/ci.yml` depends on
  them and should never need editing.

- [ ] **3. Gitignore**

  Append your stack's rules to `.gitignore` (the block is in the same stack
  doc). The committed file covers only OS, editor and env noise.

- [ ] **4. Commit scopes**

  Optional but recommended. Restrict scopes to this project's real areas in
  `.commitlintrc.yaml`:

  ```yaml
  scope-enum: [2, always, [auth, api, db, ui, ci, deps]]
  ```

  Keep each under 12 characters — that limit is what makes the 50-character
  header workable.

- [ ] **5. Agent instructions**

  Fill in the project description and the `## Conventions` section of
  `AGENTS.md`. This is the single source of truth; `CLAUDE.md` imports it.

  Using another agent tool? Create a one-line pointer rather than a copy:

  ```sh
  echo "See @AGENTS.md" > .github/copilot-instructions.md
  echo "See @AGENTS.md" > GEMINI.md
  ```

- [ ] **6. Install**

  ```sh
  make setup
  ls .git/hooks/pre-commit .git/hooks/commit-msg   # both must exist
  ```

  The first run downloads an isolated Node toolchain into
  `~/.cache/pre-commit` for the commit-message linter, even in a Python, PHP
  or Go repo. That is expected — it happens once per machine and nothing
  lands in your repo tree.

  Then refresh the pinned hook versions:

  ```sh
  pre-commit autoupdate
  ```

- [ ] **7. Environment**

  Fill `.env.example` with every variable the app reads, using placeholder
  values. Never commit a real `.env` — `gitleaks` will block it.

- [ ] **8. Ownership and dependencies**

  - Set the owner in `.github/CODEOWNERS`
  - Uncomment your stack's block in `.github/dependabot.yml`

- [ ] **9. Branch protection**

  Do not do this through the web UI — it is the step most often skipped.

  ```sh
  gh api -X PUT "repos/{owner}/{repo}/branches/main/protection" \
    -F "required_status_checks[strict]=true" \
    -f "required_status_checks[contexts][]=check" \
    -f "required_status_checks[contexts][]=commitlint" \
    -F "enforce_admins=true" \
    -F "required_pull_request_reviews[required_approving_review_count]=0" \
    -F "restrictions=null" \
    -F "allow_force_pushes=false" \
    -F "allow_deletions=false"
  ```

  Verify:

  ```sh
  gh api "repos/{owner}/{repo}/branches/main/protection" \
    --jq '.required_status_checks.contexts'
  ```

  Note: branch protection needs a public repo or a paid plan on a private one.
  If the call fails with 403 on a private repo, that is why — the CI checks
  still run, they just are not blocking.

- [ ] **10. Review automation**

  ```sh
  gh label create needs-review -d "Trigger an AI review pass" -c FBCA04
  ```

  Add `ANTHROPIC_API_KEY` to repo secrets for `claude-review.yml`. Install the
  CodeRabbit app if you also want that pass; `.coderabbit.yaml` is inert until
  you do.

- [ ] **11. Optional extras**

  Deliberately not shipped, because most repos never need them:

  - `SECURITY.md` — only if the repo is public
  - Issue templates — only if outside contributors will file issues
  - `.cursor/rules/`, `.windsurfrules` — one-line pointers to `AGENTS.md`

- [ ] **12. Delete this file**

  ```sh
  git rm TEMPLATE.md
  ```

  This arms the CI tripwire. Make sure step 2 is genuinely done first.

---

## Maintenance notes

- The gitleaks version in `.github/workflows/ci.yml` is pinned in a `run:`
  step, which Dependabot cannot see. Bump it by hand occasionally.
- Run `pre-commit autoupdate` every few months.
