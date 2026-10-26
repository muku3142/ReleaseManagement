import { useState } from 'react';
import { Link, useNavigate } from '@tanstack/react-router';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import {
  DOCUMENT_TYPES,
  DOCUMENT_TYPE_LABELS,
  templatesOfType,
  useActivateTemplate,
  useClearMyDefaultTemplate,
  useDeleteTemplate,
  useDuplicateTemplate,
  useSetMyDefaultTemplate,
  useTemplates,
  type DocumentType,
  type Template,
  type TemplateSummary,
} from '../api/templates';
import { NewTemplateDialog } from '../components/NewTemplateDialog';
import { ConfirmDialog } from '../components/ConfirmDialog';

export function TemplatesRoute() {
  const navigate = useNavigate();
  const templates = useTemplates();

  const activate = useActivateTemplate();
  const duplicate = useDuplicateTemplate();
  const setMyDefault = useSetMyDefaultTemplate();
  const clearMyDefault = useClearMyDefaultTemplate();
  const remove = useDeleteTemplate();

  const [creatingType, setCreatingType] = useState<DocumentType | null>(null);
  const [deleting, setDeleting] = useState<TemplateSummary | null>(null);

  const problem = templates.isError && templates.error instanceof ApiError ? templates.error.problem : undefined;

  const actionProblem =
    (activate.isError && activate.error instanceof ApiError && activate.error.problem) ||
    (duplicate.isError && duplicate.error instanceof ApiError && duplicate.error.problem) ||
    (setMyDefault.isError && setMyDefault.error instanceof ApiError && setMyDefault.error.problem) ||
    (clearMyDefault.isError && clearMyDefault.error instanceof ApiError && clearMyDefault.error.problem) ||
    (remove.isError && remove.error instanceof ApiError && remove.error.problem) ||
    undefined;

  function goToTemplate(t: Template) {
    navigate({ to: '/templates/$templateId', params: { templateId: t.id } });
  }

  return (
    <div className="w-full">
      <div className="bg-white p-6">
        <h2 className="text-base font-semibold text-gray-900">Templates</h2>
      </div>

      {templates.isPending && (
        <div className="mt-6 flex items-center gap-3">
          <span
            className="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600"
            aria-hidden="true"
          />
          <p className="text-sm text-gray-600">Loading templates…</p>
        </div>
      )}

      {problem && (
        <div className="mt-6">
          <p role="alert" data-reason={problem.reason} className="text-sm text-red-600">
            {messageForReason(problem)}
          </p>
          <button
            type="button"
            onClick={() => templates.refetch()}
            className="mt-2 rounded border border-gray-300 px-3 py-1.5 text-sm font-semibold text-gray-700"
          >
            Try again
          </button>
        </div>
      )}

      {actionProblem && (
        <p role="alert" data-reason={actionProblem.reason} className="mt-4 text-sm text-red-600">
          {messageForReason(actionProblem)}
        </p>
      )}

      {!templates.isPending && !problem && templates.data && (
        // DOCUMENT_TYPES fixes the section order to the enum's natural generation order, and each
        // heading renders as exactly "Release Notes templates", "Runbook templates" or
        // "Approval templates" via DOCUMENT_TYPE_LABELS below - never hardcoded per type.
        <div className="mt-6 space-y-6">
          {DOCUMENT_TYPES.map((type) => {
            const rows = templatesOfType(templates.data.templates, type);
            return (
              <section key={type} className="rounded border border-gray-200 bg-white p-4">
                <h3 className="text-base font-semibold text-gray-900">{DOCUMENT_TYPE_LABELS[type]} templates</h3>

                {rows.length === 0 ? (
                  <div className="mt-2 rounded bg-gray-50 p-6 text-center">
                    <p className="text-base font-semibold text-gray-900">
                      No {DOCUMENT_TYPE_LABELS[type]} templates yet.
                    </p>
                    <p className="mt-2 text-sm text-gray-600">Create one to get started.</p>
                  </div>
                ) : (
                  <div className="mt-2">
                    {rows.map((t) => (
                      <div key={t.id} className="flex items-center gap-3 border-b border-gray-100 py-2 last:border-b-0">
                        <Link
                          to="/templates/$templateId"
                          params={{ templateId: t.id }}
                          className="font-semibold text-blue-600 hover:underline"
                        >
                          {t.name}
                        </Link>
                        {t.active && (
                          <span className="rounded-full bg-gray-800 px-2 py-0.5 text-sm text-white">Active</span>
                        )}
                        {t.myDefault && (
                          <span className="rounded-full border border-blue-600 px-2 py-0.5 text-sm text-blue-700">
                            My default
                          </span>
                        )}
                        {t.warningCount > 0 && (
                          <span
                            aria-label="Has unresolved placeholder warnings"
                            className="h-2 w-2 rounded-full bg-amber-600"
                          />
                        )}
                        <span className="grow" />
                        {!t.active && (
                          <button
                            type="button"
                            onClick={() => activate.mutate(t.id)}
                            className="text-sm text-gray-600 hover:text-gray-900"
                          >
                            Make active
                          </button>
                        )}
                        {t.myDefault ? (
                          <button
                            type="button"
                            onClick={() => clearMyDefault.mutate(t.id)}
                            className="text-sm text-gray-600 hover:text-gray-900"
                          >
                            Clear my default
                          </button>
                        ) : (
                          <button
                            type="button"
                            onClick={() => setMyDefault.mutate(t.id)}
                            className="text-sm text-gray-600 hover:text-gray-900"
                          >
                            Set as my default
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={() => duplicate.mutate(t.id, { onSuccess: goToTemplate })}
                          className="text-sm text-gray-600 hover:text-gray-900"
                        >
                          Duplicate
                        </button>
                        <button
                          type="button"
                          onClick={() => setDeleting(t)}
                          className="text-sm text-red-600 hover:text-red-700"
                        >
                          Delete
                        </button>
                      </div>
                    ))}
                  </div>
                )}

                <button
                  type="button"
                  onClick={() => setCreatingType(type)}
                  className="mt-3 rounded bg-blue-600 px-3 py-1.5 text-sm font-semibold text-white"
                >
                  + New template
                </button>
              </section>
            );
          })}
        </div>
      )}

      <NewTemplateDialog
        open={creatingType !== null}
        documentType={creatingType ?? 'RELEASE_NOTES'}
        onCreated={(t) => {
          setCreatingType(null);
          goToTemplate(t);
        }}
        onCancel={() => setCreatingType(null)}
      />

      <ConfirmDialog
        open={deleting !== null}
        title={`Delete ${deleting?.name}?`}
        body={
          deleting?.active
            ? `This permanently deletes the template. Documents already generated from it are not affected. This cannot be undone. This is the active ${
                deleting ? DOCUMENT_TYPE_LABELS[deleting.documentType] : ''
              } template. Deleting it will leave no active template for this document type until you mark another one active.`
            : 'This permanently deletes the template. Documents already generated from it are not affected. This cannot be undone.'
        }
        confirmLabel="Delete template"
        confirmClassName="rounded bg-red-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
        pending={remove.isPending}
        onConfirm={() => {
          if (!deleting) return;
          remove.mutate(deleting.id, { onSettled: () => setDeleting(null) });
        }}
        onCancel={() => setDeleting(null)}
      />
    </div>
  );
}
