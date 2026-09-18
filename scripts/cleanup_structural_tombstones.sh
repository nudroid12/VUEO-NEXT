#!/usr/bin/env bash
set -euo pipefail

roots=(
  "mobile/src/main/java"
  "tv/src/main/java"
)

mapfile -t tombstones < <(
  grep -RIl \
    --include='*.kt' \
    '^// Structural tombstone: implementation moved to$' \
    "${roots[@]}" 2>/dev/null | sort || true
)

if [[ ${#tombstones[@]} -eq 0 ]]; then
  echo "No structural tombstones found."
  exit 0
fi

echo "Found ${#tombstones[@]} structural tombstone(s). Verifying targets..."

declare -a verified=()

for old_path in "${tombstones[@]}"; do
  first_line="$(sed -n '1p' "$old_path")"
  second_line="$(sed -n '2p' "$old_path")"
  third_line="$(sed -n '3p' "$old_path")"

  if [[ "$first_line" != "// Structural tombstone: implementation moved to" ]]; then
    echo "ERROR: unexpected tombstone format: $old_path"
    exit 1
  fi

  if [[ "$third_line" != "// Kept inert because replacement ZIP overlays cannot delete tracked paths." ]]; then
    echo "ERROR: safety marker missing: $old_path"
    exit 1
  fi

  new_path="${second_line#// }"

  if [[ "$new_path" == "$second_line" || -z "$new_path" ]]; then
    echo "ERROR: could not parse target path from: $old_path"
    exit 1
  fi

  if [[ ! -f "$new_path" ]]; then
    echo "ERROR: replacement file does not exist:"
    echo "  old: $old_path"
    echo "  new: $new_path"
    exit 1
  fi

  verified+=("$old_path")
  echo "OK: $old_path -> $new_path"
done

echo
echo "All targets verified. Removing only verified tombstones..."

for old_path in "${verified[@]}"; do
  git rm -- "$old_path"
done

echo
echo "Removed ${#verified[@]} structural tombstone(s)."
