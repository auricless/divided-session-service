import { flushSync } from 'react-dom';
import { useAssignment } from '../use-assignment.js';
import { Assignment } from '../Assignment.jsx';
import { useTitle } from '../page-support.jsx';
import { navigateTo, pathFor } from '../navigation.js';
export function GroupDisplay({ eventId, navigate = navigateTo }) {
  const { event, snapshot, error, refresh, show } = useAssignment(eventId);
  useTitle(event?.name, 'Groups');
  async function verified(action) {
    try {
      const verifiedSnapshot = await refresh();
      if (verifiedSnapshot) action(verifiedSnapshot);
    } catch {
      /* The shared feed displays errors and disables both controls. */
    }
  }
  return (
    <main className="shell">
      <header>
        <nav className="screen-only">
          <a href={`/events/${encodeURIComponent(eventId)}`}>← Registration</a>
        </nav>
        <p className="eyebrow">LIVE GROUP DISPLAY</p>
        <h1>{event?.name || 'Loading event…'}</h1>
        <p className="screen-only muted">
          Updates automatically every two seconds. Proposals require organizer action before they
          are final.
        </p>
        <div className="toolbar">
          <button
            disabled={!snapshot}
            onClick={() =>
              verified((verifiedSnapshot) => {
                // The print dialog reads DOM synchronously; commit this exact refreshed roster first.
                flushSync(() => show(verifiedSnapshot));
                window.print();
              })
            }
          >
            Print roster
          </button>
          <button
            className="secondary"
            disabled={!snapshot}
            onClick={() => verified(() => navigate(`${pathFor(eventId)}/assignment/export.csv`))}
          >
            Export CSV
          </button>
        </div>
      </header>
      <section className="card">
        <div id="assignment" aria-live="polite">
          <Assignment snapshot={snapshot} error={error} />
        </div>
      </section>
    </main>
  );
}
