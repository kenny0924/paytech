import type { ReleaseStaticCheck, TestCandidateDetail } from './riskApiModels.ts';
import { validateKey } from './riskCommands.ts';

export type CandidatePresentation =
  | { kind: 'READY_TO_VALIDATE'; expectedVersion: 1 }
  | { kind: 'RECOVERABLE_BY_CURRENT_ACTOR'; expectedVersion: 1; key: string }
  | { kind: 'RECOVERY_OWNED_BY_OTHER_ACTOR'; ownerActorId: string }
  | { kind: 'WAITING_FOR_SHADOW'; checks: readonly ReleaseStaticCheck[] }
  | { kind: 'REJECTED'; checks: readonly ReleaseStaticCheck[] };

/** The response-local decoder owns the canonical history and eight ordered checks. */
export function classifyTestCandidate(detail: TestCandidateDetail, currentActorId: string): CandidatePresentation {
  if (detail.status === 'DRAFT' && detail.version === 1) {
    return { kind: 'READY_TO_VALIDATE', expectedVersion: 1 };
  }
  if (detail.status === 'VALIDATING' && detail.version === 2) {
    if (detail.waitingForShadow && detail.allStaticChecksPassed === true) {
      return { kind: 'WAITING_FOR_SHADOW', checks: detail.staticChecks };
    }
    if (!detail.waitingForShadow && detail.allStaticChecksPassed === null) {
      const ownerActorId = detail.stateChanges[1]!.actorId;
      return ownerActorId === currentActorId
        ? { kind: 'RECOVERABLE_BY_CURRENT_ACTOR', expectedVersion: 1, key: validateKey(detail.releaseId, 1) }
        : { kind: 'RECOVERY_OWNED_BY_OTHER_ACTOR', ownerActorId };
    }
  }
  if (detail.status === 'REJECTED' && detail.version === 3) {
    return { kind: 'REJECTED', checks: detail.staticChecks };
  }
  throw new Error('候选版本状态无法识别，请刷新后重试。');
}
