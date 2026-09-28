import { useEffect, useState } from 'react';
import { Alert, Button, Input, Modal, Space, Table, Tag } from 'antd';
import { riskDecisionApi } from './riskDecisionApi';
import { riskDictionaryApi } from './riskDictionary';
import type { HistoryItem } from './riskDictionary';
import type { RuleSetListItem } from './riskApiModels';
import type { FlowReference } from './decisionFlow';
import { ApiError } from '../api/session';

/** 根列表与不可变版本历史分开查询；只把用户选定的准确版本交给流程。 */
export function FlowRuleSetPicker({pointCode,onCancel,onSelect}:{pointCode:string;onCancel:()=>void;onSelect:(reference:Omit<FlowReference,'nodeKey'>)=>void}) {
  const [code,setCode]=useState(''),[query,setQuery]=useState(''),[page,setPage]=useState(1),[root,setRoot]=useState<RuleSetListItem|null>(null),[historyPage,setHistoryPage]=useState(1);
  const [rows,setRows]=useState<RuleSetListItem[]>([]),[history,setHistory]=useState<HistoryItem[]>([]),[total,setTotal]=useState(0),[historyTotal,setHistoryTotal]=useState(0),[loading,setLoading]=useState(false),[error,setError]=useState(''),[revision,setRevision]=useState(0);
  useEffect(()=>{const abort=new AbortController();setLoading(true);setError('');const request=root?riskDictionaryApi.history(root.ruleSetId,{page:historyPage,size:10},root.versionNo,abort.signal).then(p=>{if(!abort.signal.aborted){setHistory(p.items);setHistoryTotal(p.total);}}):riskDecisionApi.ruleSets({page,size:10},{pointCode,...(query?{ruleSetCode:query}:{})},abort.signal).then(p=>{if(!abort.signal.aborted){setRows(p.items);setTotal(p.total);}});request.catch(e=>{if(!abort.signal.aborted)setError(e instanceof ApiError?e.message:'规则版本读取失败。');}).finally(()=>{if(!abort.signal.aborted)setLoading(false);});return()=>abort.abort();},[pointCode,query,page,root,historyPage,revision]);
  async function select(version:HistoryItem){if(!root)return;setLoading(true);setError('');try{const detail=await riskDecisionApi.ruleSet(version.ruleSetVersionId);if(detail.pointCode!==pointCode)throw new Error('point mismatch');onSelect({ruleSetId:root.ruleSetId,ruleSetCode:detail.ruleSetCode,ruleSetName:detail.ruleSetName,ruleSetVersionId:detail.ruleSetVersionId,versionNo:detail.versionNo,ruleCount:detail.rules.length,ruleSetStatus:'CONFIGURABLE',versionStatus:detail.status});}catch(e){setError(e instanceof ApiError?e.message:'准确版本读取失败，请重试。');}finally{setLoading(false);}}
  return <Modal open width={860} title={root?`选择准确版本 · ${root.ruleSetCode}`:'选择规则集'} footer={null} onCancel={onCancel}>
    {error&&<Alert type="error" title={error} action={<Button onClick={()=>setRevision(v=>v+1)}>重试</Button>}/>}
    {root?<><Button type="link" onClick={()=>{setRoot(null);setHistoryPage(1);}}>重新选择规则集</Button><Table rowKey="ruleSetVersionId" loading={loading} dataSource={history} pagination={{current:historyPage,pageSize:10,total:historyTotal,onChange:setHistoryPage}} columns={[{title:'版本',dataIndex:'versionNo',render:v=>`#${v}`},{title:'名称',dataIndex:'ruleSetName'},{title:'状态',dataIndex:'status',render:v=><Tag>{v==='RETIRED'?'已退休':v==='FROZEN'?'已冻结':'草稿'}</Tag>},{title:'操作',render:(_,row)=><Button disabled={row.status==='RETIRED'} onClick={()=>void select(row)}>选择此版本</Button>}]}/></>:<><Space className="flow-picker-search"><Input aria-label="规则集编码精确查询" value={code} placeholder="规则集编码（精确匹配）" onChange={e=>setCode(e.target.value)} onPressEnter={()=>{setQuery(code.trim());setPage(1);}}/><Button onClick={()=>{setQuery(code.trim());setPage(1);}}>查询</Button></Space><Table rowKey="ruleSetId" loading={loading} dataSource={rows} pagination={{current:page,pageSize:10,total,onChange:setPage}} columns={[{title:'规则集',render:(_,r)=><><strong>{r.ruleSetName}</strong><p>{r.ruleSetCode}</p></>},{title:'当前版本',render:(_,r)=>`#${r.versionNo}`},{title:'操作',render:(_,r)=><Button onClick={()=>setRoot(r)}>选择版本</Button>}]}/></>}
    <p className="cell-meta">保存后固定引用所选版本。后续规则集更新不会自动替换流程中的版本。</p>
  </Modal>;
}
