import {
  Boxes,
  FileText,
  LayoutDashboard,
  Map as MapIcon,
  Newspaper,
  Package,
  Settings,
  Sparkles,
  Ticket,
  type LucideIcon,
} from 'lucide-react';

export type NavItem = {
  label: string;
  to: '/' | '/assistant' | '/tickets' | '/releases' | '/roadmap' | '/briefings' | '/portfolio' | '/templates' | '/settings';
  icon: LucideIcon;
  group: 'Delivery' | 'Planning' | 'Configure' | null;
  goKey?: 'd' | 't' | 'r' | 'm' | 'b' | 'p' | 's';
  assistantOnly?: true;
};

export const NAV_ITEMS: readonly NavItem[] = [
  { label: 'Dashboard', to: '/', icon: LayoutDashboard, group: null, goKey: 'd' },
  { label: 'Assistant', to: '/assistant', icon: Sparkles, group: null, assistantOnly: true },
  { label: 'Tickets', to: '/tickets', icon: Ticket, group: 'Delivery', goKey: 't' },
  { label: 'Releases', to: '/releases', icon: Package, group: 'Delivery', goKey: 'r' },
  { label: 'Roadmap', to: '/roadmap', icon: MapIcon, group: 'Planning', goKey: 'm' },
  { label: 'Briefings', to: '/briefings', icon: Newspaper, group: 'Planning', goKey: 'b' },
  { label: 'Products & clients', to: '/portfolio', icon: Boxes, group: 'Planning', goKey: 'p' },
  { label: 'Templates', to: '/templates', icon: FileText, group: 'Configure' },
  { label: 'Settings', to: '/settings', icon: Settings, group: 'Configure', goKey: 's' },
];
