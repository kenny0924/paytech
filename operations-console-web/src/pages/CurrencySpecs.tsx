import { useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Button, Empty, Form, Input, InputNumber, Modal, Progress, Segmented, Select, Table, Tag } from 'antd';
import { ArrowRightOutlined, InfoCircleOutlined, LockOutlined } from '@ant-design/icons';
import type { ConsoleResource } from '../api/types';
import { session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { enumLabel } from '../i18n/index';
import { useLocale, useT } from '../i18n/react';
import { ConfigurationStatusModal } from '../configuration/ConfigurationStatusModal';
import { useConfigurationForm } from '../configuration/useConfigurationForm';
import {
  changeLogoBody, createCurrencySpecBody, currencyCodePattern, currencySpecPath, currencySpecStatuses, currencyStatusChange, currencyStatusColors,
  decodeCurrencySpecList, decodeCurrencySpecMutation, editableCurrencySpec, fileAddressPattern, logoChangeBlocked,
  manageCurrencySpecs, maxMinorUnit, maxNameLength, maxSymbolLength, numericCodePattern, roundingModes, updateCurrencySpecBody,
} from '../configuration/currencySpecModel';
import type { CurrencySpec, CurrencySpecFormValues, CurrencySpecStatus } from '../configuration/currencySpecModel';
import { usePublicFileUpload } from '../fileUpload/usePublicFileUpload';
import { currencyName, shortFileAddress } from '../referenceData/referenceData';
import '../dataQuery/dataQuery.css';
import '../referenceData/referenceData.css';
import '../configuration/currencySpecs.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
type Selection = { kind: 'spec'; currency: string } | { kind: 'new' } | null;
/** 启停确认弹窗的对象；保存并启用时版本取保存回执。 */
type StatusTarget = Pick<CurrencySpec, 'currency' | 'version' | 'status' | 'minorUnit' | 'roundingMode'>;

/**
 * 币种页签（原型 平台配置 · 币种 05—06，2026-10-27 并入"参考数据"页的第一个页签）：列表 + 右侧面板；草稿可改全部属性，启用或停用后标准属性冻结，只能更换图标
 * <li>名称列由前端按界面语言显示（Intl），英文名是 Platform 数据；图标只保存文件服务地址，公开地址由 Platform 换算。</li>
 * <li>没有管理按钮（OPERATIONS_CURRENCY_SPEC_MANAGE）时只读；没有文件上传权限时图标改为手工填写已上传的公开文件地址。</li>
 */
export function CurrencySpecsTab({ resources, onAuthorizationChanged }: Props) {
  const t = useT('currency'), tCommon = useT('common'); const { locale } = useLocale();
  const notify = useErrorNotification();
  const canManage = resources.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === manageCurrencySpecs);
  const [items, setItems] = useState<CurrencySpec[]>([]); const [loading, setLoading] = useState(true); const [failed, setFailed] = useState(false);
  const [reload, setReload] = useState(0); const [filter, setFilter] = useState<CurrencySpecStatus | 'ALL'>('ALL');
  const [selection, setSelection] = useState<Selection>(null); const [statusTarget, setStatusTarget] = useState<StatusTarget | null>(null);
  const [changing, setChanging] = useState<CurrencySpec | null>(null);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setFailed(false);
    session.request(currencySpecPath, { signal: controller.signal }, decodeCurrencySpecList)
      .then(value => { if (!controller.signal.aborted) setItems(value); })
      .catch(error => { if (!controller.signal.aborted) { setItems([]); setFailed(true); notify(error, t('specs.loadFailed')); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
  }, [reload, notify]);
  const refresh = () => setReload(value => value + 1);
  const rows = items.filter(item => filter === 'ALL' || item.status === filter);
  const creating = selection?.kind === 'new';
  const selected = selection?.kind === 'spec' ? items.find(item => item.currency === selection.currency) : creating ? undefined : rows[0];
  return <div className="currency-page">
    {failed && <Alert type="warning" showIcon title={t('specs.unavailable')} action={<Button onClick={refresh}>{tCommon('action.retry')}</Button>} />}
    <div className="refdata-toolbar">
      <Segmented<CurrencySpecStatus | 'ALL'> value={filter} onChange={setFilter}
        options={(['ALL', ...currencySpecStatuses] as const).map(value => ({ value,
          label: value === 'ALL' ? t('specs.all') : enumLabel(t, 'enum.status', value) }))} />
      <span className="dq-spacer" />
      {canManage && <Button type="primary" onClick={() => setSelection({ kind: 'new' })}>{t('specs.create')}</Button>}
    </div>
    <p className="muted currency-intro">{t('specs.intro')}</p>
    <div className="refdata-split">
      <section className="dq-panel">
        <Table<CurrencySpec> rowKey="currency" size="small" tableLayout="fixed" dataSource={rows} loading={loading} pagination={false} scroll={{ x: 800 }}
          rowClassName={row => `dq-clickable${!creating && row.currency === selected?.currency ? ' dq-selected-row' : ''}`}
          onRow={row => ({ onClick: () => setSelection({ kind: 'spec', currency: row.currency }) })}
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('specs.empty')} /> }} columns={[
            { title: t('specs.column.icon'), width: 56, render: (_, row) => <CurrencyIcon spec={row} /> },
            { title: t('specs.column.currency'), dataIndex: 'currency', width: 80, render: (value: string) => <strong>{value}</strong> },
            { title: t('specs.column.numericCode'), width: 108, className: 'refdata-nowrap', render: (_, row) => row.numericCode ?? '—' },
            { title: t('specs.column.name'), ellipsis: true, render: (_, row) => currencyName(row.currency, row.name, locale) },
            { title: t('specs.column.symbol'), width: 64, render: (_, row) => row.symbol ?? '—' },
            { title: t('specs.column.minorUnit'), dataIndex: 'minorUnit', width: 124, className: 'refdata-nowrap' },
            { title: t('specs.column.roundingMode'), width: 112, className: 'refdata-nowrap', render: (_, row) => <span className="mono">{row.roundingMode}</span> },
            { title: t('specs.column.status'), width: 104, className: 'refdata-nowrap', render: (_, row) => <Tag color={currencyStatusColors[row.status]}>{enumLabel(t, 'enum.status', row.status)}</Tag> },
            { title: t('specs.column.version'), dataIndex: 'version', width: 76, className: 'refdata-nowrap' },
          ]} />
        <p className="muted dq-table-foot">{t('specs.footnote')}</p>
      </section>
      <section className="dq-panel refdata-detail currency-panel">
        {creating || selected ? <CurrencyPanel key={creating ? 'new' : `${selected!.currency}:${selected!.version}`} spec={creating ? null : selected!}
          canManage={canManage} resources={resources} onAuthorizationChanged={onAuthorizationChanged} onCancel={() => setSelection(null)}
          onSaved={(currency, enable) => { setSelection({ kind: 'spec', currency }); refresh(); if (enable) setStatusTarget(enable); }}
          onChangeLogo={setChanging} onStatus={setStatusTarget} />
          : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={loading ? t('specs.loading') : t('specs.selectHint')} />}
      </section>
    </div>
    {statusTarget && <ConfigurationStatusModal kind="currency" code={statusTarget.currency} version={statusTarget.version}
      enabled={statusTarget.status === 'ACTIVE'} notice={<Alert type={statusTarget.status === 'ACTIVE' ? 'info' : 'warning'} showIcon
        title={currencyStatusChange(statusTarget).notice} style={{ marginBottom: 16 }} />}
      onClose={() => setStatusTarget(null)} onSaved={() => { setStatusTarget(null); refresh(); }} onAuthorizationChanged={onAuthorizationChanged} />}
    {changing && <LogoChangeModal spec={changing} resources={resources} onAuthorizationChanged={onAuthorizationChanged}
      onClose={() => setChanging(null)} onSaved={() => { setChanging(null); refresh(); }} />}
  </div>;
}

/** 币种图标：公开地址显示图片，否则显示符号或灰色占位。 */
function CurrencyIcon({ spec, large }: { spec: Pick<CurrencySpec, 'logoPublicUrl' | 'symbol' | 'currency'>; large?: boolean }) {
  const className = `currency-icon${large ? ' is-large' : ''}`;
  if (spec.logoPublicUrl) return <img className={className} src={spec.logoPublicUrl} alt={spec.currency} loading="lazy" />;
  return <span className={`${className} is-empty`} aria-label={spec.currency}>{spec.symbol ? spec.symbol.slice(0, 3) : '—'}</span>;
}

function LockedLabel({ children }: { children: ReactNode }) {
  return <span className="currency-locked">{children} <LockOutlined /></span>;
}

function ReadOnly({ label, value, mono, wide }: { label: ReactNode; value: string | null; mono?: boolean; wide?: boolean }) {
  return <div className={wide ? 'is-wide' : undefined}><div className="currency-label">{label}</div>
    <div className={`currency-readonly${mono ? ' mono' : ''}`}>{value ?? '—'}</div></div>;
}

/** 右侧面板：草稿（或新建）为表单；其他状态（或没有管理按钮）只读，启用或停用后可更换图标。 */
function CurrencyPanel({ spec, canManage, resources, onAuthorizationChanged, onCancel, onSaved, onChangeLogo, onStatus }: {
  spec: CurrencySpec | null; canManage: boolean; resources: ConsoleResource[]; onAuthorizationChanged: () => void; onCancel: () => void;
  onSaved: (currency: string, enable?: StatusTarget) => void; onChangeLogo: (spec: CurrencySpec) => void; onStatus: (spec: CurrencySpec) => void;
}) {
  const t = useT('currency'); const { locale } = useLocale();
  if (!spec || (editableCurrencySpec(spec) && canManage)) {
    return <CurrencyEditor spec={spec} resources={resources} onAuthorizationChanged={onAuthorizationChanged} onCancel={onCancel} onSaved={onSaved} />;
  }
  const frozen = spec.status !== 'DRAFT';
  return <>
    <div className="refdata-detail-head"><CurrencyIcon spec={spec} large />
      <div className="refdata-grow"><h2>{spec.currency} · {currencyName(spec.currency, spec.name, locale)}</h2>
        <span className="muted">{t('panel.meta', { status: enumLabel(t, 'enum.status', spec.status), version: spec.version })}</span></div>
      <Tag color={currencyStatusColors[spec.status]}>{enumLabel(t, 'enum.status', spec.status)}</Tag></div>
    <div className="currency-readonly-grid">
      <ReadOnly label={<LockedLabel>{t('editor.code')}</LockedLabel>} value={spec.currency} mono />
      <ReadOnly label={<LockedLabel>{t('editor.numericCode')}</LockedLabel>} value={spec.numericCode} mono />
      <ReadOnly wide label={<LockedLabel>{t('editor.name')}</LockedLabel>} value={spec.name} />
      <ReadOnly label={<LockedLabel>{t('editor.symbol')}</LockedLabel>} value={spec.symbol} />
      <ReadOnly label={<LockedLabel>{t('editor.minorUnitAndRounding')}</LockedLabel>} value={`${spec.minorUnit} · ${spec.roundingMode}`} mono />
    </div>
    {frozen && <p className="muted currency-frozen"><LockOutlined /> {t('panel.frozen')}</p>}
    <div className="currency-logo-row"><span className="muted">{t('editor.logo')}</span><CurrencyIcon spec={spec} />
      <span className="refdata-grow" title={spec.logoFileAddress ?? undefined}>{spec.logoFileAddress
        ? <><span className="mono">{shortFileAddress(spec.logoFileAddress)}</span> <span className="muted">· {spec.logoPublicUrl ? t('panel.publicFile') : t('panel.notPublic')}</span></>
        : <span className="muted">{t('panel.noLogo')}</span>}</span>
      {canManage && frozen && <Button onClick={() => onChangeLogo(spec)} disabled={logoChangeBlocked(spec)}>{t('logo.change')}</Button>}</div>
    {canManage && frozen && logoChangeBlocked(spec) && <Alert type="info" showIcon title={t('logo.blocked')} />}
    {canManage && <div className="currency-panel-foot"><span className="muted">{spec.status === 'ACTIVE' ? t('panel.disableHint') : t('panel.enableHint')}</span>
      <Button danger={spec.status === 'ACTIVE'} onClick={() => onStatus(spec)}>{currencyStatusChange(spec).verb}</Button></div>}
  </>;
}

/**
 * 新建或编辑草稿：全部属性可改；409 时保留草稿并提示刷新；Platform 422 的字段错误挂在对应控件下
 * <li>保存并启用 = 先保存，再用保存回执的版本打开启用确认（沿用启停弹窗）。</li>
 */
function CurrencyEditor({ spec, resources, onAuthorizationChanged, onCancel, onSaved }: {
  spec: CurrencySpec | null; resources: ConsoleResource[]; onAuthorizationChanged: () => void; onCancel: () => void;
  onSaved: (currency: string, enable?: StatusTarget) => void;
}) {
  const t = useT('currency'), tCommon = useT('common');
  const [form] = Form.useForm<CurrencySpecFormValues>(); const editor = useConfigurationForm(onAuthorizationChanged, form);
  const enableAfterSave = useRef(false); const [enabling, setEnabling] = useState(false);
  const roundingOptions = useMemo(() => roundingModes.map(value => ({ value, label: enumLabel(t, 'enum.roundingMode', value) })), [t]);
  const logo = Form.useWatch('logoFileAddress', form);
  async function save(values: CurrencySpecFormValues) {
    const currency = spec ? spec.currency : createCurrencySpecBody(values).currency;
    const result = spec
      ? await editor.save(`${currencySpecPath}/${encodeURIComponent(spec.currency)}`, 'PUT', updateCurrencySpecBody(values, spec),
        value => decodeCurrencySpecMutation(value, spec.currency, 'DRAFT'))
      : await editor.save(currencySpecPath, 'POST', createCurrencySpecBody(values), value => decodeCurrencySpecMutation(value, currency, 'DRAFT'));
    if (!result) return;
    onSaved(currency, enableAfterSave.current ? { currency, version: result.version, status: 'DRAFT', minorUnit: values.minorUnit ?? 0,
      roundingMode: values.roundingMode ?? 'HALF_UP' } : undefined);
  }
  const submit = (enable: boolean) => { enableAfterSave.current = enable; setEnabling(enable); form.submit(); };
  return <Form form={form} layout="vertical" className="currency-editor" disabled={editor.saving || editor.conflict} onFinish={save}
    onValuesChange={() => editor.setDirty(true)} initialValues={spec ? { currency: spec.currency, numericCode: spec.numericCode ?? '', name: spec.name ?? '',
      symbol: spec.symbol ?? '', minorUnit: spec.minorUnit, roundingMode: spec.roundingMode, logoFileAddress: spec.logoFileAddress, reason: '' }
      : { roundingMode: 'HALF_UP', logoFileAddress: null }}>
    <div className="refdata-detail-head"><CurrencyIcon spec={{ currency: spec?.currency ?? '', symbol: spec?.symbol ?? null, logoPublicUrl: spec?.logoPublicUrl ?? null }} large />
      <div className="refdata-grow"><h2>{spec ? t('editor.editTitle', { currency: spec.currency }) : t('editor.createTitle')}</h2>
        <span className="muted">{spec ? t('panel.draftMeta', { version: spec.version }) : t('editor.draftHint')}</span></div>
      <Tag color={currencyStatusColors.DRAFT}>{enumLabel(t, 'enum.status', 'DRAFT')}</Tag></div>
    <Form.Item name="currency" label={spec ? <LockedLabel>{t('editor.code')}</LockedLabel> : t('editor.code')} extra={spec ? t('editor.codeLocked') : t('editor.codeHint')}
      normalize={(value?: string) => value?.toUpperCase()}
      rules={[{ required: true, message: t('editor.codeRequired') }, { pattern: currencyCodePattern, message: t('editor.codePattern') }]}>
      <Input disabled={!!spec} maxLength={3} placeholder={t('editor.codePlaceholder')} className="mono" /></Form.Item>
    <div className="currency-form-row">
      <Form.Item name="numericCode" label={t('editor.numericCode')} rules={[{ required: true, message: t('editor.numericCodeRequired') },
        { pattern: numericCodePattern, message: t('editor.numericCodePattern') }]}><Input maxLength={3} className="mono" /></Form.Item>
      <Form.Item name="symbol" label={t('editor.symbol')} rules={[{ required: true, whitespace: true, message: t('editor.symbolRequired') },
        { max: maxSymbolLength, message: t('editor.symbolLength', { max: maxSymbolLength }) }]}><Input maxLength={maxSymbolLength} /></Form.Item>
    </div>
    <Form.Item name="name" label={t('editor.name')} extra={t('editor.nameHint')} rules={[{ required: true, whitespace: true, message: t('editor.nameRequired') },
      { max: maxNameLength, message: t('editor.nameLength', { max: maxNameLength }) }]}><Input maxLength={maxNameLength} /></Form.Item>
    <div className="currency-form-row">
      <Form.Item name="minorUnit" label={t('editor.minorUnit')}
        rules={[{ required: true, message: t('editor.minorUnitRequired') }, { type: 'integer', min: 0, max: maxMinorUnit, message: t('editor.minorUnitRange', { max: maxMinorUnit }) }]}>
        <InputNumber min={0} max={maxMinorUnit} precision={0} style={{ width: '100%' }} /></Form.Item>
      <Form.Item name="roundingMode" label={t('editor.roundingMode')} rules={[{ required: true, message: t('editor.roundingModeRequired') }]}>
        <Select options={roundingOptions} /></Form.Item>
    </div>
    <Form.Item name="logoFileAddress" label={t('editor.logo')} rules={[{ pattern: fileAddressPattern, message: t('logo.addressPattern') }]}>
      <LogoPicker resources={resources} onAuthorizationChanged={onAuthorizationChanged}
        currentUrl={spec && spec.logoFileAddress === logo ? spec.logoPublicUrl : null} /></Form.Item>
    <Form.Item name="reason" label={t('editor.reason')} rules={[{ required: true, whitespace: true, message: t('editor.reasonRequired') }]}>
      <Input maxLength={500} /></Form.Item>
    {editor.fieldErrors.some(error => error.field === 'logoFileAddress') && <Alert type="error" showIcon title={t('logo.rejected')} />}
    {editor.conflict && <Alert type="warning" showIcon title={t('editor.conflict')} />}
    <div className="currency-panel-foot"><span className={editor.dirty ? 'currency-dirty' : 'muted'}>{editor.dirty ? t('editor.unsaved') : ''}</span>
      <Button onClick={onCancel} disabled={editor.saving}>{tCommon('action.cancel')}</Button>
      <Button onClick={() => submit(false)} loading={editor.saving && !enabling}>{t('editor.saveDraft')}</Button>
      <Button type="primary" onClick={() => submit(true)} loading={editor.saving && enabling}>{t('editor.saveAndEnable')}</Button></div>
  </Form>;
}

/**
 * 图标选择：有文件上传权限时选择文件 → 公开上传 → 检查通过后写入文件地址；否则手工填写已上传公开文件的地址
 * <li>value / onChange 由 Form.Item 注入，值为 /files/v1/{fileId} 或 null。</li>
 */
function LogoPicker({ value, onChange, resources, onAuthorizationChanged, currentUrl }: {
  value?: string | null; onChange?: (value: string | null) => void; resources: ConsoleResource[]; onAuthorizationChanged: () => void; currentUrl: string | null;
}) {
  const t = useT('currency');
  const uploader = usePublicFileUpload(resources, onAuthorizationChanged);
  const input = useRef<HTMLInputElement>(null);
  const preview = uploader.state.kind === 'done' && uploader.state.file.fileAddress === value ? uploader.state.file.publicUrl : currentUrl;
  if (!uploader.enabled) return <div className="currency-logo-picker">
    <Input allowClear value={value ?? ''} placeholder={t('logo.addressPlaceholder')} className="mono" onChange={event => onChange?.(event.target.value.trim() || null)} />
    <div className="muted currency-logo-hint">{t('logo.manualHint')}</div></div>;
  return <div className="currency-logo-box">
    {preview ? <img className="currency-icon is-large" src={preview} alt="" /> : <span className="currency-icon is-large is-empty">—</span>}
    <div className="refdata-grow"><strong>{t('logo.uploadTitle')}</strong>
      <div className="muted">{value ? <span className="mono">{value}</span> : t('logo.uploadHint')}</div>
      {uploader.state.kind === 'running' && <Progress size="small" percent={uploader.state.percent} />}
      {uploader.state.kind === 'failed' && <div className="dq-text-error">{t('logo.uploadFailed', { name: uploader.state.name, reason: uploader.state.reason })}</div>}</div>
    <Button loading={uploader.state.kind === 'running'} onClick={() => input.current?.click()}>{t('logo.pick')}</Button>
    {value && <Button type="link" onClick={() => { uploader.reset(); onChange?.(null); }}>{t('logo.clear')}</Button>}
    <input ref={input} type="file" hidden accept="image/svg+xml,image/png" onChange={async event => {
      const file = event.target.files?.[0]; event.target.value = '';
      if (!file) return;
      const uploaded = await uploader.upload(file);
      if (uploaded) onChange?.(uploaded.fileAddress);
    }} />
  </div>;
}

/** Platform 对非草稿币种冻结的字段：只换图标时这些字段若与库中不同，Platform 以 422 字段错误拒绝（通常是页面数据已过期）。 */
const frozenFields = ['numericCode', 'name', 'symbol', 'minorUnit', 'roundingMode'];

/** 已启用或停用币种更换图标（原型 06）：当前 → 新图标、变更原因必填；只替换展示图标，写审计。 */
function LogoChangeModal({ spec, resources, onAuthorizationChanged, onClose, onSaved }: {
  spec: CurrencySpec; resources: ConsoleResource[]; onAuthorizationChanged: () => void; onClose: () => void; onSaved: () => void;
}) {
  const t = useT('currency'), tCommon = useT('common');
  const [form] = Form.useForm<{ logoFileAddress: string | null; reason: string }>(); const editor = useConfigurationForm(onAuthorizationChanged, form);
  const address = Form.useWatch('logoFileAddress', form);
  async function save(values: { logoFileAddress: string | null; reason: string }) {
    const result = await editor.save(`${currencySpecPath}/${encodeURIComponent(spec.currency)}`, 'PUT',
      changeLogoBody(spec, values.logoFileAddress!, values.reason), value => decodeCurrencySpecMutation(value, spec.currency, null));
    if (result) onSaved();
  }
  return <Modal open title={t('logo.modalTitle', { currency: spec.currency })} okText={t('logo.confirm')} cancelText={tCommon('action.cancel')}
    confirmLoading={editor.saving} okButtonProps={{ disabled: editor.conflict || !address || address === spec.logoFileAddress }}
    cancelButtonProps={{ disabled: editor.saving }} closable={!editor.saving} maskClosable={!editor.saving} onCancel={onClose} onOk={() => form.submit()}>
    <Form form={form} layout="vertical" disabled={editor.saving || editor.conflict} onFinish={save} onValuesChange={() => editor.setDirty(true)}
      initialValues={{ logoFileAddress: null, reason: '' }}>
      <div className="currency-logo-compare"><div className="currency-logo-current"><CurrencyIcon spec={spec} large /><div className="muted">{t('logo.current')}</div></div>
        <ArrowRightOutlined /><div className="refdata-grow">
          <Form.Item name="logoFileAddress" rules={[{ required: true, message: t('logo.required') }, { pattern: fileAddressPattern, message: t('logo.addressPattern') }]}>
            <LogoPicker resources={resources} onAuthorizationChanged={onAuthorizationChanged} currentUrl={null} /></Form.Item></div></div>
      <Form.Item name="reason" label={t('editor.reason')} rules={[{ required: true, whitespace: true, message: t('editor.reasonRequired') }]}>
        <Input maxLength={500} /></Form.Item>
      <p className="muted"><InfoCircleOutlined /> {t('logo.notice')}</p>
      {editor.fieldErrors.some(error => error.field === 'logoFileAddress') && <Alert type="error" showIcon title={t('logo.rejected')} />}
      {editor.fieldErrors.some(error => frozenFields.includes(error.field)) && <Alert type="warning" showIcon title={t('logo.frozenChanged')} />}
      {editor.conflict && <Alert type="warning" showIcon title={t('editor.conflict')} />}
    </Form>
  </Modal>;
}
