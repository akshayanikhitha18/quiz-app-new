package com.quizsphere;

import java.sql.Connection;
import java.util.List;

/** Statements run on ONE connection and are committed or rolled back together (the "A" in ACID). */
public final class Transaction extends QueryInterface {
    private final Connection conn;
    private boolean finished;

    Transaction(Connection conn) { this.conn = conn; }

    @Override
    protected Object run(String sql, List<Object> params, Mode mode) {
        if (finished) throw new DatabaseException("This transaction has already finished.");
        return executeOn(conn, sql, params, mode);
    }

    void close() { finished = true; }
}
