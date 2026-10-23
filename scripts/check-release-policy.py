"""Check credential boundaries in the manual application release workflow."""

from pathlib import Path
import re


WORKFLOW = Path(__file__).resolve().parents[1] / '.github/workflows/release.yml'


def jobs_and_global_permissions(source):
    lines = source.splitlines()
    jobs_start = next(i for i, line in enumerate(lines) if line == 'jobs:')
    header = '\n'.join(lines[:jobs_start])
    jobs = {}
    current = None
    for line in lines[jobs_start + 1:]:
        match = re.fullmatch(r'  ([a-z][a-z0-9-]*):', line)
        if match:
            current = match.group(1)
            jobs[current] = []
        elif current is not None:
            jobs[current].append(line)
    return header, {name: '\n'.join(lines) for name, lines in jobs.items()}


def permissions(block):
    match = re.search(r'(?m)^    permissions:\s*\n((?:^      .*(?:\n|$))+)', block)
    if not match:
        return {}
    return dict(re.findall(r'(?m)^      ([a-z-]+):\s*([a-z-]+)\s*$', match.group(1)))


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def main():
    header, jobs = jobs_and_global_permissions(WORKFLOW.read_text())
    require(set(jobs) == {'fetch-core', 'build', 'publish'},
            'Release must have distinct fetch-core, build, and publish jobs.')
    require('id-token: write' not in header,
            'The workflow must not grant OIDC tokens globally.')
    for name in ['fetch-core', 'publish']:
        require(permissions(jobs[name]).get('id-token') == 'write',
                f'{name} must explicitly request an OIDC token.')
    require(permissions(jobs['build']).get('id-token') != 'write',
            'The build job must not receive an OIDC token.')
    require('needs: fetch-core' in jobs['build'] and 'needs: build' in jobs['publish'],
            'The release jobs must run in fetch, build, publish order.')
    require('aws-actions/configure-aws-credentials@' not in jobs['build'] and
            not re.search(r'(?m)^      AWS_[A-Z_]+:', jobs['build']),
            'The build job must not receive AWS credentials or role configuration.')
    require('AWS_CODEARTIFACT_ROLE_ARN' in jobs['fetch-core'] and
            'AWS_ROLE_ARN' not in jobs['fetch-core'].replace('AWS_CODEARTIFACT_ROLE_ARN', ''),
            'Core retrieval must use the separate CodeArtifact role.')
    require('get-package-version-asset' in jobs['fetch-core'] and
            'get-authorization-token' not in jobs['fetch-core'],
            'Core retrieval must download assets without a bearer token.')
    for name in ['fetch-core', 'publish']:
        require(not re.search(r'\b(?:npm|mvn|mvnw|gradle|pytest|vitest|playwright)\b', jobs[name]),
                f'{name} must not run dependency, build, or test commands.')
    require('actions/checkout@' not in jobs['fetch-core'],
            'The credentialed core retrieval job must not check out source.')
    require('docker load' in jobs['publish'] and 'scripts/publish-ecr.py' in jobs['publish'],
            'Publishing must use the already verified image archive.')
    require('docker save' in jobs['build'] and 'scripts/smoke-container-stack.py' in jobs['build'],
            'The build job must verify and archive the image.')
    print('Release credential boundaries pass.')


if __name__ == '__main__':
    main()
