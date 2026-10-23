import { useEffect, useMemo } from 'react';
import type { ReactNode } from 'react';
import { Alert, Form, Input, InputNumber, Modal, Select } from 'antd';
import type { FormInstance } from 'antd';
import { DatePicker } from '../components/DatePicker';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import {
  accountBody, activeOnly, bookBody, calendarBody, decimalIdPattern, decodeCreated, decodeOpenedPeriod, entityBody,
  entityLabel, entityNoPattern, financePaths, ledgerEntityTypes, ownerIdError, ownerTypes, periodBody, periodRangeError,
  timezoneOptions,
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

/** 主体类型下拉：「显示名 代码」。 */
function useEntityTypeOptions() {
  const t = useT('finance');
  return useMemo(() => ledgerEntityTypes.map(value => ({ value, label: `${enumLabel(t, 'enum.ledgerEntityType', value)} ${value}` })), [t]);
}
const required = (message: string) => ({ required: true, whitespace: true, message });

/** 弹窗外壳：保存中禁止关闭；409 横幅与表单顶部错误统一放在表单上方。 */
export function CommandModal({ title, command, okText, onOk, onClose, onDone, children, width = 560 }: {
  title: ReactNode; command: Command; okText?: string; onOk: () => void; onClose: () => void; onDone: () => void; children: ReactNode; width?: number;
}) {
  const t = useT('finance');
  return <Modal open title={title} okText={okText ?? t('common.save')} cancelText={t('common.cancel')} confirmLoading={command.saving} okButtonProps={{ disabled: command.conflict }}
    onOk={onOk} onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }} destroyOnHidden width={width}>
    {command.conflict && <ConflictBanner onReload={onDone} />}
    {command.formError && <Alert type="error" showIcon className="finance-banner" title={command.formError} />}
    {children}
  </Modal>;
}

/** 主体标识：经营主体手工输入 entityNo；财务主体从启用的财务主体中选择。 */
function EntityIdField({ entityType }: { entityType: LedgerEntityType | undefined }) {
  const t = useT('finance');
  const entities = useFinanceEntities(entityType === 'FINANCE_ENTITY');
  if (entityType === 'FINANCE_ENTITY') {
    return <Form.Item name="entityId" label={t('modals.entityIdFinanceEntity')} rules={[{ required: true, message: t('modals.financeEntityRequired') }]}
      extra={entities.error ? t('modals.financeEntitiesLoadFailed') : undefined}>
      <Select showSearch={{ optionFilterProp: 'label' }} loading={entities.loading} placeholder={t('modals.financeEntityPlaceholder')}
        options={activeOnly(entities.items).map(item => ({ value: item.financeEntityId, label: `${item.entityCode} · ${item.displayName}` }))} />
    </Form.Item>;
  }
  return <Form.Item name="entityId" label={t('modals.entityIdEntityNo')} extra={t('modals.entityNoHint')}
    normalize={(value: string) => value?.toUpperCase()}
    rules={[required(t('modals.entityNoRequired')), { pattern: entityNoPattern, message: t('validation.entityNoPattern') }]}>
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
  const t = useT('finance');
  const [form] = Form.useForm<EntityFormValues>();
  const command = useFinanceCommand(onAuthorizationChanged, form, ['entityCode', 'legalPartyId', 'displayName']);
  async function submit() {
    const values = await form.validateFields();
    const result = await command.run(financePaths.financeEntities, entityBody(values), decodeCreated('financeEntityId'), t('modals.createEntityFailed'));
    if (result) onDone();
  }
  return <CommandModal title={t('modals.createEntityTitle')} command={command} onOk={() => void submit()} onClose={onClose} onDone={onDone}>
    <Form form={form} name="finance-create-entity" layout="vertical" disabled={command.saving || command.conflict}>
      <Form.Item name="entityCode" label={t('charts.column.code')} rules={[required(t('modals.codeRequired'))]}><Input maxLength={64} placeholder="PLT-AE" /></Form.Item>
      <Form.Item name="legalPartyId" label={t('entities.column.legalPartyId')} extra={t('modals.legalPartyIdHint')}
        rules={[required(t('modals.legalPartyIdRequired')), { pattern: decimalIdPattern, message: t('validation.decimalId') }]}>
        <Input maxLength={19} /></Form.Item>
      <Form.Item name="displayName" label={t('entities.column.displayName')} rules={[required(t('modals.displayNameRequired'))]}><Input maxLength={256} /></Form.Item>
    </Form>
    <FinanceNote>{t('modals.createEntityNote')}</FinanceNote>
  </CommandModal>;
}

export function CreateCalendarModal({ onClose, onDone, onAuthorizationChanged }: CommandProps) {
  const t = useT('finance');
  const entityTypeOptions = useEntityTypeOptions();
  const [form] = Form.useForm<CalendarFormValues>();
  const command = useFinanceCommand(onAuthorizationChanged, form, ['entityType', 'entityId', 'calendarCode', 'timezone', 'yearStartMonth']);
  const entityType = useEntityType(form);
  const zones = useMemo(() => timezoneOptions().map(value => ({ value, label: value })), []);
  async function submit() {
    const values = await form.validateFields();
    const result = await command.run(financePaths.fiscalCalendars, calendarBody(values), decodeCreated('fiscalCalendarId'), t('modals.createCalendarFailed'));
    if (result) onDone();
  }
  return <CommandModal title={t('modals.createCalendarTitle')} command={command} onOk={() => void submit()} onClose={onClose} onDone={onDone}>
    <Form form={form} name="finance-create-calendar" layout="vertical" disabled={command.saving || command.conflict} initialValues={{ entityType: 'OPERATING_ENTITY', yearStartMonth: 1 }}>
      <div className="finance-form-grid">
        <Form.Item name="entityType" label={t('field.entityType')} rules={[{ required: true, message: t('validation.entityTypeRequired') }]}><Select options={entityTypeOptions} /></Form.Item>
        <EntityIdField entityType={entityType} />
        <Form.Item name="calendarCode" label={t('calendars.column.calendarCode')} rules={[required(t('modals.calendarCodeRequired'))]}><Input maxLength={64} placeholder="AE-STD" /></Form.Item>
        <Form.Item name="timezone" label={t('calendars.column.timezone')} rules={[{ required: true, message: t('modals.timezoneRequired') }]}>
          <Select showSearch={{ optionFilterProp: 'label' }} placeholder={t('modals.timezonePlaceholder')} options={zones} /></Form.Item>
        <Form.Item name="yearStartMonth" label={t('calendars.column.yearStartMonth')} rules={[{ required: true, message: t('modals.yearStartMonthRequired') }]}>
          <InputNumber min={1} max={12} precision={0} className="finance-number" /></Form.Item>
      </div>
    </Form>
    <FinanceNote>{t('modals.createCalendarNote')}</FinanceNote>
  </CommandModal>;
}

export function CreatePeriodModal({ calendar, calendarLabel, onClose, onDone, onAuthorizationChanged }: CommandProps & { calendar: FiscalCalendar; calendarLabel: string }) {
  const t = useT('finance');
  const [form] = Form.useForm<PeriodFormValues>();
  const command = useFinanceCommand(onAuthorizationChanged, form, ['fiscalYear', 'periodNo', 'startDate', 'endDate']);
  async function submit() {
    const values = await form.validateFields();
    const result = await command.run(financePaths.periods(calendar.fiscalCalendarId), periodBody(values),
      decodeCreated('accountingPeriodId'), t('modals.createPeriodFailed'));
    if (result) onDone();
  }
  return <CommandModal title={t('modals.createPeriodTitle', { calendar: calendarLabel })} command={command} onOk={() => void submit()} onClose={onClose} onDone={onDone}>
    <Form form={form} name="finance-create-period" layout="vertical" disabled={command.saving || command.conflict} initialValues={{ fiscalYear: new Date().getFullYear() }}>
      <div className="finance-form-grid">
        <Form.Item name="fiscalYear" label={t('calendars.column.fiscalYear')} rules={[{ required: true, message: t('modals.fiscalYearRequired') }]}>
          <InputNumber min={1900} max={9999} precision={0} className="finance-number" /></Form.Item>
        <Form.Item name="periodNo" label={t('calendars.column.periodNo')} rules={[{ required: true, message: t('modals.periodNoRequired') }]}>
          <InputNumber min={1} max={999} precision={0} className="finance-number" /></Form.Item>
        <Form.Item name="startDate" label={t('field.startDate')} rules={[{ required: true, message: t('modals.startDateRequired') }]}><DatePicker /></Form.Item>
        <Form.Item name="endDate" label={t('field.endDate')} dependencies={['startDate']} rules={[{ required: true, message: t('modals.endDateRequired') },
          ({ getFieldValue }) => ({ validator: async (_, value) => {
            const message = periodRangeError(getFieldValue('startDate'), value);
            if (message) throw new Error(message);
          } })]}><DatePicker /></Form.Item>
      </div>
    </Form>
    <FinanceNote>{t('modals.createPeriodNote')}</FinanceNote>
  </CommandModal>;
}

/** 开放期间二次确认：FUTURE → OPEN 不可逆。 */
export function OpenPeriodModal({ period, calendarLabel, onClose, onDone, onAuthorizationChanged }: CommandProps & { period: AccountingPeriod; calendarLabel: string }) {
  const t = useT('finance');
  const command = useFinanceCommand(onAuthorizationChanged);
  async function submit() {
    const result = await command.run(financePaths.openPeriod(period.accountingPeriodId), undefined,
      value => decodeOpenedPeriod(value, period.accountingPeriodId), t('modals.openPeriodFailed'));
    if (result) onDone();
  }
  return <CommandModal title={t('modals.openPeriodTitle', { fiscalYear: String(period.fiscalYear), periodNo: String(period.periodNo) })} command={command} okText={t('modals.confirmOpen')}
    onOk={() => void submit()} onClose={onClose} onDone={onDone} width={520}>
    <p>{t('modals.openPeriodNotice', { startDate: period.startDate, endDate: period.endDate })}</p>
    <dl className="finance-detail-box">
      <dt>{t('modals.calendar')}</dt><dd>{calendarLabel}</dd>
      <dt>{t('modals.period')}</dt><dd>{t('modals.periodValue', { fiscalYear: String(period.fiscalYear), periodNo: String(period.periodNo), startDate: period.startDate, endDate: period.endDate })}</dd>
      <dt>{t('modals.currentStatus')}</dt><dd>{period.status}</dd>
    </dl>
  </CommandModal>;
}

export function CreateBookModal({ onClose, onDone, onAuthorizationChanged }: CommandProps) {
  const t = useT('finance');
  const entityTypeOptions = useEntityTypeOptions();
  const [form] = Form.useForm<BookFormValues>();
  const command = useFinanceCommand(onAuthorizationChanged, form, ['entityType', 'entityId', 'currency', 'chartCode']);
  const entityType = useEntityType(form);
  const currencies = useCurrencies(onAuthorizationChanged); const charts = useCharts(onAuthorizationChanged);
  async function submit() {
    const values = await form.validateFields();
    const result = await command.run(financePaths.ledgerBooks, bookBody(values), decodeCreated('ledgerBookId'), t('modals.createBookFailed'));
    if (result) onDone();
  }
  return <CommandModal title={t('modals.createBookTitle')} command={command} onOk={() => void submit()} onClose={onClose} onDone={onDone}>
    <Form form={form} name="finance-create-book" layout="vertical" disabled={command.saving || command.conflict} initialValues={{ entityType: 'OPERATING_ENTITY' }}>
      <div className="finance-form-grid">
        <Form.Item name="entityType" label={t('field.entityType')} rules={[{ required: true, message: t('validation.entityTypeRequired') }]}><Select options={entityTypeOptions} /></Form.Item>
        <EntityIdField entityType={entityType} />
        <Form.Item name="currency" label={t('field.currency')} rules={[{ required: true, message: t('validation.currencyRequired') }]}>
          <Select loading={currencies.loading} placeholder={t('modals.activeCurrenciesOnly')}
            options={activeOnly(currencies.items).map(item => ({ value: item.currency, label: t('modals.currencyOption', { currency: item.currency, minorUnit: item.minorUnit }) }))} /></Form.Item>
        <Form.Item name="chartCode" label={t('field.chart')} rules={[{ required: true, message: t('modals.chartRequired') }]}>
          <Select loading={charts.loading} placeholder={t('modals.activeChartsOnly')}
            options={activeOnly(charts.items).map(item => ({ value: item.chartCode, label: `${item.chartCode} · ${item.chartName}` }))} /></Form.Item>
      </div>
    </Form>
    <FinanceNote>{t('modals.createBookNote')}</FinanceNote>
  </CommandModal>;
}

export function CreateAccountModal({ book, entities, onClose, onDone, onAuthorizationChanged }: CommandProps & {
  book: LedgerBook; entities?: ReadonlyMap<string, FinanceEntity>;
}) {
  const t = useT('finance');
  const [form] = Form.useForm<AccountFormValues>();
  const command = useFinanceCommand(onAuthorizationChanged, form, ['accountCode', 'ownerType', 'ownerId', 'balanceBucket']);
  const chartAccounts = useChartAccounts(book.chartCode, { postingAllowed: true, accountStatus: 'ACTIVE' }, onAuthorizationChanged);
  async function submit() {
    const values = await form.validateFields();
    const result = await command.run(financePaths.accounts(book.ledgerBookId), accountBody(values), decodeCreated('accountId'), t('modals.createAccountFailed'));
    if (result) onDone();
  }
  return <CommandModal title={t('modals.createAccountTitle', { book: `${entityLabel(book.entityType, book.entityId, entities)} / ${book.currency}` })} command={command}
    onOk={() => void submit()} onClose={onClose} onDone={onDone}>
    <Form form={form} name="finance-create-account" layout="vertical" disabled={command.saving || command.conflict} initialValues={{ balanceBucket: 'NONE' }}>
      <Form.Item name="accountCode" label={t('field.accountCode')} extra={chartAccounts.error ? t('modals.chartAccountsLoadFailed') : t('modals.chartAccountsHint', { chartCode: book.chartCode })}
        rules={[{ required: true, message: t('modals.accountCodeRequired') }]}>
        <Select showSearch={{ optionFilterProp: 'label' }} loading={chartAccounts.loading} placeholder={t('modals.accountCodePlaceholder')}
          options={chartAccounts.items.map(item => ({ value: item.accountCode,
            label: `${item.accountNo} ${t('common.labelWithId', { label: item.accountName, id: enumLabel(t, 'enum.side', item.normalSide) })}` }))} /></Form.Item>
      <div className="finance-form-grid">
        <Form.Item name="ownerType" label={t('field.accountingDimensionType')} rules={[{ required: true, message: t('modals.ownerTypeRequired') }]}>
          <Select options={ownerTypes.map(value => ({ value, label: value }))} /></Form.Item>
        <Form.Item name="ownerId" label={t('field.accountingDimensionId')} dependencies={['ownerType']} rules={[({ getFieldValue }) => ({ validator: async (_, value) => {
          const message = ownerIdError(getFieldValue('ownerType'), value);
          if (message) throw new Error(message);
        } })]}><Input maxLength={64} /></Form.Item>
      </div>
      {/* 余额桶固定 NONE、不可编辑；保留字段用于承接 ACCOUNT_ALREADY_EXISTS 的字段错误（画板 05）。 */}
      <Form.Item name="balanceBucket" label={t('field.balanceBucket')}><Select disabled options={[{ value: 'NONE', label: 'NONE' }]} /></Form.Item>
    </Form>
    <FinanceNote>{t('modals.createAccountNote')}</FinanceNote>
  </CommandModal>;
}
