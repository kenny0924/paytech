import { useEffect, useMemo } from 'react';
import type { ReactNode } from 'react';
import { Alert, Form, Input, InputNumber, Modal, Select } from 'antd';
import type { FormInstance } from 'antd';
import { DatePicker } from '../components/DatePicker';
import {
  accountBody, activeOnly, bookBody, calendarBody, decimalIdPattern, decodeCreated, decodeOpenedPeriod, entityBody,
  entityLabel, entityNoPattern, entityTypeLabels, financePaths, ledgerEntityTypes, ownerIdError, ownerTypes, periodBody, periodRangeError,
  sideLabels, timezoneOptions,
} from './finance';
import type {
  AccountFormValues, AccountingPeriod, BookFormValues, CalendarFormValues, EntityFormValues, FinanceEntity, FiscalCalendar,
  LedgerBook, LedgerEntityType, PeriodFormValues,
} from './finance';
import { ConflictBanner, FinanceNote } from './FinanceParts';
import { useFinanceCommand } from './useFinanceCommand';
import { useChartAccounts, useCharts, useCurrencies, useFinanceEntities } from './useFinanceData';

interface CommandProps { onClose: () => void; onDone: () => void; onAuthorizationChanged: () => void }
type Command = ReturnType<typeof useFinanceCommand>;

const entityTypeOptions = ledgerEntityTypes.map(value => ({ value, label: `${entityTypeLabels[value]} ${value}` }));
const required = (message: string) => ({ required: true, whitespace: true, message });

/** 弹窗外壳：保存中禁止关闭；409 横幅与表单顶部错误统一放在表单上方。 */
function CommandModal({ title, command, okText = '保存', onOk, onClose, onDone, children, width = 560 }: {
  title: ReactNode; command: Command; okText?: string; onOk: () => void; onClose: () => void; onDone: () => void; children: ReactNode; width?: number;
}) {
  return <Modal open title={title} okText={okText} cancelText="取消" confirmLoading={command.saving} okButtonProps={{ disabled: command.conflict }}
    onOk={onOk} onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }} destroyOnHidden width={width}>
    {command.conflict && <ConflictBanner onReload={onDone} />}
    {command.formError && <Alert type="error" showIcon className="finance-banner" title={command.formError} />}
    {children}
  </Modal>;
}

/** 主体标识：经营主体手工输入 entityNo；财务主体从启用的财务主体中选择。 */
function EntityIdField({ entityType, label = '主体标识' }: { entityType: LedgerEntityType | undefined; label?: string }) {
  const entities = useFinanceEntities(entityType === 'FINANCE_ENTITY');
  if (entityType === 'FINANCE_ENTITY') {
    return <Form.Item name="entityId" label={`${label}（财务主体）`} rules={[{ required: true, message: '请选择财务主体' }]}
      extra={entities.error ? '财务主体加载失败，请关闭后重试' : undefined}>
      <Select showSearch={{ optionFilterProp: 'label' }} loading={entities.loading} placeholder="选择启用的财务主体"
        options={activeOnly(entities.items).map(item => ({ value: item.financeEntityId, label: `${item.entityCode} · ${item.displayName}` }))} />
    </Form.Item>;
  }
  return <Form.Item name="entityId" label={`${label}（entityNo）`} extra="两位国家码 + 三位序号，本期不向 CCT 核对"
    normalize={(value: string) => value?.toUpperCase()}
    rules={[required('请填写 entityNo'), { pattern: entityNoPattern, message: '必须是两位大写国家码加三位序号，如 AE001' }]}>
    <Input maxLength={5} placeholder="AE001" />
  </Form.Item>;
}

/** 主体类型切换时清空主体标识，避免把 entityNo 当成财务主体主键提交。 */
function useEntityType(form: FormInstance) {
  const entityType = Form.useWatch('entityType', form) as LedgerEntityType | undefined;
  useEffect(() => { if (form.isFieldsTouched(['entityType'])) form.setFieldValue('entityId', undefined); }, [entityType, form]);
  return entityType;
}

export function CreateEntityModal({ onClose, onDone, onAuthorizationChanged }: CommandProps) {
  const [form] = Form.useForm<EntityFormValues>();
  const command = useFinanceCommand(onAuthorizationChanged, form, ['entityCode', 'legalPartyId', 'displayName']);
  async function submit() {
    const values = await form.validateFields();
    const result = await command.run(financePaths.financeEntities, entityBody(values), decodeCreated('financeEntityId'), '新建财务主体失败');
    if (result) onDone();
  }
  return <CommandModal title="新建财务主体" command={command} onOk={() => void submit()} onClose={onClose} onDone={onDone}>
    <Form form={form} name="finance-create-entity" layout="vertical" disabled={command.saving || command.conflict}>
      <Form.Item name="entityCode" label="代码" rules={[required('请填写代码')]}><Input maxLength={64} placeholder="PLT-AE" /></Form.Item>
      <Form.Item name="legalPartyId" label="法律 Party ID" extra="CCT Party 主键，本期手工输入"
        rules={[required('请填写法律 Party ID'), { pattern: decimalIdPattern, message: '必须是正十进制整数字符串' }]}>
        <Input maxLength={19} /></Form.Item>
      <Form.Item name="displayName" label="显示名称" rules={[required('请填写显示名称')]}><Input maxLength={256} /></Form.Item>
    </Form>
    <FinanceNote>财务主体创建后不能修改或关闭；一个法律 Party 只能映射一个财务主体。</FinanceNote>
  </CommandModal>;
}

export function CreateCalendarModal({ onClose, onDone, onAuthorizationChanged }: CommandProps) {
  const [form] = Form.useForm<CalendarFormValues>();
  const command = useFinanceCommand(onAuthorizationChanged, form, ['entityType', 'entityId', 'calendarCode', 'timezone', 'yearStartMonth']);
  const entityType = useEntityType(form);
  const zones = useMemo(() => timezoneOptions().map(value => ({ value, label: value })), []);
  async function submit() {
    const values = await form.validateFields();
    const result = await command.run(financePaths.fiscalCalendars, calendarBody(values), decodeCreated('fiscalCalendarId'), '新建日历失败');
    if (result) onDone();
  }
  return <CommandModal title="新建财务日历" command={command} onOk={() => void submit()} onClose={onClose} onDone={onDone}>
    <Form form={form} name="finance-create-calendar" layout="vertical" disabled={command.saving || command.conflict} initialValues={{ entityType: 'OPERATING_ENTITY', yearStartMonth: 1 }}>
      <div className="finance-form-grid">
        <Form.Item name="entityType" label="主体类型" rules={[{ required: true, message: '请选择主体类型' }]}><Select options={entityTypeOptions} /></Form.Item>
        <EntityIdField entityType={entityType} />
        <Form.Item name="calendarCode" label="日历代码" rules={[required('请填写日历代码')]}><Input maxLength={64} placeholder="AE-STD" /></Form.Item>
        <Form.Item name="timezone" label="时区" rules={[{ required: true, message: '请选择时区' }]}>
          <Select showSearch={{ optionFilterProp: 'label' }} placeholder="IANA 时区，如 Asia/Dubai" options={zones} /></Form.Item>
        <Form.Item name="yearStartMonth" label="财年起始月" rules={[{ required: true, message: '请填写 1—12' }]}>
          <InputNumber min={1} max={12} precision={0} className="finance-number" /></Form.Item>
      </div>
    </Form>
    <FinanceNote>每个主体只有一套日历，创建后不能修改。</FinanceNote>
  </CommandModal>;
}

export function CreatePeriodModal({ calendar, calendarLabel, onClose, onDone, onAuthorizationChanged }: CommandProps & { calendar: FiscalCalendar; calendarLabel: string }) {
  const [form] = Form.useForm<PeriodFormValues>();
  const command = useFinanceCommand(onAuthorizationChanged, form, ['fiscalYear', 'periodNo', 'startDate', 'endDate']);
  async function submit() {
    const values = await form.validateFields();
    const result = await command.run(financePaths.periods(calendar.fiscalCalendarId), periodBody(values),
      decodeCreated('accountingPeriodId'), '新建期间失败');
    if (result) onDone();
  }
  return <CommandModal title={`新建期间 · ${calendarLabel}`} command={command} onOk={() => void submit()} onClose={onClose} onDone={onDone}>
    <Form form={form} name="finance-create-period" layout="vertical" disabled={command.saving || command.conflict} initialValues={{ fiscalYear: new Date().getFullYear() }}>
      <div className="finance-form-grid">
        <Form.Item name="fiscalYear" label="财年" rules={[{ required: true, message: '请填写财年' }]}>
          <InputNumber min={1900} max={9999} precision={0} className="finance-number" /></Form.Item>
        <Form.Item name="periodNo" label="期号" rules={[{ required: true, message: '请填写期号' }]}>
          <InputNumber min={1} max={999} precision={0} className="finance-number" /></Form.Item>
        <Form.Item name="startDate" label="开始日期" rules={[{ required: true, message: '请选择开始日期' }]}><DatePicker /></Form.Item>
        <Form.Item name="endDate" label="结束日期" dependencies={['startDate']} rules={[{ required: true, message: '请选择结束日期' },
          ({ getFieldValue }) => ({ validator: async (_, value) => {
            const message = periodRangeError(getFieldValue('startDate'), value);
            if (message) throw new Error(message);
          } })]}><DatePicker /></Form.Item>
      </div>
    </Form>
    <FinanceNote>新期间状态为 FUTURE；日期不得与同日历其他期间重叠，同一财年期号唯一。</FinanceNote>
  </CommandModal>;
}

/** 开放期间二次确认：FUTURE → OPEN 不可逆。 */
export function OpenPeriodModal({ period, calendarLabel, onClose, onDone, onAuthorizationChanged }: CommandProps & { period: AccountingPeriod; calendarLabel: string }) {
  const command = useFinanceCommand(onAuthorizationChanged);
  async function submit() {
    const result = await command.run(financePaths.openPeriod(period.accountingPeriodId), undefined,
      value => decodeOpenedPeriod(value, period.accountingPeriodId), '开放期间失败');
    if (result) onDone();
  }
  return <CommandModal title={`开放会计期间 ${period.fiscalYear} 年第 ${period.periodNo} 期？`} command={command} okText="确认开放"
    onOk={() => void submit()} onClose={onClose} onDone={onDone} width={520}>
    <p>开放后 {period.startDate} 至 {period.endDate} 的分录可以过账，期间不能回到 FUTURE；关账在期间关账工作台完成。</p>
    <dl className="finance-detail-box">
      <dt>日历</dt><dd>{calendarLabel}</dd>
      <dt>期间</dt><dd>{period.fiscalYear} · 第 {period.periodNo} 期 · {period.startDate} — {period.endDate}</dd>
      <dt>当前状态</dt><dd>{period.status}</dd>
    </dl>
  </CommandModal>;
}

export function CreateBookModal({ onClose, onDone, onAuthorizationChanged }: CommandProps) {
  const [form] = Form.useForm<BookFormValues>();
  const command = useFinanceCommand(onAuthorizationChanged, form, ['entityType', 'entityId', 'currency', 'chartCode']);
  const entityType = useEntityType(form);
  const currencies = useCurrencies(onAuthorizationChanged); const charts = useCharts(onAuthorizationChanged);
  async function submit() {
    const values = await form.validateFields();
    const result = await command.run(financePaths.ledgerBooks, bookBody(values), decodeCreated('ledgerBookId'), '新建账簿失败');
    if (result) onDone();
  }
  return <CommandModal title="新建账簿" command={command} onOk={() => void submit()} onClose={onClose} onDone={onDone}>
    <Form form={form} name="finance-create-book" layout="vertical" disabled={command.saving || command.conflict} initialValues={{ entityType: 'OPERATING_ENTITY' }}>
      <div className="finance-form-grid">
        <Form.Item name="entityType" label="主体类型" rules={[{ required: true, message: '请选择主体类型' }]}><Select options={entityTypeOptions} /></Form.Item>
        <EntityIdField entityType={entityType} />
        <Form.Item name="currency" label="币种" rules={[{ required: true, message: '请选择币种' }]}>
          <Select loading={currencies.loading} placeholder="仅启用币种"
            options={activeOnly(currencies.items).map(item => ({ value: item.currency, label: `${item.currency} · ${item.minorUnit} 位小数` }))} /></Form.Item>
        <Form.Item name="chartCode" label="科目表" rules={[{ required: true, message: '请选择科目表' }]}>
          <Select loading={charts.loading} placeholder="仅启用科目表"
            options={activeOnly(charts.items).map(item => ({ value: item.chartCode, label: `${item.chartCode} · ${item.chartName}` }))} /></Form.Item>
      </div>
    </Form>
    <FinanceNote>同主体同币种只能有一本账簿；创建后状态为 OPEN 并初始化分录序号。币种与科目表来自 Finance 已启用项。</FinanceNote>
  </CommandModal>;
}

export function CreateAccountModal({ book, entities, onClose, onDone, onAuthorizationChanged }: CommandProps & {
  book: LedgerBook; entities?: ReadonlyMap<string, FinanceEntity>;
}) {
  const [form] = Form.useForm<AccountFormValues>();
  const command = useFinanceCommand(onAuthorizationChanged, form, ['accountCode', 'ownerType', 'ownerId', 'balanceBucket']);
  const chartAccounts = useChartAccounts(book.chartCode, true, onAuthorizationChanged);
  async function submit() {
    const values = await form.validateFields();
    const result = await command.run(financePaths.accounts(book.ledgerBookId), accountBody(values), decodeCreated('accountId'), '新建账户失败');
    if (result) onDone();
  }
  return <CommandModal title={`新建账户 · ${entityLabel(book.entityType, book.entityId, entities)} / ${book.currency}`} command={command}
    onOk={() => void submit()} onClose={onClose} onDone={onDone}>
    <Form form={form} name="finance-create-account" layout="vertical" disabled={command.saving || command.conflict} initialValues={{ balanceBucket: 'NONE' }}>
      <Form.Item name="accountCode" label="科目" extra={chartAccounts.error ? '科目加载失败，请关闭后重试' : `只列出 ${book.chartCode} 科目表中可过账的科目`}
        rules={[{ required: true, message: '请选择科目' }]}>
        <Select showSearch={{ optionFilterProp: 'label' }} loading={chartAccounts.loading} placeholder="选择可过账科目"
          options={chartAccounts.items.filter(item => item.postingAllowed).map(item => ({ value: item.accountCode,
            label: `${item.accountCode} · ${item.accountName}（${sideLabels[item.normalSide]}）` }))} /></Form.Item>
      <div className="finance-form-grid">
        <Form.Item name="ownerType" label="核算对象类型" rules={[{ required: true, message: '请选择核算对象类型' }]}>
          <Select options={ownerTypes.map(value => ({ value, label: value }))} /></Form.Item>
        <Form.Item name="ownerId" label="核算对象标识" dependencies={['ownerType']} rules={[({ getFieldValue }) => ({ validator: async (_, value) => {
          const message = ownerIdError(getFieldValue('ownerType'), value);
          if (message) throw new Error(message);
        } })]}><Input maxLength={64} /></Form.Item>
      </div>
      {/* 余额桶固定 NONE、不可编辑；保留字段用于承接 ACCOUNT_ALREADY_EXISTS 的字段错误（画板 05）。 */}
      <Form.Item name="balanceBucket" label="余额桶"><Select disabled options={[{ value: 'NONE', label: 'NONE' }]} /></Form.Item>
    </Form>
    <FinanceNote>核算对象只能是经营主体、财务主体、渠道账户、银行账户或税务机关，余额桶固定 NONE；Client / Partner 余额在明细账，不在总账开户。核算对象为经营主体时填 entityNo。</FinanceNote>
  </CommandModal>;
}
