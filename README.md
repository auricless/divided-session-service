# Divided Session Service

The target distribution is one Spring Boot application serving the API and built React UI. It depends on the separately versioned `org.c3cavite:session-manager-core:1.0.0` artifact. PostgreSQL runs in its own container. The application image is `divided-session-service` and contains only the packaged application and a Java runtime.

**Draft status:** React files are packaged in the JAR, but the explicit browser routes and packaged HTTP acceptance check are still pending. The full application is not yet verified for release.

## Local build and run

Install Java 21 for the build, Maven, Node 24, npm, and Docker with Compose. The Maven compiler emits Java 17 bytecode for the Java 17 runtime image. Make the core artifact available in local Maven (`~/.m2/repository`) or configure Maven for CodeArtifact as described in [AWS setup](docs/aws-setup.md). No AWS credentials are needed to run a built image.

```sh
scripts/build-image.sh
export POSTGRES_PASSWORD='choose-a-local-password'
docker compose up --detach --wait
curl --fail http://127.0.0.1:8080/actuator/health/readiness
```

`scripts/build-image.sh` runs `npm ci --ignore-scripts`, builds React, runs Maven `clean verify -Pweb-app`, checks `target/app.jar`, then builds the image. `APP_PORT` changes the loopback host port. `APP_IMAGE` selects a prebuilt image, including a fully qualified ECR tag. `docker compose down` retains the PostgreSQL named volume; `docker compose down --volumes` removes it.

If Maven needs an explicit settings file for CodeArtifact, set `MAVEN_SETTINGS=.mvn/codeartifact-settings.xml` when invoking the build script, with the short-lived token and endpoint environment variables described in [AWS setup](docs/aws-setup.md).

The Maven `web-app` profile requires `frontend/dist/index.html` and copies the production files into the JAR. Without the profile, Maven verifies the backend alone and the existing API-only routing tests apply. The planned React routes are `/`, `/events`, `/events/{id}`, and `/events/{id}/groups`. API routes remain under `/api/v1`.

## Checks

```sh
mvn clean verify -Pquality
npm --prefix frontend ci --ignore-scripts
npm --prefix frontend run check
npm --prefix frontend test
```

The CI workflow is prepared to build the full image, run the stack with PostgreSQL, and execute Chromium acceptance tests. Its full-stack acceptance script is pending with the browser-route work above, so CI cannot pass yet. It checks out the public core repository at the immutable commit pinned in `.github/workflows/verify.yml` and installs it into the runner's local Maven cache. Update that SHA when a reviewed core revision is published.

The complete application image has a 300 MB uncompressed layer budget. Check it with `docker image history --human=false --format '{{.Size}}' divided-session-service:local` and sum the byte values. Docker Desktop's containerd `image inspect .Size` can also count stored compressed blobs, so that storage number is larger than the image's uncompressed layers.

## Release

The manual release workflow builds from the default branch, obtains a short-lived CodeArtifact token via GitHub OIDC, verifies the packaged application, then pushes semantic-version and commit-SHA tags to ECR. It requires the setup in [AWS setup](docs/aws-setup.md). It never stores AWS credentials in the runtime image.

## License

No license has been selected for this project yet.
