import type { ReactNode } from 'react';

const WIDTH_CLASSES = {
  wide: 'mx-auto w-full max-w-screen-2xl',
  narrow: 'mx-auto w-full max-w-3xl',
} as const;

type PageContainerProps = {
  width?: keyof typeof WIDTH_CLASSES;
  children: ReactNode;
};

export function PageContainer({ width = 'wide', children }: PageContainerProps) {
  return <div className={WIDTH_CLASSES[width]}>{children}</div>;
}
