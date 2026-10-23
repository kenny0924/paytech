import type { ReactNode } from 'react';
import { Alert, Button, Drawer, Modal, Space, Tag } from 'antd';
import { commercialStatusColors } from './commercial';
import type { VersionStatus } from './commercial';
import { belowStandardText, signatureState, signatureStateColors } from './versionFlow';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import type { useCommercialCommand } from './useCommercialCommand';

type Command = ReturnType<typeof useCommercialCommand>;

/** 状态标签：颜色取自 commercialStatusColors；kind 为枚举种类（commercial 命名空间 enum.<kind>.<值>），不传或未知值显示原代码。 */
export function StatusTag({ status, kind }: { status: string; kind?: string }) {
  const t = useT('commercial');
  return <Tag color={commercialStatusColors[status] ?? 'default'}>{kind ? enumLabel(t, 'enum.' + kind, status) : status}</Tag>;
}

/** 并发冲突横幅：commercial 返回 409 时提示重新加载。 */
export function ConflictBanner({ onReload }: { onReload: () => void }) {
  const t = useT('commercial');
  return <Alert type="error" showIcon className="commercial-banner" title={t('error.conflict')}
    action={<Button size="small" onClick={onReload}>{t('parts.reload')}</Button>} />;
}

export function CommercialNote({ children, tone }: { children: ReactNode; tone?: 'warning' }) {
  return <div className={`commercial-note${tone === 'warning' ? ' is-warning' : ''}`}>{children}</div>;
}

/** 弹窗外壳：保存中禁止关闭；409 横幅与业务错误统一放在表单上方。onReload 在冲突后关闭弹窗并刷新详情。 */
export function CommandModal({ title, command, okText, onOk, onClose, onReload, children, width = 600, danger = false }: {
  title: ReactNode; command: Command; okText?: string; onOk: () => void; onClose: () => void; onReload: () => void; children?: ReactNode;
  width?: number; danger?: boolean;
}) {
  const tCommon = useT('common');
  return <Modal open title={title} okText={okText ?? tCommon('action.save')} cancelText={tCommon('action.cancel')} confirmLoading={command.saving}
    okButtonProps={{ disabled: command.conflict, danger }} onOk={onOk} onCancel={() => { if (!command.saving) onClose(); }}
    mask={{ closable: !command.saving }} destroyOnHidden width={width}>
    {command.conflict && <ConflictBanner onReload={onReload} />}
    {command.formError && <Alert type="error" showIcon className="commercial-banner" title={command.formError} />}
    {children}
  </Modal>;
}

/**
 * 右侧抽屉外壳，与 CommandModal 同语义：保存中禁止关闭；409 横幅与业务错误放在内容上方；冲突后保存按钮不可用，onReload 关闭并刷新。
 * 底部固定「取消 / 保存」；宽度默认 760，窄屏占满。
 */
export function CommandDrawer({ title, command, okText, onOk, onClose, onReload, children, width = 760 }: {
  title: ReactNode; command: Command; okText?: string; onOk: () => void; onClose: () => void; onReload: () => void; children?: ReactNode; width?: number;
}) {
  const tCommon = useT('common');
  const close = () => { if (!command.saving) onClose(); };
  return <Drawer open size={`min(${width}px, 100vw)`} rootClassName="commercial-drawer cc-command-drawer" title={title} onClose={close}
    mask={{ closable: !command.saving }} destroyOnHidden
    footer={<Space className="cc-drawer-footer"><Button onClick={close} disabled={command.saving}>{tCommon('action.cancel')}</Button>
      <Button type="primary" loading={command.saving} disabled={command.conflict} onClick={onOk}>{okText ?? tCommon('action.save')}</Button></Space>}>
    {command.conflict && <ConflictBanner onReload={onReload} />}
    {command.formError && <Alert type="error" showIcon className="commercial-banner" title={command.formError} />}
    {children}
  </Drawer>;
}

/** 抽屉内的一段：标题 + 内容。 */
export function DrawerSection({ title, extra, children }: { title: ReactNode; extra?: ReactNode; children: ReactNode }) {
  return <section className="cc-drawer-section"><div className="cc-drawer-section-head"><h3>{title}</h3>{extra}</div>{children}</section>;
}

/** 版本列表破标列：有破标为红色「破标 N」，没有为「—」，旧 BFF 无字段为空。 */
export function BelowStandardTag({ count }: { count: number | null }) {
  const text = belowStandardText(count);
  if (text === null) return null;
  return count ? <Tag color="red">{text}</Tag> : <span className="commercial-cell-meta">{text}</span>;
}

/** 版本列表签署列：历史免签 / 客户已签署 / 待客户签署。 */
export function SignatureTag({ row }: { row: { status: VersionStatus; signatureExempt: boolean; hasAcceptance?: boolean } }) {
  const t = useT('commercial');
  const state = signatureState(row);
  return state ? <Tag color={signatureStateColors[state]}>{enumLabel(t, 'enum.signatureState', state)}</Tag> : <span className="commercial-cell-meta">—</span>;
}
