import { LogOut } from 'lucide-react';
import { useLogout, useSession } from '@/api/auth';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Spinner } from '@/components/ui/spinner';

export function UserMenu() {
  const session = useSession();
  const logout = useLogout();
  const email = session.data?.email ?? '';

  function handleSignOut() {
    logout.mutate(undefined, {
      onSuccess: () => window.location.assign('/login'),
    });
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" className="h-9 gap-2 px-2" aria-label="Account menu">
          <span className="flex size-7 items-center justify-center rounded-full bg-muted text-xs font-semibold text-foreground">
            {email.charAt(0).toUpperCase()}
          </span>
          <span className="hidden max-w-48 truncate text-sm text-muted-foreground lg:inline">{email}</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuLabel className="truncate text-xs font-normal text-muted-foreground">{email}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem
          disabled={logout.isPending}
          onSelect={(event) => {
            event.preventDefault();
            handleSignOut();
          }}
        >
          {logout.isPending ? <Spinner /> : <LogOut />}
          {logout.isPending ? 'Signing out…' : 'Sign out'}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
