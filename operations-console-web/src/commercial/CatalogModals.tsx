import { AutoComplete, Checkbox, Form, Input, Radio, Select, Tag } from 'antd';
import type { DimensionDefinition } from './capabilityConditions';
import { dimensionLabel } from './capabilityConditions';
import {
  acceptanceDimensionOptions, attachChoice, attachLabel, attachOptions, capabilityCodePattern, catalogCapabilityForm, catalogCapabilityKinds, catalogPaths,
  createCatalogCapabilityBody, decodeCatalogCapabilityWrite, decodeFeeEventWrite, feeEventAttachBody, newCatalogCapabilityForm, paymentMethodSuggestions,
  updateCatalogCapabilityBody,
} from './catalogMaintenance';
import type { AttachChoice, CapabilityStatusAction, CatalogCapabilityFormValues } from './catalogMaintenance';
import { capabilityKindLabel, catalogTermTypeLabel } from './catalogTree';
import type { CatalogEventRow } from './catalogTree';
import type { CatalogCapability, CatalogProduct } from './commercialConsole';
import { CommandDrawer, CommandModal, CommercialNote, DrawerSection } from './CommercialParts';
import { useCommercialCommand } from './useCommercialCommand';
import { useT } from '../i18n/react';
import './capabilityPricing.css';

interface Base { onClose: () => void; onDone: () => void; onAuthorizationChanged: () => void }

/**
 * 新增或编辑目录开通项（右侧抽屉，两段）：基本（种类、代码（新增后不可改）、名称、归属产品、支付方式）与可配维度（及必填）。
 * <li>支付方式种类必须带支付方式代码，代码输入带已知代码建议；功能等其他种类没有支付方式。</li>
 * <li>可配维度只列维度清单中可用于开通的；维度清单读取失败时允许直接输入维度代码。</li>
 */
export function CatalogCapabilityDrawer({ products, productCode, capability, definitions, definitionsFailed, onClose, onDone, onAuthorizationChanged }: Base & {
  products: CatalogProduct[]; productCode: string; capability?: CatalogCapability; definitions: DimensionDefinition[]; definitionsFailed: boolean;
}) {
  const t = useT('commercial'), tCommon = useT('common');
  const [form] = Form.useForm<CatalogCapabilityFormValues>();
  const command = useCommercialCommand(onAuthorizationChanged, 'catalog', form);
  const kind = Form.useWatch('capabilityKind', form) ?? capability?.capabilityKind ?? 'PAYMENT_METHOD';
  const selected = (Form.useWatch('dimensionCodes', form) ?? []) as string[];
  const submit = async () => {
    const values = await form.validateFields();
    const result = capability
      ? await command.run(catalogPaths.capability(capability.productCapabilityId), 'PUT', updateCatalogCapabilityBody(capability.changeVersion ?? 1, values),
        decodeCatalogCapabilityWrite(capability.productCapabilityId), t('capabilityDrawer.failed'))
      : await command.run(catalogPaths.capabilities, 'POST', createCatalogCapabilityBody(values), decodeCatalogCapabilityWrite(), t('catalogDrawer.createFailed'));
    if (result) onDone();
  };
  const productOptions = products.filter(item => !item.baseProduct && (item.status === 'ACTIVE' || item.productCode === capability?.productCode))
    .map(item => ({ value: item.productCode, label: `${item.productCode} · ${item.productName}` }));
  return <CommandDrawer title={capability ? t('catalogDrawer.editTitle', { code: capability.capabilityCode }) : t('agreement.detail.addCapability')} command={command}
    okText={capability ? tCommon('action.save') : t('shared.create')}
    onOk={() => void submit()} onClose={onClose} onReload={onDone}>
    <Form form={form} name="commercial-catalog-capability" layout="vertical"
      initialValues={capability ? catalogCapabilityForm(capability) : newCatalogCapabilityForm(productCode)}>
      <DrawerSection title={t('capabilityDrawer.basic')}>
        <Form.Item name="capabilityKind" label={t('catalogDrawer.kind')} extra={capability ? t('catalogDrawer.kindFixed') : undefined}>
          <Radio.Group optionType="button" disabled={!!capability} options={catalogCapabilityKinds.map(value => ({ value,
            label: capabilityKindLabel(value), title: value }))} /></Form.Item>
        <div className="commercial-form-grid">
          <Form.Item name="capabilityCode" label={t('catalogDrawer.code')} normalize={(v: string) => v?.toUpperCase()} extra={capability ? t('catalogDrawer.codeFixed') : t('catalogDrawer.codeHint')}
            rules={[{ required: true, whitespace: true, message: t('catalogDrawer.codeRequired') }, { pattern: capabilityCodePattern, message: t('catalogDrawer.codePattern') }]}>
            <Input maxLength={64} disabled={!!capability} /></Form.Item>
          <Form.Item name="capabilityName" label={t('catalogDrawer.name')} rules={[{ required: true, whitespace: true, message: t('catalogDrawer.nameRequired') }]}><Input maxLength={128} /></Form.Item>
          <Form.Item name="productCode" label={t('catalogDrawer.product')} rules={[{ required: true, message: t('catalogDrawer.productRequired') }]}><Select options={productOptions} /></Form.Item>
          {kind === 'PAYMENT_METHOD' ? <Form.Item name="paymentMethodCode" label={t('catalogDrawer.paymentMethodCode')} normalize={(v: string) => v?.toUpperCase()}
            extra={capability ? t('catalogDrawer.paymentMethodFixed') : t('catalogDrawer.paymentMethodHint')}
            rules={[{ required: true, whitespace: true, message: t('catalogDrawer.paymentMethodRequired') }, { pattern: capabilityCodePattern, message: t('catalogDrawer.upperCode') }]}>
            <AutoComplete disabled={!!capability} options={paymentMethodSuggestions.map(value => ({ value }))} /></Form.Item> : <div />}
        </div>
      </DrawerSection>
      <DrawerSection title={t('catalogDrawer.dimensions')}>
        <Form.Item name="dimensionCodes" label={t('catalogDrawer.dimensionCodes')}
          extra={definitionsFailed ? t('catalogDrawer.dimensionsFailedHint') : t('catalogDrawer.dimensionsHint')}>
          <Select mode={definitionsFailed ? 'tags' : 'multiple'} allowClear options={acceptanceDimensionOptions(definitions)}
            onChange={(codes: string[]) => form.setFieldValue('requiredDimensions', ((form.getFieldValue('requiredDimensions') ?? []) as string[])
              .filter(code => codes.includes(code)))} /></Form.Item>
        {selected.length > 0 && <Form.Item name="requiredDimensions" label={t('catalogDrawer.requiredDimensions')}>
          <Checkbox.Group options={selected.map(code => ({ value: code, label: dimensionLabel(code, definitions) }))} /></Form.Item>}
      </DrawerSection>
      <CommercialNote>{t('catalogDrawer.note')}</CommercialNote>
    </Form>
  </CommandDrawer>;
}

/** 停用 / 启用目录开通项。 */
export function CapabilityStatusModal({ capability, action, onClose, onDone, onAuthorizationChanged }: Base & {
  capability: CatalogCapability; action: CapabilityStatusAction;
}) {
  const t = useT('commercial');
  const command = useCommercialCommand(onAuthorizationChanged, 'catalog');
  const retire = action === 'retire';
  const submit = async () => {
    const result = await command.run(catalogPaths.capabilityAction(capability.productCapabilityId, action), 'POST',
      { expectedChangeVersion: capability.changeVersion ?? 1 }, decodeCatalogCapabilityWrite(capability.productCapabilityId),
      retire ? t('catalogStatus.retireFailed') : t('catalogStatus.activateFailed'));
    if (result) onDone();
  };
  return <CommandModal title={retire ? t('catalogStatus.retireTitle', { code: capability.capabilityCode }) : t('catalogStatus.activateTitle', { code: capability.capabilityCode })}
    command={command} okText={retire ? t('catalogStatus.retire') : t('catalogStatus.activate')} danger={retire}
    onOk={() => void submit()} onClose={onClose} onReload={onDone} width={480}>
    <p>{t(retire ? 'catalogStatus.retireBody' : 'catalogStatus.activateBody', { name: capability.capabilityName, code: capability.capabilityCode })}</p>
  </CommandModal>;
}

/**
 * 计费事件挂接开通项：同产品同事件的各条款类型登记一起改为同一挂接；逐条提交，任一失败停止并显示原因。
 * 功能类事件挂到本产品下的功能开通项，支付方式等挂到种类（任一该种类开通项），基础产品事件无需开通。
 */
export function FeeEventAttachModal({ row, capabilities, onClose, onDone, onAuthorizationChanged }: Base & {
  row: CatalogEventRow; capabilities: CatalogCapability[];
}) {
  const t = useT('commercial');
  const [form] = Form.useForm<{ choice: AttachChoice }>();
  const command = useCommercialCommand(onAuthorizationChanged, 'catalog', form);
  const current = row.feeEvents.map(attachChoice);
  const submit = async () => {
    const { choice } = await form.validateFields();
    for (const event of row.feeEvents) {
      if (attachChoice(event) === choice) continue;
      const result = await command.run(catalogPaths.feeEventCapability(event.productFeeEventId), 'PUT', feeEventAttachBody(event.changeVersion ?? 1, choice),
        decodeFeeEventWrite(event.productFeeEventId), t('attachModal.failed'));
      if (!result) return;
    }
    onDone();
  };
  return <CommandModal title={t('attachModal.title', { productCode: row.productCode, feeEventCode: row.feeEventCode })} command={command} onOk={() => void submit()} onClose={onClose}
    onReload={onDone} width={600}>
    <Form form={form} name="commercial-fee-event-attach" layout="vertical" initialValues={{ choice: current[0] }}>
      <Form.Item name="choice" label={t('attachModal.choice')} rules={[{ required: true, message: t('attachModal.choiceRequired') }]}>
        <Select options={attachOptions(row.productCode, capabilities)} /></Form.Item>
    </Form>
    <h3 className="commercial-subtitle">{t('attachModal.registrations', { count: row.feeEvents.length })}</h3>
    <ul className="cc-event-list">{row.feeEvents.map(event => <li key={event.productFeeEventId}>
      <Tag>{catalogTermTypeLabel(event.termType)}</Tag>{attachLabel(event, capabilities)}</li>)}</ul>
    <CommercialNote>{t('attachModal.note')}</CommercialNote>
  </CommandModal>;
}
