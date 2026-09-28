import { useEffect, useState } from 'react';
import { Alert, Button, Select, Space } from 'antd';
import { ApiError } from '../api/session';
import { riskDictionaryApi } from './riskDictionary';
import type { DictionaryEntry, DictionaryKind } from './riskDictionary';

/** 远程启用目录与已保存代码分别加载；读取失败不能被当作未登记。 */
export function DictionarySelect({ kind, values, multiple=false, disabled, label, maxCount, onChange }: {
  kind:DictionaryKind; values:string[]; multiple?:boolean; disabled:boolean;label:string;maxCount?:number;
  onChange:(values:string[], selected:DictionaryEntry | undefined, previous:DictionaryEntry | undefined)=>void;
}) {
  const [keyword,setKeyword]=useState('');const [page,setPage]=useState(1);const [rows,setRows]=useState<DictionaryEntry[]>([]);
  const [selected,setSelected]=useState<Record<string,DictionaryEntry|null>>({});const [total,setTotal]=useState(0);const [loading,setLoading]=useState(false);
  const [error,setError]=useState('');const [revision,setRevision]=useState(0);const selectedKey=JSON.stringify(values.filter(Boolean));
  useEffect(()=>{const abort=new AbortController();const timer=setTimeout(()=>{setLoading(true);setError('');
    riskDictionaryApi.list(kind,{page,size:20},{status:'ACTIVE',...(keyword.trim()?{keyword:keyword.trim()}: {})},abort.signal)
      .then(result=>{if(!abort.signal.aborted){setRows(old=>page===1?result.items:[...old,...result.items.filter(row=>!old.some(item=>item.code===row.code))]);setTotal(result.total);}})
      .catch(failure=>{if(!abort.signal.aborted)setError(failure instanceof ApiError?`${failure.message} ${failure.requestId}`:'字典加载失败，请重试。');})
      .finally(()=>{if(!abort.signal.aborted)setLoading(false);});},200);return()=>{clearTimeout(timer);abort.abort();};},[kind,page,keyword,revision]);
  useEffect(()=>{const abort=new AbortController();const codes:string[]=JSON.parse(selectedKey);
    Promise.all(codes.map(async code=>{try{return [code,await riskDictionaryApi.detail(kind,code,abort.signal)] as const;}catch(error){if(error instanceof ApiError&&error.status===404&&error.code==='RISK_DICTIONARY_NOT_FOUND')return [code,null] as const;throw error;}}))
      .then(entries=>{if(!abort.signal.aborted)setSelected(Object.fromEntries(entries));})
      .catch(()=>{if(!abort.signal.aborted)setError('已保存字典项状态暂时无法确认；原值已保留。');});return()=>abort.abort();},[kind,selectedKey,revision]);
  const options=new Map(rows.map(row=>[row.code,{value:row.code,label:`${row.name} · ${row.code}`,disabled:false}]));
  for(const value of values.filter(Boolean)){const row=selected[value];if(!options.has(value))options.set(value,{value,label:row?`${row.name} · ${value}${row.status==='DISABLED'?' · 已停用':''}`:`${value} · ${row===null?'未登记':'状态待确认'}`,disabled:row?.status!=='ACTIVE'});}
  return <><Select aria-label={label} showSearch={{filterOption:false,onSearch:next=>{setKeyword(next);setPage(1);}}} mode={multiple?'multiple':undefined}
    value={multiple?values.filter(Boolean):values[0]||undefined} disabled={disabled} loading={loading} options={[...options.values()]} maxCount={maxCount}
    placeholder="搜索编码或名称" onChange={(next:string|string[])=>{const chosen=Array.isArray(next)?next:[next];onChange(chosen,rows.find(row=>row.code===chosen[0]),selected[values[0]!]??undefined);}}
    popupRender={menu=><>{menu}{rows.length<total&&<Button block type="link" disabled={loading} onClick={()=>setPage(value=>value+1)}>加载更多（{rows.length}/{total}）</Button>}</>} />
    {error&&<Alert type="warning" title={error} action={<Space><Button size="small" onClick={()=>{setPage(1);setRevision(value=>value+1);}}>重试</Button></Space>} />}</>;
}
