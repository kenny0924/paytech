import type { ReactNode } from 'react';
import {
  AlignLeftOutlined, CalendarOutlined, CarryOutOutlined, CheckSquareOutlined, CloseOutlined, DatabaseOutlined, DownSquareOutlined, EditOutlined,
  FontSizeOutlined, FormOutlined, LinkOutlined, MinusOutlined, NumberOutlined, PaperClipOutlined, PlayCircleOutlined, PlusOutlined, StopOutlined,
  SwitcherOutlined, UploadOutlined,
} from '@ant-design/icons';
import type { ActionType, FieldType, NodeType } from './workflowTypes';

/** 工作流页面统一的线性图标（@ant-design/icons），替代原来的文本符号。 */
export const actionTypeIcons: Record<ActionType, ReactNode> = {
  FORM: <FormOutlined />, FILE_UPLOAD: <UploadOutlined />, PAGE_TASK: <LinkOutlined />, MANUAL_CONFIRM: <CheckSquareOutlined />, DATA_QUERY: <DatabaseOutlined />,
};

export const nodeTypeIcons: Record<NodeType, ReactNode> = {
  START: <PlayCircleOutlined />, END: <StopOutlined />, TASK: <CarryOutOutlined />, EXCLUSIVE: <CloseOutlined />,
  PARALLEL_SPLIT: <PlusOutlined />, PARALLEL_JOIN: <PlusOutlined />,
};

export const fieldTypeIcons: Record<FieldType, ReactNode> = {
  TEXT: <FontSizeOutlined />, LONG_TEXT: <AlignLeftOutlined />, NUMBER: <NumberOutlined />, BOOLEAN: <SwitcherOutlined />, ENUM: <DownSquareOutlined />,
  DATE: <CalendarOutlined />, FILE: <PaperClipOutlined />,
};

export const changeKindIcons: Record<'ADDED' | 'REMOVED' | 'CHANGED', ReactNode> = {
  ADDED: <PlusOutlined />, REMOVED: <MinusOutlined />, CHANGED: <EditOutlined />,
};
