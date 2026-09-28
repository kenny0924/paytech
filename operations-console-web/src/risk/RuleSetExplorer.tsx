import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { flowSearch, parseFlowNavigation } from './decisionFlow';
import { Alert, Button, Form, Input, Select, Space, Spin, Table, Tag } from 'antd';
import { ApiError } from '../api/session';
import type { PagingResponseBody } from '../api/types';
import { riskDictionaryApi, releaseLabels } from './riskDictionary';
import type { HistoryItem, ReleaseReference } from './riskDictionary';
import type { RiskNavigation } from './riskNavigation';
import { riskDecisionApi } from './riskDecisionApi';
import type { RuleSetDetail } from './riskApiModels';
import { RuleSetEditor } from './RuleSetEditor';
import { detailToEditor } from './ruleBuilder';
import { CHECKOUT_PREPARE_AUTHORING_V1 } from './riskAuthoringCatalog';
import { formatRiskTime, ruleSetStatusLabels } from './RuleSetDetails';

export function RuleSetExplorer({navigation,onNavigate,onAuthorizationChanged}:{navigation:RiskNavigation;onNavigate:(next:RiskNavigation)=>void;onAuthorizationChanged:()=>void}) {
  const navigate=useNavigate();
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
    load().catch(failure=>{if(abort.signal.aborted)return;if(failure instanceof ApiError&&failure.status===403)onAuthorizationChanged();setError(failure instanceof ApiError?`${failure.message}${failure.requestId?` · Request ID: ${failure.requestId}`:''}`:'暂时无法读取数据，请重试。');}).finally(()=>{if(!abort.signal.aborted)setLoading(false);});
    return()=>abort.abort();
  },[navigation,root,view,revision,onAuthorizationChanged]);
  const openVersion=(row:{ruleSetVersionId:string;versionNo:number})=>onNavigate({...navigation,view:'version',versionId:row.ruleSetVersionId,maxVersionNo:Math.max(navigation.maxVersionNo??1,row.versionNo)});
  const pageConfig=(page:number,size:number,total:number,historyView:boolean)=>({current:page,pageSize:size,total,showSizeChanger:true,pageSizeOptions:[10,20,50,100,200],onChange:(next:number,nextSize:number)=>onNavigate({...navigation,...(historyView?{historyPage:size===nextSize?next:1,historySize:nextSize}:{refPage:size===nextSize?next:1,refSize:nextSize})})});
  if(view==='version')return <>{error&&<Alert type="error" title={error}/>}<Space className="risk-explorer-toolbar"><Button onClick={()=>setRevision(value=>value+1)} loading={loading}>刷新</Button></Space>
    {loading&&!detail?<Spin/>:detail?<RuleSetEditor mode="read" detail={detail} editor={detailToEditor(detail)} base={detail} catalog={CHECKOUT_PREPARE_AUTHORING_V1} disabled commandPending={false} saving={false} fieldErrors={[]} onChange={()=>{}} onSave={()=>{}}/>:null}</>;
  return <section className="table-panel risk-explorer"><div className="risk-editor-heading"><h2>{view==='history'?'历史版本':'发布引用关系'} · {navigation.selectedRuleSetCode}</h2><Space><Button loading={loading} onClick={()=>setRevision(value=>value+1)}>刷新</Button></Space></div>
    {error&&<Alert type="error" title={error} description="当前内容尚未刷新成功。"/>}
    {view==='history'?<><p>浏览上界 #{navigation.maxVersionNo} · 历史版本按保存顺序倒序展示</p><Table<HistoryItem> rowKey="ruleSetVersionId" loading={loading} dataSource={history?.items??[]} scroll={{x:1000}} locale={{emptyText:error?'暂时无法确认历史版本':'暂无历史版本'}} pagination={pageConfig(navigation.historyPage!,navigation.historySize!,history?.total??0,true)} columns={[
      {title:'版本',render:(_,row)=><Space>#{row.versionNo}{row.isCurrent&&<Tag color="blue">当前配置</Tag>}</Space>},{title:'状态',render:(_,row)=><Tag>{ruleSetStatusLabels[row.status]}</Tag>},{title:'保存人 / 时间',render:(_,row)=><>{row.createdBy}<div className="cell-meta">{formatRiskTime(row.createTime)}</div></>},
      {title:'变更原因',dataIndex:'changeReason'},{title:'操作',render:(_,row)=><Button type="link" onClick={()=>openVersion(row)}>查看</Button>},]}/></>:
      <><Form form={form} layout="inline" className="risk-filters" initialValues={navigation} onFinish={values=>onNavigate({...navigation,refPage:1,environmentCode:values.environmentCode,releaseStatus:values.releaseStatus,refVersionId:values.refVersionId?.trim()||undefined})}>
        <Form.Item name="environmentCode" label="环境"><Select allowClear placeholder="全部" options={['TEST','PRODUCTION'].map(value=>({value,label:value}))}/></Form.Item><Form.Item name="releaseStatus" label="发布状态"><Select allowClear placeholder="全部" options={Object.entries(releaseLabels).map(([value,label])=>({value,label}))}/></Form.Item>
        <Form.Item name="refVersionId" label="规则集版本 ID" rules={[{validator:(_,value)=>!value||/^[1-9]\d{0,18}$/.test(value)&&BigInt(value)<=9223372036854775807n?Promise.resolve():Promise.reject(new Error('请输入有效的版本 ID'))}]}><Input/></Form.Item><Button htmlType="submit">查询</Button></Form>
        {releases&&<p>当前被使用：<Tag color={releases.inUse?'green':'default'}>{releases.inUse?'是':'否'}</Tag>（所有版本与环境）</p>}
        <h3>当前页关系图</h3><div className="risk-lineage" aria-label="当前页发布引用关系">{releases?.references.items.map(row=><div className="risk-lineage-row" key={`${row.releaseId}:${row.ruleSetVersionId}`}><div className="risk-lineage-node"><strong>发布 {row.releaseNo}</strong><span>{row.environmentCode} · {releaseLabels[row.status]}</span></div><span aria-hidden="true">→</span><div className="risk-lineage-node"><strong>{row.pointCode}</strong><span>决策点版本 #{row.pointVersionNo}</span></div><span aria-hidden="true">→</span><Button className="risk-lineage-node" onClick={()=>openVersion(row)}>规则集版本 #{row.versionNo}</Button></div>)}</div>
        <Table<ReleaseReference> loading={loading} rowKey={row=>`${row.releaseId}:${row.ruleSetVersionId}`} dataSource={releases?.references.items??[]} scroll={{x:900}} locale={{emptyText:error?'暂时无法确认引用关系':navigation.environmentCode||navigation.releaseStatus||navigation.refVersionId?'未找到匹配项':'暂无发布引用'}} pagination={pageConfig(navigation.refPage!,navigation.refSize!,releases?.references.total??0,false)} columns={[
          {title:'发布',dataIndex:'releaseNo'},{title:'环境',dataIndex:'environmentCode'},{title:'发布状态',render:(_,row)=><Tag color={row.status==='ACTIVE'?'green':'default'}>{releaseLabels[row.status]}</Tag>},{title:'决策点版本',render:(_,row)=><Space>#{row.pointVersionNo}<Button type="link" onClick={()=>openFlow(row.pointVersionId)}>查看编排</Button></Space>},{title:'规则集版本',render:(_,row)=><Button type="link" onClick={()=>openVersion(row)}>#{row.versionNo}</Button>},{title:'生效时间',render:(_,row)=>formatRiskTime(row.activatedTime)},]}/></>}
  </section>;
}
