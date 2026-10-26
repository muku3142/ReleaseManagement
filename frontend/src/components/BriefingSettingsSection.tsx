import { useEffect, useRef, useState } from 'react';
import { Link } from '@tanstack/react-router';
import { ApiError, type ProblemDetail } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import {
  DAY_CODES,
  DAY_LONG,
  DAY_SHORT,
  cronSummary,
  useBriefingSettings,
  useRunBriefingNow,
  useSaveBriefingSettings,
  useSetBriefingFallback,
  useUseMyJiraCredential,
  type BriefingRunNowResult,
  type BriefingSettings,
  type BriefingSettingsUpdate,
  type BriefingTaskSettings,
  type DayCode,
} from '../api/briefingSettings';
import { SectionError, SectionLoading } from './PortfolioStates';

const SPINNER =
  'h-4 w-4 animate-spin motion-reduce:animate-none rounded-full border-2 border-gray-300 border-t-blue-600';
const NUMBER_INPUT = 'w-24 rounded border border-gray-300 px-3 py-2 text-sm';
const LABEL = 'block text-sm font-medium text-gray-700';
const HELPER = 'text-xs text-gray-600';
const ERROR = 'text-xs text-red-600';
const BORDERED_BUTTON = 'rounded border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 disabled:opacity-50';

type TaskForm = {
  id: BriefingTaskSettings['id'];
  name: string;
  enabled: boolean;
  days: DayCode[];
  time: string;
  stale: string;
  due: string;
  backlog: string;
};
type FormState = { grace: string; workDays: DayCode[]; keepRuns: string; tasks: TaskForm[] };
type FieldError = { id: string; message: string };

const THRESHOLDS: Record<TaskForm['id'], { key: 'stale' | 'due' | 'backlog'; label: string; unit: string; helper: string; min: number; max: number }[]> = {
  jira_digest: [
    { key: 'stale', label: 'Stale after', unit: 'working days', helper: 'An in-progress issue with no update for this long is stale. Default 3.', min: 1, max: 30 },
    { key: 'due', label: 'Due soon within', unit: 'days', helper: 'Issues due within this many days are listed. Default 5.', min: 1, max: 60 },
  ],
  sprint_check: [
    { key: 'backlog', label: 'Backlog items when there is no next sprint', unit: 'items', helper: 'Top-ranked items checked when no future sprint exists. Default 15.', min: 1, max: 100 },
  ],
  weekly_status: [],
};

const THRESHOLD_FIELD: Record<'stale' | 'due' | 'backlog', string> = {
  stale: 'staleAfterWorkingDays',
  due: 'dueSoonDays',
  backlog: 'backlogItems',
};

function toForm(settings: BriefingSettings): FormState {
  return {
    grace: String(settings.graceMinutes),
    workDays: settings.workDays,
    keepRuns: String(settings.keepRuns),
    tasks: settings.tasks.map((task) => ({
      id: task.id,
      name: task.name,
      enabled: task.enabled,
      days: task.days,
      time: task.time,
      stale: String(task.staleAfterWorkingDays),
      due: String(task.dueSoonDays),
      backlog: String(task.backlogItems),
    })),
  };
}

function toUpdate(form: FormState): BriefingSettingsUpdate {
  return {
    graceMinutes: Number(form.grace),
    workDays: form.workDays,
    keepRuns: Number(form.keepRuns),
    tasks: form.tasks.map((task) => ({
      id: task.id,
      enabled: task.enabled,
      days: task.days,
      time: task.time,
      staleAfterWorkingDays: Number(task.stale),
      dueSoonDays: Number(task.due),
      backlogItems: Number(task.backlog),
    })),
  };
}

function inRange(value: string, min: number, max: number): boolean {
  return /^\d+$/.test(value) && Number(value) >= min && Number(value) <= max;
}

function validate(form: FormState): FieldError[] {
  const errors: FieldError[] = [];
  if (!inRange(form.grace, 1, 240)) {
    errors.push({ id: 'briefing-grace', message: 'Enter a whole number of minutes from 1 to 240.' });
  }
  if (form.workDays.length === 0) {
    errors.push({ id: 'briefing-workdays-SUN', message: 'Choose at least one day.' });
  }
  if (!inRange(form.keepRuns, 10, 500)) {
    errors.push({ id: 'briefing-keep', message: 'Enter a whole number from 10 to 500.' });
  }
  for (const task of form.tasks) {
    if (task.enabled && task.days.length === 0) {
      errors.push({ id: `briefing-${task.id}-days-SUN`, message: 'Choose at least one day.' });
    }
    if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(task.time)) {
      errors.push({ id: `briefing-${task.id}-time`, message: 'Enter a time, for example 08:44.' });
    }
    for (const threshold of THRESHOLDS[task.id]) {
      if (!inRange(task[threshold.key], threshold.min, threshold.max)) {
        errors.push({
          id: `briefing-${task.id}-${threshold.key}`,
          message: `Enter a whole number from ${threshold.min} to ${threshold.max}.`,
        });
      }
    }
  }
  return errors;
}

// Maps a server fieldErrors key such as "tasks[0].time" onto the id of the control that shows it.
function serverErrorId(key: string, form: FormState): string | undefined {
  const fixed: Record<string, string> = {
    graceMinutes: 'briefing-grace',
    keepRuns: 'briefing-keep',
    workDays: 'briefing-workdays-SUN',
  };
  if (fixed[key]) return fixed[key];
  const match = /^tasks\[(\d+)]\.(\w+)$/.exec(key);
  const task = match && form.tasks[Number(match[1])];
  if (!task) return undefined;
  const field = match[2];
  if (field === 'days') return `briefing-${task.id}-days-SUN`;
  if (field === 'time') return `briefing-${task.id}-time`;
  const threshold = (['stale', 'due', 'backlog'] as const).find((name) => THRESHOLD_FIELD[name] === field);
  return threshold ? `briefing-${task.id}-${threshold}` : undefined;
}

function stripPeriod(text: string): string {
  return text.replace(/\.$/, '');
}

function describeRun(result: BriefingRunNowResult): string {
  const parts = [`${result.name} ran for ${result.products} ${result.products === 1 ? 'product' : 'products'}.`];
  if (result.attention > 0) parts.push(`${result.attention} need attention.`);
  if (result.failed > 0) parts.push(`${result.failed} failed.`);
  if (result.needsCredential > 0) {
    parts.push(`${result.needsCredential} need a credential. Use 'Use my Jira credential' above.`);
  }
  return parts.join(' ');
}

function problemOf(error: unknown): ProblemDetail | undefined {
  return error instanceof ApiError ? error.problem : undefined;
}

function Spinner({ label }: { label: string }) {
  return (
    <span className="inline-flex items-center gap-2">
      <span className={SPINNER} aria-hidden="true" />
      {label}
    </span>
  );
}

function DayChecks({
  prefix,
  legend,
  value,
  onChange,
  error,
}: {
  prefix: string;
  legend: string;
  value: DayCode[];
  onChange: (days: DayCode[]) => void;
  error?: string;
}) {
  const errorId = `${prefix}-error`;
  return (
    <fieldset>
      <legend className={LABEL}>{legend}</legend>
      <div className="mt-1 flex flex-wrap gap-3">
        {DAY_CODES.map((day) => (
          <label key={day} className="inline-flex items-center gap-1 text-sm text-gray-700">
            <input
              id={`${prefix}-${day}`}
              type="checkbox"
              aria-label={DAY_LONG[day]}
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? errorId : undefined}
              checked={value.includes(day)}
              onChange={(event) =>
                onChange(DAY_CODES.filter((code) => (code === day ? event.target.checked : value.includes(code))))
              }
            />
            {DAY_SHORT[day]}
          </label>
        ))}
      </div>
      {error && (
        <p id={errorId} className={ERROR}>
          {error}
        </p>
      )}
    </fieldset>
  );
}

function NumberField({
  id,
  label,
  unit,
  helper,
  value,
  onChange,
  error,
}: {
  id: string;
  label: string;
  unit: string;
  helper: string;
  value: string;
  onChange: (value: string) => void;
  error?: string;
}) {
  const describedBy = [`${id}-help`, error ? `${id}-error` : undefined].filter(Boolean).join(' ');
  return (
    <div>
      <label htmlFor={id} className={LABEL}>
        {label}
      </label>
      <div className="mt-1 flex items-center gap-2">
        <input
          id={id}
          type="number"
          inputMode="numeric"
          value={value}
          onChange={(event) => onChange(event.target.value)}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={NUMBER_INPUT}
        />
        <span className={HELPER}>{unit}</span>
      </div>
      <p id={`${id}-help`} className={HELPER}>
        {helper}
      </p>
      {error && (
        <p id={`${id}-error`} className={ERROR}>
          {error}
        </p>
      )}
    </div>
  );
}

// Saves on click, apart from "Save settings": it decides whether the app runs anything on its own (D-17.1-24).
function FallbackSwitch({ enabled }: { enabled: boolean }) {
  const toggle = useSetBriefingFallback();
  const problem = toggle.isError ? problemOf(toggle.error) : undefined;
  return (
    <div className="space-y-1">
      <div className="flex flex-wrap items-center gap-3">
        <span id="briefing-fallback-label" className="text-sm font-medium text-gray-700">
          Claude unavailable: run briefings in the app
        </span>
        <button
          type="button"
          role="switch"
          aria-checked={enabled}
          aria-labelledby="briefing-fallback-label"
          aria-describedby="briefing-fallback-help"
          disabled={toggle.isPending}
          onClick={() => toggle.mutate(!enabled)}
          className={`relative inline-flex h-6 w-11 items-center rounded-full disabled:opacity-50 ${
            enabled ? 'bg-blue-600' : 'bg-gray-300'
          }`}
        >
          <span
            aria-hidden="true"
            className={`inline-block h-5 w-5 rounded-full bg-white shadow transition-transform motion-reduce:transition-none ${
              enabled ? 'translate-x-5' : 'translate-x-0.5'
            }`}
          />
        </button>
        <span className="text-sm text-gray-900">{enabled ? 'On' : 'Off'}</span>
        {toggle.isPending && <span className={SPINNER} aria-hidden="true" />}
      </div>
      <p id="briefing-fallback-help" className={HELPER}>
        Off: Claude&apos;s scheduled tasks produce the briefings and the app runs nothing on its own. On: the app runs
        the daily digest, sprint check and weekly status for every product with a Jira project, on the schedule below.
      </p>
      {problem && (
        <p role="alert" data-reason={problem.reason} className="text-sm text-red-600">
          {messageForReason(problem)}
        </p>
      )}
    </div>
  );
}

function TaskCard({
  task,
  saved,
  dirty,
  errors,
  onChange,
}: {
  task: TaskForm;
  saved: BriefingTaskSettings | undefined;
  dirty: boolean;
  errors: Map<string, string>;
  onChange: (patch: Partial<TaskForm>) => void;
}) {
  const run = useRunBriefingNow();
  const prefix = `briefing-${task.id}`;
  const blockedReason = dirty
    ? 'Save your changes first. Run briefing now uses the saved settings.'
    : saved && !saved.enabled
      ? 'Enable this briefing to run it.'
      : undefined;
  const timeError = errors.get(`${prefix}-time`);
  const runProblem = run.isError ? problemOf(run.error) : undefined;
  const runMessage = runProblem ? messageForReason(runProblem) : 'something went wrong';

  return (
    <fieldset className="space-y-4 rounded border border-gray-200 bg-white p-4">
      <legend className={LABEL}>{task.name}</legend>
      <label className="inline-flex items-center gap-2 text-sm text-gray-900">
        <input type="checkbox" checked={task.enabled} onChange={(event) => onChange({ enabled: event.target.checked })} />
        Enabled
      </label>
      <DayChecks
        prefix={`${prefix}-days`}
        legend="Days"
        value={task.days}
        onChange={(days) => onChange({ days })}
        error={errors.get(`${prefix}-days-SUN`)}
      />
      <div>
        <label htmlFor={`${prefix}-time`} className={LABEL}>
          Time
        </label>
        <div className="mt-1 flex items-center gap-2">
          <input
            id={`${prefix}-time`}
            type="time"
            value={task.time}
            onChange={(event) => onChange({ time: event.target.value })}
            aria-invalid={timeError ? true : undefined}
            aria-describedby={timeError ? `${prefix}-time-error` : undefined}
            className="w-28 rounded border border-gray-300 px-3 py-2 text-sm"
          />
          <span className={HELPER}>Africa/Cairo</span>
        </div>
        {timeError && (
          <p id={`${prefix}-time-error`} className={ERROR}>
            {timeError}
          </p>
        )}
      </div>
      {task.days.length > 0 && /^\d\d:\d\d$/.test(task.time) && (
        <p className={HELPER}>{cronSummary(task.days, task.time)}</p>
      )}
      {THRESHOLDS[task.id].map((threshold) => (
        <NumberField
          key={threshold.key}
          id={`${prefix}-${threshold.key}`}
          label={threshold.label}
          unit={threshold.unit}
          helper={threshold.helper}
          value={task[threshold.key]}
          onChange={(value) => onChange({ [threshold.key]: value })}
          error={errors.get(`${prefix}-${threshold.key}`)}
        />
      ))}
      <div className="flex flex-wrap items-center gap-3">
        <button
          type="button"
          className={BORDERED_BUTTON}
          disabled={run.isPending}
          aria-disabled={blockedReason ? true : undefined}
          aria-describedby={blockedReason ? `${prefix}-run-help` : undefined}
          onClick={() => {
            if (!blockedReason && !run.isPending) run.mutate(task.id);
          }}
        >
          {run.isPending ? <Spinner label="Running…" /> : 'Run briefing now'}
        </button>
        <p id={`${prefix}-run-help`} className={HELPER}>
          {blockedReason ?? "Runs for every active product with a Jira project, now. It ignores Claude's result."}
        </p>
      </div>
      <div role="status" aria-live="polite" className="text-sm text-gray-900">
        {run.isSuccess && (
          <>
            {describeRun(run.data)}{' '}
            <Link to="/briefings" className="text-blue-600 underline">
              Open Briefings
            </Link>
          </>
        )}
      </div>
      {run.isError && (
        <p role="alert" data-reason={runProblem?.reason} className="text-sm text-red-600">
          Couldn&apos;t run {task.name} — {stripPeriod(runMessage)}. Try again.
        </p>
      )}
    </fieldset>
  );
}

function BriefingSettingsForm({ settings }: { settings: BriefingSettings }) {
  const save = useSaveBriefingSettings();
  const designate = useUseMyJiraCredential();
  const [form, setForm] = useState<FormState>(() => toForm(settings));
  const baseRef = useRef(JSON.stringify(toForm(settings)));
  const [clientErrors, setClientErrors] = useState<FieldError[]>([]);
  const [designated, setDesignated] = useState(false);

  const base = JSON.stringify(toForm(settings));
  useEffect(() => {
    // A background refetch or a designation must not wipe edits the user has not saved yet.
    setForm((current) => (JSON.stringify(current) === baseRef.current ? toForm(settings) : current));
    baseRef.current = base;
  }, [settings, base]);

  const dirty = JSON.stringify(form) !== base;

  function edit(update: (current: FormState) => FormState) {
    setForm(update);
    setClientErrors([]);
    save.reset();
  }

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const errors = validate(form);
    setClientErrors(errors);
    if (errors.length > 0) {
      document.getElementById(errors[0].id)?.focus();
      return;
    }
    save.mutate(toUpdate(form), { onSuccess: (response) => setForm(toForm(response)) });
  }

  const saveProblem = save.isError ? problemOf(save.error) : undefined;
  const errors = new Map(clientErrors.map((error) => [error.id, error.message]));
  if (saveProblem?.reason === 'VALIDATION_FAILED') {
    for (const [key, message] of Object.entries(saveProblem.fieldErrors ?? {})) {
      const id = serverErrorId(key, form);
      if (id) errors.set(id, message);
    }
  }
  const designateProblem = designate.isError ? problemOf(designate.error) : undefined;
  const { claudeFeed, schedulerUser } = settings;

  return (
    <form onSubmit={handleSubmit} noValidate className="mt-4 space-y-8">
      <FallbackSwitch enabled={settings.fallbackEnabled} />
      <div>
        <h3 className={LABEL}>Claude feed</h3>
        <dl className="mt-2 space-y-2 text-sm">
          <div>
            <dt className="font-medium text-gray-700">Claude data folder</dt>
            <dd className="break-all text-gray-900">{claudeFeed.dataDir}</dd>
          </div>
          <div>
            <dt className="font-medium text-gray-700">Claude config file</dt>
            <dd className="break-all text-gray-900">{claudeFeed.configFile}</dd>
          </div>
          <div>
            <dt className="font-medium text-gray-700">Status</dt>
            <dd className={claudeFeed.readable ? 'text-gray-900' : 'text-amber-700'}>
              {claudeFeed.readable
                ? 'Readable'
                : 'Not readable. The app treats Claude as unavailable and runs every briefing itself.'}
            </dd>
          </div>
        </dl>
        <p className={`mt-2 ${HELPER}`}>Set by RM_BRIEFINGS_DIR on the server. Change it there and restart the app.</p>
      </div>

      <div className="space-y-4">
        <h3 className={LABEL}>Scheduler</h3>
        {settings.credentialProblem && (
          <p role="status" className="rounded border border-amber-600 bg-amber-50 p-3 text-sm text-amber-700">
            The scheduler&apos;s Jira credential is missing or expired. Choose &apos;Use my Jira credential&apos; in
            Settings &gt; Briefings.
          </p>
        )}
        <div>
          <div className="flex flex-wrap items-center gap-3 text-sm">
            <span className="font-medium text-gray-700">Scheduler user</span>
            {schedulerUser.email ? (
              <span className="text-gray-900">{schedulerUser.email}</span>
            ) : (
              <span className="text-gray-500">Not set</span>
            )}
            {schedulerUser.isYou ? (
              <span className={HELPER}>You are the scheduler user.</span>
            ) : (
              <button
                type="button"
                className={BORDERED_BUTTON}
                disabled={designate.isPending}
                onClick={() => {
                  setDesignated(false);
                  designate.mutate(undefined, { onSuccess: () => setDesignated(true) });
                }}
              >
                {designate.isPending ? <Spinner label="Saving…" /> : 'Use my Jira credential'}
              </button>
            )}
          </div>
          <p className={HELPER}>
            Scheduled briefings read Jira with the stored credential of the person who clicks this. Anyone can take
            over by clicking it.
          </p>
          <div role="status" aria-live="polite" className="text-sm text-green-700">
            {designated && !designate.isError && 'You are now the scheduler user.'}
          </div>
          {designateProblem && (
            <p role="alert" data-reason={designateProblem.reason} className="text-sm text-red-600">
              {messageForReason(designateProblem)}
            </p>
          )}
        </div>
        <NumberField
          id="briefing-grace"
          label="Grace period"
          unit="minutes"
          helper="How long after a slot the app waits for Claude's result before it runs the briefing itself. Default 30."
          value={form.grace}
          onChange={(grace) => edit((current) => ({ ...current, grace }))}
          error={errors.get('briefing-grace')}
        />
        <div>
          <DayChecks
            prefix="briefing-workdays"
            legend="Work week"
            value={form.workDays}
            onChange={(workDays) => edit((current) => ({ ...current, workDays }))}
            error={errors.get('briefing-workdays-SUN')}
          />
          <p className={HELPER}>Used to count working days for stale issues.</p>
        </div>
        <NumberField
          id="briefing-keep"
          label="Runs kept"
          unit="runs per briefing and product"
          helper="Older app runs are removed. Default 60."
          value={form.keepRuns}
          onChange={(keepRuns) => edit((current) => ({ ...current, keepRuns }))}
          error={errors.get('briefing-keep')}
        />
      </div>

      <div className="space-y-4">
        <h3 className={LABEL}>Briefings</h3>
        {form.tasks.map((task, index) => (
          <TaskCard
            key={task.id}
            task={task}
            saved={settings.tasks.find((candidate) => candidate.id === task.id)}
            dirty={dirty}
            errors={errors}
            onChange={(patch) =>
              edit((current) => ({
                ...current,
                tasks: current.tasks.map((candidate, position) =>
                  position === index ? { ...candidate, ...patch } : candidate,
                ),
              }))
            }
          />
        ))}
      </div>

      <div className="space-y-2">
        <button
          type="submit"
          disabled={save.isPending || !dirty}
          className="rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {save.isPending ? <Spinner label="Saving…" /> : 'Save settings'}
        </button>
        <div role="status" aria-live="polite" className="text-sm text-green-700">
          {save.isSuccess && !dirty && 'Settings saved.'}
        </div>
        {saveProblem && (
          <p role="alert" data-reason={saveProblem.reason} className="text-sm text-red-600">
            {messageForReason(saveProblem)}
          </p>
        )}
      </div>
    </form>
  );
}

export function BriefingSettingsSection() {
  const settings = useBriefingSettings();
  const heading = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    if (window.location.hash === '#briefings') heading.current?.focus();
  }, []);

  return (
    <section id="briefings" aria-labelledby="briefings-heading">
      <h2 id="briefings-heading" ref={heading} tabIndex={-1} className="text-base font-semibold text-gray-900">
        Briefings
      </h2>
      <p className="mt-1 text-sm text-gray-600">
        Claude runs these briefings. The app runs them on its own schedule only while the switch below is on.
      </p>
      {settings.isPending && (
        <div className="mt-4">
          <SectionLoading text="Loading briefing settings…" />
        </div>
      )}
      {settings.isError && (
        <div className="mt-4">
          <SectionError what="briefing settings" error={settings.error} onRetry={() => void settings.refetch()} />
        </div>
      )}
      {settings.data && <BriefingSettingsForm settings={settings.data} />}
    </section>
  );
}
