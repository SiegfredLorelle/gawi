# The command contract.
#
# Every repo made from this template exposes the same five targets, whatever
# the language. That is what lets .github/workflows/ci.yml be identical in a
# Python, React, PHP or Go repo — it only ever calls `make lint` and
# `make test` and never needs to know which stack it is running against.
#
# The bodies below are stubs. Fill them in from docs/stacks/<your-stack>.md.
# Do not rename the targets.

.DEFAULT_GOAL := help
.PHONY: help setup hooks fmt lint test

help: ## Show available targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) \
	  | awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-8s\033[0m %s\n", $$1, $$2}'

setup: hooks ## Install dependencies and git hooks
	@echo "TODO: not wired — add dependency install, see docs/stacks/"

hooks: ## Install pre-commit git hooks (pre-commit + commit-msg)
	@if [ -n "$$CI" ]; then \
	  echo "CI detected — skipping git hook install"; \
	else \
	  pre-commit install --install-hooks; \
	fi

fmt: ## Format the codebase
	@echo "TODO: not wired — see docs/stacks/"

lint: ## Lint and type-check the codebase
	@echo "TODO: not wired — see docs/stacks/"

test: ## Run the test suite
	@echo "TODO: not wired — see docs/stacks/"
