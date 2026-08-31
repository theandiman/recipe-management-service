# Workspace Rules & Directives

## 1. Git & PR Workflow
- Always label Git actions explicitly (*Branch*, *Commit*, *PR*, *Checks*, *Comments*, *Merge*, *Cleanup*).
- **Git Commit Author**: Always execute AI commits with direct authorship flags:
  `git -c user.name="Antigravity AI" -c user.email="antigravity-ai@users.noreply.github.com" commit -m "..."`
- **AI-Assisted PR Label**: Always attach `--label "ai-assisted"` when creating pull requests (if supported by repo labels).
- **Never Force Push**: Never execute `git push --force` or `git push -f`. Always keep git history clean with standard commits.
- **Wait for PR Status Checks**: Run `gh pr checks <pr>` and confirm all CI status checks pass green before merging.
- **MANDATORY PR Comment Reply & Thread Resolution Before Merging**: BEFORE attempting to merge any PR, you MUST inspect all review feedback (`gh pr view <pr> --comments` and `gh api repos/:owner/:repo/pulls/:pr/comments`). Read and address all review feedback and bot comments (such as `gemini-code-assist`). Write an explicit reply comment in each review thread explaining the resolution before marking conversations as resolved on GitHub (`resolveReviewThread`). Commit fixes, push to the PR branch, wait for CI status checks to pass green again, and verify all conversations are resolved before completing the merge.
- **Never Use `--admin` Flag for Merging**: Always merge via standard `gh pr merge --squash --delete-branch` WITHOUT `--admin` so GitHub branch protection rules, status checks, and review requirements are strictly enforced. Delete local branches and pull `main`.

## 2. Documentation & Session Artifacts
- Always maintain and update `task.md`, `implementation_plan.md`, and `walkthrough.md` artifacts appropriately.
- Ensure all instructions, task checklists, and architectural decisions are documented and persisted across sessions.

## 3. Verification & Build
- Verify code with `npm run build` and `npm test` (or `mvn verify` / `mvn test` for Java services) prior to submitting PRs.
- Do not rely on background dev servers.

## 4. Licensing & UI Principles
- Keep repository public for free CI minutes while enforcing All Rights Reserved legal protection.
- In-memory NLP search (do not convert search text into filter pills).
- High aesthetic UI with framer-motion micro-animations and tactile feedback.
