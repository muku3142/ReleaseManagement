import { useRef, useState } from 'react';
import { ApiError } from '../api/client';
import { JIRA_PROJECT_KEY, parseJiraKeys, useSetProductJiraProjects, type Product } from '../api/products';
import { messageForReason } from '../api/reasonMessages';
import { PRODUCT_CHIP } from './ProductsEditor';
import { SMALL_BUTTON } from './EnvironmentLadderSection';

const FIELD = 'w-full rounded border border-gray-300 px-3 py-2 text-sm';
const HELPER =
  "The Jira project keys this product's briefings read, for example CNV. Separate several keys with commas. Leave empty to skip this product.";
const INVALID_SUFFIX = 'Keys are letters and digits, starting with a letter, for example CNV.';

export function ProductJiraProjectsEditor(props: { product: Product; open: boolean; onToggle: () => void }) {
  const { product, open, onToggle } = props;
  const keys = product.jiraProjectKeys ?? [];
  const editorId = `product-${product.id}-jira-editor`;
  const button = useRef<HTMLButtonElement>(null);
  const [announcement, setAnnouncement] = useState('');

  function toggle() {
    setAnnouncement('');
    onToggle();
  }

  function closeAfterSave() {
    onToggle();
    setAnnouncement(`Jira projects saved for ${product.name}.`);
    button.current?.focus();
  }

  function cancel() {
    onToggle();
    button.current?.focus();
  }

  return (
    <div className="w-full basis-full">
      <div className="flex flex-wrap items-center gap-2 text-xs">
        {keys.length > 0 ? (
          <>
            <span className="text-gray-600">{keys.length > 1 ? 'Jira projects:' : 'Jira project:'}</span>
            {keys.map((key) => (
              <span key={key} className={PRODUCT_CHIP}>
                {key}
              </span>
            ))}
          </>
        ) : (
          <>
            <span className="text-gray-500">No Jira project</span>
            {!product.archived && <span className="text-gray-600">Briefings skip this product.</span>}
          </>
        )}
        {product.archived ? (
          <span className="text-gray-600">Archived products don&apos;t run briefings.</span>
        ) : (
          <button
            type="button"
            ref={button}
            aria-expanded={open}
            aria-controls={editorId}
            aria-label={`${keys.length > 0 ? 'Edit Jira projects' : 'Set Jira project'} for ${product.name}`}
            onClick={toggle}
            className={SMALL_BUTTON}
          >
            {keys.length > 0 ? 'Edit Jira projects' : 'Set Jira project'}
          </button>
        )}
      </div>
      <p role="status" aria-live="polite" className="sr-only">
        {announcement}
      </p>
      {open && !product.archived && <Editor product={product} editorId={editorId} onSaved={closeAfterSave} onCancel={cancel} />}
    </div>
  );
}

function Editor(props: { product: Product; editorId: string; onSaved: () => void; onCancel: () => void }) {
  const { product, editorId } = props;
  const initial = product.jiraProjectKeys ?? [];
  const save = useSetProductJiraProjects();
  const [value, setValue] = useState(initial.join(', '));
  const [invalidKey, setInvalidKey] = useState<string | null>(null);

  const parsed = parseJiraKeys(value);
  const unchanged = parsed.length === initial.length && parsed.every((key, i) => key === initial[i]);
  const problem = save.isError && save.error instanceof ApiError ? save.error.problem : undefined;

  function serverMessage(): string | null {
    if (!save.isError) return null;
    if (problem?.reason === 'PRODUCT_JIRA_KEY_TAKEN' && problem.key && problem.productName) {
      return `“${problem.key}” already belongs to ${problem.productName}.`;
    }
    return problem ? messageForReason(problem) : 'Something went wrong. Try again, and check the server logs if it persists.';
  }

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const bad = parsed.find((key) => !JIRA_PROJECT_KEY.test(key));
    setInvalidKey(bad ?? null);
    if (bad !== undefined) return;
    save.mutate({ id: product.id, keys: parsed }, { onSuccess: props.onSaved });
  }

  const message = invalidKey !== null ? `“${invalidKey}” isn't a valid Jira project key. ${INVALID_SUFFIX}` : serverMessage();
  const inputId = `${editorId}-input`;

  return (
    <form id={editorId} onSubmit={handleSubmit} className="mt-3 rounded border border-gray-200 bg-white p-4">
      <label htmlFor={inputId} className="text-sm font-semibold text-gray-700">
        Jira project keys
      </label>
      <input
        id={inputId}
        type="text"
        autoComplete="off"
        autoCapitalize="characters"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onBlur={(e) => setValue(e.target.value.toUpperCase())}
        aria-describedby={`${inputId}-help${message ? ` ${inputId}-error` : ''}`}
        className={`mt-1 ${FIELD}`}
      />
      <p id={`${inputId}-help`} className="mt-1 text-xs text-gray-600">
        {HELPER}
      </p>
      {message && (
        <p
          id={`${inputId}-error`}
          role="alert"
          data-reason={invalidKey !== null ? 'PRODUCT_JIRA_KEY_INVALID' : problem?.reason}
          className="mt-1 text-xs text-red-600"
        >
          {message}
        </p>
      )}
      <div className="mt-3 flex items-center gap-2">
        <button
          type="submit"
          disabled={unchanged || save.isPending}
          className="rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
        >
          Save Jira projects
        </button>
        <button
          type="button"
          onClick={props.onCancel}
          className="rounded border border-gray-300 px-4 py-2 text-sm text-gray-700"
        >
          Cancel
        </button>
        {save.isPending && (
          <span className="flex items-center gap-2 text-xs text-gray-600">
            <span
              className="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600 motion-reduce:animate-none"
              aria-hidden="true"
            />
            Saving…
          </span>
        )}
      </div>
    </form>
  );
}
