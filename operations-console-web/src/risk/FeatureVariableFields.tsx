import { useEffect, useState } from 'react';
import { Alert, Checkbox, Form, Radio, Select } from 'antd';
import { normalizeVelocity, riskSubjects, velocityAllowsCurrent, velocityEvents, velocityMetrics, velocityPreview, velocitySubjects, velocityWindows } from './riskVariable';
import type { ListParameters, VelocityParameters } from './riskVariable';
import { listPurposeLabels, riskListApi } from './riskControl';
import type { RiskList } from './riskControl';

export const defaultVelocity:VelocityParameters={eventCode:'PAYMENT_ATTEMPT_ASSESSED',anchorSubjectType:'CARD_FINGERPRINT',metric:'COUNT',distinctSubjectType:null,windowSeconds:600,includeCurrent:false};

/** 实时累计参数表单；创建后只读，Risk 不回显参数。 */
export function VelocityFields({value,disabled,onChange}:{value:VelocityParameters;disabled:boolean;onChange:(v:VelocityParameters)=>void}) {
  const change=(patch:Partial<VelocityParameters>)=>onChange(normalizeVelocity({...value,...patch}));
  return <div className="feature-variable-fields wide">
    <h4>统计参数（创建后不可修改）</h4>
    <div className="variable-form-grid">
      <Form.Item label="统计事件" required><Select aria-label="统计事件" disabled={disabled} value={value.eventCode} options={velocityEvents.map(e=>({value:e.value,label:e.label}))} onChange={eventCode=>change({eventCode})}/></Form.Item>
      <Form.Item label="统计主体" required><Select aria-label="统计主体" disabled={disabled} value={value.anchorSubjectType} options={velocitySubjects.map(s=>({value:s.value,label:s.label}))} onChange={anchorSubjectType=>change({anchorSubjectType})}/></Form.Item>
      <Form.Item label="统计方式" required className="wide" extra="金额合计、最近一次金额自动设置为金额，币种取本次支付币种（PAY_INPUT_CURRENCY）。"><Radio.Group disabled={disabled} value={value.metric} optionType="button" options={velocityMetrics.map(m=>({value:m.value,label:m.label}))} onChange={e=>change({metric:e.target.value})}/></Form.Item>
      {value.metric==='DISTINCT_COUNT'&&<Form.Item label="去重主体" required extra="统计同一主体关联过的不同主体数，不能与统计主体相同。"><Select aria-label="去重主体" disabled={disabled} value={value.distinctSubjectType??undefined} placeholder="请选择" options={velocitySubjects.filter(s=>s.value!==value.anchorSubjectType).map(s=>({value:s.value,label:s.label}))} onChange={distinctSubjectType=>change({distinctSubjectType})}/></Form.Item>}
      <Form.Item label="时间窗口" required className="wide"><Radio.Group disabled={disabled} value={value.windowSeconds} optionType="button" options={velocityWindows.map(w=>({value:w.value,label:w.label}))} onChange={e=>change({windowSeconds:e.target.value})}/></Form.Item>
      <Form.Item className="wide" extra="仅“次数 / 金额合计”且事件为“支付尝试评估”时可选。"><Checkbox disabled={disabled||!velocityAllowsCurrent(value)} checked={value.includeCurrent} onChange={e=>change({includeCurrent:e.target.checked})}>计入本次</Checkbox></Form.Item>
    </div>
    <Alert type="info" showIcon title={`口径预览：${velocityPreview(value)} · 值类型：数值`}/>
  </div>;
}

/** 名单命中参数：选择名单后主体类型随名单带出。 */
export function ListFields({value,disabled,onChange}:{value:ListParameters|null;disabled:boolean;onChange:(v:ListParameters)=>void}) {
  const [lists,setLists]=useState<RiskList[]>([]),[error,setError]=useState('');
  useEffect(()=>{if(disabled)return;const abort=new AbortController();riskListApi.all(abort.signal).then(r=>{if(!abort.signal.aborted)setLists(r.filter(l=>l.active));}).catch(e=>{if(!abort.signal.aborted)setError(e instanceof Error?e.message:'名单读取失败');});return()=>abort.abort();},[disabled]);
  const subject=riskSubjects.find(s=>s.value===value?.subjectType)?.label??value?.subjectType;
  return <div className="feature-variable-fields wide">
    <h4>名单参数（创建后不可修改）</h4>
    {error&&<Alert type="error" title={error}/>}
    <div className="variable-form-grid">
      <Form.Item label="名单" required extra="来自【风控名单】；停用的名单不可选。"><Select aria-label="名单" disabled={disabled} showSearch optionFilterProp="label" value={value?.listCode} placeholder="请选择名单" options={lists.map(l=>({value:l.listCode,label:`${l.listName} · ${l.listCode} · ${listPurposeLabels[l.purpose]}`}))} onChange={listCode=>{const list=lists.find(l=>l.listCode===listCode)!;onChange({listCode,subjectType:list.subjectType});}}/></Form.Item>
      <Form.Item label="主体类型"><span>{subject??'随名单带出'}</span></Form.Item>
    </div>
    {value&&<Alert type="info" showIcon title={`口径预览：评估时本次${subject}在 ${value.listCode} 中存在生效成员时为真；主体为空时为假。规则中写“不在名单中”请用“等于 假”。`}/>}
  </div>;
}
