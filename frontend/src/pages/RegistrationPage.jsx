import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../api.js';
import { useAssignment } from '../use-assignment.js';
import { Assignment } from '../Assignment.jsx';
import {
  AsyncForm,
  ConfigFields,
  GenderField,
  LifeStageField,
  configFrom,
  tiers,
} from '../forms.jsx';
import { Feedback, useTitle } from '../page-support.jsx';
import { navigateTo, pathFor } from '../navigation.js';
function ParticipantEditor({ person, clear, save, onError }) {
  return (
    <AsyncForm name="Register participant" action={save} onError={onError}>
      <input name="participantId" type="hidden" defaultValue={person?.id || ''} />
      <div className="fields">
        <label>
          Name
          <input
            name="name"
            maxLength="200"
            required
            autoComplete="off"
            defaultValue={person?.name || ''}
          />
        </label>
        <label>
          Age
          <input
            name="age"
            type="number"
            min="1"
            max="2147483647"
            required
            defaultValue={person?.age || ''}
          />
        </label>
        <GenderField defaultValue={person?.gender || ''} />
        <LifeStageField defaultValue={person?.lifeStage || ''} />
      </div>
      <button type="submit">Save participant</button>
      <button type="button" className="secondary" onClick={clear}>
        Clear form
      </button>
    </AsyncForm>
  );
}

function FacilitatorFields({ id, remove }) {
  const name = (field) => `staff.${id}.${field}`;
  return (
    <fieldset className="facilitator">
      <legend>Facilitator</legend>
      <div className="fields">
        <label>
          Name
          <input name={name('name')} required maxLength="200" />
        </label>
        <label>
          Role
          <select name={name('role')} defaultValue="LEADER">
            <option>LEADER</option>
            <option>ASSISTANT</option>
          </select>
        </label>
        <GenderField name={name('gender')} />
      </div>
      <div className="checks">
        <span>Qualified tiers:</span>
        {tiers.map((tier) => (
          <label className="check" key={tier}>
            <input type="checkbox" name={name('qualifiedTiers')} value={tier} />
            {{ TEENS: 'Teens', YOUTH: 'Youth', YOUNG_ADULT: 'Young Adults' }[tier]}
          </label>
        ))}
      </div>
      <label className="check">
        <input type="checkbox" name={name('allowAgeTierMismatch')} data-permission />
        Allow an age-tier qualification exception for this run
      </label>
      <label className="check">
        <input type="checkbox" name={name('allowGenderMismatch')} data-permission />
        Allow a facilitator gender exception for this run
      </label>
      <button type="button" className="secondary" onClick={remove}>
        Remove facilitator
      </button>
    </fieldset>
  );
}
function staffFrom(form, ids) {
  const fields = new FormData(form);
  const facilitators = ids.map((id) => ({
    name: fields.get(`staff.${id}.name`),
    role: fields.get(`staff.${id}.role`),
    gender: fields.get(`staff.${id}.gender`),
    qualifiedTiers: fields.getAll(`staff.${id}.qualifiedTiers`),
  }));
  const facilitatorExceptions = ids.flatMap((id, facilitatorIndex) => {
    const allowAgeTierMismatch = fields.has(`staff.${id}.allowAgeTierMismatch`);
    const allowGenderMismatch = fields.has(`staff.${id}.allowGenderMismatch`);
    return allowAgeTierMismatch || allowGenderMismatch
      ? [{ facilitatorIndex, allowAgeTierMismatch, allowGenderMismatch }]
      : [];
  });
  return { facilitators, facilitatorExceptions };
}

export function RegistrationPage({ eventId, navigate = navigateTo }) {
  const path = pathFor(eventId);
  const assignment = useAssignment(eventId);
  const [participants, setParticipants] = useState([]);
  const [rosterError, setRosterError] = useState(null);
  const [editor, setEditor] = useState({ version: 0, person: null });
  const [staff, setStaff] = useState([]);
  const staffId = useRef(0);
  const rosterRequests = useRef({ sequence: 0, active: true });
  const [feedback, setFeedback] = useState({ message: '', error: false });
  const report = (message, error = false) => setFeedback({ message, error });
  const onError = (message) => report(message, true);
  useTitle(assignment.event?.name, 'Session Manager');
  const refreshParticipants = useCallback(async () => {
    const requests = rosterRequests.current;
    const current = ++requests.sequence;
    const roster = await api(`${path}/participants`);
    if (requests.active && current === requests.sequence) {
      setParticipants(roster);
      setEditor((previous) => {
        if (!previous.person) return previous;
        const person = roster.find((candidate) => candidate.id === previous.person.id) || null;
        const unchanged =
          person &&
          ['name', 'age', 'gender', 'lifeStage'].every(
            (field) => person[field] === previous.person[field],
          );
        return unchanged ? previous : { version: previous.version + 1, person };
      });
    }
  }, [path]);
  const eventRevision = assignment.event?.revision;
  useEffect(() => {
    if (eventRevision === undefined) return;
    const requests = rosterRequests.current;
    requests.active = true;
    let disposed = false;
    let retry;
    async function loadParticipants() {
      try {
        await refreshParticipants();
        if (!disposed) setRosterError(null);
      } catch (error) {
        if (!disposed) {
          setRosterError(error.message);
          retry = window.setTimeout(loadParticipants, 2000);
        }
      }
    }
    loadParticipants();
    return () => {
      disposed = true;
      window.clearTimeout(retry);
      requests.active = false;
      ++requests.sequence;
    };
  }, [refreshParticipants, eventRevision]);
  const edit = (person) => setEditor((previous) => ({ version: previous.version + 1, person }));
  async function saveParticipant(form) {
    const fields = new FormData(form);
    const id = fields.get('participantId');
    await api(
      `${path}/participants${id ? `/${encodeURIComponent(id)}` : ''}`,
      id ? 'PUT' : 'POST',
      {
        name: fields.get('name'),
        age: Number(fields.get('age')),
        gender: fields.get('gender'),
        lifeStage: fields.get('lifeStage') || null,
      },
    );
    edit(null);
    await refreshParticipants();
    await assignment.refresh();
    report('Participant saved. Generate groups again when ready.');
  }
  async function removeParticipant(person) {
    if (!window.confirm(`Remove ${person.name} from this event?`)) return;
    try {
      await api(`${path}/participants/${encodeURIComponent(person.id)}`, 'DELETE');
      if (editor.person?.id === person.id) edit(null);
      await refreshParticipants();
      await assignment.refresh();
      report('Participant removed. Generate groups again when ready.');
    } catch (error) {
      onError(error.message);
    }
  }
  async function saveSettings(form) {
    await api(path, 'PUT', { name: new FormData(form).get('name'), config: configFrom(form) });
    await refreshParticipants();
    await assignment.refresh();
    report('Settings saved. The current assignment below reflects whether regrouping is needed.');
  }
  async function deleteEvent() {
    if (!window.confirm('Delete this event, all its participants and its current assignment?'))
      return;
    try {
      await api(path, 'DELETE');
      navigate('/events');
    } catch (error) {
      onError(error.message);
    }
  }
  async function generate(form) {
    await api(`${path}/assignment`, 'POST', staffFrom(form, staff));
    form.querySelectorAll('[data-permission]').forEach((input) => {
      input.checked = false;
    });
    await assignment.refresh();
    report('Groups computed. Explicit exceptions have been cleared for the next run.');
  }
  if (!assignment.event)
    return (
      <main className="shell">
        <a href="/events">← All events</a>
        <p>Loading event…</p>
        <Assignment snapshot={null} error={assignment.error} />
        <Feedback feedback={feedback} />
      </main>
    );
  const event = assignment.event;
  return (
    <main className="shell">
      <header>
        <a href="/events">← All events</a>
        <p className="eyebrow">EVENT REGISTRATION</p>
        <h1>{event.name}</h1>
      </header>
      <Feedback feedback={feedback} />
      {rosterError && (
        <p className="warning" role="alert">
          {rosterError} Retrying participant list…
        </p>
      )}
      <div className="columns">
        <section className="card">
          <h2>Register participant</h2>
          <p className="muted">Remote changes to the participant being edited refresh this form.</p>
          <ParticipantEditor
            key={editor.version}
            person={editor.person}
            clear={() => edit(null)}
            save={saveParticipant}
            onError={onError}
          />
          <h3>
            Registered attendees <span className="muted">({participants.length})</span>
          </h3>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Age</th>
                  <th>Gender</th>
                  <th>Tier / life stage</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {participants.map((person) => (
                  <tr key={person.id}>
                    <td>{person.name}</td>
                    <td>{person.age}</td>
                    <td>{person.gender}</td>
                    <td>
                      {[person.ageTier || 'Excluded', person.lifeStage].filter(Boolean).join(' / ')}
                    </td>
                    <td>
                      <button type="button" className="secondary" onClick={() => edit(person)}>
                        Edit
                      </button>
                      <button
                        type="button"
                        className="secondary"
                        onClick={() => removeParticipant(person)}
                      >
                        Remove
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
        <section className="card">
          <details>
            <summary>Event settings</summary>
            <p className="muted">
              Changes saved at another registration desk refresh these fields.
            </p>
            <AsyncForm
              key={JSON.stringify([event.name, event.config])}
              name="Event settings"
              action={saveSettings}
              onError={onError}
            >
              <label>
                Event name
                <input name="name" maxLength="200" required defaultValue={event.name} />
              </label>
              <ConfigFields config={event.config} />
              <button type="submit">Save settings</button>
            </AsyncForm>
            <button type="button" className="danger" onClick={deleteEvent}>
              Delete event and its participants
            </button>
          </details>
          <h2>Facilitator roster</h2>
          <p className="muted">
            Enter available staff for this run. Facilitators do not count toward group size. This
            form is not retained after leaving the page.
          </p>
          <AsyncForm name="Facilitator roster" action={generate} onError={onError}>
            {staff.map((id) => (
              <FacilitatorFields
                key={id}
                id={id}
                remove={() => setStaff((previous) => previous.filter((value) => value !== id))}
              />
            ))}
            <button
              type="button"
              className="secondary"
              onClick={() => {
                const id = staffId.current++;
                setStaff((previous) => [...previous, id]);
              }}
            >
              Add facilitator
            </button>
            <p className="muted">
              Exceptions apply only to this run and cannot waive participant rules, roles or the
              hard cap.
            </p>
            <button type="submit">Generate groups</button>
          </AsyncForm>
        </section>
      </div>
      <section className="card">
        <h2>Current assignment</h2>
        <p>
          <a href={`/events/${encodeURIComponent(eventId)}/groups`}>
            Open live display and print/export view
          </a>
        </p>
        <div id="assignment" aria-live="polite">
          <Assignment snapshot={assignment.snapshot} error={assignment.error} />
        </div>
      </section>
    </main>
  );
}
