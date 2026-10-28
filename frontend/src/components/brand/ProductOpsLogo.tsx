import { cn } from '@/lib/utils';
import { PRODUCT_NAME, PRODUCT_TAGLINE } from './brand';
import { ProductOpsMark } from './ProductOpsMark';

const WORDMARK = 'font-brand font-extrabold leading-none tracking-[-0.01em] text-foreground';
const WORDMARK_TEXT = PRODUCT_NAME;

type ProductOpsLogoProps = {
  variant: 'sidebar' | 'login';
  animate?: boolean;
  className?: string;
};

export function ProductOpsLogo({ variant, className, animate = false }: ProductOpsLogoProps) {
  if (variant === 'sidebar') {
    return (
      <span className={cn('flex items-center gap-3', className)}>
        <ProductOpsMark size={28} animate={animate} />
        <span className={cn(WORDMARK, 'text-[18px] group-data-[collapsible=icon]:hidden')}>{WORDMARK_TEXT}</span>
      </span>
    );
  }
  return (
    <span className={cn('flex flex-col items-center', className)}>
      <span className="rounded-[13px] shadow-[0_6px_18px_-8px_var(--brand-from)]">
        <ProductOpsMark size={48} animate={animate} />
      </span>
      <span className={cn(WORDMARK, 'mt-4 text-[30px]')}>{WORDMARK_TEXT}</span>
      <span className="mt-1 text-sm text-muted-foreground">{PRODUCT_TAGLINE}</span>
    </span>
  );
}
