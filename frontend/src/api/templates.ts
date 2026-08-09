import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from './client';

export const DOCUMENT_TYPES = ['RELEASE_NOTES', 'RUNBOOK', 'APPROVAL'] as const;
export type DocumentType = (typeof DOCUMENT_TYPES)[number];

// UI-SPEC state 0 fixes both the labels and the section order (the enum's natural generation order).
export const DOCUMENT_TYPE_LABELS: Record<DocumentType, string> = {
  RELEASE_NOTES: 'Release Notes',
  RUNBOOK: 'Runbook',
  APPROVAL: 'Approval',
};

export type ChromeFields = {
  coverEnabled: boolean;
  coverTitle: string;
  coverSubtitle: string;
  headerText: string;
  footerText: string;
  pageNumbersEnabled: boolean;
};

export type LogoMetadata = {
  contentType: string;
  filename: string;
  byteSize: number;
  width: number;
  height: number;
  uploadedAt: string;
};

export type TemplateSummary = {
  id: string;
  name: string;
  documentType: DocumentType;
  active: boolean;
  hasLogo: boolean;
  warningCount: number;
  updatedAt: string;
};

export type Template = {
  id: string;
  name: string;
  documentType: DocumentType;
  body: string;
  active: boolean;
  chrome: ChromeFields;
  logo: LogoMetadata | null;
  warnings: string[];
  createdAt: string;
  createdBy: string;
  updatedAt: string;
  updatedBy: string;
};

export type TemplateListResponse = { templates: TemplateSummary[] };

export type PlaceholderEntry = {
  scope: 'ROOT' | 'ITEM' | 'GROUP' | 'COMMENT' | 'COMMIT' | 'PULL_REQUEST';
  token: string;
  kind: 'VARIABLE' | 'SECTION';
  description: string;
  tags: string[];
};

export type LogoLimits = {
  maxBytes: number;
  maxWidth: number;
  maxHeight: number;
  contentTypes: string[];
};

export type PlaceholderCatalog = { entries: PlaceholderEntry[]; logoLimits: LogoLimits };

// Mirrors the backend ReleaseView exactly - the same view-model the server render uses, so the two
// renderers cannot disagree about the data even though D-04-10 accepts they may disagree about
// rendering.
export type ItemView = {
  title: string;
  isManual: boolean;
  key: string;
  issueType: string;
  status: string;
  resolution: string;
  assignee: string;
  reporter: string;
  description: string;
  category: string;
  comments: { text: string }[];
  commits: { id: string; message: string; author: string; url: string }[];
  pullRequests: { id: string; title: string; status: string; url: string }[];
};
export type GroupView = { groupName: string; items: ItemView[] };
export type ReleaseView = {
  name: string;
  version: string;
  status: string;
  jiraProjectKey: string;
  createdAt: string;
  frozenAt: string;
  lastRefreshedAt: string;
  items: ItemView[];
  groups: GroupView[];
};

export const templatesQueryKey = () => ['templates'] as const;
export const templateQueryKey = (templateId: string) => ['template', templateId] as const;
export const placeholderCatalogQueryKey = () => ['placeholder-catalog'] as const;
export const renderContextQueryKey = (releaseId: string) => ['render-context', releaseId] as const;

export function useTemplates() {
  return useQuery({
    queryKey: templatesQueryKey(),
    queryFn: () => apiFetch<TemplateListResponse>('/api/templates'),
  });
}

export function useTemplate(templateId: string) {
  return useQuery({
    queryKey: templateQueryKey(templateId),
    queryFn: () => apiFetch<Template>(`/api/templates/${templateId}`),
    // A 404 is a permanent answer for a fixed id - retrying it three times only delays the
    // TEMPLATE_NOT_FOUND message the UI spec requires.
    retry: false,
  });
}

// The catalog is a code constant on the server and never changes at runtime.
export function usePlaceholderCatalog() {
  return useQuery({
    queryKey: placeholderCatalogQueryKey(),
    queryFn: () => apiFetch<PlaceholderCatalog>('/api/templates/catalog'),
    staleTime: Infinity,
  });
}

export function useRenderContext(releaseId: string | undefined) {
  return useQuery({
    queryKey: renderContextQueryKey(releaseId ?? ''),
    queryFn: () => apiFetch<ReleaseView>(`/api/templates/render-context/${releaseId}`),
    enabled: Boolean(releaseId),
    retry: false,
  });
}

// Shared success handling for every mutation that returns the whole Template - the backend
// returns it precisely so no follow-up read is needed.
function useTemplateMutation<TVars>(request: (vars: TVars) => Promise<Template>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: request,
    onSuccess: (template) => {
      queryClient.setQueryData(templateQueryKey(template.id), template);
      queryClient.invalidateQueries({ queryKey: ['templates'] });
    },
  });
}

export function useCreateTemplate() {
  return useTemplateMutation((vars: { name: string; documentType: DocumentType }) =>
    apiFetch<Template>('/api/templates', {
      method: 'POST',
      body: JSON.stringify(vars),
    }),
  );
}

export function useRenameTemplate() {
  return useTemplateMutation((vars: { templateId: string; name: string }) =>
    apiFetch<Template>(`/api/templates/${vars.templateId}/name`, {
      method: 'PUT',
      body: JSON.stringify({ name: vars.name }),
    }),
  );
}

export function useSaveTemplateContent() {
  return useTemplateMutation((vars: { templateId: string; body: string; chrome: ChromeFields }) =>
    apiFetch<Template>(`/api/templates/${vars.templateId}/content`, {
      method: 'PUT',
      body: JSON.stringify({ body: vars.body, chrome: vars.chrome }),
    }),
  );
}

export function useActivateTemplate() {
  return useTemplateMutation((templateId: string) =>
    apiFetch<Template>(`/api/templates/${templateId}/activate`, { method: 'POST' }),
  );
}

export function useDuplicateTemplate() {
  return useTemplateMutation((templateId: string) =>
    apiFetch<Template>(`/api/templates/${templateId}/duplicate`, { method: 'POST' }),
  );
}

export function useUploadLogo() {
  return useTemplateMutation((vars: { templateId: string; file: File }) => {
    const form = new FormData();
    form.append('file', vars.file);
    return apiFetch<Template>(`/api/templates/${vars.templateId}/logo`, {
      method: 'POST',
      body: form,
    });
  });
}

export function useRemoveLogo() {
  return useTemplateMutation((templateId: string) =>
    apiFetch<Template>(`/api/templates/${templateId}/logo`, { method: 'DELETE' }),
  );
}

// DELETE /api/templates/{id} returns 204 with no body, so this is the one mutation that cannot
// reuse useTemplateMutation.
export function useDeleteTemplate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (templateId: string) => apiFetch<void>(`/api/templates/${templateId}`, { method: 'DELETE' }),
    onSuccess: (_data, templateId) => {
      queryClient.invalidateQueries({ queryKey: ['templates'] });
      // type: 'inactive' - removing a query that still has a mounted observer makes that observer
      // re-create and refetch it, firing a GET against the template that was just deleted and
      // painting TEMPLATE_NOT_FOUND for a frame before the navigation lands.
      queryClient.removeQueries({ queryKey: templateQueryKey(templateId), type: 'inactive' });
    },
  });
}

export function templatesOfType(templates: TemplateSummary[], type: DocumentType): TemplateSummary[] {
  return templates.filter((t) => t.documentType === type).sort((a, b) => a.name.localeCompare(b.name));
}

// UI-SPEC's rejection copy interpolates the backend-declared limit, never a hardcoded number.
export function formatMaxSize(maxBytes: number): string {
  const mb = maxBytes / (1024 * 1024);
  return Number.isInteger(mb) ? `${mb}MB` : `${Math.round(maxBytes / 1024)}KB`;
}
