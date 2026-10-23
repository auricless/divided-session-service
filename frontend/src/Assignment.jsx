/* eslint react-x/no-array-index-key: "off" -- Groups/blockers use ordinals; duplicate attendee text is valid. These lists contain no child state. */
function DetailsList({ title, values, warning = false }) {
  if (!values.length) return null;
  return (
    <>
      <h3>{title}</h3>
      <ul className={warning ? 'warning' : undefined}>
        {values.map((value, index) => (
          <li key={index}>{value}</li>
        ))}
      </ul>
    </>
  );
}
export function Assignment({ snapshot, error }) {
  if (error)
    return (
      <p className="warning" role="alert">
        Cannot verify the current assignment: {error.message} Retrying…
      </p>
    );
  if (!snapshot)
    return (
      <p className="muted">
        No current assignment. Generate groups after roster or settings changes.
      </p>
    );
  const result = snapshot.result;
  const final = result.state === 'FINAL';
  return (
    <>
      <p className={`badge${final ? '' : ' proposal'}`} data-state={result.state}>
        {final ? 'Final assignment' : 'Proposal — action required'}
      </p>
      <p>
        {result.totalGroups} {result.totalGroups === 1 ? 'group' : 'groups'} ·{' '}
        {result.totalAssignedParticipants} assigned{' '}
        {result.totalAssignedParticipants === 1 ? 'participant' : 'participants'}
      </p>
      <DetailsList
        title="Actions required"
        values={result.blockers.map((item) => item.message)}
        warning
      />
      <DetailsList title="Warnings" values={result.warnings} warning />
      {result.groups.map((group, index) => (
        <section className="group" key={index}>
          <h3>
            Group {index + 1} ·{' '}
            {[group.ageTierTarget, group.genderTarget, group.lifeStageTarget]
              .filter(Boolean)
              .join(' / ')}
          </h3>
          <p>Leader: {group.leader?.name || 'Not assigned — blocker'}</p>
          {group.assistant && <p>Assistant: {group.assistant.name}</p>}
          <DetailsList
            title={`${group.size} ${group.size === 1 ? 'participant' : 'participants'}`}
            values={group.participants.map((person) => `${person.name} (${person.age})`)}
          />
        </section>
      ))}
      <DetailsList
        title="Unassigned participants"
        values={result.unassignedParticipants.map(
          (item) => `${item.participant.name}: ${item.reason}`,
        )}
        warning
      />
    </>
  );
}
