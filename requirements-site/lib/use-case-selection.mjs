export function toggleUseCaseSelection(currentUseCaseId, nextUseCaseId) {
  return currentUseCaseId === nextUseCaseId ? null : nextUseCaseId;
}
