import { useEffect, useState } from 'react';
import { Alert, Checkbox, Form, Radio, Select } from 'antd';
import { normalizeVelocity, subjectLabel, velocityAllowsCurrent, velocityMetricLabel, velocityMetricOptions, velocityMetrics, velocityPreview, velocityWindowLabel, velocityWindowOptions, velocityWindows } from './riskVariable';
import type { FeatureCatalog, ListParameters, VelocityParameters } from './riskVariable';
import { listPurposeLabel, riskListApi } from './riskControl';
import type { RiskList } from './riskControl';
import { translator } from '../i18n/index';

const t=translator('risk');
/** 目录对该主体限制了统计方式或窗口时的提示（Risk 按主体事件量限制）：某统计方式的最长窗口短于目录中同统计方式的最长窗口即受限；不受限时为空。 */
function anchorLimitHint(catalog:FeatureCatalog,anchor:string):string {
  const options=velocityMetricOptions(catalog,anchor);
  const longest=(metric:string)=>Math.max(...catalog.velocityAnchors.flatMap(a=>a.metrics.filter(m=>m.metric===metric).flatMap(m=>m.windowSeconds)));
  const restricted=!options.some(m=>m.metric==='DISTINCT_COUNT')||options.some(m=>Math.max(...m.windowSeconds)<longest(m.metric));
  if(!restricted){return '';}
  const ranges=options.map(m=>t('feature.rangeItem',{metric:velocityMetricLabel(m.metric),window:velocityWindowLabel(Math.max(...m.windowSeconds),m.metric)})).join(t('feature.rangeSeparator'));
  const distinct=options.some(m=>m.metric==='DISTINCT_COUNT')?'supported':'unsupported';
  return t('feature.limitHint',{subject:subjectLabel(anchor),ranges,distinct});
}

/** 实时累计参数表单；可选项全部来自所选风控点的目录，创建后只读，Risk 不回显参数。 */
export function VelocityFields({catalog,value,disabled,onChange}:{catalog:FeatureCatalog;value:VelocityParameters;disabled:boolean;onChange:(v:VelocityParameters)=>void}) {
  const change=(patch:Partial<VelocityParameters>)=>onChange(normalizeVelocity(catalog,{...value,...patch}));
  const metrics=velocityMetricOptions(catalog,value.anchorSubjectType).map(m=>m.metric),windows=velocityWindowOptions(catalog,value);
  const limitHint=anchorLimitHint(catalog,value.anchorSubjectType);
  return <div className="feature-variable-fields wide">
    <h4>{t('feature.velocityTitle')}</h4>
    <div className="variable-form-grid">
      <Form.Item label={t('feature.event')} required><Select aria-label={t('feature.event')} disabled={disabled} value={value.eventCode} options={catalog.velocityEvents.map(e=>({value:e.eventCode,label:e.eventName}))} onChange={eventCode=>change({eventCode})}/></Form.Item>
      <Form.Item label={t('feature.anchor')} required><Select aria-label={t('feature.anchor')} disabled={disabled} value={value.anchorSubjectType} options={catalog.velocityAnchors.map(a=>({value:a.subjectType,label:subjectLabel(a.subjectType)}))} onChange={anchorSubjectType=>change({anchorSubjectType})}/></Form.Item>
      <Form.Item label={t('feature.metric')} required className="wide" extra={t('feature.metricHint',{currencyVariable:catalog.currencyVariableCode,limitHint})}><Radio.Group disabled={disabled} value={value.metric} optionType="button" options={velocityMetrics.filter(m=>metrics.includes(m)).map(m=>({value:m,label:velocityMetricLabel(m)}))} onChange={e=>change({metric:e.target.value})}/></Form.Item>
      {value.metric==='DISTINCT_COUNT'&&<Form.Item label={t('feature.distinctSubject')} required extra={t('feature.distinctSubjectHint')}><Select aria-label={t('feature.distinctSubject')} disabled={disabled} value={value.distinctSubjectType??undefined} placeholder={t('feature.select')} options={catalog.distinctSubjects.filter(s=>s!==value.anchorSubjectType).map(s=>({value:s,label:subjectLabel(s)}))} onChange={distinctSubjectType=>change({distinctSubjectType})}/></Form.Item>}
      <Form.Item label={t('feature.window')} required className="wide"><Radio.Group disabled={disabled} value={value.windowSeconds} optionType="button" options={velocityWindows.filter(w=>windows.includes(w)).map(w=>({value:w,label:velocityWindowLabel(w,value.metric)}))} onChange={e=>change({windowSeconds:e.target.value})}/></Form.Item>
      <Form.Item className="wide" extra={t('feature.includeCurrentHint')}><Checkbox disabled={disabled||!velocityAllowsCurrent(catalog,value)} checked={value.includeCurrent} onChange={e=>change({includeCurrent:e.target.checked})}>{t('feature.includeCurrent')}</Checkbox></Form.Item>
    </div>
    <Alert type="info" showIcon title={t('feature.velocityPreview',{preview:velocityPreview(catalog,value)})}/>
  </div>;
}

/** 名单命中参数：只列本风控点可用主体的生效名单，选择名单后主体类型随名单带出。 */
export function ListFields({catalog,value,disabled,onChange}:{catalog:FeatureCatalog;value:ListParameters|null;disabled:boolean;onChange:(v:ListParameters)=>void}) {
  const [lists,setLists]=useState<RiskList[]>([]),[loaded,setLoaded]=useState(false),[error,setError]=useState('');
  useEffect(()=>{if(disabled)return;const abort=new AbortController();riskListApi.all(abort.signal).then(r=>{if(!abort.signal.aborted){setLists(r.filter(l=>l.active&&catalog.listSubjects.includes(l.subjectType)));setLoaded(true);}}).catch(e=>{if(!abort.signal.aborted)setError(e instanceof Error?e.message:t('feature.list.loadFailed'));});return()=>abort.abort();},[disabled,catalog]);
  const subject=value?subjectLabel(value.subjectType):undefined;
  return <div className="feature-variable-fields wide">
    <h4>{t('feature.list.title')}</h4>
    {error&&<Alert type="error" title={error}/>}
    {!disabled&&!error&&loaded&&!lists.length&&<Alert type="warning" showIcon title={t('feature.list.createFirst',{subjects:catalog.listSubjects.map(subjectLabel).join(t('feature.list.or'))})}/>}
    <div className="variable-form-grid">
      <Form.Item label={t('feature.list.list')} required extra={t('feature.list.listHint')}><Select aria-label={t('feature.list.list')} disabled={disabled} showSearch optionFilterProp="label" value={value?.listCode} placeholder={t('feature.list.placeholder')} options={lists.map(l=>({value:l.listCode,label:`${l.listName} · ${l.listCode} · ${listPurposeLabel(l.purpose)}`}))} onChange={listCode=>{const list=lists.find(l=>l.listCode===listCode)!;onChange({listCode,subjectType:list.subjectType});}}/></Form.Item>
      <Form.Item label={t('feature.list.subjectType')}><span>{subject??t('feature.list.fromList')}</span></Form.Item>
    </div>
    {value&&<Alert type="info" showIcon title={t('feature.list.preview',{subject:subject??'',listCode:value.listCode})}/>}
  </div>;
}
