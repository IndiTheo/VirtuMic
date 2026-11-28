#!/usr/bin/env bash
# Regenerate Bazel and Maven lockfiles after Renovate updated MODULE.bazel.
#

set -euo pipefail
export MISE_CONFIG_FILE="ci/build-mise.toml"
eval "$(mise activate bash --quiet)"

# Repin only the lockfiles whose inputs actually changed.
# The repin check is a signature check evaluated during repository-rule
# fetch, so a plain `bazel build --nobuild` (without REPIN) surfaces the failure
# without compiling any targets.
for repo in android kotlin security framework; do
  if ! bazel build --nobuild --config=ci "@${repo}//..." > "/tmp/repin_${repo}.log" 2>&1; then
    if grep -q "must be regenerated" "/tmp/repin_${repo}.log"; then
      echo "Repinning ${repo}..."
      REPIN=1 bazel run --config=ci "@unpinned_${repo}//:pin"
    else
      echo "ERROR: build of @${repo} failed for a non-repin reason:" >&2
      cat "/tmp/repin_${repo}.log" >&2
      exit 1
    fi
  fi
done

bazel mod deps --lockfile_mode=update
