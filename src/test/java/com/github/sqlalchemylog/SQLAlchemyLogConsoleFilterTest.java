package com.github.sqlalchemylog;

import com.github.sqlalchemylog.parser.PythonLiteralParser;
import com.github.sqlalchemylog.parser.SQLAlchemySqlParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SQLAlchemyLogConsoleFilterTest {

    @Test
    public void testUserRealMySQLQueries() {
        // Real world FastAPI + SQLAlchemy query with prefix, timestamp and parameters
        String sql = "SELECT users.id AS users_id, users.user_id AS users_user_id, users.nickname AS users_nickname, users.level AS users_level, users.mobile_masked AS users_mobile_masked, users.created_at AS users_created_at \n" +
                "FROM users \n" +
                "WHERE users.user_id = %(user_id_1)s \n" +
                " LIMIT %(param_1)s";

        String paramLiteral = "{'user_id_1': 'u1001', 'param_1': 1}";

        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse(paramLiteral);
        List<String> restored = SQLAlchemySqlParser.restoreSql(sql, params);

        assertEquals(1, restored.size());
        assertTrue(restored.get(0).contains("WHERE users.user_id = 'u1001'"));
        assertTrue(restored.get(0).contains("LIMIT 1"));
    }

    @Test
    public void testUserRealOrdersQuery() {
        String sql = "SELECT orders.id AS orders_id, orders.order_id AS orders_order_id, orders.user_id AS orders_user_id \n" +
                "FROM orders \n" +
                "WHERE orders.user_id = %(user_id_1)s ORDER BY orders.created_at DESC \n" +
                " LIMIT %(param_1)s";

        String paramLiteral = "{'user_id_1': 1, 'param_1': 5}";

        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse(paramLiteral);
        List<String> restored = SQLAlchemySqlParser.restoreSql(sql, params);

        assertEquals(1, restored.size());
        assertTrue(restored.get(0).contains("WHERE orders.user_id = 1"));
        assertTrue(restored.get(0).contains("LIMIT 5"));
    }

    @Test
    public void testSQLiteInsertPositionalTuple() {
        String sql = "INSERT INTO user_account (name, fullname) VALUES (?, ?) RETURNING id";
        String paramLiteral = "('Mr 张', '张三')";

        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse(paramLiteral);
        List<String> restored = SQLAlchemySqlParser.restoreSql(sql, params);

        assertEquals(1, restored.size());
        assertEquals("INSERT INTO user_account (name, fullname) VALUES ('Mr 张', '张三') RETURNING id", restored.get(0));
    }

    @Test
    public void testSQLiteSelectInPositionalTuple() {
        String sql = "SELECT user_account.id, user_account.name, user_account.fullname \n" +
                "FROM user_account \n" +
                "WHERE user_account.name IN (?, ?)";
        String paramLiteral = "('Mr 张', 'sandy')";

        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse(paramLiteral);
        List<String> restored = SQLAlchemySqlParser.restoreSql(sql, params);

        assertEquals(1, restored.size());
        assertTrue(restored.get(0).contains("WHERE user_account.name IN ('Mr 张', 'sandy')"));
    }

    @Test
    public void testSQLiteSelectSingleElementTupleWithComma() {
        String sql = "SELECT user_account.id, user_account.name, user_account.fullname \n" +
                "FROM user_account \n" +
                "WHERE user_account.name = ?";
        String paramLiteral = "('Mr 张',)";

        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse(paramLiteral);
        List<String> restored = SQLAlchemySqlParser.restoreSql(sql, params);

        assertEquals(1, restored.size());
        assertTrue(restored.get(0).contains("WHERE user_account.name = 'Mr 张'"));
    }

    @Test
    public void testRawSqlEmptyDictAndEmptyTuple() {
        String sql = "SELECT DATABASE()";
        String paramLiteral = "{}";

        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse(paramLiteral);
        List<String> restored = SQLAlchemySqlParser.restoreSql(sql, params);

        assertEquals(1, restored.size());
        assertEquals("SELECT DATABASE()", restored.get(0));

        // Empty tuple like [raw sql] ()
        params = PythonLiteralParser.parse("()");
        restored = SQLAlchemySqlParser.restoreSql(sql, params);
        assertEquals(1, restored.size());
        assertEquals("SELECT DATABASE()", restored.get(0));
    }

    @Test
    public void testInsertManyValuesRestoration() {
        String sql = "INSERT INTO t (val) VALUES (?), (?) RETURNING id";
        String paramLiteral = "('a', 'b')";

        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse(paramLiteral);
        List<String> restored = SQLAlchemySqlParser.restoreSql(sql, params);

        assertEquals(1, restored.size());
        assertEquals("INSERT INTO t (val) VALUES ('a'), ('b') RETURNING id", restored.get(0));
    }

    @Test
    public void testLoggingTokenParsingAndSqlExtraction() {
        // Line with logging_token [req_12345]
        String sql = "SELECT 1 WHERE 1 = ?";
        String paramLiteral = "(100,)";

        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse(paramLiteral);
        List<String> restored = SQLAlchemySqlParser.restoreSql(sql, params);

        assertEquals(1, restored.size());
        assertEquals("SELECT 1 WHERE 1 = 100", restored.get(0));
    }

    @Test
    public void testDDLMultiLineTableCreation() {
        String ddl = "CREATE TABLE all_types (\n" +
                "\tid INTEGER NOT NULL, \n" +
                "\tstr_col VARCHAR(50), \n" +
                "\tPRIMARY KEY (id)\n" +
                ")";
        String paramLiteral = "()";

        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse(paramLiteral);
        List<String> restored = SQLAlchemySqlParser.restoreSql(ddl, params);

        assertEquals(1, restored.size());
        assertTrue(restored.get(0).startsWith("CREATE TABLE all_types"));
    }

    @Test
    public void testStoredProcedureWithFormatAndComments() {
        String sql = "/* query_id=987 */ CALL sync_user_orders(%s, %s)";
        String paramLiteral = "('u2001', 50)";

        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse(paramLiteral);
        List<String> restored = SQLAlchemySqlParser.restoreSql(sql, params);

        assertEquals(1, restored.size());
        assertEquals("/* query_id=987 */ CALL sync_user_orders('u2001', 50)", restored.get(0));
    }
}
