import type { RiceField, RoadmapStatus } from '../api/roadmap';

export const REACH_UNIT = 'clients';
export const EFFORT_UNIT = 'person-weeks';

export const IMPACT_OPTIONS = [
  { value: 0.25, label: '0.25 Minimal' },
  { value: 0.5, label: '0.5 Low' },
  { value: 1, label: '1 Medium' },
  { value: 2, label: '2 High' },
  { value: 3, label: '3 Massive' },
] as const;

export const CONFIDENCE_OPTIONS = [
  { value: 100, label: 'High 100%' },
  { value: 80, label: 'Medium 80%' },
  { value: 50, label: 'Low 50%' },
] as const;

export const STATUS_LABELS: Record<RoadmapStatus, string> = {
  IDEA: 'Idea',
  PLANNED: 'Planned',
  IN_PROGRESS: 'In progress',
  DONE: 'Done',
};

export const FIELD_LABELS: Record<RiceField, string> = {
  REACH: 'Reach',
  IMPACT: 'Impact',
  CONFIDENCE: 'Confidence',
  EFFORT: 'Effort',
};

export const RUBRIC = {
  formula: 'Score = Reach × Impact × Confidence ÷ Effort. Items missing any input are not scored.',
  reach:
    'How many clients this affects. A whole number, 0 or more. Choosing a product shows how many clients are linked to it, as a hint only.',
  impact: '3 Massive · 2 High · 1 Medium · 0.5 Low · 0.25 Minimal. How much it moves things for each client reached.',
  confidence: 'High 100%: backed by data. Medium 80%: some evidence. Low 50%: a guess.',
  effort: 'Total work in person-weeks. Decimals are fine (0.5). Must be above 0.',
} as const;
