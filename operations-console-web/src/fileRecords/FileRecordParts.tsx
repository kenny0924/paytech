import { Button, Space, Tag, Typography } from 'antd';
import { accessLevelColors, accessLevelLabels } from '../fileStorage/fileStorage';
import type { AccessLevel } from '../fileStorage/fileStorage';
import { scanStatusColors, scanStatusLabels } from './fileRecords';
import type { ScanStatus } from './fileRecords';

export const date = (value: string | null) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—';
export const LevelTag = ({ level }: { level: AccessLevel }) => <Tag color={accessLevelColors[level]}>{accessLevelLabels[level]}</Tag>;
export const ScanTag = ({ status }: { status: ScanStatus }) => <Tag color={scanStatusColors[status]}>{scanStatusLabels[status]}</Tag>;

/** 当前版本与进行中的替换。 */
export function VersionBadge({ current, pending }: { current: number; pending: number | null }) {
  return <Space size={4}><span>v{current}</span>{pending && <Tag color="gold">v{pending} 检查中</Tag>}</Space>;
}

/** 公开地址条：复制、打开与复制带版本地址；地址在各版本间不变，CDN 缓存期间可能仍返回旧内容。 */
export function PublicUrlBar({ publicUrl, versionedUrl, versionNo, compact = false }: {
  publicUrl: string; versionedUrl: string | null; versionNo: number; compact?: boolean;
}) {
  return <div className={compact ? 'file-public-url is-compact' : 'file-public-url'}>
    <span className="file-public-url-label">公开地址</span>
    <Typography.Text className="mono file-public-url-value" ellipsis={{ tooltip: publicUrl }}>{publicUrl}</Typography.Text>
    <Space size={4} wrap>
      <Typography.Text copyable={{ text: publicUrl, tooltips: ['复制', '已复制'], icon: [<Button key="c" size="small">复制</Button>, <Button key="d" size="small">已复制</Button>] }} />
      <Button size="small" href={publicUrl} target="_blank" rel="noopener noreferrer">打开</Button>
      {versionedUrl && <Typography.Text copyable={{ text: versionedUrl, tooltips: ['复制带版本地址', '已复制'],
        icon: [<Button key="c" size="small">复制带版本地址（?v={versionNo}）</Button>, <Button key="d" size="small">已复制</Button>] }} />}
    </Space>
    {!compact && <span className="muted">地址在各版本间保持不变；CDN 缓存期间可能仍返回旧内容，需要立即生效时用带版本地址。</span>}
  </div>;
}
