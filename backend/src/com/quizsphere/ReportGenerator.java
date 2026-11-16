package com.quizsphere;

import java.util.Map;

/**
 * ABSTRACTION + POLYMORPHISM for reporting. This class says WHAT every report class must provide;
 * StudentReport and AdminReport decide HOW, each with its own SQL.
 * All numbers come from MySQL views/queries - nothing is hard-coded.
 */
public abstract class ReportGenerator {
    protected final QueryInterface db;

    protected ReportGenerator(QueryInterface db) { this.db = db; }

    public abstract Map<String, Object> dashboard(Long userId);

    public abstract Map<String, Object> analytics(Long userId);
}
