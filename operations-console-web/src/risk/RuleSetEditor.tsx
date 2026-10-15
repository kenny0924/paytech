import { useMemo, useRef, useState } from 'react';
import { Alert, App, Button, Dropdown, Form, Input, Modal, Radio, Space, Switch, Tag, Tooltip } from 'antd';
import type { RuleSetDetail } from './riskApiModels';
import { editorToCreateRequest, isDenyAction, terminalActionLabels } from './ruleBuilder';
import type { EditorRule, RuleSetBase, RuleSetEditorModel } from './ruleBuilder';
import type { AuthoringCatalog } from './riskAuthoringCatalog';
import { saveSizeState, utf8ByteLength } from './riskCommands';
import { ConditionBuilder, newCondition } from './ConditionBuilder';
import { DictionarySelect } from './DictionarySelect';
import { ReadRuleSetWorkspace } from './ReadRuleSetWorkspace';
import { ConditionView } from './RuleSetDetails';
import { riskFieldErrorLabel } from './riskPageModel';
import { AdditionalActionEditor } from './AdditionalActionEditor';
import { conditionCount } from './ruleEditorPresentation';

const decisionLabels = terminalActionLabels;

export function RuleSetEditor({ mode = 'edit', detail, editor, base, catalog, disabled, commandPending, saving, dirty = true, fieldErrors, onChange, onSave }: {
  mode?: 'read' | 'edit'; detail?: RuleSetDetail; editor: RuleSetEditorModel; base: RuleSetBase; catalog: AuthoringCatalog; disabled: boolean; commandPending: boolean; saving: boolean; dirty?: boolean;
  fieldErrors: readonly { field: string; message: string }[]; onChange: (editor: RuleSetEditorModel) => void;
  onSave: (payload: string) => void;
}) {
  const { modal } = App.useApp();
  const dragging = useRef<number | null>(null);
  const [selectedRuleId, setSelectedRuleId] = useState<string | null>(editor.rules[0]?.id ?? null);
  const [collapsed, setCollapsed] = useState(false);
  const [basicOpen, setBasicOpen] = useState(false);
  const [ruleInfoOpen, setRuleInfoOpen] = useState(false);
  const selected = editor.rules.find(rule => rule.id === selectedRuleId) ?? editor.rules[0];
  const selectedIndex = selected ? editor.rules.indexOf(selected) : -1;
  const prepared = useMemo(() => {
    if (mode === 'read') return { payload: '', bytes: 0, size: 'NORMAL' as const, error: '' };
    try {
      const payload = JSON.stringify(editorToCreateRequest(editor, base, catalog));
      return { payload, bytes: utf8ByteLength(payload), size: saveSizeState(payload), error: '' };
    } catch (error) { return { payload: '', bytes: 0, size: 'NORMAL' as const, error: error instanceof Error ? error.message : '请检查规则配置。' }; }
  }, [editor, base, catalog, mode]);
  function updateRule(change: Partial<EditorRule>) {
    if (disabled || !selected) return;
    onChange({ ...editor, rules: editor.rules.map(rule => rule.id === selected.id ? { ...rule, ...change } : rule) });
  }
  function move(from: number, to: number) {
    if (disabled || from === to || from < 0 || from >= editor.rules.length || to < 0 || to >= editor.rules.length) return;
    const rules = [...editor.rules]; const [rule] = rules.splice(from, 1); rules.splice(to, 0, rule!); onChange({ ...editor, rules });
  }
  function addRule() {
    if (disabled || editor.rules.length >= 200) return;
    const id = crypto.randomUUID(); setSelectedRuleId(id); setRuleInfoOpen(true);
    onChange({ ...editor, rules: [...editor.rules, { id, ruleCode: '', ruleName: '', reasonCode: '', reasonMessage: '', additionalActions: [],
      enabled: true, condition: newCondition(), actionCode: '', actionVersionNo: 1 }] });
  }
  function deleteRule() {
    if (disabled || !selected) return;
    modal.confirm({ title: '移除此规则？', content: `将从本次编辑中移除“${selected.ruleName || '未命名规则'}”，保存新版本后生效。`, okText: '移除规则', cancelText: '取消',
      onOk: () => onChange({ ...editor, rules: editor.rules.filter(rule => rule.id !== selected.id) }) });
  }
  if (mode === 'read') return <ReadRuleSetWorkspace editor={editor} base={detail ?? null} />;
  return <section className="risk-editor risk-editor-workspace" aria-label="规则集编辑器">
    <header className="risk-workspace-header">
      <div className="risk-breadcrumb"><span>规则配置</span><span>/</span><span>{base ? '编辑规则集' : '新建规则集'}</span></div>
      <div className="risk-editor-heading"><div className="risk-title-line"><h2>{editor.ruleSetName || '未命名规则集'}</h2>
        <Button type="text" aria-label="编辑规则集名称" onClick={() => setBasicOpen(true)} disabled={disabled}>✎</Button><Tag variant="filled">{dirty ? '未保存' : '未修改'}</Tag></div>
        <Button type="link" onClick={() => setBasicOpen(true)} disabled={disabled}>编辑基本信息</Button></div>
      <p className="risk-meta">{editor.ruleSetCode} <span>·</span> 结账准备{base && <span> · 基于已保存版本编辑</span>}</p>
    </header>
    <div className={`risk-workspace-body${collapsed ? ' is-collapsed' : ''}`}>
      <aside className="risk-rule-sidebar" aria-label="规则列表">
        <div className="risk-sidebar-heading"><strong>规则 <span>{editor.rules.length}</span></strong>
          <Space size={0}>{!collapsed && <Button type="text" aria-label="添加规则" disabled={disabled || editor.rules.length >= 200} onClick={addRule}>＋</Button>}
            <Button type="text" aria-label={collapsed ? '展开规则列表' : '收起规则列表'} aria-expanded={!collapsed} onClick={() => setCollapsed(!collapsed)}>{collapsed ? '›' : '‹'}</Button></Space></div>
        {!collapsed && <p className="risk-sidebar-hint">按顺序匹配，遇到决定即停止</p>}
        <ol className="risk-rule-list">{editor.rules.map((rule, index) => {
          const label = decisionLabels[rule.actionCode] ?? (rule.additionalActions?.length ? '附加动作' : '未配置动作');
          const summary = `${conditionCount(rule.condition)} 个条件 · ${label}${rule.additionalActions?.length ? ` · ${rule.additionalActions.length} 个附加动作` : ''}`;
          return <li key={rule.id} className={selected?.id === rule.id ? 'is-selected' : ''}
            onDragOver={event => { if (!disabled && dragging.current !== null) event.preventDefault(); }}
            onDrop={event => { event.preventDefault(); if (dragging.current !== null) move(dragging.current, index); dragging.current = null; }}>
            {!collapsed && <span className="risk-drag-handle" draggable={!disabled} aria-hidden="true" title="拖动排序"
              onDragStart={event => { dragging.current = index; event.dataTransfer.effectAllowed = 'move'; event.dataTransfer.setData('text/plain', String(index)); }}
              onDragEnd={() => { dragging.current = null; }}>⠿</span>}
            <Tooltip title={`${rule.ruleName || '未命名规则'} · ${summary}`} placement="right">
              <button type="button" className="risk-rule-select" aria-label={`选择规则 ${index + 1} ${rule.ruleName || '未命名规则'}`} aria-current={selected?.id === rule.id ? 'true' : undefined}
                onClick={() => setSelectedRuleId(rule.id)}><span className="risk-rule-number">{String(index + 1).padStart(2, '0')}</span>
                <span className="risk-rule-summary"><strong>{rule.ruleName || '未命名规则'}</strong><small>{summary}</small></span>
                <span className={`risk-decision-dot decision-${rule.enabled ? label : 'disabled'}`} aria-label={rule.enabled ? label : '停用'} /></button>
            </Tooltip>
          </li>;
        })}</ol>
        <Button type={collapsed ? 'default' : 'link'} className="risk-add-rule" aria-label="添加规则" disabled={disabled || editor.rules.length >= 200} onClick={addRule}>{collapsed ? '＋' : '＋ 添加规则'}</Button>
        {!collapsed && editor.rules.length === 0 && <p className="risk-sidebar-hint">添加第一条规则开始配置</p>}
      </aside>
      <main className="risk-rule-detail" aria-label="选中规则编辑区">
        {selected ? <>
          <div className="risk-rule-detail-heading"><div><div className="risk-title-line"><h3>{selected.ruleName || '未命名规则'}</h3>
            <Button type="text" aria-label="编辑规则基本信息" disabled={disabled} onClick={() => setRuleInfoOpen(true)}>✎</Button></div><p className="risk-meta">{selected.ruleCode || '尚未填写规则编码'}</p></div>
            <Space><Switch aria-label={`规则 ${selectedIndex + 1} 启用`} checked={selected.enabled} disabled={disabled} onChange={enabled => updateRule({ enabled })} /><span>{selected.enabled ? '启用' : '停用'}</span>
              <Dropdown trigger={['click']} menu={{ items: [
                { key: 'up', label: '上移', disabled: disabled || selectedIndex === 0 },
                { key: 'down', label: '下移', disabled: disabled || selectedIndex === editor.rules.length - 1 },
                { type: 'divider' }, { key: 'delete', label: '删除规则', danger: true, disabled },
              ], onClick: ({ key }) => { if (key === 'delete') deleteRule(); else move(selectedIndex, selectedIndex + (key === 'up' ? -1 : 1)); } }}>
                <Button type="text" aria-label="规则操作" disabled={disabled}>···</Button></Dropdown></Space></div>
          <ConditionBuilder key={`condition-${selected.id}`} condition={selected.condition} catalog={catalog} disabled={disabled} onChange={condition => updateRule({ condition })} />
          <div className="risk-decision-options"><strong>命中后的动作（可选）</strong><Radio.Group aria-label={`规则 ${selectedIndex + 1} 执行动作`} value={selected.actionCode} disabled={disabled}
            options={[...catalog.actions.map(action => ({ value: action.actionCode, label: decisionLabels[action.actionCode] ?? action.label })), { value: '', label: '不配置最终决定' }]}
            onChange={event => updateRule({ actionCode: event.target.value, actionVersionNo: 1, reasonMessage: isDenyAction(event.target.value) ? selected.reasonMessage : '' })} /></div>
          <p className="risk-help">未配置动作时，提供命中结果，由流程连线决定下一步。节点处理结果可用于后续连线条件，流程在结束节点终止。</p>
          {isDenyAction(selected.actionCode) && <Form layout="vertical" requiredMark={false} className="risk-reject-panel">
            <div className="risk-reject-fields"><Form.Item label="拒绝原因 *" extra="从启用目录选择；旧项保留原代码与文案。"><DictionarySelect kind="rejection-reasons" label={`规则 ${selectedIndex + 1} 原因编码`}
                values={[selected.reasonCode]} disabled={disabled} onChange={(values, next, previous) => {
                  if (!next || values[0] === selected.reasonCode) return;
                  const change = () => updateRule({ reasonCode: next.code, reasonMessage: next.defaultMessage ?? '' });
                  if (selected.reasonMessage && selected.reasonMessage !== previous?.defaultMessage)
                    modal.confirm({ title: '替换当前拒绝文案？', content: '切换原因后将带入该原因的默认文案，当前手工文案将被替换。', okText: '切换并替换', cancelText: '保留当前原因和文案', onOk: change });
                  else change();
                }} /></Form.Item>
              <Form.Item label="拒绝文案 *" extra="随当前规则版本保存。"><Input.TextArea aria-label={`规则 ${selectedIndex + 1} 拒绝文案`} value={selected.reasonMessage ?? ''} disabled={disabled} maxLength={500} autoSize={{ minRows: 2, maxRows: 5 }} onChange={event => updateRule({ reasonMessage: event.target.value })} /></Form.Item></div>
            <p className="risk-help">用于内部决策记录；对外提示由接口统一映射。</p></Form>}
          <AdditionalActionEditor key={`actions-${selected.id}`} actions={selected.additionalActions ?? []} catalog={catalog} disabled={disabled} onChange={additionalActions => updateRule({ additionalActions })} />
          <details className="risk-description" key={`description-${selected.id}`}><summary>查看规则描述</summary><ConditionView condition={selected.condition} />
            {selected.reasonCode && <p>原因编码：{selected.reasonCode}</p>}</details>

        </> : <div className="risk-empty-editor"><span className="risk-empty-symbol">＋</span><h3>添加第一条规则</h3><p>设置匹配条件、决策结果和附加动作。</p><Button type="primary" disabled={disabled} onClick={addRule}>添加规则</Button></div>}
      </main>
    </div>
    <footer className="risk-editor-footer"><div className="risk-save-status"><span className={`risk-status-dot${dirty ? ' is-dirty' : ''}`} /><span>{commandPending ? '保存结果待确认' : dirty ? '有未保存的修改' : '尚未修改'}</span>
      <span className="risk-save-budget">{prepared.error || `${prepared.bytes.toLocaleString()} / 65,536 Bytes`}</span></div>
      <Space><Button type="link" disabled={disabled} onClick={() => setBasicOpen(true)}>{editor.changeReason ? '查看变更原因' : '填写变更原因'}</Button>
        <Button type="primary" loading={saving} disabled={disabled || commandPending || !!prepared.error || prepared.size === 'TOO_LARGE'} onClick={() => onSave(prepared.payload)}>保存新版本</Button></Space>
    </footer>
    {(fieldErrors.length > 0 || prepared.size !== 'NORMAL') && <div className="risk-editor-feedback">
      {fieldErrors.length > 0 && <Alert type="error" title="请检查以下字段" description={<ul>{fieldErrors.map((error, index) => <li key={index}>{riskFieldErrorLabel(error.field)}：{error.message}</li>)}</ul>} />}
      {prepared.size !== 'NORMAL' && <Alert type={prepared.size === 'TOO_LARGE' ? 'error' : 'warning'} title={prepared.size === 'TOO_LARGE' ? '超过 64 KiB，须精简规则后才能保存。' : '已达到 48 KiB，请留意 64 KiB 保存上限。'} />}</div>}
    <Modal title="规则集基本信息" open={basicOpen} onCancel={() => setBasicOpen(false)} footer={<Button type="primary" onClick={() => setBasicOpen(false)}>完成编辑</Button>}>
      <Form layout="vertical" requiredMark={false}><Form.Item label="规则集名称 *"><Input aria-label="规则集名称" value={editor.ruleSetName} disabled={disabled} maxLength={255} autoComplete="off" onChange={event => onChange({ ...editor, ruleSetName: event.target.value })} /></Form.Item>
        <Form.Item label="规则集编码 *" extra="大写字母、数字或下划线，以字母开头；创建版本流后不可修改。"><Input aria-label="规则集编码" value={editor.ruleSetCode} readOnly={base !== null} disabled={disabled} maxLength={64} autoComplete="off" onChange={event => onChange({ ...editor, ruleSetCode: event.target.value })} /></Form.Item>
        <Form.Item label="变更原因 *" extra="说明本次调整的目的；随新版本保存。"><Input.TextArea aria-label="变更原因" value={editor.changeReason} disabled={disabled} maxLength={500} showCount autoSize={{ minRows: 3, maxRows: 6 }} onChange={event => onChange({ ...editor, changeReason: event.target.value })} /></Form.Item></Form>
    </Modal>
    <Modal title="规则基本信息" open={ruleInfoOpen && !!selected} onCancel={() => setRuleInfoOpen(false)} footer={<Button type="primary" onClick={() => setRuleInfoOpen(false)}>完成编辑</Button>}>
      {selected && <Form layout="vertical" requiredMark={false}><Form.Item label="规则名称 *"><Input aria-label={`规则 ${selectedIndex + 1} 名称`} value={selected.ruleName} disabled={disabled} maxLength={255} autoComplete="off" onChange={event => updateRule({ ruleName: event.target.value })} /></Form.Item>
        <Form.Item label="规则编码 *" extra="大写字母、数字或下划线，以字母开头。"><Input aria-label={`规则 ${selectedIndex + 1} 编码`} value={selected.ruleCode} disabled={disabled} maxLength={64} autoComplete="off" onChange={event => updateRule({ ruleCode: event.target.value })} /></Form.Item>
        {!isDenyAction(selected.actionCode) && <Form.Item label="原因编码（可选）"><Input aria-label={`规则 ${selectedIndex + 1} 原因编码`} value={selected.reasonCode} disabled={disabled} maxLength={64} autoComplete="off" onChange={event => updateRule({ reasonCode: event.target.value })} /></Form.Item>}</Form>}
    </Modal>
  </section>;
}
