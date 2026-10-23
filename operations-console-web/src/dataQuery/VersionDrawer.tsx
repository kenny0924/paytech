import { useMemo, useState } from 'react';
import { App, Button, Checkbox, Drawer, Input, Modal, Tag } from 'antd';
import { usePaging } from '../hooks/usePaging';
import { QueryFailure } from '../tenantAccess/TenantAccessView';
import { dataQueryPaths, localTime } from './dataQueryCommon';
import { decodeSaveResult, decodeTemplateVersion, decodeVersionPage } from './templateModel';
import type { SaveResult, TemplateVersion, VersionSummary } from './templateModel';
import { declarationChanges, lineDiff, sideBySide } from './versionDiff';
import { ConflictBanner, Note, useResource } from './DataQueryParts';
import { useDataQueryCommand } from './useDataQueryCommand';

/**
 * 版本历史（原型 06）：左边版本列表（勾选两个对比），右边正文逐行对比与参数 / 约束变化；
 * 恢复以所选版本内容生成新版本（服务端按当前规则重新校验）；编辑器有未保存修改时先提示。
 */
export function VersionDrawer({ templateId, templateCode, currentVersionNo, expectedVersion, dirty, canManage, onClose, onRestored, onReload, onAuthorizationChanged }: {
  templateId: string; templateCode: string; currentVersionNo: number; expectedVersion: number; dirty: boolean; canManage: boolean;
  onClose: () => void; onRestored: (result: SaveResult) => void; onReload: () => void; onAuthorizationChanged: () => void;
}) {
  const { modal } = App.useApp();
  const versions = usePaging<VersionSummary>(dataQueryPaths.versions(templateId), decodeVersionPage, {}, { notifyOnError: false });
  const [selected, setSelected] = useState<number[]>(() => currentVersionNo > 1 ? [currentVersionNo - 1, currentVersionNo] : [currentVersionNo]);
  const [restoring, setRestoring] = useState<number | null>(null);
  const [left, right] = [...selected].sort((a, b) => a - b);
  const decode = useMemo(() => decodeTemplateVersion, []);
  const leftVersion = useResource(left ? dataQueryPaths.version(templateId, left) : null, decode, '版本加载失败');
  const rightVersion = useResource(right ? dataQueryPaths.version(templateId, right) : null, decode, '版本加载失败');
  const restoreTarget = left !== undefined && left !== currentVersionNo ? left : right !== undefined && right !== currentVersionNo ? right : null;

  function toggle(versionNo: number, checked: boolean) {
    setSelected(current => checked ? [...current.filter(no => no !== versionNo), versionNo].slice(-2) : current.filter(no => no !== versionNo));
  }
  function startRestore(versionNo: number) {
    if (!dirty) { setRestoring(versionNo); return; }
    modal.confirm({ title: '编辑器中有未保存的修改', content: '恢复会生成新版本并重新载入编辑器，当前未保存的修改将丢失。是否继续？',
      okText: '放弃修改并继续', cancelText: '返回编辑', okButtonProps: { danger: true }, onOk: () => setRestoring(versionNo) });
  }

  return <Drawer open rootClassName="dq-modal" size={1180} title={`版本历史 · ${templateCode}`} onClose={onClose} destroyOnHidden
    footer={<div className="dq-drawer-footer">
      {dirty ? <Note tone="warning">编辑器中有未保存的修改：恢复前会提示先保存或放弃</Note> : <span />}
      <Button onClick={onClose}>关闭</Button>
      {canManage && <Button type="primary" disabled={restoreTarget === null} onClick={() => restoreTarget !== null && startRestore(restoreTarget)}>
        {restoreTarget === null ? '选择要恢复的历史版本' : `以 v${restoreTarget} 恢复为 v${currentVersionNo + 1}`}</Button>}
    </div>}>
    <p className="muted">版本不可修改；恢复会以所选版本的内容生成一个新版本，并按当前规则重新校验。</p>
    <div className="dq-version-layout">
      <aside className="dq-version-list">
        <div className="dq-sub-title">勾选两个版本进行对比</div>
        {versions.failure ? <QueryFailure failure={versions.failure} retry={versions.refresh} /> : versions.data.items.map(item =>
          <label key={item.versionNo} className={`dq-version-item${selected.includes(item.versionNo) ? ' is-selected' : ''}`}>
            <Checkbox checked={selected.includes(item.versionNo)} onChange={event => toggle(item.versionNo, event.target.checked)} />
            <div>
              <div><strong>v{item.versionNo}</strong>{item.versionNo === currentVersionNo && <Tag color="green">当前生效</Tag>}
                {item.restoredFromVersionNo && <Tag>由 v{item.restoredFromVersionNo} 恢复</Tag>}</div>
              <div className="muted">{item.createdBy} · {localTime(item.createTime)}</div>
              {item.changeNote && <div>{item.changeNote}</div>}
            </div>
          </label>)}
        {versions.data.total > versions.data.items.length && <div className="dq-version-pager">
          <Button size="small" disabled={versions.pagination.current <= 1} onClick={() => versions.pagination.onChange(versions.pagination.current - 1, versions.pagination.pageSize)}>‹</Button>
          <span>{versions.pagination.current}</span>
          <Button size="small" disabled={versions.pagination.current * versions.pagination.pageSize >= versions.data.total}
            onClick={() => versions.pagination.onChange(versions.pagination.current + 1, versions.pagination.pageSize)}>›</Button></div>}
      </aside>
      <section className="dq-version-compare">
        {selected.length < 2 ? <Note>勾选两个版本查看差异。</Note>
          : leftVersion.data && rightVersion.data && leftVersion.data.versionNo === left && rightVersion.data.versionNo === right
            ? <VersionCompare before={leftVersion.data} after={rightVersion.data} />
            : leftVersion.failure || rightVersion.failure ? <QueryFailure failure={leftVersion.failure ?? rightVersion.failure} />
              : <Note>加载中…</Note>}
      </section>
    </div>
    {restoring !== null && <RestoreModal templateId={templateId} versionNo={restoring} nextVersionNo={currentVersionNo + 1} expectedVersion={expectedVersion}
      onClose={() => setRestoring(null)} onReload={() => { setRestoring(null); onReload(); }} onRestored={result => { setRestoring(null); onRestored(result); }}
      onAuthorizationChanged={onAuthorizationChanged} />}
  </Drawer>;
}

function VersionCompare({ before, after }: { before: TemplateVersion; after: TemplateVersion }) {
  const rows = useMemo(() => sideBySide(lineDiff(before.body, after.body)), [before.body, after.body]);
  const changes = useMemo(() => declarationChanges(before, after), [before, after]);
  const removed = rows.filter(row => row.left?.kind === 'removed').length; const added = rows.filter(row => row.right?.kind === 'added').length;
  return <>
    <div className="dq-diff-legend"><strong>v{before.versionNo} → v{after.versionNo}</strong>
      <span className="dq-diff-removed">删除 {removed}</span><span className="dq-diff-added">新增 {added}</span></div>
    <div className="dq-diff">
      <div className="dq-diff-head">v{before.versionNo} · 正文</div><div className="dq-diff-head">v{after.versionNo} · 正文</div>
      {rows.map((row, index) => <div key={index} className="dq-diff-row">
        <DiffCell line={row.left} side="left" /><DiffCell line={row.right} side="right" />
      </div>)}
    </div>
    <div className="dq-sub-title">参数与约束的变化</div>
    {changes.length === 0 ? <p className="muted">参数、约束、列与数据源均无变化。</p>
      : <dl className="dq-changes">{changes.map((change, index) => <div key={index}><dt>{change.subject}</dt><dd>{change.change}</dd></div>)}</dl>}
    {before.dataSourceId === after.dataSourceId && <p className="muted">数据源 {after.dataSourceCode}（无变化）</p>}
  </>;
}

function DiffCell({ line, side }: { line: ReturnType<typeof sideBySide>[number]['left']; side: 'left' | 'right' }) {
  if (!line) return <div className="dq-diff-cell is-empty" />;
  const changed = line.kind !== 'same';
  const number = side === 'left' ? line.leftNo : line.rightNo;
  return <div className={`dq-diff-cell${changed ? ` is-${line.kind}` : ''}`}>
    <span className="dq-diff-no">{number}</span><span className="dq-diff-sign">{line.kind === 'removed' ? '−' : line.kind === 'added' ? '+' : ''}</span>
    <code>{line.text || ' '}</code>
  </div>;
}

function RestoreModal({ templateId, versionNo, nextVersionNo, expectedVersion, onClose, onReload, onRestored, onAuthorizationChanged }: {
  templateId: string; versionNo: number; nextVersionNo: number; expectedVersion: number;
  onClose: () => void; onReload: () => void; onRestored: (result: SaveResult) => void; onAuthorizationChanged: () => void;
}) {
  const command = useDataQueryCommand(onAuthorizationChanged);
  const [note, setNote] = useState(`恢复自 v${versionNo}`);
  const submit = async () => {
    const result = await command.run(dataQueryPaths.restoration(templateId), 'POST', { versionNo, expectedVersion, changeNote: note.trim() || null },
      value => decodeSaveResult(value, templateId), '恢复版本失败');
    if (result) onRestored(result);
  };
  return <Modal open wrapClassName="dq-modal" title={`以 v${versionNo} 恢复为 v${nextVersionNo}`} okText="恢复" cancelText="取消" confirmLoading={command.saving}
    okButtonProps={{ disabled: command.conflict }} onOk={() => void submit()} onCancel={() => { if (!command.saving) onClose(); }} destroyOnHidden>
    {command.conflict && <ConflictBanner subject="模板" onReload={onReload} />}
    {command.formError && !command.fieldErrors.length && <Note tone="error">{command.formError}</Note>}
    {command.fieldErrors.length > 0 && <Note tone="error">当前规则下校验未通过：{command.fieldErrors.map(error => error.message).join('；')}</Note>}
    <p>恢复后立即生效，调用方下一次执行使用新版本。</p>
    <Input.TextArea rows={2} maxLength={512} showCount value={note} onChange={event => setNote(event.target.value)} placeholder="变更说明（可选）" />
  </Modal>;
}
