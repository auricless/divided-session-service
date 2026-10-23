import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { api } from './api.js';
import { createAssignmentFeed } from './assignment-feed.js';
vi.mock('./api.js', () => ({ api: vi.fn() }));
let feed, requests, snapshots, errors;
const snapshot = (revision) => ({ revision, result: { state: 'FINAL' } });
beforeEach(() => {
  vi.useFakeTimers();
  requests = [];
  snapshots = [];
  errors = [];
  api
    .mockReset()
    .mockImplementation(
      (path, method, body, { signal }) =>
        new Promise((resolve, reject) => requests.push({ path, resolve, reject, signal })),
    );
  feed = createAssignmentFeed('/event', {
    onEvent: vi.fn(),
    onSnapshot: (value) => snapshots.push(value),
    onError: (value) => errors.push(value),
  });
});
afterEach(() => {
  feed.dispose();
  vi.useRealTimers();
});
function answer(offset, revision) {
  requests[offset].resolve({ revision });
  requests[offset + 1].resolve(snapshot(revision));
}
const flush = async () => {
  await Promise.resolve();
  await Promise.resolve();
  await Promise.resolve();
};
it('ignores pre-navigation responses and does not create duplicate loops after repeated restoration', async () => {
  feed.start();
  window.dispatchEvent(new Event('pagehide'));
  expect(requests[0].signal.aborted).toBe(true);
  window.dispatchEvent(Object.assign(new Event('pageshow'), { persisted: true }));
  answer(2, 2);
  await flush();
  answer(0, 1);
  await flush();
  expect(snapshots.at(-1).revision).toBe(2);
  expect(vi.getTimerCount()).toBe(1);
  window.dispatchEvent(new Event('pagehide'));
  window.dispatchEvent(Object.assign(new Event('pageshow'), { persisted: true }));
  answer(4, 3);
  await flush();
  expect(snapshots.at(-1).revision).toBe(3);
  expect(vi.getTimerCount()).toBe(1);
});
it('starts once and removes lifecycle listeners and in-flight work when disposed', async () => {
  feed.start();
  feed.start();
  expect(requests).toHaveLength(2);
  feed.dispose();
  window.dispatchEvent(Object.assign(new Event('pageshow'), { persisted: true }));
  answer(0, 1);
  await flush();
  expect(requests).toHaveLength(2);
  expect(snapshots).toHaveLength(0);
  expect(vi.getTimerCount()).toBe(0);
});
it('a superseded failed request cannot clear a newer assignment', async () => {
  const oldRefresh = feed.refresh();
  const oldFailure = expect(oldRefresh).rejects.toThrow('old failure');
  const latest = feed.refresh();
  answer(2, 2);
  await latest;
  requests[0].reject(new Error('old failure'));
  requests[1].resolve(null);
  await oldFailure;
  expect(snapshots.at(-1).revision).toBe(2);
  expect(errors.at(-1)).toBeNull();
});

it('does not let a scheduled background poll cancel an action refresh', async () => {
  feed.start();
  answer(0, 1);
  await flush();
  const action = feed.refresh();
  await vi.advanceTimersByTimeAsync(2000);
  expect(requests[2].signal.aborted).toBe(false);
  expect(requests).toHaveLength(4);
  answer(2, 2);
  expect((await action).revision).toBe(2);
  await flush();
  expect(vi.getTimerCount()).toBe(1);
});

it('restores polling immediately even when a pre-navigation action has not settled', async () => {
  feed.start();
  answer(0, 1);
  await flush();
  const action = feed.refresh();
  window.dispatchEvent(new Event('pagehide'));
  window.dispatchEvent(Object.assign(new Event('pageshow'), { persisted: true }));
  expect(requests).toHaveLength(6);
  answer(4, 3);
  await flush();
  answer(2, 2);
  expect(await action).toBeNull();
  await flush();
  expect(snapshots.at(-1).revision).toBe(3);
  expect(vi.getTimerCount()).toBe(1);
});
