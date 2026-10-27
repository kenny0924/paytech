import type { ConsoleResource } from '../api/types';
import { OnboardingCodesView } from '../onboardingCodes/OnboardingCodesView';

/** 入驻码：/client-onboarding/codes。 */
export function OnboardingCodes(props: { resources: ConsoleResource[]; onAuthorizationChanged: () => void }) {
  return <OnboardingCodesView {...props} />;
}
