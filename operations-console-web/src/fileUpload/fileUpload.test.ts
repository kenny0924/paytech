import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import { pageCapabilities } from '../navigation/pageCapabilities.ts';
import { testT } from '../i18n/testing.ts';
import {
  canOpenFileRecords, canOpenFileStorage, canUploadFile, decodeUploadCompletionReceipt, decodeUploadConstraints, decodeUploadedFile, decodeUploadedFileFor, decodeUploadedFilePage, fileRecordHref,
  fileUploadPaths, pageLayout, pollUploadedFile, recentFilters, recentRangeDays, recentRangeLabel, routeRuleLabel, startUploadBody,
  uploadErrorMessage, uploadFileButton, uploadRejectionReason, uploadFileIssue, uploaderLabel, uploadValidityLabel, visibleAddresses,
} from './fileUpload.ts';
import type { UploadConstraints, UploadedFile } from './fileUpload.ts';

const t = testT('file');
const constraintsBody = {
  configured: true, accessLevel: 'PUBLIC', routeSourceSystem: 'operations-console-bff', instanceCode: 'S3_TEST', bucketName: 'mor-test-platform-public-k7q2',
  publicBaseUrl: 'https://cdn-test.example.com', scanPolicyCode: 'PUBLIC_DOCUMENT', scanPolicyRevisionNo: 1,
  allowedMimeTypes: ['application/pdf', 'image/png', 'image/jpeg'], maxSizeBytes: '20971520', maxUploadSeconds: 900,
};
const constraints = decodeUploadConstraints(constraintsBody);
const fileId = '239061120044503040';
const available: UploadedFile = {
  fileId, fileAddress: `/files/v1/${fileId}`, displayName: 'brand-logo.png', mimeType: 'image/png', sizeBytes: '86016', scanStatus: 'AVAILABLE',
  versionNo: 1, uploaderAccountId: '238877120983621632', uploadedByMe: true, createTime: '2026-10-24T08:00:00.123456Z',
  publicUrl: 'https://cdn-test.example.com/7d1e9c42', versionedUrl: 'https://cdn-test.example.com/7d1e9c42?v=1',
};
const scanning: UploadedFile = { ...available, scanStatus: 'SCANNING', publicUrl: null, versionedUrl: null };
const rejected: UploadedFile = { ...available, scanStatus: 'REJECTED', publicUrl: null, versionedUrl: null };

test('page registration: /file-upload with the upload button, placed right after File Records; paths follow the BFF contract', () => {
  const index = pageCapabilities.findIndex(p => p.componentKey === 'OPERATIONS_FILE_UPLOAD');
  assert.equal(pageCapabilities[index]?.frontendRoute, '/file-upload');
  assert.equal(pageCapabilities[index - 1]?.componentKey, 'OPERATIONS_FILE_RECORDS');
  assert.deepEqual(pageCapabilities[index]?.actions.map(a => a.resourceCode), [uploadFileButton]);
  assert.equal(fileUploadPaths.constraints, '/file-upload/v1/constraints');
  assert.equal(fileUploadPaths.files, '/file-upload/v1/files');
  assert.equal(fileUploadPaths.file(fileId), `/file-upload/v1/files/${fileId}`);
  assert.equal(fileUploadPaths.uploads, '/file-upload/v1/uploads');
  assert.equal(fileUploadPaths.uploadCompletion('239061120044503041'), '/file-upload/v1/uploads/239061120044503041/completion');
  assert.equal(fileRecordHref(fileId), `/file-records?file=${fileId}`);
});

test('constraints decoder: configured response keeps every field; unconfigured needs only configured and accessLevel', () => {
  assert.deepEqual(constraints, constraintsBody);
  assert.deepEqual(decodeUploadConstraints({ ...constraintsBody, routeSourceSystem: '*', publicBaseUrl: null }).routeSourceSystem, '*');
  const unconfigured = decodeUploadConstraints({ configured: false, accessLevel: 'PUBLIC' });
  assert.equal(unconfigured.configured, false); assert.deepEqual(unconfigured.allowedMimeTypes, []); assert.equal(unconfigured.maxSizeBytes, null);
  assert.equal(decodeUploadConstraints({ configured: false, accessLevel: 'PUBLIC', instanceCode: null, allowedMimeTypes: null }).instanceCode, null);
  for (const invalid of [null, [], { configured: false }, { configured: 'false', accessLevel: 'PUBLIC' }, { configured: false, accessLevel: 'SHARED' },
    { configured: false, accessLevel: 'PUBLIC', extra: 1 }, { ...constraintsBody, extra: 1 }, { ...constraintsBody, allowedMimeTypes: [] },
    { ...constraintsBody, allowedMimeTypes: ['image/png', 'image/png'] }, { ...constraintsBody, allowedMimeTypes: ['png'] },
    { ...constraintsBody, maxSizeBytes: 20971520 }, { ...constraintsBody, maxSizeBytes: '0' }, { ...constraintsBody, maxUploadSeconds: 0 },
    { ...constraintsBody, scanPolicyRevisionNo: '1' }, { ...constraintsBody, instanceCode: 's3_test' }, { ...constraintsBody, publicBaseUrl: 'http://cdn' },
    { ...constraintsBody, routeSourceSystem: 'Ops BFF' }, (({ bucketName: _, ...rest }) => rest)(constraintsBody)]) {
    assert.throws(() => decodeUploadConstraints(invalid), ApiError, JSON.stringify(invalid)?.slice(0, 80));
  }
});

test('file decoder: fixed field set, decimal strings, addresses only on a passed check', () => {
  assert.deepEqual(decodeUploadedFile(available), available);
  assert.deepEqual(decodeUploadedFile(scanning), scanning);
  assert.deepEqual(decodeUploadedFile({ ...available, uploaderAccountId: null, uploadedByMe: false }).uploaderAccountId, null);
  for (const invalid of [{ ...available, extra: 1 }, { ...available, fileId: 239061120044503040 }, { ...available, fileAddress: '/files/v1/1' },
    { ...available, sizeBytes: 86016 }, { ...available, scanStatus: 'DONE' }, { ...available, versionNo: 0 }, { ...available, uploadedByMe: 'true' },
    { ...available, createTime: 'yesterday' }, { ...available, publicUrl: 'http://cdn-test.example.com/x' },
    { ...available, publicUrl: null }, { ...scanning, publicUrl: available.publicUrl }, { ...available, displayName: ' ' }]) {
    assert.throws(() => decodeUploadedFile(invalid), ApiError, JSON.stringify(invalid).slice(0, 80));
  }
  assert.throws(() => decodeUploadedFileFor('239061120044503099')(available), ApiError);
  assert.equal(decodeUploadedFileFor(fileId)(available).fileId, fileId);
});

test('page decoder: same envelope as the file record list; duplicate rows are rejected', () => {
  const page = { items: [available, { ...scanning, fileId: '239061120044503041', fileAddress: '/files/v1/239061120044503041' }], page: 1, size: 10, total: 2 };
  assert.deepEqual(decodeUploadedFilePage(page), page);
  assert.throws(() => decodeUploadedFilePage({ ...page, items: [available, available] }), ApiError);
  assert.throws(() => decodeUploadedFilePage({ ...page, totalPages: 1 }), ApiError);
  assert.throws(() => decodeUploadedFilePage({ items: {}, page: 1, size: 10, total: 0 }), ApiError);
});

test('completion receipt decoder: exactly the six Platform fields, public, address matching the file; no scanStatus', () => {
  const receipt = { uploadId: '239061120044503041', fileId, fileAddress: `/files/v1/${fileId}`, completedAt: '2026-10-24T08:00:01.123456Z',
    versionNo: 1, accessLevel: 'PUBLIC' };
  assert.deepEqual(decodeUploadCompletionReceipt(receipt), receipt);
  for (const invalid of [null, { ...receipt, scanStatus: 'PENDING_SCAN' }, (({ versionNo: _, ...rest }) => rest)(receipt),
    { ...receipt, fileAddress: '/files/v1/1' }, { ...receipt, accessLevel: 'PRIVATE' }, { ...receipt, versionNo: 0 }, { ...receipt, versionNo: '1' },
    { ...receipt, fileId: 239061120044503040 }, { ...receipt, uploadId: 'u-1' }, { ...receipt, completedAt: 'now' }]) {
    assert.throws(() => decodeUploadCompletionReceipt(invalid), ApiError, JSON.stringify(invalid)?.slice(0, 80));
  }
});

test('start body has only filename, mimeType and sizeBytes as a decimal string', () => {
  assert.deepEqual(startUploadBody({ name: 'brand-logo.png', size: 86016, type: 'image/png' }),
    { filename: 'brand-logo.png', mimeType: 'image/png', sizeBytes: '86016' });
});

test('pre-check: type and size follow the constraints; blocked files never reach the API', () => {
  assert.equal(uploadFileIssue({ name: 'a.png', size: 1024, type: 'image/png' }, constraints), null);
  assert.equal(uploadFileIssue({ name: 'a.PNG', size: 1024, type: 'IMAGE/PNG' }, constraints), null);
  assert.equal(uploadFileIssue({ name: 'a.png', size: 20971520, type: 'image/png' }, constraints), null, 'the limit itself is allowed');
  assert.equal(uploadFileIssue({ name: 'a.png', size: 20971521, type: 'image/png' }, constraints), t('upload.issue.tooLarge', { size: '20.0 MB', max: '20 MB' }));
  assert.equal(uploadFileIssue({ name: 'a.csv', size: 10, type: 'text/csv' }, constraints),
    t('upload.issue.typeNotAllowed', { type: 'text/csv', allowed: 'application/pdf, image/png, image/jpeg' }));
  assert.equal(uploadFileIssue({ name: 'a', size: 10, type: '' }, constraints), t('upload.issue.unknownType'));
  assert.equal(uploadFileIssue({ name: 'a.png', size: 0, type: 'image/png' }, constraints), t('upload.issue.empty'));
  assert.equal(uploadFileIssue({ name: 'a.png', size: 10, type: 'image/png' }, decodeUploadConstraints({ configured: false, accessLevel: 'PUBLIC' })),
    t('upload.issue.notConfigured'));
});

/** Fake clock: sleep advances time; each fetch returns the next scripted file. */
function scripted(files: UploadedFile[]) {
  let clock = 0; const calls: number[] = [];
  const fetchFile = async () => { calls.push(clock); return files[Math.min(calls.length - 1, files.length - 1)]; };
  const options = { signal: new AbortController().signal, now: () => clock, sleep: async (ms: number) => { clock += ms; } };
  return { fetchFile, options, calls };
}

test('polling: checks once a second until the file passes', async () => {
  const run = scripted([scanning, scanning, available]);
  const outcome = await pollUploadedFile(run.fetchFile, run.options);
  assert.deepEqual(outcome, { kind: 'AVAILABLE', file: available });
  assert.deepEqual(run.calls, [0, 1000, 2000]);
});

test('polling: a failed check ends at once with the file and no addresses', async () => {
  const run = scripted([scanning, rejected]);
  const outcome = await pollUploadedFile(run.fetchFile, run.options);
  assert.equal(outcome.kind, 'FAILED'); assert.equal(outcome.file && visibleAddresses(outcome.file), null);
  const quarantined = await pollUploadedFile(scripted([{ ...rejected, scanStatus: 'QUARANTINED' }]).fetchFile, scripted([]).options);
  assert.equal(quarantined.kind, 'FAILED');
});

test('polling: gives up after 30 seconds (31 checks at most) and returns the last file', async () => {
  const run = scripted([scanning]);
  const outcome = await pollUploadedFile(run.fetchFile, run.options);
  assert.deepEqual(outcome, { kind: 'TIMEOUT', file: scanning });
  assert.equal(run.calls.length, 31); assert.equal(run.calls.at(-1), 30_000);
});

test('polling: abort and query failures propagate', async () => {
  const controller = new AbortController(); controller.abort();
  await assert.rejects(pollUploadedFile(async () => available, { signal: controller.signal }));
  await assert.rejects(pollUploadedFile(async () => { throw new ApiError(404, 'NOT_FOUND'); }, scripted([]).options), ApiError);
});

test('addresses: public URL, versioned URL and file address only after a passed check', () => {
  assert.deepEqual(visibleAddresses(available), { publicUrl: available.publicUrl, versionedUrl: available.versionedUrl, fileAddress: available.fileAddress });
  assert.equal(visibleAddresses(scanning), null);
  assert.equal(visibleAddresses(rejected), null);
});

test('uploader: "Me" for my uploads, otherwise the account ID', () => {
  assert.equal(uploaderLabel(available), t('upload.recent.me'));
  assert.equal(uploaderLabel({ ...available, uploadedByMe: false }), '238877120983621632');
  assert.equal(uploaderLabel({ uploaderAccountId: null, uploadedByMe: false }), '—');
});

test('permissions and configuration: read-only hides the upload area; unconfigured disables it; Storage and File Records links need those pages', () => {
  const button = { resourceType: 'BUTTON', resourceCode: uploadFileButton };
  const recordsMenu = { resourceType: 'MENU', resourceCode: 'OPERATIONS_FILE_RECORDS', componentKey: 'OPERATIONS_FILE_RECORDS' };
  assert.equal(canUploadFile([button]), true); assert.equal(canUploadFile([{ ...button, resourceType: 'MENU' }]), false);
  assert.equal(canOpenFileRecords([recordsMenu]), true); assert.equal(canOpenFileRecords([button]), false);
  const storageMenu = { resourceType: 'MENU', resourceCode: 'OPERATIONS_FILE_STORAGE', componentKey: 'OPERATIONS_FILE_STORAGE' };
  assert.equal(canOpenFileStorage([storageMenu]), true, 'Go to Storage only with the Storage page');
  assert.equal(canOpenFileStorage([recordsMenu, button]), false, 'otherwise only the contact-administrator notice');
  assert.equal(pageCapabilities.find(p => p.componentKey === 'OPERATIONS_FILE_STORAGE')?.frontendRoute, '/file-storage');
  assert.notEqual(t('upload.constraints.notConfiguredContact'), t('upload.constraints.notConfigured'));
  const unconfigured: UploadConstraints = decodeUploadConstraints({ configured: false, accessLevel: 'PUBLIC' });
  assert.deepEqual(pageLayout(false, constraints), { showUpload: false, uploadEnabled: false, notConfigured: false });
  assert.deepEqual(pageLayout(true, constraints), { showUpload: true, uploadEnabled: true, notConfigured: false });
  assert.deepEqual(pageLayout(true, unconfigured), { showUpload: true, uploadEnabled: false, notConfigured: true });
  assert.deepEqual(pageLayout(false, unconfigured), { showUpload: false, uploadEnabled: false, notConfigured: true });
  assert.deepEqual(pageLayout(true, null), { showUpload: true, uploadEnabled: false, notConfigured: false }, 'disabled until limits load');
});

test('recent filters: last N days ending now, mine as a flag', () => {
  const now = new Date('2026-10-24T08:00:00.000Z');
  assert.deepEqual(recentFilters(7, true, now), { createdFrom: '2026-10-17T08:00:00.000Z', createdTo: '2026-10-24T08:00:00.000Z', mine: 'true' });
  assert.deepEqual(recentFilters(1, false, now).createdFrom, '2026-10-23T08:00:00.000Z');
  assert.deepEqual(recentRangeDays.map(recentRangeLabel), ['Last 24 Hours', 'Last 7 Days', 'Last 31 Days']);
});

test('labels: validity, write rule and server reasons', () => {
  assert.equal(uploadValidityLabel(900), '15 minutes'); assert.equal(uploadValidityLabel(60), '1 minute'); assert.equal(uploadValidityLabel(90), '90 seconds');
  assert.equal(routeRuleLabel('*'), t('upload.constraints.ruleDefault'));
  assert.equal(routeRuleLabel('operations-console-bff'), 'Exact rule (operations-console-bff)');
  assert.equal(uploadErrorMessage(new ApiError(422, 'FILE_TYPE_REJECTED', 'req-1', 'Type not allowed: text/csv')), 'Type not allowed: text/csv');
  assert.equal(uploadErrorMessage(new ApiError(503, 'UNAVAILABLE')), new ApiError(503, 'UNAVAILABLE').message);
  assert.equal(uploadErrorMessage(new ApiError(400, 'INVALID_REQUEST', 'req-1', 'bad', [{ field: 'filename', message: 'required' }])), 'filename: required');
  assert.equal(uploadErrorMessage(new Error('Upload rejected (403)')), 'Upload rejected (403)');
  assert.equal(uploadErrorMessage('x'), t('upload.failed.generic'));
  const policy = new ApiError(422, 'FILE_POLICY_REJECTED', 'req-1', 'server reason');
  assert.equal(uploadRejectionReason(policy, true), t('upload.issue.contentRejected', { detail: 'server reason' }), 'completion 422 = content check failed');
  assert.equal(uploadRejectionReason(policy, false), 'server reason', 'start 422 keeps the server reason');
});
