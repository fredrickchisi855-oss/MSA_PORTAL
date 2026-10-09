#!/data/data/com.termux/files/usr/bin/bash

set -e

cd ~/MSA_PORTAL

echo "================================"
echo "       MSA PORTAL DEPLOY"
echo "================================"

echo "[1/5] Checking project..."

if [ ! -f "backend/server.py" ] || [ ! -f "backend/database.py" ]; then
    echo "ERROR: Backend files not found."
    exit 1
fi

echo "[2/5] Checking Python files..."

python -m py_compile backend/server.py backend/database.py

echo "[3/5] Initializing local database..."

python -c "import sys; sys.path.insert(0, 'backend'); from database import initialize_database; initialize_database()"

echo "[4/5] Checking Git..."

git status --short

echo ""
echo "IMPORTANT: Review the files above."
echo "This script will NOT delete your database."
echo "It will NOT automatically commit or push files."
echo ""
echo "Local checks completed successfully."
echo "Next: review database configuration before deployment."
