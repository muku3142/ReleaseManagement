import { useState } from 'react';
import { AddToReleaseDialog } from '../components/AddToReleaseDialog';
import { TicketBrowser } from '../components/TicketBrowser';

export function TicketsRoute() {
  // The keys are snapshotted when the trigger is pressed rather than tracked live: showModal() makes
  // the rest of the document inert, so the selection cannot change while the dialog is open.
  const [picker, setPicker] = useState<{
    ticketKeys: string[];
    jiraProjectKey: string | null;
  } | null>(null);

  return (
    // No max-width cap: the ticket table is the point of this screen and eight columns do not
    // fit in 5xl, which was cutting content off.
    <div className="w-full">
      <h2 className="text-base font-semibold text-gray-900">Browse tickets</h2>

      <TicketBrowser
        actionLabel="Add to release"
        onAction={(ticketKeys, jiraProjectKey) => setPicker({ ticketKeys, jiraProjectKey })}
      />

      <AddToReleaseDialog
        open={picker !== null}
        ticketKeys={picker?.ticketKeys ?? []}
        jiraProjectKey={picker?.jiraProjectKey ?? null}
        onClose={() => setPicker(null)}
      />
    </div>
  );
}
