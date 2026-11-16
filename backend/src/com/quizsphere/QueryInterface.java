package com.quizsphere;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ABSTRACTION: declares ONE hidden method, run(), and builds the public helpers
 * (fetchOne / fetchAll / execute) on top of it. DatabaseManager and Transaction
 * implement run() differently, so services can use either one (polymorphism).
 *
 * SECURITY: every query uses ? placeholders; values travel separately from the SQL
 * text, so user input can never change the structure of a query (SQL injection).
 */
public abstract class QueryInterface {
    public record WriteResult(int rowCount, long lastRowId) {}

    protected enum Mode { ONE, ALL, WRITE }

    protected abstract Object run(String sql, List<Object> params, Mode mode);

    public Row fetchOne(String sql, Object... params) { return (Row) run(sql, Arrays.asList(params), Mode.ONE); }

    public Row fetchOne(String sql, List<Object> params) { return (Row) run(sql, params, Mode.ONE); }

    @SuppressWarnings("unchecked")
    public List<Row> fetchAll(String sql, Object... params) { return (List<Row>) run(sql, Arrays.asList(params), Mode.ALL); }

    @SuppressWarnings("unchecked")
    public List<Row> fetchAll(String sql, List<Object> params) { return (List<Row>) run(sql, params, Mode.ALL); }

    public WriteResult execute(String sql, Object... params) { return (WriteResult) run(sql, Arrays.asList(params), Mode.WRITE); }

    public WriteResult execute(String sql, List<Object> params) { return (WriteResult) run(sql, params, Mode.WRITE); }

    /** Shared JDBC code: run one statement on a connection. */
    protected static Object executeOn(Connection conn, String sql, List<Object> params, Mode mode) {
        try (PreparedStatement ps = mode == Mode.WRITE
                ? conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
                : conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                Object v = params.get(i);
                if (v == null) ps.setNull(i + 1, Types.NULL); else ps.setObject(i + 1, v);
            }
            if (mode == Mode.WRITE) {
                int count = ps.executeUpdate();
                long id = 0;
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) id = keys.getLong(1);
                }
                return new WriteResult(count, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                List<Row> rows = new ArrayList<>();
                while (rs.next()) {
                    Row row = new Row();
                    for (int c = 1; c <= md.getColumnCount(); c++) row.put(md.getColumnLabel(c), rs.getObject(c));
                    rows.add(row);
                    if (mode == Mode.ONE) break;
                }
                if (mode == Mode.ONE) return rows.isEmpty() ? null : rows.get(0);
                return rows;
            }
        } catch (SQLException e) {
            throw DatabaseException.translate(e);
        }
    }
}
