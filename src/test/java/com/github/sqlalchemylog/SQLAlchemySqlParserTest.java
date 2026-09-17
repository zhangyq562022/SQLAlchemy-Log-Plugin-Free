package com.github.sqlalchemylog;

import com.github.sqlalchemylog.parser.PythonLiteralParser;
import com.github.sqlalchemylog.parser.SQLAlchemySqlParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SQLAlchemySqlParserTest {

    @Test
    public void testQmarkSubstitution() {
        String sql = "SELECT users.id, users.name, users.age FROM users WHERE users.name = ? AND users.age > ?";
        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse("('Alice', 18)");

        List<String> result = SQLAlchemySqlParser.restoreSql(sql, params);
        assertEquals(1, result.size());
        assertEquals("SELECT users.id, users.name, users.age FROM users WHERE users.name = 'Alice' AND users.age > 18", result.get(0));
    }

    @Test
    public void testFormatSubstitution() {
        String sql = "SELECT * FROM users WHERE name = %s AND email LIKE '%%@example.com' AND age > %s";
        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse("('Bob', 25)");

        List<String> result = SQLAlchemySqlParser.restoreSql(sql, params);
        assertEquals(1, result.size());
        assertEquals("SELECT * FROM users WHERE name = 'Bob' AND email LIKE '%%@example.com' AND age > 25", result.get(0));
    }

    @Test
    public void testPyformatNamedSubstitution() {
        String sql = "SELECT id, name FROM users WHERE name = %(name_1)s AND age > %(age_1)s";
        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse("{'name_1': 'Charlie', 'age_1': 30}");

        List<String> result = SQLAlchemySqlParser.restoreSql(sql, params);
        assertEquals(1, result.size());
        assertEquals("SELECT id, name FROM users WHERE name = 'Charlie' AND age > 30", result.get(0));
    }

    @Test
    public void testColonNamedSubstitutionWithPostgresCast() {
        String sql = "SELECT id, name, created_at::date FROM users WHERE name = :user_name AND age >= :min_age";
        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse("{'user_name': 'Dave', 'min_age': 20}");

        List<String> result = SQLAlchemySqlParser.restoreSql(sql, params);
        assertEquals(1, result.size());
        assertEquals("SELECT id, name, created_at::date FROM users WHERE name = 'Dave' AND age >= 20", result.get(0));
    }

    @Test
    public void testNumericAsyncpgSubstitution() {
        String sql = "SELECT id, name FROM users WHERE id = $1 AND status = $2";
        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse("(101, 'ACTIVE')");

        List<String> result = SQLAlchemySqlParser.restoreSql(sql, params);
        assertEquals(1, result.size());
        assertEquals("SELECT id, name FROM users WHERE id = 101 AND status = 'ACTIVE'", result.get(0));
    }

    @Test
    public void testExecutemanyBatch() {
        String sql = "INSERT INTO users (name, age) VALUES (?, ?)";
        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse("[('Charlie', 40), ('Diana', 35)]");

        List<String> result = SQLAlchemySqlParser.restoreSql(sql, params);
        assertEquals(2, result.size());
        assertEquals("INSERT INTO users (name, age) VALUES ('Charlie', 40)", result.get(0));
        assertEquals("INSERT INTO users (name, age) VALUES ('Diana', 35)", result.get(1));
    }

    @Test
    public void testUpdateAndDelete() {
        String updateSql = "UPDATE users SET age=? WHERE users.name = ?";
        PythonLiteralParser.ParsedParams updateParams = PythonLiteralParser.parse("(26, 'Bob')");
        List<String> updateRes = SQLAlchemySqlParser.restoreSql(updateSql, updateParams);
        assertEquals("UPDATE users SET age=26 WHERE users.name = 'Bob'", updateRes.get(0));

        String deleteSql = "DELETE FROM users WHERE users.age < ?";
        PythonLiteralParser.ParsedParams deleteParams = PythonLiteralParser.parse("(20,)");
        List<String> deleteRes = SQLAlchemySqlParser.restoreSql(deleteSql, deleteParams);
        assertEquals("DELETE FROM users WHERE users.age < 20", deleteRes.get(0));
    }

    @Test
    public void testOracleNumericColonPlaceholders() {
        String sql = "SELECT * FROM employees WHERE dept_id = :1 AND salary > :2";
        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse("(10, 50000)");

        List<String> result = SQLAlchemySqlParser.restoreSql(sql, params);
        assertEquals(1, result.size());
        assertEquals("SELECT * FROM employees WHERE dept_id = 10 AND salary > 50000", result.get(0));
    }

    @Test
    public void testPostgreSQLJsonbOperatorPreserved() {
        // In PostgreSQL, '?' is the JSONB top-level key existence operator.
        // When parameters are passed via %s (psycopg2), '?' must NOT be treated as a placeholder!
        String sql = "SELECT id FROM documents WHERE data ? %s AND status = %s";
        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse("('metadata', 'PUBLISHED')");

        List<String> result = SQLAlchemySqlParser.restoreSql(sql, params);
        assertEquals(1, result.size());
        assertEquals("SELECT id FROM documents WHERE data ? 'metadata' AND status = 'PUBLISHED'", result.get(0));
    }

    @Test
    public void testAsyncpgWithCast() {
        String sql = "SELECT users.id, users.name FROM users WHERE users.name = $1::VARCHAR AND users.age >= $2";
        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse("('Eve', 21)");

        List<String> result = SQLAlchemySqlParser.restoreSql(sql, params);
        assertEquals(1, result.size());
        assertEquals("SELECT users.id, users.name FROM users WHERE users.name = 'Eve'::VARCHAR AND users.age >= 21", result.get(0));
    }

    @Test
    public void testPlaceholdersInsideStringLiteralsNotReplaced() {
        String sql = "SELECT * FROM logs WHERE message = 'User said: :1 or $1 or ? or %s' AND user_id = :1";
        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse("(99,)");

        List<String> result = SQLAlchemySqlParser.restoreSql(sql, params);
        assertEquals(1, result.size());
        assertEquals("SELECT * FROM logs WHERE message = 'User said: :1 or $1 or ? or %s' AND user_id = 99", result.get(0));
    }

    @Test
    public void testMSSQLOutputClause() {
        String sql = "INSERT INTO users (name, age) OUTPUT inserted.id VALUES (:name, :age)";
        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse("{'name': 'Frank', 'age': 45}");

        List<String> result = SQLAlchemySqlParser.restoreSql(sql, params);
        assertEquals(1, result.size());
        assertEquals("INSERT INTO users (name, age) OUTPUT inserted.id VALUES ('Frank', 45)", result.get(0));
    }

    @Test
    public void testCommentsAndStoredProcedure() {
        String sql = "/* get active users */ CALL get_active_users(?, ?)";
        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse("('admin', 10)");

        List<String> result = SQLAlchemySqlParser.restoreSql(sql, params);
        assertEquals(1, result.size());
        assertEquals("/* get active users */ CALL get_active_users('admin', 10)", result.get(0));
    }
}
