#!/usr/bin/env python3
"""Offline regression tests for the ECR release boundary."""

import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("publish-ecr.py")
CONFIG = "sha256:" + "a" * 64
OTHER_CONFIG = "sha256:" + "b" * 64


def manifest(config=CONFIG):
    return json.dumps({
        "schemaVersion": 2,
        "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
        "config": {"mediaType": "application/vnd.docker.container.image.v1+json", "digest": config, "size": 1},
        "layers": [],
    }, separators=(",", ":"))


FAKE_CLI = r'''#!/usr/bin/env python3
import json, os, pathlib, sys
path = pathlib.Path(os.environ["FAKE_STATE"])
state = json.loads(path.read_text())
kind = pathlib.Path(sys.argv[0]).name
args = sys.argv[1:]
state["calls"].append([kind] + args)
def finish(value="", code=0):
    path.write_text(json.dumps(state))
    print(value)
    sys.exit(code)
if kind == "docker":
    if args[:2] == ["image", "inspect"]:
        finish(json.dumps([{"Id": state["local_config"]}]))
    if args[0] == "login":
        if sys.stdin.read() != "token\n":
            finish("bad login token", 2)
        finish("Login Succeeded")
    if args[0] == "tag":
        finish()
    if args[0] == "push":
        tag = args[1].rsplit(":", 1)[1]
        state["images"][tag] = state["local_manifest"]
        finish("pushed")
    finish("unexpected docker command", 2)
if args[:2] == ["sts", "get-caller-identity"]:
    finish(json.dumps({"Account": "123456789012"}))
if args[:2] == ["ecr", "describe-repositories"]:
    finish(json.dumps({"repositories": [{"imageTagMutability": "IMMUTABLE"}]}))
if args[:2] == ["ecr", "get-login-password"]:
    finish("token")
if args[:2] == ["ecr", "batch-get-image"]:
    tag = next(x.split("=", 1)[1] for x in args if x.startswith("imageTag="))
    if state.get("auth_error"):
        finish("AccessDeniedException", 3)
    if tag not in state["images"]:
        finish(json.dumps({"images": [], "failures": [{"imageId": {"imageTag": tag}, "failureCode": "ImageNotFound", "failureReason": "Image not found"}]}))
    image = state["images"][tag]
    finish(json.dumps({"images": [{"imageId": {"imageTag": tag, "imageDigest": "sha256:" + "c" * 64}, "imageManifest": image}], "failures": []}))
if args[:2] == ["ecr", "put-image"]:
    tag = args[args.index("--image-tag") + 1]
    if state.get("race_put_once"):
        state["race_put_once"] = False
        state["images"][tag] = args[args.index("--image-manifest") + 1]
        finish("ImageAlreadyExistsException", 4)
    if state.get("fail_put_once"):
        state["fail_put_once"] = False
        finish("ServiceUnavailableException", 4)
    if tag in state["images"]:
        finish("ImageTagAlreadyExistsException", 4)
    state["images"][tag] = args[args.index("--image-manifest") + 1]
    finish(json.dumps({"image": {"imageId": {"imageTag": tag}}}))
finish("unexpected aws command: " + " ".join(args), 2)
'''


class PublishEcrTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        for name in ("aws", "docker"):
            executable = self.directory / name
            executable.write_text(FAKE_CLI)
            executable.chmod(0o755)
        self.state_path = self.directory / "state.json"
        self.state = {"calls": [], "images": {}, "local_config": CONFIG, "local_manifest": manifest()}
        self.save()

    def save(self):
        self.state_path.write_text(json.dumps(self.state))

    def run_publish(self):
        env = os.environ.copy()
        env.update({"PATH": str(self.directory) + os.pathsep + env.get("PATH", ""), "FAKE_STATE": str(self.state_path), "AWS_REGION": "us-east-1"})
        result = subprocess.run(["python3", str(SCRIPT), "--repository", "service", "--version", "1.2.3", "--commit", "a" * 40, "--image", "service:local"], env=env, text=True, capture_output=True)
        self.state = json.loads(self.state_path.read_text())
        return result

    def test_partial_release_resumes_without_repush(self):
        self.state["fail_put_once"] = True
        self.save()
        first = self.run_publish()
        self.assertNotEqual(first.returncode, 0)
        self.assertEqual(set(self.state["images"]), {"sha-aaaaaaaaaaaa"})
        second = self.run_publish()
        self.assertEqual(second.returncode, 0, second.stderr)
        self.assertEqual(set(self.state["images"]), {"sha-aaaaaaaaaaaa", "1.2.3"})
        self.assertEqual(self.state["images"]["1.2.3"], self.state["images"]["sha-aaaaaaaaaaaa"])
        self.assertEqual(sum(call[:2] == ["docker", "push"] for call in self.state["calls"]), 1)

    def test_existing_matching_tags_are_accepted_without_push(self):
        self.state["images"] = {"sha-aaaaaaaaaaaa": manifest(), "1.2.3": manifest()}
        self.save()
        result = self.run_publish()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(any(call[:2] == ["docker", "push"] for call in self.state["calls"]))

    def test_conflicting_tag_fails(self):
        self.state["images"] = {"sha-aaaaaaaaaaaa": manifest(OTHER_CONFIG)}
        self.save()
        result = self.run_publish()
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(any(call[:2] == ["docker", "push"] for call in self.state["calls"]))

    def test_existing_version_can_restore_missing_sha(self):
        self.state["images"] = {"1.2.3": manifest()}
        self.save()
        result = self.run_publish()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(set(self.state["images"]), {"sha-aaaaaaaaaaaa", "1.2.3"})
        self.assertFalse(any(call[:2] == ["docker", "push"] for call in self.state["calls"]))

    def test_lookup_auth_error_is_not_treated_as_missing(self):
        self.state["auth_error"] = True
        self.save()
        result = self.run_publish()
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(any(call[:2] == ["docker", "push"] for call in self.state["calls"]))

    def test_concurrent_alias_writer_with_same_manifest_is_accepted(self):
        self.state["images"] = {"sha-aaaaaaaaaaaa": manifest()}
        self.state["race_put_once"] = True
        self.save()
        result = self.run_publish()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.state["images"]["1.2.3"], manifest())

    def test_existing_version_for_other_image_fails_without_writes(self):
        self.state["images"] = {"1.2.3": manifest(OTHER_CONFIG)}
        self.save()
        result = self.run_publish()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(set(self.state["images"]), {"1.2.3"})
        self.assertFalse(any(call[:2] == ["ecr", "put-image"] for call in self.state["calls"]))


if __name__ == "__main__":
    unittest.main()
