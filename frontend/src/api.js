export async function api(path, method = 'GET', body, options = {}) {
  const response = await fetch(path, {
    method,
    signal: options.signal,
    cache: 'no-store',
    headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (!response.ok)
    throw new Error(data?.message || data?.detail || `Request failed (${response.status}).`);
  return data;
}
