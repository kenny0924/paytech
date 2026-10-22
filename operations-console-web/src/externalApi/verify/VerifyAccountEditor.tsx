import { useCallback, useEffect, useMemo, useRef } from 'react';
import { flushSync } from 'react-dom';
import { Alert, Button, Form, Input } from 'antd';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { session } from '../../api/session';
import type { ConsoleResource } from '../../api/types';
import { ReasonDialog, useReasonFlow } from '../EditorParts';
import { ConflictBanner, Loading, LoadFailure, hasButton, useCopyDraft, useDetail, useEditorSave } from '../externalApiHooks';
import { codePattern, decodeMutation, manageProtocol, replaceQuery } from '../externalApiModel';
import { Field, Mono, Note, PageHeader, Panel, ReadOnlyValue, formatTime } from '../ui';
import type { Crumb } from '../ui';
import { decodeAccount, duplicateNames, toEntries, toRows, verifyPath } from './verifyModel';
import { workspaceCrumbs, workspaceRoutes } from '../../channel/channelWorkspace';
import type { EntryRow, VerifyAccountDetail } from './verifyModel';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void; channelId: string; integrationCode: string; accountCode: string | null }
interface Values {
  accountCode: string; accountName: string; channelCode: string; channelAccountId: string; entityNo?: string;
  parameters: EntryRow[]; credentials: EntryRow[];
}

const crumbsFor = (channelId: string, code: string): Crumb[] => workspaceCrumbs(channelId, code, 'verify');

/** 验证账号：详情（/:accountCode）、编辑（?mode=edit）与新建（/new）。凭据是用户决定的非敏感沙箱材料。 */
export function VerifyAccountEditor({ resources, onAuthorizationChanged, channelId, integrationCode, accountCode }: Props) {
  const location = useLocation();
  const canManage = hasButton(resources, manageProtocol);
  const editing = accountCode === null || canManage && new URLSearchParams(location.search).get('mode') === 'edit';
  const decode = useMemo(() => (value: unknown) => decodeAccount(value, accountCode ?? ''), [accountCode]);
  const detail = useDetail<VerifyAccountDetail>(accountCode ? `${verifyPath(integrationCode)}/verify-accounts/${accountCode}` : null, decode);
  if (accountCode === null && !canManage) return <Alert type="warning" showIcon title="当前没有沙箱验证维护权限" />;
  if (accountCode && detail.loading && !detail.data) return <Loading />;
  if (accountCode && (detail.failure && !detail.data || !detail.data)) {
    return <LoadFailure title="验证账号加载失败" failure={detail.failure} retry={detail.refresh} />;
  }
  if (editing) {
    return <AccountForm key={detail.data?.version ?? 'new'} channelId={channelId} integrationCode={integrationCode} detail={detail.data}
      onAuthorizationChanged={onAuthorizationChanged} onReload={detail.refresh} />;
  }
  return <AccountView channelId={channelId} integrationCode={integrationCode} account={detail.data!} canManage={canManage} />;
}

function AccountView({ channelId, integrationCode, account, canManage }: { channelId: string; integrationCode: string; account: VerifyAccountDetail; canManage: boolean }) {
  const navigate = useNavigate();
  return <>
    <PageHeader crumbs={[...crumbsFor(channelId, integrationCode), { label: account.accountCode }]} title={account.accountName} code={account.accountCode}
      actions={canManage && <Button type="primary" onClick={() => void navigate(replaceQuery('', { mode: 'edit' }))}>编辑</Button>} />
    <div className="xapi-facts">
      <Field label="渠道编码"><Mono>{account.channelCode}</Mono></Field>
      <Field label="渠道账号标识"><Mono>{account.channelAccountId}</Mono></Field>
      <Field label="经营主体编号"><Mono>{account.entityNo ?? '—（服务渠道不绑定 OE）'}</Mono></Field>
      <Field label="最近修改">{formatTime(account.updateTime)} · 账号 {account.updatedBy} · 版本 {account.version}</Field>
    </div>
    <Panel title="普通参数"><EntryTable entries={account.parameters} /></Panel>
    <Panel title="沙箱凭据"><EntryTable entries={account.credentials} /></Panel>
    <Panel title="引用的验证用例">{account.referencedCaseCodes.length ? <ul className="xapi-ref-list">{account.referencedCaseCodes.map(code =>
      <li key={code}><Mono>{code}</Mono><Link to={workspaceRoutes(channelId).verifyCase(code)}>查看</Link></li>)}</ul>
      : <p className="xapi-desc">没有用例引用该账号。</p>}</Panel>
  </>;
}

function EntryTable({ entries }: { entries: Record<string, string> }) {
  const rows = Object.entries(entries);
  if (!rows.length) return <p className="xapi-desc">—</p>;
  return <dl className="xapi-entry-list">{rows.map(([name, value]) => <div key={name}>
    <dt><Mono>{name}</Mono></dt><dd><pre className="xapi-json-block">{value}</pre></dd></div>)}</dl>;
}

function AccountForm({ channelId, integrationCode, detail, onAuthorizationChanged, onReload }: {
  channelId: string; integrationCode: string; detail?: VerifyAccountDetail; onAuthorizationChanged: () => void; onReload: () => void;
}) {
  const [form] = Form.useForm<Values>(); const navigate = useNavigate();
  const editor = useEditorSave(onAuthorizationChanged); const copy = useCopyDraft();
  const creating = !detail; const leaving = useRef(false);
  const path = `${verifyPath(integrationCode)}/verify-accounts`;
  const reasonFlow = useReasonFlow(async reason => {
    const values = form.getFieldsValue(true) as Values;
    const code = creating ? values.accountCode : detail.accountCode;
    const body = { accountName: values.accountName.trim(), channelCode: values.channelCode.trim(),
      channelAccountId: values.channelAccountId.trim(), entityNo: values.entityNo?.trim() || null,
      parameters: toEntries(values.parameters ?? []), credentials: toEntries(values.credentials ?? []), reason };
    const result = await editor.save(creating ? path : `${path}/${code}`, creating ? 'POST' : 'PUT',
      creating ? { accountCode: code, ...body } : { ...body, expectedVersion: detail.version },
      value => decodeMutation(value, 'INTEGRATION_VERIFY_ACCOUNT', `${integrationCode}/${code}`));
    if (!result) return false;
    flushSync(() => editor.setDirty(false));
    if (!leaving.current) void navigate(workspaceRoutes(channelId).verifyAccount(code), { replace: true });
    return true;
  }, () => form.validateFields().then(() => true, () => false));
  const { setSaveForLeave } = editor;
  useEffect(() => setSaveForLeave(() => { leaving.current = true; return reasonFlow.start().finally(() => { leaving.current = false; }); }));
  const latest = useCallback(() => session.request(`${path}/${detail?.accountCode}`, {}, value => decodeAccount(value, detail!.accountCode)),
    [detail, path]);
  const errorFor = (prefix: string) => editor.fieldErrors.filter(error => error.field.startsWith(prefix)).map(error => error.field);
  const entryRules = (name: 'parameters' | 'credentials') => [{ validator: async () => {
    const duplicates = duplicateNames(form.getFieldValue(name) ?? []);
    if (duplicates.length) throw new Error(`名称重复：${duplicates.join('、')}`);
  } }];
  return <>
    <PageHeader crumbs={[...crumbsFor(channelId, integrationCode), { label: creating ? '新建验证账号' : `编辑 ${detail.accountCode}` }]}
      title={creating ? '新建验证账号' : `编辑验证账号 · ${detail.accountCode}`}
      actions={<Button type="primary" loading={editor.saving} onClick={() => void reasonFlow.start()}>保存</Button>} />
    {editor.conflict && detail && <ConflictBanner code={detail.accountCode} baseVersion={detail.version} latest={latest}
      onReload={onReload} onCopy={() => copy(form.getFieldsValue(true))} />}
    <Alert type="info" showIcon title="验证账号只用于沙箱验证运行；按用户决定，密钥以明文保存在 channel-connector 数据库，请勿录入生产账号材料。" />
    <Form form={form} layout="vertical" requiredMark={false} disabled={editor.saving} onValuesChange={() => editor.setDirty(true)}
      initialValues={detail ? { accountCode: detail.accountCode, accountName: detail.accountName, channelCode: detail.channelCode,
        channelAccountId: detail.channelAccountId, entityNo: detail.entityNo ?? '', parameters: toRows(detail.parameters),
        credentials: toRows(detail.credentials) } : { channelCode: integrationCode, parameters: [{ name: '', value: '' }], credentials: [{ name: '', value: '' }] }}>
      <Panel title="基本信息"><div className="xapi-grid xapi-grid-3">
        <Form.Item name="accountCode" label="编码（创建后只读）" rules={[{ required: true, pattern: codePattern, message: '大写字母开头，包含大写字母、数字或下划线' }]}>
          {creating ? <Input maxLength={64} className="xapi-mono-input" placeholder="例如 UAT_MAIN" /> : <ReadOnlyValue mono value={detail.accountCode} />}</Form.Item>
        <Form.Item name="accountName" label="名称" rules={[{ required: true, whitespace: true, message: '请填写名称' }]}><Input maxLength={128} /></Form.Item>
        <span />
        <Form.Item name="channelCode" label="渠道编码" rules={[{ required: true, whitespace: true, message: '请填写渠道编码' }]}>
          <Input maxLength={64} className="xapi-mono-input" placeholder="例如 PAYERMAX" /></Form.Item>
        <Form.Item name="channelAccountId" label="渠道账号标识" rules={[{ required: true, whitespace: true, message: '请填写渠道账号标识' }]}>
          <Input maxLength={64} className="xapi-mono-input" /></Form.Item>
        <Form.Item name="entityNo" label="经营主体编号" rules={[{ required: true, whitespace: true, message: '请填写经营主体编号' }]}>
          <Input maxLength={64} className="xapi-mono-input" /></Form.Item>
      </div>
      <Note items={['渠道编码、账号标识与经营主体编号在流程初始化时与用例输入中的账号身份核对；用例可用 ${account.identity} 直接引用。']} /></Panel>
      <EntryPanel name="parameters" title="普通参数" placeholder="例如 appId" errors={errorFor('parameters')} rules={entryRules('parameters')} />
      <EntryPanel name="credentials" title="沙箱凭据" placeholder="例如 merchantPrivateKey" errors={errorFor('credentials')}
        rules={entryRules('credentials')} multiline />
    </Form>
    <ReasonDialog open={reasonFlow.open} saving={editor.saving} onCancel={reasonFlow.cancel} onConfirm={reason => void reasonFlow.confirm(reason)} />
  </>;
}

function EntryPanel({ name, title, placeholder, errors, rules, multiline }: {
  name: 'parameters' | 'credentials'; title: string; placeholder: string; errors: string[];
  rules: { validator: () => Promise<void> }[]; multiline?: boolean;
}) {
  return <Panel title={title} className="xapi-gap-top" extra={<span className="xapi-desc">最多 64 项，值最多 8192 字符</span>}>
    {errors.length > 0 && <Alert type="error" showIcon title={`以下位置不合法：${errors.join('、')}`} />}
    <Form.List name={name} rules={rules}>{(fields, { add, remove }, { errors: listErrors }) => <>
      {fields.map(field => <div key={field.key} className="xapi-entry-row">
        <Form.Item name={[field.name, 'name']} label="名称" style={{ marginBottom: 0 }}>
          <Input maxLength={64} className="xapi-mono-input" placeholder={placeholder} /></Form.Item>
        <Form.Item name={[field.name, 'value']} label="值" style={{ marginBottom: 0 }}>
          {multiline ? <Input.TextArea autoSize={{ minRows: 1, maxRows: 6 }} maxLength={8192} className="xapi-mono-input" />
            : <Input maxLength={8192} className="xapi-mono-input" />}</Form.Item>
        <button type="button" className="xapi-link" onClick={() => remove(field.name)}>删除</button>
      </div>)}
      <Form.ErrorList errors={listErrors} />
      <div><button type="button" className="xapi-link xapi-link-add" disabled={fields.length >= 64} onClick={() => add({ name: '', value: '' })}>添加</button></div>
    </>}</Form.List>
  </Panel>;
}
