import { Tooltip } from 'antd';
import { useT } from '../i18n/react';

export function NavigationCollapseControl({ collapsed, onToggle }: {
  collapsed: boolean;
  onToggle: () => void;
}) {
  const t = useT('nav');
  const label = t(collapsed ? 'sidebar.show' : 'sidebar.hide');
  const accessibleLabel = t(collapsed ? 'sidebar.expand' : 'sidebar.collapse');

  return <div className="sidebar-foot">
    <span className="sidebar-product">{t('sidebar.product')}</span>
    <Tooltip title={<span>{label}</span>} placement="right" mouseEnterDelay={0.4}>
      <button type="button" className={`navigation-collapse${collapsed ? ' is-collapsed' : ''}`}
        aria-label={accessibleLabel} onClick={onToggle}>
        <svg aria-hidden="true" viewBox="0 0 20 20" fill="none" stroke="currentColor"
          strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
          <path d="m12.5 5-5 5 5 5" />
        </svg>
        <span className="navigation-collapse-label">{t('sidebar.hide')}</span>
      </button>
    </Tooltip>
  </div>;
}
