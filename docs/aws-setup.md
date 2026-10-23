# AWS setup when you are ready

Nothing in this guide has been run against your account. The app does not need AWS credentials after its image is pulled. CodeArtifact stores the reusable core JAR; ECR stores the complete React/API/core application image. PostgreSQL runs separately.

Use a non-root AWS identity with permission to create the repositories, OIDC provider and IAM roles below. Install [AWS CLI v2](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) through the terminal and authenticate your own named profile. Keep credentials out of chat and Git. If using IAM Identity Center, configure and sign in with `aws configure sso` and `aws sso login --profile your-profile`.

## 1. Choose account values

Run from this repository. Choose your own region and profile; the repository names below are suggestions.

```sh
export AWS_PROFILE='your-profile'
export AWS_REGION='your-region'
export AWS_DEFAULT_REGION="$AWS_REGION"
export CODEARTIFACT_DOMAIN='session-manager'
export CODEARTIFACT_REPOSITORY='java-releases'
export ECR_REPOSITORY='divided-session-service'
export AWS_ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
```

Check the account and region before continuing. The following commands create AWS resources; they are setup instructions, not part of local development. Existing resources can be reused. See [CodeArtifact](https://aws.amazon.com/codeartifact/pricing/) and [ECR](https://aws.amazon.com/ecr/pricing/) pricing for your account and region.

## 2. Create the artifact repositories

```sh
aws codeartifact create-domain --domain "$CODEARTIFACT_DOMAIN"
aws codeartifact create-repository \
  --domain "$CODEARTIFACT_DOMAIN" --repository "$CODEARTIFACT_REPOSITORY"
aws ecr create-repository --repository-name "$ECR_REPOSITORY" \
  --image-tag-mutability IMMUTABLE \
  --image-scanning-configuration scanOnPush=true
```

Maven Central remains the source of third-party dependencies; this CodeArtifact repository needs no upstream to host our core. Immutable ECR tags prevent replacing a released image. Choose a new core/service version for a later release.

## 3. Configure GitHub's OIDC provider and two roles

If your account already has the `token.actions.githubusercontent.com` provider with audience `sts.amazonaws.com`, reuse it. Otherwise:

```sh
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com
```

Render the supplied policy templates into temporary files. They grant the core role access to its Maven package, and the service role read access to CodeArtifact plus push access to its ECR repository. Neither role can create infrastructure or delete artifacts.

```sh
export POLICY_DIRECTORY="$(mktemp -d)"
python3 - <<'PY'
import json
import os
from pathlib import Path
from string import Template

for source in Path('infrastructure/aws').glob('*.json.template'):
    rendered = Template(source.read_text()).substitute(os.environ)
    json.loads(rendered)
    (Path(os.environ['POLICY_DIRECTORY']) / source.name.removesuffix('.template')).write_text(rendered)
PY
aws iam create-role --role-name session-manager-core-publisher \
  --assume-role-policy-document "file://$POLICY_DIRECTORY/trust-core.json"
aws iam put-role-policy --role-name session-manager-core-publisher \
  --policy-name publish-core \
  --policy-document "file://$POLICY_DIRECTORY/publish-core.json"
aws iam create-role --role-name divided-session-service-publisher \
  --assume-role-policy-document "file://$POLICY_DIRECTORY/trust-service.json"
aws iam put-role-policy --role-name divided-session-service-publisher \
  --policy-name publish-service \
  --policy-document "file://$POLICY_DIRECTORY/publish-service.json"
```

Trust is restricted to each named repository's `master` branch and the STS audience. If you change repository names or the default branch, update both the templates and workflows. These workflows do not use a GitHub deployment environment; adding one changes the OIDC subject and requires a matching trust policy. [AWS's GitHub OIDC guidance](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_create_for-idp_oidc.html) explains this trust relationship.

## 4. Configure repository variables

Sign in with `gh auth login` if needed, then set these non-secret variables:

```sh
for repository in auricless/session-manager-core auricless/divided-session-service; do
  gh variable set AWS_REGION --repo "$repository" --body "$AWS_REGION"
  gh variable set CODEARTIFACT_DOMAIN --repo "$repository" --body "$CODEARTIFACT_DOMAIN"
  gh variable set CODEARTIFACT_DOMAIN_OWNER --repo "$repository" --body "$AWS_ACCOUNT_ID"
  gh variable set CODEARTIFACT_REPOSITORY --repo "$repository" --body "$CODEARTIFACT_REPOSITORY"
done
gh variable set AWS_ROLE_ARN --repo auricless/session-manager-core \
  --body "arn:aws:iam::$AWS_ACCOUNT_ID:role/session-manager-core-publisher"
gh variable set AWS_ROLE_ARN --repo auricless/divided-session-service \
  --body "arn:aws:iam::$AWS_ACCOUNT_ID:role/divided-session-service-publisher"
gh variable set ECR_REPOSITORY --repo auricless/divided-session-service --body "$ECR_REPOSITORY"
```

No long-lived AWS access-key secret is needed. GitHub Actions requests short-lived credentials using OpenID Connect. Enable the Codex GitHub connector for both replacement repositories so their implementation PRs can receive native review. Preserve branch protection and the required checks/review workflow when moving from the monorepo.

## 5. Publish and verify in order

After each repository's current-head CI and native review pass, merge its implementation. Publish the core first using its manual release workflow, then follow the [service release guide](aws-release.md). Do not trigger a workflow until the repository variables are configured.

For a local CodeArtifact build, obtain an endpoint and short-lived token without writing credentials into a settings file:

```sh
export CODEARTIFACT_REPOSITORY_URL="$(aws codeartifact get-repository-endpoint \
  --domain "$CODEARTIFACT_DOMAIN" --domain-owner "$AWS_ACCOUNT_ID" \
  --repository "$CODEARTIFACT_REPOSITORY" --format maven \
  --query repositoryEndpoint --output text)"
export CODEARTIFACT_AUTH_TOKEN="$(aws codeartifact get-authorization-token \
  --domain "$CODEARTIFACT_DOMAIN" --domain-owner "$AWS_ACCOUNT_ID" \
  --duration-seconds 900 --query authorizationToken --output text)"
MAVEN_SETTINGS=.mvn/codeartifact-settings.xml scripts/build-image.sh
unset CODEARTIFACT_AUTH_TOKEN
```

The core release workflow resolves and executes the published library through its independent Maven consumer check after deployment. The service release workflow uses a fresh Maven cache, builds with that published core, and verifies the resulting image through Chromium and HTTP against PostgreSQL. Its CI bootstrap pins the core commit in `verify.yml`; update that pin only to a reviewed core revision. These workflow checks must pass before treating registry distribution as complete.

After a successful ECR release, authenticate and start the pulled image:

```sh
export ECR_REGISTRY="$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"
aws ecr get-login-password | docker login --username AWS --password-stdin "$ECR_REGISTRY"
export APP_IMAGE="$ECR_REGISTRY/$ECR_REPOSITORY:1.0.0"
docker pull "$APP_IMAGE"
export POSTGRES_PASSWORD='choose-your-own-database-password'
docker compose up --detach --wait
```

Confirm event creation, registration, generation, live display, print/CSV and persistence after recreation. These AWS checks are pending until they are run successfully.

Only after both registry releases and replacement repositories are verified should `divided-session-manager` receive final replacement links and be archived. Keep its Git history; do not delete it.
