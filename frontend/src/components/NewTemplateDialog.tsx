import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import { DOCUMENT_TYPE_LABELS, useCreateTemplate, type DocumentType, type Template } from '../api/templates';

// Scoped to exactly one job - name-only creation for a document type fixed by the section that
// opened it (TMPL-01). No document-type selector exists here.
export function NewTemplateDialog(props: {
  open: boolean;
  documentType: DocumentType;
  onCreated: (t: Template) => void;
  onCancel: () => void;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const createTemplate = useCreateTemplate();
  const [name, setName] = useState('');

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (props.open && !dialog.open) {
      setName('');
      createTemplate.reset();
      dialog.showModal();
    }
    if (!props.open && dialog.open) dialog.close();
  }, [props.open]);

  const createProblem =
    createTemplate.isError && createTemplate.error instanceof ApiError ? createTemplate.error.problem : undefined;
  const nameError =
    createProblem?.reason === 'VALIDATION_FAILED' ? createProblem.fieldErrors?.name : undefined;
  const showCreateProblem =
    createProblem !== undefined && (createProblem.reason === 'TEMPLATE_DUPLICATE' || nameError === undefined);

  function handleSubmit() {
    createTemplate.mutate(
      { name: name.trim(), documentType: props.documentType },
      { onSuccess: props.onCreated },
    );
  }

  return (
    <dialog
      ref={dialogRef}
      onClose={props.onCancel}
      className="rounded border border-gray-200 bg-white p-6"
    >
      <h2 className="text-base font-semibold text-gray-900">
        New {DOCUMENT_TYPE_LABELS[props.documentType]} template
      </h2>

      <div className="mt-4">
        <label htmlFor="newTemplateName" className="block text-sm font-semibold text-gray-700">
          Name
        </label>
        <input
          id="newTemplateName"
          type="text"
          value={name}
          onChange={(event) => setName(event.target.value)}
          className="rounded border border-gray-300 px-3 py-2 text-sm focus-visible:ring-2 focus-visible:ring-blue-600"
        />
        {nameError && (
          <p className="mt-1 text-sm text-red-600" role="alert" data-reason="VALIDATION_FAILED">
            {nameError}
          </p>
        )}
        {showCreateProblem && (
          <p className="mt-1 text-sm text-red-600" role="alert" data-reason={createProblem.reason}>
            {messageForReason(createProblem)}
          </p>
        )}
      </div>

      <div className="mt-4 flex gap-3">
        <button
          type="button"
          onClick={props.onCancel}
          className="rounded border border-gray-300 px-4 py-2 text-sm text-gray-700"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={handleSubmit}
          disabled={createTemplate.isPending || name.trim() === ''}
          className="rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
        >
          Create template
        </button>
      </div>
    </dialog>
  );
}
