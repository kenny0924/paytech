import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Drawer, Form, Input, InputNumber, Select, Space } from 'antd';
import type { FormInstance } from 'antd';
import { ApiError, session } from '../api/session';
import {
  bandBases, bandIssues, bandModes, calculationBases, commercialPaths, componentTypeLabels, componentTypes, decodeProfileList, decodeVersion,
  defaultBusinessCalendar, newPriceDraft, partnerTermTypes, priceDraft, pricesBody, refundBehaviors,
} from './commercial';
import type { BandDraft, ComponentDraft, Price, PriceDraft, PricingProfile, TermScope, TermVersion } from './commercial';
import { feeEventOptions } from './commercialConsole';
import type { CodeOption } from './commercialConsole';
import { ConflictBanner, CommercialNote } from './CommercialParts';
import { useCatalogFeeEvents } from './useCatalog';
import { useCommercialCommand } from './useCommercialCommand';

interface Props {
  version: TermVersion; scope: TermScope; prices: readonly Price[]; onClose: () => void; onSaved: () => void; onAuthorizationChanged: () => void;
}
const decimalRule = { pattern: /^(0|[1-9][0-9]{0,15})(\.[0-9]{1,8})?$/, message: '十进制金额，最多 8 位小数' };
const rateRule = { pattern: /^(0|1)(\.[0-9]{1,10})?$/, message: '0 至 1 的小数，例如 0.029' };
const list = (values: readonly string[]) => values.map(value => ({ value, label: value }));

/** 启用与草稿画像（下拉用）；读取失败时下拉为空，保存时由 commercial 校验画像代码。 */
function useProfiles(onAuthorizationChanged: () => void) {
  const [profiles, setProfiles] = useState<PricingProfile[]>([]);
  const authorizationChanged = useRef(onAuthorizationChanged); authorizationChanged.current = onAuthorizationChanged;
  useEffect(() => {
    const controller = new AbortController();
    session.request(commercialPaths.profiles, { signal: controller.signal }, decodeProfileList)
      .then(items => { if (!controller.signal.aborted) setProfiles(items.filter(item => item.status !== 'RETIRED')); })
      .catch(error => { if (!controller.signal.aborted && error instanceof ApiError && error.status === 403) authorizationChanged.current(); });
    return () => controller.abort();
  }, []);
  return profiles;
}

/**
 * 价格集合编辑：整体替换 DRAFT 版本的全部价格
 * <li>每条价格 = OE + 币种 + 画像 + 优先级；Client 条款带结算四字段，Partner 条款不带。</li>
 * <li>组件按类型显示字段：百分比（费率、上下限）、固定额（金额）、阶梯（基数、模式、档位表）；档位连续性在保存前检查。</li>
 */
export function PriceEditorDrawer({ version, scope, prices, onClose, onSaved, onAuthorizationChanged }: Props) {
  const [form] = Form.useForm<{ prices: PriceDraft[] }>();
  const command = useCommercialCommand(onAuthorizationChanged, 'version', form);
  const profiles = useProfiles(onAuthorizationChanged);
  const catalogEvents = useCatalogFeeEvents(version.termType, onAuthorizationChanged);
  const eventChoices = catalogEvents.failed ? null : feeEventOptions(catalogEvents.items);
  const partner = partnerTermTypes.includes(version.termType);
  const save = async () => {
    const values = await form.validateFields();
    const result = await command.run(commercialPaths.prices(version.versionId), 'PUT', pricesBody(version.changeVersion, values.prices ?? []),
      decodeVersion(version.versionId), '价格保存失败');
    if (result) onSaved();
  };
  const profileOptions = profiles.map(profile => ({ value: profile.pricingProfileCode,
    label: `${profile.pricingProfileCode} · ${profile.profileName}${profile.status === 'DRAFT' ? '（草稿）' : ''}` }));
  return <Drawer open size="min(1080px, 100vw)" rootClassName="commercial-drawer" title={`编辑价格 · v${version.versionNo}`} onClose={() => { if (!command.saving) onClose(); }}
    destroyOnHidden extra={<Space><Button onClick={onClose} disabled={command.saving}>取消</Button>
      <Button type="primary" loading={command.saving} disabled={command.conflict} onClick={() => void save()}>保存全部价格</Button></Space>}>
    {command.conflict && <ConflictBanner onReload={onSaved} />}
    {command.formError && <Alert type="error" showIcon className="commercial-banner" title={command.formError} />}
    <Form form={form} name="commercial-prices" layout="vertical" initialValues={{ prices: prices.map(priceDraft) }}>
      <Form.List name="prices">{(fields, { add, remove }) => <>
        {fields.map(field => <div className="commercial-price-card" key={field.key}>
          <div className="commercial-price-head"><strong>价格 {field.name + 1}</strong><span className="commercial-spacer" />
            <Button size="small" danger onClick={() => remove(field.name)}>删除价格</Button></div>
          <div className="commercial-form-grid-3">
            <Form.Item name={[field.name, 'entityNo']} label="OE" rules={[{ required: true, message: '请选择 OE' }]}>
              <Select options={list(scope.entityNos)} /></Form.Item>
            <Form.Item name={[field.name, 'currency']} label="币种" normalize={(v: string) => v?.toUpperCase()}
              rules={[{ required: true, pattern: /^[A-Z]{3}$/, message: '三位币种' }]}><Input maxLength={3} placeholder="USD" /></Form.Item>
            <Form.Item name={[field.name, 'priorityNo']} label="优先级" extra="同 OE + 币种内唯一，越小越优先" rules={[{ required: true, message: '请填写优先级' }]}>
              <InputNumber className="commercial-number" min={1} precision={0} /></Form.Item>
          </div>
          <Form.Item name={[field.name, 'pricingProfileCode']} label="定价画像" rules={[{ required: true, message: '请选择画像' }]}>
            <Select showSearch={{ optionFilterProp: 'label' }} options={profileOptions} placeholder="排期时画像必须已启用" /></Form.Item>
          {!partner && <div className="commercial-form-grid">
            <Form.Item name={[field.name, 'settlementBusinessDays']} label="结算 T+N（工作日）" rules={[{ required: true, message: '必填' }]}>
              <InputNumber className="commercial-number" min={0} max={365} precision={0} /></Form.Item>
            <Form.Item name={[field.name, 'businessCalendarCode']} label="工作日日历" rules={[{ required: true, message: '必填' }]}>
              <Select options={list([defaultBusinessCalendar])} /></Form.Item>
            <Form.Item name={[field.name, 'settlementTimezone']} label="结算时区" rules={[{ required: true, message: '必填' }]}>
              <Select options={list(['UTC'])} /></Form.Item>
            <Form.Item name={[field.name, 'cutoffSecondOfDay']} label="日截止秒数" extra="0–86399" rules={[{ required: true, message: '必填' }]}>
              <InputNumber className="commercial-number" min={0} max={86399} precision={0} /></Form.Item>
          </div>}
          <ComponentList form={form} priceName={field.name} eventChoices={eventChoices} />
        </div>)}
        <Button onClick={() => add(newPriceDraft(version.termType, scope.entityNos[0] ?? '', 'USD', profileOptions[0]?.value ?? '', fields.length + 1))}>
          添加价格</Button>
      </>}</Form.List>
    </Form>
    <CommercialNote>保存为整体替换：未出现在此处的价格会被删除。金额为主单位十进制；费率为小数（0.029 = 2.9%）。本期排期只接受 BLENDED、交易金额阶梯，拒绝 RETROACTIVE。</CommercialNote>
  </Drawer>;
}

/** 一条价格下的组件列表；eventChoices 为 null 表示目录读取失败，计费事件改为手输。 */
function ComponentList({ form, priceName, eventChoices }: { form: FormInstance; priceName: number; eventChoices: CodeOption[] | null }) {
  return <Form.List name={[priceName, 'components']}>{(fields, { add, remove }) => <>
    {fields.map(field => <ComponentEditor key={field.key} form={form} path={['prices', priceName, 'components', field.name]} name={field.name}
      eventChoices={eventChoices} onRemove={fields.length > 1 ? () => remove(field.name) : undefined} />)}
    <Button size="small" onClick={() => add({ componentCode: '', componentType: 'FIXED', triggerCode: 'CAPTURE', calculationBase: 'FIXED_PER_EVENT',
      refundBehavior: 'KEEP', rate: null, fixedAmount: null, currency: null, minAmount: null, maxAmount: null, bandBasis: null, bandMode: null,
      bands: null } satisfies ComponentDraft)}>添加组件</Button>
  </>}</Form.List>;
}

/** 单个组件：按类型显示字段，阶梯组件附档位表；计费事件下拉来自产品目录（按条款类型）。 */
function ComponentEditor({ form, path, name, eventChoices, onRemove }: {
  form: FormInstance; path: (string | number)[]; name: number; eventChoices: CodeOption[] | null; onRemove?: () => void;
}) {
  const type = Form.useWatch([...path, 'componentType'], form) as ComponentDraft['componentType'] | undefined;
  return <div className="commercial-component">
    <div className="commercial-form-grid-3">
      <Form.Item name={[name, 'componentCode']} label="组件代码" normalize={(v: string) => v?.toUpperCase()}
        rules={[{ required: true, pattern: /^[A-Z][A-Z0-9_]{0,63}$/, message: '大写代码，如 MDR' }]}><Input maxLength={64} /></Form.Item>
      <Form.Item name={[name, 'componentType']} label="类型"><Select options={componentTypes.map(value => ({ value, label: componentTypeLabels[value] }))} /></Form.Item>
      <Form.Item name={[name, 'triggerCode']} label="计费事件" normalize={(v: string) => v?.toUpperCase()} rules={[{ required: true, message: '请选择计费事件' }]}
        extra={eventChoices === null ? '目录读取失败，请直接输入计费事件代码' : undefined}>
        {eventChoices === null ? <Input maxLength={64} /> : <Select showSearch={{ optionFilterProp: 'label' }} options={eventChoices} />}</Form.Item>
      <Form.Item name={[name, 'calculationBase']} label="计算基数"><Select options={list(calculationBases)} /></Form.Item>
      <Form.Item name={[name, 'refundBehavior']} label="退款处理" extra="非支付类事件填 KEEP"><Select options={list(refundBehaviors)} /></Form.Item>
      <Form.Item name={[name, 'currency']} label="金额币种" extra="固定额、上下限、阶梯必填，须等于价格币种" normalize={(v: string) => v?.toUpperCase() || null}>
        <Input maxLength={3} /></Form.Item>
      {type === 'PERCENTAGE' && <>
        <Form.Item name={[name, 'rate']} label="费率" rules={[{ required: true, message: '请填写费率' }, rateRule]}><Input placeholder="0.029" /></Form.Item>
        <Form.Item name={[name, 'minAmount']} label="最低金额" rules={[decimalRule]}><Input /></Form.Item>
        <Form.Item name={[name, 'maxAmount']} label="最高金额" rules={[decimalRule]}><Input /></Form.Item>
      </>}
      {type === 'FIXED' && <Form.Item name={[name, 'fixedAmount']} label="固定金额" rules={[{ required: true, message: '请填写金额' }, decimalRule]}><Input placeholder="0.30" /></Form.Item>}
      {type === 'TIERED' && <>
        <Form.Item name={[name, 'bandBasis']} label="阶梯基数" rules={[{ required: true, message: '请选择' }]}><Select options={list(bandBases)} /></Form.Item>
        <Form.Item name={[name, 'bandMode']} label="阶梯模式" rules={[{ required: true, message: '请选择' }]}><Select options={list(bandModes)} /></Form.Item>
      </>}
    </div>
    {type === 'TIERED' && <BandTable form={form} path={path} name={name} />}
    {onRemove && <Button size="small" onClick={onRemove}>删除组件</Button>}
  </div>;
}

/** 档位表：[下界, 上界)，费率与固定金额恰好一个；保存前检查连续性。 */
function BandTable({ form, path, name }: { form: FormInstance; path: (string | number)[]; name: number }) {
  const bands = (Form.useWatch([...path, 'bands'], form) ?? []) as BandDraft[];
  const mode = Form.useWatch([...path, 'bandMode'], form) as string | null;
  const issues = bands.length ? bandIssues(bands, mode) : [];
  return <Form.List name={[name, 'bands']} rules={[{ validator: async (_, value?: BandDraft[]) => {
    const problems = bandIssues(value ?? [], form.getFieldValue([...path, 'bandMode']));
    if (problems.length) throw new Error(problems[0]);
  } }]}>{(fields, { add, remove }, { errors }) => <>
    {fields.map(field => <div className="commercial-band-row" key={field.key}>
      <Form.Item name={[field.name, 'lowerBound']} rules={[{ required: true, message: '下界' }, decimalRule]}><Input placeholder="下界（含）" /></Form.Item>
      <Form.Item name={[field.name, 'upperBound']} rules={[decimalRule]}><Input placeholder="上界（不含，末档留空）" /></Form.Item>
      <Form.Item name={[field.name, 'rate']} rules={[rateRule]}><Input placeholder="费率" /></Form.Item>
      <Form.Item name={[field.name, 'fixedAmount']} rules={[decimalRule]}><Input placeholder="固定金额" /></Form.Item>
      <Button size="small" onClick={() => remove(field.name)}>移除</Button>
    </div>)}
    <Button size="small" onClick={() => add({ lowerBound: bands.at(-1)?.upperBound ?? (bands.length ? '' : '0'), upperBound: null, rate: null, fixedAmount: null })}>添加档位</Button>
    {issues.length > 0 && <ul className="commercial-issues">{issues.map(issue => <li key={issue}>{issue}</li>)}</ul>}
    <Form.ErrorList errors={errors} />
  </>}</Form.List>;
}
