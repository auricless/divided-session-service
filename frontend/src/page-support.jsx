import { useEffect } from 'react';
export function Feedback({ feedback }) {
  return (
    <p
      id="feedback"
      role="status"
      aria-live="polite"
      className={feedback.error ? 'error' : undefined}
    >
      {feedback.message}
    </p>
  );
}
export function useTitle(name, suffix) {
  useEffect(() => {
    document.title = `${name || 'Session Manager'} · ${suffix}`;
  }, [name, suffix]);
}
