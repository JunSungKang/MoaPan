import { spawnSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
import { homedir } from 'node:os';

export function desiredProtection(plan) {
  return {
    required_status_checks: { strict: true, checks: plan.governance.requiredChecks.map(context => ({ context })) },
    enforce_admins: true,
    required_pull_request_reviews: {
      dismiss_stale_reviews: true, require_code_owner_reviews: false,
      required_approving_review_count: 1, require_last_push_approval: true,
    },
    restrictions: null,
    required_linear_history: plan.mergeMethod === 'squash',
    allow_force_pushes: false, allow_deletions: false,
    required_conversation_resolution: true,
  };
}

export function matches(actual, desired) {
  const review = actual.required_pull_request_reviews;
  const checks = actual.required_status_checks;
  return Boolean(actual.enforce_admins?.enabled && review?.dismiss_stale_reviews &&
    review.require_last_push_approval && review.required_approving_review_count >= 1 &&
    !actual.allow_force_pushes?.enabled && !actual.allow_deletions?.enabled &&
    actual.required_conversation_resolution?.enabled &&
    Boolean(actual.required_linear_history?.enabled) === desired.required_linear_history &&
    checks?.strict && desired.required_status_checks.checks.every(required =>
      checks.checks?.some(check => check.context === required.context)));
}

export async function provision({ plan, repository, human, app, api, apply = false }) {
  if (plan.flowType !== 'bootstrap') throw Error('Bootstrap plan required');
  if (human !== plan.governance.prAuthorLogin || human !== plan.governance.assigneeLogin) throw Error('Human identity mismatch');
  if (app.botLogin !== plan.governance.reviewerLogin || app.selection !== 'selected' || app.repository !== repository) throw Error('App installation mismatch');
  const metadata = await api(`repos/${repository}`);
  if (metadata.full_name !== repository || metadata.permissions?.admin !== true) throw Error('Canonical repository ADMIN permission required');
  const branchPath = `repos/${repository}/branches/${encodeURIComponent(plan.targetBranch)}`;
  const branch = await api(branchPath);
  if (branch.name !== plan.targetBranch || branch.commit.sha !== plan.baseSha) throw Error('Target branch changed');
  const rulesets = await api(`repos/${repository}/rulesets`);
  if (!Array.isArray(rulesets) || rulesets.some(rule => rule.enforcement !== 'disabled')) throw Error('Existing rulesets require policy reconciliation');
  const desired = desiredProtection(plan);
  const existing = await api(`${branchPath}/protection`, { missingProtection: true });
  if (existing) {
    if (!matches(existing, desired)) throw Error('Existing protection requires policy reconciliation; refusing overwrite');
    return { result: 'already-protected', repository, branch: plan.targetBranch };
  }
  if (!apply) return { result: 'dry-run', repository, branch: plan.targetBranch, desired };
  const currentBranch = await api(branchPath);
  if (currentBranch.commit.sha !== plan.baseSha || currentBranch.protected) throw Error('Target changed before write');
  if (await api(`${branchPath}/protection`, { missingProtection: true })) throw Error('Protection appeared before write');
  await api(`${branchPath}/protection`, { method: 'PUT', body: desired });
  if (!matches(await api(`${branchPath}/protection`), desired)) throw Error('Protection read-back verification failed');
  return { result: 'protected', repository, branch: plan.targetBranch };
}

function ghApi(endpoint, { missingProtection = false, method = 'GET', body } = {}) {
  const args = ['api', endpoint, '--method', method];
  if (body) args.push('--input', '-');
  const result = spawnSync('gh', args, { encoding: 'utf8', input: body ? JSON.stringify(body) : undefined });
  if (result.error) throw result.error;
  if (result.status !== 0) {
    let payload;
    try { payload = JSON.parse(result.stdout); } catch { /* Report the actual API layer below. */ }
    if (missingProtection && method === 'GET' && /HTTP 404/.test(result.stderr) && payload?.message === 'Branch not protected') return null;
    throw Error(`GitHub API failed for ${endpoint}: ${result.stderr.trim()}`);
  }
  return JSON.parse(result.stdout);
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const [mode, planPath] = process.argv.slice(2);
  if (!['--dry-run', '--apply'].includes(mode) || !planPath || process.argv.length !== 4) throw Error('Usage: node scripts/github-protection-bootstrap.mjs --dry-run|--apply <plan>');
  const scripts = resolve(homedir(), '.codex/scripts');
  const { loadPlan } = await import(pathToFileURL(resolve(scripts, 'github-app-contract.mjs')));
  const { resolveRepository, getInstallationToken } = await import(pathToFileURL(resolve(scripts, 'github-app-auth.mjs')));
  const repo = resolveRepository();
  const installation = await getInstallationToken({ permissions: { pull_requests: 'write' } });
  const plan = loadPlan(planPath);
  console.log(JSON.stringify(await provision({ plan, repository: repo.fullName,
    human: ghApi('user').login,
    app: { botLogin: installation.botLogin, selection: installation.installation.repository_selection, repository: installation.repository.fullName },
    api: ghApi, apply: mode === '--apply' }), null, 2));
}
