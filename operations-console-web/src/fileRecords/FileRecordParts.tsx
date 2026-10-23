import { Button, Space, Tag, Typography } from 'antd';
import { accessLevelColors, accessLevelLabel } from '../fileStorage/fileStorage';
import type { AccessLevel } from '../fileStorage/fileStorage';
import { formatDateTime } from '../i18n/index';
import { useT } from '../i18n/react';
import { scanStatusColors, scanStatusLabel } from './fileRecords';
import type { ScanStatus } from './fileRecords';

export const date = (value: string | null) => formatDateTime(value);
export const LevelTag = ({ level }: { level: AccessLevel }) => <Tag color={accessLevelColors[level]}>{accessLevelLabel(level)}</Tag>;
export const ScanTag = ({ status }: { status: ScanStatus }) => <Tag color={scanStatusColors[status]}>{scanStatusLabel(status)}</Tag>;

/** 当前版本与进行中的替换。 */
export function VersionBadge({ current, pending }: { current: number; pending: number | null }) {
  const t = useT('file');
  return <Space size={4}><span>v{current}</span>{pending && <Tag color="gold">{t('records.pendingVersion', { versionNo: pending })}</Tag>}</Space>;
}

/** 公开地址条：复制、打开与复制带版本地址；地址在各版本间不变，CDN 缓存期间可能仍返回旧内容。 */
export function PublicUrlBar({ publicUrl, versionedUrl, versionNo, compact = false }: {
  publicUrl: string; versionedUrl: string | null; versionNo: number; compact?: boolean;
}) {
  const t = useT('file');
  return <div className={compact ? 'file-public-url is-compact' : 'file-public-url'}>
    <span className="file-public-url-label">{t('publicUrl.label')}</span>
    <Typography.Text className="mono file-public-url-value" ellipsis={{ tooltip: publicUrl }}>{publicUrl}</Typography.Text>
    <Space size={4} wrap>
      <Typography.Text copyable={{ text: publicUrl, tooltips: [t('check.copy'), t('check.copied')], icon: [<Button key="c" size="small">{t('check.copy')}</Button>, <Button key="d" size="small">{t('check.copied')}</Button>] }} />
      <Button size="small" href={publicUrl} target="_blank" rel="noopener noreferrer">{t('publicUrl.open')}</Button>
      {versionedUrl && <Typography.Text copyable={{ text: versionedUrl, tooltips: [t('publicUrl.copyVersioned'), t('check.copied')],
        icon: [<Button key="c" size="small">{t('publicUrl.copyVersionedWithQuery', { versionNo })}</Button>, <Button key="d" size="small">{t('check.copied')}</Button>] }} />}
    </Space>
    {!compact && <span className="muted">{t('publicUrl.note')}</span>}
  </div>;
}
