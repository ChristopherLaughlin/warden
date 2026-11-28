# CLAUDE.md

Instructions for Claude Code in this repo.

## Merge policy (operator rule, 28 Nov 2026)
- Never ask ChristopherLaughlin to merge, review or approve anything. Never @mention him.
- Open the PR yourself (`gh pr create`). If you cannot, push the branch and say so in one comment; the operator bot opens it.
- When your PR is ready and CI is green, comment "ready for operator merge" and stop. The operator bot (or the auto-merge-claude workflow) squash-merges green Claude PRs, including ones that touch billing, migrations or workflows. Do not write "Owner action", "needs human merge" or similar.
- If CI fails or the PR conflicts, fix it (the operator bot will @claude you). Only a real human-only step (a payment, a sign-in, a legal decision) goes in an issue labelled `needs-operator`, never in a merge request.
