#!/usr/bin/env python3
"""Explicit mor-test acceptance of independent Operations action grants."""
import json
import secrets
import time
import uuid

from smoke import Browser, ROOT
from roles_smoke import account, cleanup, command, nodes, save_role


def check(admin, actor, state):
    by_name = {node['displayName']: node for node in nodes(admin.request('GET', '/iam/v1/role-permission-options')['nodes'])}
    labels = ['创建人员', '禁用人员', '分配角色', '创建角色', '编辑角色']
    assert len({by_name[label]['permissionId'] for label in labels}) == 5
    base = [by_name[name]['permissionId'] for name in ['系统管理', '人员管理', '角色管理']]
    suffix = uuid.uuid4().hex[:12].upper()
    state['role'] = command(admin, 'POST', '/iam/v1/roles', {
        'roleCode': 'QA_ACTION_' + suffix, 'roleName': '独立操作验收 ' + suffix,
        'permissionIds': base, 'reason': '独立操作授权验收',
    })
    email = 'action-check-' + suffix.lower() + '@example.test'
    initial = 'Initial action ' + secrets.token_urlsafe(18) + '!'
    password = 'Changed action ' + secrets.token_urlsafe(18) + '!'
    created = command(admin, 'POST', '/iam/v1/accounts', {
        'email': email, 'initialPassword': initial, 'roleIds': [state['role']['roleId']], 'reason': '独立操作授权验收',
    })
    state['accountId'] = created['accountId']
    actor.login(email, initial)
    actor.change_password(initial, password)
    actor.login(email, password)
    token = actor.access
    action_order = ['创建角色', '编辑角色', '创建人员', '禁用人员', '分配角色']
    paths = {
        '创建人员': ('POST', '/iam/v1/accounts'),
        '禁用人员': ('POST', '/iam/v1/accounts/' + created['accountId'] + '/disable'),
        '分配角色': ('PUT', '/iam/v1/accounts/' + created['accountId'] + '/roles'),
        '创建角色': ('POST', '/iam/v1/roles'),
        '编辑角色': ('PUT', '/iam/v1/roles/' + state['role']['roleId']),
    }
    for label in action_order:
        # Keep this deliberate multi-command acceptance below the existing per-route Guard limits.
        time.sleep(2.1)
        state['role'] = save_role(admin, state['role'], base + [by_name[label]['permissionId']])
        visible = actor.request('GET', '/authorization/v1/navigation')['resources']
        assert {r['displayName'] for r in visible if r['resourceType'] == 'BUTTON'} == {label}
        for other in labels:
            if other != label:
                method, path = paths[other]
                assert command(actor, method, path, {}, status=403)['code'] == 'OPERATIONS_PERMISSION_DENIED'
        exercise_allowed(label, admin, actor, state, suffix)
        assert actor.access == token, 'Action grants must be read live without refreshing JWT'


def exercise_allowed(label, admin, actor, state, suffix):
    if label == '创建角色':
        actor.request('GET', '/iam/v1/role-permission-options')
        body = {'roleCode': 'QA_EMPTY_' + suffix, 'roleName': '独立创建角色验收', 'permissionIds': [], 'reason': '创建权限独立验证'}
        key = str(uuid.uuid4())
        state['emptyRole'] = command(actor, 'POST', '/iam/v1/roles', body, key)
        replay = command(actor, 'POST', '/iam/v1/roles', body, key)
        assert replay['roleId'] == state['emptyRole']['roleId'] and replay['version'] == state['emptyRole']['version']
        actor.request('GET', '/iam/v1/roles/' + replay['roleId'], status=403)
    elif label == '编辑角色':
        actor.request('GET', '/iam/v1/role-permission-options', status=403)
        detail = actor.request('GET', '/iam/v1/roles/' + state['emptyRole']['roleId'])
        state['emptyRole'] = save_role(actor, detail, [], name='独立编辑角色验收')
    elif label == '创建人员':
        target = command(actor, 'POST', '/iam/v1/accounts', {
            'email': 'action-target-' + suffix.lower() + '@example.test',
            'initialPassword': 'Target ' + secrets.token_urlsafe(18) + '!',
            'roleIds': [state['emptyRole']['roleId']], 'reason': '创建人员权限独立验证',
        })
        state['extraAccountId'] = target['accountId']
    elif label == '禁用人员':
        target = account(admin, state['extraAccountId'])
        command(actor, 'POST', '/iam/v1/accounts/' + target['accountId'] + '/disable', {
            'expectedVersion': target['version'], 'reason': '禁用人员权限独立验证',
        })
        assert account(admin, target['accountId'])['status'] == 'DISABLED'
    else:
        current = account(admin, state['accountId'])
        command(actor, 'PUT', '/iam/v1/accounts/' + current['accountId'] + '/roles', {
            'roleIds': [state['role']['roleId']], 'expectedMemberVersion': current['memberVersion'],
            'reason': '分配角色权限独立验证',
        })
        assert account(admin, current['accountId'])['memberVersion'] == current['memberVersion'] + 1


def main():
    material = json.loads((ROOT / 'private/operations-material.json').read_text())
    admin, actor, state = Browser(), Browser(), {}
    admin.login(material['administratorEmail'], material['administratorPassword'])
    try:
        check(admin, actor, state)
    finally:
        if state.get('extraAccountId'):
            target = account(admin, state['extraAccountId'])
            if target['status'] == 'ACTIVE':
                command(admin, 'POST', '/iam/v1/accounts/' + target['accountId'] + '/disable', {
                    'expectedVersion': target['version'], 'reason': '独立操作授权验收结束',
                })
        cleanup(admin, actor, state)
    print('PASS: five distinct action IDs; each single grant permits its actual operation and denies four siblings; create-only idempotency and update-only detail work; original JWT retained.')
    print('Synthetic accounts disabled and roles have no permissions; credentials/tokens not persisted.')


if __name__ == '__main__':
    main()
