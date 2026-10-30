import { useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, App, AutoComplete, Button, Input, Select, Switch, Tooltip } from 'antd';
import { useT } from '../i18n/react';
import { codePattern, dataQueryPaths, decodeMutation } from './dataQueryCommon';
import {
  createKeyTypeBody, keyRoleLabel, keyRoles, keyTypeForm, keyTypeLimits, keyValueTypeLabel, keyValueTypes, matchesPattern,
  newKeyTypeForm, patternProblem, patternProblemText, updateKeyTypeBody,
} from './keyTypeModel';
import type { KeyTypeForm, QueryKeyType } from './keyTypeModel';
import { ConflictBanner } from './DataQueryParts';
import { useDataQueryCommand } from './useDataQueryCommand';

/**
 * 键类型编辑（原型 04 右侧）：名称、分组（可选已有或输入新名称）、值类型（被模板使用时锁定）、默认角色、入口开关、输入提示，
 * 格式校验折叠为可选项并可现场试。停用在被 ACTIVE 模板引用时置灰。item 为 null 时新建。
 */
export function KeyTypeEditor({ item, items, canManage, onSaved, onClose, onAuthorizationChanged }: {
  item: QueryKeyType | null; items: readonly QueryKeyType[]; canManage: boolean; onSaved: (keyTypeId: string) => void; onClose: () => void;
  onAuthorizationChanged: () => void;
}) {
  const t = useT('data-query'), tCommon = useT('common'); const { modal } = App.useApp();
  const [form, setForm] = useState<KeyTypeForm>(() => item ? keyTypeForm(item) : newKeyTypeForm(items));
  const [patternOpen, setPatternOpen] = useState(() => !!item?.valuePattern);
  const [sample, setSample] = useState('');
  const command = useDataQueryCommand(onAuthorizationChanged); const status = useDataQueryCommand(onAuthorizationChanged);
  const set = (patch: Partial<KeyTypeForm>) => setForm(current => ({ ...current, ...patch }));
  const referenced = item?.referencedTemplateCount ?? 0;
  const readOnly = !canManage;
  const groups = useMemo(() => [...new Set(items.map(entry => entry.groupName))].map(value => ({ value })), [items]);
  const problem = patternProblem(form.valuePattern.trim());
  const errorOf = (field: string) => command.fieldErrors.find(error => error.field === field)?.message;
  const missing = !form.keyTypeName.trim() ? t('keyTypes.editor.nameRequired') : !form.groupName.trim() ? t('keyTypes.editor.groupRequired')
    : !item && !codePattern.test(form.keyTypeCode) ? t('keyTypes.editor.codeInvalid') : problem ? patternProblemText(problem) : null;

  async function save() {
    const result = item
      ? await command.run(dataQueryPaths.keyType(item.keyTypeId), 'PUT', updateKeyTypeBody(form, item.version),
        value => decodeMutation(value, 'QUERY_KEY_TYPE', item.keyTypeId), t('keyTypes.editor.saveFailed'))
      : await command.run(dataQueryPaths.keyTypes, 'POST', createKeyTypeBody(form), value => decodeMutation(value, 'QUERY_KEY_TYPE'), t('keyTypes.editor.createFailed'));
    if (result) onSaved(result.objectId);
  }
  function changeStatus(target: QueryKeyType) {
    const enabling = target.status !== 'ACTIVE';
    modal.confirm({
      title: enabling ? t('keyTypes.status.enableTitle', { name: target.keyTypeName }) : t('keyTypes.status.disableTitle', { name: target.keyTypeName }),
      content: enabling ? t('keyTypes.status.enableNotice') : t('keyTypes.status.disableNotice'), okButtonProps: { danger: !enabling },
      okText: enabling ? t('keyTypes.status.enable') : t('keyTypes.status.disable'), cancelText: tCommon('action.cancel'),
      onOk: async () => {
        const result = await status.run(dataQueryPaths.keyTypeStatus(target.keyTypeId), 'POST', { status: enabling ? 'ACTIVE' : 'INACTIVE', expectedVersion: target.version },
          value => decodeMutation(value, 'QUERY_KEY_TYPE', target.keyTypeId), enabling ? t('keyTypes.status.enableFailed') : t('keyTypes.status.disableFailed'));
        if (result) onSaved(result.objectId);
      },
    });
  }

  const sampleResult = !form.valuePattern.trim() || !sample ? null : problem ? null : matchesPattern(form.valuePattern.trim(), sample);
  return <div className="kt-editor">
    <header className="kt-editor-head">
      <div><h2>{item ? item.keyTypeName : t('keyTypes.editor.createTitle')}</h2>
        {item && <div className="muted"><span className="mono">{item.keyTypeCode}</span> · {referenced ? t('keyTypes.usedBy', { count: referenced }) : t('keyTypes.unused')}</div>}</div>
      <Button type="text" aria-label={t('keyTypes.editor.close')} onClick={onClose}>✕</Button>
    </header>
    {(command.conflict || status.conflict) && <ConflictBanner onReload={onClose} />}
    {command.formError && !command.fieldErrors.length && <Alert type="error" showIcon className="dq-banner" title={command.formError} />}
    {status.formError && <Alert type="error" showIcon className="dq-banner" title={status.formError} />}
    <div className="kt-editor-body">
      {!item && <Field label={t('keyTypes.editor.code')} error={errorOf('keyTypeCode')} hint={t('keyTypes.editor.codeHint')}>
        <Input className="mono" value={form.keyTypeCode} maxLength={64} disabled={readOnly} placeholder="PAYOUT_ID"
          status={form.keyTypeCode && !codePattern.test(form.keyTypeCode) ? 'error' : undefined} onChange={event => set({ keyTypeCode: event.target.value.toUpperCase() })} /></Field>}
      <Field label={t('keyTypes.editor.name')} error={errorOf('keyTypeName')}>
        <Input value={form.keyTypeName} maxLength={keyTypeLimits.name} disabled={readOnly} onChange={event => set({ keyTypeName: event.target.value })} /></Field>
      <Field label={t('keyTypes.editor.group')} error={errorOf('groupName')} hint={t('keyTypes.editor.groupHint')}>
        <AutoComplete value={form.groupName} options={groups} disabled={readOnly} maxLength={keyTypeLimits.group}
          showSearch={{ filterOption: (input, option) => !!option?.value?.toString().includes(input) }}
          onChange={groupName => set({ groupName: String(groupName ?? '') })} /></Field>
      <div className="kt-editor-pair">
        <Field label={t('keyTypes.editor.valueType')} error={errorOf('valueType')}>
          <Select value={form.valueType} disabled={readOnly || referenced > 0} options={keyValueTypes.map(value => ({ value, label: keyValueTypeLabel(value) }))}
            onChange={valueType => set({ valueType })} /></Field>
        <Field label={t('keyTypes.editor.defaultRole')} error={errorOf('defaultRole')}>
          <Select value={form.defaultRole} disabled={readOnly} options={keyRoles.map(value => ({ value, label: keyRoleLabel(value) }))}
            onChange={defaultRole => set({ defaultRole })} /></Field>
      </div>
      <div className="muted kt-editor-help">{referenced ? t('keyTypes.editor.valueTypeLocked') : t('keyTypes.editor.roleHint')}</div>
      <div className="kt-editor-divider" />
      <label className="kt-editor-switch"><Switch checked={form.entryAllowed} disabled={readOnly} onChange={entryAllowed => set({ entryAllowed })} />
        <strong>{t('keyTypes.editor.entryAllowed')}</strong></label>
      <Field label={t('keyTypes.editor.inputHint')} error={errorOf('inputHint')} hint={t('keyTypes.editor.inputHintHelp')}>
        <Input value={form.inputHint} maxLength={keyTypeLimits.hint} disabled={readOnly} onChange={event => set({ inputHint: event.target.value })} /></Field>
      <section className="kt-pattern">
        <button type="button" className="kt-pattern-head" onClick={() => setPatternOpen(open => !open)} aria-expanded={patternOpen}>
          <strong>{t('keyTypes.pattern.title')}</strong><span className="muted">{patternOpen ? t('keyTypes.pattern.collapse') : t('keyTypes.pattern.expand')}</span></button>
        {patternOpen && <>
          <Input className="mono" value={form.valuePattern} maxLength={keyTypeLimits.pattern} disabled={readOnly} placeholder={t('keyTypes.pattern.placeholder')}
            status={problem ? 'error' : undefined} onChange={event => set({ valuePattern: event.target.value })} />
          {(problem || errorOf('valuePattern')) && <div className="dq-text-error kt-editor-help">{problem ? patternProblemText(problem) : errorOf('valuePattern')}</div>}
          <div className="kt-pattern-test">
            <Input className="mono" value={sample} placeholder={t('keyTypes.pattern.samplePlaceholder')} onChange={event => setSample(event.target.value)} />
            {sampleResult === true && <span className="dq-ok">✓ {t('keyTypes.pattern.pass')}</span>}
            {sampleResult === false && <span className="dq-text-error">✕ {t('keyTypes.pattern.fail')}</span>}
          </div>
          <div className="muted kt-editor-help">{t('keyTypes.pattern.help')}</div>
        </>}
      </section>
    </div>
    {canManage && <footer className="kt-editor-foot">
      {item ? <Tooltip title={item.status === 'ACTIVE' && referenced ? t('keyTypes.status.referenced', { count: referenced }) : undefined}>
        <Button type="text" danger={item.status === 'ACTIVE'} disabled={item.status === 'ACTIVE' && referenced > 0} loading={status.saving} onClick={() => changeStatus(item)}>
          {item.status === 'ACTIVE' ? t('keyTypes.status.disable') : t('keyTypes.status.enable')}</Button></Tooltip> : <span />}
      <span className="dq-spacer" />
      {missing && <span className="muted kt-editor-missing">{missing}</span>}
      <Button onClick={onClose}>{tCommon('action.cancel')}</Button>
      <Button type="primary" loading={command.saving} disabled={!!missing || command.conflict} onClick={() => void save()}>{tCommon('action.save')}</Button>
    </footer>}
  </div>;
}

function Field({ label, error, hint, children }: { label: string; error?: string; hint?: string; children: ReactNode }) {
  return <label className="kt-field"><span className="kt-field-label">{label}</span>{children}
    {error ? <span className="dq-text-error kt-editor-help">{error}</span> : hint ? <span className="muted kt-editor-help">{hint}</span> : null}</label>;
}
