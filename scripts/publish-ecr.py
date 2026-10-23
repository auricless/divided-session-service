#!/usr/bin/env python3
"""Publish two immutable ECR tags for one verified local Docker image.

The SHA tag is canonical. A retry reads its manifest from ECR and adds only
the missing version alias, so a transient alias failure does not strand a
release in an immutable repository.
"""

import argparse
import json
import os
import re
import subprocess
import sys


MEDIA_TYPES = (
    "application/vnd.docker.distribution.manifest.v2+json",
    "application/vnd.oci.image.manifest.v1+json",
)


class PublishError(Exception):
    pass


def command(args, *, input_text=None):
    result = subprocess.run(args, input=input_text, text=True, capture_output=True)
    if result.returncode:
        detail = result.stderr.strip() or result.stdout.strip()
        raise PublishError(f"{args[0]} {args[1]} failed: {detail}")
    return result.stdout


def aws_json(*args):
    return json.loads(command(["aws", *args, "--output", "json"]))


def get_image(repository, tag):
    response = aws_json(
        "ecr", "batch-get-image", "--repository-name", repository,
        "--image-ids", f"imageTag={tag}", "--accepted-media-types", *MEDIA_TYPES,
    )
    failures = response.get("failures", [])
    if len(failures) == 1 and failures[0].get("failureCode") == "ImageNotFound" and not response.get("images"):
        return None
    if failures or len(response.get("images", [])) != 1:
        raise PublishError(f"Could not read ECR tag {tag}: {failures or response}")
    return response["images"][0]["imageManifest"]


def config_digest(image_manifest):
    try:
        manifest = json.loads(image_manifest)
        if manifest["schemaVersion"] != 2 or manifest["mediaType"] not in MEDIA_TYPES:
            raise ValueError("unsupported manifest format")
        digest = manifest["config"]["digest"]
        if not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
            raise ValueError("invalid config digest")
        return digest
    except (KeyError, TypeError, ValueError, json.JSONDecodeError) as error:
        raise PublishError(f"Unsupported ECR image manifest (single-platform Docker v2 or OCI required): {error}") from error


def add_alias(repository, tag, image_manifest):
    media_type = json.loads(image_manifest)["mediaType"]
    try:
        aws_json(
            "ecr", "put-image", "--repository-name", repository,
            "--image-tag", tag, "--image-manifest", image_manifest,
            "--image-manifest-media-type", media_type,
        )
    except PublishError as error:
        # An overlapping retry can win the race in an immutable repository.
        if not any(code in str(error) for code in ("ImageTagAlreadyExistsException", "ImageAlreadyExistsException")) or get_image(repository, tag) != image_manifest:
            raise


def publish(repository, version, commit, image, region):
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version):
        raise PublishError("Version must be a semantic release version (major.minor.patch).")
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise PublishError("Commit must be a full lowercase SHA-1 revision.")
    if not region:
        raise PublishError("AWS_REGION is required.")

    local_images = json.loads(command(["docker", "image", "inspect", image]))
    if len(local_images) != 1 or not re.fullmatch(r"sha256:[0-9a-f]{64}", local_images[0]["Id"]):
        raise PublishError("Could not read the local image config digest.")
    local_config = local_images[0]["Id"]

    identity = aws_json("sts", "get-caller-identity")
    account = identity["Account"]
    if not re.fullmatch(r"[0-9]{12}", account):
        raise PublishError("AWS returned an invalid account number.")
    repositories = aws_json("ecr", "describe-repositories", "--repository-names", repository)["repositories"]
    if len(repositories) != 1 or repositories[0].get("imageTagMutability") != "IMMUTABLE":
        raise PublishError("ECR repository must have immutable image tags.")

    sha_tag = f"sha-{commit[:12]}"
    sha_manifest = get_image(repository, sha_tag)
    version_manifest = get_image(repository, version)
    for tag, existing in ((sha_tag, sha_manifest), (version, version_manifest)):
        if existing and config_digest(existing) != local_config:
            raise PublishError(f"ECR tag {tag} points to a different image config digest.")
    if sha_manifest and version_manifest and sha_manifest != version_manifest:
        raise PublishError("ECR SHA and version tags point to different image manifests.")

    if not sha_manifest and version_manifest:
        add_alias(repository, sha_tag, version_manifest)
        sha_manifest = get_image(repository, sha_tag)
    elif not sha_manifest:
        registry = f"{account}.dkr.ecr.{region}.amazonaws.com"
        remote_image = f"{registry}/{repository}:{sha_tag}"
        password = command(["aws", "ecr", "get-login-password"]).strip()
        if not password:
            raise PublishError("ECR login password is empty.")
        command(["docker", "login", "--username", "AWS", "--password-stdin", registry], input_text=password + "\n")
        command(["docker", "tag", image, remote_image])
        try:
            command(["docker", "push", remote_image])
        except PublishError:
            # The push may have completed remotely before the client failed.
            sha_manifest = get_image(repository, sha_tag)
            if sha_manifest is None or config_digest(sha_manifest) != local_config:
                raise
        if sha_manifest is None:
            sha_manifest = get_image(repository, sha_tag)
        if sha_manifest is None:
            raise PublishError("ECR SHA tag was not readable after push.")
        if config_digest(sha_manifest) != local_config:
            raise PublishError("Pushed ECR image does not match the verified local image.")

    if version_manifest is None:
        add_alias(repository, version, sha_manifest)
    elif version_manifest != sha_manifest:
        raise PublishError("ECR version tag conflicts with the SHA tag.")

    # Confirm both aliases resolve to the same published manifest.
    if get_image(repository, sha_tag) != sha_manifest or get_image(repository, version) != sha_manifest:
        raise PublishError("ECR tags did not resolve to the expected image manifest.")
    print(f"Published {repository}:{sha_tag} and {repository}:{version}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--image", required=True)
    args = parser.parse_args()
    try:
        publish(args.repository, args.version, args.commit, args.image, os.environ.get("AWS_REGION"))
    except (PublishError, KeyError, json.JSONDecodeError, OSError) as error:
        print(f"ECR publish failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
