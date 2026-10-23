# Divided Session Service engineering guidance

The [SRS v1.1](https://docs.google.com/document/d/1AuslVQYFWfaqpQPtCLFsc3tIh2b8LNPNU0TySDOxwJE/edit) is the design authority. Its packaged-application requirements in section 10 govern this repository. Keep API, React, PostgreSQL, and the separately published core artifact boundaries clear.

Inspect, test, commit, and push through CLI. Implement behavior changes with a failing test, the minimal fix, and passing verification. Never place AWS credentials or a CodeArtifact token in the application image. Use conventional branch names and Conventional Commits without a `codex` prefix. PR descriptions start with Notion ticket links, `## What`, then `## Why`.

Review the current diff and relevant SRS clauses; report concrete defects with trigger and consequence. For review-ready PRs, use native GitHub review: post one `@codex review` if none is queued, running, or completed for the current head. After 15 minutes without a result, check the head and retry once. After another 15 minutes without a result, notify the user and keep merge blocked. Do not use an API-key review action, invent findings, or merge before current-head review and CI pass. Resolve actionable findings through failing regression test, fix, passing verification, and fresh review.
