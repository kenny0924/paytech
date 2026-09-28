import { Tooltip } from 'antd';

export function NavigationCollapseControl({ collapsed, onToggle }: {
  collapsed: boolean;
  onToggle: () => void;
}) {
  const label = collapsed ? 'Show Navigation' : 'Hide Navigation';
  const accessibleLabel = collapsed ? '展开导航' : '收起导航';

  return <div className="sidebar-foot">
    <span className="sidebar-product" lang="en">Operations Console</span>
    <Tooltip title={<span lang="en">{label}</span>} placement="right" mouseEnterDelay={0.4}>
      <button type="button" className={`navigation-collapse${collapsed ? ' is-collapsed' : ''}`}
        aria-label={accessibleLabel} onClick={onToggle}>
        <svg aria-hidden="true" viewBox="0 0 20 20" fill="none" stroke="currentColor"
          strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
          <path d="m12.5 5-5 5 5 5" />
        </svg>
        <span className="navigation-collapse-label" lang="en">Hide Navigation</span>
      </button>
    </Tooltip>
  </div>;
}
