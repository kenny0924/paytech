import assert from 'node:assert/strict';
import test from 'node:test';
import {
  countProblem, decodeDownloadGrant, decodeFiles, decodeUploadGrant, decodeUploadReceipt, fileAddressesOf, fileAddressOf, fileIdOfAddress, fileIndex, mimeAllowed,
  needsLayout, outputFileAddresses, preCheckFiles, previewKind, rulesHint, safeTransferUrl, safeUploadHeaders, sizeText, startFileAddresses, structureProblems,
  scanFailedText, blockingFiles, scanFailed, scanFailedAddresses, scanPending, uploadOne, uploadRequired, uploadRules, withoutInlineFileErrors,
} from './fileModel.ts';
import { COUNT_PAGE_SIZE, decodeSubmission } from './workflowApi.ts';
import type { UploadPhase, WorkflowPost } from './fileModel.ts';
import type { ActionRun, WorkflowFile, WorkflowGraph } from './workflowTypes.ts';
import { readFileSync } from 'node:fs';
import { testT } from '../i18n/testing.ts';

const t = testT('workflow');

const future = new Date(Date.now() + 60_000).toISOString();
const past = new Date(Date.now() - 1_000).toISOString();

test('transfer url accepts only unexpired https without credentials or fragment', () => {
  assert.equal(safeTransferUrl({ url: 'https://minio.local/b/o?X-Amz=1', expiresAt: future }), 'https://minio.local/b/o?X-Amz=1');
  for (const url of ['http://minio.local/b/o', 'https://user:pass@minio.local/o', 'https://minio.local/o#x', 'not a url'])
    assert.throws(() => safeTransferUrl({ url, expiresAt: future }), /invalid/);
  assert.throws(() => safeTransferUrl({ url: 'https://minio.local/o', expiresAt: past }), /expired/);
});

test('upload headers refuse session, cookie, proxy and gateway identity headers', () => {
  assert.equal(safeUploadHeaders({ 'Content-Type': 'application/pdf', 'If-None-Match': '*' }).get('if-none-match'), '*');
  for (const name of ['Authorization', 'cookie', 'Proxy-Authorization', 'X-Gateway-Actor-Subject', 'x-caller-service'])
    assert.throws(() => safeUploadHeaders({ [name]: 'x' }), /disallowed/);
});

test('pre-check enforces empty files, name length, mime list and max count', () => {
  const rules = { minFiles: 1, maxFiles: 2, mimeTypes: ['application/pdf', 'image/*'] };
  const pdf = { name: 'a.pdf', type: 'application/pdf', size: 10 };
  assert.equal(preCheckFiles(rules, 0, [pdf, { name: 'b.png', type: 'image/png', size: 3 }]), null);
  assert.match(preCheckFiles(rules, 1, [pdf, pdf])!, /Up to 2 files/);
  assert.match(preCheckFiles(rules, 0, [{ ...pdf, size: 0 }])!, /is empty/);
  assert.match(preCheckFiles(rules, 0, [{ name: 'x.txt', type: 'text/plain', size: 1 }])!, /text\/plain/);
  assert.match(preCheckFiles(rules, 0, [{ name: 'x.bin', type: '', size: 1 }])!, /application\/octet-stream/);
  assert.match(preCheckFiles({}, 0, [{ ...pdf, name: 'a'.repeat(256) }])!, /255/);
  assert.equal(mimeAllowed('IMAGE/JPEG', ['image/*']), true);
  assert.equal(mimeAllowed('text/plain', []), true);
  assert.equal(countProblem(rules, 0, true), 'Required');
  assert.equal(countProblem({ minFiles: 2 }, 1, false), 'At least 2 files');
  assert.equal(countProblem(rules, 3, false), 'At most 2 files');
  assert.equal(countProblem(rules, 0, false), null);
  assert.equal(rulesHint(rules), 'application/pdf, image/* · 1–2 files');
  assert.equal(rulesHint({}), 'Any type');
});

const file = (name: string, type: string, bytes: string) => Object.assign(new Blob([bytes], { type }), { name });

test('upload runs start → PUT → completion and returns the file address', async () => {
  const calls: { path: string; body: unknown }[] = []; const phases: UploadPhase[] = [];
  const post: WorkflowPost = async <T,>(path: string, body: unknown, decode: (value: unknown) => T) => {
    calls.push({ path, body });
    return decode(path.endsWith('/completion') ? { uploadId: '501', fileId: '901', fileAddress: '/files/v1/901', completedAt: future }
      : { uploadId: '501', authorization: { method: 'PUT', url: 'https://minio.local/b/o', headers: { 'Content-Type': 'application/pdf' }, expiresAt: future }, expiresAt: future });
  };
  let put: RequestInit | undefined; let putUrl = '';
  const fileId = await uploadOne(file('合同.pdf', 'application/pdf', 'pdf'), { uploads: '/u', completion: id => `/u/${id}/completion` }, post,
    async (url, init) => { putUrl = url; put = init; return { ok: true, status: 200 }; }, phase => phases.push(phase));
  assert.equal(fileId, '/files/v1/901');
  assert.deepEqual(calls, [{ path: '/u', body: { filename: '合同.pdf', mimeType: 'application/pdf', sizeBytes: '3' } }, { path: '/u/501/completion', body: undefined }]);
  assert.equal(putUrl, 'https://minio.local/b/o');
  assert.equal(put?.method, 'PUT'); assert.equal(put?.credentials, 'omit'); assert.equal(put?.redirect, 'error');
  assert.equal((put?.headers as Headers).get('content-type'), 'application/pdf');
  assert.deepEqual(phases, ['UPLOADING', 'COMPLETING', 'DONE']);
});

test('upload stops before completion when the transfer fails or the grant is unsafe', async () => {
  const completions: string[] = [];
  const grant = (url: string, method = 'PUT') => ({ uploadId: '501', authorization: { method, url, headers: {}, expiresAt: future }, expiresAt: future });
  const post = (value: unknown): WorkflowPost => async <T,>(path: string, _body: unknown, decode: (value: unknown) => T) => {
    if (path.endsWith('/completion')) completions.push(path);
    return decode(value);
  };
  const paths = { uploads: '/u', completion: (id: string) => `/u/${id}/completion` };
  const blob = file('a.pdf', 'application/pdf', 'x');
  await assert.rejects(uploadOne(blob, paths, post(grant('https://m/o')), async () => ({ ok: false, status: 403 })), /403/);
  await assert.rejects(uploadOne(blob, paths, post(grant('https://m/o')), async () => { throw new TypeError('network'); }), /wasn't confirmed/);
  await assert.rejects(uploadOne(blob, paths, post(grant('http://m/o')), async () => ({ ok: true, status: 200 })), /invalid/);
  await assert.rejects(uploadOne(blob, paths, post(grant('https://m/o', 'POST')), async () => ({ ok: true, status: 200 })), /Invalid upload authorization/);
  assert.deepEqual(completions, []);
  assert.throws(() => decodeUploadGrant({ uploadId: '0', authorization: { url: 'https://m' } }), /Invalid response/);
});

const run = (extra: Partial<ActionRun>): ActionRun => ({ actionRunId: '1', nodeRunId: 'n', actionKey: 'a', actionType: 'FILE_UPLOAD', required: true,
  assigneeId: '7', status: 'COMPLETED', output: { files: ['/files/v1/11', '/files/v1/12'] }, submittedBy: '7', submittedTime: future, ...extra });
const formTemplate = { config: { fields: [{ key: 'contract', label: '合同', type: 'FILE' as const, required: true }, { key: 'note', label: '说明', type: 'TEXT' as const, required: false }] } };

test('file address format: build and parse /files/v1/{fileId}', () => {
  assert.equal(fileAddressOf('901'), '/files/v1/901');
  assert.throws(() => fileAddressOf('0901'), /Invalid file ID/);
  assert.equal(fileIdOfAddress('/files/v1/901'), '901');
  assert.equal(fileIdOfAddress('/files/v1/9223372036854775807'), '9223372036854775807');
  for (const value of ['901', '/files/v1/0901', '/files/v1/', '/files/v1/12345678901234567890', '/files/v2/1', 'https://x/files/v1/1', '/files/v1/1/', 901, null])
    assert.equal(fileIdOfAddress(value), null);
});

test('file addresses come from FILE_UPLOAD output, FORM file fields and start values; bare ids are not valid', () => {
  assert.deepEqual(fileAddressesOf(['/files/v1/11', '11', 12, '/files/v1/012', '/files/v1/13']), ['/files/v1/11', '/files/v1/13']);
  assert.deepEqual(outputFileAddresses(run({})), ['/files/v1/11', '/files/v1/12']);
  assert.deepEqual(outputFileAddresses(run({ actionType: 'FORM', output: { contract: ['/files/v1/21', '22'], note: '/files/v1/31' } }), formTemplate), ['/files/v1/21']);
  assert.deepEqual(outputFileAddresses(run({ actionType: 'MANUAL_CONFIRM', output: { note: '/files/v1/11' } })), []);
  assert.deepEqual(startFileAddresses({ startForm: formTemplate.config, startValues: { contract: ['/files/v1/41', '/files/v1/42'], note: 'x' } }),
    ['/files/v1/41', '/files/v1/42']);
});

test('upload requirement: required switch, legacy minFiles, no count or type limits; scan state helpers', () => {
  assert.equal(uploadRequired({ required: true }), true);
  assert.equal(uploadRequired({ required: false, minFiles: 2 }), false);
  assert.equal(uploadRequired({ minFiles: 1, maxFiles: 3, mimeTypes: ['application/pdf'] }), true);
  assert.equal(uploadRequired({ minFiles: 0 }), false);
  assert.equal(uploadRequired(undefined), false);
  assert.deepEqual(uploadRules({ minFiles: 1, maxFiles: 3, mimeTypes: ['application/pdf'] }), {});
  const meta = (scanStatus: WorkflowFile['scanStatus']) => ({ fileId: '1', fileAddress: '/files/v1/1', displayName: null, mimeType: null, sizeBytes: null, scanStatus, uploaderId: null });
  assert.equal(scanPending(meta('SCANNING')), true);
  assert.equal(scanPending(meta(null)), true);
  assert.equal(scanPending(meta('AVAILABLE')), false);
  assert.equal(scanFailed(meta('REJECTED')), true);
  assert.equal(scanFailed(meta('QUARANTINED')), true);
  assert.equal(scanFailed(meta('SCANNING')), false);
});

test('file metadata decoding, preview kind and size text', () => {
  const decoded = decodeFiles([{ fileId: '1', displayName: 'a.pdf', mimeType: 'application/pdf', sizeBytes: '2048', scanStatus: 'AVAILABLE', uploaderId: '7' },
    { fileId: '2', fileAddress: '/files/v1/2', scanStatus: 'SCANNING' }, { x: 1 }]);
  assert.deepEqual(decoded[0], { fileId: '1', fileAddress: '/files/v1/1', displayName: 'a.pdf', mimeType: 'application/pdf', sizeBytes: '2048',
    scanStatus: 'AVAILABLE', uploaderId: '7' });
  assert.equal(decoded.length, 2);
  assert.equal(fileIndex({ files: decoded }).get('/files/v1/2')?.scanStatus, 'SCANNING');
  assert.deepEqual(decodeFiles(undefined), []);
  assert.equal(previewKind('application/pdf'), 'pdf');
  assert.equal(previewKind('image/png'), 'image'); assert.equal(previewKind('image/jpeg'), 'image');
  assert.equal(previewKind('image/gif'), 'other'); assert.equal(previewKind('image/svg+xml'), 'other');
  assert.equal(previewKind('text/csv'), 'other'); assert.equal(previewKind(null), 'other');
  assert.equal(sizeText('2048'), '2.0 KB'); assert.equal(sizeText(10), '10 B'); assert.equal(sizeText(null), '—');
});

test('upload receipt uses the returned file address and falls back to the fixed format', () => {
  assert.equal(decodeUploadReceipt({ uploadId: '501', fileId: '901', fileAddress: '/files/v1/901', completedAt: future }).fileAddress, '/files/v1/901');
  assert.equal(decodeUploadReceipt({ uploadId: '501', fileId: '901', completedAt: future }).fileAddress, '/files/v1/901');
  assert.throws(() => decodeUploadReceipt({ uploadId: '501', fileId: '901', fileAddress: '/files/v1/902' }), /Invalid response/);
  assert.throws(() => decodeUploadReceipt({ uploadId: '501', fileId: '901', fileAddress: '/files/v1/0901' }), /Invalid response/);
  assert.throws(() => decodeUploadReceipt({ uploadId: '501', fileId: '901', fileAddress: 901 }), /Invalid response/);
});

test('download grant decodes the effective disposition, defaulting to attachment', () => {
  const url = 'https://minio.local/b/o?sig=1';
  assert.equal(decodeDownloadGrant({ url, expiresAt: future, disposition: 'INLINE' }).disposition, 'INLINE');
  assert.equal(decodeDownloadGrant({ url, expiresAt: future, disposition: 'ATTACHMENT' }).disposition, 'ATTACHMENT');
  assert.equal(decodeDownloadGrant({ url, expiresAt: future }).disposition, 'ATTACHMENT');
  assert.throws(() => decodeDownloadGrant({ url, expiresAt: future, disposition: 'inline' }), /Invalid response/);
});

test('422 draft field errors map to node, edge or start form field', () => {
  const graph: WorkflowGraph = { nodes: [{ key: 'start', type: 'START', name: '开始', position: { x: 0, y: 0 } },
    { key: 'review', type: 'TASK', name: '审核', position: { x: 0, y: 0 } }], edges: [{ key: 'e1', from: 'start', to: 'review' }] };
  const problems = structureProblems([{ field: 'graph.nodes[1].type', message: '取值不合法' }, { field: 'graph.edges[0].from', message: '不能为空' },
    { field: 'startForm.fields[0].type', message: '取值不合法' }, { field: 'graph.settings', message: '未知属性' }], graph,
  { fields: [{ key: 'contract', label: '合同', type: 'FILE', required: true }] });
  assert.deepEqual(problems.map(item => [item.nodeKey, item.edgeKey, item.itemKey]), [['review', null, null], [null, 'e1', null], [null, null, 'contract'], [null, null, null]]);
  assert.match(problems[2].message, /Start form field contract \(startForm\.fields\[0\]\.type\) 取值不合法/);
  assert.equal(problems[3].message, 'graph.settings 未知属性');
  assert.equal(needsLayout(graph), true);
  assert.equal(needsLayout({ ...graph, nodes: [graph.nodes[0], { ...graph.nodes[1], position: { x: 240, y: 0 } }] }), false);
});

test('未通过安全检查的文件：发起与完成同一规则，按 422 路径取回地址，扫描中不挡', () => {
  const backend = '文件未通过安全检查，请删除后重新上传';
  // 前端提示与后端 detail 同一句话：中文种子须包含在后端原文中，英文基准取同一 key。
  const seed = JSON.parse(readFileSync(new URL('../../i18n/zh-CN/workflow.json', import.meta.url), 'utf8')) as Record<string, string>;
  assert.ok(backend.includes(seed['workflow.file.scanFailedText']));
  assert.equal(scanFailedText(), t('file.scanFailedText'));
  const output = { files: ['/files/v1/11', '/files/v1/12'], contract: ['/files/v1/21'] };
  assert.deepEqual(scanFailedAddresses([{ field: 'output.files[1]', message: backend }, { field: 'output.contract[0]', message: backend },
    { field: 'output.files', message: backend }, { field: 'output.files[9]', message: backend }],
  'output', output), ['/files/v1/12', '/files/v1/21']);
  // ops-bff 把远端文案统一替换为「请检查此处配置」（ConfigurationValidationConverter）：按文件元素路径判断，不看文案
  assert.deepEqual(scanFailedAddresses([{ field: 'output.files[0]', message: '请检查此处配置' }], 'output', output), ['/files/v1/11']);
  assert.deepEqual(scanFailedAddresses([{ field: 'startValues.contract[0]', message: '请检查此处配置' }], 'startValues', output), ['/files/v1/21']);
  assert.deepEqual(scanFailedAddresses([{ field: 'startValues.contract[0]', message: backend }], 'startValues', output), ['/files/v1/21']);
  assert.deepEqual(scanFailedAddresses([{ field: 'startValues.contract[0]', message: backend }], 'output', output), []);
  const meta = (id: string, scanStatus: WorkflowFile['scanStatus']): [string, WorkflowFile] => [`/files/v1/${id}`,
    { fileId: id, fileAddress: `/files/v1/${id}`, displayName: null, mimeType: null, sizeBytes: null, scanStatus, uploaderId: null }];
  const known = new Map([meta('1', 'SCANNING'), meta('2', 'QUARANTINED'), meta('3', 'AVAILABLE'), meta('4', 'PENDING_SCAN')]);
  const chosen = ['/files/v1/1', '/files/v1/2', '/files/v1/3', '/files/v1/4', '/files/v1/5'];
  assert.deepEqual(blockingFiles(chosen, known, new Set(['/files/v1/5'])), ['/files/v1/2', '/files/v1/5']);
  assert.deepEqual(blockingFiles(['/files/v1/1', '/files/v1/4'], known, new Set()), []);
  assert.deepEqual(blockingFiles(['/files/v1/1'], undefined, new Set()), []);
});

test('完成动作响应（#19）：只取 actionRunId / status / instanceStatus，不再读取验证字段', () => {
  assert.deepEqual(decodeSubmission({ actionRunId: '9', status: 'COMPLETED', instanceStatus: 'RUNNING', verificationId: '1', failureReason: 'x' }),
    { actionRunId: '9', status: 'COMPLETED', instanceStatus: 'RUNNING' });
  assert.throws(() => decodeSubmission({ actionRunId: '9', status: 'COMPLETED' }));
});

test('只取 total 的计数请求使用 Platform 接受的每页条数（size=1 会被拒绝 400）', () => {
  assert.ok([10, 20, 50, 100].includes(COUNT_PAGE_SIZE));
});

test('422 后已在文件上标红的元素错误不再按字段重复显示，其他字段错误保留', () => {
  const output = { files: ['/files/v1/11', '/files/v1/12'], monthlyVolume: '' };
  const errors = [{ field: 'output.files[1]', message: '文件未通过安全检查，请删除后重新上传' }, { field: 'output.monthlyVolume', message: '必填' },
    { field: 'output.files[5]', message: '请检查此处填写' }];
  assert.deepEqual(withoutInlineFileErrors(errors, 'output', output), [errors[1], errors[2]]);
  assert.deepEqual(withoutInlineFileErrors([], 'output', output), []);
});
