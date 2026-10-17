import { useEffect, useState } from 'react';
import { Alert, Checkbox, Form, Radio, Select } from 'antd';
import { normalizeVelocity, riskSubjects, velocityAllowsCurrent, velocityMetricOptions, velocityMetrics, velocityPreview, velocityWindowOptions, velocityWindows } from './riskVariable';
import type { FeatureCatalog, ListParameters, VelocityParameters } from './riskVariable';
import { listPurposeLabels, riskListApi } from './riskControl';
import type { RiskList } from './riskControl';

const subjectName=(value:string)=>riskSubjects.find(s=>s.value===value)?.label??value;
const metricName=(value:string)=>velocityMetrics.find(m=>m.value===value)?.label??value;
const windowName=(value:number)=>velocityWindows.find(w=>w.value===value)?.label??`${value} 秒`;
const LONGEST_WINDOW=Math.max(...velocityWindows.map(w=>w.value));
/** 目录对该主体限制了统计方式或窗口时的提示（Risk 按主体事件量限制）；不受限时为空。 */
function anchorLimitHint(catalog:FeatureCatalog,anchor:string):string {
  const options=velocityMetricOptions(catalog,anchor);
  const restricted=!options.some(m=>m.metric==='DISTINCT_COUNT')||options.some(m=>Math.max(...m.windowSeconds)<LONGEST_WINDOW);
  if(!restricted){return '';}
  const ranges=options.map(m=>`${metricName(m.metric)}最长 ${windowName(Math.max(...m.windowSeconds))}`).join('，');
  const distinct=options.some(m=>m.metric==='DISTINCT_COUNT')?'':'，不支持不同主体数';
  return `${subjectName(anchor)}事件量大，可选范围受限：${ranges}${distinct}。`;
}

/** 实时累计参数表单；可选项全部来自所选风控点的目录，创建后只读，Risk 不回显参数。 */
export function VelocityFields({catalog,value,disabled,onChange}:{catalog:FeatureCatalog;value:VelocityParameters;disabled:boolean;onChange:(v:VelocityParameters)=>void}) {
  const change=(patch:Partial<VelocityParameters>)=>onChange(normalizeVelocity(catalog,{...value,...patch}));
  const metrics=velocityMetricOptions(catalog,value.anchorSubjectType).map(m=>m.metric),windows=velocityWindowOptions(catalog,value);
  const limitHint=anchorLimitHint(catalog,value.anchorSubjectType);
  return <div className="feature-variable-fields wide">
    <h4>统计参数（创建后不可修改）</h4>
    <div className="variable-form-grid">
      <Form.Item label="统计事件" required><Select aria-label="统计事件" disabled={disabled} value={value.eventCode} options={catalog.velocityEvents.map(e=>({value:e.eventCode,label:e.eventName}))} onChange={eventCode=>change({eventCode})}/></Form.Item>
      <Form.Item label="统计主体" required><Select aria-label="统计主体" disabled={disabled} value={value.anchorSubjectType} options={catalog.velocityAnchors.map(a=>({value:a.subjectType,label:subjectName(a.subjectType)}))} onChange={anchorSubjectType=>change({anchorSubjectType})}/></Form.Item>
      <Form.Item label="统计方式" required className="wide" extra={`金额合计、最近一次金额自动设置为金额，币种取本次评估币种（${catalog.currencyVariableCode}）。${limitHint}`}><Radio.Group disabled={disabled} value={value.metric} optionType="button" options={velocityMetrics.filter(m=>metrics.includes(m.value)).map(m=>({value:m.value,label:m.label}))} onChange={e=>change({metric:e.target.value})}/></Form.Item>
      {value.metric==='DISTINCT_COUNT'&&<Form.Item label="去重主体" required extra="统计同一主体关联过的不同主体数，不能与统计主体相同。"><Select aria-label="去重主体" disabled={disabled} value={value.distinctSubjectType??undefined} placeholder="请选择" options={catalog.distinctSubjects.filter(s=>s!==value.anchorSubjectType).map(s=>({value:s,label:subjectName(s)}))} onChange={distinctSubjectType=>change({distinctSubjectType})}/></Form.Item>}
      <Form.Item label="时间窗口" required className="wide"><Radio.Group disabled={disabled} value={value.windowSeconds} optionType="button" options={velocityWindows.filter(w=>windows.includes(w.value)).map(w=>({value:w.value,label:w.label}))} onChange={e=>change({windowSeconds:e.target.value})}/></Form.Item>
      <Form.Item className="wide" extra="仅“评估”事件的次数 / 金额合计可选。"><Checkbox disabled={disabled||!velocityAllowsCurrent(catalog,value)} checked={value.includeCurrent} onChange={e=>change({includeCurrent:e.target.checked})}>计入本次</Checkbox></Form.Item>
    </div>
    <Alert type="info" showIcon title={`口径预览：${velocityPreview(catalog,value)} · 值类型：数值`}/>
  </div>;
}

/** 名单命中参数：只列本风控点可用主体的生效名单，选择名单后主体类型随名单带出。 */
export function ListFields({catalog,value,disabled,onChange}:{catalog:FeatureCatalog;value:ListParameters|null;disabled:boolean;onChange:(v:ListParameters)=>void}) {
  const [lists,setLists]=useState<RiskList[]>([]),[loaded,setLoaded]=useState(false),[error,setError]=useState('');
  useEffect(()=>{if(disabled)return;const abort=new AbortController();riskListApi.all(abort.signal).then(r=>{if(!abort.signal.aborted){setLists(r.filter(l=>l.active&&catalog.listSubjects.includes(l.subjectType)));setLoaded(true);}}).catch(e=>{if(!abort.signal.aborted)setError(e instanceof Error?e.message:'名单读取失败');});return()=>abort.abort();},[disabled,catalog]);
  const subject=value?subjectName(value.subjectType):undefined;
  return <div className="feature-variable-fields wide">
    <h4>名单参数（创建后不可修改）</h4>
    {error&&<Alert type="error" title={error}/>}
    {!disabled&&!error&&loaded&&!lists.length&&<Alert type="warning" showIcon title={`请先在【风控名单】创建 ${catalog.listSubjects.map(subjectName).join(' 或 ')} 名单`}/>}
    <div className="variable-form-grid">
      <Form.Item label="名单" required extra="来自【风控名单】；停用的名单不可选。"><Select aria-label="名单" disabled={disabled} showSearch optionFilterProp="label" value={value?.listCode} placeholder="请选择名单" options={lists.map(l=>({value:l.listCode,label:`${l.listName} · ${l.listCode} · ${listPurposeLabels[l.purpose]}`}))} onChange={listCode=>{const list=lists.find(l=>l.listCode===listCode)!;onChange({listCode,subjectType:list.subjectType});}}/></Form.Item>
      <Form.Item label="主体类型"><span>{subject??'随名单带出'}</span></Form.Item>
    </div>
    {value&&<Alert type="info" showIcon title={`口径预览：评估时本次${subject}在 ${value.listCode} 中存在生效成员时为真；主体为空时为假。规则中写“不在名单中”请用“等于 假”。`}/>}
  </div>;
}
