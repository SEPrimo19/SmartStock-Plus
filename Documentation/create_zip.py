#!/usr/bin/env python3
"""Create a clean zip of SmartStock+ source code, excluding build artifacts and caches."""

import os
import zipfile
import sys

PROJECT_DIR = r"C:\Users\Admin\Downloads\SmartStock+\SmartStock"
OUTPUT_ZIP = r"C:\Users\Admin\Downloads\SmartStock+\SmartStock_MCO1_SourceCode.zip"

# Folders and files to exclude
EXCLUDE_DIRS = {
    '.gradle-user-home',
    '.gradle',
    '.idea',
    '.kotlin',
    '.claude',
    'build',
    '.git',
}

# Specific paths under app/ to exclude
EXCLUDE_PATHS = {
    os.path.join('app', 'build'),
}

EXCLUDE_FILES = {
    'local.properties',
    'create_zip.py',
}

EXCLUDE_EXTENSIONS = {
    '.apk', '.aab', '.hprof', '.dex', '.class',
}


def should_exclude(rel_path):
    parts = rel_path.split(os.sep)

    # Check if any directory component matches exclude list
    for part in parts[:-1]:  # check directory parts
        if part in EXCLUDE_DIRS:
            return True

    # Check specific paths
    for exc_path in EXCLUDE_PATHS:
        if rel_path.startswith(exc_path + os.sep) or rel_path == exc_path:
            return True

    # Check file name
    filename = parts[-1]
    if filename in EXCLUDE_FILES:
        return True

    # Check extension
    _, ext = os.path.splitext(filename)
    if ext.lower() in EXCLUDE_EXTENSIONS:
        return True

    return False


def main():
    file_count = 0
    total_size = 0

    with zipfile.ZipFile(OUTPUT_ZIP, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        for root, dirs, files in os.walk(PROJECT_DIR):
            # Skip excluded directories in-place (prevents os.walk from descending)
            dirs[:] = [d for d in dirs if d not in EXCLUDE_DIRS]

            for filename in files:
                filepath = os.path.join(root, filename)
                rel_path = os.path.relpath(filepath, PROJECT_DIR)

                if should_exclude(rel_path):
                    continue

                # Add to zip with relative path inside a SmartStock/ folder
                arcname = os.path.join('SmartStock', rel_path)
                zf.write(filepath, arcname)
                file_size = os.path.getsize(filepath)
                total_size += file_size
                file_count += 1

    zip_size = os.path.getsize(OUTPUT_ZIP)
    print(f"Done!")
    print(f"Files included: {file_count}")
    print(f"Original size:  {total_size / (1024*1024):.1f} MB")
    print(f"Zip size:       {zip_size / (1024*1024):.1f} MB")
    print(f"Saved to:       {OUTPUT_ZIP}")


if __name__ == '__main__':
    main()
