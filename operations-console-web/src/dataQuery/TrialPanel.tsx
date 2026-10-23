import { useState } from 'react';
import { Alert, Button, Checkbox, Input, InputNumber, Select, Table } from 'antd';
import { DatePicker } from '../components/DatePicker';
import { dataQueryPaths } from './dataQueryCommon';
import { cellText, contentBody, decodeTrialResult, pagingModeLabel, trialFields, trialPaging, trialParams } from './templateModel';
import type { ResultCell, TemplateDraft, TrialField, TrialResult, TrialValue } from './templateModel';
import { useT } from '../i18n/react';
import { Note } from './DataQueryParts';
import { richText } from '../i18n/richText';
import { useDataQueryCommand } from './useDataQueryCommand';

/**
 * 试运行（原型 05）：按参数声明生成输入，草稿不保存也能试；返回结果摘要、实际 SQL 与绑定值、结果表。
 * 偏移分页可翻页与选择返回总数；游标分页用上一页的 nextCursor 取下一页。返回的列名交给 onResultColumns 补进列声明。
 */
export function TrialPanel({ draft, canRun, onAuthorizationChanged, onResultColumns }: {
  draft: TemplateDraft; canRun: boolean; onAuthorizationChanged: () => void; onResultColumns: (names: string[]) => void;
}) {
  const command = useDataQueryCommand(onAuthorizationChanged); const t = useT('data-query');
  const fields = trialFields(draft.parameters);
  const constraints = draft.constraints;
  const [values, setValues] = useState<Record<string, TrialValue>>({});
  const [page, setPage] = useState(1); const [size, setSize] = useState<number | null>(null); const [withTotal, setWithTotal] = useState(false);
  const [result, setResult] = useState<{ value: TrialResult; page: number; cursorUsed: boolean } | null>(null);
  const [localErrors, setLocalErrors] = useState<{ name: string; message: string }[]>([]);

  async function run(next: { page?: number; cursor?: string | null } = {}) {
    const params = trialParams(fields, values);
    setLocalErrors(params.errors);
    if (params.errors.length || !draft.dataSourceId) return;
    const targetPage = next.page ?? page;
    const paging = trialPaging(constraints, { page: targetPage, size: size ?? undefined, withTotal, cursor: next.cursor });
    const value = await command.run(dataQueryPaths.trials, 'POST', { ...contentBody(draft), params: params.params, ...paging }, decodeTrialResult, t('trial.failed'));
    if (value) { setResult({ value, page: targetPage, cursorUsed: !!next.cursor }); setPage(targetPage); onResultColumns(value.columns.map(column => column.name)); }
  }
  const belongs = (field: string, name: string) => field === `params.${name}` || field.startsWith(`params.${name}[`) || field.startsWith(`params.${name}.`);
  const errorsFor = (name: string) => [...localErrors.filter(error => error.name === name).map(error => error.message),
    ...command.fieldErrors.filter(error => belongs(error.field, name)).map(error => t('error.fieldError', { field: error.field.replace(/^params\./, ''), message: error.message }))];
  const otherErrors = command.fieldErrors.filter(error => !fields.some(field => belongs(error.field, field.name)));

  return <div className="dq-trial">
    <div className="dq-trial-form">
      <div className="dq-trial-hint muted">{t('trial.hint')}</div>
      {fields.length === 0 && <span className="muted">{t('trial.noParameters')}</span>}
      {fields.map(field => <div key={field.name} className="dq-trial-field">
        <span className="mono">{field.name}{field.required ? ' *' : ''}<small className="muted"> {field.label}</small></span>
        <TrialInput field={field} value={values[field.name]} onChange={value => setValues(current => ({ ...current, [field.name]: value }))} />
        {errorsFor(field.name).map(message => <div key={message} className="dq-text-error">{message}</div>)}
      </div>)}
      <div className="dq-trial-paging">
        <span className="muted">{pagingModeLabel(constraints.pagingMode)}</span>
        {constraints.pagingMode === 'OFFSET' && <span>{richText(t, 'trial.pageInput', {
          input: <InputNumber size="small" min={1} precision={0} value={page} onChange={value => setPage(value ?? 1)} /> })}</span>}
        {constraints.pagingMode !== 'NONE' && <><span>{t('trial.pageSize')}</span><InputNumber size="small" min={1} max={constraints.maxPageSize ?? undefined} precision={0}
          placeholder={String(constraints.maxPageSize ?? '')} value={size} onChange={value => setSize(value)} /></>}
        {constraints.pagingMode === 'OFFSET' && <Checkbox checked={withTotal} disabled={constraints.countAllowed !== true}
          onChange={event => setWithTotal(event.target.checked)}>{t('trial.withTotal')}</Checkbox>}
        <Button type="primary" onClick={() => void run({ page: constraints.pagingMode === 'OFFSET' ? page : 1 })} loading={command.saving}
          disabled={!canRun || !draft.dataSourceId}>{t('template.trial')}</Button>
      </div>
      {!draft.dataSourceId && <Note tone="warning">{t('trial.selectDataSource')}</Note>}
      {otherErrors.length > 0 && <Alert type="error" showIcon className="dq-banner"
        title={otherErrors.map(error => t('error.fieldError', { field: error.field, message: error.message })).join(t('error.fieldErrorSeparator'))} />}
      {command.formError && !command.fieldErrors.length && <Alert type="error" showIcon className="dq-banner" title={command.formError} />}
    </div>
    {result && <TrialOutput result={result.value} page={result.page} constraints={constraints} loading={command.saving}
      onPage={target => void run({ page: target })} onNext={cursor => void run({ cursor })} />}
  </div>;
}

function TrialInput({ field, value, onChange }: { field: TrialField; value: TrialValue; onChange: (value: TrialValue) => void }) {
  const text = typeof value === 'string' ? value : undefined;
  switch (field.control) {
    case 'select': return <Select size="small" allowClear placeholder={field.placeholder} value={text} options={field.options.map(option => ({ value: option, label: option }))}
      onChange={next => onChange(next)} />;
    case 'tags': return <Select size="small" mode="tags" allowClear placeholder={field.placeholder} value={Array.isArray(value) ? value : []} tokenSeparators={[',']}
      options={field.options.map(option => ({ value: option, label: option }))} onChange={next => onChange(next)} />;
    case 'boolean': return <Select size="small" allowClear placeholder={field.placeholder} value={typeof value === 'boolean' ? String(value) : undefined}
      options={[{ value: 'true', label: 'true' }, { value: 'false', label: 'false' }]} onChange={next => onChange(next === undefined ? undefined : next === 'true')} />;
    case 'date': return <DatePicker size="small" placeholder={field.placeholder} value={text} onChange={next => onChange(next)} />;
    case 'datetime': return <DatePicker size="small" showTime placeholder={field.placeholder} value={text} onChange={next => onChange(next)} />;
    case 'json': return <Input.TextArea rows={2} className="mono" placeholder={field.placeholder} value={text} onChange={event => onChange(event.target.value)} />;
    default: return <Input size="small" allowClear placeholder={field.placeholder} value={text} inputMode={field.control === 'text' ? undefined : 'decimal'}
      onChange={event => onChange(event.target.value)} />;
  }
}

function TrialOutput({ result, page, constraints, loading, onPage, onNext }: {
  result: TrialResult; page: number; constraints: TemplateDraft['constraints']; loading: boolean;
  onPage: (page: number) => void; onNext: (cursor: string) => void;
}) {
  const t = useT('data-query');
  const limit = constraints.pagingMode === 'NONE' ? t('trial.limitRows', { count: constraints.maxRows }) : t('trial.limitPageSize', { size: constraints.maxPageSize ?? '—' });
  return <div className="dq-trial-output">
    <div className="dq-trial-summary">
      <span className="dq-ok">{t('trial.succeeded')}</span>
      <span>{result.hasMore ? t('trial.rowsMore', { count: result.rows.length }) : t('trial.rows', { count: result.rows.length })}</span>
      {result.total !== null && <span>{t('trial.total', { total: result.total })}</span>}
      <span>{t('trial.elapsed', { millis: result.elapsedMillis })}</span>
      <span className="muted">{t('trial.limits', { limit, seconds: constraints.timeoutSeconds })}</span>
    </div>
    <pre className="dq-sql">{result.sql}</pre>
    {result.countSql && <pre className="dq-sql is-count">{result.countSql}</pre>}
    <div className="dq-bindings">{t('trial.bindings')}{result.bindings.length ? result.bindings.map((binding, index) => <span key={index}>[{index + 1}] {binding}</span>) : t('diff.none')}</div>
    <Table<ResultCell[]> size="small" loading={loading} rowKey={(_, index) => String(index)} pagination={false} scroll={{ x: 'max-content', y: 360 }}
      dataSource={result.rows} locale={{ emptyText: t('trial.noRows') }}
      columns={result.columns.map((column, index) => ({ key: column.name, title: column.label ?? column.name, ellipsis: true,
        render: (_: unknown, row: ResultCell[]) => <span className={row[index] === null ? 'muted' : 'mono'}>{cellText(row[index])}</span> }))} />
    <div className="dq-trial-foot">
      <span className="muted">{t('trial.valuesNote')}</span>
      {constraints.pagingMode === 'OFFSET' && <span>
        <Button size="small" disabled={page <= 1 || loading} onClick={() => onPage(page - 1)}>{t('trial.previous')}</Button>
        <span className="dq-page-no">{t('trial.pageNo', { page })}</span>
        <Button size="small" disabled={!result.hasMore || loading} onClick={() => onPage(page + 1)}>{t('trial.next')}</Button></span>}
      {constraints.pagingMode === 'CURSOR' && <Button size="small" disabled={!result.nextCursor || loading} onClick={() => onNext(result.nextCursor!)}>{t('trial.next')}</Button>}
    </div>
  </div>;
}
