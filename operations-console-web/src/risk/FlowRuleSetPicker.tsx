import { useEffect, useState } from 'react';
import { Alert, Button, Input, Modal, Space, Table, Tag } from 'antd';
import { riskDecisionApi } from './riskDecisionApi';
import { riskDictionaryApi } from './riskDictionary';
import type { HistoryItem } from './riskDictionary';
import type { RuleSetListItem } from './riskApiModels';
import type { FlowReference } from './decisionFlow';
import { ApiError } from '../api/session';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';

/** 根列表与不可变版本历史分开查询；只把用户选定的准确版本交给流程。 */
export function FlowRuleSetPicker({pointCode,onCancel,onSelect}:{pointCode:string;onCancel:()=>void;onSelect:(reference:Omit<FlowReference,'nodeKey'>)=>void}) {
  const t=useT('risk'),tCommon=useT('common');
  const [code,setCode]=useState(''),[query,setQuery]=useState(''),[page,setPage]=useState(1),[root,setRoot]=useState<RuleSetListItem|null>(null),[historyPage,setHistoryPage]=useState(1);
  const [rows,setRows]=useState<RuleSetListItem[]>([]),[history,setHistory]=useState<HistoryItem[]>([]),[total,setTotal]=useState(0),[historyTotal,setHistoryTotal]=useState(0),[loading,setLoading]=useState(false),[error,setError]=useState(''),[revision,setRevision]=useState(0);
  useEffect(()=>{const abort=new AbortController();setLoading(true);setError('');const request=root?riskDictionaryApi.history(root.ruleSetId,{page:historyPage,size:10},root.versionNo,abort.signal).then(p=>{if(!abort.signal.aborted){setHistory(p.items);setHistoryTotal(p.total);}}):riskDecisionApi.ruleSets({page,size:10},{pointCode,...(query?{ruleSetCode:query}:{})},abort.signal).then(p=>{if(!abort.signal.aborted){setRows(p.items);setTotal(p.total);}});request.catch(e=>{if(!abort.signal.aborted)setError(e instanceof ApiError?e.message:t('ruleSetPicker.loadFailed'));}).finally(()=>{if(!abort.signal.aborted)setLoading(false);});return()=>abort.abort();},[pointCode,query,page,root,historyPage,revision]);
  async function select(version:HistoryItem){if(!root)return;setLoading(true);setError('');try{const detail=await riskDecisionApi.ruleSet(version.ruleSetVersionId);if(detail.pointCode!==pointCode)throw new Error('point mismatch');onSelect({ruleSetId:root.ruleSetId,ruleSetCode:detail.ruleSetCode,ruleSetName:detail.ruleSetName,ruleSetVersionId:detail.ruleSetVersionId,versionNo:detail.versionNo,ruleCount:detail.rules.length,ruleSetStatus:'CONFIGURABLE',versionStatus:detail.status});}catch(e){setError(e instanceof ApiError?e.message:t('ruleSetPicker.versionLoadFailed'));}finally{setLoading(false);}}
  return <Modal open width={860} title={root?t('ruleSetPicker.versionTitle',{code:root.ruleSetCode}):t('ruleSetPicker.title')} footer={null} onCancel={onCancel}>
    {error&&<Alert type="error" title={error} action={<Button onClick={()=>setRevision(v=>v+1)}>{tCommon('action.retry')}</Button>}/>}
    {root?<><Button type="link" onClick={()=>{setRoot(null);setHistoryPage(1);}}>{t('ruleSetPicker.reselect')}</Button><Table rowKey="ruleSetVersionId" loading={loading} dataSource={history} pagination={{current:historyPage,pageSize:10,total:historyTotal,onChange:setHistoryPage}} columns={[{title:t('ruleSetPicker.column.version'),dataIndex:'versionNo',render:v=>`#${v}`},{title:t('ruleSetPicker.column.name'),dataIndex:'ruleSetName'},{title:t('ruleSetPicker.column.status'),dataIndex:'status',render:v=><Tag>{enumLabel(t,'enum.ruleSetVersionStatus',v==='RETIRED'||v==='FROZEN'?v:'DRAFT')}</Tag>},{title:t('ruleSetPicker.column.actions'),render:(_,row)=><Button disabled={row.status==='RETIRED'} onClick={()=>void select(row)}>{t('ruleSetPicker.selectThisVersion')}</Button>}]}/></>:<><Space className="flow-picker-search"><Input aria-label={t('ruleSetPicker.codeSearch')} value={code} placeholder={t('ruleSetPicker.codePlaceholder')} onChange={e=>setCode(e.target.value)} onPressEnter={()=>{setQuery(code.trim());setPage(1);}}/><Button onClick={()=>{setQuery(code.trim());setPage(1);}}>{t('ruleSetPicker.search')}</Button></Space><Table rowKey="ruleSetId" loading={loading} dataSource={rows} pagination={{current:page,pageSize:10,total,onChange:setPage}} columns={[{title:t('ruleSetPicker.column.ruleSet'),render:(_,r)=><><strong>{r.ruleSetName}</strong><p>{r.ruleSetCode}</p></>},{title:t('ruleSetPicker.column.currentVersion'),render:(_,r)=>`#${r.versionNo}`},{title:t('ruleSetPicker.column.actions'),render:(_,r)=><Button onClick={()=>setRoot(r)}>{t('ruleSetPicker.selectVersion')}</Button>}]}/></>}
    <p className="cell-meta">{t('ruleSetPicker.pinnedHint')}</p>
  </Modal>;
}
