import type { ReactNode } from 'react';
import { Alert, Button, Modal, Tag } from 'antd';
import { commercialStatusColors } from './commercial';
import type { useCommercialCommand } from './useCommercialCommand';

type Command = ReturnType<typeof useCommercialCommand>;

/** 状态标签：颜色取自 commercialStatusColors，文案可传中文映射。 */
export function StatusTag({ status, labels }: { status: string; labels?: Readonly<Record<string, string>> }) {
  return <Tag color={commercialStatusColors[status] ?? 'default'}>{labels?.[status] ?? status}</Tag>;
}

/** 并发冲突横幅：commercial 返回 409 时提示重新加载。 */
export function ConflictBanner({ onReload }: { onReload: () => void }) {
  return <Alert type="error" showIcon className="commercial-banner" title="数据已被其他操作修改，请重新加载后再试。"
    action={<Button size="small" onClick={onReload}>重新加载</Button>} />;
}

export function CommercialNote({ children, tone }: { children: ReactNode; tone?: 'warning' }) {
  return <div className={`commercial-note${tone === 'warning' ? ' is-warning' : ''}`}>{children}</div>;
}

/** 弹窗外壳：保存中禁止关闭；409 横幅与业务错误统一放在表单上方。onReload 在冲突后关闭弹窗并刷新详情。 */
export function CommandModal({ title, command, okText = '保存', onOk, onClose, onReload, children, width = 600, danger = false }: {
  title: ReactNode; command: Command; okText?: string; onOk: () => void; onClose: () => void; onReload: () => void; children?: ReactNode;
  width?: number; danger?: boolean;
}) {
  return <Modal open title={title} okText={okText} cancelText="取消" confirmLoading={command.saving}
    okButtonProps={{ disabled: command.conflict, danger }} onOk={onOk} onCancel={() => { if (!command.saving) onClose(); }}
    mask={{ closable: !command.saving }} destroyOnHidden width={width}>
    {command.conflict && <ConflictBanner onReload={onReload} />}
    {command.formError && <Alert type="error" showIcon className="commercial-banner" title={command.formError} />}
    {children}
  </Modal>;
}

/** 列表或详情的「按 ID 打开」入口说明：commercial 本期没有列表接口。 */
export function NoListNotice({ subject }: { subject: string }) {
  return <CommercialNote>commercial 本期没有{subject}列表接口：请按 ID 打开，或从合同详情的条款版本进入。新建成功后页面会直接跳转到新对象。</CommercialNote>;
}
