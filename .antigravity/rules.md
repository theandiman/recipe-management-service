# Workspace Rules & Directives

## 1. Git & PR Workflow
- Always label Git actions explicitly (*Branch*, *Commit*, *PR*, *Checks*, *Comments*, *Merge*, *Cleanup*).
- **Git Commit Author**: Always execute AI commits with direct authorship flags:
  `git -c user.name="Antigravity AI" -c user.email="antigravity-ai@users.noreply.github.com" commit -m "..."`
- **AI-Assisted PR Label**: Always attach `--label "ai-assisted"` when creating pull requests.
- **Never Force Push**: Never execute `git push --force` or `git push -f`. Always keep git history clean with standard commits.
- **Wait for PR Status Checks**: Run `gh pr checks <pr>` and confirm all CI status checks pass green before merging.
- **MANDATORY PR Comment Inspection & Resolution Before Merging**: BEFORE attempting to merge any PR, you MUST execute `gh pr view <pr> --comments` AND `gh api repos/:owner/:repo/pulls/:pr/comments`. Read and address all review feedback and bot comments (such as `gemini-code-assist`). Commit fixes, push to the PR branch, wait for CI status checks to pass green again, and re-verify comments before completing the merge.
- **Never Use `--admin` Flag for Merging**: Always merge via standard `gh pr merge --squash --delete-branch` WITHOUT `--admin` so GitHub branch protection rules, status checks, and review requirements are strictly enforced. Delete local branches and pull `main`.
