import { useMemo } from 'react';
import { Button, Segmented, Select, Switch, Tag, Tooltip } from 'antd';
import { useT } from '../i18n/react';
import { emptyJointColumn, jointEffect } from './jointModel';
import type { JointColumnRow, JointDraft, JointParticipation, JointProblem, KeyTypeInfo } from './jointModel';
import { groupKeyTypes, keyRoleLabel, keyRoles } from './keyTypeModel';
import type { KeyRole } from './keyTypeModel';
import type { ColumnRow, ParameterRow } from './templateModel';
import { Note } from './DataQueryParts';
import './jointQuery.css';

/**
 * 模板编辑器“联合查询”页签（原型 05）：参与开关、行主体键类型、按什么查（参数 → 键类型）、结果里带出的单号
 * （列 → 键类型 + 链路 / 维度）。错误与提醒就地显示，底部一句话说明保存后的效果。参数 / 约束 / 列页签不变。
 */
export function JointPanel({ value, onChange, parameters, columns, keyTypes, keyTypesFailed, problems, participation, readOnly, focusField }: {
  value: JointDraft; onChange: (value: JointDraft) => void; parameters: readonly ParameterRow[]; columns: readonly ColumnRow[];
  keyTypes: ReadonlyMap<string, KeyTypeInfo>; keyTypesFailed: boolean; problems: readonly JointProblem[]; participation: JointParticipation | null;
  readOnly: boolean; focusField?: string;
}) {
  const t = useT('data-query');
  const set = (patch: Partial<JointDraft>) => onChange({ ...value, ...patch });
  const setColumn = (index: number, patch: Partial<JointColumnRow>) => set({ columns: value.columns.map((row, i) => i === index ? { ...row, ...patch } : row) });
  const keyTypeOptions = useMemo(() => groupKeyTypes([...keyTypes.values()].filter(item => item.status === 'ACTIVE'))
    .map(group => ({ label: group.groupName, title: group.groupName,
      options: group.items.map(item => ({ value: item.keyTypeCode, label: item.keyTypeName, search: `${item.keyTypeName} ${item.keyTypeCode}` })) })), [keyTypes]);
  // 已引用但停用或不存在的键类型仍显示其名称（或编码），由错误提示说明。
  const withCurrent = (code: string | null) => !code || keyTypeOptions.some(group => group.options.some(option => option.value === code))
    ? keyTypeOptions : [...keyTypeOptions, { label: t('joint.unavailableGroup'), title: '', options: [{ value: code, label: keyTypes.get(code)?.keyTypeName ?? code, search: code }] }];
  const filter = (input: string, option?: { search?: string; label?: unknown }) => !!option?.search?.toLowerCase().includes(input.toLowerCase());
  const parameterOptions = parameters.filter(row => row.name.trim() && row.type !== 'FRAGMENT')
    .map(row => ({ value: row.name.trim(), label: t('joint.parameterOption', { name: row.name.trim() }) }));
  const columnOptions = columns.filter(row => row.name.trim()).map(row => ({ value: row.name.trim(), label: row.name.trim() }));
  const problemsAt = (prefix: string) => problems.filter(problem => problem.field === prefix || problem.field.startsWith(`${prefix}.`));
  const errorCount = problems.filter(problem => problem.level === 'ERROR').length;
  const focused = (prefix: string) => !!focusField && (focusField === prefix || focusField.startsWith(`${prefix}.`) || focusField.startsWith(`${prefix}[`));
  const subject = value.subjectKeyType ? keyTypes.get(value.subjectKeyType) : undefined;

  return <div className="dq-joint">
    <div className="dq-joint-switch">
      <Switch checked={value.enabled} disabled={readOnly} onChange={enabled => set({ enabled })} aria-label={t('joint.participate')} />
      <div><strong>{t('joint.participate')}</strong><div className="muted">{t('joint.participateHint')}</div></div>
      {participation && <Tooltip title={participation.reason ?? undefined}>
        <Tag color={participation.participating ? 'green' : 'default'}>{participation.participating ? t('joint.participating') : t('joint.notParticipating')}</Tag>
      </Tooltip>}
    </div>
    {keyTypesFailed && <Note tone="warning">{t('joint.keyTypesFailed')}</Note>}
    {value.enabled && <>
      <section className={`dq-joint-section${focused('joint.subjectKeyType') ? ' is-focused' : ''}`}>
        <div className="dq-joint-label">{t('joint.subject')}</div>
        <Select value={value.subjectKeyType ?? undefined} disabled={readOnly} showSearch={{ filterOption: filter }} placeholder={t('joint.subjectPlaceholder')}
          options={withCurrent(value.subjectKeyType)} onChange={subjectKeyType => set({ subjectKeyType })}
          status={problemsAt('joint.subjectKeyType').some(problem => problem.level === 'ERROR') ? 'error' : undefined} />
        <div className="muted dq-joint-help">{subject ? t('joint.subjectHint', { group: subject.groupName }) : t('joint.subjectHintEmpty')}</div>
        <ProblemLines problems={problemsAt('joint.subjectKeyType')} />
      </section>
      <section className={`dq-joint-section${focused('joint.keyParameter') ? ' is-focused' : ''}`}>
        <div className="dq-joint-label">{t('joint.keyParameter')}</div>
        <div className="dq-joint-parameter">
          <Select value={value.keyParameterName ?? undefined} disabled={readOnly} placeholder={t('joint.parameterPlaceholder')} options={parameterOptions}
            notFoundContent={t('joint.noParameters')} onChange={keyParameterName => set({ keyParameterName })}
            status={problemsAt('joint.keyParameter.name').some(problem => problem.level === 'ERROR') ? 'error' : undefined} />
          <span className="muted">{t('joint.is')}</span>
          <Select value={value.keyParameterKeyType ?? undefined} disabled={readOnly} showSearch={{ filterOption: filter }} placeholder={t('joint.keyTypePlaceholder')}
            options={withCurrent(value.keyParameterKeyType)} onChange={keyParameterKeyType => set({ keyParameterKeyType })}
            status={problemsAt('joint.keyParameter.keyType').some(problem => problem.level === 'ERROR') ? 'error' : undefined} />
        </div>
        <ProblemLines problems={problemsAt('joint.keyParameter')} />
      </section>
      <section className="dq-joint-section">
        <div className="dq-joint-label is-row"><span>{t('joint.columns')}</span>
          {!readOnly && <Button type="link" size="small" onClick={() => set({ columns: [...value.columns, emptyJointColumn()] })}>{t('joint.addColumn')}</Button>}</div>
        <div className="muted dq-joint-help">{t('joint.columnsHint')}</div>
        {value.columns.length === 0 && <div className="muted dq-joint-empty">{t('joint.noColumns')}</div>}
        {value.columns.map((row, index) => {
          const own = problemsAt(`joint.columns[${index}]`);
          const level = own.some(problem => problem.level === 'ERROR') ? 'error' : own.length ? 'warning' : null;
          return <div key={row.key} className={`dq-joint-column-item${focused(`joint.columns[${index}]`) ? ' is-focused' : ''}`}>
            <div className={`dq-joint-column${level ? ` is-${level}` : ''}`}>
              <Select className="mono" variant="borderless" value={row.name || undefined} disabled={readOnly} showSearch placeholder={t('joint.columnPlaceholder')}
                options={columnOptions} notFoundContent={t('joint.noDeclaredColumns')} onChange={name => setColumn(index, { name })} />
              <span className="muted" aria-hidden>→</span>
              <Select variant="borderless" value={row.keyType ?? undefined} disabled={readOnly} showSearch={{ filterOption: filter }} placeholder={t('joint.keyTypePlaceholder')}
                popupMatchSelectWidth={false} options={withCurrent(row.keyType)}
                onChange={(keyType: string) => setColumn(index, { keyType, keyRole: keyTypes.get(keyType)?.defaultRole ?? row.keyRole })} />
              <Segmented size="small" value={row.keyRole} disabled={readOnly} options={keyRoles.map(role => ({ value: role, label: keyRoleLabel(role) }))}
                onChange={keyRole => setColumn(index, { keyRole: keyRole as KeyRole })} />
              {!readOnly ? <Button size="small" type="text" aria-label={t('joint.removeColumn')} onClick={() => set({ columns: value.columns.filter((_, i) => i !== index) })}>✕</Button> : <span />}
            </div>
            <ProblemLines problems={own} />
          </div>;
        })}
      </section>
    </>}
    <div className={`dq-joint-effect${value.enabled && errorCount ? ' is-warning' : ''}`}>{jointEffect(value, keyTypes, errorCount)}</div>
  </div>;
}

function ProblemLines({ problems }: { problems: readonly JointProblem[] }) {
  return <>{problems.map((problem, index) => <div key={index} className={problem.level === 'ERROR' ? 'dq-text-error dq-joint-problem' : 'dq-joint-problem is-warning'}>
    {problem.message}</div>)}</>;
}
