import { VariableProjectionPanel } from './VariableControls';
import { useEffect, useState } from 'react';
import { Button, Descriptions, Modal, Tag, Tooltip, Typography } from 'antd';
import type { RuleSetEditorModel, EditorCondition } from './ruleBuilder';
import type { RuleSetDetail } from './riskApiModels';
import { ConditionView, formatRiskTime, ruleSetStatusLabels } from './RuleSetDetails';
import { CHECKOUT_PREPARE_AUTHORING_V1 as catalog } from './riskAuthoringCatalog';
import { conditionCount } from './ruleEditorPresentation';

/** 与编辑器共享工作区布局，只读取持久化内容，不依赖选项目录或组装保存请求。 */
export function ReadRuleSetWorkspace({editor,base}:{editor:RuleSetEditorModel;base:RuleSetDetail|null}) {
  const [selectedId,setSelectedId]=useState(editor.rules[0]?.id);const [collapsed,setCollapsed]=useState(false);const [basic,setBasic]=useState(false);
  useEffect(()=>{const query=window.matchMedia('(max-width: 1024px)');const sync=()=>{if(query.matches)setCollapsed(true);};sync();query.addEventListener('change',sync);return()=>query.removeEventListener('change',sync);},[]);
  const selected=editor.rules.find(row=>row.id===selectedId)??editor.rules[0];
  const decision=(code:string)=>({ALLOW_CHECKOUT:'PASS',REVIEW_CHECKOUT:'REVIEW',DENY_CHECKOUT:'REJECT'}[code]??'未配置动作');
  return <section className="risk-editor risk-editor-workspace" aria-label="规则集只读详情">
    <header className="risk-workspace-header">
      <div className="risk-editor-heading"><div className="risk-title-line"><h2>{editor.ruleSetName}</h2><Tag>只读</Tag>{base&&<Tag>#{base.versionNo} · {ruleSetStatusLabels[base.status]}</Tag>}</div><Button onClick={()=>setBasic(true)}>基本信息</Button></div>
      <p className="risk-meta">{editor.ruleSetCode} · {editor.pointCode}{base&&` · 保存人 ${base.createdBy} · ${formatRiskTime(base.createTime)}`}</p></header>
    {base&&<VariableProjectionPanel projection={base}/>}
    <div className={`risk-workspace-body${collapsed?' is-collapsed':''}`}><aside className="risk-rule-sidebar" aria-label="规则列表">
      <div className="risk-sidebar-heading"><strong>规则 <span>{editor.rules.length}</span></strong><Button type="text" aria-label={collapsed?'展开规则列表':'收起规则列表'} aria-expanded={!collapsed} onClick={()=>setCollapsed(!collapsed)}>{collapsed?'›':'‹'}</Button></div>
      <ol className="risk-rule-list">{editor.rules.map((rule,index)=><li key={rule.id} className={selected?.id===rule.id?'is-selected':''}><Tooltip title={`${rule.ruleName || '未命名规则'} · ${conditionCount(rule.condition)} 个条件 · ${decision(rule.actionCode)}`} placement="right"><button type="button" className="risk-rule-select" aria-current={selected?.id===rule.id?'true':undefined} onClick={()=>setSelectedId(rule.id)} aria-label={`查看规则 ${index+1} ${rule.ruleName}`}>
        <span className="risk-rule-number">{String(index+1).padStart(2,'0')}</span><span className="risk-rule-summary"><strong>{rule.ruleName || '未命名规则'}</strong><small>{conditionCount(rule.condition)} 个条件 · {decision(rule.actionCode)}</small></span><span className={`risk-decision-dot decision-${rule.enabled ? decision(rule.actionCode) : 'disabled'}`} aria-label={rule.enabled ? decision(rule.actionCode) : '停用'} /></button></Tooltip></li>)}</ol></aside>
      <main className="risk-rule-detail" aria-label="选中规则内容">{selected?<><div className="risk-rule-detail-heading"><div><h3>{selected.ruleName}</h3><p>{selected.ruleCode}</p></div><Tag>{selected.enabled?'启用':'停用'}</Tag></div>
        <section className="risk-read-condition"><h4>匹配条件</h4><ReadCondition condition={selected.condition}/></section><h4>决策结果 <Tag>{decision(selected.actionCode)}</Tag></h4>
        {selected.reasonCode&&<section className="risk-reject-panel"><div className="risk-reject-fields"><div><strong>拒绝原因</strong><p className="risk-read-value"><Typography.Text copyable>{selected.reasonCode}</Typography.Text></p><small className="cell-meta">已保存的原因代码</small></div>
          {selected.reasonMessage&&<div><strong>内部拒绝文案</strong><p className="risk-read-value risk-read-message">{selected.reasonMessage}</p><small className="cell-meta">按此版本保存的文案展示</small></div>}</div><p className="risk-help">用于内部决策记录；对外提示由接口统一映射。</p></section>}
        {!!selected.additionalActions?.length&&<h4>附加动作</h4>}{selected.additionalActions?.map(action=><section className="risk-action-panel" key={action.id}><strong>{action.actionCode==='ADD_RISK_TAG'?'添加风险标签':action.actionCode==='APPLY_COOLDOWN'?'冷却主体':action.actionCode}</strong><p>主体绑定：{action.targetBindingCode}</p>{action.parameters.map(parameter=><p key={parameter.parameterCode}>{parameter.parameterCode}：{parameter.values.join('、')}{parameter.valueType==='DURATION'?' 毫秒':''}</p>)}</section>)}
        <details className="risk-description"><summary>查看规则描述</summary><ConditionView condition={selected.condition}/></details></>:<p>此版本没有规则。</p>}</main></div>
    <footer className="risk-read-footer"><div><span className="risk-meta">保存人：{base?.createdBy} · {formatRiskTime(base?.createTime ?? null)}</span><p className="risk-read-message">变更原因：{editor.changeReason}</p></div></footer>
    <Modal open={basic} onCancel={()=>setBasic(false)} title="规则集基本信息" footer={<Button onClick={()=>setBasic(false)}>关闭</Button>}><Descriptions column={1} items={[
      {key:'code',label:'编码',children:editor.ruleSetCode},{key:'name',label:'名称',children:editor.ruleSetName},{key:'point',label:'决策点',children:editor.pointCode},
      {key:'version',label:'版本',children:base?.versionNo},{key:'reason',label:'变更原因',children:<span className="risk-read-message">{editor.changeReason}</span>},
    ]}/></Modal></section>;
}

function ReadCondition({condition}:{condition:EditorCondition}) {
  if(condition.kind!=='CONDITION')return <div className="risk-read-group"><strong>{{ALL:'满足全部条件',ANY:'满足任一条件',NOT:'不满足以下条件'}[condition.kind]}</strong>{condition.children.map(child=><ReadCondition key={child.id} condition={child}/>)}</div>;
  if(condition.kind==='CONDITION'&&condition.operands)return <ConditionView condition={condition}/>;
  const field=catalog.fields.find(row=>row.fieldCode===condition.fieldCode)?.label??condition.fieldCode;
  const operator=catalog.operators.find(row=>row.operator===condition.operator)?.label??condition.operator;
  const values=condition.values.join('、');return <div className="risk-read-predicate"><span>{condition.fieldSide==='LEFT'?field:values}</span><span>{operator}</span><span>{condition.fieldSide==='LEFT'?values:field}</span></div>;
}
