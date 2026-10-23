import { useState } from 'react';
import { Alert, App, Button, Input, Modal } from 'antd';
import { codeProblem, namespaceCodePattern } from './importModel';
import { decodeNamespace, localizationPaths } from './localizationApi';
import type { Namespace } from './localizationApi';
import { Icons, useAdminT } from './LocalizationParts';
import { nextSortOrder } from './settingsModel';
import { useLocalizationCommand } from './useLocalizationCommand';

/**
 * 新建 / 编辑命名空间（原型 09）：编码即时校验格式与重复（创建后不能修改），名称必填，说明选填。
 * 导入弹窗的"创建"入口会带上缺失的编码。
 */
export function NamespaceModal({ applicationCode, applicationName, namespaces, editing, initialCode = '', onAuthorizationChanged, onClose, onSaved }: {
  applicationCode: string; applicationName: string; namespaces: Namespace[]; editing?: Namespace; initialCode?: string;
  onAuthorizationChanged: () => void; onClose: () => void; onSaved: (namespace: Namespace) => void;
}) {
  const t = useAdminT(); const { message } = App.useApp();
  const [code, setCode] = useState(editing?.namespaceCode ?? initialCode);
  const [name, setName] = useState(editing?.namespaceName ?? '');
  const [description, setDescription] = useState(editing?.description ?? '');
  const [touched, setTouched] = useState(!!initialCode);
  const [error, setError] = useState<string | null>(null);
  const command = useLocalizationCommand(onAuthorizationChanged);
  const problem = editing ? null : codeProblem(code, namespaceCodePattern, namespaces.map(item => item.namespaceCode));
  const valid = !problem && !!name.trim();
  async function submit() {
    if (!valid) { setTouched(true); return; }
    setError(null);
    const body = { namespaceName: name.trim(), ...(description.trim() ? { description: description.trim() } : {}) };
    const result = editing
      ? await command.run(localizationPaths.namespace(applicationCode, editing.namespaceCode), 'PUT',
        { ...body, sortOrder: editing.sortOrder, expectedVersion: editing.version }, decodeNamespace, t('namespace.save_failed'))
      : await command.run(localizationPaths.namespaces(applicationCode), 'POST',
        { ...body, namespaceCode: code, sortOrder: nextSortOrder(namespaces) }, decodeNamespace, t('namespace.save_failed'));
    if (!result) return;
    if (result.ok) { void message.success(editing ? t('namespace.updated') : t('namespace.created', { code })); onSaved(result.value); return; }
    const failure = result.failure;
    if (failure.kind === 'conflict') setError(editing ? t('common.conflict_refresh') : t('namespace.duplicate'));
    else if (failure.kind === 'rejected') setError(failure.detail || t('common.rejected'));
  }
  return <Modal open width={560} title={<div className="l10n-modal-title">{editing ? t('namespace.edit_title') : t('namespace.create_title')}
    <span className="muted">{applicationName} · {t('namespace.subtitle')}</span></div>}
    onCancel={onClose} mask={{ closable: false }} footer={<div className="l10n-modal-foot"><span className="l10n-grow" />
      <Button onClick={onClose}>{t('common.cancel')}</Button>
      <Button type="primary" loading={command.saving} disabled={touched && !valid} onClick={() => void submit()}>{editing ? t('common.save') : t('common.create')}</Button></div>}>
    <div className="l10n-form">
      <label className="l10n-form-field"><span className="l10n-form-label">{t('namespace.code')}<span className="muted">{t('namespace.code_hint')}</span></span>
        <Input className="l10n-mono-input" value={code} disabled={!!editing} maxLength={64} autoFocus={!editing && !initialCode} placeholder={t('namespace.code_placeholder')}
          status={touched && problem ? 'error' : undefined} onChange={event => { setCode(event.target.value.trim()); setTouched(true); }} />
        {!editing && touched && (problem
          ? <span className="l10n-field-note l10n-danger-text">{Icons.error(12)} {t(`namespace.problem.${problem}`)}</span>
          : <span className="l10n-field-note l10n-ok-text">{Icons.checkCircle(12)} {t('namespace.available', { example: `${code}.xxx` })}</span>)}</label>
      <label className="l10n-form-field"><span className="l10n-form-label">{t('namespace.name')}</span>
        <Input value={name} maxLength={64} autoFocus={!!editing || !!initialCode} placeholder={t('namespace.name_placeholder')}
          status={touched && !name.trim() && code && !problem ? 'error' : undefined} onChange={event => setName(event.target.value)}
          onPressEnter={() => void submit()} /></label>
      <label className="l10n-form-field"><span className="l10n-form-label">{t('namespace.description')}<span className="muted">{t('common.optional')}</span></span>
        <Input.TextArea rows={3} maxLength={512} value={description} placeholder={t('namespace.description_placeholder')} onChange={event => setDescription(event.target.value)} /></label>
      {!editing && <div className="l10n-info-bar">{Icons.info()} {t('namespace.info')}</div>}
      {error && <Alert type="error" showIcon title={error} />}
    </div>
  </Modal>;
}
