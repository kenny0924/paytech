import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Button, Input, Modal, Select, Table, Tag } from 'antd';
import { requestAllPages } from '../api/paging';
import { usePaging } from '../hooks/usePaging';
import { QueryFailure } from '../tenantAccess/TenantAccessView';
import { changeTargetLabels, changeTypeLabels, channelPaths, channelStatusColors, decodeChangePage, decodeChannelPage, localTime } from './channel';
import type { ChangeTargetType, ChannelSummary, ConfigChange } from './channel';
import { useChannelCommand } from './useChannelCommand';

type Command = ReturnType<typeof useChannelCommand>;

/** 状态标签：颜色取自 channelStatusColors，文案显示 Owner 状态码（与画板一致）。 */
export function StatusTag({ status }: { status: string }) {
  return <Tag color={channelStatusColors[status] ?? 'default'}>{status}</Tag>;
}

/** 并发冲突横幅：Owner 返回 409 时提示重新加载。 */
export function ConflictBanner({ onReload }: { onReload: () => void }) {
  return <Alert type="error" showIcon className="channel-banner" title="数据已被其他操作修改，请重新加载后再试。"
    action={<Button size="small" onClick={onReload}>重新加载</Button>} />;
}

/** 页面级命令结果：冲突横幅或 Owner 拒绝原因（例如渠道激活被拒）。 */
export function CommandFeedback({ command, onReload }: { command: Command; onReload: () => void }) {
  return <>
    {command.conflict && <ConflictBanner onReload={onReload} />}
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
export function CommandModal({ title, command, okText = '保存', onOk, onClose, onReload, children, width = 600, danger = false, okDisabled = false, footer }: {
  title: ReactNode; command: Command; okText?: string; onOk: () => void; onClose: () => void; onReload: () => void; children?: ReactNode;
  width?: number; danger?: boolean; okDisabled?: boolean; footer?: ReactNode;
}) {
  return <Modal open title={title} okText={okText} cancelText="取消" confirmLoading={command.saving}
    okButtonProps={{ disabled: command.conflict || okDisabled, danger }} onOk={onOk} onCancel={() => { if (!command.saving) onClose(); }}
    mask={{ closable: !command.saving }} destroyOnHidden width={width} {...(footer !== undefined ? { footer } : {})}>
    {command.conflict && <ConflictBanner onReload={onReload} />}
    {command.formError && <Alert type="error" showIcon className="channel-banner" title={command.formError} />}
    {children}
  </Modal>;
}

/**
 * 状态变更确认：目标状态 + 可选原因（≤512，写入变更记录）；Owner 拒绝原因（例如「Platform 不存在同编码接入方」）显示在弹窗顶部
 * <li>关闭为终态，按钮标红。</li>
 */
export function StatusChangeModal<T>({ title, path, targetStatus, actionLabel, description, decode, onDone, onClose, onAuthorizationChanged }: {
  title: ReactNode; path: string; targetStatus: string; actionLabel: string; description?: ReactNode; decode: (value: unknown) => T;
  onDone: (value: T) => void; onClose: () => void; onAuthorizationChanged: () => void;
}) {
  const command = useChannelCommand(onAuthorizationChanged);
  const [reason, setReason] = useState('');
  const submit = async () => {
    const result = await command.run(path, 'POST', { targetStatus, reason: reason.trim() || null }, decode, `${actionLabel}失败`);
    if (result) onDone(result);
  };
  return <CommandModal title={title} command={command} okText={actionLabel} danger={targetStatus === 'CLOSED'} onOk={() => void submit()}
    onClose={onClose} onReload={onClose}>
    {description && <ChannelNote tone={targetStatus === 'CLOSED' ? 'warning' : undefined}>{description}</ChannelNote>}
    <div className="channel-cell-meta">原因（可选，≤512，写入变更记录；禁止填写凭据）</div>
    <Input.TextArea aria-label="原因" rows={3} maxLength={512} showCount value={reason} onChange={event => setReason(event.target.value)} />
  </CommandModal>;
}

/** 渠道选择：一次读取全部渠道（分页 200 条拼接）；失败时只能清空，不阻塞页面。 */
export function ChannelSelect({ value, onChange, allowClear = true, placeholder = '全部', excludeClosed = false }: {
  value?: string; onChange?: (value: string | undefined) => void; allowClear?: boolean; placeholder?: string; excludeClosed?: boolean;
}) {
  const [channels, setChannels] = useState<ChannelSummary[]>([]);
  useEffect(() => {
    const controller = new AbortController();
    requestAllPages(channelPaths.channels, controller.signal, undefined, {}, decodeChannelPage).then(setChannels).catch(() => setChannels([]));
    return () => controller.abort();
  }, []);
  return <Select aria-label="渠道" showSearch optionFilterProp="label" allowClear={allowClear} placeholder={placeholder} value={value}
    onChange={next => onChange?.(next ?? undefined)}
    options={channels.filter(c => !excludeClosed || c.status !== 'CLOSED').map(c => ({ value: c.channelId, label: `${c.channelCode} · ${c.channelName}` }))} />;
}

/** 变更记录（画板 13）：按对象类型 + ID 分页，按记录主键倒序。 */
export function ChangeList({ targetType, targetId }: { targetType: ChangeTargetType; targetId: string }) {
  const page = usePaging<ConfigChange>(channelPaths.changes, decodeChangePage, { targetType, targetId }, { notifyOnError: false });
  return page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} />
    : <Table<ConfigChange> rowKey="changeId" size="middle" loading={page.loading} dataSource={page.data.items} pagination={page.pagination}
      scroll={{ x: 900 }} locale={{ emptyText: '暂无变更记录' }} columns={[
        { title: '时间', width: 150, render: (_, row) => localTime(row.createTime) },
        { title: '对象', width: 200, render: (_, row) => <div className="channel-cell"><span>{changeTargetLabels[row.targetType]}</span>
          <span className="channel-cell-meta">ID {row.targetId}</span></div> },
        { title: '变更类型', width: 110, render: (_, row) => changeTypeLabels[row.changeType] ?? row.changeType },
        { title: '状态变化', width: 200, render: (_, row) => row.fromStatus || row.toStatus ? <span>{row.fromStatus ?? '—'} → {row.toStatus ?? '—'}</span> : '—' },
        { title: '操作者', width: 120, render: (_, row) => <span className="mono">{row.operatorId}</span> },
        { title: '原因', render: (_, row) => row.reason ?? '—' },
      ]} />;
}
