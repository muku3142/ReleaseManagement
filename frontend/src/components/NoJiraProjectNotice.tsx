import { Link } from '@tanstack/react-router';
import { TriangleAlert } from 'lucide-react';
import { Alert, AlertDescription } from '@/components/ui/alert';

export function NoJiraProjectNotice({ productName }: { productName: string }) {
  return (
    <Alert variant="warning" role="alert" data-reason="PRODUCT_NO_JIRA_PROJECT">
      <TriangleAlert aria-hidden />
      <AlertDescription>
        {productName} has no Jira project. Add one in{' '}
        <Link to="/portfolio" search={{ tab: 'products' }} className="text-primary underline underline-offset-4">
          Portfolio › Products
        </Link>
        .
      </AlertDescription>
    </Alert>
  );
}
