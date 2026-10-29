import test from 'node:test';
import assert from 'node:assert/strict';
import { provision } from './github-protection-bootstrap.mjs';

const plan = { flowType: 'bootstrap', targetBranch: 'base', baseSha: 'abc', mergeMethod: 'merge', governance: { prAuthorLogin: 'human', assigneeLogin: 'human', reviewerLogin: 'review[bot]', requiredChecks: [] } };
function scenario(overrides = {}) {
  const writes = [];
  let stored = overrides.existing ?? null;
  const api = async (path, opts = {}) => {
    if (opts.method === 'PUT') {
      writes.push(opts.body);
      const d = opts.body;
      stored = { ...d, enforce_admins: { enabled: d.enforce_admins }, required_linear_history: { enabled: d.required_linear_history }, allow_force_pushes: { enabled: false }, allow_deletions: { enabled: false }, required_conversation_resolution: { enabled: true } };
      return stored;
    }
    if (path === 'repos/owner/repo') return { full_name: overrides.name ?? 'owner/repo', permissions: { admin: overrides.admin ?? true } };
    if (path.endsWith('/rulesets')) return overrides.rulesets ?? [];
    if (path.endsWith('/protection')) return stored;
    return { name: 'base', commit: { sha: overrides.sha ?? 'abc' }, protected: false };
  };
  return { writes, options: { plan, repository: 'owner/repo', human: 'human', app: { botLogin: 'review[bot]', selection: 'selected', repository: 'owner/repo' }, api, apply: true } };
}
test('dry-run does not mutate', async () => {
  const s = scenario(); s.options.apply = false;
  assert.equal((await provision(s.options)).result, 'dry-run'); assert.equal(s.writes.length, 0);
});
test('installs and reads back protection, second execution is idempotent', async () => {
  const s = scenario(); assert.equal((await provision(s.options)).result, 'protected');
  assert.equal((await provision(s.options)).result, 'already-protected'); assert.equal(s.writes.length, 1);
  assert.equal(s.writes[0].required_pull_request_reviews.require_last_push_approval, true);
});
for (const [name, changes] of Object.entries({ permissions: { admin: false }, origin: { name: 'other/repo' }, baseMoved: { sha: 'changed' }, ruleset: { rulesets: [{ enforcement: 'active' }] }, existingPolicy: { existing: { enforce_admins: { enabled: false } } } })) {
  test(`rejects ${name} without a write`, async () => { const s = scenario(changes); await assert.rejects(provision(s.options)); assert.equal(s.writes.length, 0); });
}
test('API errors do not cause fallback writes', async () => {
  const s = scenario(); s.options.api = async () => { throw Error('API denied'); };
  await assert.rejects(provision(s.options), /API denied/); assert.equal(s.writes.length, 0);
});
test('different App repository cannot provision', async () => {
  const s = scenario(); s.options.app.repository = 'other/repo';
  await assert.rejects(provision(s.options), /App installation mismatch/); assert.equal(s.writes.length, 0);
});
