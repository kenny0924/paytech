import { Alert, Empty, Table, Tag } from 'antd';
import type { ReactNode } from 'react';
import { Amount } from '../finance/FinanceParts';
import { ConflictBanner } from '../finance/FinanceParts';
import { useT } from '../i18n/react';
import type { LineRejection, ReconEnum } from './reconModel';
import { reconLabel, reconStatusColors } from './reconModel';
import type { useReconCommand } from './useReconCommand';

/** 对账状态标签：颜色取 reconStatusColors，文案取 recon.enum.<kind.type>.<status>，未知 Code 原样显示。 */
export function ReconTag({ status, kind }: { status: string | null | undefined; kind: ReconEnum }) {
  const t = useT('recon');
  if (!status) return <span className="muted">—</span>;
  return <Tag color={reconStatusColors[status] ?? 'default'}>{reconLabel(t, kind, status)}</Tag>;
}

/** 金额：后端已按币种精度补齐，只做千分位与负数标识；空值显示破折号。 */
export function Money({ value }: { value: string | null | undefined }) {
  if (value === null || value === undefined) return <span className="muted">—</span>;
  return <Amount value={value} />;
}

/** 列表空态：说明为什么为空、下一步做什么（AC-V6）。 */
export function EmptyHint({ text }: { text: ReactNode }) {
  return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={text} />;
}

/** 命令反馈：409 冲突横幅与后端业务拒绝原因（含 Code），放在抽屉内容顶部。 */
export function ReconCommandFeedback({ command, onReload }: { command: ReturnType<typeof useReconCommand>; onReload: () => void }) {
  return <>
    {command.conflict && <ConflictBanner onReload={onReload} />}
    {command.formError && <Alert type="error" showIcon className="finance-banner" title={command.formError} />}
  </>;
}

/** 逐行拒绝原因（AC-V2）：行号为空表示账单头或整单层面的原因。 */
export function RejectionTable({ rejections }: { rejections: readonly LineRejection[] }) {
  const t = useT('recon');
  return <section className="table-panel">
    <Table<LineRejection & { key: number }> size="small" tableLayout="fixed" pagination={rejections.length > 20 ? { pageSize: 20 } : false}
      dataSource={rejections.map((item, key) => ({ ...item, key }))} rowKey="key" locale={{ emptyText: t('rejections.empty') }} columns={[
        { title: t('field.lineNo'), width: 100, render: (_, row) => row.lineNo ?? <span className="muted">{t('rejections.wholeFile')}</span> },
        { title: t('rejections.reason'), render: (_, row) => row.reason },
      ]} />
  </section>;
}
