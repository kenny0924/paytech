import { AutoComplete, Checkbox, Form, Input, Radio, Select, Tag } from 'antd';
import type { DimensionDefinition } from './capabilityConditions';
import { dimensionLabel } from './capabilityConditions';
import {
  acceptanceDimensionOptions, attachChoice, attachLabel, attachOptions, capabilityCodePattern, catalogCapabilityForm, catalogCapabilityKinds, catalogPaths,
  createCatalogCapabilityBody, decodeCatalogCapabilityWrite, decodeFeeEventWrite, feeEventAttachBody, newCatalogCapabilityForm, paymentMethodSuggestions,
  updateCatalogCapabilityBody,
} from './catalogMaintenance';
import type { AttachChoice, CapabilityStatusAction, CatalogCapabilityFormValues } from './catalogMaintenance';
import { capabilityKindLabels, catalogLabel, catalogTermTypeLabels } from './catalogTree';
import type { CatalogEventRow } from './catalogTree';
import type { CatalogCapability, CatalogProduct } from './commercialConsole';
import { CommandDrawer, CommandModal, CommercialNote, DrawerSection } from './CommercialParts';
import { useCommercialCommand } from './useCommercialCommand';
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
  const [form] = Form.useForm<CatalogCapabilityFormValues>();
  const command = useCommercialCommand(onAuthorizationChanged, 'catalog', form);
  const kind = Form.useWatch('capabilityKind', form) ?? capability?.capabilityKind ?? 'PAYMENT_METHOD';
  const selected = (Form.useWatch('dimensionCodes', form) ?? []) as string[];
  const submit = async () => {
    const values = await form.validateFields();
    const result = capability
      ? await command.run(catalogPaths.capability(capability.productCapabilityId), 'PUT', updateCatalogCapabilityBody(capability.changeVersion ?? 1, values),
        decodeCatalogCapabilityWrite(capability.productCapabilityId), '开通项保存失败')
      : await command.run(catalogPaths.capabilities, 'POST', createCatalogCapabilityBody(values), decodeCatalogCapabilityWrite(), '开通项创建失败');
    if (result) onDone();
  };
  const productOptions = products.filter(item => !item.baseProduct && (item.status === 'ACTIVE' || item.productCode === capability?.productCode))
    .map(item => ({ value: item.productCode, label: `${item.productCode} · ${item.productName}` }));
  return <CommandDrawer title={capability ? `编辑开通项 · ${capability.capabilityCode}` : '新增开通项'} command={command} okText={capability ? '保存' : '创建'}
    onOk={() => void submit()} onClose={onClose} onReload={onDone}>
    <Form form={form} name="commercial-catalog-capability" layout="vertical"
      initialValues={capability ? catalogCapabilityForm(capability) : newCatalogCapabilityForm(productCode)}>
      <DrawerSection title="基本">
        <Form.Item name="capabilityKind" label="种类" extra={capability ? '种类创建后不可修改' : undefined}>
          <Radio.Group optionType="button" disabled={!!capability} options={catalogCapabilityKinds.map(value => ({ value,
            label: catalogLabel(capabilityKindLabels, value), title: value }))} /></Form.Item>
        <div className="commercial-form-grid">
          <Form.Item name="capabilityCode" label="开通项代码" normalize={(v: string) => v?.toUpperCase()} extra={capability ? '代码不可修改' : '全局唯一，如 STC_PAY、RECURRING'}
            rules={[{ required: true, whitespace: true, message: '请填写代码' }, { pattern: capabilityCodePattern, message: '大写字母开头，2–64 位大写字母、数字、下划线' }]}>
            <Input maxLength={64} disabled={!!capability} /></Form.Item>
          <Form.Item name="capabilityName" label="名称" rules={[{ required: true, whitespace: true, message: '请填写名称' }]}><Input maxLength={128} /></Form.Item>
          <Form.Item name="productCode" label="归属产品" rules={[{ required: true, message: '请选择产品' }]}><Select options={productOptions} /></Form.Item>
          {kind === 'PAYMENT_METHOD' ? <Form.Item name="paymentMethodCode" label="支付方式代码" normalize={(v: string) => v?.toUpperCase()}
            extra={capability ? '支付方式不可修改' : '与 Payment 支付方式目录同一套代码；一期按格式校验'}
            rules={[{ required: true, whitespace: true, message: '支付方式开通项必须填写支付方式代码' }, { pattern: capabilityCodePattern, message: '大写代码' }]}>
            <AutoComplete disabled={!!capability} options={paymentMethodSuggestions.map(value => ({ value }))} /></Form.Item> : <div />}
        </div>
      </DrawerSection>
      <DrawerSection title="可配维度">
        <Form.Item name="dimensionCodes" label="合同开通时可设置的接受条件"
          extra={definitionsFailed ? '维度清单读取失败，可直接输入维度代码' : '只列可用于开通的维度；未选的维度在合同开通项上不可配置'}>
          <Select mode={definitionsFailed ? 'tags' : 'multiple'} allowClear options={acceptanceDimensionOptions(definitions)}
            onChange={(codes: string[]) => form.setFieldValue('requiredDimensions', ((form.getFieldValue('requiredDimensions') ?? []) as string[])
              .filter(code => codes.includes(code)))} /></Form.Item>
        {selected.length > 0 && <Form.Item name="requiredDimensions" label="必填维度">
          <Checkbox.Group options={selected.map(code => ({ value: code, label: dimensionLabel(code, definitions) }))} /></Form.Item>}
      </DrawerSection>
      <CommercialNote>停用后新合同不可再开通该项，已开通的合同不受影响；可随时重新启用。支付方式一般以币种为必填维度。</CommercialNote>
    </Form>
  </CommandDrawer>;
}

const statusTexts: Record<CapabilityStatusAction, { title: string; ok: string; body: string }> = {
  retire: { title: '停用开通项', ok: '停用', body: '停用后新合同不能再开通该项；已开通的合同与历史价格不受影响。' },
  activate: { title: '启用开通项', ok: '启用', body: '启用后合同可以开通该项。' },
};

/** 停用 / 启用目录开通项。 */
export function CapabilityStatusModal({ capability, action, onClose, onDone, onAuthorizationChanged }: Base & {
  capability: CatalogCapability; action: CapabilityStatusAction;
}) {
  const command = useCommercialCommand(onAuthorizationChanged, 'catalog');
  const text = statusTexts[action];
  const submit = async () => {
    const result = await command.run(catalogPaths.capabilityAction(capability.productCapabilityId, action), 'POST',
      { expectedChangeVersion: capability.changeVersion ?? 1 }, decodeCatalogCapabilityWrite(capability.productCapabilityId), `${text.title}失败`);
    if (result) onDone();
  };
  return <CommandModal title={`${text.title} · ${capability.capabilityCode}`} command={command} okText={text.ok} danger={action === 'retire'}
    onOk={() => void submit()} onClose={onClose} onReload={onDone} width={480}>
    <p>{capability.capabilityName}（{capability.capabilityCode}）。{text.body}</p>
  </CommandModal>;
}

/**
 * 计费事件挂接开通项：同产品同事件的各条款类型登记一起改为同一挂接；逐条提交，任一失败停止并显示原因。
 * 功能类事件挂到本产品下的功能开通项，支付方式等挂到种类（任一该种类开通项），基础产品事件无需开通。
 */
export function FeeEventAttachModal({ row, capabilities, onClose, onDone, onAuthorizationChanged }: Base & {
  row: CatalogEventRow; capabilities: CatalogCapability[];
}) {
  const [form] = Form.useForm<{ choice: AttachChoice }>();
  const command = useCommercialCommand(onAuthorizationChanged, 'catalog', form);
  const current = row.feeEvents.map(attachChoice);
  const submit = async () => {
    const { choice } = await form.validateFields();
    for (const event of row.feeEvents) {
      if (attachChoice(event) === choice) continue;
      const result = await command.run(catalogPaths.feeEventCapability(event.productFeeEventId), 'PUT', feeEventAttachBody(event.changeVersion ?? 1, choice),
        decodeFeeEventWrite(event.productFeeEventId), '挂接保存失败');
      if (!result) return;
    }
    onDone();
  };
  return <CommandModal title={`计费事件挂接 · ${row.productCode} / ${row.feeEventCode}`} command={command} onOk={() => void submit()} onClose={onClose}
    onReload={onDone} width={600}>
    <Form form={form} name="commercial-fee-event-attach" layout="vertical" initialValues={{ choice: current[0] }}>
      <Form.Item name="choice" label="挂接开通项" rules={[{ required: true, message: '请选择' }]}>
        <Select options={attachOptions(row.productCode, capabilities)} /></Form.Item>
    </Form>
    <h3 className="commercial-subtitle">登记（{row.feeEvents.length}）</h3>
    <ul className="cc-event-list">{row.feeEvents.map(event => <li key={event.productFeeEventId}>
      <Tag>{catalogLabel(catalogTermTypeLabels, event.termType)}</Tag>{attachLabel(event, capabilities)}</li>)}</ul>
    <CommercialNote>挂接决定该事件的价格需要哪类开通项：价格相关性校验按挂接判断「价格是否对应已开通项」。</CommercialNote>
  </CommandModal>;
}
