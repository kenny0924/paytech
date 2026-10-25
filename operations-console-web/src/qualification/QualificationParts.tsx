import { Alert, Button, Tag } from 'antd';
import { useNavigate } from 'react-router-dom';
import { date } from '../tenantAccess/TenantAccessView';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import { effectiveStatusColor, qualificationLink, subjectQualificationSlots } from './qualification';
import type { Qualification, RoleType } from './qualification';
import './qualification.css';

/** 实时资格状态标签（ELIGIBLE 绿、未登记灰、其余橙或红）。 */
export function EffectiveStatusTag({ status }: { status: string }) {
  const t = useT('qualification');
  return <Tag color={effectiveStatusColor(status)}>{enumLabel(t, 'enum.effectiveStatus', status)}</Tag>;
}

/**
 * 详情页的资格卡：按该角色允许的资格类型列出实时状态、有效期与决定来源，未登记的类型显示“未登记”。
 * <li>UNAVAILABLE：compliance 暂不可用，显示可重试提示，不阻塞主档；NOT_APPLICABLE（没有提供方）不渲染。</li>
 * <li>“在主体资格中查看”跳转主体资格页并预填该主体。</li>
 */
export function QualificationSummaryCard({ title, availability, items, roleType, roleId, onRetry }: {
  title: string; availability: 'AVAILABLE' | 'UNAVAILABLE' | 'NOT_APPLICABLE'; items: Qualification[]; roleType: RoleType; roleId: string; onRetry: () => void;
}) {
  const t = useT('qualification'), tCommon = useT('common');
  const navigate = useNavigate();
  if (availability === 'NOT_APPLICABLE') return null;
  return <div className="qualification-summary">
    <div className="qualification-summary-head"><strong>{title}</strong>
      <Button size="small" type="link" onClick={() => void navigate(qualificationLink(roleType, roleId))}>{t('summary.open')}</Button></div>
    {availability === 'UNAVAILABLE'
      ? <Alert showIcon type="warning" title={t('summary.unavailable')} action={<Button size="small" onClick={onRetry}>{tCommon('action.retry')}</Button>} />
      : subjectQualificationSlots(roleType, items).map(slot => <div key={slot.qualificationType} className="qualification-summary-row">
        <span>{enumLabel(t, 'enum.qualificationType', slot.qualificationType)}</span>
        <EffectiveStatusTag status={slot.qualification?.effectiveStatus ?? 'NOT_REGISTERED'} />
        {slot.qualification && <span className="cell-meta">{t('summary.meta', {
          from: date(slot.qualification.validFrom), to: date(slot.qualification.validTo),
          source: enumLabel(t, 'enum.decisionSource', slot.qualification.decisionSource) })}</span>}
      </div>)}
  </div>;
}
