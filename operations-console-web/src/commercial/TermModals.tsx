import { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Form, Input, Radio, Select } from 'antd';
import type { FormInstance } from 'antd';
import { ApiError, session } from '../api/session';
import { DatePicker } from '../components/DatePicker';
import { useDetail } from '../tenantAccess/TenantAccessView';
import {
  acceptanceMethodLabels, acceptanceMethods, commercialPaths, decodeAgreementOverview, decodeVersion, feeTaxTreatmentLabels, feeTaxTreatments, idPattern,
  partnerTermTypes, partyRoleLabels, pricingModelLabels, pricingModels, utcInstant, versionStatusLabels,
} from './commercial';
import type { TermVersion, VersionAction } from './commercial';
import { consolePaths, decodeTermVersionPage } from './commercialConsole';
import type { TermVersionListItem } from './commercialConsole';
import { CommandModal, CommercialNote } from './CommercialParts';
import { decodeTemplateList, templateChoices, templateLabel, templatePaths } from './templates';
import type { TemplatePlan } from './templates';
import { useCommercialCommand } from './useCommercialCommand';
import {
  availableGenerationModes, basisRequired, createVersionBody, defaultGenerationMode, fillFromTemplateBody, generationModeLabels, signatureBody,
  signatureIssues, signatureParties,
} from './versionFlow';
import type { CreateVersionValues, GenerationMode, SignatureFormValues } from './versionFlow';
import './capabilityPricing.css';

interface Base { onClose: () => void; onAuthorizationChanged: () => void }
const idRule = { pattern: idPattern, message: '必须是正十进制整数' };
const hashRule = { pattern: /^[0-9a-fA-F]{64}$/, message: '必须是 64 位十六进制 SHA-256' };
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
  const planId = Form.useWatch('templatePlanId', form) as string | undefined;
  const versions = usePlanVersions(planId).filter(item => usableTemplateStatuses.includes(item.status));
  const plan = templates.find(item => item.planId === planId);
  const choose = (next: string) => {
    const chosen = templates.find(item => item.planId === next);
    form.setFieldsValue({ templatePlanId: next, [versionField]: chosen?.activeVersionId ?? chosen?.latestVersionId ?? undefined });
  };
  return <div className="cc-mode-extra">
    <Form.Item name="templatePlanId" label="标准价目" rules={[{ required: true, message: '请选择标准价目' }]}
      extra={failed ? '标准价目读取失败，请稍后重试' : !loading && !templates.length ? '该 OE 与条款类型没有可用的标准价目' : undefined}>
      <Select loading={loading} onChange={choose} options={templates.map(item => ({ value: item.planId,
        label: `${item.templateName} · ${item.entityNo}${item.activeVersionNo ? `（生效 v${item.activeVersionNo}）` : ''}` }))} /></Form.Item>
    <Form.Item name={versionField} label="模板版本" rules={[{ required: true, message: '请选择模板版本' }, idRule]}>
      <Select options={(versions.length ? versions.map(item => ({ value: item.versionId,
        label: `v${item.versionNo} · ${versionStatusLabels[item.status]}${item.versionId === plan?.activeVersionId ? '（当前生效）' : ''}` }))
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
  const [form] = Form.useForm<CreateVersionValues & { templatePlanId?: string }>();
  const command = useCommercialCommand(onAuthorizationChanged, 'version', form);
  const partner = partnerTermTypes.includes(termType);
  const modes = availableGenerationModes({ partner, template, hasPrevious: !!copyFromVersionId });
  const mode = (Form.useWatch('mode', form) ?? defaultGenerationMode(modes)) as GenerationMode;
  const templates = useTemplates(entityNos, termType, modes.includes('TEMPLATE'), onAuthorizationChanged);
  const submit = async () => {
    const values = await form.validateFields();
    const path = template ? templatePaths.versions(planId) : commercialPaths.versions(planId);
    const version = await command.run(path, 'POST', createVersionBody(expectedChangeVersion, values, partner), decodeVersion(), '版本创建失败');
    if (version) onCreated(version);
  };
  const required = basisRequired(mode);
  const basisOptions = (labels: Readonly<Record<string, string>>, values: readonly string[]) => [...(required ? [] : [{ value: inherit, label: '沿用来源' }]),
    ...values.map(value => ({ value, label: labels[value], title: value }))];
  const changeMode = (next: GenerationMode) => form.setFieldsValue({ mode: next, pricingModel: basisRequired(next) ? 'BLENDED' : inherit,
    feeTaxTreatment: basisRequired(next) ? 'EXCLUSIVE' : inherit });
  const initialMode = defaultGenerationMode(modes);
  return <CommandModal title={template ? '新建标准价目版本' : '新建草稿版本'} command={command} okText="创建" onOk={() => void submit()} onClose={onClose}
    onReload={onClose} width={680}>
    <Form form={form} name="commercial-version-create" layout="vertical" initialValues={{ mode: initialMode, copyFromVersionId,
      agreementId: agreementId ?? undefined, pricingModel: basisRequired(initialMode) ? 'BLENDED' : inherit,
      feeTaxTreatment: basisRequired(initialMode) ? 'EXCLUSIVE' : inherit }}>
      <Form.Item name="mode" label="生成方式">
        <Radio.Group className="cc-mode-group" onChange={event => changeMode(event.target.value as GenerationMode)}
          options={modes.map(value => ({ value, label: generationModeLabels[value] }))} /></Form.Item>
      {mode === 'COPY' && <Form.Item name="copyFromVersionId" label="来源版本 ID" rules={[{ required: true, message: '请填写来源版本' }, idRule]}>
        <Input maxLength={19} /></Form.Item>}
      {mode === 'TEMPLATE' && <TemplateVersionPicker form={form} templates={templates.items} versionField="sourceTemplateVersionId" loading={templates.loading}
        failed={templates.failed} />}
      <div className="commercial-form-grid">
        {partner ? <Form.Item name="agreementId" label="冻结的 PARTNER 合同 ID" rules={[{ required: true, whitespace: true, message: 'Partner 条款必须指定合同' }, idRule]}>
          <Input maxLength={19} /></Form.Item> : null}
        <Form.Item name="pricingModel" label="定价模型" tooltip="混合费率 BLENDED：按约定费率收费；IC++ INTERCHANGE_PLUS_PLUS：交换费 + 卡组织费 + 加价，本期排期只接受混合费率"
          rules={[{ required, message: '空白版本必须选择' }]}>
          <Radio.Group optionType="button" options={basisOptions(pricingModelLabels, pricingModels)} /></Form.Item>
        <Form.Item name="feeTaxTreatment" label="含税口径" tooltip="含税 INCLUSIVE：价格已含税；不含税 EXCLUSIVE：税费另计" rules={[{ required, message: '空白版本必须选择' }]}>
          <Radio.Group optionType="button" options={basisOptions(feeTaxTreatmentLabels, feeTaxTreatments)} /></Form.Item>
      </div>
      <CommercialNote>{mode === 'TEMPLATE'
        ? '基于标准价目只生成合同已开通的支付方式 × 币种价格，并按开通项接受条件裁剪（例如只收 Visa 借记卡时不生成 Amex 行）；功能类费用只在功能已开通时保留。每行记录来源模板价格。'
        : mode === 'COPY' ? '复制会带上来源版本的全部价格、组件、档位与来源记录。' : '空白版本从零编辑价格。版本号由 commercial 递增。'}</CommercialNote>
    </Form>
  </CommandModal>;
}

/** 从标准价目补齐：只追加当前草稿缺失的（OE × 币种 × 画像）价格行，规则同基于模板生成。 */
export function FillFromTemplateModal({ version, entityNos, onDone, onClose, onReload, onAuthorizationChanged }: Base & {
  version: TermVersion; entityNos: readonly string[]; onDone: (result: TermVersion) => void; onReload: () => void;
}) {
  const [form] = Form.useForm<{ templatePlanId?: string; templateVersionId: string }>();
  const command = useCommercialCommand(onAuthorizationChanged, 'version', form);
  const templates = useTemplates(entityNos, version.termType, true, onAuthorizationChanged);
  const submit = async () => {
    const values = await form.validateFields();
    const result = await command.run(commercialPaths.fillFromTemplate(version.versionId), 'POST', fillFromTemplateBody(version.changeVersion, values.templateVersionId),
      decodeVersion(version.versionId), '补齐失败');
    if (result) onDone(result);
  };
  return <CommandModal title={`从标准价目补齐 · v${version.versionNo}`} command={command} okText="补齐" onOk={() => void submit()} onClose={onClose}
    onReload={onReload} width={640}>
    <Form form={form} name="commercial-fill-from-template" layout="vertical"
      initialValues={{ templateVersionId: version.templateUpdate?.latestTemplateVersionId ?? version.sourceTemplateVersionId ?? undefined }}>
      <TemplateVersionPicker form={form} templates={templates.items} versionField="templateVersionId" loading={templates.loading} failed={templates.failed} />
    </Form>
    <CommercialNote>只追加缺失的价格行，已有价格不变；适用于新开通一项后补齐该开通项的价格。</CommercialNote>
  </CommandModal>;
}

const transitionTexts: Record<Exclude<VersionAction, 'schedule'>, { title: string; ok: string; body: string; danger?: boolean }> = {
  submit: { title: '提交审批', ok: '提交', body: '提交后价格冻结，进入待审批；你将记为 Maker，批准人不能是你本人。' },
  approve: { title: '批准版本', ok: '批准', body: '批准只记录「已同意」；合同方案还需登记客户签署后才能排期，排期与激活时 commercial 会重新执行全部激活校验。批准人不能是提交人。' },
  reject: { title: '驳回版本', ok: '驳回', danger: true, body: '驳回后版本回到草稿，可继续编辑价格再提交。' },
  activate: { title: '立即激活', ok: '激活', body: '只在生效起点已到而定时作业尚未切换时使用；前一生效版本将被替代。' },
};

/** 无额外字段的版本状态推进：提交、批准、驳回、激活；path 为模板专用路由时覆盖默认路径。 */
export function VersionTransitionModal({ version, action, path, onDone, onClose, onReload, onAuthorizationChanged }: Base & {
  version: Pick<TermVersion, 'versionId' | 'versionNo' | 'status' | 'changeVersion'>; action: Exclude<VersionAction, 'schedule'>; path?: string;
  onDone: () => void; onReload: () => void;
}) {
  const command = useCommercialCommand(onAuthorizationChanged, 'version');
  const text = transitionTexts[action];
  const submit = async () => {
    const result = await command.run(path ?? commercialPaths.versionAction(version.versionId, action), 'POST',
      { expectedChangeVersion: version.changeVersion }, decodeVersion(version.versionId), `${text.title}失败`);
    if (result) onDone();
  };
  return <CommandModal title={`${text.title} · v${version.versionNo}`} command={command} okText={text.ok} danger={text.danger}
    onOk={() => void submit()} onClose={onClose} onReload={onReload} width={480}>
    <p>当前状态：{versionStatusLabels[version.status]}。{text.body}</p>
  </CommandModal>;
}

/** 排期：写入生效起点（UTC）并执行激活校验；path 为模板专用路由时覆盖默认路径。 */
export function ScheduleModal({ version, path, template = false, onDone, onClose, onReload, onAuthorizationChanged }: Base & {
  version: TermVersion; path?: string; template?: boolean; onDone: () => void; onReload: () => void;
}) {
  const [form] = Form.useForm<{ effectiveFrom: string }>();
  const command = useCommercialCommand(onAuthorizationChanged, 'version', form);
  const submit = async () => {
    const values = await form.validateFields();
    const result = await command.run(path ?? commercialPaths.versionAction(version.versionId, 'schedule'), 'POST',
      { expectedChangeVersion: version.changeVersion, effectiveFrom: utcInstant(values.effectiveFrom) }, decodeVersion(version.versionId), '排期失败');
    if (result) onDone();
  };
  return <CommandModal title={`排期 · v${version.versionNo}`} command={command} okText="排期" onOk={() => void submit()} onClose={onClose} onReload={onReload} width={520}>
    <Form form={form} name="commercial-version-schedule" layout="vertical">
      <Form.Item name="effectiveFrom" label="生效起点（本地时间，按 UTC 提交）" rules={[{ required: true, message: '请选择生效起点' }]}><DatePicker showTime /></Form.Item>
    </Form>
    <CommercialNote>{template ? '标准价目只做结构校验（阶梯连续、同方案区间不重叠），同一模板方案生效版本唯一；不参与运行时取价。'
      : '排期会校验：价格协议已签署（历史版本免签）、合同生效且覆盖起点、画像启用、OE 被合同覆盖、每条价格对应已开通项（相关性）、每个开通项的币种与条件组合都有价格（完整性）、阶梯连续、同方案区间不重叠。'}</CommercialNote>
  </CommandModal>;
}

/**
 * 登记客户签署价格协议：签署方从合同 CLIENT（Client 方案）或 LOCAL_PARTNER（Partner 方案）参与方带出；
 * 价格附件文件 ID + SHA-256、方式、时刻、编号。成功后版本为「客户已签署」，可以排期。
 */
export function SignatureModal({ version, onDone, onClose, onReload, onAuthorizationChanged }: Base & {
  version: TermVersion & { agreementId: string }; onDone: () => void; onReload: () => void;
}) {
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
      decodeVersion(version.versionId), '签署登记失败');
    if (result) onDone();
  };
  return <CommandModal title={`登记客户签署 · v${version.versionNo}`} command={command} okText="登记" onOk={() => void submit()} onClose={onClose}
    onReload={onReload} width={640}>
    {agreement.failure ? <Alert type="warning" showIcon className="commercial-banner" title="合同参与方读取失败，无法带出签署方" /> : null}
    {issues.length > 0 && <Alert type="error" showIcon className="commercial-banner" title={issues.join('；')} />}
    <Form form={form} name="commercial-version-signature" layout="vertical" initialValues={{ acceptanceMethod: 'E_SIGNATURE' }}>
      <Form.Item name="partyId" label={`签署方（合同 ${partner ? 'Local Partner' : 'Client'} 参与方）`} rules={[{ required: true, message: '请选择签署方' }]}>
        <Select loading={agreement.loading} options={parties.map(party => ({ value: party.partyId,
          label: `${partyRoleLabels[party.partyRole]} · Party ${party.partyId}` }))} /></Form.Item>
      <Form.Item name="acceptanceMethod" label="签署方式"><Radio.Group optionType="button"
        options={acceptanceMethods.map(value => ({ value, label: acceptanceMethodLabels[value], title: value }))} /></Form.Item>
      <div className="commercial-form-grid">
        <Form.Item name="acceptedAt" label="签署时刻" rules={[{ required: true, message: '请选择签署时刻' }]}><DatePicker showTime /></Form.Item>
        <Form.Item name="acceptanceReference" label="签署编号" extra="全局唯一；同编号同内容重放返回原结果" rules={[{ required: true, whitespace: true, message: '请填写签署编号' },
          { pattern: /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/, message: '字母数字及 . _ : -' }]}><Input maxLength={128} /></Form.Item>
        <Form.Item name="fileId" label="价格附件文件 ID" rules={[{ required: true, whitespace: true, message: '请填写文件 ID' }, idRule]}><Input maxLength={19} /></Form.Item>
        <Form.Item name="contentHash" label="价格附件 SHA-256" rules={[{ required: true, whitespace: true, message: '请填写 SHA-256' }, hashRule]}><Input maxLength={64} /></Form.Item>
      </div>
    </Form>
    <CommercialNote>全部价格变更（含降价）都需要客户签署价格协议；未签署的版本不能排期。一个版本只登记一次签署。</CommercialNote>
  </CommandModal>;
}
