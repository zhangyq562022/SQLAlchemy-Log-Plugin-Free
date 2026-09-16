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
    public void testRawSqlEmptyDict() {
        String sql = "SELECT DATABASE()";
        String paramLiteral = "{}";

        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse(paramLiteral);
        List<String> restored = SQLAlchemySqlParser.restoreSql(sql, params);

        assertEquals(1, restored.size());
        assertEquals("SELECT DATABASE()", restored.get(0));
    }
}
