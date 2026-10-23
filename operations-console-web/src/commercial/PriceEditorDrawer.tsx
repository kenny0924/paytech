import { useEffect, useRef, useState } from 'react';
import { flushSync } from 'react-dom';
import { Alert, Button, Drawer, Form, Input, InputNumber, Select, Space, Tag, Tooltip } from 'antd';
import type { FormInstance, FormListFieldData } from 'antd';
import { ApiError, session } from '../api/session';
import { commercialPaths, decodeProfileList, decodeVersion, defaultBusinessCalendar, newPriceDraft, partnerTermTypes, pricesBody } from './commercial';
import type { AgreementCapability, Price, PriceDraft, PricingProfile, TermVersion } from './commercial';
import { priceKey, priceMatchesCapability } from './benchmark';
import { feeEventOptions } from './commercialConsole';
import type { CatalogCapability, CodeOption } from './commercialConsole';
import type { ComponentBenchmark } from './termReview';
import { ConflictBanner, CommercialNote } from './CommercialParts';
import { ComponentTable } from './ComponentTable.tsx';
import { priceSummary, toFormDrafts, toSubmitDrafts } from './componentTable.ts';
import { useCatalogFeeEvents } from './useCatalog';
import { useCommercialCommand } from './useCommercialCommand';
import { useT } from '../i18n/react';

/** 卡片头的开通项标签依据；不传则不显示（Partner 条款、标准价目）。 */
export interface DrawerGrouping { capabilities: readonly AgreementCapability[]; catalog: readonly CatalogCapability[] }
interface Props {
  version: TermVersion; entityNos: readonly string[]; prices: readonly Price[]; onClose: () => void; onSaved: (result: TermVersion) => void;
  onAuthorizationChanged: () => void;
  /** 保存路径；标准价目走模板专用路由。 */
  savePath?: string; grouping?: DrawerGrouping;
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
 * <li>保存：校验失败时展开出错价格并滚动到第一处错误；通过后换回小数费率、推导币种，再按原 pricesBody 提交（来源模板价格原样回传）。</li>
 * <li>卡片头显示对应开通项（挂不上标红）与来源标签；组件代码旁显示上次保存时的破标标记。保存后的相关性提示由编辑器展示。</li>
 */
export function PriceEditorDrawer({ version, entityNos, prices, onClose, onSaved, onAuthorizationChanged, savePath, grouping }: Props) {
  const t = useT('commercial'), tCommon = useT('common');
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
    const result = await command.run(savePath ?? commercialPaths.prices(version.versionId), 'PUT', pricesBody(version.changeVersion, drafts),
      decodeVersion(version.versionId), t('priceEditor.failed'));
    if (result) onSaved(result);
  };
  const benchmarks = (draft: PriceDraft | undefined) => (componentCode: string): ComponentBenchmark | null => {
    if (!draft) return null;
    const original = prices.find(price => priceKey(price) === priceKey(draft));
    return original?.components.find(component => component.componentCode === componentCode)?.benchmark ?? null;
  };
  const profileOptions = profiles.map(profile => ({ value: profile.pricingProfileCode,
    label: `${profile.pricingProfileCode} · ${profile.profileName}${profile.status === 'DRAFT' ? t('priceEditor.draftSuffix') : ''}` }));
  return <Drawer open size="min(1080px, 100vw)" rootClassName="commercial-drawer" title={t('priceEditor.title', { versionNo: version.versionNo })} onClose={() => { if (!command.saving) onClose(); }}
    destroyOnHidden extra={<Space><Button onClick={onClose} disabled={command.saving}>{tCommon('action.cancel')}</Button>
      <Button type="primary" loading={command.saving} disabled={command.conflict} onClick={() => void save()}>{t('priceEditor.saveAll')}</Button></Space>}>
    {command.conflict && <ConflictBanner onReload={onClose} />}
    {command.formError && <Alert type="error" showIcon className="commercial-banner" title={command.formError} />}
    <Form form={form} name="commercial-prices" layout="vertical" initialValues={initialValues}>
      <Form.List name="prices">{(fields, { add, remove }) => {
        collapse.register(fields); priceFields.current = fields;
        return <>
          {fields.map(field => <PriceCard key={field.key} form={form} field={field} open={collapse.isOpen(field.key)} partner={partner} entityNos={entityNos}
            profileOptions={profileOptions} eventChoices={eventChoices} onToggle={() => collapse.setOpen([field.key], !collapse.isOpen(field.key))}
            onRemove={() => remove(field.name)} grouping={grouping} profiles={profiles} benchmarks={benchmarks} />)}
          <Button onClick={() => add(newPriceDraft(version.termType, entityNos[0] ?? '', 'USD', profileOptions[0]?.value ?? '', fields.length + 1))}>
            {t('priceEditor.addPrice')}</Button>
        </>;
      }}</Form.List>
    </Form>
    <CommercialNote>{t('priceEditor.note')}</CommercialNote>
  </Drawer>;
}

const priceCardId = (index: string | number) => `commercial-price-card-${index}`;

/** 卡片头的开通项标签：画像支付方式 + 条件与合同开通项比较（近似）；无支付方式的价格不判断。 */
function CapabilityTag({ draft, grouping, profiles }: { draft: PriceDraft | undefined; grouping: DrawerGrouping | undefined; profiles: readonly PricingProfile[] }) {
  const t = useT('commercial');
  if (!draft || !grouping) return null;
  const profile = profiles.find(item => item.pricingProfileCode === draft.pricingProfileCode);
  if (!profile?.paymentMethodCode) return null;
  const price = { entityNo: draft.entityNo, currency: draft.currency, paymentMethodCode: profile.paymentMethodCode, conditions: profile.conditions };
  const matched = grouping.capabilities.find(item => item.status === 'ENABLED' && priceMatchesCapability(price, item, grouping.catalog))
    ?? grouping.capabilities.find(item => priceMatchesCapability(price, item, grouping.catalog));
  return matched ? <Tag>{matched.capabilityCode}</Tag> : <Tag color="red">{t('priceGroup.unmatched')}</Tag>;
}

/** 一条价格：折叠头（摘要 + 开通项 / 来源标签 + 删除），展开后 OE / 币种 / 优先级 / 画像一行、结算四字段一行（Partner 无）与组件表格；折叠时仍挂载以参与校验。 */
function PriceCard({ form, field, open, partner, entityNos, profileOptions, eventChoices, onToggle, onRemove, grouping, profiles, benchmarks }: {
  form: FormInstance; field: FormListFieldData; open: boolean; partner: boolean; entityNos: readonly string[]; profileOptions: CodeOption[];
  eventChoices: CodeOption[] | null; onToggle: () => void; onRemove: () => void; grouping?: DrawerGrouping; profiles: readonly PricingProfile[];
  benchmarks: (draft: PriceDraft | undefined) => (componentCode: string) => ComponentBenchmark | null;
}) {
  const t = useT('commercial');
  const draft = Form.useWatch(['prices', field.name], form) as PriceDraft | undefined;
  return <div className={`commercial-price-card pc-card${open ? ' is-open' : ''}`} id={priceCardId(field.name)}>
    <div className="pc-head">
      <button type="button" className="pc-toggle" aria-expanded={open} onClick={onToggle}>
        <span className="pc-caret">{open ? '▾' : '▸'}</span><span className="pc-summary">{priceSummary(draft, field.name)}</span>
      </button>
      <CapabilityTag draft={draft} grouping={grouping} profiles={profiles} />
      {draft?.sourceTemplatePriceId && <Tooltip title={t('priceEditor.sourceTooltip', { id: draft.sourceTemplatePriceId })}><Tag color="geekblue">{t('source.standardTemplate')}</Tag></Tooltip>}
      <Button size="small" danger onClick={onRemove}>{t('priceEditor.removePrice')}</Button>
    </div>
    <div className="pc-body" hidden={!open}>
      <div className="pc-grid-price">
        <Form.Item name={[field.name, 'entityNo']} label="OE" rules={[{ required: true, message: t('capabilityDrawer.entityNoRequired') }]}>
          <Select options={list(entityNos)} /></Form.Item>
        <Form.Item name={[field.name, 'currency']} label={t('matrix.column.currency')} normalize={(v: string) => v?.toUpperCase()}
          rules={[{ required: true, pattern: /^[A-Z]{3}$/, message: t('priceEditor.currencyPattern') }]}><Input maxLength={3} placeholder="USD" /></Form.Item>
        <Form.Item name={[field.name, 'priorityNo']} label={t('priceEditor.priority')} tooltip={t('priceEditor.priorityTooltip')} rules={[{ required: true, message: t('priceEditor.priorityRequired') }]}>
          <InputNumber className="commercial-number" min={1} precision={0} /></Form.Item>
        <Form.Item name={[field.name, 'pricingProfileCode']} label={t('priceEditor.profile')} rules={[{ required: true, message: t('priceEditor.profileRequired') }]}>
          <Select showSearch={{ optionFilterProp: 'label' }} options={profileOptions} placeholder={t('priceEditor.profilePlaceholder')} /></Form.Item>
      </div>
      {!partner && <div className="pc-grid-settlement">
        <Form.Item name={[field.name, 'settlementBusinessDays']} label={t('priceEditor.settlementDays')} rules={[{ required: true, message: t('priceEditor.required') }]}>
          <InputNumber className="commercial-number" min={0} max={365} precision={0} /></Form.Item>
        <Form.Item name={[field.name, 'businessCalendarCode']} label={t('priceEditor.calendar')} rules={[{ required: true, message: t('priceEditor.required') }]}>
          <Select options={list([defaultBusinessCalendar])} /></Form.Item>
        <Form.Item name={[field.name, 'settlementTimezone']} label={t('priceEditor.timezone')} rules={[{ required: true, message: t('priceEditor.required') }]}>
          <Select options={list(['UTC'])} /></Form.Item>
        <Form.Item name={[field.name, 'cutoffSecondOfDay']} label={t('priceEditor.cutoff')} tooltip="0–86399" rules={[{ required: true, message: t('priceEditor.required') }]}>
          <InputNumber className="commercial-number" min={0} max={86399} precision={0} /></Form.Item>
      </div>}
      <ComponentTable form={form} priceName={field.name} eventChoices={eventChoices} benchmarkOf={benchmarks(draft)} />
    </div>
  </div>;
}
