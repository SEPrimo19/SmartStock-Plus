#!/usr/bin/env python3
"""Assemble the SmartStock+ MCO 2 final submission package.

Produces, under  C:\\Users\\Admin\\Downloads\\SmartStock+\\SmartStock_MCO2_Package\\ :
  - SmartStock_MCO2_SourceCode.zip   (clean source, no build/caches/keys)
  - SmartStock+_MCO2.apk             (installable debug build)
  - SmartStock+_MCO2_Presentation.pptx
  - SmartStock+_MCO2_Presentation_Script.pdf
  - README.txt                       (build / install / setup instructions)

Then zips the whole folder into  SmartStock_MCO2_Package.zip  for upload.
The source zip mirrors the midterm exclusion rules so it stays small (~3 MB).
"""

import os
import shutil
import zipfile

ROOT = r"C:\Users\Admin\Downloads\SmartStock+"
PROJECT_DIR = os.path.join(ROOT, "SmartStock")
PKG_DIR = os.path.join(ROOT, "SmartStock_MCO2_Package")
SRC_ZIP = os.path.join(PKG_DIR, "SmartStock_MCO2_SourceCode.zip")
PKG_ZIP = os.path.join(ROOT, "SmartStock_MCO2_Package.zip")

# Dirs/files excluded from the SOURCE zip (build artifacts, caches, secrets).
EXCLUDE_DIRS = {
    ".gradle-user-home", ".gradle", ".gradle-sandbox", ".android-home",
    ".idea", ".kotlin", ".vscode", ".claude", "build", ".git",
    "_mco2_extracted", "_assets", "Lesssons",
}
EXCLUDE_PATHS = {os.path.join("app", "build")}
EXCLUDE_FILES = {
    "local.properties",          # contains SDK path + Supabase keys
    "build_mco2_package.py",
    "create_zip.py",
    # large legacy presentation binaries are not part of the MCO2 source
    "SmartStock_MCO1_Presentation_v2.pptx",
    "SmartStock_MCO1_Presentation.pptx",
    "SmartStock_MCO1_Presentation_Scripts.docx",
}
EXCLUDE_EXTENSIONS = {".apk", ".aab", ".hprof", ".dex", ".class", ".iml"}


def should_exclude(rel_path: str) -> bool:
    parts = rel_path.split(os.sep)
    for part in parts[:-1]:
        if part in EXCLUDE_DIRS:
            return True
    for exc in EXCLUDE_PATHS:
        if rel_path == exc or rel_path.startswith(exc + os.sep):
            return True
    name = parts[-1]
    if name in EXCLUDE_FILES:
        return True
    return os.path.splitext(name)[1].lower() in EXCLUDE_EXTENSIONS


def build_source_zip() -> tuple[int, float]:
    files, total = 0, 0
    with zipfile.ZipFile(SRC_ZIP, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        for root, dirs, names in os.walk(PROJECT_DIR):
            dirs[:] = [d for d in dirs if d not in EXCLUDE_DIRS]
            for n in names:
                fp = os.path.join(root, n)
                rel = os.path.relpath(fp, PROJECT_DIR)
                if should_exclude(rel):
                    continue
                zf.write(fp, os.path.join("SmartStock", rel))
                total += os.path.getsize(fp)
                files += 1
    return files, total / (1024 * 1024)


def find_apk() -> str | None:
    out = os.path.join(PROJECT_DIR, "app", "build", "outputs", "apk")
    if not os.path.isdir(out):
        return None
    cands = []
    for r, _, fs in os.walk(out):
        for f in fs:
            if f.endswith(".apk"):
                cands.append(os.path.join(r, f))
    if not cands:
        return None
    # prefer a release apk if present, else debug
    cands.sort(key=lambda p: (0 if "release" in p else 1, p))
    return cands[0]


README = """SmartStock+ — MCO 2 Final Submission
=====================================

Team 2 · Mobile Programming 2 · Instructor: Mr. Aaron Jude Pael

CONTENTS
--------
  SmartStock_MCO2_SourceCode.zip         Full Android Studio project (clean)
  SmartStock+_MCO2.apk                   Installable app (Android 8.0+ / API 26+)
  SmartStock+_MCO2_Presentation.pptx     Final presentation deck
  SmartStock+_MCO2_Presentation_Script.pdf  Per-slide speaker script + roles
  README.txt                             This file

INSTALL THE APP
---------------
  1. Copy SmartStock+_MCO2.apk to an Android device (API 26 / Android 8.0+).
  2. Enable "Install unknown apps" for your file manager, then tap the APK.
  3. The app is offline-first: it works with no connection and syncs to the
     cloud automatically when online.

BUILD FROM SOURCE
-----------------
  1. Unzip SmartStock_MCO2_SourceCode.zip and open the SmartStock folder in
     Android Studio (Giraffe or newer).
  2. Create  local.properties  in the project root with:

        sdk.dir=C:\\\\Users\\\\<you>\\\\AppData\\\\Local\\\\Android\\\\Sdk
        supabaseUrl=YOUR_SUPABASE_PROJECT_URL
        supabaseAnonKey=YOUR_SUPABASE_ANON_KEY

     (local.properties is intentionally excluded — it holds machine-specific
     paths and project secrets.)
  3. In Supabase, run  supabase/01_schema.sql  in the SQL Editor to create
     the schema, Row-Level Security policies, and the item-images Storage
     bucket.
  4. Build & run:  gradlew.bat assembleDebug   (or Run from Android Studio).

ARCHITECTURE (short)
--------------------
  Jetpack Compose UI -> ViewModel (StateFlow) -> Repository -> Room (local)
  and Supabase (cloud, PostgREST + GoTrue + Storage). Background sync via
  WorkManager, last-write-wins on updatedAt, natural-key reconciliation,
  multi-tenant isolation enforced by PostgreSQL Row-Level Security.
"""


def main():
    if os.path.isdir(PKG_DIR):
        shutil.rmtree(PKG_DIR)
    os.makedirs(PKG_DIR)

    n, mb = build_source_zip()
    print(f"source zip : {n} files, {mb:.1f} MB -> {os.path.basename(SRC_ZIP)}")

    apk = find_apk()
    if apk:
        dst = os.path.join(PKG_DIR, "SmartStock+_MCO2.apk")
        shutil.copy2(apk, dst)
        print(f"apk        : {os.path.getsize(dst)/(1024*1024):.1f} MB ({os.path.basename(apk)})")
    else:
        print("apk        : NOT FOUND (build it first: gradlew.bat assembleDebug)")

    for src, label in [
        (os.path.join(PROJECT_DIR, "SmartStock+_MCO2_Presentation.pptx"),
         "SmartStock+_MCO2_Presentation.pptx"),
        (os.path.join(PROJECT_DIR, "SmartStock+_MCO2_Presentation_Script.pdf"),
         "SmartStock+_MCO2_Presentation_Script.pdf"),
    ]:
        if os.path.exists(src):
            shutil.copy2(src, os.path.join(PKG_DIR, label))
            print(f"copied     : {label}")
        else:
            print(f"MISSING    : {label}")

    with open(os.path.join(PKG_DIR, "README.txt"), "w", encoding="utf-8") as f:
        f.write(README)
    print("wrote      : README.txt")

    if os.path.exists(PKG_ZIP):
        os.remove(PKG_ZIP)
    with zipfile.ZipFile(PKG_ZIP, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        for r, _, fs in os.walk(PKG_DIR):
            for f in fs:
                fp = os.path.join(r, f)
                zf.write(fp, os.path.join("SmartStock_MCO2_Package",
                                          os.path.relpath(fp, PKG_DIR)))
    print(f"\nFINAL      : {PKG_ZIP}  "
          f"({os.path.getsize(PKG_ZIP)/(1024*1024):.1f} MB)")


if __name__ == "__main__":
    main()
