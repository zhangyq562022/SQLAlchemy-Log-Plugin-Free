package com.github.sqlalchemylog;

import com.github.sqlalchemylog.format.BasicFormatter;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

public class BasicFormatterTest {

    @Test
    public void testFormatSelect() {
        BasicFormatter formatter = new BasicFormatter();
        String raw = "SELECT id, name, age FROM users WHERE id = 1 AND age > 18 ORDER BY id DESC";
        String formatted = formatter.format(raw);

        String upper = formatted.toUpperCase(Locale.ROOT);
        assertTrue(upper.contains("SELECT"));
        assertTrue(upper.contains("FROM"));
        assertTrue(upper.contains("WHERE"));
        assertTrue(upper.contains("ORDER") && upper.contains("BY"));
        // Confirm newlines are inserted
        assertTrue(formatted.contains("\n"));
    }

    @Test
    public void testFormatInsert() {
        BasicFormatter formatter = new BasicFormatter();
        String raw = "INSERT INTO users (id, name, age) VALUES (1, 'Alice', 30)";
        String formatted = formatter.format(raw);

        String upper = formatted.toUpperCase(Locale.ROOT);
        assertTrue(upper.contains("INSERT"));
        assertTrue(upper.contains("INTO"));
        assertTrue(upper.contains("VALUES"));
        assertTrue(formatted.contains("\n"));
    }
}
