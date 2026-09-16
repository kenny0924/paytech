from pathlib import Path
import json,re
root=Path(__file__).resolve().parents[4]
d=root/'requirements/20260914/client-console-注册与客户入驻/db'
m=json.loads((root/'platform-support-service/docs/database/platform-support.pdma.json').read_text())
schema=(root/'platform-support-service/docs/database/sql/platform-support-schema.sql').read_text()
tables=set(re.findall(r'CREATE TABLE\s+`([^\`]+)\`',schema))
selected=['t_authentication_challenge','t_file_upload','t_file_scan_policy','t_file_scan_requirement','t_file_object','t_file_owner_ref','t_file_scan_result']
out=[]
for e in m['entities']:
 if e['defKey'] not in selected:continue
 fields=e['fields'];fmap={f['id']:f['defKey'] for f in fields}
 def q(x):return "'"+str(x).replace("'","''")+"'"
 lines=[]
 for f in fields:
  typ=f['type']
  if f.get('len','')!='':typ+='('+str(f['len'])+(','+str(f['scale']) if f.get('scale','')!='' else '')+')'
  default=(' DEFAULT '+str(f['defaultValue'])) if f.get('defaultValue','')!='' else ''
  lines.append('  `'+f['defKey']+'` '+typ+(' NOT NULL' if f.get('notNull') else ' NULL')+default+' COMMENT '+q(f['comment']))
 pk=[f['defKey'] for f in fields if f.get('primaryKey')]
 lines.append('  PRIMARY KEY ('+','.join('`'+x+'`' for x in pk)+')')
 for ix in e.get('indexes',[]):
  names=[fmap[f['fieldDefKey']] for f in ix['fields']]
  lines.append('  '+('UNIQUE KEY' if ix.get('unique') else 'KEY')+' `'+ix['defKey']+'` ('+','.join('`'+x+'`' for x in names)+') COMMENT '+q(ix.get('comment','')))
 out.append('CREATE TABLE `'+e['defKey']+'` (\n'+',\n'.join(lines)+'\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='+q(e.get('comment',''))+';\n')
 print(e['defKey'],'present_in_current_schema='+str(e['defKey'] in tables),len(fields))
(d/'platform-additions-review.sql').write_text('-- REVIEW ONLY. Existing Platform model additions for email-link authentication and controlled files.\n-- Excludes Google, OTP and any seed of real accounts or external storage.\n\n'+'\n'.join(out))
(d/'platform-additions-inventory.json').write_text(json.dumps({'tables':[{'name':e['defKey'],'fieldCount':len(e['fields']),'currentSchemaHasTable':e['defKey'] in tables} for e in m['entities'] if e['defKey'] in selected]},ensure_ascii=False,indent=2)+'\n')
