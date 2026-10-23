import assert from 'node:assert/strict';
import test from 'node:test';
import {
  catalogTabs, creatableTypes, currentReferences, designerLayout, draftOf, editorSteps, fieldWidth, filterTemplates, isDirty, minFieldWidth, needsLeaveConfirm,
  newTemplateType, referenceText, suggestTemplateCode,
} from './catalogModel.ts';
import { addField, fieldKeyFromLabel, insertField } from './formModel.ts';
import { defaultConfig, normalizeConfig } from './templateModel.ts';
import type { ActionConfig, ActionTemplate, ActionType, FormDefinition, TemplateReferences } from './workflowTypes.ts';
import { testT } from '../i18n/testing.ts';

const t = testT('workflow');

const row = (templateId: string, actionType: ActionType, templateName: string, extra: Partial<ActionTemplate> = {}): ActionTemplate => ({
  templateId, templateCode: `CODE_${templateId}`, templateName, actionType, description: null, config: null, status: 'ACTIVE', version: 1,
  updatedBy: '1', updateTime: '2026-10-22T01:00:00Z', ...extra,
});
const items = [
  row('1', 'FORM', '商户资料表单', { description: '商户主体、注册与结算信息' }), row('2', 'FORM', '退款原因说明'), row('3', 'FILE_UPLOAD', '营业执照上传'),
  row('4', 'PAGE_TASK', 'MID 配置任务'), row('5', 'MANUAL_CONFIRM', '线下签约确认'), row('6', 'MANUAL_CONFIRM', '保证金到账确认', { status: 'INACTIVE' }),
];

test('列表页签：全部 + 四类带数量；没有数据查询动作时不出现该页签', () => {
  assert.deepEqual(catalogTabs(items), [{ key: 'ALL', count: 6 }, { key: 'FORM', count: 2 }, { key: 'FILE_UPLOAD', count: 1 },
    { key: 'PAGE_TASK', count: 1 }, { key: 'MANUAL_CONFIRM', count: 2 }]);
  assert.deepEqual(catalogTabs([]).map(tab => tab.count), [0, 0, 0, 0, 0]);
});

test('列表页签：已有数据查询动作时追加「数据查询」并计数', () => {
  const tabs = catalogTabs([...items, row('7', 'DATA_QUERY', '历史查询')]);
  assert.deepEqual(tabs.at(-1), { key: 'DATA_QUERY', count: 1 });
  assert.equal(tabs[0].count, 7);
});

test('按页签与关键字过滤：名称、编码、描述，不区分大小写，保持顺序', () => {
  assert.deepEqual(filterTemplates(items, 'MANUAL_CONFIRM', '').map(item => item.templateId), ['5', '6']);
  assert.deepEqual(filterTemplates(items, 'ALL', '结算').map(item => item.templateId), ['1']);
  assert.deepEqual(filterTemplates(items, 'ALL', ' mid ').map(item => item.templateId), ['4']);
  assert.deepEqual(filterTemplates(items, 'ALL', 'code_3').map(item => item.templateId), ['3']);
  assert.deepEqual(filterTemplates(items, 'FORM', '执照'), []);
});

test('被引用列：N 个流程；0 或后端缺字段时为「—」', () => {
  assert.equal(referenceText(3), '3 processes');
  assert.equal(referenceText(1), '1 process');
  assert.equal(referenceText(0), '—');
  assert.equal(referenceText(null), '—');
  assert.equal(referenceText(undefined), '—');
});

test('引用抽屉：只列当前发布版本（每个流程一行），历史版本单独计数', () => {
  const reference = (definitionId: string, versionNo: number, currentVersion: boolean) => ({ definitionId, definitionCode: definitionId, definitionName: definitionId,
    versionNo, currentVersion, frozenTemplateVersion: 1, sameAsCurrent: true, runningInstanceCount: 0 });
  const references: TemplateReferences = { templateId: '1', currentTemplateVersion: 2,
    items: [reference('A', 2, true), reference('A', 1, false), reference('B', 1, true), reference('C', 3, false)] };
  const grouped = currentReferences(references);
  assert.deepEqual(grouped.current.map(item => item.definitionId), ['A', 'B']);
  assert.equal(grouped.historical, 2);
  assert.deepEqual(currentReferences(null), { current: [], historical: 0 });
});

test('未保存修改：名称、描述、配置变化才算；首尾空白、旧校验键与空参数行不算', () => {
  const saved = row('1', 'FORM', '商户资料', { config: { fields: [{ key: 'name', label: '名称', type: 'TEXT', required: true, rules: { maxLength: 20 } }] } });
  const baseline = draftOf(saved, 'FORM');
  assert.equal(isDirty('FORM', baseline, { ...baseline }), false);
  assert.equal(isDirty('FORM', baseline, { ...baseline, templateName: ' 商户资料 ' }), false);
  // 旧配置的 maxLength 保存时会被剥离，去掉它不算修改。
  assert.equal(isDirty('FORM', baseline, { ...baseline, config: { fields: [{ key: 'name', label: '名称', type: 'TEXT', required: true }] } }), false);
  assert.equal(isDirty('FORM', baseline, { ...baseline, description: '新描述' }), true);
  assert.equal(isDirty('FORM', baseline, { ...baseline, config: { fields: [{ key: 'name', label: '名称', type: 'TEXT', required: false }] } }), true);
  const page = draftOf(row('2', 'PAGE_TASK', '页面', { config: { pageRoute: '/a', parameters: [], instruction: '' } }), 'PAGE_TASK');
  assert.equal(isDirty('PAGE_TASK', page, { ...page, config: { pageRoute: '/a', parameters: [{ key: '', label: '' }], instruction: '' } }), false);
});

test('新建动作：空白草稿不算修改，填了名称算；离开确认只在有修改且可编辑时出现', () => {
  const blank = draftOf(null, 'MANUAL_CONFIRM');
  assert.deepEqual(blank, { templateCode: '', templateName: '', description: '', config: { instruction: '' } });
  assert.equal(isDirty('MANUAL_CONFIRM', blank, { ...blank, templateCode: 'AUTO_CODE' }), false);
  const named = { ...blank, templateName: '线下签约确认' };
  assert.equal(isDirty('MANUAL_CONFIRM', blank, named), true);
  // 新建成功后基线等于草稿：不再拦截跳转到编辑页。
  assert.equal(isDirty('MANUAL_CONFIRM', named, named), false);
  assert.equal(needsLeaveConfirm(true, true), true);
  assert.equal(needsLeaveConfirm(true, false), false);
  assert.equal(needsLeaveConfirm(false, true), false);
});

test('编码建议：英文与数字转大写下划线；纯中文用「类型_序号」；避开已有编码', () => {
  assert.equal(suggestTemplateCode('Merchant KYC form', 'FORM', new Set()), 'MERCHANT_KYC_FORM');
  assert.equal(suggestTemplateCode('MID 配置任务', 'PAGE_TASK', new Set()), 'MID');
  assert.equal(suggestTemplateCode('MID 配置任务', 'PAGE_TASK', new Set(['MID'])), 'MID_1');
  assert.equal(suggestTemplateCode('线下签约确认', 'MANUAL_CONFIRM', new Set()), 'MANUAL_CONFIRM_1');
  assert.equal(suggestTemplateCode('', 'FORM', new Set(['FORM_1', 'FORM_2'])), 'FORM_3');
  assert.equal(suggestTemplateCode('3DS 结果', 'FILE_UPLOAD', new Set()), 'FILE_UPLOAD_3DS');
  assert.match(suggestTemplateCode('a'.repeat(100), 'FORM', new Set()), /^[A-Z][A-Z0-9_]{0,63}$/);
});

test('字段键随显示名称生成：汉字转拼音，英文与数字转小写下划线，重复加序号，无可用字符保留默认键', () => {
  assert.equal(fieldKeyFromLabel('Company Name', new Set(), 'text1'), 'company_name');
  assert.equal(fieldKeyFromLabel('Company Name', new Set(['company_name']), 'text1'), 'company_name_2');
  assert.equal(fieldKeyFromLabel('Company Name', new Set(['company_name', 'company_name_2']), 'text1'), 'company_name_3');
  assert.equal(fieldKeyFromLabel('公司注册名称', new Set(), 'text1'), 'gong_si_zhu_ce_ming_cheng');
  assert.equal(fieldKeyFromLabel('公司注册名称', new Set(['gong_si_zhu_ce_ming_cheng']), 'text1'), 'gong_si_zhu_ce_ming_cheng_2');
  assert.equal(fieldKeyFromLabel('MID 数量', new Set(), 'number1'), 'mid_shu_liang');
  assert.equal(fieldKeyFromLabel('MID数量', new Set(), 'number1'), 'mid_shu_liang');
  assert.equal(fieldKeyFromLabel('2026 预算', new Set(), 'number1'), 'f_2026_yu_suan');
  assert.equal(fieldKeyFromLabel('绿色通道', new Set(), 'flag1'), 'lv_se_tong_dao');
  assert.equal(fieldKeyFromLabel('（）、。', new Set(), 'text1'), 'text1');
  assert.match(fieldKeyFromLabel('中'.repeat(100), new Set(), 'text1'), /^[a-z][a-z0-9_]{0,63}$/);
  assert.match(fieldKeyFromLabel('x'.repeat(100), new Set(), 'text1'), /^[A-Za-z][A-Za-z0-9_]{0,63}$/);
});

test('从字段类型拖到画布：插入到目标字段之前，越界加到末尾', () => {
  let form: FormDefinition = { fields: [] };
  form = addField(form, 'TEXT').form; form = addField(form, 'DATE').form;
  const inserted = insertField(form, 'ENUM', 1);
  assert.equal(inserted.index, 1);
  assert.deepEqual(inserted.form.fields.map(field => field.type), ['TEXT', 'ENUM', 'DATE']);
  assert.deepEqual(inserted.form.fields[1].rules, { options: [{ value: 'OPTION_1', label: t('form.defaultOption', { index: 1 }) }] });
  assert.equal(insertField(form, 'BOOLEAN', 99).index, 2);
});

test('步骤：表单为字段设计、其他类型为说明；引用只在已保存的动作出现并带数量', () => {
  assert.deepEqual(editorSteps('FORM', true).map(step => step.label), ['Basic Information', 'Field Design']);
  assert.deepEqual(editorSteps('FILE_UPLOAD', false, 2), [{ key: 'basic', label: 'Basic Information' }, { key: 'config', label: 'Instructions' },
    { key: 'references', label: 'References', count: 2 }]);
  assert.deepEqual(editorSteps('MANUAL_CONFIRM', false).at(-1), { key: 'references', label: 'References' });
});

test('新建路由的类型：只接受四类可新建类型，数据查询与非法值回列表', () => {
  assert.deepEqual(creatableTypes.map(newTemplateType), creatableTypes);
  assert.equal(newTemplateType('DATA_QUERY'), null);
  assert.equal(newTemplateType('form'), null);
  assert.equal(newTemplateType(null), null);
});

// 后端 ActionConfigJsonReader / FormDefinitionJsonReader 的允许键（StrictJson.allowOnly）。
const allowedKeys: Record<Exclude<ActionType, 'DATA_QUERY'>, string[]> = {
  FORM: ['fields'], FILE_UPLOAD: ['minFiles', 'maxFiles', 'mimeTypes', 'instruction', 'required'], PAGE_TASK: ['pageRoute', 'parameters', 'instruction'],
  MANUAL_CONFIRM: ['instruction', 'noteRequired'],
};
const fieldKeys = ['key', 'label', 'type', 'required', 'rules'];

function assertAccepted(type: Exclude<ActionType, 'DATA_QUERY'>, config: ActionConfig) {
  const root = config as unknown as Record<string, unknown>;
  for (const key of Object.keys(root)) assert.ok(allowedKeys[type].includes(key), `${type} 不允许 ${key}`);
  if (type === 'FORM') for (const field of (config as FormDefinition).fields) {
    for (const key of Object.keys(field)) assert.ok(fieldKeys.includes(key), `字段不允许 ${key}`);
    if (field.rules) assert.deepEqual(Object.keys(field.rules), ['options']);
  }
  if (type === 'PAGE_TASK') for (const parameter of (root.parameters as Record<string, unknown>[])) assert.deepEqual(Object.keys(parameter).sort(), ['key', 'label']);
}

test('各类型保存的配置只含后端读取器接受的键：表单只有键 / 名称 / 类型 / 必填 / 枚举选项，上传只有说明与至少一个', () => {
  for (const type of creatableTypes) assertAccepted(type as Exclude<ActionType, 'DATA_QUERY'>, normalizeConfig(type, defaultConfig(type)));
  const form = normalizeConfig('FORM', { fields: [
    { key: 'name', label: '名称', type: 'TEXT', required: true, rules: { maxLength: 20 } },
    { key: 'level', label: '等级', type: 'ENUM', required: false, rules: { options: [{ value: 'A', label: 'A 级' }], maxLength: 3 } },
  ] });
  assertAccepted('FORM', form);
  assert.deepEqual(form, { fields: [{ key: 'name', label: '名称', type: 'TEXT', required: true },
    { key: 'level', label: '等级', type: 'ENUM', required: false, rules: { options: [{ value: 'A', label: 'A 级' }] } }] });
  const upload = normalizeConfig('FILE_UPLOAD', { minFiles: 2, maxFiles: 5, mimeTypes: ['application/pdf'], instruction: '上传执照' });
  assert.deepEqual(upload, { required: true, instruction: '上传执照' });
  assert.deepEqual(normalizeConfig('FILE_UPLOAD', { required: false }), { required: false, instruction: '' });
  const page = normalizeConfig('PAGE_TASK', { pageRoute: '/channel/mids', parameters: [{ key: 'channelCode', label: '渠道' }, { key: '', label: '' }], instruction: '配置 MID' });
  assertAccepted('PAGE_TASK', page);
  assert.deepEqual(page, { pageRoute: '/channel/mids', parameters: [{ key: 'channelCode', label: '渠道' }], instruction: '配置 MID' });
  assert.deepEqual(normalizeConfig('MANUAL_CONFIRM', { instruction: '确认线下签约', noteRequired: true }), { instruction: '确认线下签约' });
});

test('1280 宽度下字段设计不挤压：画布能放下完整字段，至少单列 240px（AC-04）', () => {
  // 1280 视口：侧栏 200、内容边距 48、编辑器左侧步骤 168 + 间距 12、边框约 4 → 字段设计容器约 848px。
  const at1280 = designerLayout(1280 - 200 - 48 - 168 - 12 - 4);
  assert.equal(at1280.stacked, false);
  assert.deepEqual([at1280.palette, at1280.properties], [168, 300]);
  assert.ok(fieldWidth(at1280) >= minFieldWidth, `字段宽度 ${fieldWidth(at1280)}`);
  // 1920：三栏为原型的 200 | 画布 | 340，画布放两列字段。
  const at1920 = designerLayout(1920 - 200 - 48 - 168 - 12 - 4);
  assert.deepEqual([at1920.palette, at1920.properties, at1920.columns], [200, 340, 2]);
  assert.ok(fieldWidth(at1920) >= minFieldWidth);
  // 更窄（如抽屉）：字段类型改为顶部一行，画布仍不低于单列下限。
  const narrow = designerLayout(700);
  assert.equal(narrow.stacked, true);
  assert.equal(narrow.columns, 1);
  assert.ok(fieldWidth(narrow) >= minFieldWidth);
  assert.equal(designerLayout(420).single, true);
  for (let width = 320; width <= 2400; width += 20) {
    const layout = designerLayout(width);
    assert.ok(fieldWidth(layout) >= minFieldWidth, `容器 ${width}px 时字段宽度 ${fieldWidth(layout)}`);
  }
});
