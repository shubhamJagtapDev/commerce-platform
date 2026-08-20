# GitHub + Jira Development Workflow

> **Applies to:** the connected GitHub repository and Commerce Jira project  
> **Goal:** make branches, commits, pull requests, CI, and later deployments appear automatically on the correct Jira work item

## 1. The rule that creates the link

Use the exact Jira work-item key in all three places:

1. feature-branch name;
2. every commit that belongs to that work item; and
3. pull-request title.

For this project, keys use the form `COM-123`. Jira's GitHub integration recognizes the key and adds the branch, commits, and pull request to the work item's **Development** panel after the branch is pushed.

Use uppercase keys exactly as shown in Jira: `COM-43`, not `com-43`.

## 2. Naming standard

### Branches

Use one deliverable Jira work item per branch and pull request.

```text
<type>/COM-<number>-<short-kebab-case-description>
```

Allowed types:

| Type | Use for | Example |
|---|---|---|
| `feat` | New product capability | `feat/COM-43-keycloak-bff-login` |
| `fix` | Defect correction | `fix/COM-57-session-expiry` |
| `chore` | Build, dependency, tooling, or maintenance work | `chore/COM-61-upgrade-springdoc` |
| `docs` | Documentation-only deliverable | `docs/COM-62-github-jira-workflow` |
| `test` | Test/evidence work with its own Jira item | `test/COM-63-auth-negative-matrix` |

Do not use a vague branch such as `feature/login` or put unrelated work items on one branch. If one change legitimately fulfills two work items, nominate one as the primary work item and include both keys in the commit and PR title.

### Commit subjects

Use Conventional Commit style and include the Jira key in the subject:

```text
<type>(<scope>): COM-123 <imperative summary>
```

Examples:

```text
feat(identity): COM-43 add authorization-code callback validation
test(identity): COM-43 cover invalid state and nonce paths
fix(catalog): COM-57 reject revoked maintainer grants
docs(platform): COM-62 document GitHub and Jira workflow
```

Use an optional body for the why, behavior change, risks, or evidence—not to repeat the diff. Never put passwords, tokens, personal data, or internal incident details in a commit subject or body; this repository is public.

### Pull-request title

Use the exact key as a prefix:

```text
COM-123: Short imperative PR title
```

Example:

```text
COM-43: Establish the Keycloak BFF principal boundary
```

Keep the key in the PR title even when the source branch and commits already contain it. This independently links the PR and makes the intended Jira scope obvious to reviewers.

## 3. Standard implementation flow

### Step 1 — Start from the Jira work item

Before coding, read the work item's acceptance criteria and linked design/evidence documents. Move the work item to **In Progress** manually when you actually begin implementation, according to the project workflow.

Copy its key, for example `COM-43`.

### Step 2 — Update local `main`

```bash
git switch main
git pull --ff-only origin main
```

`--ff-only` refuses an unexpected merge commit and makes it clear when local history needs attention.

### Step 3 — Create the feature branch

```bash
git switch -c feat/COM-43-keycloak-bff-login
```

### Step 4 — Make focused commits

```bash
git add services/identity-access-service
git diff --cached --check
git commit -m "feat(identity): COM-43 add authorization-code callback validation"
```

Every implementation commit for the work item includes `COM-43`. This is required for commits to appear individually in Jira.

### Step 5 — Validate before pushing

Run the relevant local checks. For the current Gate 1 foundation:

```bash
./dev test
./dev verify
```

Do not commit generated local secrets, `.env`, build output, logs, or evidence containing secrets/PII.

### Step 6 — Push the branch

```bash
git push -u origin feat/COM-43-keycloak-bff-login
```

Pushing is the point at which the connected GitHub repository can send the branch and commit metadata to Jira. The Development panel may take a few minutes to update.

### Step 7 — Open the pull request

Open a PR from the feature branch into `main` with:

```text
Title: COM-43: Establish the Keycloak BFF principal boundary
```

Use this description template:

```markdown
## Jira

Implements COM-43

## What changed

- [concise behavior-level change]

## Validation

- [command or evidence link]

## Security / rollback notes

- [relevant risk, migration, or "None"]
```

The Jira key in the PR title or source branch links the PR. The description provides reviewer context but is not a substitute for the title.

### Step 8 — Merge and update Jira deliberately

Merge only after the protected-branch CI checks pass. Then transition the work item according to actual delivery evidence. A merged PR is not automatically proof that a Jira item is **Done**: for this project, the required tests, security evidence, acceptance criteria, and any migration/rollback requirements must also be satisfied.

## 4. Multi-item and follow-up work

Prefer splitting unrelated work into separate branches and PRs. This keeps Jira's Development panel, review scope, rollback, and release notes accurate.

When a single atomic change must reference multiple work items, include each key:

```text
feat(identity): COM-43 COM-46 establish maintainer principal relay contract
```

and:

```text
COM-43 COM-46: Establish maintainer principal relay contract
```

State the primary item first. Do not add an Epic key merely for visibility if the commit does not directly deliver work from that Epic; link the actual task/sub-task that owns the deliverable.

## 5. CI, builds, and deployments

The GitHub Actions workflow runs on pull requests and pushes to `main`. It is a validation gate, not a Jira status-transition mechanism.

Jira can associate builds and deployments only when the build/deployment is connected and an associated commit contains the Jira key. Keep the Jira key in commit subjects even when the branch and PR are correctly named.

Jira records at most 100 linked commits per work item. Prefer focused commits and split long-running work when it approaches that volume.

## 6. Smart Commits: optional and conservative

Smart Commits can add comments, log work, or transition Jira items from commit messages. They require Jira administrator enablement and an appropriate workflow transition.

The project default is **not** to use Smart Commits for status transitions. Keep state changes manual until the team has agreed on transition semantics and evidence requirements.

If Smart Commits are later approved, use them only for deliberate actions. Example syntax:

```text
COM-43 #comment Callback validation evidence attached
```

Do not use `#resolve`, `#done`, time logging, or status-changing commands by habit. They can make Jira claim completion before the acceptance gate has been reviewed.

## 7. Verify that Jira linked the work

After pushing a branch or creating a PR:

1. Open the Jira work item, such as `COM-43`.
2. Find the **Development** panel.
3. Confirm that the branch, commits, and pull request appear.
4. On the board, look for development icons on the work-item card.

The linked items should point back to the correct GitHub branch, commits, and PR. Treat a missing link as a traceability defect and correct the next commit/PR title before merge.

## 8. Troubleshooting missing Jira development information

| Symptom | Likely cause | Fix |
|---|---|---|
| Branch does not appear | Key absent/misspelled, or branch was not pushed | Include uppercase `COM-123` in the branch name and push it. |
| Commits do not appear | Key absent from commit message | Add the key to all future related commits; do not rewrite published shared history solely for display. |
| PR does not appear | Key absent from both PR title and source branch | Rename the PR to start with `COM-123:`; confirm the branch name too. |
| Nothing appears after a few minutes | Wrong repository connected, integration sync delay, or missing Jira permission | Verify the connected GitHub repository and ask the Jira/project admin to confirm development-tool access. |
| Lowercase key does not link | Jira key parser expects uppercase format | Use `COM-123`, not `com-123`. |
| Build/deployment is absent | Build/deployment integration is not configured or associated commit lacks key | Configure the appropriate integration and retain the key in commits. |

## 9. Quick reference

```bash
# 1. Start the item
git switch main
git pull --ff-only origin main
git switch -c feat/COM-43-keycloak-bff-login

# 2. Commit with the exact Jira key
git add <files>
git diff --cached --check
git commit -m "feat(identity): COM-43 add authorization-code callback validation"

# 3. Push and open a PR titled:
git push -u origin feat/COM-43-keycloak-bff-login
# COM-43: Establish the Keycloak BFF principal boundary
```

## 10. Authoritative references

- [Atlassian: link GitHub development information to Jira work items](https://support.atlassian.com/jira-cloud-administration/docs/use-the-github-for-jira-app/)
- [Atlassian: reference work items in development spaces](https://support.atlassian.com/jira-software-cloud/docs/reference-issues-in-your-development-work/)
- [Atlassian: view development information for a work item](https://support.atlassian.com/jira-software-cloud/docs/view-development-information-for-an-issue/)
- [Atlassian: Smart Commits](https://support.atlassian.com/jira-software-cloud/docs/process-issues-with-smart-commits/)
