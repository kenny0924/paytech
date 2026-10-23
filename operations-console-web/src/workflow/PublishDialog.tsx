import { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Modal, Spin } from 'antd';
import { CheckOutlined, InfoCircleOutlined, WarningOutlined } from '@ant-design/icons';
import { changeKindIcons } from './workflowIcons';
import { createPagingLoader } from '../api/paging';
import { session } from '../api/session';
import { problemTarget, runningInstanceNote, versionChanges } from './definitionModel';
import type { VersionChange, VersionContent } from './definitionModel';
import { decodeInstanceSummary, decodePage, decodeVersion, workflowPaths, COUNT_PAGE_SIZE } from './workflowApi';
import type { ActionTemplate, DefinitionSummary, Problem, VersionBody } from './workflowTypes';

const loadInstancePage = createPagingLoader(workflowPaths.instances, decodePage(decodeInstanceSummary));

/**
 * 发布确认（原型 12）：变更摘要（与当前发布版本比较）、检查结果（有问题不能发布）、运行中实例提示。
 * 打开前宿主已保存草稿，problems 为服务端结果。发布接口只收 expectedVersion，因此不提供「发布说明」。
 */
export function PublishDialog({ definition, nextVersionNo, draft, problems, templates, canReadInstances, saving, error, onLocate, onCancel, onConfirm }: {
  definition: DefinitionSummary; nextVersionNo: number; draft: VersionContent; problems: Problem[]; templates: Map<string, ActionTemplate>;
  canReadInstances: boolean; saving: boolean;
  /** 发布请求被拒绝时的说明（问题清单另行刷新）。 */
  error: string | null; onLocate: (target: { problem?: Problem; change?: VersionChange }) => void; onCancel: () => void; onConfirm: () => void;
}) {
  const current = definition.currentVersionNo;
  const [base, setBase] = useState<VersionBody | null | 'failed'>(current ? null : 'failed');
  const [running, setRunning] = useState<number | null>(null);
  // 先读发布版本（变更摘要），再读运行中实例数（只取 total），串行发出；实例数失败或 403 时不显示提示。
  useEffect(() => {
    const controller = new AbortController();
    void (async () => {
      if (current) {
        try { const value = await session.request(workflowPaths.version(definition.definitionId, current), { signal: controller.signal }, decodeVersion); if (!controller.signal.aborted) setBase(value); }
        catch { if (!controller.signal.aborted) setBase('failed'); }
      }
      if (!current || !canReadInstances || controller.signal.aborted) return;
      try {
        const page = await loadInstancePage({ page: 1, size: COUNT_PAGE_SIZE }, controller.signal, { scope: 'ALL', definitionId: definition.definitionId, status: 'RUNNING' });
        if (!controller.signal.aborted) setRunning(page.total);
      } catch { /* 没有实例查看权限或读取失败：不显示提示 */ }
    })();
    return () => controller.abort();
  }, [definition.definitionId, current, canReadInstances]);
  const changes = useMemo(() => {
    if (!base || base === 'failed') return null;
    const frozen = new Map((base.actionSnapshot?.templates ?? []).map(item => [item.templateId, item.templateName]));
    return versionChanges(base, draft, id => templates.get(id)?.templateName ?? frozen.get(id) ?? `#${id}`);
  }, [base, draft, templates]);
  const note = runningInstanceNote(running, current, nextVersionNo);
  return <Modal open width={720} title={<div><div>发布 v{nextVersionNo}</div>
    <div className="muted wf-modal-subtitle">{definition.definitionName} · {current ? `草稿基于 v${current}` : '首次发布'}</div></div>}
  onCancel={onCancel} footer={<div className="wf-modal-foot"><span /><span><Button onClick={onCancel}>取消</Button> <Button type="primary" loading={saving}
    disabled={problems.length > 0} onClick={onConfirm}>确认发布</Button></span></div>}>
    <section className="wf-publish-section">
      <div className="wf-publish-title">变更摘要 {current && <span className="muted">与 v{current} 相比{changes ? ` · ${changes.length} 处` : ''}</span>}</div>
      {!current ? <div className="wf-publish-box muted">首次发布，发布后即可发起</div>
        : base === null ? <div className="wf-publish-box"><Spin size="small" /></div>
        : !changes ? <div className="wf-publish-box muted">发布版本读取失败，无法生成变更摘要；不影响发布</div>
        : !changes.length ? <div className="wf-publish-box muted">与 v{current} 的流程图、启动表单相同（流程名称、说明或通知等其他设置可能有变化）</div>
        : <div className="wf-publish-changes">{changes.map((change, index) => <div key={index} className="wf-publish-change">
          <span className={`wf-change-icon is-${change.kind.toLowerCase()}`} aria-hidden>{changeKindIcons[change.kind]}</span>
          <span className="muted">{change.action}</span><strong className="wf-grow">「{change.subject}」</strong>
          <span className="muted">{change.area === 'GRAPH' ? '流程图' : '启动表单'}</span>
          {(change.nodeKey || change.edgeKey || change.area === 'START_FORM') && <Button type="link" size="small" onClick={() => onLocate({ change })}>查看</Button>}
        </div>)}</div>}
    </section>
    <section className="wf-publish-section">
      <div className="wf-publish-title">检查结果</div>
      {problems.length ? <div className="wf-publish-box is-warn">
        <div className="wf-warn-text"><WarningOutlined /> {problems.length} 个问题，处理后才能发布</div>
        {problems.map((problem, index) => <div key={index} className="wf-issue-row">
          <span className="wf-grow"><strong>{problemTarget(problem, draft.graph)}：</strong>{problem.message}</span>
          {(problem.nodeKey || problem.edgeKey || problem.itemKey) && <Button type="link" size="small" onClick={() => onLocate({ problem })}>定位</Button>}
        </div>)}
      </div> : <div className="wf-publish-box is-ok"><span className="wf-ok-text"><CheckOutlined /> 0 个问题</span> <span className="muted">节点、连线与表单检查均已通过，可以发布</span></div>}
    </section>
    {note && <div className="wf-publish-box is-info"><InfoCircleOutlined /> {note}</div>}
    {error && <Alert type="error" showIcon title={error} style={{ marginTop: 12 }} />}
  </Modal>;
}
