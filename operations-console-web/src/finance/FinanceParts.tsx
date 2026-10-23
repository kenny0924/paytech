import { Alert, Button, Tag } from 'antd';
import type { ReactNode } from 'react';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import { formatAmount, sideColors, statusColors } from './finance';
import type { Side } from './finance';

/** 金额：右对齐由列 align 控制；按币种精度补零，负数（反向余额）红色并加「反向」标签。 */
export function Amount({ value, minorUnit, suffix }: { value: string; minorUnit?: number; suffix?: string }) {
  const t = useT('finance');
  const amount = formatAmount(value, minorUnit);
  return <span className={`finance-amount${amount.negative ? ' is-negative' : ''}`}>
    {amount.text}{suffix ? ` ${suffix}` : ''}{amount.negative && <span className="finance-reverse">{t('amount.reverse')}</span>}
  </span>;
}

/** 状态标签：颜色统一取自 statusColors，文案取 finance.enum.<enumType>.<status>（未传或未知时显示代码）。 */
export function StatusTag({ status, enumType }: { status: string; enumType?: string }) {
  const t = useT('finance');
  return <Tag color={statusColors[status] ?? 'default'}>{enumType ? enumLabel(t, 'enum.' + enumType, status) : status}</Tag>;
}

export function SideTag({ side }: { side: Side }) {
  const t = useT('finance');
  return <Tag color={sideColors[side]}>{enumLabel(t, 'enum.side', side)}</Tag>;
}

/** 列表加载失败：内联提示与重试（错误通知已由加载方弹出）。 */
export function LoadFailure({ message, onRetry }: { message: string; onRetry: () => void }) {
  const t = useT('finance');
  return <Alert type="warning" showIcon title={message || t('common.loadFailed')}
    action={<Button size="small" onClick={onRetry}>{t('common.retry')}</Button>} />;
}

/** 并发冲突横幅：Finance 返回 409 时提示重新加载。 */
export function ConflictBanner({ onReload }: { onReload: () => void }) {
  const t = useT('finance');
  return <Alert type="error" showIcon className="finance-banner" title={t('common.conflict')}
    action={<Button size="small" onClick={onReload}>{t('common.reload')}</Button>} />;
}

export function FinanceNote({ children, tone }: { children: ReactNode; tone?: 'warning' }) {
  return <div className={`finance-note${tone === 'warning' ? ' is-warning' : ''}`}>{children}</div>;
}
