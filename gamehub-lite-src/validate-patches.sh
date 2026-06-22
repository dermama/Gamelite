#!/bin/bash
#
# GameHub Lite Patch Validator
# Validates patch files for common issues before committing
#
# Usage: ./validate-patches.sh
#
# Optional dependency: pip install unidiff (for syntax validation)
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PATCHES_DIR="$SCRIPT_DIR/patches"

errors=0
warnings=0

print_header() {
    echo ""
    echo "====================================="
    echo "  GameHub Lite Patch Validator"
    echo "====================================="
    echo ""
}

print_check() {
    echo -e "${BLUE}==>${NC} $1"
}

print_ok() {
    echo -e "  ${GREEN}[OK]${NC} $1"
}

print_warn() {
    echo -e "  ${YELLOW}[WARN]${NC} $1"
    warnings=$((warnings + 1))
}

print_err() {
    echo -e "  ${RED}[ERROR]${NC} $1"
    errors=$((errors + 1))
}

# ── Check 1: Patch file encoding ────────────────────────────────────────────

check_encoding() {
    print_check "Checking patch file encodings..."

    local count=0
    while IFS= read -r -d '' patch_file; do
        local encoding
        encoding=$(file -b --mime-encoding "$patch_file")
        if [[ "$encoding" != "us-ascii" && "$encoding" != "utf-8" ]]; then
            print_err "$patch_file is encoded as $encoding (must be UTF-8 or ASCII)"
        fi

        # Check for UTF-8 BOM
        if [ "$(head -c 3 "$patch_file" | xxd -p)" = "efbbbf" ]; then
            print_err "$patch_file contains UTF-8 BOM (byte order mark)"
        fi

        # Check for CRLF line endings
        if grep -qP '\r\n' "$patch_file" 2>/dev/null; then
            print_warn "$patch_file has CRLF line endings (Windows) - may cause patch failures"
        fi

        count=$((count + 1))
    done < <(find "$PATCHES_DIR/diffs" -name '*.patch' -print0)

    if [ $count -eq 0 ]; then
        print_warn "No patch files found"
    else
        print_ok "Checked $count patch files"
    fi
}

# ── Check 2: Trailing newlines on manifest files ────────────────────────────

check_trailing_newlines() {
    print_check "Checking trailing newlines on manifest files..."

    for f in "$PATCHES_DIR"/files_to_patch.txt "$PATCHES_DIR"/files_to_add.txt "$PATCHES_DIR"/files_to_delete.txt; do
        if [ -f "$f" ] && [ -s "$f" ]; then
            if [ "$(tail -c 1 "$f" | wc -l)" -eq 0 ]; then
                print_err "$(basename "$f") is missing a trailing newline - last entry may be silently skipped"
            else
                print_ok "$(basename "$f")"
            fi
        fi
    done
}

# ── Check 3: Orphaned patches ───────────────────────────────────────────────

check_orphaned_patches() {
    print_check "Checking for orphaned patches..."

    local orphaned=0
    local missing=0

    # Patch files not listed in files_to_patch.txt
    while IFS= read -r -d '' patch_file; do
        local rel_path="${patch_file#$PATCHES_DIR/diffs/}"
        local target_file="${rel_path%.patch}"
        if ! grep -qxF "$target_file" "$PATCHES_DIR/files_to_patch.txt"; then
            print_err "Orphaned patch: $rel_path (not listed in files_to_patch.txt)"
            orphaned=$((orphaned + 1))
        fi
    done < <(find "$PATCHES_DIR/diffs" -name '*.patch' -print0)

    # Entries in files_to_patch.txt without a matching patch or binary replacement
    while IFS= read -r target_file; do
        [ -z "$target_file" ] && continue
        local patch_path="$PATCHES_DIR/diffs/${target_file}.patch"
        local binary_path="$PATCHES_DIR/binary_replacements/${target_file}"
        if [ ! -f "$patch_path" ] && [ ! -f "$binary_path" ]; then
            print_err "Missing patch for: $target_file (listed in files_to_patch.txt but no .patch file exists)"
            missing=$((missing + 1))
        fi
    done < "$PATCHES_DIR/files_to_patch.txt"

    if [ $orphaned -eq 0 ] && [ $missing -eq 0 ]; then
        print_ok "All patches are properly tracked"
    fi
}

# ── Check 4: Patch syntax validation (requires python3 + unidiff) ───────────

check_patch_syntax() {
    print_check "Validating patch syntax..."

    if ! python3 -c "import unidiff" 2>/dev/null; then
        print_warn "Skipping syntax validation - install with: pip install unidiff"
        return
    fi

    python3 << 'PYEOF'
import os, sys

patches_dir = os.environ.get('PATCHES_DIR', 'patches')
diffs_dir = os.path.join(patches_dir, 'diffs')

from unidiff import PatchSet
from unidiff.errors import UnidiffParseError

errs = 0
checked = 0

for root, dirs, files in os.walk(diffs_dir):
    for fname in sorted(files):
        if not fname.endswith('.patch'):
            continue
        fpath = os.path.join(root, fname)
        checked += 1
        try:
            patch = PatchSet.from_filename(fpath, encoding='utf-8')
            if len(patch) == 0:
                print(f'  \033[1;33m[WARN]\033[0m {fpath}: empty patch (no hunks)')
        except UnidiffParseError as e:
            print(f'  \033[0;31m[ERROR]\033[0m {fpath}: invalid syntax: {e}')
            errs += 1
        except UnicodeDecodeError as e:
            print(f'  \033[0;31m[ERROR]\033[0m {fpath}: encoding error: {e}')
            errs += 1

if errs == 0:
    print(f'  \033[0;32m[OK]\033[0m All {checked} patches have valid unified diff syntax')
else:
    print(f'  \033[0;31m[ERROR]\033[0m {errs} of {checked} patches have invalid syntax')

sys.exit(errs)
PYEOF
    local exit_code=$?
    errors=$((errors + exit_code))
}

# ── Check 5: lsdiff structural validation ───────────────────────────────────

check_patch_structure() {
    print_check "Validating patch structure..."

    if ! command -v lsdiff &>/dev/null; then
        print_warn "Skipping structural validation - install patchutils (brew install patchutils / apt install patchutils)"
        return
    fi

    local checked=0
    local failed=0
    while IFS= read -r -d '' patch_file; do
        if ! lsdiff "$patch_file" > /dev/null 2>&1; then
            print_err "lsdiff cannot parse: ${patch_file#$PATCHES_DIR/}"
            failed=$((failed + 1))
        fi
        checked=$((checked + 1))
    done < <(find "$PATCHES_DIR/diffs" -name '*.patch' -print0)

    if [ $failed -eq 0 ]; then
        print_ok "All $checked patches pass structural validation"
    fi
}

# ── Main ─────────────────────────────────────────────────────────────────────

main() {
    print_header

    if [ ! -d "$PATCHES_DIR/diffs" ]; then
        echo -e "${RED}No patches directory found at: $PATCHES_DIR/diffs${NC}"
        exit 1
    fi

    export PATCHES_DIR

    check_encoding
    check_trailing_newlines
    check_orphaned_patches
    check_patch_syntax
    check_patch_structure

    echo ""
    echo "====================================="
    if [ $errors -gt 0 ]; then
        echo -e "  ${RED}FAILED: $errors error(s), $warnings warning(s)${NC}"
        echo "====================================="
        exit 1
    elif [ $warnings -gt 0 ]; then
        echo -e "  ${YELLOW}PASSED with $warnings warning(s)${NC}"
        echo "====================================="
        exit 0
    else
        echo -e "  ${GREEN}All checks passed${NC}"
        echo "====================================="
        exit 0
    fi
}

main "$@"
