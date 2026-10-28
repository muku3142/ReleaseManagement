import { createRoot } from 'react-dom/client';
import { QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from '@tanstack/react-router';
import { router } from './router';
import { queryClient } from './api/queryClient';
import { ThemeProvider } from './components/theme/ThemeProvider';
import '@fontsource-variable/inter';
import '@fontsource-variable/nunito';
import './index.css';

createRoot(document.getElementById('root')!).render(
  <ThemeProvider>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </ThemeProvider>,
);
