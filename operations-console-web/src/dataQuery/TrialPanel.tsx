import { useState } from 'react';
import { Alert, Button, Checkbox, Input, InputNumber, Select, Table } from 'antd';
import { DatePicker } from '../components/DatePicker';
import { dataQueryPaths } from './dataQueryCommon';
import { cellText, contentBody, decodeTrialResult, pagingModeLabels, trialFields, trialPaging, trialParams } from './templateModel';
import type { ResultCell, TemplateDraft, TrialField, TrialResult, TrialValue } from './templateModel';
import { Note } from './DataQueryParts';
import { useDataQueryCommand } from './useDataQueryCommand';

/**
 * 试运行（原型 05）：按参数声明生成输入，草稿不保存也能试；返回结果摘要、实际 SQL 与绑定值、结果表。
 * 偏移分页可翻页与选择返回总数；游标分页用上一页的 nextCursor 取下一页。
 */
export function TrialPanel({ draft, canRun, onAuthorizationChanged }: { draft: TemplateDraft; canRun: boolean; onAuthorizationChanged: () => void }) {
  const command = useDataQueryCommand(onAuthorizationChanged);
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
    const value = await command.run(dataQueryPaths.trials, 'POST', { ...contentBody(draft), params: params.params, ...paging }, decodeTrialResult, '试运行失败');
    if (value) { setResult({ value, page: targetPage, cursorUsed: !!next.cursor }); setPage(targetPage); }
  }
  const belongs = (field: string, name: string) => field === `params.${name}` || field.startsWith(`params.${name}[`) || field.startsWith(`params.${name}.`);
  const errorsFor = (name: string) => [...localErrors.filter(error => error.name === name).map(error => error.message),
    ...command.fieldErrors.filter(error => belongs(error.field, name)).map(error => `${error.field.replace(/^params\./, '')}：${error.message}`)];
  const otherErrors = command.fieldErrors.filter(error => !fields.some(field => belongs(error.field, field.name)));

  return <div className="dq-trial">
    <div className="dq-trial-form">
      <div className="dq-trial-hint muted">按参数声明生成，不保存草稿也能试</div>
      {fields.length === 0 && <span className="muted">模板没有声明参数。</span>}
      {fields.map(field => <div key={field.name} className="dq-trial-field">
        <span className="mono">{field.name}{field.required ? ' *' : ''}<small className="muted"> {field.label}</small></span>
        <TrialInput field={field} value={values[field.name]} onChange={value => setValues(current => ({ ...current, [field.name]: value }))} />
        {errorsFor(field.name).map(message => <div key={message} className="dq-text-error">{message}</div>)}
      </div>)}
      <div className="dq-trial-paging">
        <span className="muted">{pagingModeLabels[constraints.pagingMode]}</span>
        {constraints.pagingMode === 'OFFSET' && <>第 <InputNumber size="small" min={1} precision={0} value={page} onChange={value => setPage(value ?? 1)} /> 页</>}
        {constraints.pagingMode !== 'NONE' && <>每页 <InputNumber size="small" min={1} max={constraints.maxPageSize ?? undefined} precision={0}
          placeholder={String(constraints.maxPageSize ?? '')} value={size} onChange={value => setSize(value)} /></>}
        {constraints.pagingMode === 'OFFSET' && <Checkbox checked={withTotal} disabled={constraints.countAllowed !== true}
          onChange={event => setWithTotal(event.target.checked)}>返回总数</Checkbox>}
        <Button type="primary" onClick={() => void run({ page: constraints.pagingMode === 'OFFSET' ? page : 1 })} loading={command.saving}
          disabled={!canRun || !draft.dataSourceId}>▶ 试运行</Button>
      </div>
      {!draft.dataSourceId && <Note tone="warning">请先在顶栏选择数据源。</Note>}
      {otherErrors.length > 0 && <Alert type="error" showIcon className="dq-banner"
        title={otherErrors.map(error => `${error.field}：${error.message}`).join('；')} />}
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
  const limit = constraints.pagingMode === 'NONE' ? `最多 ${constraints.maxRows} 行` : `每页 ≤ ${constraints.maxPageSize ?? '—'}`;
  return <div className="dq-trial-output">
    <div className="dq-trial-summary">
      <span className="dq-ok">成功</span>
      <span>{result.rows.length} 行{result.hasMore ? ' · 还有更多' : ''}</span>
      {result.total !== null && <span>总数 {result.total}</span>}
      <span>耗时 {result.elapsedMillis} ms</span>
      <span className="muted">上限：{limit} · 超时 {constraints.timeoutSeconds} s</span>
    </div>
    <pre className="dq-sql">{result.sql}</pre>
    {result.countSql && <pre className="dq-sql is-count">{result.countSql}</pre>}
    <div className="dq-bindings">绑定：{result.bindings.length ? result.bindings.map((binding, index) => <span key={index}>[{index + 1}] {binding}</span>) : '无'}</div>
    <Table<ResultCell[]> size="small" loading={loading} rowKey={(_, index) => String(index)} pagination={false} scroll={{ x: 'max-content', y: 360 }}
      dataSource={result.rows} locale={{ emptyText: '没有返回行' }}
      columns={result.columns.map((column, index) => ({ key: column.name, title: column.label ?? column.name, ellipsis: true,
        render: (_: unknown, row: ResultCell[]) => <span className={row[index] === null ? 'muted' : 'mono'}>{cellText(row[index])}</span> }))} />
    <div className="dq-trial-foot">
      <span className="muted">数值与 64 位 ID 以字符串返回；时间按库中原值显示</span>
      {constraints.pagingMode === 'OFFSET' && <span>
        <Button size="small" disabled={page <= 1 || loading} onClick={() => onPage(page - 1)}>‹ 上一页</Button>
        <span className="dq-page-no">第 {page} 页</span>
        <Button size="small" disabled={!result.hasMore || loading} onClick={() => onPage(page + 1)}>下一页 ›</Button></span>}
      {constraints.pagingMode === 'CURSOR' && <Button size="small" disabled={!result.nextCursor || loading} onClick={() => onNext(result.nextCursor!)}>下一页 ›</Button>}
    </div>
  </div>;
}
