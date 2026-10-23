"""Check the trust policies against GitHub's immutable repository subject format."""
import json
from pathlib import Path
from string import Template
import unittest


ROOT = Path(__file__).resolve().parents[1]
VALUES = {
    'AWS_ACCOUNT_ID': '123456789012',
    'GITHUB_OWNER_ID': '42',
    'CORE_REPOSITORY_ID': '101',
    'SERVICE_REPOSITORY_ID': '202',
}


def policy(name):
    source = (ROOT / 'infrastructure/aws' / name).read_text()
    return json.loads(Template(source).substitute(VALUES))['Statement'][0]


class ImmutableTrustTest(unittest.TestCase):
    def test_subjects_bind_the_owner_repository_and_release_branch(self):
        cases = [
            ('trust-core.json.template',
             'repo:auricless@42/session-manager-core@101:ref:refs/heads/master'),
            ('trust-service.json.template',
             'repo:auricless@42/divided-session-service@202:ref:refs/heads/master'),
        ]
        for name, expected in cases:
            with self.subTest(template=name):
                self.assertEqual(
                    expected,
                    policy(name)['Condition']['StringEquals']['token.actions.githubusercontent.com:sub'],
                )

    def test_policies_require_the_sts_audience_and_exact_subject(self):
        for name in ['trust-core.json.template', 'trust-service.json.template']:
            with self.subTest(template=name):
                statement = policy(name)
                self.assertEqual('sts:AssumeRoleWithWebIdentity', statement['Action'])
                equals = statement['Condition']['StringEquals']
                self.assertEqual('sts.amazonaws.com', equals['token.actions.githubusercontent.com:aud'])
                self.assertNotIn('*', equals['token.actions.githubusercontent.com:sub'])


if __name__ == '__main__':
    unittest.main()
