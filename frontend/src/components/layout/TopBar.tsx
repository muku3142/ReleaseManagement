import { CommandPalette } from '@/components/CommandPalette';
import { ThemeMenu } from '@/components/layout/ThemeMenu';
import { UserMenu } from '@/components/layout/UserMenu';
import { Separator } from '@/components/ui/separator';
import { SidebarTrigger } from '@/components/ui/sidebar';

export function TopBar() {
  return (
    <header className="sticky top-0 z-20 flex h-14 items-center gap-3 border-b bg-background/85 px-4 backdrop-blur supports-[backdrop-filter]:bg-background/85 lg:px-6">
      <SidebarTrigger aria-label="Toggle sidebar" className="size-9 max-md:size-11" />
      <Separator orientation="vertical" className="h-4" />
      <CommandPalette />
      <div className="flex-1" />
      <ThemeMenu />
      <UserMenu />
    </header>
  );
}
