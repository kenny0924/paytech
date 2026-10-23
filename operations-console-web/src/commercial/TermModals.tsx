import { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Form, Input, Radio, Select } from 'antd';
import type { FormInstance } from 'antd';
import { ApiError, session } from '../api/session';
import { DatePicker } from '../components/DatePicker';
import { useDetail } from '../tenantAccess/TenantAccessView';
import {
  acceptanceMethods, commercialPaths, decodeAgreementOverview, decodeVersion, feeTaxTreatments, idPattern, joinList, partnerTermTypes, pricingModels, utcInstant,
} from './commercial';
import type { TermVersion, VersionAction } from './commercial';
import { consolePaths, decodeTermVersionPage } from './commercialConsole';
import type { TermVersionListItem } from './commercialConsole';
import { CommandModal, CommercialNote } from './CommercialParts';
import { decodeTemplateList, templateChoices, templateLabel, templatePaths } from './templates';
import type { TemplatePlan } from './templates';
import { useCommercialCommand } from './useCommercialCommand';
import {
  availableGenerationModes, basisRequired, createVersionBody, defaultGenerationMode, fillFromTemplateBody, signatureBody,
  signatureIssues, signatureParties,
} from './versionFlow';
import type { CreateVersionValues, GenerationMode, SignatureFormValues } from './versionFlow';
import { enumLabel } from '../i18n/index';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';
import './capabilityPricing.css';

interface Base { onClose: () => void; onAuthorizationChanged: () => void }
const idRule = (t: Translate) => ({ pattern: idPattern, message: t('form.idPattern') });
const hashRule = (t: Translate) => ({ pattern: /^[0-9a-fA-F]{64}$/, message: t('form.hashPattern') });
/** 复制时的「沿用来源」选项值；提交前转成 null。 */
const inherit = '';
/** 可作为生成 / 补齐来源的模板版本状态。 */
const usableTemplateStatuses = ['APPROVED', 'SCHEDULED', 'ACTIVE', 'SUPERSEDED'];

/** 模板列表（按 OE 与条款类型）；读取失败时为空并提示。 */
function useTemplates(entityNos: readonly string[], termType: string, enabled: boolean, onAuthorizationChanged: () => void) {
  const [state, setState] = useState<{ items: TemplatePlan[]; loading: boolean; failed: boolean }>({ items: [], loading: enabled, failed: false });
  const authorizationChanged = useRef(onAuthorizationChanged); authorizationChanged.current = onAuthorizationChanged;
  const key = entityNos.join(',');
  useEffect(() => {
    if (!enabled) return undefined;
    const controller = new AbortController();
    setState(current => ({ ...current, loading: true }));
    session.request(`${templatePaths.templates}?termType=${encodeURIComponent(termType)}`, { signal: controller.signal }, decodeTemplateList)
      .then(items => { if (!controller.signal.aborted) setState({ items: templateChoices(items, key.split(','), termType), loading: false, failed: false }); })
      .catch(error => {
        if (controller.signal.aborted) return;
        if (error instanceof ApiError && error.status === 403) authorizationChanged.current();
        setState({ items: [], loading: false, failed: true });
      });
    return () => controller.abort();
  }, [enabled, key, termType]);
  return state;
}

/** 一个方案的版本列表（模板版本选择用）。 */
function usePlanVersions(planId: string | undefined) {
  const [versions, setVersions] = useState<TermVersionListItem[]>([]);
  useEffect(() => {
    if (!planId) { setVersions([]); return undefined; }
    const controller = new AbortController();
    session.request(`${consolePaths.versions}?planId=${planId}&page=1&size=50`, { signal: controller.signal }, decodeTermVersionPage)
      .then(page => { if (!controller.signal.aborted) setVersions(page.items); })
      .catch(() => { if (!controller.signal.aborted) setVersions([]); });
    return () => controller.abort();
  }, [planId]);
  return versions;
}

/** 选模板 + 版本：模板默认生效版本；只列可用状态的版本。表单字段 templatePlanId（只用于界面）与 versionField。 */
function TemplateVersionPicker({ form, templates, versionField, loading, failed }: {
  form: FormInstance; templates: TemplatePlan[]; versionField: string; loading: boolean; failed: boolean;
}) {
  const t = useT('commercial');
  const planId = Form.useWatch('templatePlanId', form) as string | undefined;
  const versions = usePlanVersions(planId).filter(item => usableTemplateStatuses.includes(item.status));
  const plan = templates.find(item => item.planId === planId);
  const choose = (next: string) => {
    const chosen = templates.find(item => item.planId === next);
    form.setFieldsValue({ templatePlanId: next, [versionField]: chosen?.activeVersionId ?? chosen?.latestVersionId ?? undefined });
  };
  return <div className="cc-mode-extra">
    <Form.Item name="templatePlanId" label={t('source.standardTemplate')} rules={[{ required: true, message: t('templatePicker.templateRequired') }]}
      extra={failed ? t('templatePicker.failed') : !loading && !templates.length ? t('templatePicker.none') : undefined}>
      <Select loading={loading} onChange={choose} options={templates.map(item => ({ value: item.planId,
        label: `${item.templateName} · ${item.entityNo}${item.activeVersionNo ? t('templatePicker.activeSuffix', { versionNo: item.activeVersionNo }) : ''}` }))} /></Form.Item>
    <Form.Item name={versionField} label={t('templatePicker.version')} rules={[{ required: true, message: t('templatePicker.versionRequired') }, idRule(t)]}>
      <Select options={(versions.length ? versions.map(item => ({ value: item.versionId,
        label: `v${item.versionNo} · ${enumLabel(t, 'enum.versionStatus', item.status)}${item.versionId === plan?.activeVersionId ? t('templatePicker.currentSuffix') : ''}` }))
        : plan?.activeVersionId ? [{ value: plan.activeVersionId, label: templateLabel(plan, plan.activeVersionNo) }] : [])} /></Form.Item>
  </div>;
}

/**
 * 新建 DRAFT 版本，三选一：复制上一版本（全部价格与来源）、基于标准价目（只生成合同已开通项 × 币种的价格，按接受条件裁剪，每行记来源）、空白。
 * <li>空白必须给出定价模型与含税口径；复制与基于模板可沿用来源。Partner 条款需冻结 PARTNER 合同，且不能基于标准价目。</li>
 * <li>template=true 为标准价目自身的新版本：走模板专用路由，没有「基于标准价目」。</li>
 */
export function CreateVersionModal({ planId, termType, expectedChangeVersion, agreementId, copyFromVersionId, entityNos = [], template = false, onCreated, onClose,
  onAuthorizationChanged }: Base & {
  planId: string; termType: string; expectedChangeVersion: string; agreementId?: string | null; copyFromVersionId?: string; entityNos?: readonly string[];
  template?: boolean; onCreated: (version: TermVersion) => void;
}) {
  const t = useT('commercial');
  const [form] = Form.useForm<CreateVersionValues & { templatePlanId?: string }>();
  const command = useCommercialCommand(onAuthorizationChanged, 'version', form);
  const partner = partnerTermTypes.includes(termType);
  const modes = availableGenerationModes({ partner, template, hasPrevious: !!copyFromVersionId });
  const mode = (Form.useWatch('mode', form) ?? defaultGenerationMode(modes)) as GenerationMode;
  const templates = useTemplates(entityNos, termType, modes.includes('TEMPLATE'), onAuthorizationChanged);
  const submit = async () => {
    const values = await form.validateFields();
    const path = template ? templatePaths.versions(planId) : commercialPaths.versions(planId);
    const version = await command.run(path, 'POST', createVersionBody(expectedChangeVersion, values, partner), decodeVersion(), t('createVersion.failed'));
    if (version) onCreated(version);
  };
  const required = basisRequired(mode);
  const basisOptions = (kind: string, values: readonly string[]) => [...(required ? [] : [{ value: inherit, label: t('createVersion.inherit') }]),
    ...values.map(value => ({ value, label: enumLabel(t, 'enum.' + kind, value), title: value }))];
  const changeMode = (next: GenerationMode) => form.setFieldsValue({ mode: next, pricingModel: basisRequired(next) ? 'BLENDED' : inherit,
    feeTaxTreatment: basisRequired(next) ? 'EXCLUSIVE' : inherit });
  const initialMode = defaultGenerationMode(modes);
  return <CommandModal title={template ? t('createVersion.templateTitle') : t('createVersion.title')} command={command} okText={t('shared.create')} onOk={() => void submit()} onClose={onClose}
    onReload={onClose} width={680}>
    <Form form={form} name="commercial-version-create" layout="vertical" initialValues={{ mode: initialMode, copyFromVersionId,
      agreementId: agreementId ?? undefined, pricingModel: basisRequired(initialMode) ? 'BLENDED' : inherit,
      feeTaxTreatment: basisRequired(initialMode) ? 'EXCLUSIVE' : inherit }}>
      <Form.Item name="mode" label={t('createVersion.mode')}>
        <Radio.Group className="cc-mode-group" onChange={event => changeMode(event.target.value as GenerationMode)}
          options={modes.map(value => ({ value, label: enumLabel(t, 'enum.generationMode', value) }))} /></Form.Item>
      {mode === 'COPY' && <Form.Item name="copyFromVersionId" label={t('createVersion.copyFrom')} rules={[{ required: true, message: t('createVersion.copyFromRequired') }, idRule(t)]}>
        <Input maxLength={19} /></Form.Item>}
      {mode === 'TEMPLATE' && <TemplateVersionPicker form={form} templates={templates.items} versionField="sourceTemplateVersionId" loading={templates.loading}
        failed={templates.failed} />}
      <div className="commercial-form-grid">
        {partner ? <Form.Item name="agreementId" label={t('createVersion.partnerAgreement')} rules={[{ required: true, whitespace: true, message: t('createVersion.partnerAgreementRequired') }, idRule(t)]}>
          <Input maxLength={19} /></Form.Item> : null}
        <Form.Item name="pricingModel" label={t('createVersion.pricingModel')} tooltip={t('createVersion.pricingModelTooltip')}
          rules={[{ required, message: t('createVersion.blankRequired') }]}>
          <Radio.Group optionType="button" options={basisOptions('pricingModel', pricingModels)} /></Form.Item>
        <Form.Item name="feeTaxTreatment" label={t('createVersion.feeTaxTreatment')} tooltip={t('createVersion.feeTaxTreatmentTooltip')} rules={[{ required, message: t('createVersion.blankRequired') }]}>
          <Radio.Group optionType="button" options={basisOptions('feeTaxTreatment', feeTaxTreatments)} /></Form.Item>
      </div>
      <CommercialNote>{mode === 'TEMPLATE' ? t('createVersion.note.template') : mode === 'COPY' ? t('createVersion.note.copy') : t('createVersion.note.blank')}</CommercialNote>
    </Form>
  </CommandModal>;
}

/** 从标准价目补齐：只追加当前草稿缺失的（OE × 币种 × 画像）价格行，规则同基于模板生成。 */
export function FillFromTemplateModal({ version, entityNos, onDone, onClose, onReload, onAuthorizationChanged }: Base & {
  version: TermVersion; entityNos: readonly string[]; onDone: (result: TermVersion) => void; onReload: () => void;
}) {
  const t = useT('commercial');
  const [form] = Form.useForm<{ templatePlanId?: string; templateVersionId: string }>();
  const command = useCommercialCommand(onAuthorizationChanged, 'version', form);
  const templates = useTemplates(entityNos, version.termType, true, onAuthorizationChanged);
  const submit = async () => {
    const values = await form.validateFields();
    const result = await command.run(commercialPaths.fillFromTemplate(version.versionId), 'POST', fillFromTemplateBody(version.changeVersion, values.templateVersionId),
      decodeVersion(version.versionId), t('fillTemplate.failed'));
    if (result) onDone(result);
  };
  return <CommandModal title={t('fillTemplate.title', { versionNo: version.versionNo })} command={command} okText={t('fillTemplate.ok')} onOk={() => void submit()} onClose={onClose}
    onReload={onReload} width={640}>
    <Form form={form} name="commercial-fill-from-template" layout="vertical"
      initialValues={{ templateVersionId: version.templateUpdate?.latestTemplateVersionId ?? version.sourceTemplateVersionId ?? undefined }}>
      <TemplateVersionPicker form={form} templates={templates.items} versionField="templateVersionId" loading={templates.loading} failed={templates.failed} />
    </Form>
    <CommercialNote>{t('fillTemplate.note')}</CommercialNote>
  </CommandModal>;
}

/**
 * 无额外字段的版本状态推进：提交、批准、驳回、激活；path 为模板专用路由时覆盖默认路径。
 * 文案为 commercial 命名空间的 versionTransition.<动作>.title / ok / body / failed；驳回为危险操作。
 */
export function VersionTransitionModal({ version, action, path, onDone, onClose, onReload, onAuthorizationChanged }: Base & {
  version: Pick<TermVersion, 'versionId' | 'versionNo' | 'status' | 'changeVersion'>; action: Exclude<VersionAction, 'schedule'>; path?: string;
  onDone: () => void; onReload: () => void;
}) {
  const t = useT('commercial');
  const command = useCommercialCommand(onAuthorizationChanged, 'version');
  const prefix = `versionTransition.${action}`;
  const submit = async () => {
    const result = await command.run(path ?? commercialPaths.versionAction(version.versionId, action), 'POST',
      { expectedChangeVersion: version.changeVersion }, decodeVersion(version.versionId), t(prefix + '.failed'));
    if (result) onDone();
  };
  return <CommandModal title={t('versionTransition.title', { action: t(prefix + '.title'), versionNo: version.versionNo })} command={command}
    okText={t(prefix + '.ok')} danger={action === 'reject'} onOk={() => void submit()} onClose={onClose} onReload={onReload} width={480}>
    <p>{t('versionTransition.body', { status: enumLabel(t, 'enum.versionStatus', version.status), body: t(prefix + '.body') })}</p>
  </CommandModal>;
}

/** 排期：写入生效起点（UTC）并执行激活校验；path 为模板专用路由时覆盖默认路径。 */
export function ScheduleModal({ version, path, template = false, onDone, onClose, onReload, onAuthorizationChanged }: Base & {
  version: TermVersion; path?: string; template?: boolean; onDone: () => void; onReload: () => void;
}) {
  const t = useT('commercial');
  const [form] = Form.useForm<{ effectiveFrom: string }>();
  const command = useCommercialCommand(onAuthorizationChanged, 'version', form);
  const submit = async () => {
    const values = await form.validateFields();
    const result = await command.run(path ?? commercialPaths.versionAction(version.versionId, 'schedule'), 'POST',
      { expectedChangeVersion: version.changeVersion, effectiveFrom: utcInstant(values.effectiveFrom) }, decodeVersion(version.versionId), t('schedule.failed'));
    if (result) onDone();
  };
  return <CommandModal title={t('schedule.title', { versionNo: version.versionNo })} command={command} okText={t('version.action.schedule')} onOk={() => void submit()} onClose={onClose} onReload={onReload} width={520}>
    <Form form={form} name="commercial-version-schedule" layout="vertical">
      <Form.Item name="effectiveFrom" label={t('schedule.effectiveFrom')} rules={[{ required: true, message: t('form.effectiveFromRequired') }]}><DatePicker showTime /></Form.Item>
    </Form>
    <CommercialNote>{template ? t('schedule.note.template') : t('schedule.note.agreement')}</CommercialNote>
  </CommandModal>;
}

/**
 * 登记客户签署价格协议：签署方从合同 CLIENT（Client 方案）或 LOCAL_PARTNER（Partner 方案）参与方带出；
 * 价格附件文件 ID + SHA-256、方式、时刻、编号。成功后版本为「客户已签署」，可以排期。
 */
export function SignatureModal({ version, onDone, onClose, onReload, onAuthorizationChanged }: Base & {
  version: TermVersion & { agreementId: string }; onDone: () => void; onReload: () => void;
}) {
  const t = useT('commercial');
  const [form] = Form.useForm<SignatureFormValues>();
  const command = useCommercialCommand(onAuthorizationChanged, 'version', form);
  const decode = useMemo(() => decodeAgreementOverview(version.agreementId), [version.agreementId]);
  const agreement = useDetail(commercialPaths.agreement(version.agreementId), decode);
  const partner = partnerTermTypes.includes(version.termType);
  const parties = signatureParties(agreement.data?.agreement.parties ?? [], partner);
  const [issues, setIssues] = useState<string[]>([]);
  useEffect(() => {
    if (parties.length === 1 && !form.getFieldValue('partyId')) form.setFieldValue('partyId', parties[0].partyId);
  }, [parties, form]);
  const submit = async () => {
    const values = await form.validateFields();
    const problems = signatureIssues(values, parties);
    setIssues(problems);
    if (problems.length) return;
    const result = await command.run(commercialPaths.versionAcceptance(version.versionId), 'POST', signatureBody(version.changeVersion, values),
      decodeVersion(version.versionId), t('agreement.acceptance.failed'));
    if (result) onDone();
  };
  return <CommandModal title={t('signature.title', { versionNo: version.versionNo })} command={command} okText={t('agreement.acceptance.ok')} onOk={() => void submit()} onClose={onClose}
    onReload={onReload} width={640}>
    {agreement.failure ? <Alert type="warning" showIcon className="commercial-banner" title={t('signature.partiesFailed')} /> : null}
    {issues.length > 0 && <Alert type="error" showIcon className="commercial-banner" title={joinList(issues, 'clauses')} />}
    <Form form={form} name="commercial-version-signature" layout="vertical" initialValues={{ acceptanceMethod: 'E_SIGNATURE' }}>
      <Form.Item name="partyId" label={t('signature.party', { role: partner ? 'Local Partner' : 'Client' })} rules={[{ required: true, message: t('signature.partyRequired') }]}>
        <Select loading={agreement.loading} options={parties.map(party => ({ value: party.partyId,
          label: `${enumLabel(t, 'enum.partyRole', party.partyRole)} · Party ${party.partyId}` }))} /></Form.Item>
      <Form.Item name="acceptanceMethod" label={t('sign.field.method')}><Radio.Group optionType="button"
        options={acceptanceMethods.map(value => ({ value, label: enumLabel(t, 'enum.acceptanceMethod', value), title: value }))} /></Form.Item>
      <div className="commercial-form-grid">
        <Form.Item name="acceptedAt" label={t('sign.field.time')} rules={[{ required: true, message: t('sign.issue.time') }]}><DatePicker showTime /></Form.Item>
        <Form.Item name="acceptanceReference" label={t('sign.field.reference')} extra={t('signature.referenceHint')} rules={[{ required: true, whitespace: true, message: t('sign.field.referenceRequired') },
          { pattern: /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/, message: t('agreement.acceptance.referencePattern') }]}><Input maxLength={128} /></Form.Item>
        <Form.Item name="fileId" label={t('signature.fileId')} rules={[{ required: true, whitespace: true, message: t('form.fileIdRequired') }, idRule(t)]}><Input maxLength={19} /></Form.Item>
        <Form.Item name="contentHash" label={t('signature.hash')} rules={[{ required: true, whitespace: true, message: t('form.hashRequired') }, hashRule(t)]}><Input maxLength={64} /></Form.Item>
      </div>
    </Form>
    <CommercialNote>{t('signature.note')}</CommercialNote>
  </CommandModal>;
}
