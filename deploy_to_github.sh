#!/data/data/com.termux/files/usr/bin/bash

set -e

echo "======================================"
echo "       MSA PORTAL DEPLOYMENT"
echo "======================================"

cd "$HOME/MSA_PORTAL"

# Confirm this is the correct Git project
if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    echo "ERROR: MSA PORTAL Git repository not found."
    exit 1
fi

# Confirm the GitHub remote
if ! git remote get-url origin >/dev/null 2>&1; then
    echo "ERROR: GitHub remote 'origin' is missing."
    exit 1
fi

echo
echo "STEP 1: Checking project..."
git remote -v

# Protect database files and local backups
touch .gitignore

for entry in \
    "*.db" \
    "*.db-journal" \
    "*.db-wal" \
    "*.db-shm" \
    ".env" \
    ".env.*" \
    "backups/" \
    "__pycache__/" \
    "*.pyc"
do
    grep -Fxq "$entry" .gitignore || echo "$entry" >> .gitignore
done

echo
echo "STEP 2: Checking for sensitive files already tracked..."

if git ls-files | grep -E '(^|/)([^/]*\.db(-journal|-wal|-shm)?|\.env([^/]*)?)$|(^|/)backups/' ; then
    echo
    echo "STOP: Git already tracks one or more database, backup,"
    echo "or environment files."
    echo "No commit or push was performed."
    echo "These files must be handled safely before deployment."
    exit 1
fi

echo
echo "STEP 3: Preparing application files..."

# Stage project changes, excluding databases and private backups
git add -A -- . \
    ':(exclude,glob)**/*.db' \
    ':(exclude,glob)**/*.db-journal' \
    ':(exclude,glob)**/*.db-wal' \
    ':(exclude,glob)**/*.db-shm' \
    ':(exclude,glob)**/.env' \
    ':(exclude,glob)**/.env.*' \
    ':(exclude,glob)**/backups/**' \
    ':(exclude,glob)**/__pycache__/**' \
    ':(exclude,glob)**/*.pyc'

# Final safety check on staged files
if git diff --cached --name-only | grep -E '(^|/)([^/]*\.db(-journal|-wal|-shm)?|\.env([^/]*)?)$|(^|/)backups/'; then
    echo "STOP: A database, backup, or environment file is staged."
    echo "Nothing will be committed or pushed."
    exit 1
fi

echo
echo "STEP 4: Files prepared for upload:"
git status --short

if git diff --cached --quiet; then
    echo
    echo "No new changes to upload."
    exit 0
fi

echo
echo "STEP 5: Creating deployment commit..."
git commit -m "Update MSA PORTAL application"

echo
echo "STEP 6: Uploading to GitHub..."
git push origin master

echo
echo "======================================"
echo "GITHUB UPLOAD COMPLETED"
echo "======================================"
echo "If Render auto-deploy is enabled,"
echo "Render should deploy the new commit."
echo
echo "IMPORTANT:"
echo "Your local database records were NOT uploaded."
echo "They must be migrated securely to the online database."
