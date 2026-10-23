import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { AsyncForm, ConfigFields, configFrom } from '../forms.jsx';
import { Feedback, useTitle } from '../page-support.jsx';
import { navigateTo } from '../navigation.js';
export function EventList({ navigate = navigateTo }) {
  const [events, setEvents] = useState(null);
  const [feedback, setFeedback] = useState({ message: '', error: false });
  const error = (message) => setFeedback({ message, error: true });
  useTitle('Events', 'Session Manager');
  useEffect(() => {
    let request;
    function load() {
      request?.abort();
      const current = new AbortController();
      request = current;
      api('/api/v1/events', 'GET', undefined, { signal: current.signal })
        .then((value) => {
          if (!current.signal.aborted) {
            setEvents(value);
            setFeedback({ message: '', error: false });
          }
        })
        .catch((problem) => {
          if (!current.signal.aborted) setFeedback({ message: problem.message, error: true });
        });
    }
    const hide = () => request?.abort();
    const restore = (event) => {
      if (event.persisted) {
        setEvents(null);
        load();
      }
    };
    window.addEventListener('pagehide', hide);
    window.addEventListener('pageshow', restore);
    load();
    return () => {
      hide();
      window.removeEventListener('pagehide', hide);
      window.removeEventListener('pageshow', restore);
    };
  }, []);
  async function create(form) {
    const fields = new FormData(form);
    const event = await api('/api/v1/events', 'POST', {
      name: fields.get('name'),
      config: configFrom(form),
    });
    navigate(`/events/${encodeURIComponent(event.id)}`);
  }
  return (
    <main className="shell">
      <header>
        <p className="eyebrow">SESSION MANAGER</p>
        <h1>Events</h1>
        <p>Register attendees, configure grouping rules, and prepare a staffed assignment.</p>
      </header>
      <Feedback feedback={feedback} />
      <div className="columns">
        <section className="card">
          <h2>Your events</h2>
          {events === null ? (
            <p>Loading events…</p>
          ) : events.length === 0 ? (
            <p className="muted">No events yet. Create one to begin registration.</p>
          ) : (
            <ul className="event-list">
              {events.map((event) => (
                <li key={event.id}>
                  <a href={`/events/${encodeURIComponent(event.id)}`}>{event.name}</a>
                </li>
              ))}
            </ul>
          )}
        </section>
        <section className="card">
          <h2>Create event</h2>
          <AsyncForm name="Create event" action={create} onError={error}>
            <label>
              Event name
              <input name="name" maxLength="200" required autoComplete="off" />
            </label>
            <ConfigFields />
            <button type="submit">Create event</button>
          </AsyncForm>
        </section>
      </div>
    </main>
  );
}
