package com.github.sqlalchemylog;

import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

public class SQLAlchemyLogConsoleFilterProvider implements ConsoleFilterProvider {

    private final Key<SQLAlchemyLogConsoleFilter> key = Key.create(SQLAlchemyLogConsoleFilter.class.getName());

    @Override
    public Filter @NotNull [] getDefaultFilters(@NotNull Project project) {
        SQLAlchemyLogConsoleFilter filter = project.getUserData(key);
        if (filter == null) {
            filter = new SQLAlchemyLogConsoleFilter(project);
            project.putUserData(key, filter);
        }
        return new Filter[]{filter};
    }
}
