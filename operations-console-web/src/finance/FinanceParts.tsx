import { Alert, Button, Tag } from 'antd';
import type { ReactNode } from 'react';
import { formatAmount, sideColors, sideLabels, statusColors } from './finance';
import type { Side } from './finance';

/** 金额：右对齐由列 align 控制；按币种精度补零，负数（反向余额）红色并加「反向」标签。 */
export function Amount({ value, minorUnit, suffix }: { value: string; minorUnit?: number; suffix?: string }) {
  const amount = formatAmount(value, minorUnit);
  return <span className={`finance-amount${amount.negative ? ' is-negative' : ''}`}>
    {amount.text}{suffix ? ` ${suffix}` : ''}{amount.negative && <span className="finance-reverse">反向</span>}
  </span>;
}

/** 状态标签：颜色统一取自 statusColors，文案可传中文映射。 */
export function StatusTag({ status, labels }: { status: string; labels?: Readonly<Record<string, string>> }) {
  return <Tag color={statusColors[status] ?? 'default'}>{labels?.[status] ?? status}</Tag>;
}

export function SideTag({ side }: { side: Side }) {
  return <Tag color={sideColors[side]}>{sideLabels[side]}</Tag>;
}

/** 列表加载失败：内联提示与重试（错误通知已由加载方弹出）。 */
export function LoadFailure({ message, onRetry }: { message: string; onRetry: () => void }) {
  return <Alert type="warning" showIcon title={message || '暂时无法加载，请重试。'}
    action={<Button size="small" onClick={onRetry}>重试</Button>} />;
}

/** 并发冲突横幅：Finance 返回 409 时提示重新加载。 */
export function ConflictBanner({ onReload }: { onReload: () => void }) {
  return <Alert type="error" showIcon className="finance-banner" title="数据已发生变化（并发操作），请重新加载后再试。"
    action={<Button size="small" onClick={onReload}>重新加载</Button>} />;
}

export function FinanceNote({ children, tone }: { children: ReactNode; tone?: 'warning' }) {
  return <div className={`finance-note${tone === 'warning' ? ' is-warning' : ''}`}>{children}</div>;
}
