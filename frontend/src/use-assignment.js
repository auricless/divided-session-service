import { useCallback, useEffect, useRef, useState } from 'react';
import { createAssignmentFeed } from './assignment-feed.js';

export function useAssignment(eventId) {
  const [event, setEvent] = useState(null);
  const [snapshot, setSnapshot] = useState(null);
  const [error, setError] = useState(null);
  const feed = useRef(null);
  useEffect(() => {
    const current = createAssignmentFeed(`/api/v1/events/${encodeURIComponent(eventId)}`, {
      onEvent: setEvent,
      onSnapshot: setSnapshot,
      onError: setError,
    });
    feed.current = current;
    current.start();
    return () => {
      current.dispose();
      feed.current = null;
    };
  }, [eventId]);
  const refresh = useCallback(() => feed.current?.refresh() ?? Promise.resolve(null), []);
  const show = useCallback((value) => feed.current?.show(value), []);
  return { event, snapshot, error, refresh, show };
}
