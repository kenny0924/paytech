import type { KeyboardEvent } from 'react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, App, Button, Descriptions, Drawer, Dropdown, Form, Input, Modal, Select, Space, Table, Tag, Typography } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import type { ConsoleResource, PagingResponseBody } from '../api/types';
import { ApiError } from '../api/session';
import { dictionaryWriteOutcome, hasDictionaryAction, riskDictionaryApi } from '../risk/riskDictionary';
import type { DictionaryEntry, DictionaryKind, ConfigurationReference } from '../risk/riskDictionary';
import { formatRiskTime } from '../risk/RuleSetDetails';
import { parseRiskNavigation, riskNavigationSearch } from '../risk/riskNavigation';
import { useUnsavedRuleGuard } from '../risk/useUnsavedRuleGuard';
import { flowSearch, parseFlowNavigation } from '../risk/decisionFlow';
import '../risk/riskRuleConfiguration.css';
import { enumLabel, translator } from '../i18n/index';
import { useT } from '../i18n/react';

type Draft={code:string;name:string;description:string|null;defaultMessage?:string};
type Panel={mode:'CREATE'|'UPDATE'|'READ';entry:DictionaryEntry|null};
/** 状态与配置归属显示名是 `risk.enum.dictionaryStatus.<值>`、`risk.enum.referenceScope.<值>`。 */
const statuses=['ACTIVE','DISABLED'];
const scopes=['ALL','CURRENT','HISTORY','DELETED'];
const tr=translator('risk');
function fieldRule(max:number,required:boolean){return {validator:(_:unknown,value:unknown)=>{
  const text=typeof value==='string'?value:'';return (!required||!!text.trim())&&[...text].length<=max&&!/[\x00-\x08\x0b\x0c\x0e-\x1f\x7f-\x9f]/.test(text)&&!/[\uD800-\uDFFF]/u.test(text)?Promise.resolve():Promise.reject(new Error(tr('dictionary.fieldRule',{max,required:required?'yes':'no'})));
}};}
/** 在首尾按 Tab 时直接回到抽屉内，避免原生焦点短暂落到浏览器地址栏。 */
function keepDrawerFocus(event:KeyboardEvent<HTMLDivElement>) {
  if(event.key!=='Tab'||event.ctrlKey||event.metaKey||event.altKey)return;
  const container=event.currentTarget;
  const items=Array.from(container.querySelectorAll<HTMLElement>('button:not([disabled]),input:not([disabled]),textarea:not([disabled]),select:not([disabled]),a[href],[tabindex]:not([tabindex="-1"])'))
    .filter(item=>item.getAttribute('aria-disabled')!=='true'&&item.getClientRects().length>0&&getComputedStyle(item).visibility!=='hidden');
  const first=items[0];const last=items.at(-1);const active=document.activeElement;
  if(!first){event.preventDefault();container.focus();return;}
  if(event.shiftKey&&(active===first||active===container)){event.preventDefault();last!.focus();}
  else if(!event.shiftKey&&(active===last||active===container)){event.preventDefault();first.focus();}
}
export function RiskDictionary({kind,resources,actorId,onAuthorizationChanged}:{kind:DictionaryKind;resources:readonly ConsoleResource[];actorId:string;onAuthorizationChanged:()=>void}) {
  const t=useT('risk'),tNav=useT('nav');const {modal,message}=App.useApp();const location=useLocation();const navigate=useNavigate();
  const reason=kind==='rejection-reasons';const kindParam=reason?'reason':'tag';
  const statusLabel=(value:string)=>enumLabel(t,'enum.dictionaryStatus',value);
  const query=useMemo(()=>new URLSearchParams(location.search),[location.search]);
  const one=(key:string)=>query.getAll(key).length===1?query.get(key)!:'';
  const page=/^[1-9]\d*$/.test(one('page'))&&Number(one('page'))<=100000?Number(one('page')):1;
  const size=[10,20,50,100,200].includes(Number(one('size')))?Number(one('size')):10;
  const keyword=one('keyword').trim();const status=statuses.includes(one('status'))?one('status'):'';
  const refCode=/^[A-Z][A-Z0-9_]{0,63}$/.test(one('code'))?one('code'):'';
  const references=one('view')==='references'&&!!refCode;const scope=scopes.includes(one('scope'))?one('scope'):'ALL';
  const [revision,setRevision]=useState(0);const [loading,setLoading]=useState(false);const [error,setError]=useState('');
  const [data,setData]=useState<PagingResponseBody<DictionaryEntry>|null>(null);const [refs,setRefs]=useState<PagingResponseBody<ConfigurationReference>|null>(null);
  const [panel,setPanel]=useState<Panel|null>(null);const [statusTarget,setStatusTarget]=useState<DictionaryEntry|null>(null);const [busy,setBusy]=useState(false);
  const [dirty,setDirty]=useState(false);const [unknown,setUnknown]=useState(false);const [conflict,setConflict]=useState(false);const [writeError,setWriteError]=useState('');const [observed,setObserved]=useState<DictionaryEntry|null|undefined>(undefined);
  const createTrigger=useRef<HTMLAnchorElement|HTMLButtonElement|null>(null);const focusCode=useRef<string|null>(null);
  const restoreTrigger=()=>{if(!mounted.current)return;const trigger=focusCode.current?Array.from(document.querySelectorAll<HTMLButtonElement>('button[aria-label]')).find(button=>button.getAttribute('aria-label')===t('ruleSetList.actionsFor',{code:focusCode.current})&&button.getClientRects().length>0):createTrigger.current;if(trigger?.isConnected)trigger.focus({preventScroll:true});};
  const [form]=Form.useForm<Draft>();const request=useRef<AbortController|null>(null);const pending=useRef(false);const mounted=useRef(true);
  const confirmLeave=useCallback(()=>new Promise<boolean>(resolve=>{modal.confirm({title:t('dictionary.leave.title'),content:t('dictionary.leave.content'),okText:t('rulePage.leave.ok'),cancelText:t('rulePage.leave.cancel'),onOk:()=>resolve(true),onCancel:()=>resolve(false)});}),[modal,t]);
  useUnsavedRuleGuard(dirty||unknown,confirmLeave);
  useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;request.current?.abort();};},[]);
  // 下拉菜单项关闭后已被移除；页面关闭状态提交后，恢复到当前列表中的触发按钮。
  const wasOpen=useRef(false);
  useEffect(()=>{const open=!!panel||!!statusTarget;const closing=wasOpen.current&&!open;wasOpen.current=open;if(closing){const frame=requestAnimationFrame(restoreTrigger);return()=>cancelAnimationFrame(frame);}},[panel,statusTarget]);
  const can=(action:'CREATE'|'UPDATE'|'STATUS')=>hasDictionaryAction(resources,kind,action);
  const report=(failure:unknown)=>{if(failure instanceof ApiError&&failure.status===403)onAuthorizationChanged();return failure instanceof ApiError?`${failure.message}${failure.requestId?` · Request ID: ${failure.requestId}`:''}`:t('dictionary.requestFailed');};
  useEffect(()=>{const abort=new AbortController();setLoading(true);setError('');
    const load=references?riskDictionaryApi.references(kind,refCode,{page,size},scope,abort.signal).then(value=>{if(!abort.signal.aborted)setRefs(value);}):riskDictionaryApi.list(kind,{page,size},{...(keyword?{keyword}:{}),...(status?{status}:{})},abort.signal).then(value=>{if(!abort.signal.aborted)setData(value);});
    load.catch(failure=>{if(!abort.signal.aborted)setError(report(failure));}).finally(()=>{if(!abort.signal.aborted)setLoading(false);});return()=>abort.abort();
  // 查询状态由 URL 唯一持有；过期请求不能覆盖新筛选。
  // eslint-disable-next-line react-hooks/exhaustive-deps
  },[kind,location.search,revision]);
  const go=(values:Record<string,string|number|undefined>)=>{const search=new URLSearchParams();if(values.view==='references'){search.set('listPage',one('listPage')||String(page));search.set('listSize',one('listSize')||String(size));}for(const [key,value]of Object.entries(values))if(value!==undefined&&value!=='')search.set(key,String(value));void navigate({search:`?${search}`});};
  const filters={...(keyword?{keyword}:{}),...(status?{status}:{})};
  function clearWrite(){setWriteError('');setUnknown(false);setConflict(false);setObserved(undefined);}
  async function close(){if(busy)return;if((dirty||unknown)&&!await confirmLeave())return;setPanel(null);setStatusTarget(null);setDirty(false);clearWrite();}
  async function open(entry:DictionaryEntry|null,mode:Panel['mode']) {
    focusCode.current=entry?.code??null;request.current?.abort();clearWrite();setDirty(false);
    if(!entry){form.setFieldsValue({code:'',name:'',description:null,defaultMessage:''});setPanel({mode,entry:null});return;}
    const abort=new AbortController();request.current=abort;setLoading(true);
    try{const current=await riskDictionaryApi.detail(kind,entry.code,abort.signal);if(abort.signal.aborted)return;form.setFieldsValue(current);setPanel({mode,entry:current});}
    catch(failure){if(!abort.signal.aborted)setError(report(failure));}finally{if(!abort.signal.aborted)setLoading(false);}
  }
  async function write(operation:'CREATE'|'UPDATE'|'STATUS',code:string,body:Record<string,unknown>){
    if(pending.current||unknown||!can(operation))return;pending.current=true;setBusy(true);setWriteError('');setObserved(undefined);
    try{await riskDictionaryApi.write(kind,operation,code,body,actorId);if(!mounted.current)return;setPanel(null);setStatusTarget(null);setDirty(false);clearWrite();setRevision(value=>value+1);void message.success(t('dictionary.saved'));}
    catch(failure){if(!mounted.current)return;const result=dictionaryWriteOutcome(failure);setUnknown(result==='UNKNOWN');setConflict(result==='CONFLICT');setWriteError(result==='UNKNOWN'?`${t('dictionary.unknownResult')}${failure instanceof ApiError&&failure.requestId?` · Request ID: ${failure.requestId}`:''}`:report(failure));}
    finally{pending.current=false;if(mounted.current)setBusy(false);}
  }
  function save(values:Draft){if(!panel||panel.mode==='READ')return;
    const body={name:values.name.trim(),description:values.description||null,...(reason?{defaultMessage:values.defaultMessage}:{})};
    void write(panel.mode,values.code,panel.mode==='CREATE'?{...body,[reason?'reasonCode':'tagCode']:values.code}:{...body,expectedVersion:panel.entry!.version});
  }
  async function refreshCurrent(){const code=statusTarget?.code??panel?.entry?.code??form.getFieldValue('code');if(!code||busy)return;setBusy(true);
    const abort=new AbortController();request.current?.abort();request.current=abort;
    try{const current=await riskDictionaryApi.detail(kind,code,abort.signal);if(!abort.signal.aborted)setObserved(current);}catch(failure){if(abort.signal.aborted)return;if(failure instanceof ApiError&&failure.status===404&&failure.code==='RISK_DICTIONARY_NOT_FOUND')setObserved(null);else setWriteError(report(failure));}finally{if(mounted.current)setBusy(false);}}
  function reloadDraft(){if(!observed||!panel)return;form.setFieldsValue(observed);setPanel({...panel,mode:panel.mode==='CREATE'?'UPDATE':panel.mode,entry:observed});setDirty(false);clearWrite();}
  const metadata=(entry:DictionaryEntry)=><Descriptions size="small" column={1} items={[
    {key:'code',label:t('readWorkspace.code'),children:<Typography.Text copyable>{entry.code}</Typography.Text>},{key:'name',label:t('ruleSetPicker.column.name'),children:entry.name},{key:'description',label:t('dictionary.description'),children:entry.description??'—'},
    ...(reason?[{key:'message',label:t('dictionary.defaultMessage'),children:<span className="risk-read-message">{entry.defaultMessage}</span>}]:[]),
    {key:'status',label:t('candidate.field.statusVersion'),children:`${statusLabel(entry.status)} / ${entry.version}`},{key:'created',label:t('candidate.field.created'),children:`${entry.createdBy} / ${formatRiskTime(entry.createTime)}`},{key:'updated',label:t('dictionary.updated'),children:`${entry.updatedBy} / ${formatRiskTime(entry.updateTime)}`},]}/>;
  const feedback=<>{writeError&&<Alert type={unknown?'warning':'error'} title={writeError}/>}{(unknown||conflict)&&<Button loading={busy} onClick={()=>void refreshCurrent()}>{t('dictionary.queryCurrent')}</Button>}
    {observed!==undefined&&<section className="risk-observed"><h4>{t('dictionary.currentResult')}</h4>{observed?metadata(observed):<p>{t('dictionary.notFound')}</p>}
      {panel&&observed&&<Button onClick={reloadDraft}>{t('dictionary.loadCurrent')}</Button>}
      {unknown&&<Button onClick={()=>{setUnknown(false);setWriteError('');if(statusTarget&&observed)setStatusTarget(observed);}}>{t('dictionary.verified')}</Button>}</section>}</>;
  const pageConfig={current:page,pageSize:size,total:references?refs?.total??0:data?.total??0,showSizeChanger:true,pageSizeOptions:[10,20,50,100,200],onChange:(next:number,nextSize:number)=>go({...filters,...(references?{view:'references',code:refCode,scope}:{}),page:size===nextSize?next:1,size:nextSize})};
  return <div className="risk-rule-configuration"><div className="page-heading"><h1>{tNav(reason?'resource.OPERATIONS_RISK_REJECTION_REASONS':'resource.OPERATIONS_RISK_SUBJECT_TAGS')}{references?` · ${t('dictionary.referenceDetail')}`:''}</h1><Space>{references?<Button onClick={()=>go({...filters,page:one('listPage')||1,size:one('listSize')||10})}>{t('dictionary.back')}</Button>:can('CREATE')&&<Button ref={createTrigger} type="primary" onClick={()=>void open(null,'CREATE')}>{t('dictionary.create',{kind:kindParam})}</Button>}<Button loading={loading} onClick={()=>setRevision(value=>value+1)}>{t('candidateList.refresh')}</Button></Space></div>
    {error&&<Alert type="error" title={error} description={t('ruleSetExplorer.notRefreshed')}/>}
    <section className="table-panel">{references?<><p>{t('dictionary.code')}<Typography.Text copyable>{refCode}</Typography.Text></p><Select aria-label={t('dictionary.scope')} value={scope} options={scopes.map(value=>({value,label:enumLabel(t,'enum.referenceScope',value)}))} onChange={scope=>go({...filters,view:'references',code:refCode,scope,page:1,size})}/>
      <Table<ConfigurationReference> rowKey={row=>'sourceType' in row?`FLOW:${row.flowVersionId}:${row.nodeKey}`:`RULE:${row.ruleSetVersionId}:${row.ruleCode}:${row.actionSequenceNo}`} dataSource={refs?.items??[]} loading={loading} pagination={pageConfig} scroll={{x:1100}} locale={{emptyText:error?t('dictionary.referencesUnknown'):t('dictionary.noReferences')}} columns={[
        {title:t('dictionary.column.source'),render:(_,row)=>'sourceType' in row?t('dictionary.source.flow'):t('dictionary.source.ruleSet')},
        {title:t('dictionary.column.configNode'),render:(_,row)=>'sourceType' in row?<>{row.flowName}<div className="cell-meta">{row.flowCode} / {row.nodeName}</div></>:<>{row.ruleSetName}<div className="cell-meta">{row.ruleSetCode} / {row.ruleName}</div></>},
        {title:t('ruleSetPicker.column.version'),render:(_,row)=>`#${'sourceType' in row?row.flowVersionNo:row.versionNo}`},
        {title:t('dictionary.scope'),render:(_,row)=><Tag>{enumLabel(t,'enum.referenceScope',('sourceType' in row?row.flowStatus:row.ruleSetStatus)==='DELETED'?'DELETED':row.isCurrentConfiguration?'CURRENT':'HISTORY')}</Tag>},
        {title:t('dictionary.column.environments'),render:(_,row)=>'sourceType' in row?t('dictionary.notExecuted'):row.activeEnvironments.join(t('evaluation.valueSeparator'))||t('dictionary.unused')},
        {title:t('dictionary.column.binding'),render:(_,row)=>row.targetBindingCode??'—'},
        {title:t('candidateList.column.actions'),render:(_,row)=>'sourceType' in row?<Button type="link" onClick={()=>void navigate({pathname:'/risk/decision-flows',search:flowSearch({...parseFlowNavigation(''),view:'version',pointCode:row.pointCode,flowId:row.flowId,flowVersionId:row.flowVersionId,nodeKey:row.nodeKey})})}>{t('dictionary.viewVersion')}</Button>:row.ruleSetStatus==='DELETED'?<span>{t('dictionary.inaccessible')}</span>:<Button type="link" onClick={()=>{const nav={...parseRiskNavigation(''),view:'version' as const,selectedRuleSetCode:row.ruleSetCode,rootId:row.ruleSetId,versionId:row.ruleSetVersionId,maxVersionNo:row.versionNo,filters:{pointCode:row.pointCode},historyPage:1,historySize:10,refPage:1,refSize:10};void navigate({pathname:'/risk/rule-configuration',search:riskNavigationSearch(nav)},{state:{riskOrigin:'dictionary',riskSource:location.pathname}});}}>{t('dictionary.viewVersion')}</Button>},]}/></>:
      <><Form key={location.search} layout="inline" className="risk-filters" initialValues={{keyword,status:status||undefined}} onFinish={values=>go({keyword:values.keyword?.trim(),status:values.status,page:1,size})}><Form.Item label={t('dictionary.codeName')} name="keyword" rules={[fieldRule(100,false)]}><Input allowClear/></Form.Item><Form.Item label={t('candidateList.status')} name="status"><Select allowClear placeholder={t('ruleSetExplorer.all')} options={statuses.map(value=>({value,label:statusLabel(value)}))}/></Form.Item><Button htmlType="submit">{t('candidateList.search')}</Button></Form>
        <Table<DictionaryEntry> rowKey="code" dataSource={data?.items??[]} loading={loading} pagination={pageConfig} scroll={{x:1000}} locale={{emptyText:error?t('dictionary.unknown'):keyword||status?t('ruleSetExplorer.noMatch'):t('dictionary.empty',{kind:kindParam})}} columns={[
          {title:t('dictionary.codeName'),render:(_,row)=><><strong>{row.name}</strong><div><Typography.Text copyable>{row.code}</Typography.Text></div></>},{title:reason?t('dictionary.defaultMessage'):t('dictionary.meaning'),render:(_,row)=><span className="risk-cell-description">{reason?row.defaultMessage:row.description??'—'}</span>},
          {title:t('candidateList.status'),render:(_,row)=><Tag color={row.status==='ACTIVE'?'green':'default'}>{statusLabel(row.status)}</Tag>},{title:t('dictionary.updated'),render:(_,row)=><>{row.updatedBy}<div className="cell-meta">{formatRiskTime(row.updateTime)}</div></>},
          {title:t('candidateList.column.actions'),fixed:'right',render:(_,row)=><Dropdown autoFocus trigger={['click','hover']} menu={{items:[{key:'detail',label:t('ruleSetList.action.detail'),onClick:()=>void open(row,'READ')},...(can('UPDATE')?[{key:'edit',label:t('ruleSetList.action.edit'),onClick:()=>void open(row,'UPDATE')}]:[]),{key:'refs',label:t('ruleSetList.action.references'),onClick:()=>go({...filters,view:'references',code:row.code,scope:'ALL',page:1,size:10,listPage:page,listSize:size})},...(can('STATUS')?[{key:'status',label:row.status==='ACTIVE'?t('dictionary.disable'):t('dictionary.enable'),onClick:()=>{focusCode.current=row.code;clearWrite();setStatusTarget(row);}}]:[])]}}><Button type="link" aria-label={t('ruleSetList.actionsFor',{code:row.code})}>{t('ruleSetList.actions')}</Button></Dropdown>},]}/></>}
    </section>
    <Drawer onKeyDown={keepDrawerFocus} focusable={{trap:true,focusTriggerAfterClose:false}} open={!!panel} title={t('dictionary.drawerTitle',{mode:panel?.mode==='CREATE'?'create':panel?.mode==='UPDATE'?'update':'read',kind:kindParam})} size={560} className="risk-dictionary-drawer" onClose={()=>void close()} closable={!busy} keyboard={!busy} mask={{closable:!busy}} destroyOnHidden
      extra={panel?.mode!=='READ'&&<Button type="primary" loading={busy} disabled={unknown||!panel||!can(panel.mode)} onClick={()=>form.submit()}>{panel?.mode==='CREATE'?t('dictionary.createAndEnable'):t('dictionary.save')}</Button>}>
      {panel?.mode==='READ'&&panel.entry?metadata(panel.entry):<Form form={form} layout="vertical" requiredMark={false} onFinish={save} onValuesChange={()=>setDirty(true)} disabled={busy||unknown}>
        <Form.Item name="code" label={t('dictionary.codeRequired')} rules={[{required:true,pattern:/^[A-Z][A-Z0-9_]{0,63}$/,message:t('dictionary.codePattern')}]}><Input readOnly={panel?.mode!=='CREATE'} autoComplete="off"/></Form.Item>
        <Form.Item name="name" label={t('dictionary.nameRequired')} rules={[fieldRule(100,true)]}><Input autoComplete="off"/></Form.Item><Form.Item name="description" label={reason?t('dictionary.internalDescription'):t('dictionary.meaning')} rules={[fieldRule(500,false)]}><Input.TextArea autoSize={{minRows:3,maxRows:8}}/></Form.Item>
        {reason&&<Form.Item name="defaultMessage" label={t('dictionary.defaultMessageRequired')} rules={[fieldRule(500,true)]}><Input.TextArea autoSize={{minRows:3,maxRows:8}}/></Form.Item>}
        {panel?.entry&&<p>{t('dictionary.currentVersion',{version:panel.entry.version,status:statusLabel(panel.entry.status)})}</p>}</Form>}{feedback}
    </Drawer>
    <Modal focusable={{focusTriggerAfterClose:false}} open={!!statusTarget} title={t('dictionary.statusTitle',{action:statusTarget?.status==='ACTIVE'?'disable':'enable',kind:kindParam})} okText={t('dictionary.confirm')} cancelText={t('ruleEditor.cancel')} confirmLoading={busy} okButtonProps={{disabled:unknown||!can('STATUS')}} closable={!busy} keyboard={!busy} mask={{closable:!busy}} onCancel={()=>void close()}
      onOk={()=>{if(statusTarget)void write('STATUS',statusTarget.code,{expectedVersion:statusTarget.version,status:statusTarget.status==='ACTIVE'?'DISABLED':'ACTIVE'});}}>
      <p>{statusTarget?.code} · {statusTarget?.name}</p><p>{statusTarget?.status==='ACTIVE'?t('dictionary.disableHint'):t('dictionary.enableHint')}</p>{feedback}
    </Modal>
  </div>;
}
