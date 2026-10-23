import { useMemo, useState } from 'react';
import { Alert, App, Button, Checkbox, Descriptions, Drawer, Form, Input, Select, Spin, Table, Tag } from 'antd';
import { enumLabel } from '../i18n/index';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';
import { localTime } from './finance';
import type { ChartAccount } from './finance';
import {
  amountSourceCodes, chartAccountLabel, decodeRuleVersion, defaultEffectiveFrom, groupRuleLines, instructionTypes, ledgerWorkflowErrorTargets,
  ruleAccountOptions, ruleBundleName, ruleLinesError, ruleOwnerRoles, ruleVersionActions, ruleVersionBody, templateRuleLines,
  workflowPaths,
} from './financeWorkflow';
import type { RuleLine, RuleLineValues, RuleVersion, RuleVersionStatus } from './financeWorkflow';
import { FinanceNote } from './FinanceParts';
import { useFinanceCommand } from './useFinanceCommand';
import { ActorText, CommandFeedback, ReviewActions, useDetail, WorkflowTag } from './WorkflowParts';
import type { ReviewAction } from './WorkflowParts';

const labelled = (t: Translate, type: string, values: readonly string[]) => values.map(value => ({ value, label: enumLabel(t, 'enum.' + type, value) }));
const bucketOptions = ['NONE', 'PENDING', 'AVAILABLE', 'LOCKED'].map(value => ({ value, label: value }));
const formFields = ['effectiveFrom', 'lines'];
type VersionAction = 'submit' | 'approve' | 'reject' | 'void';
const expectedStatus: Record<VersionAction, readonly RuleVersionStatus[]> = {
  submit: ['PENDING_APPROVAL'], approve: ['ACTIVE', 'APPROVED'], reject: ['REJECTED'], void: ['VOIDED'],
};
/** 各命令的文案 key（finance.rules.action.<命令>.label / confirm / done / failure）；审批成功提示按生效状态另取。 */
const actionText = (t: Translate, action: VersionAction, part: 'label' | 'confirm' | 'done' | 'failure') => t(`rules.action.${action}.${part}`);

/** 规则行表格：按指令类型分组，科目显示编号 + 名称。 */
export function RuleLinesTable({ lines, accounts }: { lines: readonly (RuleLine & { ruleLineId: string })[]; accounts: ReadonlyMap<string, ChartAccount> }) {
  const t = useT('finance');
  return <>{groupRuleLines(lines).map(group => <section key={group.instructionType} className="finance-rule-group">
    <h3 className="finance-subtitle">{enumLabel(t, 'enum.instructionType', group.instructionType)} <span className="cell-meta mono">{group.instructionType}</span></h3>
    <div className="table-panel"><Table<RuleLine & { ruleLineId: string }> rowKey="ruleLineId" size="small" tableLayout="fixed" dataSource={group.lines}
      pagination={false} scroll={{ x: 900 }} columns={[
        { title: t('field.lineNo'), dataIndex: 'lineNo', width: 56 },
        { title: t('rules.field.lineCode'), width: 170, render: (_, row) => <span className="mono finance-code">{row.lineCode}</span> },
        { title: t('rules.field.amountSource'), width: 130, render: (_, row) => enumLabel(t, 'enum.amountSource', row.amountSourceCode) },
        { title: t('field.accountingDimension'), width: 130, render: (_, row) => enumLabel(t, 'enum.ruleOwnerRole', row.ownerRole) },
        { title: t('field.accountCode'), render: (_, row) => chartAccountLabel(row.accountCode, accounts) },
        { title: t('field.balanceBucket'), width: 100, dataIndex: 'balanceBucket' },
        { title: t('field.side'), width: 56, render: (_, row) => enumLabel(t, 'enum.side', row.entrySide) },
        { title: t('rules.field.required'), width: 60, render: (_, row) => row.requiredFlag ? t('common.yes') : t('common.no') },
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
  const t = useT('finance');
  const { message } = App.useApp();
  const decode = useMemo(() => decodeRuleVersion(bundleCode, bundleVer), [bundleCode, bundleVer]);
  const detail = useDetail<RuleVersion>(workflowPaths.ruleVersion(bundleCode, bundleVer), decode, onAuthorizationChanged, t('rules.versionLoadFailed'));
  const command = useFinanceCommand(onAuthorizationChanged, undefined, [], ledgerWorkflowErrorTargets);
  const version = detail.item;
  async function act(action: VersionAction) {
    const result = await command.run(workflowPaths.ruleVersionAction(bundleCode, bundleVer, action), undefined,
      decodeRuleVersion(bundleCode, bundleVer, expectedStatus[action]), actionText(t, action, 'failure'));
    if (!result) return;
    void message.success(action === 'approve' ? (result.status === 'ACTIVE' ? t('rules.approvedActive') : t('rules.approvedScheduled', { time: localTime(result.effectiveFrom) }))
      : actionText(t, action, 'done'));
    detail.reload(); onChanged();
  }
  const allowed = version ? ruleVersionActions(version, actorId, canManage, canApprove) : null;
  const actions: ReviewAction[] = allowed ? (['void', 'reject', 'submit', 'approve'] as const).filter(action => allowed[action]).map(action => ({
    key: action, label: actionText(t, action, 'label'), danger: action === 'void' || action === 'reject', primary: action === 'submit' || action === 'approve',
    confirm: actionText(t, action, 'confirm'), onConfirm: () => void act(action) })) : [];
  const note = version && version.makerId === actorId && version.status === 'PENDING_APPROVAL' && canApprove ? t('rules.selfReview')
    : version && version.status === 'DRAFT' && version.makerId !== actorId && canManage ? t('rules.makerOnly') : undefined;
  return <Drawer open size={1080} rootClassName="finance-drawer finance-page" onClose={() => { if (!command.saving) onClose(); }} destroyOnHidden
    title={t('rules.versionTitle', { bundle: ruleBundleName(bundleCode), bundleVer })}>
    {detail.error ? <Alert type="warning" showIcon title={detail.error} /> : !version ? <Spin /> : <>
      <CommandFeedback command={command} onReload={detail.reload} />
      <Descriptions column={2} size="small" className="finance-detail-head" items={[
        { key: 'status', label: t('field.status'), children: <WorkflowTag status={version.status} enumType="ruleVersionStatus" /> },
        { key: 'bundle', label: t('rules.field.bundle'), children: <span>{ruleBundleName(bundleCode)} <span className="cell-meta mono">{bundleCode}</span></span> },
        { key: 'from', label: t('rules.field.effectiveFrom'), children: localTime(version.effectiveFrom) },
        { key: 'to', label: t('rules.field.effectiveTo'), children: version.effectiveTo ? localTime(version.effectiveTo) : '—' },
        { key: 'maker', label: t('workflow.maker'), children: <ActorText value={version.makerId} actorId={actorId} /> },
        { key: 'checker', label: t('workflow.checker'), children: <ActorText value={version.checkerId} actorId={actorId} /> },
        { key: 'approved', label: t('workflow.approvedAt'), children: version.approvedTime ? localTime(version.approvedTime) : '—' },
        { key: 'lines', label: t('rules.field.lines'), children: t('rules.lineCount', { count: version.lines.length }) },
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
  const t = useT('finance');
  const decode = useMemo(() => decodeRuleVersion(bundleCode, currentBundleVer ?? undefined), [bundleCode, currentBundleVer]);
  const template = useDetail<RuleVersion>(currentBundleVer ? workflowPaths.ruleVersion(bundleCode, currentBundleVer) : null, decode, onAuthorizationChanged, t('rules.currentLoadFailed'));
  const ready = !currentBundleVer || !!template.item;
  return <Drawer open size="min(1280px, 100vw)" rootClassName="finance-drawer finance-page" title={t('rules.createTitle', { bundle: ruleBundleName(bundleCode) })} onClose={onClose} destroyOnHidden>
    {template.error ? <Alert type="warning" showIcon title={template.error} /> : !ready ? <Spin />
      : <RuleVersionEditor bundleCode={bundleCode} template={template.item ?? null} chartAccounts={chartAccounts} accounts={accounts}
        onClose={onClose} onCreated={onCreated} onAuthorizationChanged={onAuthorizationChanged} />}
  </Drawer>;
}

function RuleVersionEditor({ bundleCode, template, chartAccounts, accounts, onClose, onCreated, onAuthorizationChanged }: {
  bundleCode: string; template: RuleVersion | null; chartAccounts: readonly ChartAccount[]; accounts: ReadonlyMap<string, ChartAccount>;
  onClose: () => void; onCreated: (version: RuleVersion) => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('finance');
  const { message } = App.useApp();
  const [form] = Form.useForm<EditorValues>();
  const sideOptions = useMemo(() => labelled(t, 'side', ['DEBIT', 'CREDIT']), [t]);
  const command = useFinanceCommand(onAuthorizationChanged, form, formFields, ledgerWorkflowErrorTargets);
  const [linesError, setLinesError] = useState<string | null>(null);
  const lineCount = ((Form.useWatch('lines', form) ?? []) as RuleLineValues[]).length;
  const accountOptions = useMemo(() => {
    const options = ruleAccountOptions(chartAccounts);
    // 模板中已不可选的科目仍显示原值，便于看出需要替换
    const missing = (template?.lines ?? []).map(line => line.accountCode).filter(code => !options.some(option => option.value === code));
    return [...options, ...[...new Set(missing)].map(code => ({ value: code, label: t('rules.unavailableAccount', { account: chartAccountLabel(code, accounts) }), disabled: true }))];
  }, [chartAccounts, template, accounts, t]);
  async function submit() {
    const values = await form.validateFields();
    const error = ruleLinesError(values.lines ?? []);
    setLinesError(error);
    if (error) return;
    let body: ReturnType<typeof ruleVersionBody>;
    try { body = ruleVersionBody(values.effectiveFrom, values.lines); } catch (failure) {
      form.setFields([{ name: 'effectiveFrom', errors: [failure instanceof Error ? failure.message : t('rules.effectiveFromInvalid')] }]); return;
    }
    const created = await command.run(workflowPaths.ruleVersions(bundleCode), body, decodeRuleVersion(bundleCode, undefined, ['DRAFT']), t('rules.createFailed'));
    if (!created) return;
    void message.success(t('rules.draftSaved', { bundleVer: created.bundleVer }));
    onCreated(created);
  }
  return <>
    <CommandFeedback command={command} onReload={onClose} />
    {linesError && <Alert type="error" showIcon className="finance-banner" title={linesError} />}
    <Form form={form} name="finance-create-rule-version" layout="vertical" disabled={command.saving || command.conflict}
      initialValues={{ effectiveFrom: defaultEffectiveFrom(), lines: templateRuleLines(template) }}>
      <div className="finance-form-grid">
        <Form.Item name="effectiveFrom" label={t('rules.field.effectiveFromLocal')} rules={[{ required: true, message: t('rules.effectiveFromRequired') }]}
          extra={t('rules.effectiveFromHint')}><Input type="datetime-local" step={1} /></Form.Item>
        <Form.Item label={t('rules.field.template')}>{template ? t('rules.templateCurrent', { bundleVer: template.bundleVer, count: template.lines.length }) : t('rules.templateBlank')}</Form.Item>
      </div>
      <h3 className="finance-subtitle">{t('rules.field.lines')}</h3>
      <Form.List name="lines">
        {(fields, { add, remove }) => <>
          {fields.map(field => { const first = field.name === 0;
            return <div key={field.key} className="finance-line-row finance-rule-line">
              <Form.Item name={[field.name, 'instructionType']} label={first ? t('journal.field.instructionType') : undefined} rules={[{ required: true, message: t('validation.required') }]}>
                <Select options={labelled(t, 'instructionType', instructionTypes)} /></Form.Item>
              <Form.Item name={[field.name, 'lineCode']} label={first ? t('rules.field.lineCode') : undefined} rules={[{ required: true, whitespace: true, message: t('validation.required') }]}>
                <Input maxLength={64} /></Form.Item>
              <Form.Item name={[field.name, 'amountSourceCode']} label={first ? t('rules.field.amountSource') : undefined} rules={[{ required: true, message: t('validation.required') }]}>
                <Select options={labelled(t, 'amountSource', amountSourceCodes)} /></Form.Item>
              <Form.Item name={[field.name, 'ownerRole']} label={first ? t('field.accountingDimension') : undefined} rules={[{ required: true, message: t('validation.required') }]}>
                <Select options={labelled(t, 'ruleOwnerRole', ruleOwnerRoles)} /></Form.Item>
              <Form.Item name={[field.name, 'accountCode']} label={first ? t('field.accountCode') : undefined} rules={[{ required: true, message: t('validation.required') }]}>
                <Select showSearch={{ optionFilterProp: 'label' }} options={accountOptions} placeholder={t('rules.accountPlaceholder')} /></Form.Item>
              <Form.Item name={[field.name, 'balanceBucket']} label={first ? t('field.balanceBucket') : undefined} rules={[{ required: true, message: t('validation.required') }]}>
                <Select options={bucketOptions} /></Form.Item>
              <Form.Item name={[field.name, 'entrySide']} label={first ? t('field.side') : undefined} rules={[{ required: true, message: t('validation.required') }]}>
                <Select options={sideOptions} /></Form.Item>
              <Form.Item name={[field.name, 'requiredFlag']} label={first ? t('rules.field.required') : undefined} valuePropName="checked"><Checkbox /></Form.Item>
              <Button type="text" danger className={first ? 'finance-line-remove is-first' : 'finance-line-remove'} onClick={() => remove(field.name)}>{t('workflow.remove')}</Button>
            </div>; })}
          <Button type="dashed" disabled={fields.length >= 500} onClick={() => add({ balanceBucket: 'NONE', requiredFlag: true })}>{t('rules.addLine')}</Button>
        </>}
      </Form.List>
    </Form>
    <div className="finance-drawer-footer finance-editor-footer">
      <Tag>{t('rules.lineCount', { count: lineCount })}</Tag>
      <span className="finance-spacer" />
      <Button onClick={onClose} disabled={command.saving}>{t('common.cancel')}</Button>
      <Button type="primary" loading={command.saving} disabled={command.conflict} onClick={() => void submit()}>{t('rules.saveDraft')}</Button>
    </div>
    <FinanceNote>{t('rules.editorNote')}</FinanceNote>
  </>;
}
