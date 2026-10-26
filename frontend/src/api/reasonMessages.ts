import type { ProblemDetail } from './client';

// The only place failure text is chosen - keyed on the machine-readable `reason`
// property and never on message matching (D-04). Later phases must extend this
// table rather than adding ad-hoc error text.
export const REASON_MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: 'Your Jira credential is no longer valid. Enter a current Jira API token.',
  ACCOUNT_MISMATCH: 'That API token belongs to a different Jira account. Sign out and sign in as that account.',
  SITE_NOT_CONFIGURED: 'No Jira Cloud site is configured yet. Set one on the Settings screen first.',
  JIRA_UNREACHABLE: 'The configured Jira site could not be reached. Check the site URL and your network, then try again.',
  JIRA_RATE_LIMITED: 'Jira is rate-limiting requests. Wait a moment and try again.',
  JIRA_FORBIDDEN: 'That Jira account is not permitted to access the configured site.',
  JIRA_ERROR: 'Jira returned an unexpected error. Try again shortly.',
  RESULT_SET_TOO_LARGE: 'This query matched too many tickets to load. Narrow your fix version or JQL filter.',
  ADO_UNAUTHORIZED: 'Azure DevOps rejected this token. Use a token scoped to one organization with Code (Read) access.',
  ADO_ORGANIZATION_NOT_FOUND: 'No Azure DevOps organization with that name is reachable with this token.',
  ADO_RATE_LIMITED: 'Azure DevOps is rate-limiting requests. Wait a moment and try again.',
  ADO_UNREACHABLE: 'Azure DevOps could not be reached. Try again later.',
  ADO_ERROR: 'Azure DevOps returned an unexpected error.',
  ADO_TOKEN_REQUIRED: 'Enter the token: no Azure DevOps token is stored for this organization yet.',
  RELEASE_NOT_FOUND: "This release doesn't exist or has been deleted.",
  MANUAL_ITEM_NOT_FOUND: "This manual item doesn't exist or has already been removed.",
  RELEASE_FROZEN: 'This release is frozen and can no longer be changed.',
  UNFREEZE_RELEASE_NOT_FROZEN: 'This release is already open.',
  UNFREEZE_HAS_PROMOTIONS: "Can't unfreeze: this release has promotion history. Create a new release to change scope.",
  UNFREEZE_HAS_INSTALLS: "Can't unfreeze: client installs are recorded for this release. Create a new release to change scope.",
  RELEASE_DUPLICATE: 'A release with this name and version already exists. Choose a different name or version.',
  REFRESH_INCOMPLETE:
    'Jira did not return every ticket on this release, so nothing was refreshed. Remove the tickets that no longer exist in Jira, or check your Jira permissions, then try again.',
  SESSION_EXPIRED: 'Your session has ended. Sign in again.',
  INVALID_SESSION: 'Your session has ended. Sign in again.',
  UNAUTHENTICATED: 'Your session has ended. Sign in again.',
  FORBIDDEN: 'That request was rejected. Reload the page and try again.',
  TEMPLATE_NOT_FOUND: "This template doesn't exist or has been deleted.",
  TEMPLATE_DUPLICATE: 'A template with this name already exists for this document type. Choose a different name.',
  TEMPLATE_MALFORMED: 'Template not saved — check the placeholders and sections in this template.',
  RENDER_BOUND_EXCEEDED:
    'Template not saved — validation took too long or produced too much content to render safely. Simplify the template (fewer nested sections or shorter loops) and try again.',
  LOGO_INVALID_TYPE:
    "This file isn't a supported image. Upload a PNG or JPEG — SVG and other formats aren't accepted.",
  LOGO_TOO_LARGE: 'This image is too large.',
  LOGO_DIMENSIONS_EXCEEDED: 'This image is too large.',
  DOCUMENT_NOT_FOUND: "This document doesn't exist yet.",
  NO_TEMPLATE: 'No template is available for this document type. Create one from Templates, or choose one, before generating.',
  TEMPLATE_TYPE_MISMATCH: 'The chosen template is for a different document type. Choose a template of this document type.',
  CHOSEN_TEMPLATE_NOT_FOUND: 'The chosen template no longer exists. Choose another template and try again.',
  RELEASE_NOT_FROZEN: 'Only frozen releases can be promoted. Freeze the release first.',
  PROMOTION_STAGE_EARLIER:
    "Can't promote to an earlier stage. Choose the current stage or a later one; to go back, record a rollback on the current stage.",
  PROMOTION_TARGET_RETIRED: 'That stage has been retired from the environment ladder and can no longer be targeted.',
  PROMOTION_ROLLBACK_WITHOUT_SUCCESS:
    'A rollback needs a successful deployment to that stage dated at or before the rollback.',
  PROMOTION_ALREADY_SUPERSEDED: 'This entry has already been corrected. Correct the latest correction instead.',
  PROMOTION_DEPLOYED_AT_INVALID: 'The deployment time cannot be in the future or before the release was frozen.',
  PROMOTION_OUTCOME_NOT_RECORDABLE: 'An entry can only be marked VOIDED by correcting it.',
  PROMOTION_NOT_FOUND: "This promotion entry doesn't exist on this release.",
  STAGE_NOT_FOUND: "This environment stage doesn't exist.",
  STAGE_IN_USE: 'Promotions reference this stage, so it can be retired but not deleted.',
  STAGE_NAME_DUPLICATE: 'A stage with this name already exists, including retired stages.',
  PRODUCTION_STAGE_PROTECTED:
    'The Production stage cannot be retired or deleted. Move the Production flag to another stage first.',
  LAST_ACTIVE_STAGE: 'The ladder must keep at least one active stage.',
  STAGE_RETIRED: 'A retired stage cannot carry the Production flag. Restore it first.',
  STAGE_ORDER_MISMATCH: 'The new order must list every stage exactly once.',
  PRODUCT_NAME_DUPLICATE: 'A product with this name already exists. Names are not case-sensitive. Choose a different name.',
  CLIENT_NAME_DUPLICATE: 'A client with this name already exists. Names are not case-sensitive. Choose a different name.',
  PRODUCT_ARCHIVED: 'That product is archived. Unarchive it first.',
  CLIENT_ARCHIVED: 'That client is archived. Unarchive it first.',
  CLIENT_LINK_HAS_INSTALLS:
    "This client has install records for this product, so the link can't be removed. Archive the client instead to stop new installs.",
  PRODUCT_HAS_INSTALLS_ON_RELEASE: "Can't remove: installs are recorded for this product on this release.",
  PRODUCT_NOT_FOUND: "This product doesn't exist.",
  CLIENT_NOT_FOUND: "This client doesn't exist.",
  INSTALL_NOT_FOUND: "This install record doesn't exist on this release.",
  INSTALL_RELEASE_NOT_FROZEN: 'Installs can only be recorded for a frozen release. Freeze the release first.',
  INSTALL_PRODUCT_NOT_ON_RELEASE: "This release doesn't cover that product. Add the product to the release first.",
  INSTALL_PRODUCT_ARCHIVED: "That product is archived and can't receive new installs.",
  INSTALL_CLIENTS_REJECTED: "Nothing was recorded. One or more clients can't receive this install.",
  INSTALL_ALREADY_SUPERSEDED: 'This install has already been corrected or voided. Correct the latest entry instead.',
  INSTALL_INSTALLED_AT_INVALID: "Installed time can't be in the future.",
  INSTALL_OUTCOME_NOT_RECORDABLE: 'An install can only be marked Voided by voiding it.',
  RELEASE_PRODUCT_REQUIRED: 'Choose at least one product for this release.',
  RELEASE_LAST_PRODUCT: 'A release needs at least one product.',
  ROADMAP_REACH_INVALID: 'Enter a whole number, 0 or more.',
  ROADMAP_IMPACT_INVALID: 'Choose an Impact of 0.25, 0.5, 1, 2 or 3.',
  ROADMAP_CONFIDENCE_INVALID: 'Choose a Confidence of High 100%, Medium 80% or Low 50%.',
  ROADMAP_EFFORT_INVALID: 'Enter a number above 0, for example 0.5.',
  ROADMAP_TOO_MANY_ITEMS: 'Too many items at once. Add up to 100 at a time.',
  ROADMAP_REFRESH_TOO_LARGE:
    'There are too many Jira items to refresh at once. Archive items you no longer track, then try again.',
  ROADMAP_PRODUCT_ARCHIVED: 'That product is archived. Choose an active product.',
  ROADMAP_ITEM_NOT_JIRA: 'This item was added by hand, so there is nothing to refresh from Jira.',
  ROADMAP_ITEM_ARCHIVED: 'This item is archived. Restore it before changing its inputs.',
  ROADMAP_JIRA_KEY_DUPLICATE: 'That issue is already on the roadmap.',
  ROADMAP_ITEM_NOT_FOUND: 'That roadmap item no longer exists. Reload the page.',
  RELEASE_PACKAGES_URL_INVALID: 'Use an https link to OneDrive or SharePoint (sharepoint.com, onedrive.live.com or 1drv.ms).',
  PRODUCT_JIRA_KEY_INVALID:
    "That isn't a valid Jira project key. Keys are letters and digits, starting with a letter, for example CNV.",
  PRODUCT_JIRA_KEY_TAKEN: 'That Jira project key already belongs to another product. A key can belong to one product only.',
  BRIEFING_NO_JIRA_CREDENTIAL: 'You have no stored Jira credential. Sign in with a Jira API token first.',
  BRIEFING_SETTINGS_INVALID: 'Some briefing settings are not valid. Check the highlighted fields and try again.',
  BRIEFING_TASK_NOT_RUNNABLE: "This briefing can't be run by the app. Only the three Jira briefings run here.",
  BRIEFING_TASK_DISABLED: 'Enable this briefing to run it.',
  EXPORT_FAILED: "The document couldn't be exported. Try again, and check the server logs if it persists.",
};

export function messageForReason(problem: ProblemDetail): string {
  if (problem.reason === 'VALIDATION_FAILED') {
    const fieldMessages = problem.fieldErrors ? Object.values(problem.fieldErrors) : [];
    return fieldMessages.length > 0 ? fieldMessages.join(' ') : 'Check the values you entered.';
  }
  return REASON_MESSAGES[problem.reason] ?? 'Something went wrong. Try again, and check the server logs if it persists.';
}
