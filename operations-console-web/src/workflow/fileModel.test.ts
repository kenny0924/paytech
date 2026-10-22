import assert from 'node:assert/strict';
import test from 'node:test';
import {
  INLINE_PREVIEW_SUPPORTED, canReverify, countProblem, decodeFiles, decodeUploadGrant, fileAddressesOf, fileAddressOf, fileIdOfAddress, fileIndex, hasFileInput, mimeAllowed,
  needsLayout, outputFileAddresses, preCheckFiles, previewKind, rulesHint, safeTransferUrl, safeUploadHeaders, sizeText, startFileAddresses, structureProblems,
  uploadOne,
} from './fileModel.ts';
import type { UploadPhase, WorkflowPost } from './fileModel.ts';
import type { ActionRun, WorkflowGraph } from './workflowTypes.ts';

const future = new Date(Date.now() + 60_000).toISOString();
const past = new Date(Date.now() - 1_000).toISOString();

test('transfer url accepts only unexpired https without credentials or fragment', () => {
  assert.equal(safeTransferUrl({ url: 'https://minio.local/b/o?X-Amz=1', expiresAt: future }), 'https://minio.local/b/o?X-Amz=1');
  for (const url of ['http://minio.local/b/o', 'https://user:pass@minio.local/o', 'https://minio.local/o#x', 'not a url'])
    assert.throws(() => safeTransferUrl({ url, expiresAt: future }), /无效/);
  assert.throws(() => safeTransferUrl({ url: 'https://minio.local/o', expiresAt: past }), /过期/);
});

test('upload headers refuse session, cookie, proxy and gateway identity headers', () => {
  assert.equal(safeUploadHeaders({ 'Content-Type': 'application/pdf', 'If-None-Match': '*' }).get('if-none-match'), '*');
  for (const name of ['Authorization', 'cookie', 'Proxy-Authorization', 'X-Gateway-Actor-Subject', 'x-caller-service'])
    assert.throws(() => safeUploadHeaders({ [name]: 'x' }), /不允许/);
});

test('pre-check enforces empty files, name length, mime list and max count', () => {
  const rules = { minFiles: 1, maxFiles: 2, mimeTypes: ['application/pdf', 'image/*'] };
  const pdf = { name: 'a.pdf', type: 'application/pdf', size: 10 };
  assert.equal(preCheckFiles(rules, 0, [pdf, { name: 'b.png', type: 'image/png', size: 3 }]), null);
  assert.match(preCheckFiles(rules, 1, [pdf, pdf])!, /最多 2 个/);
  assert.match(preCheckFiles(rules, 0, [{ ...pdf, size: 0 }])!, /空文件/);
  assert.match(preCheckFiles(rules, 0, [{ name: 'x.txt', type: 'text/plain', size: 1 }])!, /text\/plain/);
  assert.match(preCheckFiles(rules, 0, [{ name: 'x.bin', type: '', size: 1 }])!, /application\/octet-stream/);
  assert.match(preCheckFiles({}, 0, [{ ...pdf, name: 'a'.repeat(256) }])!, /255/);
  assert.equal(mimeAllowed('IMAGE/JPEG', ['image/*']), true);
  assert.equal(mimeAllowed('text/plain', []), true);
  assert.equal(countProblem(rules, 0, true), '必填');
  assert.equal(countProblem({ minFiles: 2 }, 1, false), '至少 2 个文件');
  assert.equal(countProblem(rules, 3, false), '最多 2 个文件');
  assert.equal(countProblem(rules, 0, false), null);
  assert.equal(rulesHint(rules), 'application/pdf、image/* · 1—2 个');
  assert.equal(rulesHint({}), '类型不限');
});

const file = (name: string, type: string, bytes: string) => Object.assign(new Blob([bytes], { type }), { name });

test('upload runs start → PUT → completion and returns the file address', async () => {
  const calls: { path: string; body: unknown }[] = []; const phases: UploadPhase[] = [];
  const post: WorkflowPost = async <T,>(path: string, body: unknown, decode: (value: unknown) => T) => {
    calls.push({ path, body });
    return decode(path.endsWith('/completion') ? { uploadId: '501', fileId: '901', completedAt: future }
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
  await assert.rejects(uploadOne(blob, paths, post(grant('https://m/o')), async () => { throw new TypeError('network'); }), /未确认/);
  await assert.rejects(uploadOne(blob, paths, post(grant('http://m/o')), async () => ({ ok: true, status: 200 })), /无效/);
  await assert.rejects(uploadOne(blob, paths, post(grant('https://m/o', 'POST')), async () => ({ ok: true, status: 200 })), /无效/);
  assert.deepEqual(completions, []);
  assert.throws(() => decodeUploadGrant({ uploadId: '0', authorization: { url: 'https://m' } }), /无效/);
});

const run = (extra: Partial<ActionRun>): ActionRun => ({ actionRunId: '1', nodeRunId: 'n', actionKey: 'a', actionType: 'FILE_UPLOAD', required: true,
  assigneeId: '7', status: 'UNVERIFIED', output: { files: ['/files/v1/11', '/files/v1/12'] }, submittedBy: '7', submittedTime: future, ...extra });
const formTemplate = { config: { fields: [{ key: 'contract', label: '合同', type: 'FILE' as const, required: true }, { key: 'note', label: '说明', type: 'TEXT' as const, required: false }] } };

test('file address format: build and parse /files/v1/{fileId}', () => {
  assert.equal(fileAddressOf('901'), '/files/v1/901');
  assert.throws(() => fileAddressOf('0901'), /无效/);
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
  assert.equal(hasFileInput(run({ actionType: 'FORM' }), formTemplate), true);
  assert.equal(hasFileInput(run({ actionType: 'FORM' }), { config: { fields: [] } }), false);
});

test('re-verify only for the assignee of an unverified submitted file action on an active node', () => {
  assert.equal(canReverify(run({}), undefined, '7', true), true);
  assert.equal(canReverify(run({}), undefined, '8', true), false);
  assert.equal(canReverify(run({}), undefined, '7', false), false);
  assert.equal(canReverify(run({ status: 'VERIFIED' }), undefined, '7', true), false);
  assert.equal(canReverify(run({ output: null }), undefined, '7', true), false);
  assert.equal(canReverify(run({ actionType: 'MANUAL_CONFIRM' }), undefined, '7', true), false);
});

test('file metadata decoding, preview kind and size text', () => {
  const decoded = decodeFiles([{ fileId: '1', displayName: 'a.pdf', mimeType: 'application/pdf', sizeBytes: '2048', scanStatus: 'AVAILABLE', uploaderId: '7' },
    { fileId: '2', fileAddress: '/files/v1/2', scanStatus: 'SCANNING' }, { x: 1 }]);
  assert.deepEqual(decoded[0], { fileId: '1', fileAddress: '/files/v1/1', displayName: 'a.pdf', mimeType: 'application/pdf', sizeBytes: '2048',
    scanStatus: 'AVAILABLE', uploaderId: '7' });
  assert.equal(decoded.length, 2);
  assert.equal(fileIndex({ files: decoded }).get('/files/v1/2')?.scanStatus, 'SCANNING');
  assert.deepEqual(decodeFiles(undefined), []);
  assert.equal(INLINE_PREVIEW_SUPPORTED, false);
  assert.equal(previewKind('application/pdf'), 'other'); assert.equal(previewKind('image/png'), 'other');
  assert.equal(previewKind('application/pdf', true), 'pdf'); assert.equal(previewKind('image/png', true), 'image');
  assert.equal(previewKind('text/csv', true), 'other'); assert.equal(previewKind(null, true), 'other');
  assert.equal(sizeText('2048'), '2.0 KB'); assert.equal(sizeText(10), '10 B'); assert.equal(sizeText(null), '—');
});

test('422 draft field errors map to node, edge or start form field', () => {
  const graph: WorkflowGraph = { nodes: [{ key: 'start', type: 'START', name: '开始', position: { x: 0, y: 0 } },
    { key: 'review', type: 'TASK', name: '审核', position: { x: 0, y: 0 } }], edges: [{ key: 'e1', from: 'start', to: 'review' }] };
  const problems = structureProblems([{ field: 'graph.nodes[1].type', message: '取值不合法' }, { field: 'graph.edges[0].from', message: '不能为空' },
    { field: 'startForm.fields[0].type', message: '取值不合法' }, { field: 'graph.settings', message: '未知属性' }], graph,
  { fields: [{ key: 'contract', label: '合同', type: 'FILE', required: true }] });
  assert.deepEqual(problems.map(item => [item.nodeKey, item.edgeKey, item.itemKey]), [['review', null, null], [null, 'e1', null], [null, null, 'contract'], [null, null, null]]);
  assert.match(problems[2].message, /启动表单字段 contract（startForm\.fields\[0\]\.type）取值不合法/);
  assert.equal(problems[3].message, 'graph.settings 未知属性');
  assert.equal(needsLayout(graph), true);
  assert.equal(needsLayout({ ...graph, nodes: [graph.nodes[0], { ...graph.nodes[1], position: { x: 240, y: 0 } }] }), false);
});
