import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { api } from './api.js';
import { EventList, RegistrationPage, GroupDisplay } from './App.jsx';

vi.mock('./api.js', () => ({ api: vi.fn() }));
const config = {
  capSize: 6,
  targetSize: 4,
  genderMode: 'SEPARATED',
  activeTiers: ['TEENS', 'YOUTH', 'YOUNG_ADULT'],
  lifeStageSubgroupingEnabled: true,
  ageTierBoundaries: {
    TEENS: { minAge: 13, maxAge: 15 },
    YOUTH: { minAge: 16, maxAge: 22 },
    YOUNG_ADULT: { minAge: 23, maxAge: 2147483647 },
  },
};
const event = { id: 'test-event', name: '<script>Event</script>', revision: 1, config };
const assignment = (revision) => ({
  eventId: event.id,
  revision,
  result: {
    state: 'FINAL',
    totalGroups: 0,
    totalAssignedParticipants: 0,
    groups: [],
    blockers: [],
    warnings: [],
    unassignedParticipants: [],
  },
});
const eventPath = '/api/v1/events/test-event';
let currentEvent, currentAssignment, participants;
beforeEach(() => {
  api.mockReset();
  currentEvent = structuredClone(event);
  currentAssignment = null;
  participants = [];
  api.mockImplementation(async (path, method = 'GET') => {
    if (method !== 'GET') throw new Error('Unexpected mutation');
    if (path === '/api/v1/events') return [];
    if (path === eventPath) return currentEvent;
    if (path === `${eventPath}/assignment`) return currentAssignment;
    if (path === `${eventPath}/participants`) return participants;
    throw new Error(`Unexpected request ${path}`);
  });
});

describe('React registration', () => {
  it('requires an explicit cap and submits the configured defaults when creating an event', async () => {
    const user = userEvent.setup();
    const navigate = vi.fn();
    render(<EventList navigate={navigate} />);
    expect(
      await screen.findByText('No events yet. Create one to begin registration.'),
    ).toBeVisible();
    const form = screen.getByRole('form', { name: 'Create event' });
    expect(within(form).getByLabelText('Maximum participants per group')).toHaveValue(null);
    expect(within(form).getByLabelText('Target participants per group')).toHaveValue(4);
    await user.type(within(form).getByLabelText('Event name'), 'Sunday');
    await user.type(within(form).getByLabelText('Maximum participants per group'), '6');
    api.mockImplementationOnce(async () => event);
    await user.click(within(form).getByRole('button', { name: 'Create event' }));
    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/events/test-event'));
    expect(api).toHaveBeenCalledWith('/api/v1/events', 'POST', { name: 'Sunday', config });
  });

  it('renders untrusted names as text and retains invalid participant input for correction', async () => {
    const user = userEvent.setup();
    render(<RegistrationPage eventId={event.id} navigate={vi.fn()} />);
    expect(await screen.findByRole('heading', { name: event.name })).toBeVisible();
    expect(document.querySelector('script')).toBeNull();
    const form = screen.getByRole('form', { name: 'Register participant' });
    await user.type(within(form).getByLabelText('Name', { exact: true }), 'Ada');
    await user.type(within(form).getByLabelText('Age'), '28');
    await user.selectOptions(within(form).getByLabelText('Gender', { exact: true }), 'FEMALE');
    api.mockImplementationOnce(async () => {
      throw new Error('Young Adult participants require a life stage for this session');
    });
    await user.click(within(form).getByRole('button', { name: 'Save participant' }));
    expect(await screen.findByText(/Young Adult participants require a life stage/)).toBeVisible();
    expect(within(form).getByLabelText('Name', { exact: true })).toHaveValue('Ada');
    expect(within(form).getByLabelText('Age')).toHaveValue(28);
  });

  it('edits and removes the selected participant without replacing its event ownership', async () => {
    const user = userEvent.setup();
    participants = [
      { id: 'person-1', name: 'Alex', age: 18, gender: 'MALE', ageTier: 'YOUTH', lifeStage: null },
    ];
    const original = api.getMockImplementation();
    api.mockImplementation(async (path, method, body) => {
      if (method === 'PUT') {
        participants = [{ ...participants[0], ...body }];
        return participants[0];
      }
      if (method === 'DELETE') {
        participants = [];
        return null;
      }
      return original(path, method, body);
    });
    vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
    render(<RegistrationPage eventId={event.id} navigate={vi.fn()} />);
    await screen.findByRole('button', { name: 'Edit', exact: true });
    await user.click(screen.getByRole('button', { name: 'Edit', exact: true }));
    const form = screen.getByRole('form', { name: 'Register participant' });
    await user.clear(within(form).getByLabelText('Age'));
    await user.type(within(form).getByLabelText('Age'), '19');
    await user.click(within(form).getByRole('button', { name: 'Save participant' }));
    await screen.findByText('Participant saved. Generate groups again when ready.');
    expect(api).toHaveBeenCalledWith(`${eventPath}/participants/person-1`, 'PUT', {
      name: 'Alex',
      age: 19,
      gender: 'MALE',
      lifeStage: null,
    });
    await user.click(screen.getByRole('button', { name: 'Remove', exact: true }));
    expect(api.mock.calls.some(([, method]) => method === 'DELETE')).toBe(false);
    await user.click(screen.getByRole('button', { name: 'Remove', exact: true }));
    await screen.findByText('Participant removed. Generate groups again when ready.');
    expect(api).toHaveBeenCalledWith(`${eventPath}/participants/person-1`, 'DELETE');
    expect(screen.queryByRole('button', { name: 'Remove', exact: true })).not.toBeInTheDocument();
    vi.restoreAllMocks();
  });

  it('shows proposal blockers and clears explicit facilitator permissions after each successful run', async () => {
    const user = userEvent.setup();
    const original = api.getMockImplementation();
    api.mockImplementation(async (path, method, body) => {
      if (method === 'POST') {
        const result = assignment(1);
        result.result.state = 'NON_FINAL_PROPOSAL';
        result.result.blockers = [{ message: 'Assign a LEADER to group 1' }];
        result.result.warnings = body.facilitatorExceptions.length
          ? ['Explicit age-tier exception applied']
          : [];
        currentAssignment = result;
        return result;
      }
      return original(path, method, body);
    });
    render(<RegistrationPage eventId={event.id} navigate={vi.fn()} />);
    await screen.findByRole('heading', { name: event.name });
    await user.click(screen.getByRole('button', { name: 'Add facilitator' }));
    const facilitator = screen.getByRole('group', { name: 'Facilitator', exact: true });
    await user.type(within(facilitator).getByLabelText('Name', { exact: true }), 'Sam');
    await user.selectOptions(within(facilitator).getByLabelText('Gender', { exact: true }), 'MALE');
    await user.click(within(facilitator).getByLabelText('Youth', { exact: true }));
    const permission = within(facilitator).getByLabelText(
      'Allow an age-tier qualification exception for this run',
    );
    await user.click(permission);
    await user.click(screen.getByRole('button', { name: 'Generate groups' }));
    expect(await screen.findByText('Assign a LEADER to group 1')).toBeVisible();
    expect(screen.getByText('Explicit age-tier exception applied')).toBeVisible();
    expect(permission).not.toBeChecked();
    await user.click(screen.getByRole('button', { name: 'Generate groups' }));
    const calls = api.mock.calls.filter(([, method]) => method === 'POST');
    expect(calls[0][2].facilitatorExceptions).toEqual([
      { facilitatorIndex: 0, allowAgeTierMismatch: true, allowGenderMismatch: false },
    ]);
    expect(calls[1][2].facilitatorExceptions).toEqual([]);
  });

  it.each([
    ['invalidated', null],
    ['replaced', assignment(3)],
  ])('keeps a newer %s assignment when an older generation response arrives', async (_, newer) => {
    vi.useFakeTimers();
    const original = api.getMockImplementation();
    let resolvePost;
    api.mockImplementation((path, method, body) => {
      if (path === `${eventPath}/assignment` && method === 'POST') {
        currentEvent = { ...currentEvent, revision: 2 };
        currentAssignment = assignment(2);
        return new Promise((resolve) => {
          resolvePost = resolve;
        });
      }
      return original(path, method, body);
    });
    try {
      render(<RegistrationPage eventId={event.id} />);
      await act(async () => {
        await Promise.resolve();
      });
      fireEvent.submit(screen.getByRole('form', { name: 'Facilitator roster' }));
      expect(resolvePost).toBeTypeOf('function');
      currentEvent = { ...currentEvent, revision: 3 };
      currentAssignment = newer && {
        ...newer,
        result: { ...newer.result, totalAssignedParticipants: 3 },
      };
      await act(async () => vi.advanceTimersByTimeAsync(2000));
      if (newer)
        expect(document.querySelector('#assignment')).toHaveTextContent('3 assigned participants');
      else expect(screen.getByText(/No current assignment/)).toBeVisible();
      await act(async () => resolvePost(assignment(2)));
      if (newer)
        expect(document.querySelector('#assignment')).toHaveTextContent('3 assigned participants');
      else expect(screen.getByText(/No current assignment/)).toBeVisible();
      expect(currentEvent.revision).toBe(3);
    } finally {
      vi.useRealTimers();
    }
  });

  it('clears one-run permissions and the old assignment when verification fails after generation', async () => {
    const user = userEvent.setup();
    const original = api.getMockImplementation();
    currentAssignment = assignment(1);
    let postSucceeded = false;
    api.mockImplementation((path, method, body) => {
      if (path === `${eventPath}/assignment` && method === 'POST') {
        postSucceeded = true;
        currentEvent = { ...currentEvent, revision: 2 };
        currentAssignment = assignment(2);
        return currentAssignment;
      }
      if (postSucceeded && path === eventPath) throw new Error('Verification offline');
      return original(path, method, body);
    });
    render(<RegistrationPage eventId={event.id} />);
    expect(await screen.findByText('Final assignment')).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Add facilitator' }));
    const facilitator = screen.getByRole('group', { name: 'Facilitator', exact: true });
    await user.type(within(facilitator).getByLabelText('Name', { exact: true }), 'Sam');
    await user.selectOptions(within(facilitator).getByLabelText('Gender', { exact: true }), 'MALE');
    await user.click(within(facilitator).getByLabelText('Youth', { exact: true }));
    const permission = within(facilitator).getByLabelText(
      'Allow an age-tier qualification exception for this run',
    );
    await user.click(permission);
    await user.click(screen.getByRole('button', { name: 'Generate groups' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Verification offline');
    expect(permission).not.toBeChecked();
    expect(screen.queryByText('Final assignment')).not.toBeInTheDocument();
  });
});

describe('React live display', () => {
  it('disables print/export when their own refresh observes mismatched revisions', async () => {
    currentAssignment = assignment(1);
    const print = vi.spyOn(window, 'print').mockImplementation(() => {});
    render(<GroupDisplay eventId={event.id} />);
    await screen.findByText('Final assignment');
    expect(screen.getByRole('button', { name: 'Print roster' })).toBeEnabled();
    currentEvent = { ...event, revision: 2 };
    fireEvent.click(screen.getByRole('button', { name: 'Print roster' }));
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Print roster' })).toBeDisabled(),
    );
    expect(screen.getByRole('button', { name: 'Export CSV' })).toBeDisabled();
    expect(print).not.toHaveBeenCalled();
    print.mockRestore();
  });

  it('clears output on pagehide and refreshes on persisted pageshow, then keeps polling', async () => {
    vi.useFakeTimers();
    currentAssignment = assignment(1);
    try {
      render(<GroupDisplay eventId={event.id} />);
      await act(async () => {
        await Promise.resolve();
      });
      expect(screen.getByText('Final assignment')).toBeVisible();
      act(() => window.dispatchEvent(new Event('pagehide')));
      expect(screen.getByRole('button', { name: 'Print roster' })).toBeDisabled();
      currentEvent = { ...event, revision: 2 };
      currentAssignment = assignment(2);
      await act(async () =>
        window.dispatchEvent(Object.assign(new Event('pageshow'), { persisted: true })),
      );
      expect(screen.getByRole('button', { name: 'Print roster' })).toBeEnabled();
      currentEvent = { ...event, revision: 3 };
      currentAssignment = null;
      await act(async () => vi.advanceTimersByTimeAsync(2000));
      expect(screen.getByRole('button', { name: 'Print roster' })).toBeDisabled();
    } finally {
      vi.useRealTimers();
    }
  });
});

it('saves event settings without changing the API contract or event identity', async () => {
  const user = userEvent.setup();
  const original = api.getMockImplementation();
  api.mockImplementation(async (path, method, body) => {
    if (method === 'PUT') {
      currentEvent = { ...currentEvent, ...body, revision: 2 };
      return currentEvent;
    }
    return original(path, method, body);
  });
  render(<RegistrationPage eventId={event.id} />);
  await screen.findByRole('heading', { name: event.name });
  await user.click(screen.getByText('Event settings', { selector: 'summary' }));
  const form = screen.getByRole('form', { name: 'Event settings' });
  await user.clear(within(form).getByLabelText('Event name'));
  await user.type(within(form).getByLabelText('Event name'), 'Renamed event');
  await user.clear(within(form).getByLabelText('Target participants per group'));
  await user.type(within(form).getByLabelText('Target participants per group'), '3');
  await user.click(
    within(form).getByLabelText('Separate Young Adult students and working professionals'),
  );
  await user.click(within(form).getByRole('button', { name: 'Save settings' }));
  expect(await screen.findByRole('heading', { name: 'Renamed event' })).toBeVisible();
  expect(api).toHaveBeenCalledWith(eventPath, 'PUT', {
    name: 'Renamed event',
    config: { ...config, targetSize: 3, lifeStageSubgroupingEnabled: false },
  });
});

it('requires confirmation before deleting the event and returns to the event list', async () => {
  const user = userEvent.setup();
  const navigate = vi.fn();
  const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
  render(<RegistrationPage eventId={event.id} navigate={navigate} />);
  await screen.findByRole('heading', { name: event.name });
  await user.click(screen.getByText('Event settings', { selector: 'summary' }));
  await user.click(screen.getByRole('button', { name: 'Delete event and its participants' }));
  expect(api.mock.calls.some(([, method]) => method === 'DELETE')).toBe(false);
  api.mockImplementationOnce(async () => null);
  await user.click(screen.getByRole('button', { name: 'Delete event and its participants' }));
  await waitFor(() => expect(navigate).toHaveBeenCalledWith('/events'));
  expect(api).toHaveBeenCalledWith(eventPath, 'DELETE');
  confirm.mockRestore();
});

it('clears print/export on a connection failure and retries on the next poll', async () => {
  vi.useFakeTimers();
  const original = api.getMockImplementation();
  currentAssignment = assignment(1);
  try {
    render(<GroupDisplay eventId={event.id} />);
    await act(async () => {
      await Promise.resolve();
    });
    expect(screen.getByRole('button', { name: 'Print roster' })).toBeEnabled();
    api.mockImplementation(async () => {
      throw new Error('Offline');
    });
    await act(async () => vi.advanceTimersByTimeAsync(2000));
    expect(screen.getByRole('alert')).toHaveTextContent(
      'Cannot verify the current assignment: Offline',
    );
    expect(screen.getByRole('button', { name: 'Print roster' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Export CSV' })).toBeDisabled();
    api.mockImplementation(original);
    await act(async () => vi.advanceTimersByTimeAsync(2000));
    expect(screen.getByRole('button', { name: 'Print roster' })).toBeEnabled();
  } finally {
    vi.useRealTimers();
  }
});

it('exports only after its own refresh returns a current snapshot', async () => {
  const user = userEvent.setup();
  const navigate = vi.fn();
  currentAssignment = assignment(1);
  render(<GroupDisplay eventId={event.id} navigate={navigate} />);
  await screen.findByText('Final assignment');
  await user.click(screen.getByRole('button', { name: 'Export CSV' }));
  await waitFor(() => expect(navigate).toHaveBeenCalledWith(`${eventPath}/assignment/export.csv`));
  currentEvent = { ...event, revision: 2 };
  await user.click(screen.getByRole('button', { name: 'Export CSV' }));
  await waitFor(() => expect(screen.getByRole('button', { name: 'Export CSV' })).toBeDisabled());
  expect(navigate).toHaveBeenCalledTimes(1);
});

it('commits a newly refreshed roster to the DOM before opening the print dialog', async () => {
  const group = (people) => ({
    ageTierTarget: 'YOUTH',
    genderTarget: 'MALE',
    lifeStageTarget: null,
    leader: { name: 'Sam' },
    assistant: null,
    size: people.length,
    participants: people.map((name) => ({ name, age: 18 })),
  });
  currentAssignment = assignment(1);
  currentAssignment.result = {
    ...currentAssignment.result,
    totalGroups: 1,
    totalAssignedParticipants: 1,
    groups: [group(['Before refresh'])],
  };
  const printed = [];
  const print = vi.spyOn(window, 'print').mockImplementation(() => {
    printed.push(document.querySelector('#assignment').textContent);
  });
  try {
    render(<GroupDisplay eventId={event.id} />);
    await screen.findByText('Before refresh (18)');
    currentEvent = { ...event, revision: 2 };
    currentAssignment = assignment(2);
    currentAssignment.result = {
      ...currentAssignment.result,
      totalGroups: 1,
      totalAssignedParticipants: 2,
      groups: [group(['After refresh', 'New walk-in'])],
    };
    fireEvent.click(screen.getByRole('button', { name: 'Print roster' }));
    await waitFor(() => expect(print).toHaveBeenCalledOnce());
    expect(printed[0]).toContain('New walk-in');
    expect(printed[0]).toContain('2 assigned participants');
    expect(printed[0]).not.toContain('Before refresh');
  } finally {
    print.mockRestore();
  }
});

it('refreshes remotely changed settings while preserving drafts across roster-only revisions', async () => {
  vi.useFakeTimers();
  const original = api.getMockImplementation();
  api.mockImplementation(async (path, method, body) => {
    if (method === 'PUT') {
      currentEvent = { ...currentEvent, ...body };
      return currentEvent;
    }
    return original(path, method, body);
  });
  try {
    render(<RegistrationPage eventId={event.id} />);
    await act(async () => {
      await Promise.resolve();
    });
    fireEvent.click(screen.getByText('Event settings', { selector: 'summary' }));
    let form = screen.getByRole('form', { name: 'Event settings' });
    fireEvent.change(within(form).getByLabelText('Target participants per group'), {
      target: { value: '3' },
    });
    currentEvent = { ...currentEvent, revision: 2 };
    await act(async () => vi.advanceTimersByTimeAsync(2000));
    expect(within(form).getByLabelText('Target participants per group')).toHaveValue(3);
    const remoteConfig = {
      ...config,
      capSize: 8,
      targetSize: 5,
      genderMode: 'MIXED',
      activeTiers: ['YOUTH'],
      lifeStageSubgroupingEnabled: false,
      ageTierBoundaries: {
        TEENS: { minAge: 13, maxAge: 17 },
        YOUTH: { minAge: 18, maxAge: 25 },
        YOUNG_ADULT: { minAge: 26, maxAge: 2147483647 },
      },
    };
    currentEvent = { ...currentEvent, name: 'Remote settings', config: remoteConfig, revision: 3 };
    await act(async () => vi.advanceTimersByTimeAsync(2000));
    form = screen.getByRole('form', { name: 'Event settings' });
    expect(within(form).getByLabelText('Event name')).toHaveValue('Remote settings');
    expect(within(form).getByLabelText('Maximum participants per group')).toHaveValue(8);
    expect(within(form).getByLabelText('Target participants per group')).toHaveValue(5);
    expect(within(form).getByLabelText('Gender grouping')).toHaveValue('MIXED');
    expect(within(form).getByLabelText('TEENS from age')).toHaveValue(13);
    expect(within(form).getByLabelText('YOUTH from age')).toHaveValue(18);
    expect(
      within(form).getByLabelText('Separate Young Adult students and working professionals'),
    ).not.toBeChecked();
    await act(async () => fireEvent.submit(form));
    expect(api).toHaveBeenCalledWith(eventPath, 'PUT', {
      name: 'Remote settings',
      config: remoteConfig,
    });
  } finally {
    vi.useRealTimers();
  }
});

it('refreshes participant counts and rows after remote edits, deletions and cache restoration', async () => {
  vi.useFakeTimers();
  participants = [{ id: 'remote', name: 'Before edit', age: 18, gender: 'MALE', ageTier: 'YOUTH' }];
  try {
    render(<RegistrationPage eventId={event.id} />);
    await act(async () => {
      await Promise.resolve();
    });
    expect(screen.getByText('Before edit')).toBeVisible();
    participants = [{ ...participants[0], name: 'After remote edit' }];
    currentEvent = { ...currentEvent, revision: 2 };
    await act(async () => vi.advanceTimersByTimeAsync(2000));
    expect(screen.getByText('After remote edit')).toBeVisible();
    expect(screen.queryByText('Before edit')).not.toBeInTheDocument();
    act(() => window.dispatchEvent(new Event('pagehide')));
    participants = [];
    currentEvent = { ...currentEvent, revision: 3 };
    await act(async () =>
      window.dispatchEvent(Object.assign(new Event('pageshow'), { persisted: true })),
    );
    expect(screen.queryByText('After remote edit')).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Registered attendees/ })).toHaveTextContent('(0)');
  } finally {
    vi.useRealTimers();
  }
});

it('synchronizes the selected participant on remote edits and clears it on remote deletion', async () => {
  vi.useFakeTimers();
  participants = [{ id: 'remote', name: 'Original', age: 18, gender: 'MALE', ageTier: 'YOUTH' }];
  try {
    render(<RegistrationPage eventId={event.id} />);
    await act(async () => {
      await Promise.resolve();
    });
    fireEvent.click(screen.getByRole('button', { name: 'Edit', exact: true }));
    const nameInput = () =>
      within(screen.getByRole('form', { name: 'Register participant' })).getByLabelText('Name', {
        exact: true,
      });
    fireEvent.change(nameInput(), { target: { value: 'Unsaved draft' } });
    currentEvent = { ...currentEvent, revision: 2 };
    await act(async () => vi.advanceTimersByTimeAsync(2000));
    expect(nameInput()).toHaveValue('Unsaved draft');
    participants = [{ ...participants[0], name: 'Remote correction', age: 19 }];
    currentEvent = { ...currentEvent, revision: 3 };
    await act(async () => vi.advanceTimersByTimeAsync(2000));
    expect(nameInput()).toHaveValue('Remote correction');
    participants = [];
    currentEvent = { ...currentEvent, revision: 4 };
    await act(async () => vi.advanceTimersByTimeAsync(2000));
    expect(nameInput()).toHaveValue('');
    expect(document.querySelector('input[name="participantId"]')).toHaveValue('');
  } finally {
    vi.useRealTimers();
  }
});

it('reloads the event list after cached restoration and ignores a late pre-navigation response', async () => {
  const pending = [];
  api.mockImplementation(() => new Promise((resolve) => pending.push(resolve)));
  const view = render(<EventList />);
  act(() => window.dispatchEvent(new Event('pagehide')));
  await act(async () =>
    window.dispatchEvent(Object.assign(new Event('pageshow'), { persisted: true })),
  );
  expect(pending).toHaveLength(2);
  await act(async () => pending[1]([{ id: 'new', name: 'New event' }]));
  await act(async () => pending[0]([]));
  expect(screen.getByRole('link', { name: 'New event' })).toHaveAttribute('href', '/events/new');
  view.unmount();
  window.dispatchEvent(Object.assign(new Event('pageshow'), { persisted: true }));
  expect(pending).toHaveLength(2);
});

it('retries a failed participant read without requiring another event mutation', async () => {
  vi.useFakeTimers();
  const original = api.getMockImplementation();
  let attempts = 0;
  participants = [
    { id: 'retry', name: 'Recovered attendee', age: 18, gender: 'MALE', ageTier: 'YOUTH' },
  ];
  api.mockImplementation(async (path, ...args) => {
    if (path === `${eventPath}/participants` && ++attempts === 1)
      throw new Error('Roster temporarily offline');
    return original(path, ...args);
  });
  try {
    render(<RegistrationPage eventId={event.id} />);
    await act(async () => {
      await Promise.resolve();
    });
    expect(screen.getByText(/Roster temporarily offline/)).toBeVisible();
    await act(async () => vi.advanceTimersByTimeAsync(2000));
    expect(screen.getByText('Recovered attendee')).toBeVisible();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Registered attendees/ })).toHaveTextContent('(1)');
  } finally {
    vi.useRealTimers();
  }
});

it('preserves participant drafts when remote configuration changes only the derived tier', async () => {
  vi.useFakeTimers();
  participants = [{ id: 'draft', name: 'Original', age: 18, gender: 'MALE', ageTier: 'YOUTH' }];
  try {
    render(<RegistrationPage eventId={event.id} />);
    await act(async () => {
      await Promise.resolve();
    });
    fireEvent.click(screen.getByRole('button', { name: 'Edit', exact: true }));
    const form = () => screen.getByRole('form', { name: 'Register participant' });
    fireEvent.change(within(form()).getByLabelText('Name', { exact: true }), {
      target: { value: 'Unsaved name' },
    });
    fireEvent.change(within(form()).getByLabelText('Age'), { target: { value: '28' } });
    currentEvent = {
      ...currentEvent,
      revision: 2,
      config: {
        ...config,
        ageTierBoundaries: {
          ...config.ageTierBoundaries,
          TEENS: { minAge: 13, maxAge: 18 },
          YOUTH: { minAge: 19, maxAge: 22 },
        },
      },
    };
    participants = [{ ...participants[0], ageTier: 'TEENS' }];
    await act(async () => vi.advanceTimersByTimeAsync(2000));
    expect(within(form()).getByLabelText('Name', { exact: true })).toHaveValue('Unsaved name');
    expect(within(form()).getByLabelText('Age')).toHaveValue(28);
    expect(screen.getByRole('cell', { name: 'TEENS' })).toBeVisible();
  } finally {
    vi.useRealTimers();
  }
});
