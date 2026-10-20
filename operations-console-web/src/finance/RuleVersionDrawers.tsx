import { useMemo, useState } from 'react';
import { Alert, App, Button, Checkbox, Descriptions, Drawer, Form, Input, Select, Spin, Table, Tag } from 'antd';
import { localTime, sideLabels } from './finance';
import type { ChartAccount } from './finance';
import {
  amountSourceLabels, chartAccountLabel, decodeRuleVersion, defaultEffectiveFrom, groupRuleLines, instructionTypeLabels, ledgerWorkflowErrorTargets,
  ruleAccountOptions, ruleBundleName, ruleLinesError, ruleOwnerRoleLabels, ruleVersionActions, ruleVersionBody, ruleVersionStatusLabels, templateRuleLines,
  workflowPaths,
} from './financeWorkflow';
import type { RuleLine, RuleLineValues, RuleVersion, RuleVersionStatus } from './financeWorkflow';
import { FinanceNote } from './FinanceParts';
import { useFinanceCommand } from './useFinanceCommand';
import { ActorText, CommandFeedback, ReviewActions, useDetail, WorkflowTag } from './WorkflowParts';
import type { ReviewAction } from './WorkflowParts';

const labelled = (labels: Readonly<Record<string, string>>) => Object.entries(labels).map(([value, label]) => ({ value, label }));
const bucketOptions = ['NONE', 'PENDING', 'AVAILABLE', 'LOCKED'].map(value => ({ value, label: value }));
const sideOptions = (['DEBIT', 'CREDIT'] as const).map(value => ({ value, label: sideLabels[value] }));
const formFields = ['effectiveFrom', 'lines'];
type VersionAction = 'submit' | 'approve' | 'reject' | 'void';
const expectedStatus: Record<VersionAction, readonly RuleVersionStatus[]> = {
  submit: ['PENDING_APPROVAL'], approve: ['ACTIVE', 'APPROVED'], reject: ['REJECTED'], void: ['VOIDED'],
};
const actionTexts: Record<VersionAction, { label: string; confirm: string; done: string; failure: string }> = {
  submit: { label: '提交审批', confirm: '提交后不能再修改，等待他人审批。确认提交？', done: '已提交，等待审批', failure: '提交规则版本失败' },
  void: { label: '作废草稿', confirm: '作废后该草稿不能再提交，可重新新建版本。确认作废？', done: '草稿已作废', failure: '作废规则版本失败' },
  reject: { label: '驳回', confirm: '驳回后制单人可重新新建版本。确认驳回？', done: '已驳回', failure: '驳回规则版本失败' },
  approve: { label: '审批通过', confirm: '生效时间已到则立即生效、旧版本退役；否则为待生效，到时自动切换。确认审批？', done: '', failure: '审批规则版本失败' },
};

/** 规则行表格：按指令类型分组，科目显示编号 + 名称。 */
export function RuleLinesTable({ lines, accounts }: { lines: readonly (RuleLine & { ruleLineId: string })[]; accounts: ReadonlyMap<string, ChartAccount> }) {
  return <>{groupRuleLines(lines).map(group => <section key={group.instructionType} className="finance-rule-group">
    <h3 className="finance-subtitle">{instructionTypeLabels[group.instructionType] ?? group.instructionType} <span className="cell-meta mono">{group.instructionType}</span></h3>
    <div className="table-panel"><Table<RuleLine & { ruleLineId: string }> rowKey="ruleLineId" size="small" tableLayout="fixed" dataSource={group.lines}
      pagination={false} scroll={{ x: 900 }} columns={[
        { title: '行号', dataIndex: 'lineNo', width: 56 },
        { title: '行代码', width: 170, render: (_, row) => <span className="mono finance-code">{row.lineCode}</span> },
        { title: '金额来源', width: 130, render: (_, row) => amountSourceLabels[row.amountSourceCode] ?? row.amountSourceCode },
        { title: '核算对象', width: 130, render: (_, row) => ruleOwnerRoleLabels[row.ownerRole] ?? row.ownerRole },
        { title: '科目', render: (_, row) => chartAccountLabel(row.accountCode, accounts) },
        { title: '余额桶', width: 100, dataIndex: 'balanceBucket' },
        { title: '方向', width: 56, render: (_, row) => sideLabels[row.entrySide] },
        { title: '必需', width: 60, render: (_, row) => row.requiredFlag ? '是' : '否' },
      ]} /></div>
  </section>)}</>;
}

/**
 * 规则版本详情：规则行与审批状态
 * <li>制单人本人的草稿可提交 / 作废；他人的待审批版本可审批 / 驳回（需复核权限）。四个命令都不带请求体。</li>
 */
export function RuleVersionDrawer({ bundleCode, bundleVer, accounts, canManage, canApprove, actorId, onClose, onChanged, onAuthorizationChanged }: {
  bundleCode: string; bundleVer: string; accounts: ReadonlyMap<string, ChartAccount>; canManage: boolean; canApprove: boolean; actorId: string;
  onClose: () => void; onChanged: () => void; onAuthorizationChanged: () => void;
}) {
  const { message } = App.useApp();
  const decode = useMemo(() => decodeRuleVersion(bundleCode, bundleVer), [bundleCode, bundleVer]);
  const detail = useDetail<RuleVersion>(workflowPaths.ruleVersion(bundleCode, bundleVer), decode, onAuthorizationChanged, '规则版本加载失败');
  const command = useFinanceCommand(onAuthorizationChanged, undefined, [], ledgerWorkflowErrorTargets);
  const version = detail.item;
  async function act(action: VersionAction) {
    const result = await command.run(workflowPaths.ruleVersionAction(bundleCode, bundleVer, action), undefined,
      decodeRuleVersion(bundleCode, bundleVer, expectedStatus[action]), actionTexts[action].failure);
    if (!result) return;
    void message.success(action === 'approve' ? (result.status === 'ACTIVE' ? '已审批并立即生效' : `已审批，将于 ${localTime(result.effectiveFrom)} 生效`)
      : actionTexts[action].done);
    detail.reload(); onChanged();
  }
  const allowed = version ? ruleVersionActions(version, actorId, canManage, canApprove) : null;
  const actions: ReviewAction[] = allowed ? (['void', 'reject', 'submit', 'approve'] as const).filter(action => allowed[action]).map(action => ({
    key: action, label: actionTexts[action].label, danger: action === 'void' || action === 'reject', primary: action === 'submit' || action === 'approve',
    confirm: actionTexts[action].confirm, onConfirm: () => void act(action) })) : [];
  const note = version && version.makerId === actorId && version.status === 'PENDING_APPROVAL' && canApprove ? '你是制单人，不能审批自己的版本'
    : version && version.status === 'DRAFT' && version.makerId !== actorId && canManage ? '只有制单人可以提交或作废该草稿' : undefined;
  return <Drawer open size={1080} rootClassName="finance-drawer finance-page" onClose={() => { if (!command.saving) onClose(); }} destroyOnHidden
    title={`${ruleBundleName(bundleCode)} · 版本 ${bundleVer}`}>
    {detail.error ? <Alert type="warning" showIcon title={detail.error} /> : !version ? <Spin /> : <>
      <CommandFeedback command={command} onReload={detail.reload} />
      <Descriptions column={2} size="small" className="finance-detail-head" items={[
        { key: 'status', label: '状态', children: <WorkflowTag status={version.status} labels={ruleVersionStatusLabels} /> },
        { key: 'bundle', label: '规则包', children: <span>{ruleBundleName(bundleCode)} <span className="cell-meta mono">{bundleCode}</span></span> },
        { key: 'from', label: '生效时间', children: localTime(version.effectiveFrom) },
        { key: 'to', label: '失效时间', children: version.effectiveTo ? localTime(version.effectiveTo) : '—' },
        { key: 'maker', label: '制单人', children: <ActorText value={version.makerId} actorId={actorId} /> },
        { key: 'checker', label: '复核人', children: <ActorText value={version.checkerId} actorId={actorId} /> },
        { key: 'approved', label: '审批时间', children: version.approvedTime ? localTime(version.approvedTime) : '—' },
        { key: 'lines', label: '规则行', children: `${version.lines.length} 行` },
      ]} />
      <RuleLinesTable lines={version.lines} accounts={accounts} />
      <ReviewActions saving={command.saving} note={note} actions={actions} />
    </>}
  </Drawer>;
}

interface EditorValues { effectiveFrom: string; lines: RuleLineValues[] }

/**
 * 新建规则版本：以当前生效版本为模板复制规则行（无生效版本时从空行开始），行编辑器增删改，设置生效时间，保存为草稿
 * <li>页面预检行完整、组内行代码唯一、每个指令类型借贷齐全；科目只列 STANDARD 科目表中启用、可记账、非控制科目；ledger 以 RULE_LINE_INVALID 兜底。</li>
 */
export function CreateRuleVersionDrawer({ bundleCode, currentBundleVer, chartAccounts, accounts, onClose, onCreated, onAuthorizationChanged }: {
  bundleCode: string; currentBundleVer: string | null; chartAccounts: readonly ChartAccount[]; accounts: ReadonlyMap<string, ChartAccount>;
  onClose: () => void; onCreated: (version: RuleVersion) => void; onAuthorizationChanged: () => void;
}) {
  const decode = useMemo(() => decodeRuleVersion(bundleCode, currentBundleVer ?? undefined), [bundleCode, currentBundleVer]);
  const template = useDetail<RuleVersion>(currentBundleVer ? workflowPaths.ruleVersion(bundleCode, currentBundleVer) : null, decode, onAuthorizationChanged, '当前版本加载失败');
  const ready = !currentBundleVer || !!template.item;
  return <Drawer open size="min(1280px, 100vw)" rootClassName="finance-drawer finance-page" title={`新建版本 · ${ruleBundleName(bundleCode)}`} onClose={onClose} destroyOnHidden>
    {template.error ? <Alert type="warning" showIcon title={template.error} /> : !ready ? <Spin />
      : <RuleVersionEditor bundleCode={bundleCode} template={template.item ?? null} chartAccounts={chartAccounts} accounts={accounts}
        onClose={onClose} onCreated={onCreated} onAuthorizationChanged={onAuthorizationChanged} />}
  </Drawer>;
}

function RuleVersionEditor({ bundleCode, template, chartAccounts, accounts, onClose, onCreated, onAuthorizationChanged }: {
  bundleCode: string; template: RuleVersion | null; chartAccounts: readonly ChartAccount[]; accounts: ReadonlyMap<string, ChartAccount>;
  onClose: () => void; onCreated: (version: RuleVersion) => void; onAuthorizationChanged: () => void;
}) {
  const { message } = App.useApp();
  const [form] = Form.useForm<EditorValues>();
  const command = useFinanceCommand(onAuthorizationChanged, form, formFields, ledgerWorkflowErrorTargets);
  const [linesError, setLinesError] = useState<string | null>(null);
  const lineCount = ((Form.useWatch('lines', form) ?? []) as RuleLineValues[]).length;
  const accountOptions = useMemo(() => {
    const options = ruleAccountOptions(chartAccounts);
    // 模板中已不可选的科目仍显示原值，便于看出需要替换
    const missing = (template?.lines ?? []).map(line => line.accountCode).filter(code => !options.some(option => option.value === code));
    return [...options, ...[...new Set(missing)].map(code => ({ value: code, label: `${chartAccountLabel(code, accounts)}（不可用）`, disabled: true }))];
  }, [chartAccounts, template, accounts]);
  async function submit() {
    const values = await form.validateFields();
    const error = ruleLinesError(values.lines ?? []);
    setLinesError(error);
    if (error) return;
    let body: ReturnType<typeof ruleVersionBody>;
    try { body = ruleVersionBody(values.effectiveFrom, values.lines); } catch (failure) {
      form.setFields([{ name: 'effectiveFrom', errors: [failure instanceof Error ? failure.message : '生效时间无效'] }]); return;
    }
    const created = await command.run(workflowPaths.ruleVersions(bundleCode), body, decodeRuleVersion(bundleCode, undefined, ['DRAFT']), '新建规则版本失败');
    if (!created) return;
    void message.success(`已保存草稿版本 ${created.bundleVer}`);
    onCreated(created);
  }
  return <>
    <CommandFeedback command={command} onReload={onClose} />
    {linesError && <Alert type="error" showIcon className="finance-banner" title={linesError} />}
    <Form form={form} name="finance-create-rule-version" layout="vertical" disabled={command.saving || command.conflict}
      initialValues={{ effectiveFrom: defaultEffectiveFrom(), lines: templateRuleLines(template) }}>
      <div className="finance-form-grid">
        <Form.Item name="effectiveFrom" label="生效时间（本地时区）" rules={[{ required: true, message: '请填写生效时间' }]}
          extra="必须晚于当前生效版本；已到的时间审批后立即生效，未来时间审批后到时自动生效。"><Input type="datetime-local" step={1} /></Form.Item>
        <Form.Item label="模板">{template ? `当前生效版本 ${template.bundleVer}（${template.lines.length} 行）` : '无生效版本，从空白开始'}</Form.Item>
      </div>
      <h3 className="finance-subtitle">规则行</h3>
      <Form.List name="lines">
        {(fields, { add, remove }) => <>
          {fields.map(field => { const first = field.name === 0;
            return <div key={field.key} className="finance-line-row finance-rule-line">
              <Form.Item name={[field.name, 'instructionType']} label={first ? '指令类型' : undefined} rules={[{ required: true, message: '必填' }]}>
                <Select options={labelled(instructionTypeLabels)} /></Form.Item>
              <Form.Item name={[field.name, 'lineCode']} label={first ? '行代码' : undefined} rules={[{ required: true, whitespace: true, message: '必填' }]}>
                <Input maxLength={64} /></Form.Item>
              <Form.Item name={[field.name, 'amountSourceCode']} label={first ? '金额来源' : undefined} rules={[{ required: true, message: '必填' }]}>
                <Select options={labelled(amountSourceLabels)} /></Form.Item>
              <Form.Item name={[field.name, 'ownerRole']} label={first ? '核算对象' : undefined} rules={[{ required: true, message: '必填' }]}>
                <Select options={labelled(ruleOwnerRoleLabels)} /></Form.Item>
              <Form.Item name={[field.name, 'accountCode']} label={first ? '科目' : undefined} rules={[{ required: true, message: '必填' }]}>
                <Select showSearch={{ optionFilterProp: 'label' }} options={accountOptions} placeholder="科目编号 / 名称" /></Form.Item>
              <Form.Item name={[field.name, 'balanceBucket']} label={first ? '余额桶' : undefined} rules={[{ required: true, message: '必填' }]}>
                <Select options={bucketOptions} /></Form.Item>
              <Form.Item name={[field.name, 'entrySide']} label={first ? '方向' : undefined} rules={[{ required: true, message: '必填' }]}>
                <Select options={sideOptions} /></Form.Item>
              <Form.Item name={[field.name, 'requiredFlag']} label={first ? '必需' : undefined} valuePropName="checked"><Checkbox /></Form.Item>
              <Button type="text" danger className={first ? 'finance-line-remove is-first' : 'finance-line-remove'} onClick={() => remove(field.name)}>删除</Button>
            </div>; })}
          <Button type="dashed" disabled={fields.length >= 500} onClick={() => add({ balanceBucket: 'NONE', requiredFlag: true })}>添加规则行</Button>
        </>}
      </Form.List>
    </Form>
    <div className="finance-drawer-footer finance-editor-footer">
      <Tag>{lineCount} 行</Tag>
      <span className="finance-spacer" />
      <Button onClick={onClose} disabled={command.saving}>取消</Button>
      <Button type="primary" loading={command.saving} disabled={command.conflict} onClick={() => void submit()}>保存草稿</Button>
    </div>
    <FinanceNote>行号按指令类型内的顺序自动编号。保存后为草稿，制单人提交后由他人审批；有草稿或待审批版本时不能再新建。</FinanceNote>
  </>;
}
