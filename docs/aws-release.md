# Manual image release

The `Publish application image` workflow runs only on the repository's `master` default branch. Configure repository variables `AWS_ROLE_ARN`, `AWS_REGION`, `CODEARTIFACT_DOMAIN`, `CODEARTIFACT_DOMAIN_OWNER`, and `CODEARTIFACT_REPOSITORY`. `ECR_REPOSITORY` is optional and defaults to `divided-session-service`. The AWS IAM role must trust this repository's master branch through GitHub OIDC and be able to read the core artifact from CodeArtifact and push to the ECR repository.

Before dispatching the workflow, publish the immutable `session-manager-core:1.0.0` artifact to CodeArtifact and verify the service's default-branch CI. The release uses CodeArtifact even if a core artifact happens to be cached on the runner; it never checks out core source. It runs the frontend and backend checks, builds `target/app.jar`, and pushes ECR tags matching the POM's semantic version and `sha-<commit-prefix>`. An existing tag causes the workflow to fail. No `latest` tag is produced.

To deploy, pull a specific ECR tag, set `APP_IMAGE` to its full image URI, and provide `POSTGRES_PASSWORD` for the Compose database (or `DATABASE_URL`, `DATABASE_USER`, and `DATABASE_PASSWORD` when running the image with another PostgreSQL instance). The image does not need AWS credentials to start or serve requests. See [AWS setup](aws-setup.md) for resource and trust configuration.
