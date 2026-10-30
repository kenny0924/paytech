import { useMemo, useState } from 'react';
import { Button, Checkbox, Drawer, Input, Tag } from 'antd';
import type { ConsoleResource } from '../api/types';
import { useT } from '../i18n/react';
import { Loading, QueryFailure } from '../tenantAccess/TenantAccessView';
import { dataQueryButtons, dataQueryPaths, hasButton } from '../dataQuery/dataQueryCommon';
import { groupKeyTypes, keyRoleLabel, keyTypeOrderBody, moveKeyType } from '../dataQuery/keyTypeModel';
import type { QueryKeyType } from '../dataQuery/keyTypeModel';
import { laneColors } from '../dataQuery/jointLayout';
import { KeyTypeEditor } from '../dataQuery/KeyTypeEditor';
import { ConflictBanner } from '../dataQuery/DataQueryParts';
import { useDataQueryCommand } from '../dataQuery/useDataQueryCommand';
import { useKeyTypes } from '../dataQuery/useKeyTypes';
import { useMediaQuery } from '../dataQuery/useMediaQuery';
import '../dataQuery/dataQuery.css';
import '../dataQuery/jointQuery.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
type Editing = { kind: 'create' } | { kind: 'edit'; keyTypeId: string } | null;

/**
 * 键类型（原型 04）：按分组列出，拖动排序（顺序决定入口下拉与链路图位置，K5 整体保存；冲突时回滚）；
 * 搜索与“显示已停用”在页面内过滤。点击一行在右侧编辑；维护权限沿用查询模板维护按钮。
 */
export function QueryKeyTypes({ resources, onAuthorizationChanged }: Props) {
  const t = useT('data-query');
  const keyTypes = useKeyTypes();
  const canManage = hasButton(resources, dataQueryButtons.templateManage);
  const [keyword, setKeyword] = useState(''); const [showInactive, setShowInactive] = useState(false);
  const [editing, setEditing] = useState<Editing>(null);
  const [dragging, setDragging] = useState<string | null>(null); const [over, setOver] = useState<string | null>(null);
  const order = useDataQueryCommand(onAuthorizationChanged);
  const wide = useMediaQuery('(min-width: 1200px)');
  const all = keyTypes.items;
  const colors = useMemo(() => laneColors(groupKeyTypes(all.filter(item => item.status === 'ACTIVE')).map(group => ({ groupName: group.groupName, rank: group.rank })), []), [all]);
  const needle = keyword.trim().toLowerCase();
  const shown = all.filter(item => (showInactive || item.status === 'ACTIVE')
    && (!needle || item.keyTypeName.toLowerCase().includes(needle) || item.keyTypeCode.toLowerCase().includes(needle) || item.groupName.toLowerCase().includes(needle)));
  const groups = groupKeyTypes(shown);
  const selected = editing?.kind === 'edit' ? all.find(item => item.keyTypeId === editing.keyTypeId) ?? null : null;

  async function drop(targetId: string) {
    const movingId = dragging; setDragging(null); setOver(null);
    if (!movingId || !canManage) return;
    const next = moveKeyType(all, movingId, targetId);
    if (!next) return;
    const previous = all;
    keyTypes.setItems(next);
    const result = await order.run(dataQueryPaths.keyTypeOrder, 'PUT', keyTypeOrderBody(next), () => true, t('keyTypes.order.failed'));
    if (!result) { keyTypes.setItems(previous); return; }
    keyTypes.refresh();
  }

  const editor = editing && (editing.kind === 'create' || selected) ? <KeyTypeEditor key={editing.kind === 'edit' ? `${editing.keyTypeId}:${selected?.version}` : 'new'}
    item={selected} items={all} canManage={canManage} onAuthorizationChanged={onAuthorizationChanged} onClose={() => setEditing(null)}
    onSaved={keyTypeId => { setEditing({ kind: 'edit', keyTypeId }); keyTypes.refresh(); }} /> : null;

  return <div className="configuration-page dq-page kt-page">
    <div className="dq-list-toolbar">
      <div><span className="muted">{t('keyTypes.intro')}</span></div>
      {canManage && <Button type="primary" onClick={() => setEditing({ kind: 'create' })}>{t('keyTypes.create')}</Button>}
    </div>
    {order.conflict && <ConflictBanner onReload={() => { order.resetConflict(); keyTypes.refresh(); }} />}
    {order.formError && <div className="dq-note is-error">{order.formError}</div>}
    <div className={`kt-layout${editor && wide ? ' has-editor' : ''}`}>
      <section className="dq-panel kt-list">
        <div className="kt-toolbar">
          <Input allowClear value={keyword} maxLength={64} placeholder={t('keyTypes.search')} onChange={event => setKeyword(event.target.value)} />
          <Checkbox checked={showInactive} onChange={event => setShowInactive(event.target.checked)}>{t('keyTypes.showInactive')}</Checkbox>
        </div>
        {keyTypes.failure ? <QueryFailure failure={keyTypes.failure} retry={keyTypes.refresh} />
          : keyTypes.loading && !all.length ? <Loading />
            : groups.length === 0 ? <div className="muted kt-empty">{needle ? t('keyTypes.noMatch') : t('keyTypes.empty')}</div>
              : groups.map(group => <div key={group.groupName} className="kt-group">
                <div className="kt-group-title"><span className="jq-dot" style={{ background: colors.get(group.groupName) ?? '#a0aec0' }} />{group.groupName}</div>
                {group.items.map(item => <KeyTypeRow key={item.keyTypeId} item={item} selected={selected?.keyTypeId === item.keyTypeId}
                  draggable={canManage && !order.saving} dragging={dragging === item.keyTypeId}
                  over={over === item.keyTypeId && dragging !== null && all.find(entry => entry.keyTypeId === dragging)?.groupName === item.groupName}
                  onSelect={() => setEditing({ kind: 'edit', keyTypeId: item.keyTypeId })} onDragStart={() => setDragging(item.keyTypeId)}
                  onDragEnd={() => { setDragging(null); setOver(null); }} onDragOver={() => setOver(item.keyTypeId)} onDrop={() => void drop(item.keyTypeId)} />)}
              </div>)}
        {canManage && all.length > 0 && <div className="muted kt-foot">{t('keyTypes.dragHint')}</div>}
      </section>
      {editor && wide && <aside className="dq-panel kt-editor-panel">{editor}</aside>}
    </div>
    {!wide && <Drawer open={!!editor} rootClassName="dq-modal" size={480} closable={false} onClose={() => setEditing(null)} destroyOnHidden>{editor}</Drawer>}
  </div>;
}

function KeyTypeRow({ item, selected, draggable, dragging, over, onSelect, onDragStart, onDragEnd, onDragOver, onDrop }: {
  item: QueryKeyType; selected: boolean; draggable: boolean; dragging: boolean; over: boolean; onSelect: () => void; onDragStart: () => void;
  onDragEnd: () => void; onDragOver: () => void; onDrop: () => void;
}) {
  const t = useT('data-query');
  return <div role="button" tabIndex={0} className={`kt-row${selected ? ' is-selected' : ''}${dragging ? ' is-dragging' : ''}${over ? ' is-over' : ''}${item.status !== 'ACTIVE' ? ' is-inactive' : ''}`}
    draggable={draggable} onDragStart={event => { event.dataTransfer.effectAllowed = 'move'; onDragStart(); }} onDragEnd={onDragEnd}
    onDragOver={event => { event.preventDefault(); onDragOver(); }} onDrop={event => { event.preventDefault(); onDrop(); }}
    onClick={onSelect} onKeyDown={event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); onSelect(); } }}>
    <span className="dq-drag" aria-hidden title={draggable ? t('keyTypes.drag') : undefined}>{draggable ? '⋮⋮' : ''}</span>
    <span className="kt-name"><span>{item.keyTypeName}</span><span className="mono muted">{item.keyTypeCode}</span></span>
    <span><Tag className={`kt-role is-${item.defaultRole.toLowerCase()}`}>{keyRoleLabel(item.defaultRole)}</Tag>
      {item.status !== 'ACTIVE' && <Tag>{t('keyTypes.inactive')}</Tag>}</span>
    <span className="kt-hint">{item.entryAllowed ? item.inputHint ?? '—' : <span className="muted">{t('keyTypes.notEntry')}</span>}</span>
    <span className="muted kt-count">{t('keyTypes.templateCount', { count: item.referencedTemplateCount })}</span>
  </div>;
}
