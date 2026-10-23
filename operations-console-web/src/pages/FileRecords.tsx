import { useLocation } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { buildMenuTree, menuPages } from '../navigation/menu';
import { canDownloadFileRecord } from '../fileRecords/fileRecords';
import { FileRecordList } from '../fileRecords/FileRecordList';
import { FileRecordDetailView } from '../fileRecords/FileRecordDetailView';
import '../configuration/configuration.css';
import '../notification/notification.css';
import '../fileRecords/fileRecords.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }

/**
 * 文件记录：列表与 ?file=&lt;id&gt; 详情
 * <li>只读查询；下载需要「下载」按钮权限，每次下载写审计。</li>
 * <li>详情的最近下载嵌入审计组件，需要审计日志页面权限。</li>
 */
export function FileRecords({ resources, onAuthorizationChanged }: Props) {
  const location = useLocation(); const query = new URLSearchParams(location.search);
  const canDownload = canDownloadFileRecord(resources);
  const canAudit = menuPages(buildMenuTree(resources)).some(page => page.componentKey === 'OPERATIONS_AUDIT');
  const fileId = query.get('file');
  return fileId
    ? <FileRecordDetailView key={fileId} fileId={fileId} canDownload={canDownload} canAudit={canAudit} onAuthorizationChanged={onAuthorizationChanged} />
    : <FileRecordList key={location.search} canDownload={canDownload} onAuthorizationChanged={onAuthorizationChanged} />;
}
