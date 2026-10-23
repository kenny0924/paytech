import { Button, Tooltip } from 'antd';
import { useT } from '../i18n/react';

/** 列表工具栏的图标刷新：跟在条数后面，加载中旋转；失败时提示改为“重试”。 */
export function RefreshButton({ onClick, loading = false, failed = false }: { onClick: () => void; loading?: boolean; failed?: boolean }) {
  const t = useT('common');
  const label = t(failed ? 'action.retry' : 'action.refresh');
  return <Tooltip title={label}>
    <Button type="text" size="small" className="refresh-button" aria-label={label} onClick={onClick} disabled={loading}
      icon={<svg className={loading ? 'refresh-icon is-spinning' : 'refresh-icon'} viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M13.5 8a5.5 5.5 0 1 1-1.61-3.89" /><path d="M13.5 2.5v3h-3" />
      </svg>} />
  </Tooltip>;
}
