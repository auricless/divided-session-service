// @vitest-environment node
import { createRequire } from 'node:module';
import { spawnSync } from 'node:child_process';
import { expect, it } from 'vitest';

const require = createRequire(import.meta.url);
const Ajv = require('ajv');
const uriPath = createRequire(require.resolve('ajv')).resolve('uri-js');

it('resolves nested schema references used by the linter without changing Ajv major versions', () => {
  const ajv = new Ajv();
  ajv.addSchema({
    $id: 'https://schemas.example.test/shared/person.json',
    definitions: { 'a/b': { type: 'integer', minimum: 1 } },
  });
  const validate = ajv.compile({
    $id: 'https://schemas.example.test/app/config.json',
    type: 'object',
    properties: { age: { $ref: '../shared/person.json#/definitions/a~1b' } },
    required: ['age'],
    additionalProperties: false,
  });
  expect(validate({ age: 18 })).toBe(true);
  expect(validate({ age: 0 })).toBe(false);
  expect(validate({ age: '18' })).toBe(false);
  expect(validate({ age: 18, extra: true })).toBe(false);
});

it('normalizes Unicode line separators without hanging the linter process', () => {
  // Keep the known uri-js infinite-loop input outside the test process.
  const result = spawnSync(
    process.execPath,
    [
      '-e',
      `
    const uri = require(${JSON.stringify(uriPath)});
    const normalized = uri.normalize('https://schemas.example.test/\\u2028segment', { iri: true });
    if (!normalized.includes('segment')) process.exit(1);
  `,
    ],
    { timeout: 1000, encoding: 'utf8' },
  );
  expect(result.error).toBeUndefined();
  expect(result.status, result.stderr).toBe(0);
});
