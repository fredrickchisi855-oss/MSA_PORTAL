import sqlite3
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
DB_FILE = BASE_DIR / "msa_portal.db"


def get_connection():
    conn = sqlite3.connect(DB_FILE)
    conn.row_factory = sqlite3.Row
    return conn


def initialize_database():
    conn = get_connection()
    cursor = conn.cursor()

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS students (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            form INTEGER NOT NULL CHECK(form BETWEEN 1 AND 6),
            class_name TEXT NOT NULL,
            password TEXT NOT NULL,
            status TEXT NOT NULL DEFAULT 'Active',
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            student_id_code TEXT
        )
    """)

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS academic_years (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            year INTEGER NOT NULL UNIQUE,
            active INTEGER NOT NULL DEFAULT 0
        )
    """)

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS results (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            student_id INTEGER NOT NULL,
            academic_year INTEGER NOT NULL,
            term TEXT NOT NULL,
            file_name TEXT,
            file_path TEXT,
            published INTEGER NOT NULL DEFAULT 0,
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (student_id) REFERENCES students(id)
        )
    """)

    # Create initial academic years from 2026 onward.
    for year in range(2026, 2031):
        cursor.execute(
            "INSERT OR IGNORE INTO academic_years (year, active) VALUES (?, ?)",
            (year, 1 if year == 2026 else 0)
        )



    # --------------------------------------------------
    # PERMANENT MSA STUDENT ID MIGRATION
    # --------------------------------------------------
    student_columns = [
        row[1]
        for row in conn.execute("PRAGMA table_info(students)").fetchall()
    ]

    if "student_id_code" not in student_columns:
        conn.execute(
            "ALTER TABLE students ADD COLUMN student_id_code TEXT"
        )

    # Give existing students permanent MSA IDs.
    existing_students = conn.execute("""
        SELECT id
        FROM students
        WHERE student_id_code IS NULL
           OR TRIM(student_id_code) = ''
        ORDER BY id
    """).fetchall()

    for student in existing_students:
        max_row = conn.execute("""
            SELECT MAX(
                CASE
                    WHEN student_id_code LIKE 'MSA-%'
                    THEN CAST(SUBSTR(student_id_code, 5) AS INTEGER)
                    ELSE 0
                END
            )
            FROM students
        """).fetchone()

        next_number = int(max_row[0] or 0) + 1
        student_code = f"MSA-{next_number:02d}"

        while conn.execute("""
            SELECT 1
            FROM students
            WHERE student_id_code = ?
            LIMIT 1
        """, (student_code,)).fetchone():
            next_number += 1
            student_code = f"MSA-{next_number:02d}"

        conn.execute("""
            UPDATE students
            SET student_id_code = ?
            WHERE id = ?
        """, (student_code, student["id"]))

    conn.execute("""
        CREATE UNIQUE INDEX IF NOT EXISTS
        idx_students_student_id_code
        ON students(student_id_code)
    """)

    conn.execute("""
        CREATE TABLE IF NOT EXISTS password_reset_requests (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            student_id INTEGER NOT NULL,
            status TEXT NOT NULL DEFAULT 'pending',
            reset_pin TEXT,
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            approved_at TEXT,
            expires_at TEXT,
            used_at TEXT,
            FOREIGN KEY (student_id) REFERENCES students(id)
        )
    """)

    conn.commit()
    conn.close()

    print("✓ MSA PORTAL database initialized.")
    print(f"✓ Database: {DB_FILE}")


if __name__ == "__main__":
    initialize_database()
