import { act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Sidebar, SidebarProvider, SidebarTrigger } from '@/components/ui/sidebar';
import { Skeleton } from '@/components/ui/skeleton';
import { Spinner } from '@/components/ui/spinner';
import { Table } from '@/components/ui/table';

let container: HTMLDivElement;
let root: Root;

beforeAll(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
});

beforeEach(() => {
  localStorage.clear();
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  localStorage.clear();
});

function render(node: ReactNode) {
  act(() => root.render(node));
}

function classesOf(selector: string): string[] {
  const element = container.querySelector(selector);
  expect(element).not.toBeNull();
  return Array.from(element!.classList);
}

describe('Badge status variants', () => {
  it.each([
    ['success', ['bg-success-muted', 'text-success', 'border-success/25']],
    ['warning', ['bg-warning-muted', 'text-warning', 'border-warning/25']],
    ['destructive', ['bg-destructive-muted', 'text-destructive', 'border-destructive/25']],
    ['info', ['bg-info-muted', 'text-info', 'border-info/25']],
    ['neutral', ['bg-muted', 'text-muted-foreground', 'border-transparent']],
    ['outline', ['bg-transparent', 'text-foreground', 'border-border']],
  ] as const)('%s renders its token classes on the shared pill base', (variant, expected) => {
    render(<Badge variant={variant}>Status</Badge>);
    const classes = classesOf('[data-slot="badge"]');
    expect(classes).toEqual(expect.arrayContaining([...expected, 'rounded-full', 'font-semibold', 'text-xs']));
  });

  it('defaults to neutral', () => {
    render(<Badge>Status</Badge>);
    expect(classesOf('[data-slot="badge"]')).toContain('bg-muted');
  });
});

describe('Alert variants', () => {
  it('warning uses the warning tokens', () => {
    render(<Alert variant="warning">Heads up</Alert>);
    expect(classesOf('[data-slot="alert"]')).toEqual(expect.arrayContaining(['bg-warning-muted', 'text-warning']));
  });

  it('info uses the info tokens', () => {
    render(<Alert variant="info">Note</Alert>);
    expect(classesOf('[data-slot="alert"]')).toEqual(expect.arrayContaining(['bg-info-muted', 'text-info']));
  });
});

describe('Button', () => {
  it('destructive text sits on its token, not white', () => {
    render(<Button variant="destructive">Delete</Button>);
    const classes = classesOf('[data-slot="button"]');
    expect(classes).toContain('text-destructive-foreground');
    expect(classes).not.toContain('text-white');
  });

  it('uses the solid ring-2 focus treatment', () => {
    render(<Button>Save</Button>);
    expect(classesOf('[data-slot="button"]')).toEqual(
      expect.arrayContaining(['focus-visible:ring-2', 'focus-visible:ring-ring', 'focus-visible:ring-offset-2']),
    );
  });

  it('presses with a motion-safe scale instead of a nudge', () => {
    render(<Button>Save</Button>);
    const classes = classesOf('[data-slot="button"]');
    expect(classes).toContain('motion-safe:active:not-aria-[haspopup]:scale-[0.98]');
    expect(classes.some((c) => c.includes('translate-y-px'))).toBe(false);
  });

  it('link variant uses the animated underline', () => {
    render(<Button variant="link">Open</Button>);
    const classes = classesOf('[data-slot="button"]');
    expect(classes).toContain('link-underline');
    expect(classes).not.toContain('hover:underline');
  });
});

describe('Card interactive', () => {
  const INTERACTIVE = ['motion-safe:hover:-translate-y-0.5', 'hover:shadow-md', 'hover:ring-primary/40'];

  it('lifts with a violet ring on hover when interactive', () => {
    render(<Card interactive>Tile</Card>);
    expect(container.querySelector('[data-slot="card"]')!.hasAttribute('data-interactive')).toBe(true);
    expect(classesOf('[data-slot="card"]')).toEqual(expect.arrayContaining(INTERACTIVE));
  });

  it('a plain card has no hover lift', () => {
    render(<Card>Tile</Card>);
    expect(container.querySelector('[data-slot="card"]')!.hasAttribute('data-interactive')).toBe(false);
    const classes = classesOf('[data-slot="card"]');
    for (const name of INTERACTIVE) expect(classes).not.toContain(name);
  });
});

describe('Loading primitives', () => {
  it('Spinner is hidden from assistive tech and stops under reduced motion', () => {
    render(<Spinner />);
    const spinner = container.querySelector('[data-slot="spinner"]')!;
    expect(spinner.getAttribute('aria-hidden')).toBe('true');
    expect(spinner.getAttribute('role')).toBeNull();
    expect(spinner.classList.contains('motion-reduce:animate-none')).toBe(true);
  });

  it('Skeleton is hidden from assistive tech', () => {
    render(<Skeleton />);
    expect(container.querySelector('[data-slot="skeleton"]')!.getAttribute('aria-hidden')).toBe('true');
  });
});

describe('Sidebar persistence', () => {
  function sidebarState() {
    return container.querySelector('[data-slot="sidebar"][data-state]')!.getAttribute('data-state');
  }

  function renderSidebar() {
    render(
      <SidebarProvider>
        <Sidebar collapsible="icon">nav</Sidebar>
        <SidebarTrigger />
      </SidebarProvider>,
    );
  }

  it('writes the toggled state to localStorage and never to a cookie', () => {
    renderSidebar();
    expect(sidebarState()).toBe('expanded');
    act(() => (container.querySelector('[data-slot="sidebar-trigger"]') as HTMLButtonElement).click());
    expect(localStorage.getItem('productops-sidebar')).toBe('false');
    expect(sidebarState()).toBe('collapsed');
    expect(document.cookie).toBe('');
  });

  it('starts collapsed when the stored value is false', () => {
    localStorage.setItem('productops-sidebar', 'false');
    renderSidebar();
    expect(sidebarState()).toBe('collapsed');
  });

  it('ignores any stored value other than false', () => {
    localStorage.setItem('productops-sidebar', 'nonsense');
    renderSidebar();
    expect(sidebarState()).toBe('expanded');
  });
});

describe('Table', () => {
  it('passes containerProps to the scroll container', () => {
    render(<Table containerProps={{ className: 'max-h-[70vh]', tabIndex: 0 }} />);
    const wrapper = container.querySelector('[data-slot="table-container"]')!;
    expect(wrapper.classList.contains('max-h-[70vh]')).toBe(true);
    expect(wrapper.classList.contains('overflow-x-auto')).toBe(true);
    expect(wrapper.getAttribute('tabindex')).toBe('0');
  });
});
