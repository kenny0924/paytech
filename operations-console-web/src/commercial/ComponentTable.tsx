import { useState } from 'react';
import { Button, Form, Input, Select, Tag, Tooltip } from 'antd';
import type { FormInstance, FormRule as Rule, SelectProps } from 'antd';
import { bandBases, bandIssues, bandModes, calculationBases, componentTypes, joinList, refundBehaviors } from './commercial';
import type { BandDraft, ComponentDraft, ComponentType } from './commercial';
import { benchmarkDetail, benchmarkMark } from './benchmark';
import type { CodeOption } from './commercialConsole';
import type { ComponentBenchmark } from './termReview';
import {
  amountPattern, componentTypePatch, copyComponentDraft, isEditable, labeledOptions, newComponentDraft, percentIssue, tierSummary,
} from './componentTable.ts';
import { enumLabel } from '../i18n/index';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';
import './componentTable.css';

type NamePath = (string | number)[];

const amountRule = (t: Translate): Rule => ({ pattern: amountPattern, message: t('componentTable.amountPattern') });
const percentRule: Rule = { validator: async (_, value?: string | null) => { const issue = percentIssue(value); if (issue) throw new Error(issue); } };
const required = (message: string): Rule => ({ required: true, message });
const upper = (value: string) => value?.toUpperCase();

/** 下拉：已选显示名称，展开后名称 + 代码副文字。 */
const labeledSelect: Partial<SelectProps> = {
  size: 'small', popupMatchSelectWidth: false,
  optionRender: option => <span className="ct-option">{option.label}<span className="ct-option-code">{option.value}</span></span>,
};

/** 主行列定义（字段 → 列名 key）：错误汇总时作为字段名前缀。 */
const rowColumns: readonly [keyof ComponentDraft, string][] = [
  ['componentCode', 'componentTable.column.componentCode'], ['triggerCode', 'componentTable.column.event'], ['componentType', 'componentTable.column.type'],
  ['calculationBase', 'componentTable.column.calculationBase'], ['rate', 'componentTable.column.rate'], ['fixedAmount', 'componentTable.column.fixedAmount'],
  ['minAmount', 'componentTable.column.min'], ['maxAmount', 'componentTable.column.max'], ['refundBehavior', 'componentTable.column.refund'],
];
const bandColumns: readonly [keyof BandDraft, string][] = [['lowerBound', 'componentTable.band.lower'], ['upperBound', 'componentTable.band.upper'],
  ['rate', 'componentTable.column.rate'], ['fixedAmount', 'componentTable.column.fixedAmount']];

const startsWith = (name: readonly (string | number)[], prefix: NamePath) => prefix.every((part, index) => name[index] === part);

/**
 * 一条价格下的组件表格：每行一个组件，单元格直接编辑
 * <li>类型决定可编辑列：百分比（费率 / 最低 / 最高）、固定额（固定金额）、阶梯（档位子表）；不适用列显示「—」。</li>
 * <li>费率按百分比输入；组件币种不在此填写，保存时由 toSubmitDrafts 推导。</li>
 * <li>单元格只显示红框，错误文字汇总在该行下方；eventChoices 为 null 表示目录读取失败，计费事件改为手输。</li>
 * <li>benchmarkOf 给出上次保存时的破标比较：组件代码旁标「破标」（悬停看模板值）。</li>
 */
export function ComponentTable({ form, priceName, eventChoices, benchmarkOf }: {
  form: FormInstance; priceName: number; eventChoices: CodeOption[] | null; benchmarkOf?: (componentCode: string) => ComponentBenchmark | null;
}) {
  const t = useT('commercial');
  return <Form.List name={[priceName, 'components']}>{(fields, { add, remove }) => <>
    <div className="ct-scroll">
      <table className="ct-table">
        <colgroup>
          <col className="ct-col-code" /><col className="ct-col-event" /><col className="ct-col-type" /><col className="ct-col-base" />
          <col className="ct-col-amount" /><col className="ct-col-amount" /><col className="ct-col-bound" /><col className="ct-col-bound" />
          <col className="ct-col-refund" /><col className="ct-col-actions" />
        </colgroup>
        <thead><tr>
          <th>{t('componentTable.column.componentCode')}</th><th>{t('componentTable.column.event')}</th><th>{t('componentTable.column.type')}</th>
          <th>{t('componentTable.column.calculationBase')}</th><th>{t('componentTable.column.ratePercent')}</th><th>{t('componentTable.column.fixedAmount')}</th>
          <th>{t('componentTable.column.min')}</th><th>{t('componentTable.column.max')}</th><th>{t('componentTable.column.refund')}</th><th />
        </tr></thead>
        {fields.map((field, index) => {
          const path = ['prices', priceName, 'components', field.name];
          return <ComponentRows key={field.key} form={form} path={path} name={field.name} eventChoices={eventChoices} benchmarkOf={benchmarkOf}
            onCopy={() => add(copyComponentDraft(form.getFieldValue(path) as ComponentDraft), index + 1)}
            onRemove={fields.length > 1 ? () => remove(field.name) : undefined} />;
        })}
      </table>
    </div>
    {eventChoices === null && <div className="ct-hint">{t('componentTable.catalogFailed')}</div>}
    <Button size="small" className="ct-add" onClick={() => add(newComponentDraft())}>{t('componentTable.addComponent')}</Button>
  </>}</Form.List>;
}

/** 一个组件：主行 + 行错误 + 阶梯档位子行（阶梯组件始终挂载以参与校验，收起时隐藏；档位有错时自动展开）。 */
function ComponentRows({ form, path, name, eventChoices, benchmarkOf, onCopy, onRemove }: {
  form: FormInstance; path: NamePath; name: number; eventChoices: CodeOption[] | null; benchmarkOf?: (componentCode: string) => ComponentBenchmark | null;
  onCopy: () => void; onRemove?: () => void;
}) {
  const t = useT('commercial');
  const [tierOpen, setTierOpen] = useState(false);
  const type = Form.useWatch([...path, 'componentType'], form) as ComponentType | undefined;
  const bands = Form.useWatch([...path, 'bands'], form) as BandDraft[] | null | undefined;
  const bandMode = Form.useWatch([...path, 'bandMode'], form) as string | null | undefined;
  const code = Form.useWatch([...path, 'componentCode'], form) as string | undefined;
  const benchmark = code && benchmarkOf ? benchmarkOf(code) : null;
  const mark = benchmarkMark(benchmark);
  const changeType = (next: ComponentType) => {
    const patch = componentTypePatch(next, form.getFieldValue(path) as ComponentDraft);
    Object.entries(patch).forEach(([key, value]) => form.setFieldValue([...path, key], value));
  };
  const typeOptions = componentTypes.map(value => ({ value, label: enumLabel(t, 'enum.componentType', value) }));
  const cell = (column: 'rate' | 'fixedAmount' | 'minAmount' | 'maxAmount', rules: Rule[], placeholder?: string, suffix?: string) =>
    isEditable(type, column)
      ? <td><Form.Item name={[name, column]} rules={rules} noStyle><Input size="small" placeholder={placeholder} suffix={suffix} /></Form.Item></td>
      : <td className="ct-na">—</td>;
  return <tbody className="ct-component">
    <tr className="ct-row">
      <td><Form.Item name={[name, 'componentCode']} normalize={upper} noStyle
        rules={[required(t('componentTable.enterValue')), { pattern: /^[A-Z][A-Z0-9_]{0,63}$/, message: t('componentTable.codePattern') }]}><Input size="small" maxLength={64}
          suffix={mark?.below ? <Tooltip title={t('componentTable.benchmarkTooltip', { detail: benchmarkDetail(benchmark) ?? '' })}><Tag color="red" className="ct-benchmark">{t('benchmark.below')}</Tag></Tooltip>
            : undefined} /></Form.Item></td>
      <td><Form.Item name={[name, 'triggerCode']} normalize={upper} noStyle rules={[required(t('componentTable.selectValue'))]}>
        {eventChoices === null ? <Input size="small" maxLength={64} placeholder={t('componentTable.eventPlaceholder')} />
          : <Select size="small" popupMatchSelectWidth={false} showSearch={{ optionFilterProp: 'label' }} options={eventChoices}
            labelRender={item => item.value} />}</Form.Item></td>
      <td><Form.Item name={[name, 'componentType']} noStyle rules={[required(t('componentTable.selectValue'))]}>
        <Select size="small" options={typeOptions} onChange={changeType} /></Form.Item></td>
      <td><Form.Item name={[name, 'calculationBase']} noStyle rules={[required(t('componentTable.selectValue'))]}>
        <Select {...labeledSelect} options={labeledOptions(calculationBases, 'calculationBase')} /></Form.Item></td>
      {type === 'TIERED'
        ? <td colSpan={4}><Button type="link" size="small" className="ct-tier-toggle" aria-expanded={tierOpen} onClick={() => setTierOpen(open => !open)}>
          {tierSummary(bands, bandMode)} {tierOpen ? '▴' : '▾'}</Button></td>
        : <>
          {cell('rate', [required(t('componentTable.rateRequired')), percentRule], '2.9', '%')}
          {cell('fixedAmount', [required(t('componentTable.amountRequired')), amountRule(t)], '0.30')}
          {cell('minAmount', [amountRule(t)])}
          {cell('maxAmount', [amountRule(t)])}
        </>}
      <td><Form.Item name={[name, 'refundBehavior']} noStyle rules={[required(t('componentTable.selectValue'))]}>
        <Select {...labeledSelect} options={labeledOptions(refundBehaviors, 'refundBehavior')} /></Form.Item></td>
      <td className="ct-actions">
        <Button type="link" size="small" onClick={onCopy}>{t('componentTable.copy')}</Button>
        <Button type="link" size="small" danger disabled={!onRemove} onClick={onRemove}>{t('componentTable.delete')}</Button>
      </td>
    </tr>
    <Form.Item noStyle shouldUpdate>{() => <RowErrors form={form} path={path} />}</Form.Item>
    {type === 'TIERED' && <Form.Item noStyle shouldUpdate>{() => {
      const forced = form.getFieldsError().some(item => item.errors.length > 0
        && (startsWith(item.name, [...path, 'bands']) || startsWith(item.name, [...path, 'bandBasis']) || startsWith(item.name, [...path, 'bandMode'])));
      return <tr className="ct-tier-row" hidden={!(tierOpen || forced)}><td colSpan={10}><BandTable form={form} path={path} name={name} /></td></tr>;
    }}</Form.Item>}
  </tbody>;
}

/** 主行单元格的错误文字，显示在行下方，不撑开列宽。 */
function RowErrors({ form, path }: { form: FormInstance; path: NamePath }) {
  const t = useT('commercial');
  const messages = rowColumns.flatMap(([column, label]) => form.getFieldError([...path, column])
    .map(error => t('componentTable.fieldError', { field: t(label), error })));
  if (!messages.length) return null;
  return <tr className="ct-error-row"><td colSpan={10}>{joinList(messages, 'clauses')}</td></tr>;
}

/** 档位子表：阶梯基数 / 模式 + [下界, 上界) 档位，费率（%）与固定金额恰好一个；连续性提示复用 bandIssues（只判断有无费率，不受百分比换算影响）。 */
function BandTable({ form, path, name }: { form: FormInstance; path: NamePath; name: number }) {
  const t = useT('commercial');
  const bands = (Form.useWatch([...path, 'bands'], form) ?? []) as BandDraft[];
  const mode = Form.useWatch([...path, 'bandMode'], form) as string | null;
  const issues = bands.length ? bandIssues(bands, mode) : [];
  const headErrors = [...form.getFieldError([...path, 'bandBasis']).map(error => t('componentTable.fieldError', { field: t('componentTable.band.basis'), error })),
    ...form.getFieldError([...path, 'bandMode']).map(error => t('componentTable.fieldError', { field: t('componentTable.band.mode'), error }))];
  return <div className="ct-tier">
    <div className="ct-tier-head">
      <span className="ct-tier-label">{t('componentTable.band.basis')}</span>
      <Form.Item name={[name, 'bandBasis']} noStyle rules={[required(t('componentTable.selectValue'))]}>
        <Select {...labeledSelect} className="ct-tier-select" options={labeledOptions(bandBases, 'bandBasis')} /></Form.Item>
      <span className="ct-tier-label">{t('componentTable.band.mode')}</span>
      <Form.Item name={[name, 'bandMode']} noStyle rules={[required(t('componentTable.selectValue'))]}>
        <Select {...labeledSelect} className="ct-tier-select" options={labeledOptions(bandModes, 'bandMode')} /></Form.Item>
    </div>
    <Form.List name={[name, 'bands']} rules={[{ validator: async (_, value?: BandDraft[]) => {
      const problems = bandIssues(value ?? [], form.getFieldValue([...path, 'bandMode']));
      if (problems.length) throw new Error(problems[0]);
    } }]}>{(fields, { add, remove }) => <>
      <table className="ct-band-table">
        <thead><tr><th>{t('componentTable.band.lowerInclusive')}</th><th>{t('componentTable.band.upperExclusive')}</th><th>{t('componentTable.column.ratePercent')}</th>
          <th>{t('componentTable.column.fixedAmount')}</th><th /></tr></thead>
        <tbody>{fields.map(field => <tr key={field.key}>
          <td><Form.Item name={[field.name, 'lowerBound']} noStyle rules={[required(t('componentTable.enterValue')), amountRule(t)]}><Input size="small" placeholder="0" /></Form.Item></td>
          <td><Form.Item name={[field.name, 'upperBound']} noStyle rules={[amountRule(t)]}><Input size="small" placeholder={t('componentTable.band.upperPlaceholder')} /></Form.Item></td>
          <td><Form.Item name={[field.name, 'rate']} noStyle rules={[percentRule]}><Input size="small" suffix="%" /></Form.Item></td>
          <td><Form.Item name={[field.name, 'fixedAmount']} noStyle rules={[amountRule(t)]}><Input size="small" /></Form.Item></td>
          <td className="ct-actions"><Button type="link" size="small" onClick={() => remove(field.name)}>{t('componentTable.band.remove')}</Button></td>
        </tr>)}</tbody>
      </table>
      <Button size="small" type="link" onClick={() => add({ lowerBound: bands.at(-1)?.upperBound ?? (bands.length ? '' : '0'), upperBound: null, rate: null,
        fixedAmount: null } satisfies BandDraft)}>{t('componentTable.band.add')}</Button>
    </>}</Form.List>
    <BandErrors form={form} path={path} headErrors={headErrors} issues={issues} />
  </div>;
}

/** 档位错误：单元格校验文字 + 连续性提示（与 commercial 激活校验一致）。 */
function BandErrors({ form, path, headErrors, issues }: { form: FormInstance; path: NamePath; headErrors: string[]; issues: string[] }) {
  const t = useT('commercial');
  const bandsPath = [...path, 'bands'];
  const cellErrors = form.getFieldsError().filter(item => item.errors.length > 0 && item.name.length === bandsPath.length + 2 && startsWith(item.name, bandsPath))
    .flatMap(item => {
      const label = bandColumns.find(([column]) => column === item.name[bandsPath.length + 1])?.[1];
      return item.errors.map(error => t('componentTable.band.cellError', { no: Number(item.name[bandsPath.length]) + 1, field: label ? t(label) : '', error }));
    });
  const messages = [...headErrors, ...cellErrors];
  if (!messages.length && !issues.length) return null;
  return <ul className="ct-issues">{[...messages, ...issues].map(message => <li key={message}>{message}</li>)}</ul>;
}
