import { expect, test } from '@playwright/test';

test('registers, assigns and refreshes a printable roster through the production UI origin', async ({
  page,
  request,
}, testInfo) => {
  let path;
  const eventName = `Browser acceptance ${Date.now()}`;
  try {
    await page.goto('/events');
    const create = page.getByRole('form', { name: 'Create event' });
    await create.getByLabel('Event name').fill(eventName);
    await create.getByLabel('Maximum participants per group').fill('6');
    await create.getByRole('button', { name: 'Create event', exact: true }).click();
    await expect(page).toHaveURL(/\/events\/[^/]+$/);
    path = '/api/v1/events/' + new URL(page.url()).pathname.split('/').at(-1);
    await expect(page.getByRole('heading', { name: eventName })).toBeVisible();
    const registration = page.getByRole('form', { name: 'Register participant' });
    for (const name of ['First attendee', 'Second attendee']) {
      await registration.getByLabel('Name', { exact: true }).fill(name);
      await registration.getByLabel('Age', { exact: true }).fill('18');
      await registration
        .getByRole('combobox', { name: 'Gender', exact: true })
        .selectOption('MALE');
      await registration.getByRole('button', { name: 'Save participant' }).click();
      await expect(registration.getByLabel('Name', { exact: true })).toHaveValue('');
    }
    await expect(page.getByRole('heading', { name: /Registered attendees/ })).toContainText('(2)');
    await page.getByRole('button', { name: 'Generate groups' }).click();
    await expect(page.getByText('Proposal — action required')).toBeVisible();
    await page.getByRole('button', { name: 'Add facilitator' }).click();
    const staff = page.getByRole('group', { name: 'Facilitator', exact: true });
    await staff.getByLabel('Name', { exact: true }).fill('Sam');
    await staff.getByRole('combobox', { name: 'Gender', exact: true }).selectOption('MALE');
    await staff.getByLabel('Youth', { exact: true }).check();
    await page.getByRole('button', { name: 'Generate groups' }).click();
    await expect(page.getByText('Final assignment', { exact: true })).toBeVisible();
    await page.getByRole('link', { name: 'Open live display and print/export view' }).click();
    await expect(page.getByRole('button', { name: 'Print roster' })).toBeEnabled();

    // A separate HTTP client updates the roster; the open display must poll it.
    const response = await request.post(path + '/participants', {
      data: { name: 'Remote walk-in', age: 19, gender: 'MALE' },
    });
    expect(response.status()).toBe(201);
    await expect(page.getByRole('button', { name: 'Print roster' })).toBeDisabled();
    await expect(page.getByRole('button', { name: 'Export CSV' })).toBeDisabled();
    const refreshed = await request.post(path + '/assignment', {
      data: {
        facilitators: [{ name: 'Sam', role: 'LEADER', gender: 'MALE', qualifiedTiers: ['YOUTH'] }],
        facilitatorExceptions: [],
      },
    });
    expect(refreshed.ok()).toBe(true);
    await expect(page.getByText('Remote walk-in (19)')).toBeVisible();
    await page.evaluate(() => {
      window.print = () => {
        window.printedRoster = document.querySelector('#assignment').textContent;
      };
    });
    await page.getByRole('button', { name: 'Print roster' }).click();
    await expect.poll(() => page.evaluate(() => window.printedRoster)).toContain('Remote walk-in');
    await expect
      .poll(() => page.evaluate(() => window.printedRoster))
      .toContain('3 assigned participants');
    const downloading = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Export CSV' }).click();
    const download = await downloading;
    const stream = await download.createReadStream();
    const chunks = [];
    for await (const chunk of stream) chunks.push(chunk);
    expect(Buffer.concat(chunks).toString()).toContain('Remote walk-in');
  } finally {
    if (path && testInfo.status !== 'timedOut')
      expect((await request.delete(path)).status()).toBe(204);
  }
});
