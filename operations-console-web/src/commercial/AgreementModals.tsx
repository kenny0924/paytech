import { Button, Form, Input, InputNumber, Radio, Select } from 'antd';
import { DatePicker } from '../components/DatePicker';
import {
  acceptanceBody, acceptanceMethodLabels, acceptanceMethods, agreementBody, agreementTypeLabels, businessModes, capabilityBody, capabilityStatusLabels,
  capabilityStatuses, commercialPaths, decodeAgreementWrite, decodeCapabilityWrite, decodePlan, entityNoPattern, idPattern, limitCodes, localInput,
  offsetInstant, partnerTermTypes, partyRoleLabels, partyRoles, planBody, reserveError, termTypeLabels, termTypes,
} from './commercial';
import type {
  AcceptanceFormValues, Agreement, AgreementCapability, AgreementFormValues, AgreementParty, AgreementWriteResult, CapabilityFormValues, PlanFormValues,
  TermPlan,
} from './commercial';
import { CommandModal, CommercialNote } from './CommercialParts';
import { useCommercialCommand } from './useCommercialCommand';

interface Base { onClose: () => void; onReload: () => void; onAuthorizationChanged: () => void }
const required = (message: string) => ({ required: true, whitespace: true, message });
const idRule = { pattern: idPattern, message: '必须是正十进制整数' };
const hashRule = { pattern: /^[0-9a-fA-F]{64}$/, message: '必须是 64 位十六进制 SHA-256' };
const options = (labels: Readonly<Record<string, string>>, values: readonly string[]) => values.map(value => ({ value, label: `${labels[value] ?? value} ${value}` }));

/** 新建合同：CLIENT 绑定一个 OE，PARTNER 覆盖多个 OE；资金模式与定价权本期固定，不在表单中选择。 */
export function CreateAgreementModal({ onClose, onCreated, onAuthorizationChanged }: { onClose: () => void; onCreated: (agreementId: string) => void; onAuthorizationChanged: () => void }) {
  const [form] = Form.useForm<AgreementFormValues>();
  const command = useCommercialCommand(onAuthorizationChanged, 'agreement', form);
  const type = Form.useWatch('agreementType', form) ?? 'CLIENT';
  const submit = async () => {
    const values = await form.validateFields();
    const result = await command.run(commercialPaths.agreements, 'POST', agreementBody(values), decodeAgreementWrite(), '合同创建失败');
    if (result) onCreated(result.agreementId);
  };
  return <CommandModal title="新建合同" command={command} okText="创建" onOk={() => void submit()} onClose={onClose} onReload={onClose} width={760}>
    <Form form={form} name="commercial-agreement-create" layout="vertical" initialValues={{ agreementType: 'CLIENT', businessMode: 'MOR',
      parties: [{ partyRole: 'CLIENT' }, { partyRole: 'PLATFORM_SIGNATORY' }] }}>
      <div className="commercial-form-grid">
        <Form.Item name="agreementNo" label="合同编号" rules={[required('请填写合同编号'), { pattern: /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/, message: '字母数字及 . _ -，最长 64 位' }]}>
          <Input maxLength={64} placeholder="AGR-AE001-0001" /></Form.Item>
        <Form.Item name="agreementName" label="合同名称" rules={[required('请填写合同名称')]}><Input maxLength={255} /></Form.Item>
        <Form.Item name="agreementType" label="合同类型"><Radio.Group optionType="button" options={options(agreementTypeLabels, ['CLIENT', 'PARTNER'])} /></Form.Item>
        {type === 'CLIENT'
          ? <Form.Item name="businessMode" label="业务模式" rules={[{ required: true, message: '请选择业务模式' }]}><Radio.Group optionType="button" options={businessModes.map(v => ({ value: v, label: v }))} /></Form.Item>
          : <div />}
        {type === 'CLIENT' ? <>
          <Form.Item name="clientId" label="Client ID" rules={[required('请填写 Client ID'), idRule]}><Input maxLength={19} /></Form.Item>
          <Form.Item name="entityNo" label="经营主体 OE" normalize={(v: string) => v?.toUpperCase()} rules={[required('请填写 OE'), { pattern: entityNoPattern, message: '两位国家码加三位序号，如 AE001' }]}>
            <Input maxLength={5} placeholder="AE001" /></Form.Item>
          <Form.Item name="referralPartnerId" label="推荐伙伴 ID（可空）" rules={[idRule]}><Input maxLength={19} /></Form.Item>
          <div />
        </> : <>
          <Form.Item name="localPartnerId" label="Local Partner ID" rules={[required('请填写 Local Partner ID'), idRule]}><Input maxLength={19} /></Form.Item>
          <Form.Item name="operatingEntityNos" label="覆盖 OE" extra="提交签署前至少一个" rules={[{ validator: async (_, value?: string[]) => {
            if ((value ?? []).some(v => !entityNoPattern.test(v))) throw new Error('OE 必须是两位国家码加三位序号');
          } }]}><Select mode="tags" tokenSeparators={[',', ' ']} placeholder="AE001, SA001" /></Form.Item>
        </>}
        <Form.Item name="effectiveFrom" label="生效起点" rules={[{ required: true, message: '请选择生效起点' }]}><DatePicker showTime /></Form.Item>
        <Form.Item name="effectiveTo" label="生效终点（可空）"><DatePicker showTime /></Form.Item>
      </div>
      <h3 className="commercial-subtitle">参与方</h3>
      <Form.List name="parties">{(fields, { add, remove }) => <div className="commercial-row-list">
        {fields.map(field => <div className="commercial-row" key={field.key}>
          <Form.Item name={[field.name, 'partyId']} rules={[required('Party ID'), idRule]}><Input placeholder="Party ID" maxLength={19} /></Form.Item>
          <Form.Item name={[field.name, 'partyProfileId']} rules={[required('资料版本 ID'), idRule]}><Input placeholder="资料版本 ID" maxLength={19} /></Form.Item>
          <Form.Item name={[field.name, 'partyRole']} rules={[{ required: true, message: '角色' }]}><Select options={options(partyRoleLabels, partyRoles)} /></Form.Item>
          <Button onClick={() => remove(field.name)} disabled={fields.length <= 1}>移除</Button>
        </div>)}
        <Button onClick={() => add({ partyRole: type === 'CLIENT' ? 'OPERATING_ENTITY' : 'LOCAL_PARTNER' })}>添加参与方</Button>
      </div>}</Form.List>
      <h3 className="commercial-subtitle">签署文件（可后补，提交签署前必须恰好一份）</h3>
      <div className="commercial-form-grid">
        <Form.Item name="signingFileId" label="文件 ID" rules={[idRule]}><Input maxLength={19} /></Form.Item>
        <Form.Item name="signingContentHash" label="SHA-256" dependencies={['signingFileId']} rules={[hashRule, ({ getFieldValue }) => ({ validator: async (_, value?: string) => {
          if (getFieldValue('signingFileId') && !value) throw new Error('填写文件 ID 时必须填写 SHA-256');
        } })]}><Input maxLength={64} /></Form.Item>
      </div>
      <CommercialNote>资金模式本期固定 SETTLE_THROUGH_PLATFORM、定价权固定 PLATFORM；同一合同编号重复提交且内容一致时返回原合同。</CommercialNote>
    </Form>
  </CommandModal>;
}

/** 无表单的状态推进：提交签署、激活。 */
export function TransitionModal({ agreement, action, onDone, onClose, onReload, onAuthorizationChanged }: Base & {
  agreement: Agreement; action: 'submit' | 'activate'; onDone: (result: AgreementWriteResult) => void;
}) {
  const command = useCommercialCommand(onAuthorizationChanged, 'agreement');
  const path = action === 'submit' ? commercialPaths.submitForSignature(agreement.agreementId) : commercialPaths.activateAgreement(agreement.agreementId);
  const submit = async () => {
    const result = await command.run(path, 'POST', { expectedChangeVersion: agreement.changeVersion }, decodeAgreementWrite(agreement.agreementId),
      action === 'submit' ? '提交签署失败' : '激活失败');
    if (result) onDone(result);
  };
  return <CommandModal title={action === 'submit' ? '提交签署' : '激活合同'} command={command} okText={action === 'submit' ? '提交签署' : '激活'}
    onOk={() => void submit()} onClose={onClose} onReload={onReload} width={480}>
    <p>{action === 'submit'
      ? `合同 ${agreement.agreementNo} 将进入待签署；之后各参与方逐一登记签署结果。`
      : `合同 ${agreement.agreementNo} 将生效，并向 acquiring 发布状态变更；生效后条款版本才能排期。`}</p>
  </CommandModal>;
}

/** 登记一个 Party 全部角色的签署结果。 */
export function AcceptanceModal({ agreement, party, onDone, onClose, onReload, onAuthorizationChanged }: Base & {
  agreement: Agreement; party: AgreementParty; onDone: (result: AgreementWriteResult) => void;
}) {
  const [form] = Form.useForm<AcceptanceFormValues>();
  const command = useCommercialCommand(onAuthorizationChanged, 'agreement', form);
  const method = Form.useWatch('acceptanceMethod', form) ?? 'E_SIGNATURE';
  const submit = async () => {
    const values = await form.validateFields();
    const result = await command.run(commercialPaths.acceptance(agreement.agreementId, party.partyId), 'POST',
      acceptanceBody(agreement.changeVersion, values), decodeAgreementWrite(agreement.agreementId), '签署登记失败');
    if (result) onDone(result);
  };
  return <CommandModal title={`登记签署 · Party ${party.partyId}`} command={command} okText="登记" onOk={() => void submit()} onClose={onClose} onReload={onReload}>
    <Form form={form} name="commercial-acceptance" layout="vertical" initialValues={{ acceptanceMethod: 'E_SIGNATURE' }}>
      <Form.Item name="acceptanceMethod" label="签署方式"><Radio.Group optionType="button" options={options(acceptanceMethodLabels, acceptanceMethods)} /></Form.Item>
      <div className="commercial-form-grid">
        <Form.Item name="acceptanceReference" label="签署编号" extra="全局唯一；同编号重放返回原结果" rules={[required('请填写签署编号'),
          { pattern: /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/, message: '字母数字及 . _ : -' }]}><Input maxLength={128} /></Form.Item>
        <Form.Item name="acceptedAt" label="签署时刻" rules={[{ required: true, message: '请选择签署时刻' }]}><DatePicker showTime /></Form.Item>
        {method === 'CLICK_ACCEPTANCE'
          ? <Form.Item name="acceptedByActorId" label="接受人 Actor ID" rules={[required('点击接受必须填写接受人'), idRule]}><Input maxLength={19} /></Form.Item>
          : <>
            <Form.Item name="fileId" label="执行文件 ID" rules={[required('请填写执行文件 ID'), idRule]}><Input maxLength={19} /></Form.Item>
            <Form.Item name="contentHash" label="执行文件 SHA-256" rules={[required('请填写 SHA-256'), hashRule]}><Input maxLength={64} /></Form.Item>
          </>}
      </div>
      <CommercialNote>同一 Party 的全部角色一次登记；全部 Party 签完后合同进入「已签署」。点击接受的证据是合同的签署文件。</CommercialNote>
    </Form>
  </CommandModal>;
}

/** 终止生效合同：生效终点不早于现在，附终止通知文件。 */
export function TerminateModal({ agreement, onDone, onClose, onReload, onAuthorizationChanged }: Base & {
  agreement: Agreement; onDone: (result: AgreementWriteResult) => void;
}) {
  const [form] = Form.useForm<{ effectiveTo: string; fileId: string; contentHash: string }>();
  const command = useCommercialCommand(onAuthorizationChanged, 'agreement', form);
  const submit = async () => {
    const values = await form.validateFields();
    const result = await command.run(commercialPaths.terminateAgreement(agreement.agreementId), 'POST', {
      expectedChangeVersion: agreement.changeVersion, effectiveTo: offsetInstant(values.effectiveTo),
      terminationNotice: { fileId: values.fileId.trim(), contentHash: values.contentHash.trim().toLowerCase() },
    }, decodeAgreementWrite(agreement.agreementId), '终止失败');
    if (result) onDone(result);
  };
  return <CommandModal title="终止合同" command={command} okText="终止" danger onOk={() => void submit()} onClose={onClose} onReload={onReload}>
    <Form form={form} name="commercial-terminate" layout="vertical" initialValues={{ effectiveTo: localInput(agreement.effectiveTo) }}>
      <Form.Item name="effectiveTo" label="生效终点" extra="不早于现在，不晚于原终点" rules={[{ required: true, message: '请选择生效终点' }]}><DatePicker showTime /></Form.Item>
      <div className="commercial-form-grid">
        <Form.Item name="fileId" label="终止通知文件 ID" rules={[required('请填写文件 ID'), idRule]}><Input maxLength={19} /></Form.Item>
        <Form.Item name="contentHash" label="SHA-256" rules={[required('请填写 SHA-256'), hashRule]}><Input maxLength={64} /></Form.Item>
      </div>
    </Form>
  </CommandModal>;
}

const productOptions = ['ACQUIRING', 'PAYOUT', 'FX', 'DISPUTE', 'DATA_ANALYTICS'];
/** 整体替换订阅产品；基座产品（TAX_INVOICE、PLATFORM）不在此维护。 */
export function ProductsModal({ agreement, onDone, onClose, onReload, onAuthorizationChanged }: Base & {
  agreement: Agreement; onDone: (result: AgreementWriteResult) => void;
}) {
  const [form] = Form.useForm<{ productCodes: string[] }>();
  const command = useCommercialCommand(onAuthorizationChanged, 'agreement', form);
  const submit = async () => {
    const values = await form.validateFields();
    const result = await command.run(commercialPaths.products(agreement.agreementId), 'PUT', {
      expectedChangeVersion: agreement.changeVersion, productCodes: [...new Set((values.productCodes ?? []).map(code => code.trim().toUpperCase()))],
    }, decodeAgreementWrite(agreement.agreementId), '订阅保存失败');
    if (result) onDone(result);
  };
  return <CommandModal title="订阅产品" command={command} onOk={() => void submit()} onClose={onClose} onReload={onReload} width={520}>
    <Form form={form} name="commercial-products" layout="vertical" initialValues={{ productCodes: agreement.productCodes }}>
      <Form.Item name="productCodes" label="产品" extra="整体替换；仍有开通项的产品不能移除"><Select mode="tags" options={productOptions.map(value => ({ value, label: value }))} /></Form.Item>
    </Form>
  </CommandModal>;
}

const capabilityOptions = ['CARD_VISA', 'CARD_MC', 'CARD_AMEX', 'CARD_UNIONPAY', 'APPLE_PAY', 'GOOGLE_PAY', 'PAYOUT_LOCAL', 'PAYOUT_SWIFT', 'FX_SPOT',
  'DISPUTE_HANDLING', 'ANALYTICS_SUBSCRIPTION'];
/** 新增或整体改写开通项：OE、可开通项、区间、保证金与约束行。 */
export function CapabilityModal({ agreement, capability, onDone, onClose, onReload, onAuthorizationChanged }: Base & {
  agreement: Agreement; capability?: AgreementCapability; onDone: () => void;
}) {
  const [form] = Form.useForm<CapabilityFormValues>();
  const command = useCommercialCommand(onAuthorizationChanged, 'agreement', form);
  const entities = agreement.agreementType === 'CLIENT' ? [agreement.entityNo ?? ''] : agreement.operatingEntityNos;
  const submit = async () => {
    const values = await form.validateFields();
    const body = capabilityBody(agreement.changeVersion, values, !!capability);
    const path = capability ? commercialPaths.capability(agreement.agreementId, capability.agreementCapabilityId) : commercialPaths.capabilities(agreement.agreementId);
    const result = await command.run(path, capability ? 'PUT' : 'POST', body, decodeCapabilityWrite(agreement.agreementId), '开通项保存失败');
    if (result) onDone();
  };
  return <CommandModal title={capability ? `编辑开通项 · ${capability.capabilityCode}` : '新增开通项'} command={command} onOk={() => void submit()}
    onClose={onClose} onReload={onReload} width={720}>
    <Form form={form} name="commercial-capability" layout="vertical" initialValues={capability ? {
      entityNo: capability.entityNo, capabilityCode: capability.capabilityCode, status: capability.status, effectiveFrom: localInput(capability.effectiveFrom),
      effectiveTo: localInput(capability.effectiveTo), rollingReserveRate: capability.rollingReserveRate ?? undefined, rollingReserveDays: capability.rollingReserveDays,
      limits: capability.limits,
    } : { entityNo: entities[0], limits: [{ limitCode: 'CURRENCY', limitValue: 'USD' }] }}>
      <div className="commercial-form-grid">
        <Form.Item name="entityNo" label="OE" rules={[{ required: true, message: '请选择 OE' }]}>
          <Select disabled={!!capability} options={entities.map(value => ({ value, label: value }))} /></Form.Item>
        <Form.Item name="capabilityCode" label="可开通项" rules={[{ required: true, message: '请选择可开通项' }]}>
          <Select disabled={!!capability} showSearch options={capabilityOptions.map(value => ({ value, label: value }))} /></Form.Item>
        {capability && <Form.Item name="status" label="状态"><Radio.Group optionType="button" options={options(capabilityStatusLabels, capabilityStatuses)} /></Form.Item>}
        {capability && <div />}
        <Form.Item name="effectiveFrom" label="生效起点" rules={[{ required: true, message: '请选择生效起点' }]}><DatePicker showTime /></Form.Item>
        <Form.Item name="effectiveTo" label="生效终点（可空）"><DatePicker showTime /></Form.Item>
        <Form.Item name="rollingReserveRate" label="滚动保证金比例" extra="0–1，例如 0.1" rules={[{ pattern: /^(0(\.[0-9]{1,10})?|1(\.0{1,10})?)$/, message: '0 到 1 的小数' }]}>
          <Input placeholder="0.1" /></Form.Item>
        <Form.Item name="rollingReserveDays" label="保证金天数" dependencies={['rollingReserveRate']} rules={[({ getFieldValue }) => ({ validator: async (_, days?: number | null) => {
          const error = reserveError(getFieldValue('rollingReserveRate'), days); if (error) throw new Error(error);
        } })]}><InputNumber className="commercial-number" min={1} max={3650} precision={0} /></Form.Item>
      </div>
      <h3 className="commercial-subtitle">约束（同类 OR，异类 AND）</h3>
      <Form.List name="limits">{(fields, { add, remove }) => <div className="commercial-row-list">
        {fields.map(field => <div className="commercial-row" key={field.key}>
          <Form.Item name={[field.name, 'limitCode']} rules={[{ required: true, message: '约束类别' }]}><Select options={limitCodes.map(value => ({ value, label: value }))} /></Form.Item>
          <Form.Item name={[field.name, 'limitValue']} normalize={(v: string) => v?.toUpperCase()} rules={[required('约束值')]}><Input placeholder="USD / AE / 1" maxLength={128} /></Form.Item>
          <div />
          <Button onClick={() => remove(field.name)}>移除</Button>
        </div>)}
        <Button onClick={() => add({ limitCode: 'CURRENCY' })}>添加约束</Button>
      </div>}</Form.List>
      <CommercialNote>CURRENCY 为三位币种，CONSUMER_COUNTRY 为两位国家码，REQUIRE_3DS 固定填 1。开通项的每个约束币种在条款版本排期时都必须有价格。</CommercialNote>
    </Form>
  </CommandModal>;
}

/** 新建条款方案：Client 条款引用 CLIENT 合同，Partner 条款引用同一 Local Partner 的 PARTNER 合同。 */
export function CreatePlanModal({ agreement, onClose, onCreated, onAuthorizationChanged }: {
  agreement?: Pick<Agreement, 'agreementId' | 'agreementType' | 'clientId' | 'localPartnerId'>; onClose: () => void;
  onCreated: (plan: TermPlan) => void; onAuthorizationChanged: () => void;
}) {
  const [form] = Form.useForm<PlanFormValues>();
  const command = useCommercialCommand(onAuthorizationChanged, 'version', form);
  const termType = Form.useWatch('termType', form) ?? 'CLIENT_PRICING';
  const partner = partnerTermTypes.includes(termType);
  const submit = async () => {
    const values = await form.validateFields();
    const plan = await command.run(commercialPaths.plans, 'POST', planBody(values), decodePlan, '方案创建失败');
    if (plan) onCreated(plan);
  };
  const defaultType = agreement?.agreementType === 'PARTNER' ? 'PARTNER_SHARE' : 'CLIENT_PRICING';
  return <CommandModal title="新建条款方案" command={command} okText="创建" onOk={() => void submit()} onClose={onClose} onReload={onClose}>
    <Form form={form} name="commercial-plan-create" layout="vertical" initialValues={{ termType: defaultType, agreementId: agreement?.agreementId,
      ownerId: agreement?.agreementType === 'PARTNER' ? agreement.localPartnerId ?? undefined : agreement?.clientId ?? undefined }}>
      <div className="commercial-form-grid">
        <Form.Item name="termType" label="条款类型" rules={[{ required: true, message: '请选择条款类型' }]}>
          <Select options={options(termTypeLabels, termTypes)} /></Form.Item>
        <Form.Item name="planCode" label="方案编号" extra="同一条款类型内唯一" rules={[required('请填写方案编号'), { pattern: /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/, message: '字母数字及 . _ -' }]}>
          <Input maxLength={64} /></Form.Item>
        <Form.Item name="agreementId" label="合同 ID" rules={[required('请填写合同 ID'), idRule]}><Input maxLength={19} disabled={!!agreement} /></Form.Item>
        <Form.Item name="ownerId" label={partner ? 'Local Partner ID' : 'Client ID'} extra="必须等于合同的 Owner" rules={[required('请填写 Owner'), idRule]}>
          <Input maxLength={19} /></Form.Item>
      </div>
      <CommercialNote>一份合同每种条款类型只有一个方案；方案创建后在其下新建版本、编辑价格并走审批。</CommercialNote>
    </Form>
  </CommandModal>;
}
