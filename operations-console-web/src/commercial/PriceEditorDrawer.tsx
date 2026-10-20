import { useEffect, useRef, useState } from 'react';
import { flushSync } from 'react-dom';
import { Alert, Button, Drawer, Form, Input, InputNumber, Select, Space } from 'antd';
import type { FormInstance, FormListFieldData } from 'antd';
import { ApiError, session } from '../api/session';
import { commercialPaths, decodeProfileList, decodeVersion, defaultBusinessCalendar, newPriceDraft, partnerTermTypes, pricesBody } from './commercial';
import type { Price, PriceDraft, PricingProfile, TermScope, TermVersion } from './commercial';
import { feeEventOptions } from './commercialConsole';
import type { CodeOption } from './commercialConsole';
import { ConflictBanner, CommercialNote } from './CommercialParts';
import { ComponentTable } from './ComponentTable.tsx';
import { priceSummary, toFormDrafts, toSubmitDrafts } from './componentTable.ts';
import { useCatalogFeeEvents } from './useCatalog';
import { useCommercialCommand } from './useCommercialCommand';

interface Props {
  version: TermVersion; scope: TermScope; prices: readonly Price[]; onClose: () => void; onSaved: () => void; onAuthorizationChanged: () => void;
}
interface ValidationFailure { errorFields?: { name: (string | number)[]; errors: string[] }[] }
const list = (values: readonly string[]) => values.map(value => ({ value, label: value }));
/** 超过该条数时价格卡片默认全部折叠。 */
const expandedPriceLimit = 3;

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
 * 价格卡片折叠状态：按 Form.List 的 field.key 记录；初始价格超过 3 条时默认折叠，新增价格默认展开。
 * toggled 只记与默认值不同的卡片。
 */
function usePriceCollapse(initialCount: number) {
  const initialKeys = useRef<Set<number> | null>(null);
  const [toggled, setToggled] = useState<ReadonlySet<number>>(new Set());
  const defaultOpen = (key: number) => !initialKeys.current?.has(key) || initialCount <= expandedPriceLimit;
  const isOpen = (key: number) => defaultOpen(key) !== toggled.has(key);
  const register = (fields: readonly FormListFieldData[]) => { initialKeys.current ??= new Set(fields.map(field => field.key)); };
  const setOpen = (keys: readonly number[], open: boolean) => setToggled(current => {
    const next = new Set(current);
    keys.forEach(key => { if (defaultOpen(key) === open) next.delete(key); else next.add(key); });
    return next;
  });
  return { isOpen, register, setOpen };
}

/**
 * 价格集合编辑：整体替换 DRAFT 版本的全部价格
 * <li>每条价格 = OE + 币种 + 画像 + 优先级；Client 条款带结算四字段，Partner 条款不带；卡片可折叠，头部显示一行摘要。</li>
 * <li>组件为每行一个的可编辑表格（ComponentTable）；费率按百分比输入，组件币种保存时自动取价格币种。</li>
 * <li>保存：校验失败时展开出错价格并滚动到第一处错误；通过后换回小数费率、推导币种，再按原 pricesBody 提交。</li>
 */
export function PriceEditorDrawer({ version, scope, prices, onClose, onSaved, onAuthorizationChanged }: Props) {
  const [form] = Form.useForm<{ prices: PriceDraft[] }>();
  const command = useCommercialCommand(onAuthorizationChanged, 'version', form);
  const profiles = useProfiles(onAuthorizationChanged);
  const catalogEvents = useCatalogFeeEvents(version.termType, onAuthorizationChanged);
  const eventChoices = catalogEvents.failed ? null : feeEventOptions(catalogEvents.items);
  const partner = partnerTermTypes.includes(version.termType);
  const collapse = usePriceCollapse(prices.length);
  const priceFields = useRef<readonly FormListFieldData[]>([]);
  const [initialValues] = useState(() => ({ prices: toFormDrafts(prices) }));
  const revealFirstError = (failure: ValidationFailure) => {
    const errorFields = failure.errorFields ?? [];
    if (!errorFields.length) return;
    const keys = errorFields.map(item => priceFields.current.find(field => field.name === item.name[1])?.key).filter(key => key !== undefined);
    flushSync(() => collapse.setOpen([...new Set(keys)], true));
    const first = errorFields[0].name;
    document.getElementById(priceCardId(first[1]))?.scrollIntoView({ block: 'start' });
    form.scrollToField(first, { block: 'center' });
  };
  const save = async () => {
    try { await form.validateFields(); } catch (failure) { revealFirstError(failure as ValidationFailure); return; }
    const drafts = toSubmitDrafts((form.getFieldsValue(true) as { prices?: PriceDraft[] }).prices ?? []);
    const result = await command.run(commercialPaths.prices(version.versionId), 'PUT', pricesBody(version.changeVersion, drafts),
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
    <Form form={form} name="commercial-prices" layout="vertical" initialValues={initialValues}>
      <Form.List name="prices">{(fields, { add, remove }) => {
        collapse.register(fields); priceFields.current = fields;
        return <>
          {fields.map(field => <PriceCard key={field.key} form={form} field={field} open={collapse.isOpen(field.key)} partner={partner} scope={scope}
            profileOptions={profileOptions} eventChoices={eventChoices} onToggle={() => collapse.setOpen([field.key], !collapse.isOpen(field.key))}
            onRemove={() => remove(field.name)} />)}
          <Button onClick={() => add(newPriceDraft(version.termType, scope.entityNos[0] ?? '', 'USD', profileOptions[0]?.value ?? '', fields.length + 1))}>
            添加价格</Button>
        </>;
      }}</Form.List>
    </Form>
    <CommercialNote>保存为整体替换：未出现在此处的价格会被删除。金额为主单位十进制；费率按百分比输入（2.9 即 2.9%，保存为 0.029）。
      组件金额币种自动取价格币种（固定额、阶梯、填了上下限的百分比），其余为空。本期排期只接受 BLENDED、交易金额阶梯，拒绝 RETROACTIVE。</CommercialNote>
  </Drawer>;
}

const priceCardId = (index: string | number) => `commercial-price-card-${index}`;

/** 一条价格：折叠头（摘要 + 删除），展开后 OE / 币种 / 优先级 / 画像一行、结算四字段一行（Partner 无）与组件表格；折叠时仍挂载以参与校验。 */
function PriceCard({ form, field, open, partner, scope, profileOptions, eventChoices, onToggle, onRemove }: {
  form: FormInstance; field: FormListFieldData; open: boolean; partner: boolean; scope: TermScope; profileOptions: CodeOption[];
  eventChoices: CodeOption[] | null; onToggle: () => void; onRemove: () => void;
}) {
  const draft = Form.useWatch(['prices', field.name], form) as PriceDraft | undefined;
  return <div className={`commercial-price-card pc-card${open ? ' is-open' : ''}`} id={priceCardId(field.name)}>
    <div className="pc-head">
      <button type="button" className="pc-toggle" aria-expanded={open} onClick={onToggle}>
        <span className="pc-caret">{open ? '▾' : '▸'}</span><span className="pc-summary">{priceSummary(draft, field.name)}</span>
      </button>
      <Button size="small" danger onClick={onRemove}>删除价格</Button>
    </div>
    <div className="pc-body" hidden={!open}>
      <div className="pc-grid-price">
        <Form.Item name={[field.name, 'entityNo']} label="OE" rules={[{ required: true, message: '请选择 OE' }]}>
          <Select options={list(scope.entityNos)} /></Form.Item>
        <Form.Item name={[field.name, 'currency']} label="币种" normalize={(v: string) => v?.toUpperCase()}
          rules={[{ required: true, pattern: /^[A-Z]{3}$/, message: '三位币种' }]}><Input maxLength={3} placeholder="USD" /></Form.Item>
        <Form.Item name={[field.name, 'priorityNo']} label="优先级" tooltip="同 OE + 币种内唯一，越小越优先" rules={[{ required: true, message: '请填写优先级' }]}>
          <InputNumber className="commercial-number" min={1} precision={0} /></Form.Item>
        <Form.Item name={[field.name, 'pricingProfileCode']} label="定价画像" rules={[{ required: true, message: '请选择画像' }]}>
          <Select showSearch={{ optionFilterProp: 'label' }} options={profileOptions} placeholder="排期时画像必须已启用" /></Form.Item>
      </div>
      {!partner && <div className="pc-grid-settlement">
        <Form.Item name={[field.name, 'settlementBusinessDays']} label="结算 T+N（工作日）" rules={[{ required: true, message: '必填' }]}>
          <InputNumber className="commercial-number" min={0} max={365} precision={0} /></Form.Item>
        <Form.Item name={[field.name, 'businessCalendarCode']} label="工作日日历" rules={[{ required: true, message: '必填' }]}>
          <Select options={list([defaultBusinessCalendar])} /></Form.Item>
        <Form.Item name={[field.name, 'settlementTimezone']} label="结算时区" rules={[{ required: true, message: '必填' }]}>
          <Select options={list(['UTC'])} /></Form.Item>
        <Form.Item name={[field.name, 'cutoffSecondOfDay']} label="日截止秒数" tooltip="0–86399" rules={[{ required: true, message: '必填' }]}>
          <InputNumber className="commercial-number" min={0} max={86399} precision={0} /></Form.Item>
      </div>}
      <ComponentTable form={form} priceName={field.name} eventChoices={eventChoices} />
    </div>
  </div>;
}
