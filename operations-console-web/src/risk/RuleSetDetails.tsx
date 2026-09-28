import { operatorLabels } from './riskVariable';
import { Descriptions, Tag, Typography } from 'antd';
import type { RuleSetDetail } from './riskApiModels';
import { detailToEditor } from './ruleBuilder';
import type { EditorCondition } from './ruleBuilder';
import { CHECKOUT_PREPARE_AUTHORING_V1 as catalog } from './riskAuthoringCatalog';

export const ruleSetStatusLabels = { DRAFT: '草稿', FROZEN: '已冻结', RETIRED: '已退役' } as const;
export function formatRiskTime(value: string | null) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'; }
export function ContentHash({ hash }: { hash: string }) {
  return <Typography.Text copyable={{ text: hash, format: 'text/plain', tooltips: ['复制完整 Hash', '已复制'],
    icon: [<span key="copy">复制完整 Hash</span>, <span key="copied">已复制</span>] }}>{hash.slice(0, 12)}…</Typography.Text>;
}
export function ConditionView({ condition }: { condition: EditorCondition }) {
  if (condition.kind !== 'CONDITION') return <div className="risk-condition-view"><strong>{{ ALL: '满足全部', ANY: '满足任一', NOT: '不满足' }[condition.kind]}</strong>
    <ul>{condition.children.map(child => <li key={child.id}><ConditionView condition={child} /></li>)}</ul></div>;
  if(condition.operands){const render=(side:'LEFT'|'RIGHT')=>condition.operands!.filter(o=>o.operandRole===side).map(o=>o.sourceType==='VARIABLE'?`变量 ${o.variableCode}`:`${String(o.literalValue)}${o.numericUnit==='MONEY'?` (${o.currencyCode??o.currencyVariableCode})`:''}`).join('、');return <span>{render('LEFT')} {operatorLabels[condition.operator as keyof typeof operatorLabels]??condition.operator} {render('RIGHT')}</span>;}
  const field = catalog.fields.find(item => item.fieldCode === condition.fieldCode)?.label ?? condition.fieldCode;
  const operator = catalog.operators.find(item => item.operator === condition.operator)?.label ?? condition.operator;
  const values = condition.values.join('、');
  return <span>{condition.fieldSide === 'LEFT' ? `${field} ${operator} ${values}` : `${values} ${operator} ${field}`}<small className="cell-meta">（{condition.fieldSide === 'LEFT' ? '字段在左' : '字段在右'}）</small></span>;
}
export function RuleSetDetails({ detail }: { detail: RuleSetDetail }) {
  const editor = detailToEditor(detail);
  return <div className="risk-details">
    <Descriptions bordered size="small" column={1} items={[
      { key: 'code', label: '规则集编码', children: detail.ruleSetCode }, { key: 'name', label: '名称', children: detail.ruleSetName },
      { key: 'point', label: '决策点', children: detail.pointCode }, { key: 'id', label: '版本 ID', children: detail.ruleSetVersionId },
      { key: 'version', label: '业务版本', children: detail.versionNo }, { key: 'status', label: '状态', children: <Tag>{ruleSetStatusLabels[detail.status]}</Tag> },
      { key: 'hash', label: '内容摘要', children: <ContentHash hash={detail.contentHash} /> },
      { key: 'reason', label: '变更原因', children: detail.changeReason },
      { key: 'created', label: '创建人 / 时间', children: `${detail.createdBy} / ${formatRiskTime(detail.createTime)}` },
      { key: 'frozen', label: '冻结人 / 时间', children: `${detail.frozenBy ?? '—'} / ${formatRiskTime(detail.frozenTime)}` },
    ]} />
    <h3>规则内容</h3>
    {editor.rules.map((rule, index) => <section key={rule.id} className="risk-rule-card">
      <h4>{index + 1}. {rule.ruleName} <Tag>{rule.enabled ? '启用' : '停用'}</Tag></h4>
      <p className="cell-meta">{rule.ruleCode}{rule.reasonCode && ` · 原因编码 ${rule.reasonCode}`}</p>
      <ConditionView condition={rule.condition} />
      <p>决定：{rule.actionCode ? catalog.actions.find(action => action.actionCode === rule.actionCode)?.label ?? rule.actionCode : '不产生终态决定'}</p>
      {rule.reasonMessage && <p>拒绝文案：{rule.reasonMessage}</p>}
      {(rule.additionalActions ?? []).map(action => <div key={action.id}>
        <p>附加动作：{action.actionCode} · 主体绑定 {action.targetBindingCode}</p>
        {action.parameters.map(parameter => <p key={parameter.parameterCode}>{parameter.valueType === 'DURATION' ? '时长（毫秒）' : '标签'}：{parameter.values.join('、')}</p>)}
      </div>)}
    </section>)}
  </div>;
}
