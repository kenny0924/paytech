import { Descriptions, Tag } from 'antd';
import { ExportOutlined } from '@ant-design/icons';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';
import { fieldLabel, valueView } from './schemaLabel';
import type { ValueView } from './schemaLabel';

/** 一个资料值：链接在新标签页打开（不带来源页信息，悬停显示完整网址），多值目录为"名称 编码"标签，空值为灰字说明。 */
function ProfileValue({ view }: { view: ValueView }) {
  const t = useT('client-review');
  switch (view.kind) {
    case 'empty': return <span className="muted">{view.text}</span>;
    case 'link': return <a className="profile-link" href={view.href} target="_blank" rel="noopener noreferrer" title={view.href}>
      <span className="profile-link-text">{view.text}</span><ExportOutlined className="profile-link-icon" aria-label={t('application.openLink')} /></a>;
    case 'tag': return <Tag>{view.text}</Tag>;
    case 'tags': return <span className="profile-tags">{view.tags.map(tag => <Tag key={tag.code} color="blue">
      {tag.label}{tag.label !== tag.code && <span className="profile-tag-code">{tag.code}</span>}</Tag>)}</span>;
    default: return <>{view.text}</>;
  }
}

/** 一组资料值（Descriptions 条目）：字段名沿用审核页文案，值按 valueView 展示；不适用的行（如非预售的天数）不显示。 */
export function profileItems(t: Translate, tSchema: Translate, group: string, fields: Record<string, string | null>, jurisdictionCode: string | null) {
  return Object.entries(fields).flatMap(([field, value]) => {
    const view = valueView(tSchema, group, field, value, jurisdictionCode, fields);
    return view ? [{ key: field, label: fieldLabel(t, tSchema, group, field), children: <ProfileValue view={view} /> }] : [];
  });
}

/** 资料分组的 Descriptions（审核"申请"页签与 Client 详情共用）。 */
export function ProfileDescriptions({ group, fields, jurisdictionCode }: { group: string; fields: Record<string, string | null>; jurisdictionCode: string | null }) {
  const t = useT('client-review'), tSchema = useT('onboarding-schema');
  return <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 3 }} items={profileItems(t, tSchema, group, fields, jurisdictionCode)} />;
}
