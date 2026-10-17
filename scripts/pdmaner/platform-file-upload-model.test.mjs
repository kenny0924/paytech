import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const root = new URL('../../', import.meta.url);
const model = JSON.parse(readFileSync(new URL('paytech/platform-support/docs/database/platform-support.pdma.json', root)));
const proposal = JSON.parse(readFileSync(new URL('requirements/20260914/client-console-注册与客户入驻/db/file-upload-closure-proposal.json', root)));
const entity = name => model.entities.find(item => item.defKey === name);

test('upload persists the approved exact target and recovery facts without a second upload secret', () => {
  const upload = entity('t_file_upload');
  assert.equal(upload.fields.length, 18);
  for (const expected of proposal.proposedUploadFields) {
    const actual = upload.fields.find(field => field.defKey === expected.name);
    assert.ok(actual, expected.name);
    assert.equal(actual.type + (actual.len ? `(${actual.len})` : ''), expected.type);
    assert.equal(actual.notNull, true);
    assert.equal(actual.comment, expected.comment + (expected.name === 'owner_type' ? '取值来源：Platform 受控文件 Owner 类型目录；非固定枚举。' : ''));
  }
  assert.ok(!upload.fields.some(field => field.defKey === 'upload_token_hash'));
  const target = upload.indexes.find(index => index.defKey === 'uk_upload_storage_target');
  assert.deepEqual(target.fields.map(ref => upload.fields.find(field => field.id === ref.fieldDefKey).defKey),
    ['storage_provider', 'storage_container_ref', 'storage_object_ref']);
});

test('Platform scan recovery uses the Foundation fifteen-column identity and lease contract', () => {
  const task = entity('t_compensation_event');
  assert.deepEqual(task.fields.map(field => field.defKey), ['id', 'event_key', 'event_type', 'sub_event_type',
    'payload_json', 'execute_status', 'execute_count', 'next_execute_at', 'lock_owner', 'lock_until',
    'last_error_code', 'last_error_summary', 'lock_version', 'create_time', 'update_time']);
  for (const name of ['event_key', 'event_type', 'sub_event_type']) {
    assert.equal(task.fields.find(field => field.defKey === name).extProps.collation, 'utf8mb4_0900_bin');
  }
  assert.equal(task.fields.find(field => field.defKey === 'payload_json').notNull, false);
  assert.equal(task.fields.find(field => field.defKey === 'lock_version').defaultValue, '1');
});
