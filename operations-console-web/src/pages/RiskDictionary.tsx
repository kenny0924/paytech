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

type Draft={code:string;name:string;description:string|null;defaultMessage?:string};
type Panel={mode:'CREATE'|'UPDATE'|'READ';entry:DictionaryEntry|null};
const labels={ACTIVE:'启用',DISABLED:'停用'};
const scopes={ALL:'全部配置归属',CURRENT:'当前配置',HISTORY:'历史版本',DELETED:'已删除配置'};
function fieldRule(max:number,required:boolean){return {validator:(_:unknown,value:unknown)=>{
  const text=typeof value==='string'?value:'';return (!required||!!text.trim())&&[...text].length<=max&&!/[\x00-\x08\x0b\x0c\x0e-\x1f\x7f-\x9f]/.test(text)&&!/[\uD800-\uDFFF]/u.test(text)?Promise.resolve():Promise.reject(new Error(`请输入${required?'1 至 ':''}${max}字以内的有效内容`));
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
  const {modal,message}=App.useApp();const location=useLocation();const navigate=useNavigate();
  const name=kind==='rejection-reasons'?'拒绝原因':'主体标签';const reason=kind==='rejection-reasons';
  const query=useMemo(()=>new URLSearchParams(location.search),[location.search]);
  const one=(key:string)=>query.getAll(key).length===1?query.get(key)!:'';
  const page=/^[1-9]\d*$/.test(one('page'))&&Number(one('page'))<=100000?Number(one('page')):1;
  const size=[10,20,50,100,200].includes(Number(one('size')))?Number(one('size')):10;
  const keyword=one('keyword').trim();const status=Object.hasOwn(labels,one('status'))?one('status'):'';
  const refCode=/^[A-Z][A-Z0-9_]{0,63}$/.test(one('code'))?one('code'):'';
  const references=one('view')==='references'&&!!refCode;const scope=Object.hasOwn(scopes,one('scope'))?one('scope'):'ALL';
  const [revision,setRevision]=useState(0);const [loading,setLoading]=useState(false);const [error,setError]=useState('');
  const [data,setData]=useState<PagingResponseBody<DictionaryEntry>|null>(null);const [refs,setRefs]=useState<PagingResponseBody<ConfigurationReference>|null>(null);
  const [panel,setPanel]=useState<Panel|null>(null);const [statusTarget,setStatusTarget]=useState<DictionaryEntry|null>(null);const [busy,setBusy]=useState(false);
  const [dirty,setDirty]=useState(false);const [unknown,setUnknown]=useState(false);const [conflict,setConflict]=useState(false);const [writeError,setWriteError]=useState('');const [observed,setObserved]=useState<DictionaryEntry|null|undefined>(undefined);
  const createTrigger=useRef<HTMLAnchorElement|HTMLButtonElement|null>(null);const focusCode=useRef<string|null>(null);
  const restoreTrigger=()=>{if(!mounted.current)return;const trigger=focusCode.current?Array.from(document.querySelectorAll<HTMLButtonElement>('button[aria-label]')).find(button=>button.getAttribute('aria-label')===`操作 ${focusCode.current}`&&button.getClientRects().length>0):createTrigger.current;if(trigger?.isConnected)trigger.focus({preventScroll:true});};
  const [form]=Form.useForm<Draft>();const request=useRef<AbortController|null>(null);const pending=useRef(false);const mounted=useRef(true);
  const confirmLeave=useCallback(()=>new Promise<boolean>(resolve=>{modal.confirm({title:'离开字典维护？',content:'有未保存内容或尚未人工核对的保存结果，离开将不再保留本页输入。',okText:'确认离开',cancelText:'留在当前页',onOk:()=>resolve(true),onCancel:()=>resolve(false)});}),[modal]);
  useUnsavedRuleGuard(dirty||unknown,confirmLeave);
  useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;request.current?.abort();};},[]);
  // 下拉菜单项关闭后已被移除；页面关闭状态提交后，恢复到当前列表中的触发按钮。
  const wasOpen=useRef(false);
  useEffect(()=>{const open=!!panel||!!statusTarget;const closing=wasOpen.current&&!open;wasOpen.current=open;if(closing){const frame=requestAnimationFrame(restoreTrigger);return()=>cancelAnimationFrame(frame);}},[panel,statusTarget]);
  const can=(action:'CREATE'|'UPDATE'|'STATUS')=>hasDictionaryAction(resources,kind,action);
  const report=(failure:unknown)=>{if(failure instanceof ApiError&&failure.status===403)onAuthorizationChanged();return failure instanceof ApiError?`${failure.message}${failure.requestId?` · Request ID: ${failure.requestId}`:''}`:'请求未完成，请稍后重试。';};
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
    try{await riskDictionaryApi.write(kind,operation,code,body,actorId);if(!mounted.current)return;setPanel(null);setStatusTarget(null);setDirty(false);clearWrite();setRevision(value=>value+1);void message.success('字典已保存');}
    catch(failure){if(!mounted.current)return;const result=dictionaryWriteOutcome(failure);setUnknown(result==='UNKNOWN');setConflict(result==='CONFLICT');setWriteError(result==='UNKNOWN'?`保存结果尚未确认。请按代码查询当前值、版本和更新人，核对后再操作。${failure instanceof ApiError&&failure.requestId?` · Request ID: ${failure.requestId}`:''}`:report(failure));}
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
    {key:'code',label:'编码',children:<Typography.Text copyable>{entry.code}</Typography.Text>},{key:'name',label:'名称',children:entry.name},{key:'description',label:'说明',children:entry.description??'—'},
    ...(reason?[{key:'message',label:'默认文案',children:<span className="risk-read-message">{entry.defaultMessage}</span>}]:[]),
    {key:'status',label:'状态 / 版本',children:`${labels[entry.status]} / ${entry.version}`},{key:'created',label:'创建人 / 时间',children:`${entry.createdBy} / ${formatRiskTime(entry.createTime)}`},{key:'updated',label:'更新人 / 时间',children:`${entry.updatedBy} / ${formatRiskTime(entry.updateTime)}`},]}/>;
  const feedback=<>{writeError&&<Alert type={unknown?'warning':'error'} title={writeError}/>}{(unknown||conflict)&&<Button loading={busy} onClick={()=>void refreshCurrent()}>人工查询当前值</Button>}
    {observed!==undefined&&<section className="risk-observed"><h4>当前查询结果</h4>{observed?metadata(observed):<p>当前未查到该代码；这不构成历史写入回执。</p>}
      {panel&&observed&&<Button onClick={reloadDraft}>载入当前值（替换本页草稿）</Button>}
      {unknown&&<Button onClick={()=>{setUnknown(false);setWriteError('');if(statusTarget&&observed)setStatusTarget(observed);}}>已人工核对，允许重新操作</Button>}</section>}</>;
  const pageConfig={current:page,pageSize:size,total:references?refs?.total??0:data?.total??0,showSizeChanger:true,pageSizeOptions:[10,20,50,100,200],onChange:(next:number,nextSize:number)=>go({...filters,...(references?{view:'references',code:refCode,scope}:{}),page:size===nextSize?next:1,size:nextSize})};
  return <div className="risk-rule-configuration"><div className="page-heading"><h1>{name}{references?' · 引用明细':''}</h1><Space>{references?<Button onClick={()=>go({...filters,page:one('listPage')||1,size:one('listSize')||10})}>返回字典</Button>:can('CREATE')&&<Button ref={createTrigger} type="primary" onClick={()=>void open(null,'CREATE')}>新增{name}</Button>}<Button loading={loading} onClick={()=>setRevision(value=>value+1)}>刷新</Button></Space></div>
    {error&&<Alert type="error" title={error} description="当前内容尚未刷新成功。"/>}
    <section className="table-panel">{references?<><p>字典编码：<Typography.Text copyable>{refCode}</Typography.Text></p><Select aria-label="配置归属" value={scope} options={Object.entries(scopes).map(([value,label])=>({value,label}))} onChange={scope=>go({...filters,view:'references',code:refCode,scope,page:1,size})}/>
      <Table<ConfigurationReference> rowKey={row=>'sourceType' in row?`FLOW:${row.flowVersionId}:${row.nodeKey}`:`RULE:${row.ruleSetVersionId}:${row.ruleCode}:${row.actionSequenceNo}`} dataSource={refs?.items??[]} loading={loading} pagination={pageConfig} scroll={{x:1100}} locale={{emptyText:error?'暂时无法确认引用':'暂无引用'}} columns={[
        {title:'来源',render:(_,row)=>'sourceType' in row?'决策流':'规则集'},
        {title:'配置 / 节点',render:(_,row)=>'sourceType' in row?<>{row.flowName}<div className="cell-meta">{row.flowCode} / {row.nodeName}</div></>:<>{row.ruleSetName}<div className="cell-meta">{row.ruleSetCode} / {row.ruleName}</div></>},
        {title:'版本',render:(_,row)=>`#${'sourceType' in row?row.flowVersionNo:row.versionNo}`},
        {title:'配置归属',render:(_,row)=><Tag>{('sourceType' in row?row.flowStatus:row.ruleSetStatus)==='DELETED'?'已删除配置':row.isCurrentConfiguration?'当前配置':'历史版本'}</Tag>},
        {title:'生效环境',render:(_,row)=>'sourceType' in row?'配置尚未执行':row.activeEnvironments.join('、')||'未使用'},
        {title:'主体绑定',render:(_,row)=>row.targetBindingCode??'—'},
        {title:'操作',render:(_,row)=>'sourceType' in row?<Button type="link" onClick={()=>void navigate({pathname:'/risk/decision-flows',search:flowSearch({...parseFlowNavigation(''),view:'version',pointCode:row.pointCode,flowId:row.flowId,flowVersionId:row.flowVersionId,nodeKey:row.nodeKey})})}>查看版本</Button>:row.ruleSetStatus==='DELETED'?<span>不可访问</span>:<Button type="link" onClick={()=>{const nav={...parseRiskNavigation(''),view:'version' as const,selectedRuleSetCode:row.ruleSetCode,rootId:row.ruleSetId,versionId:row.ruleSetVersionId,maxVersionNo:row.versionNo,filters:{pointCode:row.pointCode},historyPage:1,historySize:10,refPage:1,refSize:10};void navigate({pathname:'/risk/rule-configuration',search:riskNavigationSearch(nav)},{state:{riskOrigin:'dictionary',riskSource:location.pathname}});}}>查看版本</Button>},]}/></>:
      <><Form key={location.search} layout="inline" className="risk-filters" initialValues={{keyword,status:status||undefined}} onFinish={values=>go({keyword:values.keyword?.trim(),status:values.status,page:1,size})}><Form.Item label="编码 / 名称" name="keyword" rules={[fieldRule(100,false)]}><Input allowClear/></Form.Item><Form.Item label="状态" name="status"><Select allowClear placeholder="全部" options={Object.entries(labels).map(([value,label])=>({value,label}))}/></Form.Item><Button htmlType="submit">查询</Button></Form>
        <Table<DictionaryEntry> rowKey="code" dataSource={data?.items??[]} loading={loading} pagination={pageConfig} scroll={{x:1000}} locale={{emptyText:error?'暂时无法确认字典':keyword||status?'未找到匹配项':`暂无${name}`}} columns={[
          {title:'编码 / 名称',render:(_,row)=><><strong>{row.name}</strong><div><Typography.Text copyable>{row.code}</Typography.Text></div></>},{title:reason?'默认文案':'含义说明',render:(_,row)=><span className="risk-cell-description">{reason?row.defaultMessage:row.description??'—'}</span>},
          {title:'状态',render:(_,row)=><Tag color={row.status==='ACTIVE'?'green':'default'}>{labels[row.status]}</Tag>},{title:'更新人 / 时间',render:(_,row)=><>{row.updatedBy}<div className="cell-meta">{formatRiskTime(row.updateTime)}</div></>},
          {title:'操作',fixed:'right',render:(_,row)=><Dropdown autoFocus trigger={['click','hover']} menu={{items:[{key:'detail',label:'详情',onClick:()=>void open(row,'READ')},...(can('UPDATE')?[{key:'edit',label:'编辑',onClick:()=>void open(row,'UPDATE')}]:[]),{key:'refs',label:'引用关系',onClick:()=>go({...filters,view:'references',code:row.code,scope:'ALL',page:1,size:10,listPage:page,listSize:size})},...(can('STATUS')?[{key:'status',label:row.status==='ACTIVE'?'停用':'启用',onClick:()=>{focusCode.current=row.code;clearWrite();setStatusTarget(row);}}]:[])]}}><Button type="link" aria-label={`操作 ${row.code}`}>操作 ▾</Button></Dropdown>},]}/></>}
    </section>
    <Drawer onKeyDown={keepDrawerFocus} focusable={{trap:true,focusTriggerAfterClose:false}} open={!!panel} title={`${panel?.mode==='CREATE'?'新增':panel?.mode==='UPDATE'?'编辑':'详情'}${name}`} size={560} className="risk-dictionary-drawer" onClose={()=>void close()} closable={!busy} keyboard={!busy} mask={{closable:!busy}} destroyOnHidden
      extra={panel?.mode!=='READ'&&<Button type="primary" loading={busy} disabled={unknown||!panel||!can(panel.mode)} onClick={()=>form.submit()}>{panel?.mode==='CREATE'?'创建并启用':'保存'}</Button>}>
      {panel?.mode==='READ'&&panel.entry?metadata(panel.entry):<Form form={form} layout="vertical" requiredMark={false} onFinish={save} onValuesChange={()=>setDirty(true)} disabled={busy||unknown}>
        <Form.Item name="code" label="编码 *" rules={[{required:true,pattern:/^[A-Z][A-Z0-9_]{0,63}$/,message:'1–64 位大写字母、数字或下划线，以字母开头'}]}><Input readOnly={panel?.mode!=='CREATE'} autoComplete="off"/></Form.Item>
        <Form.Item name="name" label="名称 *" rules={[fieldRule(100,true)]}><Input autoComplete="off"/></Form.Item><Form.Item name="description" label={reason?'内部说明':'含义说明'} rules={[fieldRule(500,false)]}><Input.TextArea autoSize={{minRows:3,maxRows:8}}/></Form.Item>
        {reason&&<Form.Item name="defaultMessage" label="默认内部文案 *" rules={[fieldRule(500,true)]}><Input.TextArea autoSize={{minRows:3,maxRows:8}}/></Form.Item>}
        {panel?.entry&&<p>当前版本：{panel.entry.version} · {labels[panel.entry.status]}</p>}</Form>}{feedback}
    </Drawer>
    <Modal focusable={{focusTriggerAfterClose:false}} open={!!statusTarget} title={`${statusTarget?.status==='ACTIVE'?'停用':'启用'}${name}`} okText="确认" cancelText="取消" confirmLoading={busy} okButtonProps={{disabled:unknown||!can('STATUS')}} closable={!busy} keyboard={!busy} mask={{closable:!busy}} onCancel={()=>void close()}
      onOk={()=>{if(statusTarget)void write('STATUS',statusTarget.code,{expectedVersion:statusTarget.version,status:statusTarget.status==='ACTIVE'?'DISABLED':'ACTIVE'});}}>
      <p>{statusTarget?.code} · {statusTarget?.name}</p><p>{statusTarget?.status==='ACTIVE'?'停用后不能新选，已有规则配置和发布保持原状。':'启用后，后续配置可重新选择。'}</p>{feedback}
    </Modal>
  </div>;
}
