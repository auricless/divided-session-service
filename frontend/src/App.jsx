import { EventList } from './pages/EventList.jsx';
import { RegistrationPage } from './pages/RegistrationPage.jsx';
import { GroupDisplay } from './pages/GroupDisplay.jsx';
import { navigateTo } from './navigation.js';
export { EventList, RegistrationPage, GroupDisplay };

export function App({ pathname = window.location.pathname, navigate = navigateTo }) {
  if (pathname === '/' || pathname === '/events' || pathname === '/events/')
    return <EventList navigate={navigate} />;
  const route = /^\/events\/([^/]+)(\/groups)?\/?$/.exec(pathname);
  if (route) {
    let eventId;
    try {
      eventId = decodeURIComponent(route[1]);
    } catch {
      return (
        <main className="shell">
          <h1>Page not found</h1>
          <a href="/events">All events</a>
        </main>
      );
    }
    return route[2] ? (
      <GroupDisplay key={eventId} eventId={eventId} navigate={navigate} />
    ) : (
      <RegistrationPage key={eventId} eventId={eventId} navigate={navigate} />
    );
  }
  return (
    <main className="shell">
      <h1>Page not found</h1>
      <a href="/events">All events</a>
    </main>
  );
}
