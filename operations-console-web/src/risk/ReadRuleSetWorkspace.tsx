import { VariableProjectionPanel } from './VariableControls';
import { useEffect, useState } from 'react';
import { Button, Descriptions, Modal, Tag, Tooltip, Typography } from 'antd';
import type { RuleSetEditorModel, EditorCondition } from './ruleBuilder';
import { terminalActionLabels } from './ruleBuilder';
import type { RuleSetDetail } from './riskApiModels';
import { ConditionView, formatRiskTime, ruleSetStatusLabel } from './RuleSetDetails';
import { useT } from '../i18n/react';
import { CHECKOUT_PREPARE_AUTHORING_V1 as catalog } from './riskAuthoringCatalog';
import { conditionCount } from './ruleEditorPresentation';

/** 与编辑器共享工作区布局，只读取持久化内容，不依赖选项目录或组装保存请求。 */
export function ReadRuleSetWorkspace({editor,base}:{editor:RuleSetEditorModel;base:RuleSetDetail|null}) {
  const t=useT('risk');
  const [selectedId,setSelectedId]=useState(editor.rules[0]?.id);const [collapsed,setCollapsed]=useState(false);const [basic,setBasic]=useState(false);
  useEffect(()=>{const query=window.matchMedia('(max-width: 1024px)');const sync=()=>{if(query.matches)setCollapsed(true);};sync();query.addEventListener('change',sync);return()=>query.removeEventListener('change',sync);},[]);
  const selected=editor.rules.find(row=>row.id===selectedId)??editor.rules[0];
  const decision=(code:string)=>terminalActionLabels[code]??t('readWorkspace.noAction');
  return <section className="risk-editor risk-editor-workspace" aria-label={t('readWorkspace.label')}>
    <header className="risk-workspace-header">
      <div className="risk-editor-heading"><div className="risk-title-line"><h2>{editor.ruleSetName}</h2><Tag>{t('readWorkspace.readonly')}</Tag>{base&&<Tag>#{base.versionNo} · {ruleSetStatusLabel(base.status)}</Tag>}</div><Button onClick={()=>setBasic(true)}>{t('readWorkspace.basicInfo')}</Button></div>
      <p className="risk-meta">{editor.ruleSetCode} · {editor.pointCode}{base&&` · ${t('readWorkspace.savedBy',{actor:base.createdBy,time:formatRiskTime(base.createTime)})}`}</p></header>
    {base&&<VariableProjectionPanel projection={base}/>}
    <div className={`risk-workspace-body${collapsed?' is-collapsed':''}`}><aside className="risk-rule-sidebar" aria-label={t('readWorkspace.ruleList')}>
      <div className="risk-sidebar-heading"><strong>{t('readWorkspace.rules')} <span>{editor.rules.length}</span></strong><Button type="text" aria-label={collapsed?t('readWorkspace.expand'):t('readWorkspace.collapse')} aria-expanded={!collapsed} onClick={()=>setCollapsed(!collapsed)}>{collapsed?'›':'‹'}</Button></div>
      <ol className="risk-rule-list">{editor.rules.map((rule,index)=><li key={rule.id} className={selected?.id===rule.id?'is-selected':''}><Tooltip title={t('readWorkspace.ruleTooltip',{name:rule.ruleName || t('readWorkspace.unnamed'),count:conditionCount(rule.condition),decision:decision(rule.actionCode)})} placement="right"><button type="button" className="risk-rule-select" aria-current={selected?.id===rule.id?'true':undefined} onClick={()=>setSelectedId(rule.id)} aria-label={t('readWorkspace.viewRule',{index:index+1,name:rule.ruleName})}>
        <span className="risk-rule-number">{String(index+1).padStart(2,'0')}</span><span className="risk-rule-summary"><strong>{rule.ruleName || t('readWorkspace.unnamed')}</strong><small>{t('readWorkspace.ruleSummary',{count:conditionCount(rule.condition),decision:decision(rule.actionCode)})}</small></span><span className={`risk-decision-dot decision-${rule.enabled ? decision(rule.actionCode) : 'disabled'}`} aria-label={rule.enabled ? decision(rule.actionCode) : t('ruleSetDetails.disabled')} /></button></Tooltip></li>)}</ol></aside>
      <main className="risk-rule-detail" aria-label={t('readWorkspace.selectedRule')}>{selected?<><div className="risk-rule-detail-heading"><div><h3>{selected.ruleName}</h3><p>{selected.ruleCode}</p></div><Tag>{selected.enabled?t('ruleSetDetails.enabled'):t('ruleSetDetails.disabled')}</Tag></div>
        <section className="risk-read-condition"><h4>{t('readWorkspace.conditions')}</h4><ReadCondition condition={selected.condition}/></section><h4>{t('readWorkspace.decision')} <Tag>{decision(selected.actionCode)}</Tag></h4>
        {selected.reasonCode&&<section className="risk-reject-panel"><div className="risk-reject-fields"><div><strong>{t('flowPanel.rejectReason')}</strong><p className="risk-read-value"><Typography.Text copyable>{selected.reasonCode}</Typography.Text></p><small className="cell-meta">{t('readWorkspace.savedReasonCode')}</small></div>
          {selected.reasonMessage&&<div><strong>{t('readWorkspace.internalMessage')}</strong><p className="risk-read-value risk-read-message">{selected.reasonMessage}</p><small className="cell-meta">{t('readWorkspace.messageAsSaved')}</small></div>}</div><p className="risk-help">{t('readWorkspace.messageHint')}</p></section>}
        {!!selected.additionalActions?.length&&<h4>{t('additionalAction.title')}</h4>}{selected.additionalActions?.map(action=><section className="risk-action-panel" key={action.id}><strong>{action.actionCode==='ADD_RISK_TAG'?t('additionalAction.addRiskTag'):action.actionCode==='APPLY_COOLDOWN'?t('additionalAction.cooldownSubject'):action.actionCode}</strong><p>{t('readWorkspace.binding',{binding:action.targetBindingCode})}</p>{action.parameters.map(parameter=><p key={parameter.parameterCode}>{t('flowPanel.outputValue',{name:parameter.parameterCode,value:parameter.values.join(t('evaluation.valueSeparator'))})}{parameter.valueType==='DURATION'?` ${t('readWorkspace.ms')}`:''}</p>)}</section>)}
        <details className="risk-description"><summary>{t('readWorkspace.description')}</summary><ConditionView condition={selected.condition}/></details></>:<p>{t('readWorkspace.noRules')}</p>}</main></div>
    <footer className="risk-read-footer"><div><span className="risk-meta">{t('readWorkspace.footerSavedBy',{actor:base?.createdBy??'',time:formatRiskTime(base?.createTime ?? null)})}</span><p className="risk-read-message">{t('readWorkspace.footerReason',{reason:editor.changeReason})}</p></div></footer>
    <Modal open={basic} onCancel={()=>setBasic(false)} title={t('readWorkspace.basicTitle')} footer={<Button onClick={()=>setBasic(false)}>{t('readWorkspace.close')}</Button>}><Descriptions column={1} items={[
      {key:'code',label:t('readWorkspace.code'),children:editor.ruleSetCode},{key:'name',label:t('ruleSetPicker.column.name'),children:editor.ruleSetName},{key:'point',label:t('fieldError.pointCode'),children:editor.pointCode},
      {key:'version',label:t('ruleSetPicker.column.version'),children:base?.versionNo},{key:'reason',label:t('fieldError.changeReason'),children:<span className="risk-read-message">{editor.changeReason}</span>},
    ]}/></Modal></section>;
}

function ReadCondition({condition}:{condition:EditorCondition}) {
  const t=useT('risk');
  if(condition.kind!=='CONDITION')return <div className="risk-read-group"><strong>{t(`readWorkspace.group.${condition.kind}`)}</strong>{condition.children.map(child=><ReadCondition key={child.id} condition={child}/>)}</div>;
  if(condition.kind==='CONDITION'&&condition.operands)return <ConditionView condition={condition}/>;
  const field=catalog.fields.find(row=>row.fieldCode===condition.fieldCode)?.label??condition.fieldCode;
  const operator=catalog.operators.find(row=>row.operator===condition.operator)?.label??condition.operator;
  const values=condition.values.join(t('evaluation.valueSeparator'));return <div className="risk-read-predicate"><span>{condition.fieldSide==='LEFT'?field:values}</span><span>{operator}</span><span>{condition.fieldSide==='LEFT'?values:field}</span></div>;
}
