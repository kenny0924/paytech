import { Button, Form, Input, InputNumber, Radio, Select, Tag } from 'antd';
import { DatePicker } from '../components/DatePicker';
import {
  acceptanceBody, acceptanceMethods, agreementBody, businessModes, capabilityStatuses, commercialPaths, decodeAgreementWrite, decodeCapabilityWrite,
  decodePlan, entityNoPattern, idPattern, localInput, offsetInstant, partnerTermTypes, partyRoles, planBody, reserveError, termTypes,
} from './commercial';
import type {
  AcceptanceFormValues, Agreement, AgreementCapability, AgreementFormValues, AgreementParty, AgreementWriteResult, PlanFormValues, TermPlan,
} from './commercial';
import {
  capabilityBody, capabilityChoices, capabilityDimensions, capabilityFormPatch, dimensionNames, existingMark, reservePercentIssue,
} from './capabilityConditions';
import type { CapabilityChoice, CapabilityFormValues, DimensionDefinition } from './capabilityConditions';
import { ConditionFields } from './ConditionFields';
import { capabilityKindLabel } from './catalogTree';
import { ClientIdInput } from './ClientIdInput';
import { browserStorage, rememberClient, subscribableProductOptions } from './commercialConsole';
import { CommandDrawer, CommandModal, CommercialNote, DrawerSection } from './CommercialParts';
import { useCatalogCapabilities, useCatalogDimensions, useCatalogProducts } from './useCatalog';
import { useCommercialCommand } from './useCommercialCommand';
import { enumLabel } from '../i18n/index';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';
import './capabilityPricing.css';

interface Base { onClose: () => void; onReload: () => void; onAuthorizationChanged: () => void }
const required = (message: string) => ({ required: true, whitespace: true, message });
const idRule = (t: Translate) => ({ pattern: idPattern, message: t('form.idPattern') });
const hashRule = (t: Translate) => ({ pattern: /^[0-9a-fA-F]{64}$/, message: t('form.hashPattern') });
/** 下拉选项「显示名 代码」；显示名为 commercial 命名空间的 enum.<kind>.<值>。 */
const options = (t: Translate, kind: string, values: readonly string[]) =>
  values.map(value => ({ value, label: `${enumLabel(t, 'enum.' + kind, value)} ${value}` }));
/** 按钮组只显示名称，代码放悬停提示，避免长代码把按钮挤到第二行。 */
const buttonOptions = (t: Translate, kind: string, values: readonly string[]) =>
  values.map(value => ({ value, label: enumLabel(t, 'enum.' + kind, value), title: value }));

/** 新建合同：CLIENT 绑定一个 OE，PARTNER 覆盖多个 OE；资金模式与定价权本期固定，不在表单中选择。 */
export function CreateAgreementModal({ onClose, onCreated, onAuthorizationChanged }: { onClose: () => void; onCreated: (agreementId: string) => void; onAuthorizationChanged: () => void }) {
  const t = useT('commercial');
  const [form] = Form.useForm<AgreementFormValues>();
  const command = useCommercialCommand(onAuthorizationChanged, 'agreement', form);
  const type = Form.useWatch('agreementType', form) ?? 'CLIENT';
  const submit = async () => {
    const values = await form.validateFields();
    const result = await command.run(commercialPaths.agreements, 'POST', agreementBody(values), decodeAgreementWrite(), t('agreement.create.failed'));
    if (!result) return;
    if (values.agreementType === 'CLIENT' && values.clientId) rememberClient(browserStorage(), values.clientId);
    onCreated(result.agreementId);
  };
  return <CommandModal title={t('agreement.create.title')} command={command} okText={t('shared.create')} onOk={() => void submit()} onClose={onClose} onReload={onClose} width={760}>
    <Form form={form} name="commercial-agreement-create" layout="vertical" initialValues={{ agreementType: 'CLIENT', businessMode: 'MOR',
      parties: [{ partyRole: 'CLIENT' }, { partyRole: 'PLATFORM_SIGNATORY' }] }}>
      <div className="commercial-form-grid">
        <Form.Item name="agreementNo" label={t('agreement.field.agreementNo')} rules={[required(t('agreement.create.agreementNoRequired')),
          { pattern: /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/, message: t('agreement.create.agreementNoPattern') }]}>
          <Input maxLength={64} placeholder="AGR-AE001-0001" /></Form.Item>
        <Form.Item name="agreementName" label={t('agreement.create.agreementName')} rules={[required(t('agreement.create.agreementNameRequired'))]}><Input maxLength={255} /></Form.Item>
        <Form.Item name="agreementType" label={t('agreement.create.agreementType')}><Radio.Group optionType="button" options={buttonOptions(t, 'agreementType', ['CLIENT', 'PARTNER'])} /></Form.Item>
        {type === 'CLIENT'
          ? <Form.Item name="businessMode" label={t('agreement.create.businessMode')} rules={[{ required: true, message: t('agreement.create.businessModeRequired') }]}><Radio.Group optionType="button" options={businessModes.map(v => ({ value: v, label: v }))} /></Form.Item>
          : <div />}
        {type === 'CLIENT' ? <>
          <Form.Item name="clientId" label="Client ID" extra={t('agreement.create.clientIdHint')} rules={[required(t('agreement.create.clientIdRequired')), idRule(t)]}>
            <ClientIdInput /></Form.Item>
          <Form.Item name="entityNo" label={t('agreement.create.entityNo')} normalize={(v: string) => v?.toUpperCase()} rules={[required(t('agreement.create.entityNoRequired')), { pattern: entityNoPattern, message: t('form.entityNoPattern') }]}>
            <Input maxLength={5} placeholder="AE001" /></Form.Item>
          <Form.Item name="referralPartnerId" label={t('agreement.create.referralPartnerId')} rules={[idRule(t)]}><Input maxLength={19} /></Form.Item>
          <div />
        </> : <>
          <Form.Item name="localPartnerId" label="Local Partner ID" rules={[required(t('agreement.create.localPartnerIdRequired')), idRule(t)]}><Input maxLength={19} /></Form.Item>
          <Form.Item name="operatingEntityNos" label={t('agreement.create.operatingEntityNos')} extra={t('agreement.create.operatingEntityNosHint')} rules={[{ validator: async (_, value?: string[]) => {
            if ((value ?? []).some(v => !entityNoPattern.test(v))) throw new Error(t('agreement.create.operatingEntityNosPattern'));
          } }]}><Select mode="tags" tokenSeparators={[',', ' ']} placeholder="AE001, SA001" /></Form.Item>
        </>}
        <Form.Item name="effectiveFrom" label={t('form.effectiveFrom')} rules={[{ required: true, message: t('form.effectiveFromRequired') }]}><DatePicker showTime /></Form.Item>
        <Form.Item name="effectiveTo" label={t('form.effectiveToOptional')}><DatePicker showTime /></Form.Item>
      </div>
      <h3 className="commercial-subtitle">{t('agreement.create.parties')}</h3>
      <Form.List name="parties">{(fields, { add, remove }) => <div className="commercial-row-list">
        {fields.map(field => <div className="commercial-row" key={field.key}>
          <Form.Item name={[field.name, 'partyId']} rules={[required('Party ID'), idRule(t)]}><Input placeholder="Party ID" maxLength={19} /></Form.Item>
          <Form.Item name={[field.name, 'partyProfileId']} rules={[required(t('agreement.create.partyProfileId')), idRule(t)]}><Input placeholder={t('agreement.create.partyProfileId')} maxLength={19} /></Form.Item>
          <Form.Item name={[field.name, 'partyRole']} rules={[{ required: true, message: t('agreement.party.role') }]}><Select options={options(t, 'partyRole', partyRoles)} /></Form.Item>
          <Button onClick={() => remove(field.name)} disabled={fields.length <= 1}>{t('agreement.create.removeParty')}</Button>
        </div>)}
        <Button onClick={() => add({ partyRole: type === 'CLIENT' ? 'OPERATING_ENTITY' : 'LOCAL_PARTNER' })}>{t('agreement.create.addParty')}</Button>
      </div>}</Form.List>
      <h3 className="commercial-subtitle">{t('agreement.create.signingDocument')}</h3>
      <div className="commercial-form-grid">
        <Form.Item name="signingFileId" label={t('agreement.documents.fileId')} rules={[idRule(t)]}><Input maxLength={19} /></Form.Item>
        <Form.Item name="signingContentHash" label="SHA-256" dependencies={['signingFileId']} rules={[hashRule(t), ({ getFieldValue }) => ({ validator: async (_, value?: string) => {
          if (getFieldValue('signingFileId') && !value) throw new Error(t('agreement.create.hashWithFile'));
        } })]}><Input maxLength={64} /></Form.Item>
      </div>
      <CommercialNote>{t('agreement.create.note')}</CommercialNote>
    </Form>
  </CommandModal>;
}

/** 无表单的状态推进：提交签署、激活。 */
export function TransitionModal({ agreement, action, onDone, onClose, onReload, onAuthorizationChanged }: Base & {
  agreement: Agreement; action: 'submit' | 'activate'; onDone: (result: AgreementWriteResult) => void;
}) {
  const t = useT('commercial');
  const command = useCommercialCommand(onAuthorizationChanged, 'agreement');
  const path = action === 'submit' ? commercialPaths.submitForSignature(agreement.agreementId) : commercialPaths.activateAgreement(agreement.agreementId);
  const submit = async () => {
    const result = await command.run(path, 'POST', { expectedChangeVersion: agreement.changeVersion }, decodeAgreementWrite(agreement.agreementId),
      action === 'submit' ? t('agreement.transition.submitFailed') : t('agreement.transition.activateFailed'));
    if (result) onDone(result);
  };
  return <CommandModal title={action === 'submit' ? t('agreement.action.submit') : t('agreement.transition.activateTitle')} command={command}
    okText={action === 'submit' ? t('agreement.action.submit') : t('agreement.action.activate')}
    onOk={() => void submit()} onClose={onClose} onReload={onReload} width={480}>
    <p>{action === 'submit'
      ? t('agreement.transition.submitNotice', { agreementNo: agreement.agreementNo })
      : t('agreement.transition.activateNotice', { agreementNo: agreement.agreementNo })}</p>
  </CommandModal>;
}

/** 登记一个 Party 全部角色的签署结果。 */
export function AcceptanceModal({ agreement, party, onDone, onClose, onReload, onAuthorizationChanged }: Base & {
  agreement: Agreement; party: AgreementParty; onDone: (result: AgreementWriteResult) => void;
}) {
  const t = useT('commercial');
  const [form] = Form.useForm<AcceptanceFormValues>();
  const command = useCommercialCommand(onAuthorizationChanged, 'agreement', form);
  const method = Form.useWatch('acceptanceMethod', form) ?? 'E_SIGNATURE';
  const submit = async () => {
    const values = await form.validateFields();
    const result = await command.run(commercialPaths.acceptance(agreement.agreementId, party.partyId), 'POST',
      acceptanceBody(agreement.changeVersion, values), decodeAgreementWrite(agreement.agreementId), t('agreement.acceptance.failed'));
    if (result) onDone(result);
  };
  return <CommandModal title={t('agreement.acceptance.title', { partyId: party.partyId })} command={command} okText={t('agreement.acceptance.ok')} onOk={() => void submit()} onClose={onClose} onReload={onReload}>
    <Form form={form} name="commercial-acceptance" layout="vertical" initialValues={{ acceptanceMethod: 'E_SIGNATURE' }}>
      <Form.Item name="acceptanceMethod" label={t('sign.field.method')}><Radio.Group optionType="button" options={buttonOptions(t, 'acceptanceMethod', acceptanceMethods)} /></Form.Item>
      <div className="commercial-form-grid">
        <Form.Item name="acceptanceReference" label={t('sign.field.reference')} extra={t('agreement.acceptance.referenceHint')} rules={[required(t('sign.field.referenceRequired')),
          { pattern: /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/, message: t('agreement.acceptance.referencePattern') }]}><Input maxLength={128} /></Form.Item>
        <Form.Item name="acceptedAt" label={t('sign.field.time')} rules={[{ required: true, message: t('sign.issue.time') }]}><DatePicker showTime /></Form.Item>
        {method === 'CLICK_ACCEPTANCE'
          ? <Form.Item name="acceptedByActorId" label={t('agreement.acceptance.actorId')} rules={[required(t('agreement.acceptance.actorIdRequired')), idRule(t)]}><Input maxLength={19} /></Form.Item>
          : <>
            <Form.Item name="fileId" label={t('agreement.acceptance.fileId')} rules={[required(t('agreement.acceptance.fileIdRequired')), idRule(t)]}><Input maxLength={19} /></Form.Item>
            <Form.Item name="contentHash" label={t('agreement.acceptance.hash')} rules={[required(t('form.hashRequired')), hashRule(t)]}><Input maxLength={64} /></Form.Item>
          </>}
      </div>
      <CommercialNote>{t('agreement.acceptance.note')}</CommercialNote>
    </Form>
  </CommandModal>;
}

/** 终止生效合同：生效终点不早于现在，附终止通知文件。 */
export function TerminateModal({ agreement, onDone, onClose, onReload, onAuthorizationChanged }: Base & {
  agreement: Agreement; onDone: (result: AgreementWriteResult) => void;
}) {
  const t = useT('commercial');
  const [form] = Form.useForm<{ effectiveTo: string; fileId: string; contentHash: string }>();
  const command = useCommercialCommand(onAuthorizationChanged, 'agreement', form);
  const submit = async () => {
    const values = await form.validateFields();
    const result = await command.run(commercialPaths.terminateAgreement(agreement.agreementId), 'POST', {
      expectedChangeVersion: agreement.changeVersion, effectiveTo: offsetInstant(values.effectiveTo),
      terminationNotice: { fileId: values.fileId.trim(), contentHash: values.contentHash.trim().toLowerCase() },
    }, decodeAgreementWrite(agreement.agreementId), t('agreement.terminate.failed'));
    if (result) onDone(result);
  };
  return <CommandModal title={t('agreement.terminate.title')} command={command} okText={t('agreement.action.terminate')} danger onOk={() => void submit()} onClose={onClose} onReload={onReload}>
    <Form form={form} name="commercial-terminate" layout="vertical" initialValues={{ effectiveTo: localInput(agreement.effectiveTo) }}>
      <Form.Item name="effectiveTo" label={t('form.effectiveTo')} extra={t('agreement.terminate.effectiveToHint')} rules={[{ required: true, message: t('form.effectiveToRequired') }]}><DatePicker showTime /></Form.Item>
      <div className="commercial-form-grid">
        <Form.Item name="fileId" label={t('agreement.terminate.fileId')} rules={[required(t('form.fileIdRequired')), idRule(t)]}><Input maxLength={19} /></Form.Item>
        <Form.Item name="contentHash" label="SHA-256" rules={[required(t('form.hashRequired')), hashRule(t)]}><Input maxLength={64} /></Form.Item>
      </div>
    </Form>
  </CommandModal>;
}

/** 整体替换订阅产品；基座产品（TAX_INVOICE、PLATFORM）不在此维护。 */
export function ProductsModal({ agreement, onDone, onClose, onReload, onAuthorizationChanged }: Base & {
  agreement: Agreement; onDone: (result: AgreementWriteResult) => void;
}) {
  const t = useT('commercial');
  const [form] = Form.useForm<{ productCodes: string[] }>();
  const command = useCommercialCommand(onAuthorizationChanged, 'agreement', form);
  const products = useCatalogProducts(onAuthorizationChanged);
  const submit = async () => {
    const values = await form.validateFields();
    const result = await command.run(commercialPaths.products(agreement.agreementId), 'PUT', {
      expectedChangeVersion: agreement.changeVersion, productCodes: [...new Set((values.productCodes ?? []).map(code => code.trim().toUpperCase()))],
    }, decodeAgreementWrite(agreement.agreementId), t('agreement.products.failed'));
    if (result) onDone(result);
  };
  return <CommandModal title={t('agreement.products.title')} command={command} onOk={() => void submit()} onClose={onClose} onReload={onReload} width={520}>
    <Form form={form} name="commercial-products" layout="vertical" initialValues={{ productCodes: agreement.productCodes }}>
      <Form.Item name="productCodes" label={t('agreement.products.products')} extra={products.failed ? t('agreement.products.catalogFailedHint')
        : t('agreement.products.hint')}>
        <Select mode="tags" loading={products.loading} options={subscribableProductOptions(products.items)} /></Form.Item>
    </Form>
  </CommandModal>;
}

/**
 * 新增或整体改写开通项（右侧抽屉，三段）：基本（OE、可开通项卡片 / 状态）、接受条件（逐维度一行 + 3DS + 汇总）、有效期与保证金。
 * <li>卡片列出已订阅产品下全部启用的目录项（给出 productCode 时只列该产品）；该 OE 已有的项置灰并标「已开通」，点击切换为编辑那条开通项（onEdit）。</li>
 */
export function CapabilityDrawer({ agreement, capability, productCode, onEdit, onDone, onClose, onReload, onAuthorizationChanged }: Base & {
  agreement: Agreement; capability?: AgreementCapability; productCode?: string; onEdit: (capability: AgreementCapability) => void; onDone: () => void;
}) {
  const t = useT('commercial');
  const [form] = Form.useForm<CapabilityFormValues>();
  const command = useCommercialCommand(onAuthorizationChanged, 'agreement', form);
  const catalog = useCatalogCapabilities(onAuthorizationChanged);
  const products = useCatalogProducts(onAuthorizationChanged);
  const definitions = useCatalogDimensions(onAuthorizationChanged).items;
  const entities = agreement.agreementType === 'CLIENT' ? [agreement.entityNo ?? ''] : agreement.operatingEntityNos;
  const entityNo = Form.useWatch('entityNo', form) ?? capability?.entityNo ?? entities[0];
  const code = Form.useWatch('capabilityCode', form) ?? capability?.capabilityCode;
  const catalogItem = catalog.items.find(item => item.capabilityCode === code);
  const dimensions = capabilityDimensions(catalogItem, capability?.conditions ?? [{ dimensionCode: 'CURRENCY', values: [] }]);
  const choices = capabilityChoices(catalog.items, agreement.productCodes, agreement.capabilities, entityNo ?? '', productCode);
  const productName = productCode ? products.items.find(item => item.productCode === productCode)?.productName ?? productCode : null;
  const choose = (next: string) => {
    const currencies = form.getFieldValue(['conditions', 'CURRENCY']) as string[] | undefined;
    form.setFieldsValue({ capabilityCode: next, conditions: currencies?.length ? { CURRENCY: currencies } : {} });
  };
  const submit = async () => {
    const values = await form.validateFields();
    const body = capabilityBody(agreement.changeVersion, values, dimensions, !!capability);
    const path = capability ? commercialPaths.capability(agreement.agreementId, capability.agreementCapabilityId) : commercialPaths.capabilities(agreement.agreementId);
    const result = await command.run(path, capability ? 'PUT' : 'POST', body, decodeCapabilityWrite(agreement.agreementId), t('capabilityDrawer.failed'));
    if (result) onDone();
  };
  return <CommandDrawer title={capability ? t('capabilityDrawer.editTitle', { entityNo: capability.entityNo, name: catalogItem?.capabilityName ?? capability.capabilityCode })
    : productName ? t('capabilityDrawer.productTitle', { product: productName }) : t('agreement.detail.addCapability')}
    command={command} onOk={() => void submit()} onClose={onClose} onReload={onReload}>
    <Form form={form} name="commercial-capability" layout="vertical" initialValues={capability ? {
      entityNo: capability.entityNo, capabilityCode: capability.capabilityCode, status: capability.status, effectiveFrom: localInput(capability.effectiveFrom),
      effectiveTo: localInput(capability.effectiveTo), ...capabilityFormPatch(capability),
    } : { entityNo: entities[0], conditions: {}, require3ds: false }}>
      <DrawerSection title={t('capabilityDrawer.basic')}>
        <div className="commercial-form-grid">
          <Form.Item name="entityNo" label="OE" rules={[{ required: true, message: t('capabilityDrawer.entityNoRequired') }]}>
            <Select disabled={!!capability} options={entities.map(value => ({ value, label: value }))} /></Form.Item>
          {capability ? <Form.Item name="status" label={t('shared.column.status')}><Radio.Group optionType="button" options={buttonOptions(t, 'capabilityStatus', capabilityStatuses)} /></Form.Item>
            : <div />}
        </div>
        {capability ? <Form.Item name="capabilityCode" hidden><Input /></Form.Item>
          : <Form.Item name="capabilityCode" label={t('productSections.column.capability')} rules={[{ required: true, whitespace: true, message: t('capabilityDrawer.capabilityRequired') }]}
            extra={catalog.failed ? t('capabilityDrawer.catalogFailedHint')
              : productName ? t('capabilityDrawer.productHint', { product: productName }) : t('capabilityDrawer.subscribedHint')} normalize={(v: string) => v?.toUpperCase()}>
            {catalog.failed ? <Input maxLength={64} />
              : <CapabilityCards choices={choices} loading={catalog.loading} definitions={definitions} onChoose={choose} onEdit={onEdit} />}</Form.Item>}
      </DrawerSection>
      {code && <DrawerSection title={t('productSections.column.conditions')} extra={<span className="commercial-cell-meta">{t('capabilityDrawer.conditionsHint')}</span>}>
        <ConditionFields form={form} dimensions={dimensions} definitions={definitions} />
      </DrawerSection>}
      <DrawerSection title={t('capabilityDrawer.validityReserve')}>
        <div className="commercial-form-grid">
          <Form.Item name="effectiveFrom" label={t('form.effectiveFrom')} rules={[{ required: true, message: t('form.effectiveFromRequired') }]}><DatePicker showTime /></Form.Item>
          <Form.Item name="effectiveTo" label={t('form.effectiveToOptional')}><DatePicker showTime /></Form.Item>
          <Form.Item name="reservePercent" label={t('capabilityDrawer.reservePercent')} extra={t('capabilityDrawer.reservePercentHint')} rules={[{ validator: async (_, value?: string) => {
            const issue = reservePercentIssue(value); if (issue) throw new Error(issue);
          } }]}><Input placeholder="5" suffix="%" /></Form.Item>
          <Form.Item name="rollingReserveDays" label={t('capabilityDrawer.reserveDays')} dependencies={['reservePercent']} rules={[({ getFieldValue }) => ({ validator: async (_, days?: number | null) => {
            const error = reserveError(getFieldValue('reservePercent'), days); if (error) throw new Error(error);
          } })]}><InputNumber className="commercial-number" min={1} max={3650} precision={0} /></Form.Item>
        </div>
      </DrawerSection>
      <CommercialNote>{t('capabilityDrawer.note')}</CommercialNote>
    </Form>
  </CommandDrawer>;
}

/** 可开通项卡片单选：名称、代码、种类 Tag 与可配维度中文名；该 OE 已有的项置灰带标记，点击编辑那条开通项。受控于 Form.Item。 */
function CapabilityCards({ choices, loading, definitions, value, onChoose, onEdit }: {
  choices: CapabilityChoice[]; loading: boolean; definitions: DimensionDefinition[]; value?: string; onChoose: (code: string) => void;
  onEdit: (capability: AgreementCapability) => void;
}) {
  const t = useT('commercial');
  if (loading) return <div className="commercial-cell-meta">{t('capabilityDrawer.catalogLoading')}</div>;
  if (!choices.length) return <CommercialNote tone="warning">{t('capabilityDrawer.noChoices')}</CommercialNote>;
  return <div className="cc-cards" role="radiogroup">{choices.map(({ item, existing }) => {
    const mark = existingMark(existing);
    const selected = value === item.capabilityCode;
    return <button key={item.capabilityCode} type="button" role="radio" aria-checked={selected}
      title={existing ? t('capabilityDrawer.existingTitle') : undefined}
      className={`cc-card${selected ? ' is-selected' : ''}${existing ? ' is-existing' : ''}`}
      onClick={() => existing ? onEdit(existing) : onChoose(item.capabilityCode)}>
      <span className="cc-card-top"><span className="cc-card-name">{item.capabilityName}</span>
        {mark && <Tag color={existing?.status === 'ENABLED' ? 'green' : 'default'} className="cc-card-mark">{mark}</Tag>}</span>
      <span className="cc-card-line"><span className="mono cc-card-code">{item.capabilityCode}</span>
        <Tag className="cc-card-kind">{capabilityKindLabel(item.capabilityKind)}</Tag>
        {item.paymentMethodCode && item.paymentMethodCode !== item.capabilityCode && <span className="mono cc-card-code">{item.paymentMethodCode}</span>}</span>
      <span className="commercial-cell-meta cc-card-dims">{dimensionNames(item.dimensions, definitions)}</span>
    </button>;
  })}</div>;
}

/** 新建条款方案：Client 条款引用 CLIENT 合同，Partner 条款引用同一 Local Partner 的 PARTNER 合同。 */
export function CreatePlanModal({ agreement, onClose, onCreated, onAuthorizationChanged }: {
  agreement?: Pick<Agreement, 'agreementId' | 'agreementType' | 'clientId' | 'localPartnerId'>; onClose: () => void;
  onCreated: (plan: TermPlan) => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('commercial');
  const [form] = Form.useForm<PlanFormValues>();
  const command = useCommercialCommand(onAuthorizationChanged, 'version', form);
  const termType = Form.useWatch('termType', form) ?? 'CLIENT_PRICING';
  const partner = partnerTermTypes.includes(termType);
  const submit = async () => {
    const values = await form.validateFields();
    const plan = await command.run(commercialPaths.plans, 'POST', planBody(values), decodePlan, t('plan.create.failed'));
    if (plan) onCreated(plan);
  };
  const defaultType = agreement?.agreementType === 'PARTNER' ? 'PARTNER_SHARE' : 'CLIENT_PRICING';
  return <CommandModal title={t('agreement.detail.createPlan')} command={command} okText={t('shared.create')} onOk={() => void submit()} onClose={onClose} onReload={onClose}>
    <Form form={form} name="commercial-plan-create" layout="vertical" initialValues={{ termType: defaultType, agreementId: agreement?.agreementId,
      ownerId: agreement?.agreementType === 'PARTNER' ? agreement.localPartnerId ?? undefined : agreement?.clientId ?? undefined }}>
      <div className="commercial-form-grid">
        <Form.Item name="termType" label={t('shared.column.termType')} rules={[{ required: true, message: t('form.termTypeRequired') }]}>
          <Select options={options(t, 'termType', termTypes)} /></Form.Item>
        <Form.Item name="planCode" label={t('plan.field.planCode')} extra={t('plan.create.planCodeHint')} rules={[required(t('plan.create.planCodeRequired')),
          { pattern: /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/, message: t('form.codePattern') }]}>
          <Input maxLength={64} /></Form.Item>
        <Form.Item name="agreementId" label={t('plan.field.agreementId')} rules={[required(t('plan.create.agreementIdRequired')), idRule(t)]}><Input maxLength={19} disabled={!!agreement} /></Form.Item>
        <Form.Item name="ownerId" label={partner ? 'Local Partner ID' : 'Client ID'} extra={t('plan.create.ownerHint')} rules={[required(t('plan.create.ownerRequired')), idRule(t)]}>
          <Input maxLength={19} /></Form.Item>
      </div>
      <CommercialNote>{t('plan.create.note')}</CommercialNote>
    </Form>
  </CommandModal>;
}
