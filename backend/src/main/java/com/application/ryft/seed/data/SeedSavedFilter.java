package com.application.ryft.seed.data;

import com.application.ryft.issues.entity.IssueType;
import java.util.List;

/**
 * A fluent builder, not a positional record: {@code shared}/{@code onlyMine} were two adjacent booleans,
 * exactly the kind of call site (bare {@code true}/{@code false} literals) that reads ambiguously and is
 * easy to transpose without the compiler catching it — see {@link SeedIssue}'s javadoc for the same
 * reasoning. {@link #onlyMine()} builds the filter's {@code assigneeIds} as the seeding caller's own id
 * (resolved at seed time, not declarable statically here — see
 * {@code DemoProjectInstaller#createSavedFilters}). {@link #ofTypes} left unset means "don't filter by
 * type" — same convention as {@code search.dto.IssueSearchRequest}.
 */
public final class SeedSavedFilter {

    private final String name;
    private boolean shared;
    private boolean onlyMine;
    private List<IssueType> types = List.of();

    private SeedSavedFilter(String name) {
        this.name = name;
    }

    public static SeedSavedFilter named(String name) {
        return new SeedSavedFilter(name);
    }

    public SeedSavedFilter shared() {
        this.shared = true;
        return this;
    }

    public SeedSavedFilter onlyMine() {
        this.onlyMine = true;
        return this;
    }

    public SeedSavedFilter ofTypes(IssueType... types) {
        this.types = List.of(types);
        return this;
    }

    public String name() {
        return name;
    }

    public boolean isShared() {
        return shared;
    }

    public boolean isOnlyMine() {
        return onlyMine;
    }

    public List<IssueType> types() {
        return types;
    }
}
