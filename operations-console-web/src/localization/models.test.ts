import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ApiError } from '../api/session.ts';
import type { KeyImportPreview, LocaleCoverage, ReleaseDiff, ReleasePrecheck, TranslationEntry } from './localizationApi.ts';
import { decodePrecheck, decodeReleaseSummary } from './localizationApi.ts';
import { codeProblem, importBlocker, importRows, namespaceCodePattern, applicationCodePattern, summarizeImportFile, SOURCE_FORMAT, TRANSLATION_FORMAT, MAX_IMPORT_BYTES } from './importModel.ts';
import { POLL_LIMIT_MS, bundleSummary, changeRows, changeTotal, hasPending, pollDecision, progressSteps, releaseDisplay, switchDirection, switchImpact } from './releaseModel.ts';
import { coverageChecks, draftOf, lowestRequiredCoverage, nextSortOrder, settingsDirty, thresholdChoices } from './settingsModel.ts';
import { editorState, entryStatus, issueGroups, nextAfterSave, withDraftError, problemTarget, saveErrorMessages, siblingKey, tabCounts, unpublishedRevisions } from './translationModel.ts';
import { classifyFailure, localizationCommand } from './commandModel.ts';

const entry = (keyCode: string, values: Partial<TranslationEntry> = {}): TranslationEntry => ({
  namespaceCode: keyCode.split('.')[0], keyCode, sourceText: '{name} was removed from the team', description: null, parameters: ['name'],
  text: null, version: 0, sourceChanged: false, updatedBy: null, updateTime: null, ...values });
const coverage = (locale: string, values: Partial<LocaleCoverage> = {}): LocaleCoverage => ({
  locale, required: true, totalKeys: 100, translatedKeys: 100, sourceChangedKeys: 0, coveragePercent: 100, satisfied: true, ...values });
const precheck = (values: Partial<ReleasePrecheck> = {}): ReleasePrecheck => ({
  publishable: true, contentRevision: 9, nextReleaseNo: 8, activeKeyCount: 100, coverage: [coverage('en', { required: false }), coverage('zh-CN')],
  missing: [], missingTotal: 0, sourceChanged: [], sourceChangedTotal: 0, problems: [], changeSummary: null, ...values });

test('tab counts come from the target locale coverage', () => {
  assert.equal(tabCounts(undefined), null);
  assert.deepEqual(tabCounts(coverage('zh-CN', { totalKeys: 1322, translatedKeys: 1284, sourceChangedKeys: 5 })), { all: 1322, untranslated: 38, source_changed: 5 });
});

test('list status: saved translations, untranslated, source changed, unsaved edits and errors (prototypes 01, 02)', () => {
  assert.equal(entryStatus(entry('team.a')), 'untranslated');
  assert.equal(entryStatus(entry('team.a', { text: '{name} 已被移出团队' })), 'translated');
  assert.equal(entryStatus(entry('team.a', { text: '{name} 已被移出团队', sourceChanged: true })), 'source_changed');
  assert.equal(entryStatus(entry('team.a', { text: '{name} 已被移出团队' }), '{name} 已移出'), 'editing');
  assert.equal(entryStatus(entry('team.a'), '{名字} 已被移出团队'), 'error');
  // A saved translation that no longer matches the base (e.g. the base gained a parameter) is shown as an error.
  assert.equal(entryStatus(entry('team.a', { text: '已被移出团队' })), 'error');
});

test('editor state: can save only valid changes, or confirm an unchanged translation whose source changed', () => {
  const saved = entry('team.a', { text: '{name} 已被移出团队', version: 3 });
  assert.deepEqual(editorState(saved, saved.text!), { issues: [], dirty: false, canSave: false, confirmOnly: false });
  assert.equal(editorState(saved, '{name} 离开了').canSave, true);
  assert.equal(editorState(saved, '{名字} 离开了').canSave, false);
  assert.equal(editorState(saved, '').canSave, false);
  assert.equal(editorState(entry('team.a'), '').issues.length, 0);
  const changed = { ...saved, sourceChanged: true };
  assert.deepEqual(editorState(changed, changed.text!), { issues: [], dirty: false, canSave: true, confirmOnly: true });
});

test('keyboard navigation and save-and-next stay inside the list', () => {
  const entries = ['a.1', 'a.2', 'a.3'].map(key => entry(key));
  assert.equal(siblingKey(entries, 'a.2', 1), 'a.3');
  assert.equal(siblingKey(entries, 'a.3', 1), 'a.3');
  assert.equal(siblingKey(entries, 'a.1', -1), 'a.1');
  assert.equal(siblingKey(entries, 'missing', 1), 'a.1');
  assert.equal(siblingKey([], 'a.1', 1), null);
  assert.equal(nextAfterSave(entries, 'a.1'), 'a.2');
  assert.equal(nextAfterSave(entries, 'a.3'), null);
});

test('issue groups summarize blockers for the "N issues" popover and locate problems (prototype 02)', () => {
  assert.equal(problemTarget('translations.zh-CN.team.member.removed')?.keyCode, 'team.member.removed');
  assert.equal(problemTarget('translations.zh-CN.team.member.removed')?.locale, 'zh-CN');
  assert.equal(problemTarget('namespaces.team'), null);
  const clean = issueGroups(precheck(), 100)!;
  assert.equal(clean.blocking, 0);
  const groups = issueGroups(precheck({
    publishable: false,
    coverage: [coverage('en', { required: false }), coverage('zh-CN', { translatedKeys: 62, coveragePercent: 62, satisfied: false, sourceChangedKeys: 5 })],
    missing: [...Array(24)].map((_, index) => ({ locale: 'zh-CN', keyCode: `onboarding.k${index}` }))
      .concat([...Array(10)].map((_, index) => ({ locale: 'zh-CN', keyCode: `developer.k${index}` })), [...Array(4)].map((_, index) => ({ locale: 'zh-CN', keyCode: `team.k${index}` }))),
    missingTotal: 38, sourceChangedTotal: 5,
    problems: [{ field: 'translations.zh-CN.team.member.removed', message: 'mismatch' }],
  }), 100)!;
  assert.equal(groups.blocking, 2);
  assert.deepEqual(groups.errors, [{ locale: 'zh-CN', keyCode: 'team.member.removed', message: 'mismatch' }]);
  assert.equal(groups.coverageGaps[0].missing, 38);
  assert.deepEqual(groups.coverageGaps[0].byNamespace.map(item => `${item.namespace} ${item.count}`), ['onboarding 24', 'developer 10', 'team 4']);
  assert.equal(groups.sourceChangedTotal, 5);
  const empty = issueGroups(precheck({ publishable: false, activeKeyCount: 0 }), 100)!;
  assert.equal(empty.noKeys, true);
  assert.equal(empty.blocking, 1);
  assert.equal(issueGroups(null, 100), null);
  const withDraft = withDraftError(clean, { locale: 'zh-CN', keyCode: 'team.member.removed', message: 'renamed' })!;
  assert.equal(withDraft.blocking, 1);
  assert.equal(withDraft.errors.length, 1);
  assert.equal(withDraftError(groups, { locale: 'zh-CN', keyCode: 'team.member.removed', message: 'again' }), groups);
  assert.equal(withDraftError(groups, null), groups);
});

test('unpublished changes compare the content revision with the live release', () => {
  assert.equal(unpublishedRevisions(30, 7), 23);
  assert.equal(unpublishedRevisions(7, 7), 0);
  assert.equal(unpublishedRevisions(5, null), 5);
  assert.deepEqual(saveErrorMessages([{ field: 'translations[0].text', message: 'bad' }, { field: 'locale', message: 'x' }]), ['bad']);
});

test('release display and polling stop at READY / FAILED or after the limit (prototype 05)', () => {
  assert.equal(releaseDisplay({ status: 'PENDING_CHECK', current: false }), 'checking');
  assert.equal(releaseDisplay({ status: 'READY', current: true }), 'live');
  assert.equal(releaseDisplay({ status: 'READY', current: false }), 'offline');
  assert.equal(releaseDisplay({ status: 'FAILED', current: false }), 'failed');
  assert.equal(pollDecision('PENDING_CHECK', 0), 'continue');
  assert.equal(pollDecision('PENDING_CHECK', POLL_LIMIT_MS), 'timeout');
  assert.equal(pollDecision('READY', 0), 'done');
  assert.equal(pollDecision('FAILED', 0), 'done');
  assert.equal(pollDecision(undefined, 0), 'done');
  assert.equal(hasPending([{ status: 'READY' }, { status: 'PENDING_CHECK' }]), true);
  assert.equal(hasPending([{ status: 'READY' }, { status: 'FAILED' }]), false);
  assert.deepEqual(progressSteps({ status: 'PENDING_CHECK' }).map(step => step.state), ['done', 'done', 'active', 'waiting']);
  assert.deepEqual(progressSteps({ status: 'FAILED' }).map(step => step.state), ['done', 'done', 'failed', 'waiting']);
  assert.deepEqual(progressSteps({ status: 'READY' }).map(step => step.state), ['done', 'done', 'done', 'done']);
});

const change = (locale: string, keyCode: string, before: string | null, after: string | null) => ({ locale, keyCode, before, after });
const diff: ReleaseDiff = { releaseNo: 8, baseReleaseNo: 7,
  added: [change('en', 'team.new', null, 'New'), change('zh-CN', 'team.new', null, '新'), change('zh-CN', 'team.old', null, '补齐')],
  changed: [change('en', 'team.role.admin', 'Admin', 'Administrator'), change('zh-CN', 'onboarding.address', '经营地址', '营业地址')],
  removed: [change('en', 'developer.sandbox.banner', 'Sandbox', null), change('zh-CN', 'developer.sandbox.banner', '沙箱', null)] };

test('change summary groups keys, translation edits, English changes and deprecations without double counting', () => {
  const rows = changeRows(diff);
  assert.deepEqual(rows.map(row => `${row.kind}:${row.locale}:${row.items.length}`), ['added:en:1', 'translation:zh-CN:3', 'source:en:1', 'removed:en:1']);
  assert.equal(changeTotal(rows), 6);
  assert.deepEqual(changeRows({ releaseNo: 1, baseReleaseNo: null, added: [], changed: [], removed: [] }), []);
});

test('switch impact describes what users see after switching to the target release (prototype 06)', () => {
  // diff(target #7, base = live #8): what #8 has and #7 lacks shows as removed.
  const back: ReleaseDiff = { releaseNo: 7, baseReleaseNo: 8, added: [], removed: [change('en', 'team.new', 'New', null), change('zh-CN', 'team.new', '新', null)],
    changed: [change('zh-CN', 'onboarding.address', '经营地址', '营业地址'), change('zh-CN', 'team.x', null, null)] };
  const impact = switchImpact(back);
  assert.equal(impact.missingKeys, 1);
  assert.equal(impact.extraKeys, 0);
  assert.deepEqual(impact.translations.map(item => [item.locale, item.count, item.example?.before, item.example?.after]), [['zh-CN', 2, '经营地址', '营业地址']]);
  assert.equal(switchDirection(7, 8), 'back');
  assert.equal(switchDirection(9, 8), 'forward');
  assert.equal(switchDirection(1, null), 'forward');
  assert.deepEqual(bundleSummary([{ locale: 'zh-CN', namespaceCode: 'team', fileAddress: '', contentHash: '', byteSize: 1000 },
    { locale: 'en', namespaceCode: 'team', fileAddress: '', contentHash: '', byteSize: 24 }]), { locales: ['en', 'zh-CN'], namespaces: 1, bytes: 1024, files: 2 });
});

const preview = (values: Partial<KeyImportPreview> = {}): KeyImportPreview => ({ importable: true, previewDigest: 'a'.repeat(64), keySetRevision: 3,
  added: [], sourceChanged: [], deprecated: [], restored: [], unchangedCount: 10, missingNamespaces: [], problems: [], ...values });
const item = (keyCode: string) => ({ namespaceCode: keyCode.split('.')[0], keyCode, sourceText: 'x', previousSourceText: null, description: null });

test('import preview blocks confirmation for missing namespaces, file problems, a stale preview or no changes (prototype 03)', () => {
  assert.equal(importBlocker(preview({ added: [item('team.a')] }), false), null);
  assert.equal(importBlocker(preview({ added: [item('billing.a')], missingNamespaces: ['billing'], importable: false }), false), 'missing_namespaces');
  assert.equal(importBlocker(preview({ problems: [{ field: 'format', message: 'x' }], importable: false }), false), 'problems');
  assert.equal(importBlocker(preview({ added: [item('team.a')] }), true), 'stale');
  assert.equal(importBlocker(preview(), false), 'no_changes');
  assert.equal(importBlocker(preview({ importable: false, added: [item('team.a')] }), false), 'not_importable');
  assert.equal(importBlocker(null, false), 'not_importable');
  assert.deepEqual(importRows(preview({ added: [item('a.1')], deprecated: [item('a.2'), item('a.3')] })).map(row => `${row.kind}:${row.items.length}`), ['added:1', 'deprecated:2']);
});

test('the import file is checked locally before upload', () => {
  const file = (value: unknown) => JSON.stringify(value);
  const source = { format: SOURCE_FORMAT, application: 'client-console', namespaces: { team: { 'team.a': 'A', 'team.b': { text: 'B' } }, auth: { 'auth.a': 'A' } } };
  const expected = { application: 'client-console', format: SOURCE_FORMAT };
  assert.deepEqual(summarizeImportFile(file(source), 100, expected), { problem: null, application: 'client-console',
    namespaces: [{ code: 'team', keys: 2 }, { code: 'auth', keys: 1 }], keys: 3 });
  assert.equal(summarizeImportFile('', 0, expected).problem, 'empty');
  assert.equal(summarizeImportFile('{}', MAX_IMPORT_BYTES + 1, expected).problem, 'too_large');
  assert.equal(summarizeImportFile('{', 1, expected).problem, 'not_json');
  assert.equal(summarizeImportFile(file({ ...source, format: 'x' }), 10, expected).problem, 'bad_format');
  assert.equal(summarizeImportFile(file({ ...source, application: 'checkout' }), 10, expected).problem, 'application_mismatch');
  const translation = { format: TRANSLATION_FORMAT, application: 'client-console', locale: 'zh-CN', namespaces: { team: { 'team.a': '甲' } } };
  assert.equal(summarizeImportFile(file(translation), 10, { ...expected, format: TRANSLATION_FORMAT, locale: 'zh-CN' }).problem, null);
  assert.equal(summarizeImportFile(file(translation), 10, { ...expected, format: TRANSLATION_FORMAT, locale: 'ja' }).problem, 'locale_mismatch');
});

test('namespace and application codes are validated immediately (prototypes 08, 09)', () => {
  assert.equal(codeProblem('billing', namespaceCodePattern, ['team']), null);
  assert.equal(codeProblem('', namespaceCodePattern, []), 'required');
  assert.equal(codeProblem('Billing', namespaceCodePattern, []), 'pattern');
  assert.equal(codeProblem('bill.ing', namespaceCodePattern, []), 'pattern');
  assert.equal(codeProblem('team', namespaceCodePattern, ['team']), 'duplicate');
  assert.equal(codeProblem('a', applicationCodePattern, []), 'pattern');
  assert.equal(codeProblem('merchant-portal', applicationCodePattern, ['client-console']), null);
});

test('settings show live coverage against the draft requirement (prototype 07)', () => {
  const application = { applicationCode: 'client-console', applicationName: 'Client', defaultLocale: 'en', requiredLocales: ['zh-CN'], coverageThresholdPercent: 100,
    status: 'ENABLED', currentReleaseNo: 8, keySetRevision: 1, contentRevision: 1, version: 2, updatedBy: null, updateTime: null, pendingChangeCount: 0 };
  const draft = draftOf(application);
  assert.equal(settingsDirty(application, draft), false);
  assert.equal(settingsDirty(application, { ...draft, coverageThresholdPercent: 95 }), true);
  assert.equal(settingsDirty(application, { ...draft, requiredLocales: [] }), true);
  const items = [coverage('en', { required: false }), coverage('zh-CN', { totalKeys: 1322, translatedKeys: 1260, coveragePercent: 95, satisfied: false })];
  assert.deepEqual(coverageChecks(items, draft), [{ locale: 'zh-CN', percent: 95, satisfied: false, missing: 62 }]);
  assert.equal(coverageChecks(items, { ...draft, coverageThresholdPercent: 95 })[0].satisfied, true);
  assert.equal(lowestRequiredCoverage(items)?.coveragePercent, 95);
  assert.equal(lowestRequiredCoverage([coverage('en', { required: false })]), null);
  assert.deepEqual(thresholdChoices(80), [100, 95, 90, 80]);
  assert.equal(nextSortOrder([{ sortOrder: 10 }, { sortOrder: 40 }]), 50);
  assert.equal(nextSortOrder([]), 10);
});

test('write commands reuse the idempotency key only for the same retry, and failures are classified', () => {
  const first = localizationCommand('/x', 'POST', { a: 1 });
  assert.equal(localizationCommand('/x', 'POST', { a: 1 }, first).key, first.key);
  assert.notEqual(localizationCommand('/x', 'POST', { a: 2 }, first).key, first.key);
  assert.notEqual(localizationCommand('/x', 'PUT', { a: 1 }, first).key, first.key);
  assert.deepEqual(classifyFailure(new ApiError(409, 'I18N_IMPORT_PREVIEW_STALE', 'r', 'stale')), { kind: 'conflict', code: 'I18N_IMPORT_PREVIEW_STALE', detail: 'stale' });
  const rejected = classifyFailure(new ApiError(422, 'I18N_COVERAGE_INSUFFICIENT', 'r', 'gap', [{ field: 'zh-CN', message: 'x' }]));
  assert.equal(rejected.kind, 'rejected');
  assert.equal(classifyFailure(new ApiError(503, 'X')).kind, 'other');
  assert.equal(classifyFailure(new Error('x')).kind, 'other');
});

test('response decoders reject unknown release states and default optional precheck fields', () => {
  assert.throws(() => decodeReleaseSummary({ releaseNo: 1, status: 'UNKNOWN' }));
  assert.equal(decodeReleaseSummary({ releaseNo: 3, status: 'READY', current: true }).current, true);
  const decoded = decodePrecheck({ publishable: false });
  assert.deepEqual([decoded.missing, decoded.problems, decoded.nextReleaseNo], [[], [], 1]);
});

test('batch saves are split by request size (48 KB) and entry count (200), in order', async () => {
  const { splitBatches, batchErrorTargets, parsePaste, BATCH_MAX_BYTES } = await import('./translationModel.ts');
  const item = (index: number, size = 10) => ({ keyCode: `team.k${index}`, text: '译'.repeat(size), expectedVersion: 1 });
  const many = [...Array(450)].map((_, index) => item(index));
  assert.deepEqual(splitBatches('zh-CN', many).map(batch => batch.length), [200, 200, 50]);
  const large = [...Array(10)].map((_, index) => item(index, 6000));
  const batches = splitBatches('zh-CN', large);
  for (const batch of batches) assert.ok(new TextEncoder().encode(JSON.stringify({ locale: 'zh-CN', translations: batch })).length <= BATCH_MAX_BYTES);
  assert.deepEqual(batches.flat().map(entry => entry.keyCode), large.map(entry => entry.keyCode));
  assert.deepEqual(splitBatches('zh-CN', []), []);
  assert.deepEqual(batchErrorTargets([{ field: 'translations[1].text', message: 'bad' }, { field: 'locale', message: 'x' }], [item(0), item(1)]),
    [{ keyCode: 'team.k1', message: 'bad' }, { keyCode: null, message: 'x' }]);
  assert.deepEqual(parsePaste('team.a\t甲\n\nnot a key\nteam.b\t{name} 乙\nteam.a\t甲2'), {
    entries: [{ keyCode: 'team.a', text: '甲2' }, { keyCode: 'team.b', text: '{name} 乙' }], invalidLines: [3] });
  assert.deepEqual(parsePaste('{"team.a": "甲", "team.b": ""}').entries, [{ keyCode: 'team.a', text: '甲' }]);
  assert.deepEqual(parsePaste(JSON.stringify({ format: 'code-mor-i18n-translation/1', namespaces: { team: { 'team.a': '甲' } } })).entries, [{ keyCode: 'team.a', text: '甲' }]);
});

test('syntax issues carry a character position for the reason text', async () => {
  const { validateTranslation } = await import('./icu.ts');
  const [issue] = validateTranslation('{count, plural, other {#}}', '{count, plural, one {#}}');
  assert.equal(issue.code, 'missing_other');
  assert.equal(issue.start, 0);
  const [brace] = validateTranslation('Hi {name}', 'Hi {name');
  assert.equal(brace.start + 1, 4);
});

test('import file checks: only 409 I18N_IMPORT_FILE_NOT_READY is retried with backoff, other errors surface at once', async () => {
  const { withScanRetry } = await import('./commandModel.ts');
  const waits: number[] = []; let calls = 0; let waiting = 0;
  const sleep = async (ms: number) => { waits.push(ms); };
  const notReady = new ApiError(409, 'I18N_IMPORT_FILE_NOT_READY', 'r', 'scanning');
  assert.equal(await withScanRetry(async () => { if (++calls < 3) throw notReady; return 'ok'; }, () => false, () => waiting++, [1000, 2000, 3000], sleep), 'ok');
  assert.deepEqual([calls, waiting, waits], [3, 2, [1000, 2000]]);
  calls = 0;
  await assert.rejects(withScanRetry(async () => { calls++; throw new ApiError(400, 'BUSINESS_CHECK_FAILED', 'r', 'rejected'); }, () => false, () => {}, [1000], sleep),
    (error: unknown) => (error as ApiError).code === 'BUSINESS_CHECK_FAILED');
  assert.equal(calls, 1);
  calls = 0;
  await assert.rejects(withScanRetry(async () => { calls++; throw notReady; }, () => false, () => {}, [1, 1], sleep), (error: unknown) => error === notReady);
  assert.equal(calls, 3);
});

test('change summaries from Platform become rows with samples; check progress and pending counts decode', async () => {
  const { summaryRows, summaryTotal } = await import('./releaseModel.ts');
  const { decodeChangeSummary, decodeReleaseDetail, decodeApplication } = await import('./localizationApi.ts');
  const summary = decodeChangeSummary({ baseReleaseNo: 7, addedKeyCount: 12, addedKeys: ['onboarding.tax.vat.label'], deprecatedKeyCount: 2, deprecatedKeys: ['a.b'],
    sourceChangedKeyCount: 0, sourceChangedKeys: [], translationChanges: [{ locale: 'zh-CN', changedCount: 44, sampleKeys: ['team.a'] }] })!;
  assert.deepEqual(summaryRows(summary).map(row => `${row.kind}:${row.locale}:${row.count}:${row.samples.length}`),
    ['added:en:12:1', 'translation:zh-CN:44:1', 'removed:en:2:1']);
  assert.equal(summaryTotal(summary), 58);
  assert.equal(decodeChangeSummary(null), null);
  const detail = decodeReleaseDetail({ release: { releaseNo: 8, status: 'PENDING_CHECK' }, bundles: [], checkProgress: { total: 12, available: 9, pending: 2, rejected: 1 } });
  assert.deepEqual(detail.checkProgress, { total: 12, available: 9, pending: 2, rejected: 1 });
  assert.equal(detail.release.changeSummary, null);
  const base = { applicationCode: 'a', applicationName: 'A', defaultLocale: 'en' };
  assert.equal(decodeApplication({ ...base, pendingChangeCount: 23 }).pendingChangeCount, 23);
  assert.equal(decodeApplication(base).pendingChangeCount, null);
});

test('批量粘贴只读取涉及的命名空间，并在请求页数超过预算时要求分批', async () => {
  const { pastePlan, PASTE_PAGE_BUDGET, TRANSLATION_PAGE_SIZE } = await import('./translationModel.ts');
  const namespaces = [{ namespaceCode: 'auth', activeKeyCount: 42 }, { namespaceCode: 'channel', activeKeyCount: 1157 },
    { namespaceCode: 'risk', activeKeyCount: 1132 }, { namespaceCode: 'workflow', activeKeyCount: 989 }];
  const small = pastePlan(['auth.login.title', 'auth.login.submit', 'channel.list.title', 'unknown.key'], namespaces);
  assert.deepEqual(small, { namespaces: ['auth', 'channel'], pages: 1 + 6, tooLarge: false });
  const many = Array.from({ length: 30 }, (_, i) => ({ namespaceCode: `ns${i}`, activeKeyCount: 200 }));
  const large = pastePlan(many.map(item => `${item.namespaceCode}.a.b`), many);
  assert.equal(large.pages, 30); assert.equal(large.tooLarge, true); assert.ok(large.pages > PASTE_PAGE_BUDGET);
  assert.ok(TRANSLATION_PAGE_SIZE <= 200, 'page size within the list contract');
});
