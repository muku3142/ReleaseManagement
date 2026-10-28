import { act } from 'react';
import { createRoot } from 'react-dom/client';

// The project has no @testing-library/react; this is the minimal renderHook the hook tests need.
export function renderHook<P, R>(hook: (props: P) => R, initialProps: NoInfer<P>) {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  const container = document.createElement('div');
  const root = createRoot(container);
  const result = { current: undefined as R };

  function Harness({ props }: { props: P }) {
    result.current = hook(props);
    return null;
  }

  act(() => root.render(<Harness props={initialProps} />));
  return {
    result,
    rerender: (props: P) => act(() => root.render(<Harness props={props} />)),
    unmount: () => act(() => root.unmount()),
  };
}
