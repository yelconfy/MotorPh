# MotorPH ERP — Installation Guide

A Java Swing desktop HR and payroll management system for MotorPH, scoped to Philippine statutory requirements (2024 figures). This guide walks through every resource, tool, and configuration step required to get the application running from a clean machine.

---

## 1. Resources Needed

Before you begin, gather the following:

| Resource | Detail |
|---|---|
| Source code | The MotorPH ERP Git repository (clone from GitHub) |
| SQL scripts | The numbered scripts in the `scripts/` folder (01 → 17) |
| Seed data files | `CredCSV.csv`, `EmpDetailsCSV.csv`, `EmpAttendanceRecordCSV.csv` (under `app/files/`) |
| UI assets | Tabler outline SVG icons (already committed under the resources path) |
| Disk location | A working folder **outside** any cloud-synced directory (see Troubleshooting) |

---

## 2. Tools Needed

### Required — these versions matter

The project is pinned to specific tooling. Don't substitute these.

| Tool | Version / Notes |
|---|---|
| **JDK** | Java 23 (the Gradle toolchain targets `JavaLanguageVersion.of(23)`) |
| **Gradle** | 8.14.4 — **use the committed wrapper** (`gradlew` / `gradlew.bat`). Do **not** install Gradle 9.x; it breaks NetBeans tooling. |
| **SQL Server** | Microsoft SQL Server (Express or Developer edition is fine), listening on port `1433` |
| **Git** | For cloning and version control |

### Pick one of each — your choice

These are interchangeable. The application and database don't care which you use, so go with whatever you're comfortable with.

| Role | Options | Notes |
|---|---|---|
| **SQL client** | DBeaver **or** SQL Server Management Studio (SSMS) | Either can run the setup scripts. SSMS is the native Microsoft tool; DBeaver is cross-platform. See the SQL-client note in Step 2 below — the "run the whole script" shortcut differs between them. |
| **Code editor / IDE** | VS Code, NetBeans, IntelliJ IDEA, or Eclipse | Use whatever you prefer for editing and running via the Gradle wrapper. **One exception:** if this is being submitted for academic grading, it **must** also open and run in **NetBeans**, since that's the required environment for grading. For everyday work any editor is fine. |

> In short: SQL Server, JDK 23, Gradle 8.14.4, and Git are fixed. Your SQL client and your editor are personal preference — DBeaver/VS Code, SSMS/NetBeans, or any mix work equally well.

### Bundled dependencies (no manual install required)

These are resolved automatically by Gradle from Maven Central:

- `mssql-jdbc` 12.4.2 — SQL Server JDBC driver
- `flatlaf` 3.7.1 — modern Swing look-and-feel
- `openpdf` 3.0.5 — payslip / report PDF generation
- `jbcrypt` 0.4 — password hashing
- `svg-salamander` 1.0 — SVG icon rendering
- `guava` 33.2.1, `opencsv` 5.9, `junit-jupiter` 5.10.3

---

## 3. Database Configuration (step-by-step)

The database is built by running the numbered SQL scripts **in order**. Each script's header documents its run order and dependencies.

### Step 1 — Confirm SQL Server is reachable

Make sure your SQL Server instance is running and accepting connections on `localhost:1433`.

### Step 2 — Open the scripts in your SQL client

Open each script and **run the entire file**, not just one statement. How you do that depends on your client:

- **DBeaver:** ⚠️ Use **Alt+X (Execute Script)** to run the whole file. `Ctrl+Enter` runs only the single statement under the cursor — this previously caused a view to be created in the wrong database because the `USE MotorPH_ERP;` line at the top never executed.
- **SSMS:** Press **F5 (Execute)** with no text selected, or select all (`Ctrl+A`) first, to run the whole script. If you highlight only part of the script, only that part runs — same trap as above.

> The underlying rule is identical in both: always run the **full** script so the leading `USE MotorPH_ERP;` runs before anything else.

### Step 3 — Run the scripts in order

| Order | Script | Purpose |
|---|---|---|
| 1 | `01 - Reference Tables.sql` | Drops & recreates `MotorPH_ERP`, builds reference + statutory table structure |
| 2 | `06 - Views` | Core views (incl. `vw_EmployeePayslipReport`) |
| 3 | `07 - ETL for Employees.sql` | Loads `EmpDetailsCSV.csv` into Employees |
| 4 | `08 - ETL for Credentials.sql` | Loads `CredCSV.csv`; seeds Departments, Account_Role, Users |
| 5 | `09 - Seed Access Control RBAC.sql` | Seeds Module / Permission / Role_Permission (idempotent) |
| 6 | `11 - Create SQL Login.sql` | Creates the app login `yel` and grants `db_owner` |
| 7 | `12 - Seed Statutory Tables.sql` | SSS / PhilHealth / Pag-IBIG / withholding tax (verified 2024 PH figures) |
| 8 | `16 - Premium Rate Table (+ seed).sql` | Versioned premium-rate multipliers + night-diff window |
| 9 | `17 - View_Monthly_Payroll_Summary.sql` | `vw_MonthlyPayrollSummary` reporting view |

> Run the attendance ETL (`EmpAttendanceRecordCSV.csv`) at its numbered position as well. The RBAC seed (09), statutory seed (12), and premium-rate seed (16) are all idempotent/self-correcting, so they are safe to re-run.

### Step 4 — Update the BULK INSERT file paths

Scripts **07 and 08** load CSVs via `BULK INSERT` and contain a hardcoded source path:

```sql
FROM 'C:\...\app\files\CredCSV.csv'   -- <<< UPDATE PATH
```

**Edit each `FROM '...'` path** to point to where the CSV actually lives on your machine before running. SQL Server (not your DBeaver client) must be able to read this path.

### Step 5 — Configure the application connection

Open `app/src/main/java/DataAccess/DatabaseConnector.java` and set the connection constants to match your environment:

```java
private static final String SERVER  = "localhost";
private static final String PORT    = "1433";
private static final String DB_NAME = "MotorPH_ERP";

// SQL Server Authentication — fill these in:
private static final String USER = "yel";
private static final String PASS = "Password1";
```

- For **SQL Server Authentication**, set `USER` / `PASS` to the login created by script 11.
- For **Windows Authentication**, leave `USER` empty/blank — the connector switches to `integratedSecurity=true` automatically.

> 🔒 Change `Password1` (used in script 11 and the connector) to a strong secret before any real use. It is the shared application credential.

### Step 6 — Passwords are rehashed in-app

Script 08 inserts a **placeholder** password hash and sets `MustChangePassword = 1` — T-SQL cannot produce bcrypt hashes. The application replaces these with real hashes via the first-login forced password change. This is expected; no action needed.

---

## 4. Gradle Configuration (step-by-step)

### Step 1 — Move the project outside cloud-synced folders

Place the project somewhere like `C:\Projects\MotorPH`. **Do not** keep it under OneDrive (see Troubleshooting).

### Step 2 — Confirm the wrapper is present

The repo commits the Gradle wrapper, locking the version to 8.14.4. You should see:

```
gradlew
gradlew.bat
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
```

Always invoke the wrapper, never a globally installed `gradle`.

### Step 3 — Verify the Java toolchain

Ensure JDK 23 is installed and discoverable. The build declares the toolchain in `app/build.gradle.kts`:

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(23)
    }
}
```

### Step 4 — Build

From the project root:

```bash
# macOS / Linux
./gradlew build

# Windows
gradlew.bat build
```

### Step 5 — Run

```bash
# macOS / Linux
./gradlew run

# Windows
gradlew.bat run
```

The main class is `com.MotorPh.App`. On launch you'll get the login window; sign in with a seeded credential and complete the forced password change on first login.

### Step 6 — Open in your IDE

Any editor that supports Gradle (VS Code, IntelliJ IDEA, Eclipse, NetBeans) can open the project — it will use the committed wrapper, so the Gradle and JDK versions stay consistent across machines. Build and run from the IDE as normal, or stick with `gradlew run` from the terminal.

**For academic grading:** open the project folder in **NetBeans** as a Gradle project and confirm it builds and runs there, since NetBeans is the required grading environment.

---

## 5. Troubleshooting — Issues We Actually Hit

### Build fails on public-type-vs-filename casing (e.g. `ILogInProcess.java` / `LogInProcess.java`)

**Cause:** On Windows, Git's `core.ignorecase=true` makes case-only file renames done on disk invisible to Git. A clean build then fails because the committed filename casing doesn't match the public type name.

**Fix:** Rename through Git, not the file system:

```bash
git mv -f old.java New.java
git commit -m "Fix file casing"
git push
```

Verify with `git ls-files` / `git ls-tree HEAD` — **not** `git status`, which reports clean for case-only differences.

### Gradle build breaks / files become "reparse points" (OneDrive)

**Cause:** Projects stored under a OneDrive path break Gradle. OneDrive Files On-Demand converts files to reparse points, which the build can't read.

**Fix:** Move the project outside OneDrive entirely (e.g. `C:\Projects\MotorPH`) and rebuild.

### NetBeans tooling error mentioning `org.gradle.util.VersionNumber`

**Cause:** Gradle 9.x removed `VersionNumber`, which the NetBeans Gradle integration relies on.

**Fix:** Stay on Gradle 8.14.4 via the committed wrapper. Don't upgrade the wrapper to 9.x. Make sure the wrapper is committed so every machine uses the same version.

### A view or object landed in the wrong database

**Cause:** Running a multi-statement script but executing only the statement under the cursor (or only a highlighted selection) — so the `USE MotorPH_ERP;` at the top never runs and objects get created in `master` or whatever database is currently selected.

**Fix:** Always run the **full** script:
- **DBeaver:** Alt+X (Execute Script).
- **SSMS:** F5 with nothing selected, or Ctrl+A then F5.

### `BULK INSERT` fails with "cannot open file" or access denied

**Cause:** The path in scripts 07/08 still points to a different machine, or SQL Server itself can't read the location.

**Fix:** Update the `FROM '...'` path to your local CSV location, and ensure the **SQL Server service account** (not your client) has read access to that folder.

### MSSQL JDBC driver not found / connection refused

**Checklist:**
- SQL Server is running and listening on `1433`.
- TCP/IP protocol is enabled for the instance.
- `SERVER` / `PORT` / `DB_NAME` in `DatabaseConnector.java` match your instance.
- The login `yel` exists (script 11 ran successfully) and the password matches `PASS`.
- For self-signed certs, the connector already sets `encrypt=true;trustServerCertificate=true;`.

### `JAVA_HOME` errors when running the Gradle scripts

**Fix:** Set `JAVA_HOME` to your JDK 23 install directory and ensure `java` is on your `PATH`.

---

## 6. Default Login Flow

1. Launch the app (`gradlew run` or via NetBeans).
2. Sign in with a seeded username from `CredCSV.csv`.
3. On first login you are forced to set a new password (the seed hashes are placeholders).
4. The shell shows only the modules your role is granted (RBAC from script 09).
5. Logging in from another workstation takes over the session (newest-login-wins, single-session enforcement).

---

*Built to industry-correctness standards on JDK 23 / Java Swing / FlatLaf, with SQL Server as the system of record.*
