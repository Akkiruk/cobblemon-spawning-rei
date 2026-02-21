import os

filepath = os.path.join(os.path.dirname(os.path.abspath(__file__)), "CHANGELOG.md")

with open(filepath, "r", encoding="utf-8") as f:
    lines = f.readlines()

# Line numbers are 1-indexed in the read output, 0-indexed here
# Need to:
# 1. Add blank line between line 734 and 735 (index 733 and 734)
# 2. Remove duplicate block lines 746-756 (but indices will shift after insert)

# First, insert blank line after index 733 (after "- Affects both JEI...")
# The line at index 733 is "- Affects both JEI ingredient lookups and REI entry identifiers\n"
# The line at index 734 is "## [1.10.3] - 2026-02-11\n"

# Verify
assert "Affects both JEI" in lines[733], f"Line 734 unexpected: {lines[733]}"
assert "## [1.10.3]" in lines[734], f"Line 735 unexpected: {lines[734]}"

# Insert blank line
lines.insert(734, "\n")

# Now indices shifted by 1. The duplicate [1.10.3] was at line 746 (index 745),
# now it's at index 746. Let's find it.
# Find the second occurrence of "## [1.10.3]"
first_found = False
dup_start = None
dup_end = None
for i, line in enumerate(lines):
    if "## [1.10.3]" in line:
        if first_found:
            dup_start = i
            break
        first_found = True

# Find where the duplicate block ends (just before ## [1.10.1])
for i in range(dup_start, len(lines)):
    if "## [1.10.1]" in lines[i]:
        dup_end = i
        break

assert dup_start is not None, "Could not find duplicate [1.10.3]"
assert dup_end is not None, "Could not find [1.10.1] after duplicate"

print(f"Removing duplicate block from line {dup_start+1} to {dup_end} (before [1.10.1])")
print(f"Lines being removed:")
for i in range(dup_start, dup_end):
    print(f"  {i+1}: {lines[i].rstrip()}")

# Remove the duplicate block
del lines[dup_start:dup_end]

with open(filepath, "w", encoding="utf-8") as f:
    f.writelines(lines)

print(f"\nDone! File now has {len(lines)} lines.")
