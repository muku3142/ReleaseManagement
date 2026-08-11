import type { ReactNode } from 'react';
import type { ChromeFields } from '../api/templates';

export type ChromeFieldName = 'coverTitle' | 'coverSubtitle' | 'headerText' | 'footerText';

export function ChromeFieldsForm(props: {
  chrome: ChromeFields;
  onChange: (chrome: ChromeFields) => void;
  onFieldFocus: (field: ChromeFieldName, start: number, end: number) => void;
  children?: ReactNode;
}) {
  const { chrome, onChange, onFieldFocus } = props;

  function trackSelection(field: ChromeFieldName) {
    return (e: React.SyntheticEvent<HTMLInputElement>) => {
      onFieldFocus(field, e.currentTarget.selectionStart ?? 0, e.currentTarget.selectionEnd ?? 0);
    };
  }

  return (
    <div className="space-y-3 p-4">
      <label className="flex items-center gap-2 text-sm text-gray-700">
        <input
          type="checkbox"
          checked={chrome.coverEnabled}
          onChange={(e) => onChange({ ...chrome, coverEnabled: e.target.checked })}
        />
        Show cover page
      </label>

      <div>
        <label className="block text-sm font-semibold text-gray-700">Cover title</label>
        <input
          type="text"
          value={chrome.coverTitle}
          onChange={(e) => onChange({ ...chrome, coverTitle: e.target.value })}
          onBlur={trackSelection('coverTitle')}
          onSelect={trackSelection('coverTitle')}
          className="w-full rounded border border-gray-300 px-3 py-2 text-sm focus-visible:ring-2 focus-visible:ring-blue-600"
        />
        <p className="mt-1 text-xs text-gray-500">
          Accepts {'{{ }}'} placeholders, e.g. {'{{release.name}}'}.
        </p>
      </div>

      <div>
        <label className="block text-sm font-semibold text-gray-700">Cover subtitle</label>
        <input
          type="text"
          value={chrome.coverSubtitle}
          onChange={(e) => onChange({ ...chrome, coverSubtitle: e.target.value })}
          onBlur={trackSelection('coverSubtitle')}
          onSelect={trackSelection('coverSubtitle')}
          className="w-full rounded border border-gray-300 px-3 py-2 text-sm focus-visible:ring-2 focus-visible:ring-blue-600"
        />
        <p className="mt-1 text-xs text-gray-500">
          Accepts {'{{ }}'} placeholders, e.g. {'{{release.name}}'}.
        </p>
      </div>

      <div>
        <label className="block text-sm font-semibold text-gray-700">Header text</label>
        <input
          type="text"
          value={chrome.headerText}
          onChange={(e) => onChange({ ...chrome, headerText: e.target.value })}
          onBlur={trackSelection('headerText')}
          onSelect={trackSelection('headerText')}
          className="w-full rounded border border-gray-300 px-3 py-2 text-sm focus-visible:ring-2 focus-visible:ring-blue-600"
        />
        <p className="mt-1 text-xs text-gray-500">Shown as a strip at the top of every page.</p>
      </div>

      <div>
        <label className="block text-sm font-semibold text-gray-700">Footer text</label>
        <input
          type="text"
          value={chrome.footerText}
          onChange={(e) => onChange({ ...chrome, footerText: e.target.value })}
          onBlur={trackSelection('footerText')}
          onSelect={trackSelection('footerText')}
          className="w-full rounded border border-gray-300 px-3 py-2 text-sm focus-visible:ring-2 focus-visible:ring-blue-600"
        />
        <p className="mt-1 text-xs text-gray-500">Shown as a strip at the bottom of every page.</p>
      </div>

      <label className="flex items-center gap-2 text-sm text-gray-700">
        <input
          type="checkbox"
          checked={chrome.pageNumbersEnabled}
          onChange={(e) => onChange({ ...chrome, pageNumbersEnabled: e.target.checked })}
        />
        Show page numbers
      </label>
      <p className="text-xs text-gray-500">
        Preview shows a literal placeholder — actual page numbers are calculated when the document is generated.
      </p>

      <div>
        <label className="block text-sm font-semibold text-gray-700">Cover logo height (mm)</label>
        <input
          type="number"
          min={5}
          max={120}
          step={1}
          value={chrome.logoRenderHeightMm}
          onChange={(e) => onChange({ ...chrome, logoRenderHeightMm: Number(e.target.value) })}
          className="w-full rounded border border-gray-300 px-3 py-2 text-sm focus-visible:ring-2 focus-visible:ring-blue-600"
        />
        <p className="mt-1 text-xs text-gray-500">
          The cover logo is scaled down to fit this height, keeping its proportions. A smaller logo is
          left at its own size. 5–120mm.
        </p>
      </div>

      {/* All seven fields above stay editable regardless of the coverEnabled/pageNumbersEnabled
      toggles - the toggles govern whether the preview and future generation *use* them, not
      whether they can be filled in ahead of time. Do not add `disabled`/dimming here. */}

      {props.children}
    </div>
  );
}
