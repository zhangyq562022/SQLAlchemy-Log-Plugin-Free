# SQLAlchemy Log Free (PyCharm & IntelliJ Plugin)

[English](README.md) | [中文说明](README_CN.md)

**SQLAlchemy Log Free** is a free and open-source JetBrains plugin designed for **PyCharm** and other IntelliJ Platform IDEs. It intercepts Python SQLAlchemy SQL logs output in the execution console, automatically replaces placeholders with bound Python parameter values, and prints complete, executable, and syntax-highlighted SQL statements in a dedicated Tool Window in real-time.

Inspired by the popular [`starxg/mybatis-log-plugin-free`](https://github.com/starxg/mybatis-log-plugin-free) plugin.

---

## Features

- **Real-Time Log Interception**: Automatically monitors Python application execution consoles (Run, Debug, Test, Services, FastAPI, Flask, Django, etc.).
- **Smart Parameter Binding**:
  - `?` (qmark style - SQLite, ODBC)
  - `%s` (format style - MySQL, psycopg2)
  - `%(name)s` (pyformat style - PostgreSQL, pymysql)
  - `:name` (named style - Oracle, text queries, safely ignoring PostgreSQL `::cast` type syntax)
  - `$1, $2, ...` (numeric style - asyncpg)
- **Rich Python Type Support**:
  - `None` &rarr; `NULL`
  - `True` / `False` &rarr; `1` / `0`
  - `datetime.datetime(...)` &rarr; `'YYYY-MM-DD HH:MM:SS.ffffff'`
  - `datetime.date(...)` &rarr; `'YYYY-MM-DD'`
  - `datetime.time(...)` &rarr; `'HH:MM:SS'`
  - `Decimal(...)` &rarr; `99.95`
  - `UUID(...)` &rarr; `'...'`
  - Strings with quotation and escape handling (`'It''s ok'`)
  - Binary bytes (`b'...'`)
- **Executemany / Batch Query Support**: Expands batch operations (`[('Charlie', 40), ('Diana', 35)]`) into executable SQL statements.
- **Transaction Visibility**: Monitors and highlights `BEGIN`, `COMMIT`, `ROLLBACK`, and `SAVEPOINT` statements.
- **Dedicated Tool Window**:
  - Independent `SQLAlchemy Log` tab.
  - Query counter and execution timing info.
  - Color-coded SQL by query type: **SELECT**, **INSERT**, **UPDATE**, **DELETE**, and **TRANSACTIONS**.
- **Actions & Controls**:
  - **Rerun / Start** (Play button): Start/restart log capturing.
  - **Stop / Pause** (Suspend button): Pause capturing.
  - **Settings** (Gear button): Configure engine prefix, parameter prefix, keyword filters, and SQL colors.
  - **Navigate Previous / Next SQL** (Up/Down arrows): Jump between SQL statements.
  - **Pretty Print** (Format toggle): Pretty print and indent SQL with one click.
  - **Copy SQL**: Copy selected text or the entire SQL query to the clipboard.
  - **Clear All**: Clear the log console.

---

## Installation

### Method 1: Install from Plugin Zip
1. Download or build the plugin distribution package `SQLAlchemy-Log-Plugin-Free-1.0.0.zip`.
2. In PyCharm / IntelliJ:
   - Go to **Settings** (`Cmd + ,` or `Ctrl + Alt + S`) &rarr; **Plugins**.
   - Click the gear icon :gear: &rarr; **Install Plugin from Disk...**.
   - Select the `SQLAlchemy-Log-Plugin-Free-1.0.0.zip` file.
   - Click **Restart IDE**.

### Method 2: Build from Source
```bash
# Clone the repository
git clone https://github.com/starxg/SQLAlchemy-Log-Plugin-Free.git
cd SQLAlchemy-Log-Plugin-Free

# Build the plugin package
./gradlew buildPlugin

# Output zip location:
# build/distributions/SQLAlchemy-Log-Plugin-Free-1.0.0.zip
```

---

## Usage

### 1. Enable SQLAlchemy Logging
Ensure your Python application outputs SQLAlchemy engine logs. For example:

```python
import logging
from sqlalchemy import create_engine

# Option A: Built-in echo
engine = create_engine("sqlite:///example.db", echo=True)

# Option B: Standard logging configuration
logging.basicConfig()
logging.getLogger("sqlalchemy.engine").setLevel(logging.INFO)
```

### 2. Open SQLAlchemy Log Window
- Click **Tools** &rarr; **SQLAlchemy Log** from the top menu.
- Or right-click inside any console and select **SQLAlchemy Log**.

### 3. Run Your Application
Run or debug your Python program as usual. The plugin will intercept the engine logs and restore executable SQL:

```
[Console output]:
INFO:sqlalchemy.engine.Engine:SELECT users.id, users.name, users.age FROM users WHERE users.name = ? AND users.age > ?
INFO:sqlalchemy.engine.Engine:[generated in 0.00018s] ('Alice', 18)

[SQLAlchemy Log Window]:
-- #1 -- [generated in 0.00018s]
SELECT users.id, users.name, users.age 
FROM users 
WHERE users.name = 'Alice' AND users.age > 18;
```

---

## Settings & Customization

Click the **Settings** (:gear:) button on the tool window's left toolbar to open the settings dialog:

- **Engine Log Prefix**: Defaults to `sqlalchemy.engine`. Customizable if you use a custom logger format.
- **Parameter Indicator**: Defaults to `[` (matches `[generated in ...]`, `[cached since ...]`, `[raw sql]`).
- **Ignore Keywords**: Filter out noisy queries (e.g. `PRAGMA`, `table_info`).
- **SQL Colors**: Customize text colors for `SELECT`, `INSERT`, `UPDATE`, `DELETE`, and `TRANSACTION`.

---

## License

Licensed under the [Apache License 2.0](LICENSE).
