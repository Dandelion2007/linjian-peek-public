#!/bin/bash
set -eu
PROJECT="$(cd "$(dirname "$0")/.." && pwd)"
TEST_OUT=$(mktemp -d)
trap 'rm -rf "$TEST_OUT"' EXIT
java com.sun.tools.javac.Main -encoding UTF-8 -d "$TEST_OUT" \
    "$PROJECT/app/src/main/java/dev/linjian/peek/GatePolicy.java" \
    "$PROJECT/tests/GatePolicyTest.java"
java -cp "$TEST_OUT" dev.linjian.peek.GatePolicyTest
