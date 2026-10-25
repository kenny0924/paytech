import { emptyOperand, literalOperand } from './riskVariable';
import { VariablePredicateEditor } from './VariableControls';
import { useState } from 'react';
import { Button, Input, Segmented, Select, Tooltip } from 'antd';
import { canonicalFieldValue, operatorsForField } from './ruleBuilder';
import type { EditorCondition } from './ruleBuilder';
import type { AuthoringCatalog } from './riskAuthoringCatalog';
import { appendCondition, conditionDepth, removeConditionChild } from './ruleEditorPresentation';
import { useT } from '../i18n/react';

export function newCondition(): EditorCondition {
  return { id: crypto.randomUUID(), kind: 'CONDITION', fieldSide: 'LEFT', fieldCode: '', operator: 'EQ', values: [], operands: [emptyOperand(),literalOperand('STRING')] };
}
function valuesForOperator(operator: string, values: string[]): string[] {
  if (operator === 'IS_NULL' || operator === 'IS_NOT_NULL') return [];
  if (operator === 'BETWEEN') return [values[0] ?? '', values[1] ?? ''];
  if (operator === 'IN' || operator === 'NOT_IN') return values.length ? values : [''];
  return [values[0] ?? ''];
}

type Predicate = Extract<EditorCondition, { kind: 'CONDITION' }>;
function ConditionRow({ condition, catalog, disabled, onChange, onRemove }: {
  condition: Predicate; catalog: AuthoringCatalog; disabled: boolean; onChange: (value: EditorCondition) => void; onRemove?: () => void;
}) {
  const t = useT('risk');
  if(condition.operands)return <div className="risk-condition-row-wrap"><VariablePredicateEditor operator={condition.operator} operands={condition.operands} filters={{pointCode:catalog.variableQueryContext?.pointCode,usage:'RULE_CONDITION'}} disabled={disabled} onChange={(operator,operands)=>onChange({...condition,operator,operands})}/>{onRemove&&<Button type="text" danger disabled={disabled} onClick={onRemove}>{t('flowCondition.remove')}</Button>}</div>;
  const field = catalog.fields.find(item => item.fieldCode === condition.fieldCode);
  if(!field)return <p>{t('conditionBuilder.legacy', { field: condition.fieldCode })}</p>;
  const set = condition.operator === 'IN' || condition.operator === 'NOT_IN';
  const errors = condition.values.flatMap(value => {
    if (!value) return [];
    try { canonicalFieldValue(condition.fieldCode, value); return []; }
    catch (error) { return [error instanceof Error ? error.message : t('conditionBuilder.checkValue')]; }
  });
  const choices = field.widget === 'ENTITY_SOURCE' ? ['CLIENT', 'PARTNER'].map(value => ({ value, label: value })) : [];
  const fieldControl = <Select aria-label={t('conditionBuilder.field')} value={condition.fieldCode} disabled={disabled} className="risk-condition-field"
    options={catalog.fields.map(item => ({ value: item.fieldCode, label: item.label }))} onChange={fieldCode => {
      const allowed = operatorsForField(fieldCode); const operator = allowed.includes(condition.operator as typeof allowed[number]) ? condition.operator : 'EQ';
      onChange({ ...condition, fieldCode, operator, values: valuesForOperator(operator, ['']) });
    }} />;
  const valuesControl = <div className="risk-condition-value">
    {set ? <Select aria-label={t('conditionBuilder.valueSet')} mode={field.widget === 'ENTITY_SOURCE' ? 'multiple' : 'tags'} value={condition.values.filter(value => value !== '')} disabled={disabled}
      options={choices} tokenSeparators={[',', '\uFF0C']} maxCount={100} placeholder={field.helpText} status={errors.length ? 'error' : undefined}
      onChange={(values: string[]) => onChange({ ...condition, values: values.length ? values : [''] })} />
      : condition.values.length ? <div className="risk-inline-values">{condition.values.map((value, index) => <div key={index}>
        {index > 0 && <span className="risk-range-separator">{t('conditionBuilder.rangeTo')}</span>}
        {field.widget === 'ENTITY_SOURCE' ? <Select aria-label={t('conditionBuilder.value', { index: index + 1 })} value={value || undefined} disabled={disabled} options={choices} placeholder={t('conditionBuilder.selectSource')}
          onChange={next => onChange({ ...condition, values: condition.values.map((item, position) => position === index ? next : item) })} />
          : <Input aria-label={t('conditionBuilder.value', { index: index + 1 })} value={value} autoComplete="off" spellCheck={false} disabled={disabled} placeholder={field.helpText}
            inputMode={field.widget === 'DECIMAL' ? 'decimal' : field.widget === 'MCC' ? 'numeric' : 'text'} status={errors.length ? 'error' : undefined}
            onChange={event => onChange({ ...condition, values: condition.values.map((item, position) => position === index ? event.target.value : item) })} />}
      </div>)}</div> : <span className="risk-help">{t('conditionBuilder.noValue')}</span>}
  </div>;
  return <div className="risk-condition-row-wrap"><div className={`risk-condition-row${condition.fieldSide === 'RIGHT' ? ' field-on-right' : ''}`}>
    {condition.fieldSide === 'LEFT' ? fieldControl : valuesControl}
    <Select aria-label={t('conditionBuilder.operator')} value={condition.operator} disabled={disabled} className="risk-condition-operator"
      options={catalog.operators.filter(item => operatorsForField(condition.fieldCode).includes(item.operator)).map(item => ({ value: item.operator, label: item.label }))}
      onChange={operator => onChange({ ...condition, operator, values: valuesForOperator(operator, condition.values) })} />
    {condition.fieldSide === 'LEFT' ? valuesControl : fieldControl}
    <Tooltip title={condition.fieldSide === 'LEFT' ? t('conditionBuilder.fieldOnLeft') : t('conditionBuilder.fieldOnRight')}>
      <Button type="text" className="risk-condition-direction" aria-label={t('conditionBuilder.switchSide')} disabled={disabled}
        onClick={() => onChange({ ...condition, fieldSide: condition.fieldSide === 'LEFT' ? 'RIGHT' : 'LEFT' })}>⇄</Button></Tooltip>
    <Button type="text" aria-label={t('conditionBuilder.delete')} disabled={disabled || !onRemove} onClick={onRemove}>×</Button>
  </div>{errors.length > 0 && <p className="risk-field-error" role="alert">{[...new Set(errors)].join(t('flowCondition.childSeparator'))}</p>}</div>;
}

export function ConditionBuilder({ condition, catalog, disabled, onChange, depth = 1, onRemove }: {
  condition: EditorCondition; catalog: AuthoringCatalog; disabled: boolean; onChange: (value: EditorCondition) => void; depth?: number; onRemove?: () => void;
}) {
  const t = useT('risk');
  const [singleJoin, setSingleJoin] = useState<'ALL' | 'ANY'>('ALL');
  const join = condition.kind === 'ALL' || condition.kind === 'ANY' ? condition.kind : singleJoin;
  const canWrap = depth + conditionDepth(condition) <= 12;
  function add(group = false) {
    const child: EditorCondition = group ? { id: crypto.randomUUID(), kind: 'ALL', children: [newCondition(), newCondition()] } : newCondition();
    onChange(appendCondition(condition, child, join));
  }
  const rootLeaf = condition.kind === 'CONDITION';
  return <section className={`risk-condition${depth > 1 ? ' risk-condition-nested' : ''}`} aria-label={depth === 1 ? t('conditionBuilder.ruleCondition') : t('conditionBuilder.group')}>
    <div className="risk-condition-toolbar"><strong>{depth === 1 ? t('conditionBuilder.when') : t('conditionBuilder.group')}</strong>
      {condition.kind === 'NOT' ? <span className="risk-negation-label">{t('conditionBuilder.not')}</span> : <Segmented aria-label={t('conditionBuilder.join')} value={join} disabled={disabled}
        options={[{ value: 'ALL', label: t('conditionBuilder.all'), title: t('conditionBuilder.allTitle') }, { value: 'ANY', label: t('conditionBuilder.any'), title: t('conditionBuilder.anyTitle') }]}
        onChange={kind => { setSingleJoin(kind as 'ALL' | 'ANY'); if (!rootLeaf) onChange({ ...condition, kind: kind as 'ALL' | 'ANY' }); }} />}
      <div className="risk-condition-toolbar-end"><Button type="text" size="small" disabled={disabled || (condition.kind !== 'NOT' && !canWrap)}
        onClick={() => onChange(condition.kind === 'NOT' ? condition.children[0]! : { id: crypto.randomUUID(), kind: 'NOT', children: [condition] })}>{condition.kind === 'NOT' ? t('conditionBuilder.unnegate') : t('conditionBuilder.negate')}</Button>
        {onRemove && <Button type="text" aria-label={t('conditionBuilder.deleteGroup')} disabled={disabled} onClick={onRemove}>×</Button>}</div>
    </div>
    <div className="risk-condition-box">
      {condition.kind === 'CONDITION' ? <ConditionRow condition={condition} catalog={catalog} disabled={disabled} onChange={onChange} />
        : condition.children.map((child, index) => {
          const update = (next: EditorCondition) => onChange({ ...condition, children: condition.children.map((item, position) => position === index ? next : item) });
          const remove = condition.kind !== 'NOT' ? () => onChange(removeConditionChild(condition, index)) : undefined;
          return child.kind === 'CONDITION' ? <ConditionRow key={child.id} condition={child} catalog={catalog} disabled={disabled} onChange={update} onRemove={remove} />
            : <ConditionBuilder key={child.id} condition={child} catalog={catalog} disabled={disabled} depth={depth + 1} onChange={update} onRemove={remove} />;
        })}
    </div>
    <div className="risk-condition-add"><Button type="link" disabled={disabled || depth >= 12 || ((rootLeaf || condition.kind === 'NOT') && !canWrap)} onClick={() => add()}>{t('flowCondition.add')}</Button>
      <Button type="link" disabled={disabled || depth >= 11 || ((rootLeaf || condition.kind === 'NOT') && !canWrap)} onClick={() => add(true)}>{t('conditionBuilder.addGroup')}</Button></div>
  </section>;
}
