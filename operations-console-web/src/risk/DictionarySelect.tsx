import { useEffect, useState } from 'react';
import { Alert, Button, Select, Space } from 'antd';
import { ApiError } from '../api/session';
import { riskDictionaryApi } from './riskDictionary';
import type { DictionaryEntry, DictionaryKind } from './riskDictionary';
import { useT } from '../i18n/react';

/** 远程启用目录与已保存代码分别加载；读取失败不能被当作未登记。 */
export function DictionarySelect({ kind, values, multiple=false, disabled, label, maxCount, onChange }: {
  kind:DictionaryKind; values:string[]; multiple?:boolean; disabled:boolean;label:string;maxCount?:number;
  onChange:(values:string[], selected:DictionaryEntry | undefined, previous:DictionaryEntry | undefined)=>void;
}) {
  const t = useT('risk'), tCommon = useT('common');
  const [keyword,setKeyword]=useState('');const [page,setPage]=useState(1);const [rows,setRows]=useState<DictionaryEntry[]>([]);
  const [selected,setSelected]=useState<Record<string,DictionaryEntry|null>>({});const [total,setTotal]=useState(0);const [loading,setLoading]=useState(false);
  const [error,setError]=useState('');const [revision,setRevision]=useState(0);const selectedKey=JSON.stringify(values.filter(Boolean));
  useEffect(()=>{const abort=new AbortController();const timer=setTimeout(()=>{setLoading(true);setError('');
    riskDictionaryApi.list(kind,{page,size:20},{status:'ACTIVE',...(keyword.trim()?{keyword:keyword.trim()}: {})},abort.signal)
      .then(result=>{if(!abort.signal.aborted){setRows(old=>page===1?result.items:[...old,...result.items.filter(row=>!old.some(item=>item.code===row.code))]);setTotal(result.total);}})
      .catch(failure=>{if(!abort.signal.aborted)setError(failure instanceof ApiError?`${failure.message} ${failure.requestId}`:t('dictionarySelect.loadFailed'));})
      .finally(()=>{if(!abort.signal.aborted)setLoading(false);});},200);return()=>{clearTimeout(timer);abort.abort();};},[kind,page,keyword,revision]);
  useEffect(()=>{const abort=new AbortController();const codes:string[]=JSON.parse(selectedKey);
    Promise.all(codes.map(async code=>{try{return [code,await riskDictionaryApi.detail(kind,code,abort.signal)] as const;}catch(error){if(error instanceof ApiError&&error.status===404&&error.code==='RISK_DICTIONARY_NOT_FOUND')return [code,null] as const;throw error;}}))
      .then(entries=>{if(!abort.signal.aborted)setSelected(Object.fromEntries(entries));})
      .catch(()=>{if(!abort.signal.aborted)setError(t('dictionarySelect.statusUnknown'));});return()=>abort.abort();},[kind,selectedKey,revision]);
  const options=new Map(rows.map(row=>[row.code,{value:row.code,label:`${row.name} · ${row.code}`,disabled:false}]));
  for(const value of values.filter(Boolean)){const row=selected[value];if(!options.has(value))options.set(value,{value,label:row?(row.status==='DISABLED'?t('dictionarySelect.disabledOption',{name:row.name,code:value}):`${row.name} · ${value}`):row===null?t('dictionarySelect.unregistered',{code:value}):t('dictionarySelect.statusPending',{code:value}),disabled:row?.status!=='ACTIVE'});}
  return <><Select aria-label={label} showSearch={{filterOption:false,onSearch:next=>{setKeyword(next);setPage(1);}}} mode={multiple?'multiple':undefined}
    value={multiple?values.filter(Boolean):values[0]||undefined} disabled={disabled} loading={loading} options={[...options.values()]} maxCount={maxCount}
    placeholder={t('dictionarySelect.placeholder')} onChange={(next:string|string[])=>{const chosen=Array.isArray(next)?next:[next];onChange(chosen,rows.find(row=>row.code===chosen[0]),selected[values[0]!]??undefined);}}
    popupRender={menu=><>{menu}{rows.length<total&&<Button block type="link" disabled={loading} onClick={()=>setPage(value=>value+1)}>{t('dictionarySelect.loadMore',{loaded:rows.length,total})}</Button>}</>} />
    {error&&<Alert type="warning" title={error} action={<Space><Button size="small" onClick={()=>{setPage(1);setRevision(value=>value+1);}}>{tCommon('action.retry')}</Button></Space>} />}</>;
}
