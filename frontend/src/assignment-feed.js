import { api } from './api.js';

// Shared by registration, live display and refresh-before-print/export.
export function createAssignmentFeed(eventPath, options) {
  let sequence = 0;
  let stopped = false;
  let started = false;
  let pollCycle = 0;
  let timer;
  let request;
  let actionInFlight;
  const draw = (snapshot) => options.onSnapshot(snapshot);
  async function read() {
    if (stopped) return null;
    const current = ++sequence;
    request?.abort();
    request = new AbortController();
    const signal = request.signal;
    try {
      const [event, snapshot] = await Promise.all([
        api(eventPath, 'GET', undefined, { signal }),
        api(`${eventPath}/assignment`, 'GET', undefined, { signal }),
      ]);
      if (stopped || current !== sequence) return null;
      options.onEvent(event);
      options.onError(null);
      // Concurrent reads may straddle a roster/config mutation.
      const verified = snapshot?.revision === event.revision ? snapshot : null;
      draw(verified);
      return verified;
    } catch (error) {
      if (!stopped && current === sequence) {
        draw(null);
        options.onError(error);
      }
      throw error;
    }
  }
  async function refresh() {
    const pending = read();
    actionInFlight = pending;
    try {
      return await pending;
    } finally {
      if (actionInFlight === pending) actionInFlight = null;
    }
  }
  async function poll(cycle) {
    try {
      // A user refresh takes priority; background work shares its result.
      await (actionInFlight || read());
    } catch {
      /* refresh already clears unverified results; retry on the next poll. */
    } finally {
      if (!stopped && cycle === pollCycle) timer = window.setTimeout(() => poll(cycle), 2000);
    }
  }
  function start() {
    if (started && !stopped) return;
    started = true;
    stopped = false;
    return poll(++pollCycle);
  }
  function stop() {
    stopped = true;
    ++sequence;
    ++pollCycle;
    request?.abort();
    actionInFlight = null;
    window.clearTimeout(timer);
  }
  function hide() {
    stop();
    draw(null);
  }
  function restore(event) {
    if (event.persisted && started && stopped) start();
  }
  window.addEventListener('pagehide', hide);
  window.addEventListener('pageshow', restore);
  return {
    refresh,
    start,
    show(snapshot) {
      if (!stopped) {
        ++sequence;
        request?.abort();
        draw(snapshot);
        options.onError(null);
      }
    },
    dispose() {
      stop();
      window.removeEventListener('pagehide', hide);
      window.removeEventListener('pageshow', restore);
    },
  };
}
