import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { transformWithOxc } from 'vite';
import { ApiError } from '../api/session.ts';
import type { PermissionNode, RoleResponseBody } from '../api/types.ts';
import { permissionRemovalImpact, permissionSelection, permissionTree, roleCommand, togglePermissionNode } from './roleEditor.ts';

const nodes: PermissionNode[] = [{
  nodeId: 'resource:system', displayName: '系统管理', permissionId: '100', children: [{
    nodeId: 'resource:accounts', displayName: '人员管理', permissionId: '101', children: [
      { nodeId: 'resource:create', displayName: '创建人员', permissionId: '102', children: [] },
      { nodeId: 'resource:disable', displayName: '禁用人员', permissionId: '103', children: [] },
      { nodeId: 'resource:assign', displayName: '分配角色', permissionId: '104', children: [] },
    ],
  }, { nodeId: 'resource:roles', displayName: '角色管理', permissionId: '110', children: [
    { nodeId: 'resource:role-create', displayName: '创建角色', permissionId: '111', children: [] },
    { nodeId: 'resource:role-edit', displayName: '编辑角色', permissionId: '112', children: [] },
  ] }],
}, { nodeId: 'group:other', displayName: '其他权限', permissionId: null,
  children: [{ nodeId: 'permission:200', displayName: '其他能力', permissionId: '200', children: [] }] }];
const values = { roleCode: 'SUPPORT_TEAM', roleName: '  客服角色  ', reason: '  支持日常查询  ' };
const role: RoleResponseBody = {
  roleId: '900000000000000777', roleCode: 'EXISTING_ROLE', roleName: '客服', systemRole: false, version: 7, permissionIds: ['101'],
};
const riskNodes: PermissionNode[] = [{
  nodeId: 'resource:OPERATIONS_RISK_MANAGEMENT', displayName: '风控管理', permissionId: '900000000000000536', children: [{
    nodeId: 'resource:OPERATIONS_RISK_RULE_CONFIGURATION', displayName: '规则配置', permissionId: '900000000000000536', children: [
      { nodeId: 'resource:OPERATIONS_RISK_RULE_SET_SAVE', displayName: '保存为新版本', permissionId: '900000000000000537', children: [] },
      { nodeId: 'resource:OPERATIONS_RISK_RULE_SET_FREEZE', displayName: '冻结版本', permissionId: '900000000000000538', children: [] },
      { nodeId: 'resource:OPERATIONS_RISK_TEST_CANDIDATE_CREATE', displayName: '创建 TEST Candidate', permissionId: '900000000000000539', children: [] },
      { nodeId: 'resource:OPERATIONS_RISK_TEST_CANDIDATE_VALIDATE', displayName: '执行静态校验', permissionId: '900000000000000540', children: [] },
    ],
  }],
}, {
  nodeId: 'resource:OPERATIONS_AUDIT', displayName: '审计日志', permissionId: '900000000000000600', children: [],
}];
const riskRead = '900000000000000536';
const riskSave = '900000000000000537';
const riskWrites = ['900000000000000537', '900000000000000538', '900000000000000539', '900000000000000540'];
const outsideRisk = '900000000000000600';

interface RenderedNode { type: unknown; props: Record<string, unknown> }
interface ConfirmOptions { onOk?: () => void; onCancel?: () => void }

const rolesSource = readFileSync(new URL('Roles.tsx', import.meta.url), 'utf8')
  .replace(/^import .+;\n/gm, '').replace(/^export /gm, '')
  .replace('  return <>', '  capture({ changePermission });\n  return <>');
const { code: compiledRolesSource } = await transformWithOxc(rolesSource, 'Roles.tsx', {
  jsx: { runtime: 'classic', pragma: 'jsx', pragmaFrag: 'Fragment' },
});

function rolePermissionHarness(initialPermissionIds: string[]) {
  const hooks: unknown[] = []; let hook = 0; let captured!: { changePermission(nodeId: string, checked: boolean): void };
  const confirmations: ConfirmOptions[] = [];
  const form = { resetFields() {}, setFieldsValue() {}, submit() {} };
  const Form = { useForm: () => [form], Item: 'Form.Item' };
  const Input = { TextArea: 'Input.TextArea' };
  const dependencies = {
    ApiError, session: { request: async () => { throw new Error('not used'); } },
    useErrorNotification: () => () => {},
    usePaging: () => ({ data: { page: 1, size: 10, total: 0, items: [] }, loading: false, error: null,
      refresh() {}, pagination: { current: 1, pageSize: 10, onChange() {} } }),
    DeleteConfirmationModal: 'DeleteConfirmationModal', roleDeletionCommand: () => ({}),
    permissionRemovalImpact, permissionSelection, permissionTree, roleCommand, togglePermissionNode,
    App: { useApp: () => ({ message: { success() {} }, modal: { confirm(value: ConfirmOptions) { confirmations.push(value); } } }) },
    Form, Input, Fragment: 'Fragment',
    ...Object.fromEntries(['Button', 'Drawer', 'Space', 'Spin', 'Table', 'Tag', 'Tooltip', 'Tree'].map(name => [name, name])),
    jsx: (type: unknown, props: Record<string, unknown> | null, ...children: unknown[]): RenderedNode =>
      ({ type, props: { ...props, children } }),
    useEffect() {}, useMemo<T>(value: () => T) { return value(); },
    useRef<T>(initial: T) { const index = hook++; return hooks[index] ??= { current: initial }; },
    useState<T>(initial: T | (() => T)) {
      const index = hook++;
      if (!(index in hooks)) {
        hooks[index] = index === 0 ? { roleId: null, viewOnly: false } : index === 3 ? riskNodes : index === 4 ? [...initialPermissionIds]
          : typeof initial === 'function' ? (initial as () => T)() : initial;
      }
      return [hooks[index], (next: T | ((previous: T) => T)) => {
        hooks[index] = typeof next === 'function' ? (next as (previous: T) => T)(hooks[index] as T) : next;
      }];
    },
    capture(value: typeof captured) { captured = value; },
  };
  const Roles = new Function(...Object.keys(dependencies), `${compiledRolesSource}\nreturn Roles;`)(...Object.values(dependencies)) as
    (props: { resources: unknown[]; onSaved(): void }) => RenderedNode;
  const rendered = Roles({ resources: [{ resourceType: 'BUTTON', resourceCode: 'OPERATIONS_ROLE_CREATE' }], onSaved() {} });
  function descendants(value: unknown): RenderedNode[] {
    if (Array.isArray(value)) return value.flatMap(descendants);
    if (!value || typeof value !== 'object' || !('props' in value)) return [];
    const node = value as RenderedNode;
    return [node, ...Object.values(node.props).flatMap(descendants)];
  }
  const tree = descendants(rendered).find(node => node.type === 'Tree');
  assert.ok(tree, 'the real Roles permission Tree is rendered');
  return {
    click(nodeId: string, antChecked: boolean) {
      (tree.props.onCheck as (keys: unknown, info: { node: { key: string }; checked: boolean }) => void)([], {
        node: { key: nodeId }, checked: antChecked,
      });
    },
    get permissionIds() { return hooks[4] as string[]; },
    confirmations,
  };
}

test('checking one action selects its access ancestors without selecting sibling actions', () => {
  const selected = togglePermissionNode(nodes, [], 'resource:create', true);
  assert.deepEqual(selected, ['100', '101', '102']);
  assert.deepEqual(permissionSelection(nodes, selected), {
    checked: ['resource:create'], halfChecked: ['resource:system', 'resource:accounts'],
  });
  assert.deepEqual(togglePermissionNode(nodes, selected, 'resource:disable', true), ['100', '101', '102', '103']);
});

test('checking a parent selects every descendant and ancestor without another branch', () => {
  const selected = togglePermissionNode(nodes, [], 'resource:accounts', true);
  assert.deepEqual(selected, ['100', '101', '102', '103', '104']);
  assert.deepEqual(permissionSelection(nodes, selected), {
    checked: ['resource:accounts', 'resource:create', 'resource:disable', 'resource:assign'],
    halfChecked: ['resource:system'],
  });
  const all = togglePermissionNode(nodes, [], 'resource:system', true);
  assert.deepEqual(all, ['100', '101', '102', '103', '104', '110', '111', '112']);
  assert.deepEqual(permissionSelection(nodes, all).halfChecked, []);
});

test('unchecking a parent clears its subtree while unchecking a leaf preserves siblings and access', () => {
  const all = togglePermissionNode(nodes, [], 'resource:system', true);
  const withoutLeaf = togglePermissionNode(nodes, all, 'resource:create', false);
  assert.deepEqual(withoutLeaf, ['100', '101', '103', '104', '110', '111', '112']);
  assert.deepEqual(permissionSelection(nodes, withoutLeaf).halfChecked, ['resource:system', 'resource:accounts']);
  assert.deepEqual(togglePermissionNode(nodes, all, 'resource:accounts', false), ['100', '110', '111', '112']);
  assert.deepEqual(togglePermissionNode(nodes, [...all, '200'], 'resource:system', false), ['200']);
});

test('saved parent read permission renders partial selection without granting any descendants', () => {
  const saved = ['100', '101'];
  assert.deepEqual(permissionSelection(nodes, saved), { checked: [], halfChecked: ['resource:system', 'resource:accounts'] });
  assert.deepEqual(saved, ['100', '101']);
  assert.deepEqual(JSON.parse(roleCommand(values, saved, role, null).payload).permissionIds, saved);
  assert.deepEqual(permissionSelection(nodes, ['102']), {
    checked: ['resource:create'], halfChecked: ['resource:system', 'resource:accounts'],
  });
});

test('a parent remains partial when its own access permission is missing even if descendants are selected', () => {
  assert.deepEqual(permissionSelection(nodes, ['102', '103', '104']), {
    checked: ['resource:create', 'resource:disable', 'resource:assign'],
    halfChecked: ['resource:system', 'resource:accounts'],
  });
  assert.deepEqual(permissionSelection(nodes, []), { checked: [], halfChecked: [] });
});

test('structural groups batch select their permissions without submitting a group ID', () => {
  const tree = permissionTree(nodes);
  assert.equal(tree[1].permissionId, null);
  const selected = togglePermissionNode(nodes, [], 'group:other', true);
  assert.deepEqual(selected, ['200']);
  assert.deepEqual(permissionSelection(nodes, selected), { checked: ['group:other', 'permission:200'], halfChecked: [] });
  assert.deepEqual(togglePermissionNode(nodes, selected, 'group:other', false), []);
  assert.deepEqual(JSON.parse(roleCommand(values, selected, null, null).payload).permissionIds, ['200']);
});

test('repeated permission IDs remain one actual grant across resource nodes', () => {
  const aliases: PermissionNode[] = [nodes[0], { nodeId: 'resource:alias', displayName: '相同真实权限', permissionId: '102', children: [] }];
  const selected = togglePermissionNode(aliases, [], 'resource:create', true);
  assert.deepEqual(selected, ['100', '101', '102']);
  assert.deepEqual(permissionSelection(aliases, selected).checked, ['resource:create', 'resource:alias']);
  const cleared = togglePermissionNode(aliases, selected, 'resource:alias', false);
  assert.deepEqual(cleared, ['100', '101']);
  assert.deepEqual(permissionSelection(aliases, cleared).checked, []);
});

test('each Risk write selects the shared READ permission and only that write once', () => {
  const writes = [
    ['resource:OPERATIONS_RISK_RULE_SET_SAVE', '900000000000000537'],
    ['resource:OPERATIONS_RISK_RULE_SET_FREEZE', '900000000000000538'],
    ['resource:OPERATIONS_RISK_TEST_CANDIDATE_CREATE', '900000000000000539'],
    ['resource:OPERATIONS_RISK_TEST_CANDIDATE_VALIDATE', '900000000000000540'],
  ] as const;
  for (const [nodeId, permissionId] of writes) {
    assert.deepEqual(togglePermissionNode(riskNodes, [], nodeId, true), ['900000000000000536', permissionId]);
  }
});

test('Risk READ aliases report the complete selected subtree and strictly descendant impact', () => {
  const selected = ['900000000000000536', '900000000000000537', '900000000000000538',
    '900000000000000539', '900000000000000540', '900000000000000600'];
  assert.deepEqual(permissionRemovalImpact(riskNodes, selected, 'resource:OPERATIONS_RISK_RULE_CONFIGURATION'), {
    removedPermissionIds: ['900000000000000536', '900000000000000537', '900000000000000538',
      '900000000000000539', '900000000000000540'],
    removedDescendantPermissionIds: ['900000000000000537', '900000000000000538',
      '900000000000000539', '900000000000000540'],
  });
  assert.deepEqual(permissionRemovalImpact(riskNodes, selected, 'resource:OPERATIONS_RISK_MANAGEMENT'), {
    removedPermissionIds: ['900000000000000536', '900000000000000537', '900000000000000538',
      '900000000000000539', '900000000000000540'],
    removedDescendantPermissionIds: ['900000000000000536', '900000000000000537',
      '900000000000000538', '900000000000000539', '900000000000000540'],
  });
  assert.deepEqual(togglePermissionNode(riskNodes, selected, 'resource:OPERATIONS_RISK_RULE_CONFIGURATION', false),
    ['900000000000000600'], 'a sibling outside the Risk subtree remains selected');
});

test('removal impact is deduplicated, sorted and empty below a leaf', () => {
  assert.deepEqual(permissionRemovalImpact(riskNodes,
    ['900000000000000600', '900000000000000537', '900000000000000537'],
    'resource:OPERATIONS_RISK_RULE_SET_SAVE'), {
    removedPermissionIds: ['900000000000000537'], removedDescendantPermissionIds: [],
  });
  assert.throws(() => permissionRemovalImpact(riskNodes, [], 'resource:missing'),
    (error: unknown) => error instanceof ApiError && error.code === 'INVALID_RESPONSE');
});

for (const nodeId of ['resource:OPERATIONS_RISK_MANAGEMENT', 'resource:OPERATIONS_RISK_RULE_CONFIGURATION']) {
  test(`${nodeId} treats Ant halfChecked checked=true as removal and never bulk-grants writes`, () => {
    const selected = [riskRead, riskSave, outsideRisk];
    const cancelled = rolePermissionHarness(selected);
    cancelled.click(nodeId, true);
    assert.deepEqual(cancelled.permissionIds, selected, 'dependent removal waits for confirmation');
    assert.equal(cancelled.confirmations.length, 1);
    cancelled.confirmations[0]!.onCancel?.();
    assert.deepEqual(cancelled.permissionIds, selected, 'cancel preserves every permission');

    const confirmed = rolePermissionHarness(selected);
    confirmed.click(nodeId, true);
    confirmed.confirmations[0]!.onOk?.();
    assert.deepEqual(confirmed.permissionIds, [outsideRisk], 'confirmation removes Risk while preserving its outside sibling');
  });

  test(`${nodeId} removes READ-only selection and adds only when its subtree is empty`, () => {
    const readOnly = rolePermissionHarness([riskRead, outsideRisk]);
    readOnly.click(nodeId, true);
    assert.ok(riskWrites.every(id => !readOnly.permissionIds.includes(id)), 'READ-only click cannot grant a write');
    readOnly.confirmations[0]?.onOk?.();
    assert.deepEqual(readOnly.permissionIds, [outsideRisk]);

    const empty = rolePermissionHarness([outsideRisk]);
    empty.click(nodeId, false);
    assert.deepEqual(empty.permissionIds, [riskRead, ...riskWrites, outsideRisk].sort(),
      'an empty subtree is the only state that adds the whole selected branch');
  });

  test(`${nodeId} removes a fully selected branch independently of Ant checked metadata`, () => {
    const full = rolePermissionHarness([riskRead, ...riskWrites, outsideRisk]);
    full.click(nodeId, true);
    assert.equal(full.confirmations.length, 1);
    full.confirmations[0]!.onOk?.();
    assert.deepEqual(full.permissionIds, [outsideRisk]);
  });
}

test('Roles confirms descendant removal before the false toggle callback', () => {
  const source = readFileSync(new URL('Roles.tsx', import.meta.url), 'utf8');
  assert.match(source, /permissionRemovalImpact\(nodes, current, nodeId\)/);
  assert.match(source, /if \(impact\.removedPermissionIds\.length === 0\) \{[\s\S]*?togglePermissionNode\(nodes, current, nodeId, true\)[\s\S]*?return;/);
  assert.match(source, /if \(impact\.removedDescendantPermissionIds\.length === 0\) \{[\s\S]*?togglePermissionNode\(nodes, current, nodeId, remove\)[\s\S]*?return;/);
  assert.match(source, /modal\.confirm\(\{[\s\S]*?onOk[\s\S]*?togglePermissionNode\(nodes, current, nodeId, false\)[\s\S]*?\}\)/);
  assert.match(source, /onCheck=\{editable \? \(_, info\) => changePermission\(info\.node\.key\) : undefined\}/);
  assert.equal(source.match(/togglePermissionNode\(nodes, current, nodeId, false\)/g)?.length, 1,
    'only the confirmation callback contains an explicit destructive subtree toggle');
});

test('invalid trees reject duplicate resource keys and malformed descendants before display', () => {
  for (const invalid of [null, [{ ...nodes[0], children: null }], [nodes[0], nodes[0]],
    [{ ...nodes[0], permissionId: 100 }]]) {
    assert.throws(() => permissionTree(invalid as unknown as PermissionNode[]),
      (error: unknown) => error instanceof ApiError && error.code === 'INVALID_RESPONSE');
  }
});

test('create and update submit only approved fields and allow clearing all permissions', () => {
  const create = roleCommand(values, ['102', '101', '102'], null, null);
  assert.equal(create.path, '/iam/v1/roles'); assert.equal(create.method, 'POST');
  assert.deepEqual(JSON.parse(create.payload), {
    roleCode: 'SUPPORT_TEAM', roleName: '客服角色', permissionIds: ['101', '102'], reason: '支持日常查询',
  });
  const update = roleCommand(values, [], role, null);
  assert.equal(update.path, '/iam/v1/roles/900000000000000777'); assert.equal(update.method, 'PUT');
  assert.deepEqual(JSON.parse(update.payload), {
    roleName: '客服角色', permissionIds: [], expectedVersion: 7, reason: '支持日常查询',
  });
});

test('retries preserve the key for the same permission set but a new version or payload gets a new key', () => {
  const first = roleCommand(values, ['101', '102'], role, null);
  const retry = roleCommand(values, ['102', '101', '102'], role, first);
  assert.equal(retry.key, first.key); assert.equal(retry.payload, first.payload);
  assert.notEqual(roleCommand({ ...values, reason: '调整权限' }, ['101', '102'], role, first).key, first.key);
  assert.notEqual(roleCommand(values, ['101'], role, first).key, first.key);
  assert.notEqual(roleCommand(values, ['101', '102'], { ...role, version: 8 }, first).key, first.key);
  assert.notEqual(roleCommand(values, ['101', '102'], { ...role, roleId: '900000000000000778' }, first).key, first.key);
});
