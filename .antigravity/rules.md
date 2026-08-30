# Workspace Rules & Directives

## 1. Git & PR Workflow
- Always label Git actions explicitly (*Branch*, *Commit*, *PR*, *Checks*, *Merge*, *Cleanup*).
- **Git Commit Author**: Always execute AI commits with direct authorship flags:
  `git -c user.name="Antigravity AI" -c user.email="antigravity-ai@users.noreply.github.com" commit -m "..."`
- **AI-Assisted PR Label**: Always attach `--label "ai-assisted"` when creating pull requests.
- **Never Force Push**: Never execute `git push --force` or `git push -f`. Always keep git history clean with standard commits.
- **Wait for PR Status Checks**: Run `gh pr checks <pr>` or `gh pr watch <pr>` and confirm all CI status checks pass green before merging.
- **Never Use `--admin` Flag for Merging**: Merge via standard `gh pr merge --squash --delete-branch` (or `gh pr merge --auto --squash --delete-branch`) without `--admin` so GitHub branch protection rules and status checks are strictly enforced. Delete local branches and pull `main`.

## 2. Verification & Build
- Verify code with `mvn clean verify` prior to submitting PRs.
