# Divided Session Service

Organize attendees into small groups, assign facilitators, and display, print or export the roster. The organizer sets age ranges, grouping options, a target size and a required maximum group size. A roster is final when every group meets the rules and has a leader; otherwise the app shows a proposal with the issues to resolve.

## How it works

One Spring Boot application serves the React UI and REST API. It calls the separately versioned [Java core library](https://github.com/auricless/session-manager-core) and stores events, attendees and current assignments in PostgreSQL. The application image contains the compiled UI, API and core library; PostgreSQL runs separately.

The stack uses Java 17, Spring Boot 4, React 19, Vite, PostgreSQL 17 and Flyway. Maven declares `org.c3cavite:session-manager-core:1.0.0` as a dependency; no core source is copied into this repository.

## Build and run locally

Install Git, JDK 21, Maven 3.9+, Node 24 and Docker with Compose. The build compiles for the Java 17 runtime. Until CodeArtifact is configured, install the standalone core into your local Maven repository:

```sh
git clone https://github.com/auricless/session-manager-core.git
git clone https://github.com/auricless/divided-session-service.git
cd divided-session-service
mvn -f ../session-manager-core/pom.xml install
scripts/build-image.sh
export POSTGRES_PASSWORD='choose-your-own-database-password'
docker compose up --detach --wait
```

Open [localhost:8080/events](http://localhost:8080/events). Only the application port is published, on localhost. `APP_PORT` changes that port; `APP_IMAGE` selects a prebuilt image instead of `divided-session-service:local`.

The build script compiles React, runs Maven verification with the `web-app` profile, packages the production assets in the JAR and builds the image. Its runtime has no Node, Maven or JDK. A CodeArtifact build uses `MAVEN_SETTINGS=.mvn/codeartifact-settings.xml` and the token/endpoint variables described in [AWS setup](docs/aws-setup.md).

## Use the app

1. Create an event and set its maximum group size. Adjust the target size, age ranges and grouping options as needed.
2. Register attendees, then enter the available facilitators and their qualifications.
3. Generate groups. Resolve proposal blockers and generate again until the roster is ready. Facilitator exceptions apply to one computation and never increase the maximum group size.
4. Open the live group display, print the roster or download CSV. Changes to attendees or configuration invalidate the previous assignment.

Facilitator entries belong to the current screen; enter them again after navigating away. This local deployment has no sign-in system or public TLS configuration.

## Operate the stack

```sh
docker compose ps
docker compose logs --follow app
curl --fail http://127.0.0.1:8080/actuator/health/readiness
docker compose down
```

Readiness includes database availability. Normal shutdown and container recreation retain the named PostgreSQL volume. Keep the same database password when reusing that volume. `docker compose down --volumes` explicitly deletes the saved data.

## Development and verification

```sh
mvn clean verify -Pquality
npm --prefix frontend ci --ignore-scripts
npm --prefix frontend run check
npm --prefix frontend test
```

For the running packaged image, `npm --prefix frontend run test:e2e` exercises the registration, display, print and CSV workflow in Chromium. `python3 scripts/smoke-container-stack.py --project your-disposable-project` checks routes, health, runtime isolation, API behavior and persistence by recreating that test stack. Install Chromium with `node frontend/node_modules/@playwright/test/cli.js install chromium` first. Use a disposable Compose project for the smoke check; it stops and recreates its containers.

CI builds the real image and runs both checks against PostgreSQL. Until CodeArtifact is available, CI installs the core from the immutable reviewed commit pinned in `verify.yml`. The complete image has a 300 MB uncompressed layer budget; Docker Desktop's containerd storage total also counts compressed blobs.

## Releases

The manual release workflow resolves the core from CodeArtifact using a fresh Maven cache, builds and tests the application image, then publishes version and commit tags to ECR. Running a pulled image does not require AWS credentials. See [one-time AWS setup](docs/aws-setup.md) and [release instructions](docs/aws-release.md). AWS account setup and the first registry releases are still pending.

## License

No license has been selected for this project yet.
