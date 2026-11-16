# QuizSphere — Student Quiz and Assessment Portal (Java + MySQL + HTML/CSS/JS)

Backend: Java (built-in `com.sun.net.httpserver`, JDBC, no frameworks). Frontend: HTML, CSS, vanilla JavaScript.
Only external dependency: the MySQL JDBC driver (Connector/J) jar.

## 1. Install (Windows)
| Software | Check command |
|---|---|
| JDK 17 or newer | `javac -version` |
| MySQL 8.0.16 or newer | `mysql --version` (add MySQL `bin` folder to PATH if not found) |
| VS Code (optional) | open this folder with `code .` |

## 2. MySQL driver
Download **MySQL Connector/J** (Platform Independent) from https://dev.mysql.com/downloads/connector/j/ , unzip it and copy
`mysql-connector-j-<version>.jar` into the `lib` folder.

## 3. Create the database
Command Prompt (use your own MySQL root password; add `-P <port>` if MySQL is not on 3306):
```
mysql -u root -p < database\schema.sql
mysql -u root -p quizsphere < database\seed.sql
```
PowerShell (it does not support `<`):
```
cmd /c "mysql -u root -p < database\schema.sql"
cmd /c "mysql -u root -p quizsphere < database\seed.sql"
```
Create a limited database user (log in with `mysql -u root -p`, then run):
```sql
CREATE USER 'quizsphere_user'@'localhost' IDENTIFIED BY 'choose-a-strong-password';
GRANT SELECT, INSERT, UPDATE, DELETE ON quizsphere.* TO 'quizsphere_user'@'localhost';
FLUSH PRIVILEGES;
```

## 4. Configure
Command Prompt: `copy .env.example .env`   PowerShell: `Copy-Item .env.example .env`
Open `.env`, set `DB_PASSWORD` to the password above (and `DB_PORT` if yours is not 3306). `.env` is git-ignored.

## 5. Create the administrator (once)
```
create-admin.bat
```
Type username, email, full name and a password (hidden). Nothing is stored in plain text.

## 6. Run
```
run.bat
```
Open **http://127.0.0.1:8000** in your browser. Stop with Ctrl+C.
(Mac/Linux: `javac -d out $(find backend/src -name "*.java")` then `java -cp "out:lib/*" com.quizsphere.Main`.)

* Student: click **Register**, create an account, log in, open Subjects, start a quiz.
* Administrator: log in on the same login page with the admin username; you are sent to the admin dashboard.

## 7. Automated checks (no database needed)
```
test.bat
```

## 8. Manual test plan
1. Register with invalid data -> field errors; with valid data -> redirected to login. Log in/out.
2. Dashboard shows zeros for a new student; take **OOP Fundamentals**: answer, mark for review, refresh mid-quiz (answers and timer survive), submit -> result, review, history, analytics update.
3. Let a quiz timer reach 00:00 -> auto-submitted, result flagged auto-submitted.
4. Double-click Start/Submit -> still only one attempt/result.
5. Log in as a second student: they cannot open the first student's `results.html?attempt=<id>` (404 message).
6. As a student, open `admin-dashboard.html` -> redirected; call `/api/admin/students` -> 403.
7. Admin: create a quiz, add questions, activate, deactivate; try deleting a question used in attempts -> refused.

## 9. Troubleshooting
| Message | Fix |
|---|---|
| `DB_PASSWORD is not set` | Create `.env` (step 4). |
| `MySQL rejected the username or password` | `DB_USER`/`DB_PASSWORD` do not match step 3. |
| `Cannot reach MySQL` | MySQL service not running (Windows Services) or wrong `DB_PORT`. |
| `database does not exist` | Run `schema.sql` (step 3). |
| `MySQL JDBC driver not found` | Copy the Connector/J jar into `lib`. |
| `Port 8000 is already in use` | Change `SERVER_PORT` in `.env`. |
| `javac` not recognized | Install a JDK (not just a JRE) and reopen the terminal. |
| `Unknown column / CHECK` errors on schema | MySQL older than 8.0.16. |

## 10. Security notes
Passwords: PBKDF2-HMAC-SHA256 + random salt. Sessions: random token in an HttpOnly, SameSite=Strict cookie (only its hash is stored).
CSRF token on every state-changing request. All SQL is parameterised. Grading, deadlines and authorisation are enforced in Java;
the answer key never reaches the browser before submission. **Limitation:** this local server uses plain HTTP (no encryption);
for production put it behind HTTPS, set `COOKIE_SECURE=true`, and add stronger rate limiting.

## 11. Layout
`backend/src/com/quizsphere` Java sources · `frontend` pages, css, js · `database` schema.sql, seed.sql, reports.sql · `tests` plain-Java checks.
