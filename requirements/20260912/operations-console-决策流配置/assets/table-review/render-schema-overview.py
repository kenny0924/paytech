"""Render one review poster from the canonical Risk model plus the pending simplification.

This generates documentation only; it never edits the canonical model or executes SQL.
"""
from pathlib import Path
import copy
import hashlib
import html
import json
import math
import re

from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[4]
MODEL = ROOT / 'risk-compliance-service/docs/database/risk-compliance.pdma.json'
model = json.loads(MODEL.read_text())
evidence = json.loads((OUT / 'evidence.json').read_text())

ORDER = [
    'decision_point', 'rule_set', 'rule_set_version', 'risk_rule',
    'rule_node', 'rule_operand', 'action_version', 'action_parameter_definition',
    'rule_action', 'rule_action_parameter', 'rule_reject_reason', 'rule_subject_tag',
    'decision_flow', 'decision_flow_version', 'decision_flow_node', 'decision_flow_edge',
    'decision_point_version', 'decision_point_input_field', 'decision_point_rule_set',
    'decision_release', 'release_audit_event',
    'decision_point_feature', 'feature_version', 'feature_dependency', 'decision_point_obligation',
    'release_shadow_run', 'decision_evaluation', 'evaluation_feature_result',
    'evaluation_rule_trace', 'evaluation_action_intent', 'action_effect', 'decision_point_limit_binding',
]
ORDER = ['t_' + name for name in ORDER]
TABLES = {e['defKey']: copy.deepcopy(e) for e in model['entities'] if e['defKey'] in ORDER}
ALL_TABLES = {e['defKey']: e for e in model['entities']}
assert len(TABLES) == 32
assert set(ORDER) == {t['table'] for t in evidence['tables']}
IDS = {name: f'T{i:02}' for i, name in enumerate(ORDER, 1)}
REMOVED = {('t_rule_operand', 'input_field_id'), ('t_rule_action_parameter', 'input_field_id')}

ADDITIONS = {
    't_rule_set_version': [
        dict(defKey='input_contract_code', defName='输入合同编码', type='VARCHAR', len='64', scale='',
             notNull=True, primaryKey=False, defaultValue='', added=True,
             comment='准确定位 Risk 工程输入合同；与 input_contract_version 配对，不经旧顺序绑定解析。'),
        dict(defKey='input_contract_version', defName='输入合同版本', type='INT', len='', scale='',
             notNull=True, primaryKey=False, defaultValue='', added=True,
             comment='准确工程合同版本，不追随最新值；不是 HTTP API 版本。'),
    ],
}
for name in ('t_rule_operand', 't_rule_action_parameter'):
    ADDITIONS[name] = [dict(defKey='field_code', defName='Context 字段编码', type='VARCHAR', len='64',
        scale='', notNull=False, primaryKey=False, defaultValue='', added=True,
        comment='CONTEXT 来源必填，按所属规则版本的准确输入合同检查类型和用途；其他来源必须为空。')]
for name, fields in ADDITIONS.items():
    anchor = 'decision_point_id' if name == 't_rule_set_version' else 'input_field_id'
    idx = next(i for i, f in enumerate(TABLES[name]['fields']) if f['defKey'] == anchor) + 1
    TABLES[name]['fields'][idx:idx] = fields
for name, key in REMOVED:
    next(f for f in TABLES[name]['fields'] if f['defKey'] == key)['removed'] = True

relations = copy.deepcopy(evidence['relations'])
for r in relations:
    r['removed'] = (r['from'], r['field']) in REMOVED
    r['kind'] = 'model'
relations.extend([
    dict(from_='t_rule_set_version', field='decision_point_id + rule_set_code', to='t_rule_set',
         refField='decision_point_id + rule_set_code', kind='business', note='业务键归属；没有 rule_set_id 列'),
    dict(from_='t_risk_rule', field='reason_code', to='t_rule_reject_reason', refField='reason_code',
         kind='business', note='DENY 原因；历史手工代码按原合同读取'),
    dict(from_='t_rule_action_parameter', field='string_value', to='t_rule_subject_tag', refField='tag_code',
         kind='business', note='仅 ADD_RISK_TAG 的 TAG 字面量；不是所有 string_value'),
])
for r in relations:
    if 'from_' in r:
        r['from'] = r.pop('from_')
    r.setdefault('removed', False)
# Exact field references also include current-version pointers from the canonical model.
known = {(r['from'], r['field'], r['to'], r['refField']) for r in relations}
entity_ids = {e['id']: e for e in model['entities']}
for name, entity in TABLES.items():
    for c in entity.get('correlations', []):
        src = next((f for f in entity['fields'] if f.get('id') == c.get('myField')), None)
        target = entity_ids.get(c.get('refEntity'))
        dst = next((f for f in target['fields'] if f.get('id') == c.get('refField')), None) if target else None
        if src and dst:
            k = (name, src['defKey'], target['defKey'], dst['defKey'])
            if k not in known:
                relations.append(dict(zip(('from', 'field', 'to', 'refField'), k),
                    kind='model', removed=(name, src['defKey']) in REMOVED))
                known.add(k)

external = sorted({r['from'] for r in relations if r['from'] not in IDS}
                  | {r['to'] for r in relations if r['to'] not in IDS})
REF_IDS = IDS | {name: f'X{i:02}' for i, name in enumerate(external, 1)}
for name in ('t_rule_operand', 't_rule_action_parameter'):
    relations.append(dict(**{'from': name}, field='field_code', to='ENGINE_INPUT_CATALOG',
        refField='fieldCode', kind='engine', removed=False,
        note='沿所属规则版本的 input_contract_code + input_contract_version 解析；非数据库 FK'))
relations.append(dict(**{'from': 't_rule_set_version'}, field='input_contract_code + input_contract_version',
    to='ENGINE_INPUT_CATALOG', refField='contractCode + contractVersion', kind='engine', removed=False,
    note='与流程校验共用一份工程目录，不新增输入目录表'))
relations.append(dict(**{'from': 't_decision_flow_version'}, field='input_contract_code + input_contract_version',
    to='ENGINE_INPUT_CATALOG', refField='contractCode + contractVersion', kind='engine', removed=False,
    note='与规则配置共用准确合同；不依赖旧顺序绑定'))
REF_IDS['ENGINE_INPUT_CATALOG'] = 'E1'

FONT = '/System/Library/Fonts/Supplemental/Arial Unicode.ttf'
MONO = '/System/Library/Fonts/Menlo.ttc'
fonts = {}
def font(size, mono=False):
    return fonts.setdefault((size, mono), ImageFont.truetype(MONO if mono else FONT, size))

def wrap(value, width, size=18, mono=False):
    value = str(value)
    lines, line = [], ''
    for ch in value:
        if line and font(size, mono).getlength(line + ch) > width:
            lines.append(line)
            line = ch
        else:
            line += ch
    return lines + [line]

def dtype(f):
    typ = f['type'].upper()
    if f.get('len') != '' and f.get('len') is not None:
        typ += '(' + str(f['len']) + (',' + str(f['scale']) if f.get('scale') not in ('', None) else '') + ')'
    return typ

P = dict(bg='#F6F8FC', paper='#FFFFFF', ink='#17263C', muted='#5E6E83', border='#D4DCE8',
    blue='#315DAD', bluebg='#EAF0FC', green='#137B50', greenbg='#E7F5ED', red='#BB3744', redbg='#FFF0F0',
    gold='#956622', goldbg='#FFF5E3', purple='#715398', purplebg='#F1EDF8', line='#8397B4')
W, M, CW, GAP = 4272, 70, 1000, 44
ops, bounds = [], []
def rect(x,y,w,h,fill,stroke=None,r=0):
    ops.append(('rect',x,y,w,h,fill,stroke,r))
def text(x,y,value,size=20,color=None,mono=False,width=None,strike=False,tip=None):
    if width is not None:
        assert font(size,mono).getlength(value) <= width + 1, (value,width,font(size,mono).getlength(value))
    ops.append(('text',x,y,str(value),size,color or P['ink'],mono,strike,tip))
def lines(points,color=None,width=2,dash=False,arrow=False):
    ops.append(('line',points,color or P['line'],width,dash,arrow))
def group_title(y,title,subtitle,color):
    rect(M,y,W-2*M,66,P['paper'],P['border'],8)
    rect(M,y,8,66,color)
    text(M+24,y+14,title,27,color)
    text(M+1150,y+21,subtitle,21,P['muted'])

rect(0,0,W,200,P['paper'])
text(M,36,'决策流 · 表关系与全字段总览',54)
text(M,111,'精简方案确认稿  /  2026-09-12  /  本次精简尚未研发或执行迁移',25,P['muted'])
text(2630,47,'32 张表  ·  391 个目标字段',36,P['blue'])
text(2630,106,'本轮 +4 列 / −2 列 / −2 索引；另展示 2 个拟删字段供对照',22,P['muted'])

group_title(220,'01  主要关系','箭头：所属 / 被引用表 → 引用表；图下 Ref 列与关系清单补齐全部字段关系',P['blue'])

boxes={}
def box(key,x,y,w=590,h=100,kind='blue',note=None):
    name=ORDER[key-1] if isinstance(key,int) else key
    ident=IDS.get(name,'E1')
    label=TABLES[name]['defName'] if name in TABLES else '共享工程输入目录（非表）'
    rect(x,y,w,h,P[kind+'bg'],P['border'],10)
    text(x+18,y+16,ident+'  '+label,26,P[kind],width=w-36)
    text(x+18,y+57,note or name,19,P['ink'],mono=note is None,width=w-36)
    boxes[name]=(x,y,w,h)

cols=[90+680*i for i in range(6)]
for i,key in enumerate((1,2,3,4,5,6)): box(key,cols[i],325)
for i,key in enumerate((13,14,15,16,'ENGINE_INPUT_CATALOG')):
    box(key,cols[i],600,note='合同编码 + 合同版本 + fieldCode' if isinstance(key,str) else None)
rect(cols[5],600,590,100,P['bluebg'],P['border'],10)
text(cols[5]+18,614,'T11 拒绝原因  /  T12 主体标签',25,P['blue'])
text(cols[5]+18,657,'业务代码引用；详见 Ref 与关系清单',19)

for i in range(5):
    lines([(cols[i]+590,375),(cols[i+1],375)],P['blue'],2,dash=i==1,arrow=True)
    text(cols[i]+601,339,'1:N',18,P['blue'])
text(cols[1]+45,440,'T02 → T03 按 decision_point_id + rule_set_code 归属；没有 rule_set_id 列',19,P['muted'])
lines([(385,425),(385,600)],P['blue'],2,arrow=True)
text(405,496,'1:N',19,P['blue'])
for i in range(3):
    lines([(cols[i]+590,650),(cols[i+1],650)],P['blue'],2,arrow=True)
    text(cols[i]+601,614,'1:N',18,P['blue'])
text(cols[2]+5,716,'T15 → T16：source_node_id / target_node_id',19,P['muted'])
lines([(cols[1]+295,700),(cols[1]+295,770),(cols[3]+295,770),(cols[3]+295,700)],P['blue'],2,arrow=True)
text(cols[1]+515,740,'T14 → T16：同一流程版本 1:N 连线',19,P['blue'])
lines([(cols[2]+295,425),(cols[2]+295,600)],P['blue'],2,arrow=True)
text(cols[2]+315,499,'1:N 准确规则版本',19,P['blue'])
text(cols[4]+8,736,'规则字段、动作目标与流程校验共用定义',19,P['green'])
text(cols[4]+8,766,'Context 直接存 field_code；不再复制协议字段',19,P['green'])

# Old chain remains visually distinct from the normal authoring path.
text(90,853,'历史兼容：T17 / T18 / T19 不再随规则保存新增',25,P['gold'])
for key,x,y in [(17,90,904),(18,770,904),(19,1450,904),(20,90,1150),(21,770,1150)]:
    box(key,x,y,kind='gold')
lines([(680,954),(770,954)],P['gold'],2,dash=True,arrow=True)
lines([(385,904),(385,884),(1745,884),(1745,904)],P['gold'],2,dash=True,arrow=True)
lines([(385,1004),(385,1150)],P['gold'],2,dash=True,arrow=True)
lines([(680,1200),(770,1200)],P['gold'],2,dash=True,arrow=True)
lines([(90,375),(42,375),(42,954),(90,954)],P['gold'],2,dash=True,arrow=True)
lines([(1450,365),(1415,365),(1415,1030),(1745,1030),(1745,1004)],P['gold'],2,dash=True,arrow=True)
text(432,1069,'旧发布精确引用',19,P['gold'])
text(1474,1163,'T22–T32  预留能力与后续执行',24,P['purple'])
text(1474,1206,'本轮不裁剪，完整字段和引用见下方',19,P['muted'])
text(1474,1240,'旧 Hash / 真实 ID / 成功回放继续保留',19,P['muted'])

text(2250,853,'动作及参数：保留原职责，Context 引用改为字段代码',25,P['blue'])
for key,x,y in [(7,2250,904),(8,3200,904),(9,2250,1150),(10,3200,1150)]:
    box(key,x,y,w=880,kind='blue')
lines([(3130,954),(3200,954)],P['blue'],2,arrow=True)
lines([(2690,1004),(2690,1150)],P['blue'],2,arrow=True)
lines([(3640,1004),(3640,1150)],P['blue'],2,arrow=True)
lines([(3130,1200),(3200,1200)],P['blue'],2,arrow=True)
lines([(2130,375),(2082,375),(2082,1200),(2250,1200)],P['blue'],2,arrow=True)
text(2730,1069,'动作定义 → 绑定',19,P['blue'])
text(3680,1069,'参数定义 → 值',19,P['blue'])
text(90,1326,'四张流程表保留。规则节点 T05 是规则内部条件；流程节点 T15 是跨规则集步骤，不能合并。',23,P['ink'])

# Cards show every field in the reviewed scope, including crossed-out removals.
def field_refs(name,key):
    vals=[]
    for r in relations:
        if r['from']==name and key in r['field'].split(' + '):
            ref=REF_IDS[r['to']]
            if r['kind']=='business': ref+='*'
            if ref not in vals: vals.append(ref)
    return '/'.join(vals) or '—'

def card_rows(name):
    out=[]
    for f in TABLES[name]['fields']:
        cn=wrap(f['defName'],180,17)
        ty=wrap(dtype(f),142,17,True)
        ref=wrap(field_refs(name,f['defKey']),102,16,True)
        key=wrap(f['defKey'],383,17,True)
        default=str(f.get('defaultValue') or '')
        flags=('NN' if f.get('notNull') else 'NULL')
        flag_lines=[flags]+(wrap('='+default,73,14) if default else [])
        rowh=max(len(cn),len(ty),len(key),len(ref),len(flag_lines))*24+8
        out.append((f,key,cn,ty,flag_lines,ref,rowh))
    return out

def card_height(name):
    return 142+sum(r[-1] for r in card_rows(name))+58

def card(name,x,y):
    t=TABLES[name]; num=int(IDS[name][1:]); h=card_height(name)
    kind='gold' if 17<=num<=21 else 'purple' if num>=22 else 'blue'
    rect(x,y,CW,h,P['paper'],P['border'],10)
    rect(x,y,CW,96,P[kind+'bg'])
    text(x+18,y+16,IDS[name]+'  '+t['defName'],26,P[kind],width=CW-36)
    text(x+18,y+57,name,23,P['ink'],True,width=CW-36)
    if name in ADDITIONS: label='本轮字段调整'
    elif 13<=num<=16: label='RCO-17 新表 · 保留'
    elif 17<=num<=19: label='历史兼容 · 停止新增'
    elif num>=22: label='本轮不裁剪'
    elif num>=20: label='旧发布与历史 · 保留'
    else: label='保留'
    # Badge is separate from table name, inside the header's first row.
    text(x+CW-font(19).getlength(label)-18,y+23,label,19,P[kind])
    for dx,value in [(17,'标'),(49,'字段'),(438,'中文名'),(624,'类型'),(780,'空 / 默认'),(880,'Ref')]:
        text(x+dx,y+111,value,16,P['muted'])
    yy=y+142
    for f,key,cn,ty,flags,ref,rh in card_rows(name):
        removed=f.get('removed',False); added=f.get('added',False)
        if removed or added: rect(x+1,yy,CW-2,rh,P['redbg' if removed else 'greenbg'])
        color=P['red'] if removed else P['green'] if added else P['ink']
        mark='−' if removed else '+' if added else 'P' if f.get('primaryKey') else ''
        text(x+18,yy+6,mark,18,color)
        for dx,parts,size,mono in [(49,key,17,True),(438,cn,17,False),(624,ty,17,True),
                                    (780,flags,14,False),(880,ref,16,True)]:
            for j,part in enumerate(parts):
                text(x+dx,yy+6+j*24,part,size,color,mono,strike=removed and dx==49,tip=f.get('comment'))
        lines([(x+16,yy+rh),(x+CW-16,yy+rh)],P['border'],1)
        yy+=rh
    drop={'t_rule_operand':'拟删除索引 idx_rule_op_input',
          't_rule_action_parameter':'拟删除索引 idx_rule_param_input'}.get(name)
    indexes=t.get('indexes',[])
    uk=sum(bool(i.get('unique')) for i in indexes)
    label=drop or f'目标字段 {len(t["fields"])-sum(bool(f.get("removed")) for f in t["fields"])}  ·  PK 保留；原模型索引 {len(indexes)}（UK {uk}）'
    text(x+18,yy+17,label,17,P['red'] if drop else P['muted'],width=CW-36)
    bounds.append((x,y,CW,h,name))
    return h

y=1410
groups=[('02  正常配置与共享规则','16 张表；新增字段用 + 标记，删除字段用 − 划线',ORDER[:16],P['blue']),
        ('03  旧发布与历史兼容','5 张表；保留原事实，前三张不再随新规则保存新增',ORDER[16:21],P['gold']),
        ('04  本轮不裁剪的能力','11 张表；其中 7 张未接入、4 张已有读取；不代表真实执行已上线',ORDER[21:],P['purple'])]
for title,subtitle,names,color in groups:
    group_title(y,title,subtitle,color); y+=92
    for offset in range(0,len(names),4):
        row=names[offset:offset+4]
        heights=[card(name,M+i*(CW+GAP),y) for i,name in enumerate(row)]
        y+=max(heights)+34
    y+=18

group_title(y,'05  全部字段关系','child.field → parent.field；逻辑引用，不建立物理外键；* 为业务代码解析',P['blue'])
y+=95
text(M,y,'P = 主键  ·  NN = 非空  ·  NULL = 可空  ·  Ref = 下列引用编号  ·  + 新增  ·  − 拟删除  ·  E1 = 工程输入目录（非表）',21)
y+=45
legend=[]
for name in external:
    legend.append(f'{REF_IDS[name]}  {name}  {ALL_TABLES[name]["defName"]}')
legend.append('E1  (pointCode, contractCode, contractVersion, fieldCode)；共享定义，不新增数据表')
for line in legend:
    text(M,y,line,20,P['muted']); y+=29
y+=20
relation_start=y
ledger=[]
for r in sorted(relations,key=lambda r:(REF_IDS[r['from']],r['field'],REF_IDS[r['to']])):
    a=REF_IDS[r['from']]+'.'+r['field']; b=REF_IDS[r['to']]+'.'+r['refField']
    prefix='− ' if r['removed'] else '* ' if r['kind']=='business' else '+ ' if r['kind']=='engine' else '  '
    s=prefix+a+' → '+b
    if r.get('note'): s+='  ['+r['note']+']'
    ledger.append((wrap(s,2020,18),r))
split=math.ceil(len(ledger)/2)
bottom=y
for col,items in enumerate((ledger[:split],ledger[split:])):
    yy=relation_start; xx=M+col*2085
    for parts,r in items:
        color=P['red'] if r['removed'] else P['green'] if r['kind']=='engine' else P['muted']
        for part in parts: text(xx,yy,part,18,color); yy+=26
        yy+=7
    bottom=max(bottom,yy)
y=bottom+24
lines([(M,y),(W-M,y)],P['border'])
text(M,y+25,'范围：32 张已评审表的全部字段；X 编号是 Risk 库内其他领域表，只列引用边界，不是跨库关系。',22,P['muted'])
text(M,y+65,'依据：唯一 PDManer / 完整 Schema + simplification.md 待确认修订；蓝色配置、金色历史、紫色暂保留。',22,P['muted'])
text(M,y+105,'最终 391 个字段；图中共展示 393 行（含 2 行拟删除）。本图不代表精简已实施、数据库已迁移或 test 已部署。',22,P['muted'])
H=y+165

image=Image.new('RGB',(W,H),P['bg']); draw=ImageDraw.Draw(image)
svg=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" role="img" aria-labelledby="title desc">',
     '<title id="title">决策流精简方案：32 张表、全部字段与逻辑关系</title>',
     '<desc id="desc">方案确认稿，展示四个新增字段、两个拟删字段；保留原四张流程表及旧历史，不表示已迁移。</desc>',
     f'<rect width="{W}" height="{H}" fill="{P["bg"]}"/>']

def segment(a,b,color,width,dash):
    if not dash: draw.line([a,b],fill=color,width=width); return
    length=math.dist(a,b)
    if not length:return
    for offset in range(0,math.ceil(length),14):
        t0=offset/length; t1=min(offset+7,length)/length
        draw.line([(a[0]+(b[0]-a[0])*t0,a[1]+(b[1]-a[1])*t0),
                   (a[0]+(b[0]-a[0])*t1,a[1]+(b[1]-a[1])*t1)],fill=color,width=width)

for op in ops:
    if op[0]=='rect':
        _,x,yy,w,h,fill,stroke,r=op
        draw.rounded_rectangle((x,yy,x+w,yy+h),radius=r,fill=fill,outline=stroke,width=1)
        svg.append(f'<rect x="{x}" y="{yy}" width="{w}" height="{h}" rx="{r}" fill="{fill}" stroke="{stroke or "none"}"/>')
    elif op[0]=='text':
        _,x,yy,s,size,color,mono,strike,tip=op
        draw.text((x,yy),s,font=font(size,mono),fill=color,anchor='lt')
        # SVG uses a hanging baseline to match the poster's top-aligned text.
        family='Menlo,monospace' if mono else 'Arial Unicode MS,Heiti SC,sans-serif'
        attr=' text-decoration="line-through"' if strike else ''
        svg.append(f'<text x="{x}" y="{yy}" dominant-baseline="hanging" font-family="{family}" font-size="{size}" fill="{color}"{attr}>')
        if tip: svg.append('<title>'+html.escape(tip)+'</title>')
        svg.append(html.escape(s)+'</text>')
        if strike:
            draw.line((x,yy+size*.58,x+font(size,mono).getlength(s),yy+size*.58),fill=color,width=1)
    elif op[0]=='line':
        _,points,color,width,dash,arrow=op
        for a,b in zip(points,points[1:]):segment(a,b,color,width,dash)
        attr=' stroke-dasharray="7 7"' if dash else ''
        svg.append(f'<polyline points="{" ".join(f"{x},{z}" for x,z in points)}" fill="none" stroke="{color}" stroke-width="{width}"{attr}/>')
        if arrow:
            end,prev=points[-1],points[-2]; angle=math.atan2(end[1]-prev[1],end[0]-prev[0])
            arrowpts=[end,(end[0]-13*math.cos(angle-.5),end[1]-13*math.sin(angle-.5)),
                      (end[0]-13*math.cos(angle+.5),end[1]-13*math.sin(angle+.5))]
            draw.polygon(arrowpts,fill=color)
            svg.append(f'<polygon points="{" ".join(f"{x:.1f},{z:.1f}" for x,z in arrowpts)}" fill="{color}"/>')
svg.append('</svg>')
(OUT/'decision-flow-schema-overview.svg').write_text('\n'.join(svg)+'\n')
image.save(OUT/'decision-flow-schema-overview.png',optimize=True)
preview=image.copy(); preview.thumbnail((1450,3000)); preview.save(OUT/'decision-flow-schema-overview-preview.png')
# Inspection crop contains only the already-generated technical diagram.
first=next(b for b in bounds if b[4]=='t_rule_set_version')
x,yy,w,h,_=first
image.crop((x,yy,x+w,yy+h)).save(OUT/'decision-flow-schema-overview-detail.png')
source=dict(modelSha256=hashlib.sha256(MODEL.read_bytes()).hexdigest(),
    proposalSha256=hashlib.sha256((OUT.parents[1]/'simplification.md').read_bytes()).hexdigest(),
    width=W,height=H,tableCount=32,baselineFieldCount=389,targetFieldCount=391,
    displayedFieldCount=393,addedFields=4,removedFields=2,removedIndexes=2,
    relationCount=len(relations),externalTables=external,
    tables=[dict(id=IDS[n],table=n,name=TABLES[n]['defName'],fields=TABLES[n]['fields']) for n in ORDER],
    relations=relations)
(OUT/'decision-flow-schema-overview.json').write_text(json.dumps(source,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({k:source[k] for k in ['width','height','tableCount','targetFieldCount','displayedFieldCount','relationCount']},ensure_ascii=False))
