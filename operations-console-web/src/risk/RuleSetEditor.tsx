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
import { useT } from '../i18n/react';

const decisionLabels = terminalActionLabels;

export function RuleSetEditor({ mode = 'edit', detail, editor, base, catalog, disabled, commandPending, saving, dirty = true, fieldErrors, onChange, onSave }: {
  mode?: 'read' | 'edit'; detail?: RuleSetDetail; editor: RuleSetEditorModel; base: RuleSetBase; catalog: AuthoringCatalog; disabled: boolean; commandPending: boolean; saving: boolean; dirty?: boolean;
  fieldErrors: readonly { field: string; message: string }[]; onChange: (editor: RuleSetEditorModel) => void;
  onSave: (payload: string) => void;
}) {
  const t = useT('risk'), tNav = useT('nav');
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
    } catch (error) { return { payload: '', bytes: 0, size: 'NORMAL' as const, error: error instanceof Error ? error.message : t('ruleEditor.checkConfig') }; }
  }, [editor, base, catalog, mode, t]);
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
    modal.confirm({ title: t('ruleEditor.removeRule.title'), content: t('ruleEditor.removeRule.content', { name: selected.ruleName || t('readWorkspace.unnamed') }), okText: t('ruleEditor.removeRule.ok'), cancelText: t('ruleEditor.cancel'),
      onOk: () => onChange({ ...editor, rules: editor.rules.filter(rule => rule.id !== selected.id) }) });
  }
  if (mode === 'read') return <ReadRuleSetWorkspace editor={editor} base={detail ?? null} />;
  return <section className="risk-editor risk-editor-workspace" aria-label={t('ruleEditor.label')}>
    <header className="risk-workspace-header">
      <div className="risk-breadcrumb"><span>{tNav('resource.OPERATIONS_RISK_RULE_CONFIGURATION')}</span><span>/</span><span>{base ? t('ruleEditor.editTitle') : t('ruleEditor.createTitle')}</span></div>
      <div className="risk-editor-heading"><div className="risk-title-line"><h2>{editor.ruleSetName || t('ruleEditor.unnamedRuleSet')}</h2>
        <Button type="text" aria-label={t('ruleEditor.editName')} onClick={() => setBasicOpen(true)} disabled={disabled}>✎</Button><Tag variant="filled">{dirty ? t('ruleEditor.unsaved') : t('ruleEditor.unchanged')}</Tag></div>
        <Button type="link" onClick={() => setBasicOpen(true)} disabled={disabled}>{t('ruleEditor.editBasicInfo')}</Button></div>
      <p className="risk-meta">{editor.ruleSetCode} <span>·</span> {t('point.CHECKOUT_PREPARE')}{base && <span> · {t('ruleEditor.basedOnSaved')}</span>}</p>
    </header>
    <div className={`risk-workspace-body${collapsed ? ' is-collapsed' : ''}`}>
      <aside className="risk-rule-sidebar" aria-label={t('readWorkspace.ruleList')}>
        <div className="risk-sidebar-heading"><strong>{t('readWorkspace.rules')} <span>{editor.rules.length}</span></strong>
          <Space size={0}>{!collapsed && <Button type="text" aria-label={t('ruleEditor.addRule')} disabled={disabled || editor.rules.length >= 200} onClick={addRule}>{'\uFF0B'}</Button>}
            <Button type="text" aria-label={collapsed ? t('readWorkspace.expand') : t('readWorkspace.collapse')} aria-expanded={!collapsed} onClick={() => setCollapsed(!collapsed)}>{collapsed ? '›' : '‹'}</Button></Space></div>
        {!collapsed && <p className="risk-sidebar-hint">{t('ruleEditor.orderHint')}</p>}
        <ol className="risk-rule-list">{editor.rules.map((rule, index) => {
          const label = decisionLabels[rule.actionCode] ?? (rule.additionalActions?.length ? t('additionalAction.title') : t('readWorkspace.noAction'));
          const summary = t('ruleEditor.ruleSummary', { conditions: conditionCount(rule.condition), decision: label, actions: rule.additionalActions?.length ?? 0 });
          return <li key={rule.id} className={selected?.id === rule.id ? 'is-selected' : ''}
            onDragOver={event => { if (!disabled && dragging.current !== null) event.preventDefault(); }}
            onDrop={event => { event.preventDefault(); if (dragging.current !== null) move(dragging.current, index); dragging.current = null; }}>
            {!collapsed && <span className="risk-drag-handle" draggable={!disabled} aria-hidden="true" title={t('ruleEditor.dragToSort')}
              onDragStart={event => { dragging.current = index; event.dataTransfer.effectAllowed = 'move'; event.dataTransfer.setData('text/plain', String(index)); }}
              onDragEnd={() => { dragging.current = null; }}>⠿</span>}
            <Tooltip title={`${rule.ruleName || t('readWorkspace.unnamed')} · ${summary}`} placement="right">
              <button type="button" className="risk-rule-select" aria-label={t('ruleEditor.selectRule', { index: index + 1, name: rule.ruleName || t('readWorkspace.unnamed') })} aria-current={selected?.id === rule.id ? 'true' : undefined}
                onClick={() => setSelectedRuleId(rule.id)}><span className="risk-rule-number">{String(index + 1).padStart(2, '0')}</span>
                <span className="risk-rule-summary"><strong>{rule.ruleName || t('readWorkspace.unnamed')}</strong><small>{summary}</small></span>
                <span className={`risk-decision-dot decision-${rule.enabled ? label : 'disabled'}`} aria-label={rule.enabled ? label : t('ruleSetDetails.disabled')} /></button>
            </Tooltip>
          </li>;
        })}</ol>
        <Button type={collapsed ? 'default' : 'link'} className="risk-add-rule" aria-label={t('ruleEditor.addRule')} disabled={disabled || editor.rules.length >= 200} onClick={addRule}>{collapsed ? '\uFF0B' : t('ruleEditor.addRulePlus')}</Button>
        {!collapsed && editor.rules.length === 0 && <p className="risk-sidebar-hint">{t('ruleEditor.addFirstHint')}</p>}
      </aside>
      <main className="risk-rule-detail" aria-label={t('ruleEditor.selectedArea')}>
        {selected ? <>
          <div className="risk-rule-detail-heading"><div><div className="risk-title-line"><h3>{selected.ruleName || t('readWorkspace.unnamed')}</h3>
            <Button type="text" aria-label={t('ruleEditor.editRuleInfo')} disabled={disabled} onClick={() => setRuleInfoOpen(true)}>✎</Button></div><p className="risk-meta">{selected.ruleCode || t('ruleEditor.noRuleCode')}</p></div>
            <Space><Switch aria-label={t('ruleEditor.enabledLabel', { index: selectedIndex + 1 })} checked={selected.enabled} disabled={disabled} onChange={enabled => updateRule({ enabled })} /><span>{selected.enabled ? t('ruleSetDetails.enabled') : t('ruleSetDetails.disabled')}</span>
              <Dropdown trigger={['click']} menu={{ items: [
                { key: 'up', label: t('ruleEditor.moveUp'), disabled: disabled || selectedIndex === 0 },
                { key: 'down', label: t('ruleEditor.moveDown'), disabled: disabled || selectedIndex === editor.rules.length - 1 },
                { type: 'divider' }, { key: 'delete', label: t('ruleEditor.deleteRule'), danger: true, disabled },
              ], onClick: ({ key }) => { if (key === 'delete') deleteRule(); else move(selectedIndex, selectedIndex + (key === 'up' ? -1 : 1)); } }}>
                <Button type="text" aria-label={t('ruleEditor.ruleActions')} disabled={disabled}>···</Button></Dropdown></Space></div>
          <ConditionBuilder key={`condition-${selected.id}`} condition={selected.condition} catalog={catalog} disabled={disabled} onChange={condition => updateRule({ condition })} />
          <div className="risk-decision-options"><strong>{t('ruleEditor.actionOnMatch')}</strong><Radio.Group aria-label={t('ruleEditor.actionLabel', { index: selectedIndex + 1 })} value={selected.actionCode} disabled={disabled}
            options={[...catalog.actions.map(action => ({ value: action.actionCode, label: decisionLabels[action.actionCode] ?? action.label })), { value: '', label: t('ruleEditor.noFinalDecision') }]}
            onChange={event => updateRule({ actionCode: event.target.value, actionVersionNo: 1, reasonMessage: isDenyAction(event.target.value) ? selected.reasonMessage : '' })} /></div>
          <p className="risk-help">{t('ruleEditor.noActionHint')}</p>
          {isDenyAction(selected.actionCode) && <Form layout="vertical" requiredMark={false} className="risk-reject-panel">
            <div className="risk-reject-fields"><Form.Item label={t('flowPanel.rejectReasonRequired')} extra={t('ruleEditor.reasonHint')}><DictionarySelect kind="rejection-reasons" label={t('ruleEditor.reasonCodeLabel', { index: selectedIndex + 1 })}
                values={[selected.reasonCode]} disabled={disabled} onChange={(values, next, previous) => {
                  if (!next || values[0] === selected.reasonCode) return;
                  const change = () => updateRule({ reasonCode: next.code, reasonMessage: next.defaultMessage ?? '' });
                  if (selected.reasonMessage && selected.reasonMessage !== previous?.defaultMessage)
                    modal.confirm({ title: t('ruleEditor.replaceMessage.title'), content: t('ruleEditor.replaceMessage.content'), okText: t('ruleEditor.replaceMessage.ok'), cancelText: t('ruleEditor.replaceMessage.cancel'), onOk: change });
                  else change();
                }} /></Form.Item>
              <Form.Item label={t('flowPanel.rejectMessageRequired')} extra={t('ruleEditor.messageHint')}><Input.TextArea aria-label={t('ruleEditor.messageLabel', { index: selectedIndex + 1 })} value={selected.reasonMessage ?? ''} disabled={disabled} maxLength={500} autoSize={{ minRows: 2, maxRows: 5 }} onChange={event => updateRule({ reasonMessage: event.target.value })} /></Form.Item></div>
            <p className="risk-help">{t('readWorkspace.messageHint')}</p></Form>}
          <AdditionalActionEditor key={`actions-${selected.id}`} actions={selected.additionalActions ?? []} catalog={catalog} disabled={disabled} onChange={additionalActions => updateRule({ additionalActions })} />
          <details className="risk-description" key={`description-${selected.id}`}><summary>{t('readWorkspace.description')}</summary><ConditionView condition={selected.condition} />
            {selected.reasonCode && <p>{t('ruleEditor.reasonCodeValue', { code: selected.reasonCode })}</p>}</details>

        </> : <div className="risk-empty-editor"><span className="risk-empty-symbol">{'\uFF0B'}</span><h3>{t('ruleEditor.addFirstTitle')}</h3><p>{t('ruleEditor.addFirstDescription')}</p><Button type="primary" disabled={disabled} onClick={addRule}>{t('ruleEditor.addRuleButton')}</Button></div>}
      </main>
    </div>
    <footer className="risk-editor-footer"><div className="risk-save-status"><span className={`risk-status-dot${dirty ? ' is-dirty' : ''}`} /><span>{commandPending ? t('ruleEditor.savePending') : dirty ? t('ruleEditor.hasChanges') : t('ruleEditor.noChanges')}</span>
      <span className="risk-save-budget">{prepared.error || `${prepared.bytes.toLocaleString()} / 65,536 Bytes`}</span></div>
      <Space><Button type="link" disabled={disabled} onClick={() => setBasicOpen(true)}>{editor.changeReason ? t('ruleEditor.viewReason') : t('ruleEditor.enterReason')}</Button>
        <Button type="primary" loading={saving} disabled={disabled || commandPending || !!prepared.error || prepared.size === 'TOO_LARGE'} onClick={() => onSave(prepared.payload)}>{t('ruleEditor.saveNewVersion')}</Button></Space>
    </footer>
    {(fieldErrors.length > 0 || prepared.size !== 'NORMAL') && <div className="risk-editor-feedback">
      {fieldErrors.length > 0 && <Alert type="error" title={t('ruleEditor.checkFields')} description={<ul>{fieldErrors.map((error, index) => <li key={index}>{t('flowPanel.outputValue', { name: riskFieldErrorLabel(error.field), value: error.message })}</li>)}</ul>} />}
      {prepared.size !== 'NORMAL' && <Alert type={prepared.size === 'TOO_LARGE' ? 'error' : 'warning'} title={prepared.size === 'TOO_LARGE' ? t('ruleEditor.tooLarge') : t('ruleEditor.nearLimit')} />}</div>}
    <Modal title={t('readWorkspace.basicTitle')} open={basicOpen} onCancel={() => setBasicOpen(false)} footer={<Button type="primary" onClick={() => setBasicOpen(false)}>{t('ruleEditor.done')}</Button>}>
      <Form layout="vertical" requiredMark={false}><Form.Item label={t('ruleEditor.ruleSetNameRequired')}><Input aria-label={t('fieldError.ruleSetName')} value={editor.ruleSetName} disabled={disabled} maxLength={255} autoComplete="off" onChange={event => onChange({ ...editor, ruleSetName: event.target.value })} /></Form.Item>
        <Form.Item label={t('ruleEditor.ruleSetCodeRequired')} extra={t('ruleEditor.ruleSetCodeHint')}><Input aria-label={t('fieldError.ruleSetCode')} value={editor.ruleSetCode} readOnly={base !== null} disabled={disabled} maxLength={64} autoComplete="off" onChange={event => onChange({ ...editor, ruleSetCode: event.target.value })} /></Form.Item>
        <Form.Item label={t('ruleEditor.changeReasonRequired')} extra={t('ruleEditor.changeReasonHint')}><Input.TextArea aria-label={t('fieldError.changeReason')} value={editor.changeReason} disabled={disabled} maxLength={500} showCount autoSize={{ minRows: 3, maxRows: 6 }} onChange={event => onChange({ ...editor, changeReason: event.target.value })} /></Form.Item></Form>
    </Modal>
    <Modal title={t('ruleEditor.ruleInfoTitle')} open={ruleInfoOpen && !!selected} onCancel={() => setRuleInfoOpen(false)} footer={<Button type="primary" onClick={() => setRuleInfoOpen(false)}>{t('ruleEditor.done')}</Button>}>
      {selected && <Form layout="vertical" requiredMark={false}><Form.Item label={t('ruleEditor.ruleNameRequired')}><Input aria-label={t('ruleEditor.ruleNameLabel', { index: selectedIndex + 1 })} value={selected.ruleName} disabled={disabled} maxLength={255} autoComplete="off" onChange={event => updateRule({ ruleName: event.target.value })} /></Form.Item>
        <Form.Item label={t('ruleEditor.ruleCodeRequired')} extra={t('ruleEditor.ruleCodeHint')}><Input aria-label={t('ruleEditor.ruleCodeLabel', { index: selectedIndex + 1 })} value={selected.ruleCode} disabled={disabled} maxLength={64} autoComplete="off" onChange={event => updateRule({ ruleCode: event.target.value })} /></Form.Item>
        {!isDenyAction(selected.actionCode) && <Form.Item label={t('ruleEditor.reasonCodeOptional')}><Input aria-label={t('ruleEditor.reasonCodeLabel', { index: selectedIndex + 1 })} value={selected.reasonCode} disabled={disabled} maxLength={64} autoComplete="off" onChange={event => updateRule({ reasonCode: event.target.value })} /></Form.Item>}</Form>}
    </Modal>
  </section>;
}
