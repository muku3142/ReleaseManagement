import { useEffect, useRef, useState } from 'react';
import { useNavigate } from '@tanstack/react-router';
import { Lock, LogOut, Monitor, Moon, PanelLeft, Plus, Search, Sun } from 'lucide-react';
import { useAssistantStatus } from '@/api/assistant';
import { useLogout } from '@/api/auth';
import { useClients } from '@/api/clients';
import { parseDashboardSearch } from '@/api/dashboard';
import { useProducts } from '@/api/products';
import { parseRoadmapSearch, useRoadmap } from '@/api/roadmap';
import { useReleases } from '@/api/releases';
import { NAV_ITEMS } from '@/components/layout/navItems';
import { useTheme, type Theme } from '@/components/theme/ThemeProvider';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Command,
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandShortcut,
} from '@/components/ui/command';
import { Kbd } from '@/components/ui/kbd';
import { useSidebar } from '@/components/ui/sidebar';
import { Spinner } from '@/components/ui/spinner';
import { useGoShortcuts } from '@/hooks/useGoShortcuts';

const ACTIVE_ROADMAP = { products: [], done: false, archived: false };

const THEMES: { value: Theme; label: string; icon: typeof Sun }[] = [
  { value: 'light', label: 'Use light theme', icon: Sun },
  { value: 'dark', label: 'Use dark theme', icon: Moon },
  { value: 'system', label: 'Use system theme', icon: Monitor },
];

type ListQuery = { isPending: boolean; isError: boolean };

function ListState({ query, noun }: { query: ListQuery; noun: string }) {
  if (query.isError) {
    return (
      <CommandItem disabled forceMount value={`${noun}:error`} className="text-muted-foreground">
        {`Couldn't load ${noun}. Close the palette and open it again to retry.`}
      </CommandItem>
    );
  }
  if (query.isPending) {
    return (
      <CommandItem disabled forceMount value={`${noun}:loading`} className="text-xs text-muted-foreground">
        <Spinner />
        {`Loading ${noun}…`}
      </CommandItem>
    );
  }
  return null;
}

function isMacPlatform(): boolean {
  const nav = navigator as Navigator & { userAgentData?: { platform?: string } };
  return /mac/i.test(nav.userAgentData?.platform ?? nav.platform);
}

export function CommandPalette() {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const navigated = useRef(false);
  const navigate = useNavigate();
  const { theme, setTheme } = useTheme();
  const sidebar = useSidebar();
  const logout = useLogout();
  const assistant = useAssistantStatus(true);
  const releases = useReleases('', open);
  const clients = useClients(open);
  const products = useProducts(open);
  const roadmap = useRoadmap(ACTIVE_ROADMAP, open);
  useGoShortcuts();

  const mod = isMacPlatform() ? '⌘' : 'Ctrl';
  const searching = search.trim() !== '';

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key.toLowerCase() === 'k' && (event.metaKey || event.ctrlKey)) {
        event.preventDefault();
        setSearch('');
        setOpen((current) => !current);
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, []);

  function changeOpen(next: boolean) {
    setSearch('');
    setOpen(next);
  }

  function closeForNavigation() {
    navigated.current = true;
    changeOpen(false);
  }

  function runAction(action: () => void) {
    changeOpen(false);
    action();
  }

  function signOut() {
    logout.mutate(undefined, { onSuccess: () => window.location.assign('/login') });
  }

  const pages = [
    ...NAV_ITEMS.filter((page) => !page.assistantOnly),
    ...(assistant.data?.enabled === true ? NAV_ITEMS.filter((page) => page.assistantOnly) : []),
  ];

  return (
    <>
      <Button variant="outline" className="hidden h-9 justify-start gap-2 md:inline-flex md:w-64" onClick={() => changeOpen(true)}>
        <Search className="size-4" />
        <span className="text-muted-foreground">Search or jump to…</span>
        <Kbd className="ml-auto">{`${mod} K`}</Kbd>
      </Button>
      <Button
        variant="ghost"
        size="icon-lg"
        className="max-md:size-11 md:hidden"
        aria-label="Open command palette"
        onClick={() => changeOpen(true)}
      >
        <Search className="size-4" />
      </Button>
      <CommandDialog
        open={open}
        onOpenChange={changeOpen}
        title="Command palette"
        description="Search for a page, release, client, product or roadmap item, or run an action."
        className="top-[20%] max-w-xl rounded-xl bg-popover shadow-lg sm:max-w-xl"
        onCloseAutoFocus={(event) => {
          // Radix would return focus to the trigger; after a navigation it belongs on the new page.
          if (!navigated.current) return;
          navigated.current = false;
          event.preventDefault();
          document.getElementById('content')?.focus();
        }}
      >
        <Command>
          <CommandInput placeholder="Type a command or search…" value={search} onValueChange={setSearch} />
          <CommandList className="max-h-[min(60vh,24rem)]">
            <CommandEmpty>{`No results for “${search}”.`}</CommandEmpty>

            <CommandGroup heading="Go to">
              {pages.map((page) => (
                <CommandItem
                  key={page.to}
                  value={`page:${page.to}`}
                  keywords={[page.label]}
                  onSelect={() => {
                    closeForNavigation();
                    navigate({ to: page.to });
                  }}
                >
                  <page.icon />
                  {page.label}
                  {page.goKey && (
                    <CommandShortcut>
                      <Kbd>{`G ${page.goKey.toUpperCase()}`}</Kbd>
                    </CommandShortcut>
                  )}
                </CommandItem>
              ))}
            </CommandGroup>

            <CommandGroup heading="Actions">
              <CommandItem
                value="action:new-release"
                keywords={['New release', 'Start a release from tickets']}
                onSelect={() => {
                  closeForNavigation();
                  navigate({ to: '/tickets', search: { new: true } });
                }}
              >
                <Plus />
                New release
              </CommandItem>
              {THEMES.filter((option) => option.value !== theme).map((option) => (
                <CommandItem
                  key={option.value}
                  value={`action:theme-${option.value}`}
                  keywords={[option.label]}
                  onSelect={() => runAction(() => setTheme(option.value))}
                >
                  <option.icon />
                  {option.label}
                </CommandItem>
              ))}
              <CommandItem
                value="action:toggle-sidebar"
                keywords={[sidebar.open ? 'Collapse sidebar' : 'Expand sidebar']}
                onSelect={() => runAction(sidebar.toggleSidebar)}
              >
                <PanelLeft />
                {sidebar.open ? 'Collapse sidebar' : 'Expand sidebar'}
                <CommandShortcut>
                  <Kbd>{`${mod} B`}</Kbd>
                </CommandShortcut>
              </CommandItem>
              <CommandItem value="action:sign-out" keywords={['Sign out']} onSelect={() => runAction(signOut)}>
                <LogOut />
                Sign out
              </CommandItem>
            </CommandGroup>

            {searching && (
              <>
                <CommandGroup heading="Releases" forceMount={releases.isPending || releases.isError}>
                  <ListState query={releases} noun="releases" />
                  {releases.data?.releases.map((release) => (
                    <CommandItem
                      key={release.id}
                      value={`release:${release.id}`}
                      keywords={[release.name, release.version, release.jiraProjectKey ?? '']}
                      onSelect={() => {
                        closeForNavigation();
                        navigate({ to: '/releases/$releaseId', params: { releaseId: release.id } });
                      }}
                    >
                      <span className="truncate">{`${release.name} ${release.version}`}</span>
                      {release.status === 'FROZEN' ? (
                        <Badge variant="neutral" className="ml-auto">
                          <Lock />
                          Frozen
                        </Badge>
                      ) : (
                        <Badge variant="outline" className="ml-auto">
                          Open
                        </Badge>
                      )}
                    </CommandItem>
                  ))}
                </CommandGroup>

                <CommandGroup heading="Clients" forceMount={clients.isPending || clients.isError}>
                  <ListState query={clients} noun="clients" />
                  {clients.data?.clients
                    .filter((client) => !client.archived)
                    .map((client) => (
                      <CommandItem
                        key={client.id}
                        value={`client:${client.id}`}
                        keywords={[client.name]}
                        onSelect={() => {
                          closeForNavigation();
                          navigate({ to: '/portfolio', search: { tab: 'clients', client: client.id } });
                        }}
                      >
                        <span className="truncate">{client.name}</span>
                      </CommandItem>
                    ))}
                </CommandGroup>

                <CommandGroup heading="Products" forceMount={products.isPending || products.isError}>
                  <ListState query={products} noun="products" />
                  {products.data?.products
                    .filter((product) => !product.archived)
                    .map((product) => (
                      <CommandItem
                        key={product.id}
                        value={`product:${product.id}`}
                        keywords={[product.name]}
                        onSelect={() => {
                          closeForNavigation();
                          navigate({ to: '/', search: parseDashboardSearch({ products: product.id }) });
                        }}
                      >
                        <span className="truncate">{product.name}</span>
                      </CommandItem>
                    ))}
                </CommandGroup>

                <CommandGroup heading="Roadmap items" forceMount={roadmap.isPending || roadmap.isError}>
                  <ListState query={roadmap} noun="roadmap items" />
                  {roadmap.data?.items.map((item) => (
                    <CommandItem
                      key={item.id}
                      value={`roadmap:${item.id}`}
                      keywords={[item.title, item.jiraKey ?? '']}
                      onSelect={() => {
                        closeForNavigation();
                        navigate({ to: '/roadmap', search: parseRoadmapSearch({ item: item.id }) });
                      }}
                    >
                      <span className="truncate">{item.title}</span>
                      {item.jiraKey && (
                        <span className="ml-auto font-mono text-xs text-muted-foreground">{item.jiraKey}</span>
                      )}
                    </CommandItem>
                  ))}
                </CommandGroup>
              </>
            )}
          </CommandList>
        </Command>
      </CommandDialog>
    </>
  );
}
