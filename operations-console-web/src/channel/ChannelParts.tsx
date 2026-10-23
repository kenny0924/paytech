import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Button, Input, Modal, Select, Table, Tag } from 'antd';
import { requestAllPages } from '../api/paging';
import { usePaging } from '../hooks/usePaging';
import { QueryFailure } from '../tenantAccess/TenantAccessView';
import { useT } from '../i18n/react';
import { changeTargetLabel, changeTypeLabel, channelPaths, channelStatusColors, decodeChangePage, decodeChannelPage, localTime } from './channel';
import type { ChangeTargetType, ChannelSummary, ChannelType, ConfigChange } from './channel';
import { useChannelCommand } from './useChannelCommand';

type Command = ReturnType<typeof useChannelCommand>;

/** 状态标签：颜色取自 channelStatusColors，文案显示 Owner 状态码（与画板一致）。 */
export function StatusTag({ status }: { status: string }) {
  return <Tag color={channelStatusColors[status] ?? 'default'}>{status}</Tag>;
}

/** 并发冲突横幅：Owner 返回 409 时提示重新加载；带 Owner 原因时一并显示（例如引用保护、授权已存在）。 */
export function ConflictBanner({ onReload, detail, reloadLabel }: { onReload: () => void; detail?: string | null; reloadLabel?: string }) {
  const t = useT('channel');
  return <Alert type="error" showIcon className="channel-banner" title={detail ? t('error.rejectedWithDetail', { detail }) : t('error.conflict')}
    description={detail ? t('conflict.detailHint') : undefined}
    action={<Button size="small" onClick={onReload}>{reloadLabel ?? t('conflict.reload')}</Button>} />;
}

/** 页面级命令结果：冲突横幅或 Owner 拒绝原因（例如渠道激活被拒）。 */
export function CommandFeedback({ command, onReload }: { command: Command; onReload: () => void }) {
  return <>
    {command.conflict && <ConflictBanner onReload={onReload} detail={command.conflictDetail} />}
    {command.formError && <Alert type="error" showIcon closable className="channel-banner" title={command.formError}
      onClose={command.clearError} />}
  </>;
}

export function ChannelNote({ children, tone }: { children: ReactNode; tone?: 'warning' }) {
  return <div className={`channel-note${tone === 'warning' ? ' is-warning' : ''}`}>{children}</div>;
}

/** 信息条：标签 + 值的横向摘要。 */
export function Summary({ items }: { items: { label: string; value: ReactNode }[] }) {
  return <dl className="channel-summary">{items.map(item => <div key={item.label}><dt>{item.label}</dt><dd>{item.value}</dd></div>)}</dl>;
}

/** 弹窗外壳：保存中禁止关闭；409 横幅与业务错误统一放在表单上方。 */
export function CommandModal({ title, command, okText, onOk, onClose, onReload, children, width = 600, danger = false, okDisabled = false, footer }: {
  title: ReactNode; command: Command; okText?: string; onOk: () => void; onClose: () => void; onReload: () => void; children?: ReactNode;
  width?: number; danger?: boolean; okDisabled?: boolean; footer?: ReactNode;
}) {
  const tCommon = useT('common');
  return <Modal open title={title} okText={okText ?? tCommon('action.save')} cancelText={tCommon('action.cancel')} confirmLoading={command.saving}
    okButtonProps={{ disabled: command.conflict || okDisabled, danger }} onOk={onOk} onCancel={() => { if (!command.saving) onClose(); }}
    mask={{ closable: !command.saving }} destroyOnHidden width={width} {...(footer !== undefined ? { footer } : {})}>
    {command.conflict && <ConflictBanner onReload={onReload} detail={command.conflictDetail} />}
    {command.formError && <Alert type="error" showIcon className="channel-banner" title={command.formError} />}
    {children}
  </Modal>;
}

/**
 * 状态变更确认：目标状态 + 可选原因（≤512，写入变更记录）；Owner 拒绝原因（例如「Platform 不存在同编码接入方」）显示在弹窗顶部
 * <li>关闭为终态，按钮标红。</li>
 */
export function StatusChangeModal<T>({ title, path, targetStatus, actionLabel, description, decode, onDone, onClose, onAuthorizationChanged,
  reasonRequired = false, extraBody, children }: {
  title: ReactNode; path: string; targetStatus: string; actionLabel: string; description?: ReactNode; decode: (value: unknown) => T;
  onDone: (value: T) => void; onClose: () => void; onAuthorizationChanged: () => void;
  /** 原因必填（例如暂停服务渠道）；未填时禁止提交。 */
  reasonRequired?: boolean;
  /** 额外请求字段，例如授权状态变更的 expectedVersion。 */
  extraBody?: Record<string, unknown>; children?: ReactNode;
}) {
  const t = useT('channel');
  const command = useChannelCommand(onAuthorizationChanged);
  const [reason, setReason] = useState('');
  const submit = async () => {
    const result = await command.run(path, 'POST', { targetStatus, ...extraBody, reason: reason.trim() || null }, decode, t('status.actionFailed', { action: actionLabel }));
    if (result) onDone(result);
  };
  return <CommandModal title={title} command={command} okText={actionLabel} danger={targetStatus === 'CLOSED'} onOk={() => void submit()}
    okDisabled={reasonRequired && !reason.trim()} onClose={onClose} onReload={onClose}>
    {description && <ChannelNote tone={targetStatus === 'CLOSED' ? 'warning' : undefined}>{description}</ChannelNote>}
    {children}
    <div className="channel-cell-meta">{reasonRequired ? t('status.reasonRequiredHint') : t('status.reasonOptionalHint')}</div>
    <Input.TextArea aria-label={t('changes.column.reason')} rows={3} maxLength={512} showCount value={reason} onChange={event => setReason(event.target.value)} />
  </CommandModal>;
}

/** 渠道选择：一次读取全部渠道（分页 200 条拼接）；失败时只能清空，不阻塞页面。MID 与成本页只列支付渠道（channelType）。 */
export function ChannelSelect({ value, onChange, allowClear = true, placeholder, excludeClosed = false, channelType }: {
  value?: string; onChange?: (value: string | undefined) => void; allowClear?: boolean; placeholder?: string; excludeClosed?: boolean; channelType?: ChannelType;
}) {
  const t = useT('channel');
  const [channels, setChannels] = useState<ChannelSummary[]>([]);
  useEffect(() => {
    const controller = new AbortController();
    requestAllPages(channelPaths.channels, controller.signal, undefined, {}, decodeChannelPage).then(setChannels).catch(() => setChannels([]));
    return () => controller.abort();
  }, []);
  return <Select aria-label={t('filter.channel')} showSearch optionFilterProp="label" allowClear={allowClear} placeholder={placeholder ?? t('filter.all')} value={value}
    onChange={next => onChange?.(next ?? undefined)}
    options={channels.filter(c => (!excludeClosed || c.status !== 'CLOSED') && (!channelType || c.channelType === channelType)).map(c => ({ value: c.channelId, label: `${c.channelCode} · ${c.channelName}` }))} />;
}

/** 侧栏用的紧凑留痕：最近若干条变更，逐条一行（时间 · 操作者、类型、状态变化、原因）。 */
export function ChangeTrail({ targetType, targetId }: { targetType: ChangeTargetType; targetId: string }) {
  const t = useT('channel');
  const page = usePaging<ConfigChange>(channelPaths.changes, decodeChangePage, { targetType, targetId }, { notifyOnError: false });
  if (page.failure) return <QueryFailure failure={page.failure} retry={page.refresh} />;
  if (!page.loading && page.data.items.length === 0) return <div className="channel-cell-meta">{t('changes.empty')}</div>;
  return <ul className="channel-trail">{page.data.items.map(row => <li key={row.changeId}>
    <span>{changeTypeLabel(row.changeType)}{row.fromStatus || row.toStatus ? ` · ${row.fromStatus ?? '—'} → ${row.toStatus ?? '—'}` : ''}
      {row.reason ? ` · ${row.reason}` : ''}</span>
    <span className="channel-cell-meta">{t('changes.trailMeta', { time: localTime(row.createTime), operator: row.operatorId })}</span></li>)}</ul>;
}

/** 变更记录（画板 13）：按对象类型 + ID 分页，按记录主键倒序。 */
export function ChangeList({ targetType, targetId }: { targetType: ChangeTargetType; targetId: string }) {
  const t = useT('channel');
  const page = usePaging<ConfigChange>(channelPaths.changes, decodeChangePage, { targetType, targetId }, { notifyOnError: false });
  return page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} />
    : <Table<ConfigChange> rowKey="changeId" size="middle" loading={page.loading} dataSource={page.data.items} pagination={page.pagination}
      scroll={{ x: 900 }} locale={{ emptyText: t('changes.empty') }} columns={[
        { title: t('changes.column.time'), width: 150, render: (_, row) => localTime(row.createTime) },
        { title: t('changes.object'), width: 200, render: (_, row) => <div className="channel-cell"><span>{changeTargetLabel(row.targetType)}</span>
          <span className="channel-cell-meta">ID {row.targetId}</span></div> },
        { title: t('changes.column.changeType'), width: 110, render: (_, row) => changeTypeLabel(row.changeType) },
        { title: t('changes.column.statusChange'), width: 200, render: (_, row) => row.fromStatus || row.toStatus ? <span>{row.fromStatus ?? '—'} → {row.toStatus ?? '—'}</span> : '—' },
        { title: t('changes.column.operator'), width: 120, render: (_, row) => <span className="mono">{row.operatorId}</span> },
        { title: t('changes.column.reason'), render: (_, row) => row.reason ?? '—' },
      ]} />;
}
