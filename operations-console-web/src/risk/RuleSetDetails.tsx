import { operatorLabel } from './riskVariable';
import { enumLabel, formatDateTime, translator } from '../i18n/index';
import { useT } from '../i18n/react';
import { Descriptions, Tag, Typography } from 'antd';
import type { RuleSetDetail } from './riskApiModels';
import { detailToEditor } from './ruleBuilder';
import type { EditorCondition } from './ruleBuilder';
import { CHECKOUT_PREPARE_AUTHORING_V1 as catalog } from './riskAuthoringCatalog';

/** 规则集版本状态显示名是 `risk.enum.ruleSetStatus.<值>`。 */
export const ruleSetStatuses = ['DRAFT', 'FROZEN', 'RETIRED'] as const;
export function ruleSetStatusLabel(status: string): string { return enumLabel(translator('risk'), 'enum.ruleSetStatus', status); }
export function formatRiskTime(value: string | null) { return formatDateTime(value); }
export function ContentHash({ hash }: { hash: string }) {
  const t = useT('risk');
  return <Typography.Text copyable={{ text: hash, format: 'text/plain', tooltips: [t('ruleSetDetails.copyHash'), t('ruleSetDetails.copied')],
    icon: [<span key="copy">{t('ruleSetDetails.copyHash')}</span>, <span key="copied">{t('ruleSetDetails.copied')}</span>] }}>{hash.slice(0, 12)}…</Typography.Text>;
}
export function ConditionView({ condition }: { condition: EditorCondition }) {
  const t = useT('risk');
  if (condition.kind !== 'CONDITION') return <div className="risk-condition-view"><strong>{t(`ruleSetDetails.group.${condition.kind}`)}</strong>
    <ul>{condition.children.map(child => <li key={child.id}><ConditionView condition={child} /></li>)}</ul></div>;
  if(condition.operands){const render=(side:'LEFT'|'RIGHT')=>condition.operands!.filter(o=>o.operandRole===side).map(o=>o.sourceType==='VARIABLE'?t('flowCondition.variable',{code:o.variableCode??''}):`${String(o.literalValue)}${o.numericUnit==='MONEY'?` (${o.currencyCode??o.currencyVariableCode})`:''}`).join(t('evaluation.valueSeparator'));return <span>{render('LEFT')} {operatorLabel(condition.operator)} {render('RIGHT')}</span>;}
  const field = catalog.fields.find(item => item.fieldCode === condition.fieldCode)?.label ?? condition.fieldCode;
  const operator = catalog.operators.find(item => item.operator === condition.operator)?.label ?? condition.operator;
  const values = condition.values.join(t('evaluation.valueSeparator'));
  return <span>{condition.fieldSide === 'LEFT' ? `${field} ${operator} ${values}` : `${values} ${operator} ${field}`}<small className="cell-meta">{condition.fieldSide === 'LEFT' ? t('ruleSetDetails.fieldOnLeft') : t('ruleSetDetails.fieldOnRight')}</small></span>;
}
export function RuleSetDetails({ detail }: { detail: RuleSetDetail }) {
  const t = useT('risk');
  const editor = detailToEditor(detail);
  return <div className="risk-details">
    <Descriptions bordered size="small" column={1} items={[
      { key: 'code', label: t('fieldError.ruleSetCode'), children: detail.ruleSetCode }, { key: 'name', label: t('ruleSetPicker.column.name'), children: detail.ruleSetName },
      { key: 'point', label: t('fieldError.pointCode'), children: detail.pointCode }, { key: 'id', label: t('ruleSetDetails.versionId'), children: detail.ruleSetVersionId },
      { key: 'version', label: t('ruleSetDetails.businessVersion'), children: detail.versionNo }, { key: 'status', label: t('ruleSetPicker.column.status'), children: <Tag>{ruleSetStatusLabel(detail.status)}</Tag> },
      { key: 'hash', label: t('candidate.field.contentHash'), children: <ContentHash hash={detail.contentHash} /> },
      { key: 'reason', label: t('fieldError.changeReason'), children: detail.changeReason },
      { key: 'created', label: t('candidate.field.created'), children: `${detail.createdBy} / ${formatRiskTime(detail.createTime)}` },
      { key: 'frozen', label: t('ruleSetDetails.frozen'), children: `${detail.frozenBy ?? '—'} / ${formatRiskTime(detail.frozenTime)}` },
    ]} />
    <h3>{t('ruleSetDetails.rules')}</h3>
    {editor.rules.map((rule, index) => <section key={rule.id} className="risk-rule-card">
      <h4>{index + 1}. {rule.ruleName} <Tag>{rule.enabled ? t('ruleSetDetails.enabled') : t('ruleSetDetails.disabled')}</Tag></h4>
      <p className="cell-meta">{rule.ruleCode}{rule.reasonCode && ` · ${t('ruleSetDetails.reasonCode', { code: rule.reasonCode })}`}</p>
      <ConditionView condition={rule.condition} />
      <p>{t('ruleSetDetails.decision', { decision: rule.actionCode ? catalog.actions.find(action => action.actionCode === rule.actionCode)?.label ?? rule.actionCode : t('ruleSetDetails.noDecision') })}</p>
      {rule.reasonMessage && <p>{t('ruleSetDetails.rejectMessage', { message: rule.reasonMessage })}</p>}
      {(rule.additionalActions ?? []).map(action => <div key={action.id}>
        <p>{t('ruleSetDetails.additionalAction', { action: action.actionCode, binding: action.targetBindingCode })}</p>
        {action.parameters.map(parameter => <p key={parameter.parameterCode}>{t('flowPanel.outputValue', { name: parameter.valueType === 'DURATION' ? t('ruleSetDetails.durationMs') : t('ruleSetDetails.tags'), value: parameter.values.join(t('evaluation.valueSeparator')) })}</p>)}
      </div>)}
    </section>)}
  </div>;
}
