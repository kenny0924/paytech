import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { flowSearch, parseFlowNavigation } from './decisionFlow';
import { Alert, Button, Form, Input, Select, Space, Spin, Table, Tag } from 'antd';
import { ApiError } from '../api/session';
import type { PagingResponseBody } from '../api/types';
import { riskDictionaryApi, releaseLabel, releaseStatusCodes } from './riskDictionary';
import type { HistoryItem, ReleaseReference } from './riskDictionary';
import type { RiskNavigation } from './riskNavigation';
import { riskDecisionApi } from './riskDecisionApi';
import type { RuleSetDetail } from './riskApiModels';
import { RuleSetEditor } from './RuleSetEditor';
import { detailToEditor } from './ruleBuilder';
import { CHECKOUT_PREPARE_AUTHORING_V1 } from './riskAuthoringCatalog';
import { formatRiskTime, ruleSetStatusLabel } from './RuleSetDetails';
import { useT } from '../i18n/react';

export function RuleSetExplorer({navigation,onNavigate,onAuthorizationChanged}:{navigation:RiskNavigation;onNavigate:(next:RiskNavigation)=>void;onAuthorizationChanged:()=>void}) {
  const t=useT('risk');const navigate=useNavigate();
  const openFlow=(pointVersionId:string)=>void navigate({pathname:'/risk/decision-flows',search:flowSearch({...parseFlowNavigation(''),view:'legacy',pointVersionId})});
  const [revision,setRevision]=useState(0);const [loading,setLoading]=useState(true);const [error,setError]=useState('');
  const [history,setHistory]=useState<PagingResponseBody<HistoryItem>|null>(null);const [releases,setReleases]=useState<Awaited<ReturnType<typeof riskDictionaryApi.releases>>|null>(null);const [detail,setDetail]=useState<RuleSetDetail|null>(null);
  const [form]=Form.useForm();const root=navigation.rootId!;const view=navigation.view;
  useEffect(()=>{const abort=new AbortController();setLoading(true);setError('');
    const load=async()=>{
      if(view==='history') {const data=await riskDictionaryApi.history(root,{page:navigation.historyPage!,size:navigation.historySize!},navigation.maxVersionNo!,abort.signal);if(!abort.signal.aborted)setHistory(data);}
      else if(view==='references') {const data=await riskDictionaryApi.releases(root,{page:navigation.refPage!,size:navigation.refSize!},{...(navigation.environmentCode?{environmentCode:navigation.environmentCode}:{}),...(navigation.releaseStatus?{status:navigation.releaseStatus}:{}),...(navigation.refVersionId?{ruleSetVersionId:navigation.refVersionId}:{})},abort.signal);if(!abort.signal.aborted)setReleases(data);}
      else {const data=await riskDecisionApi.ruleSet(navigation.versionId!,abort.signal);
        const membership=await riskDictionaryApi.history(root,{page:1,size:10},data.versionNo,abort.signal);
        if(data.ruleSetCode!==navigation.selectedRuleSetCode || data.pointCode!==navigation.filters.pointCode || membership.items[0]?.ruleSetVersionId!==data.ruleSetVersionId)throw new ApiError(404,'RISK_CONFIGURATION_NOT_FOUND');
        if(!abort.signal.aborted)setDetail(data);}
    };
    load().catch(failure=>{if(abort.signal.aborted)return;if(failure instanceof ApiError&&failure.status===403)onAuthorizationChanged();setError(failure instanceof ApiError?`${failure.message}${failure.requestId?` · Request ID: ${failure.requestId}`:''}`:t('ruleSetExplorer.loadFailed'));}).finally(()=>{if(!abort.signal.aborted)setLoading(false);});
    return()=>abort.abort();
  },[navigation,root,view,revision,onAuthorizationChanged]);
  const openVersion=(row:{ruleSetVersionId:string;versionNo:number})=>onNavigate({...navigation,view:'version',versionId:row.ruleSetVersionId,maxVersionNo:Math.max(navigation.maxVersionNo??1,row.versionNo)});
  const pageConfig=(page:number,size:number,total:number,historyView:boolean)=>({current:page,pageSize:size,total,showSizeChanger:true,pageSizeOptions:[10,20,50,100,200],onChange:(next:number,nextSize:number)=>onNavigate({...navigation,...(historyView?{historyPage:size===nextSize?next:1,historySize:nextSize}:{refPage:size===nextSize?next:1,refSize:nextSize})})});
  if(view==='version')return <>{error&&<Alert type="error" title={error}/>}<Space className="risk-explorer-toolbar"><Button onClick={()=>setRevision(value=>value+1)} loading={loading}>{t('candidateList.refresh')}</Button></Space>
    {loading&&!detail?<Spin/>:detail?<RuleSetEditor mode="read" detail={detail} editor={detailToEditor(detail)} base={detail} catalog={CHECKOUT_PREPARE_AUTHORING_V1} disabled commandPending={false} saving={false} fieldErrors={[]} onChange={()=>{}} onSave={()=>{}}/>:null}</>;
  return <section className="table-panel risk-explorer"><div className="risk-editor-heading"><h2>{view==='history'?t('ruleSetList.action.history'):t('ruleSetExplorer.references')} · {navigation.selectedRuleSetCode}</h2><Space><Button loading={loading} onClick={()=>setRevision(value=>value+1)}>{t('candidateList.refresh')}</Button></Space></div>
    {error&&<Alert type="error" title={error} description={t('ruleSetExplorer.notRefreshed')}/>}
    {view==='history'?<><p>{t('ruleSetExplorer.historyHint',{maxVersionNo:navigation.maxVersionNo??''})}</p><Table<HistoryItem> rowKey="ruleSetVersionId" loading={loading} dataSource={history?.items??[]} scroll={{x:1000}} locale={{emptyText:error?t('ruleSetExplorer.historyUnknown'):t('ruleSetExplorer.historyEmpty')}} pagination={pageConfig(navigation.historyPage!,navigation.historySize!,history?.total??0,true)} columns={[
      {title:t('ruleSetPicker.column.version'),render:(_,row)=><Space>#{row.versionNo}{row.isCurrent&&<Tag color="blue">{t('ruleSetExplorer.current')}</Tag>}</Space>},{title:t('candidateList.status'),render:(_,row)=><Tag>{ruleSetStatusLabel(row.status)}</Tag>},{title:t('ruleSetExplorer.savedBy'),render:(_,row)=><>{row.createdBy}<div className="cell-meta">{formatRiskTime(row.createTime)}</div></>},
      {title:t('fieldError.changeReason'),dataIndex:'changeReason'},{title:t('candidateList.column.actions'),render:(_,row)=><Button type="link" onClick={()=>openVersion(row)}>{t('ruleSetExplorer.view')}</Button>},]}/></>:
      <><Form form={form} layout="inline" className="risk-filters" initialValues={navigation} onFinish={values=>onNavigate({...navigation,refPage:1,environmentCode:values.environmentCode,releaseStatus:values.releaseStatus,refVersionId:values.refVersionId?.trim()||undefined})}>
        <Form.Item name="environmentCode" label={t('candidate.field.environment')}><Select allowClear placeholder={t('ruleSetExplorer.all')} options={['TEST','PRODUCTION'].map(value=>({value,label:value}))}/></Form.Item><Form.Item name="releaseStatus" label={t('ruleSetExplorer.releaseStatus')}><Select allowClear placeholder={t('ruleSetExplorer.all')} options={releaseStatusCodes.map(value=>({value,label:releaseLabel(value)}))}/></Form.Item>
        <Form.Item name="refVersionId" label={t('candidateList.column.ruleSetVersionId')} rules={[{validator:(_,value)=>!value||/^[1-9]\d{0,18}$/.test(value)&&BigInt(value)<=9223372036854775807n?Promise.resolve():Promise.reject(new Error(t('ruleSetExplorer.invalidVersionId')))}]}><Input/></Form.Item><Button htmlType="submit">{t('candidateList.search')}</Button></Form>
        {releases&&<p>{t('ruleSetExplorer.inUse')}<Tag color={releases.inUse?'green':'default'}>{releases.inUse?t('evaluation.changed.yes'):t('evaluation.changed.no')}</Tag>{t('ruleSetExplorer.inUseScope')}</p>}
        <h3>{t('ruleSetExplorer.lineage')}</h3><div className="risk-lineage" aria-label={t('ruleSetExplorer.lineageLabel')}>{releases?.references.items.map(row=><div className="risk-lineage-row" key={`${row.releaseId}:${row.ruleSetVersionId}`}><div className="risk-lineage-node"><strong>{t('ruleSetExplorer.release',{releaseNo:row.releaseNo})}</strong><span>{row.environmentCode} · {releaseLabel(row.status)}</span></div><span aria-hidden="true">→</span><div className="risk-lineage-node"><strong>{row.pointCode}</strong><span>{t('ruleSetExplorer.pointVersion',{versionNo:row.pointVersionNo})}</span></div><span aria-hidden="true">→</span><Button className="risk-lineage-node" onClick={()=>openVersion(row)}>{t('ruleSetExplorer.ruleSetVersion',{versionNo:row.versionNo})}</Button></div>)}</div>
        <Table<ReleaseReference> loading={loading} rowKey={row=>`${row.releaseId}:${row.ruleSetVersionId}`} dataSource={releases?.references.items??[]} scroll={{x:900}} locale={{emptyText:error?t('ruleSetExplorer.referencesUnknown'):navigation.environmentCode||navigation.releaseStatus||navigation.refVersionId?t('ruleSetExplorer.noMatch'):t('ruleSetExplorer.noReferences')}} pagination={pageConfig(navigation.refPage!,navigation.refSize!,releases?.references.total??0,false)} columns={[
          {title:t('ruleSetExplorer.column.release'),dataIndex:'releaseNo'},{title:t('candidate.field.environment'),dataIndex:'environmentCode'},{title:t('ruleSetExplorer.releaseStatus'),render:(_,row)=><Tag color={row.status==='ACTIVE'?'green':'default'}>{releaseLabel(row.status)}</Tag>},{title:t('ruleSetExplorer.column.pointVersion'),render:(_,row)=><Space>#{row.pointVersionNo}<Button type="link" onClick={()=>openFlow(row.pointVersionId)}>{t('ruleSetExplorer.viewFlow')}</Button></Space>},{title:t('ruleSetExplorer.column.ruleSetVersion'),render:(_,row)=><Button type="link" onClick={()=>openVersion(row)}>#{row.versionNo}</Button>},{title:t('ruleSetExplorer.column.activatedAt'),render:(_,row)=>formatRiskTime(row.activatedTime)},]}/></>}
  </section>;
}
