package com.github.sqlalchemylog;

import com.github.sqlalchemylog.parser.PythonLiteralParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PythonLiteralParserTest {

    @Test
    public void testEmptyAndNone() {
        PythonLiteralParser.ParsedParams empty = PythonLiteralParser.parse("()");
        assertTrue(empty.isEmpty());

        empty = PythonLiteralParser.parse("{}");
        assertTrue(empty.isEmpty());

        empty = PythonLiteralParser.parse("[]");
        assertTrue(empty.isEmpty());
    }

    @Test
    public void testPositionalTuple() {
        String input = "('Alice', 30, True, False, None)";
        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse(input);

        assertFalse(params.isEmpty());
        assertTrue(params.isPositional());
        assertFalse(params.isNamed());
        assertFalse(params.isBatch());

        List<String> items = params.getFirstPositional();
        assertEquals(5, items.size());
        assertEquals("'Alice'", items.get(0));
        assertEquals("30", items.get(1));
        assertEquals("1", items.get(2));
        assertEquals("0", items.get(3));
        assertEquals("NULL", items.get(4));
    }

    @Test
    public void testSingleElementTuple() {
        String input = "(20,)";
        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse(input);

        List<String> items = params.getFirstPositional();
        assertEquals(1, items.size());
        assertEquals("20", items.get(0));
    }

    @Test
    public void testNamedDictionary() {
        String input = "{'name_1': 'Bob', 'age_1': 25, 'is_active': True}";
        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse(input);

        assertFalse(params.isEmpty());
        assertTrue(params.isNamed());
        assertFalse(params.isPositional());

        Map<String, String> map = params.getFirstNamed();
        assertEquals("'Bob'", map.get("name_1"));
        assertEquals("25", map.get("age_1"));
        assertEquals("1", map.get("is_active"));
    }

    @Test
    public void testComplexPythonTypes() {
        String input = "(datetime.datetime(2026, 9, 16, 14, 30, 0, 123456), datetime.date(2026, 9, 16), Decimal('99.95'), UUID('12345678-1234-5678-1234-567812345678'), b'abc', 'It\\'s a test')";
        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse(input);

        List<String> items = params.getFirstPositional();
        assertEquals(6, items.size());
        assertEquals("'2026-09-16 14:30:00.123456'", items.get(0));
        assertEquals("'2026-09-16'", items.get(1));
        assertEquals("99.95", items.get(2));
        assertEquals("'12345678-1234-5678-1234-567812345678'", items.get(3));
        assertEquals("'abc'", items.get(4));
        assertEquals("'It''s a test'", items.get(5));
    }

    @Test
    public void testBatchTupleList() {
        String input = "[('Charlie', 40), ('Diana', 35)]";
        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse(input);

        assertTrue(params.isBatch());
        assertTrue(params.isPositional());
        assertEquals(2, params.getPositionalBatches().size());

        List<String> batch1 = params.getPositionalBatches().get(0);
        assertEquals("'Charlie'", batch1.get(0));
        assertEquals("40", batch1.get(1));

        List<String> batch2 = params.getPositionalBatches().get(1);
        assertEquals("'Diana'", batch2.get(0));
        assertEquals("35", batch2.get(1));
    }

    @Test
    public void testBatchDictList() {
        String input = "[{'name': 'Charlie', 'age': 40}, {'name': 'Diana', 'age': 35}]";
        PythonLiteralParser.ParsedParams params = PythonLiteralParser.parse(input);

        assertTrue(params.isBatch());
        assertTrue(params.isNamed());
        assertEquals(2, params.getNamedBatches().size());

        assertEquals("'Charlie'", params.getNamedBatches().get(0).get("name"));
        assertEquals("40", params.getNamedBatches().get(0).get("age"));
        assertEquals("'Diana'", params.getNamedBatches().get(1).get("name"));
        assertEquals("35", params.getNamedBatches().get(1).get("age"));
    }
}
