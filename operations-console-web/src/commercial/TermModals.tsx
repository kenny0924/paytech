import { Form, Input, Radio } from 'antd';
import { DatePicker } from '../components/DatePicker';
import {
  commercialPaths, decodeVersion, feeTaxTreatmentLabels, feeTaxTreatments, idPattern, partnerTermTypes, pricingModelLabels, pricingModels, utcInstant,
  versionStatusLabels,
} from './commercial';
import type { TermVersion, VersionAction } from './commercial';
import { CommandModal, CommercialNote } from './CommercialParts';
import { useCommercialCommand } from './useCommercialCommand';

interface Base { onClose: () => void; onAuthorizationChanged: () => void }
const idRule = { pattern: idPattern, message: '必须是正十进制整数' };
/** 复制时的「沿用来源」选项值；提交前转成 null。 */
const inherit = '';

/** 新建 DRAFT 版本：可复制已有版本的全部价格；不复制时必须给出定价模型与含税口径。Partner 条款需冻结 PARTNER 合同。 */
export function CreateVersionModal({ planId, termType, expectedChangeVersion, agreementId, copyFromVersionId, onCreated, onClose, onAuthorizationChanged }: Base & {
  planId: string; termType: string; expectedChangeVersion: string; agreementId?: string | null; copyFromVersionId?: string;
  onCreated: (version: TermVersion) => void;
}) {
  const [form] = Form.useForm<{ copyFromVersionId?: string; agreementId?: string; pricingModel?: string; feeTaxTreatment?: string }>();
  const command = useCommercialCommand(onAuthorizationChanged, 'version', form);
  const copying = !!Form.useWatch('copyFromVersionId', form)?.trim();
  const partner = partnerTermTypes.includes(termType);
  const submit = async () => {
    const values = await form.validateFields();
    const copy = values.copyFromVersionId?.trim() || null;
    const version = await command.run(commercialPaths.versions(planId), 'POST', {
      expectedChangeVersion, copyFromVersionId: copy, agreementId: partner ? values.agreementId?.trim() || null : null,
      pricingModel: values.pricingModel || null, feeTaxTreatment: values.feeTaxTreatment || null,
    }, decodeVersion(), '版本创建失败');
    if (version) onCreated(version);
  };
  return <CommandModal title={copyFromVersionId ? '复制为新草稿版本' : '新建草稿版本'} command={command} okText="创建" onOk={() => void submit()} onClose={onClose} onReload={onClose}>
    <Form form={form} name="commercial-version-create" layout="vertical" initialValues={{ copyFromVersionId, agreementId: agreementId ?? undefined,
      pricingModel: copyFromVersionId ? inherit : 'BLENDED', feeTaxTreatment: copyFromVersionId ? inherit : 'EXCLUSIVE' }}>
      <div className="commercial-form-grid">
        <Form.Item name="copyFromVersionId" label="复制来源版本 ID（可空）" rules={[idRule]}><Input maxLength={19} /></Form.Item>
        {partner ? <Form.Item name="agreementId" label="冻结的 PARTNER 合同 ID" rules={[{ required: true, whitespace: true, message: 'Partner 条款必须指定合同' }, idRule]}>
          <Input maxLength={19} /></Form.Item> : <div />}
        <Form.Item name="pricingModel" label="定价模型" tooltip="混合费率 BLENDED：按约定费率收费；IC++ INTERCHANGE_PLUS_PLUS：交换费 + 卡组织费 + 加价，本期排期只接受混合费率"
          rules={[{ required: !copying, message: '不复制时必须选择' }]}>
          <Radio.Group optionType="button" options={[...(copying ? [{ value: inherit, label: '沿用来源' }] : []),
            ...pricingModels.map(value => ({ value, label: pricingModelLabels[value], title: value }))]} /></Form.Item>
        <Form.Item name="feeTaxTreatment" label="含税口径" tooltip="含税 INCLUSIVE：价格已含税；不含税 EXCLUSIVE：税费另计" rules={[{ required: !copying, message: '不复制时必须选择' }]}>
          <Radio.Group optionType="button" options={[...(copying ? [{ value: inherit, label: '沿用来源' }] : []),
            ...feeTaxTreatments.map(value => ({ value, label: feeTaxTreatmentLabels[value], title: value }))]} /></Form.Item>
      </div>
      <CommercialNote>版本号由 commercial 递增；复制会带上来源版本的全部价格、组件与档位。</CommercialNote>
    </Form>
  </CommandModal>;
}

const transitionTexts: Record<Exclude<VersionAction, 'schedule'>, { title: string; ok: string; body: string; danger?: boolean }> = {
  submit: { title: '提交审批', ok: '提交', body: '提交后价格冻结，进入待审批；你将记为 Maker，批准人不能是你本人。' },
  approve: { title: '批准版本', ok: '批准', body: '批准只记录「已同意」；排期与激活时 commercial 会重新执行全部激活校验。批准人不能是提交人。' },
  reject: { title: '驳回版本', ok: '驳回', danger: true, body: '驳回后版本回到草稿，可继续编辑价格再提交。' },
  activate: { title: '立即激活', ok: '激活', body: '只在生效起点已到而定时作业尚未切换时使用；前一生效版本将被替代。' },
};

/** 无额外字段的版本状态推进：提交、批准、驳回、激活。 */
export function VersionTransitionModal({ version, action, onDone, onClose, onReload, onAuthorizationChanged }: Base & {
  version: Pick<TermVersion, 'versionId' | 'versionNo' | 'status' | 'changeVersion'>; action: Exclude<VersionAction, 'schedule'>; onDone: () => void;
  onReload: () => void;
}) {
  const command = useCommercialCommand(onAuthorizationChanged, 'version');
  const text = transitionTexts[action];
  const submit = async () => {
    const result = await command.run(commercialPaths.versionAction(version.versionId, action), 'POST',
      { expectedChangeVersion: version.changeVersion }, decodeVersion(version.versionId), `${text.title}失败`);
    if (result) onDone();
  };
  return <CommandModal title={`${text.title} · v${version.versionNo}`} command={command} okText={text.ok} danger={text.danger}
    onOk={() => void submit()} onClose={onClose} onReload={onReload} width={480}>
    <p>当前状态：{versionStatusLabels[version.status]}。{text.body}</p>
  </CommandModal>;
}

/** 排期：写入生效起点（UTC）并执行激活校验 1–5。 */
export function ScheduleModal({ version, onDone, onClose, onReload, onAuthorizationChanged }: Base & {
  version: TermVersion; onDone: () => void; onReload: () => void;
}) {
  const [form] = Form.useForm<{ effectiveFrom: string }>();
  const command = useCommercialCommand(onAuthorizationChanged, 'version', form);
  const submit = async () => {
    const values = await form.validateFields();
    const result = await command.run(commercialPaths.versionAction(version.versionId, 'schedule'), 'POST',
      { expectedChangeVersion: version.changeVersion, effectiveFrom: utcInstant(values.effectiveFrom) }, decodeVersion(version.versionId), '排期失败');
    if (result) onDone();
  };
  return <CommandModal title={`排期 · v${version.versionNo}`} command={command} okText="排期" onOk={() => void submit()} onClose={onClose} onReload={onReload} width={520}>
    <Form form={form} name="commercial-version-schedule" layout="vertical">
      <Form.Item name="effectiveFrom" label="生效起点（本地时间，按 UTC 提交）" rules={[{ required: true, message: '请选择生效起点' }]}><DatePicker showTime /></Form.Item>
    </Form>
    <CommercialNote>排期会校验：合同生效且覆盖起点、画像启用、OE 被合同覆盖、每个开通项的每个约束币种都有价格、阶梯连续、同方案区间不重叠。</CommercialNote>
  </CommandModal>;
}
