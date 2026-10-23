export const navigateTo = (path) => window.location.assign(path);
export const pathFor = (id) => `/api/v1/events/${encodeURIComponent(id)}`;
