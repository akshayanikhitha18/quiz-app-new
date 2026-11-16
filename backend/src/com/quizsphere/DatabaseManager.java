package com.quizsphere;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The ONLY class that opens MySQL connections.
 *  - keeps a small pool of connections (the HTTP server is multi-threaded);
 *  - runs single statements in autocommit mode;
 *  - runs groups of statements in a Transaction (commit together or roll back together).
 *
 * The only external dependency of the project is the MySQL JDBC driver (MySQL Connector/J).
 */
public final class DatabaseManager extends QueryInterface {
    private static final Logger LOG = Logger.getLogger("quizsphere.db");

    public interface TxWork<T> { T run(Transaction tx); }

    public interface TxBlock { void run(Transaction tx); }

    private final Config config;
    private final BlockingQueue<Connection> pool;
    private final Object lock = new Object();
    private boolean initialised;

    public DatabaseManager(Config config) {
        this.config = config;
        this.pool = new ArrayBlockingQueue<>(config.dbPoolSize());
    }

    /** Current time as UTC (the DB session also runs in UTC, so all timestamps agree). */
    public static LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    }

    private Connection open() throws SQLException {
        String url = "jdbc:mysql://" + config.dbHost() + ":" + config.dbPort() + "/" + config.dbName()
                + "?useUnicode=true&characterEncoding=utf8&tinyInt1isBit=false"
                + "&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
                + "&connectTimeout=10000&useSSL=false&allowPublicKeyRetrieval=true";
        Connection c = DriverManager.getConnection(url, config.dbUser(), config.dbPassword());
        c.setAutoCommit(true);
        try (Statement s = c.createStatement()) {
            s.execute("SET time_zone = '+00:00'");
        }
        return c;
    }

    private void ensurePool() {
        synchronized (lock) {
            if (initialised) return;
            try {
                for (int i = 0; i < config.dbPoolSize(); i++) pool.add(open());
            } catch (SQLException e) {
                for (Connection c : pool) try { c.close(); } catch (SQLException ignored) { }
                pool.clear();
                throw DatabaseException.translate(e);
            }
            initialised = true;
        }
    }

    private Connection acquire() {
        ensurePool();
        Connection c;
        try {
            c = pool.poll(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseException.Unavailable("The server is busy. Please try again.");
        }
        if (c == null) throw new DatabaseException.Unavailable("The server is busy. Please try again.");
        try {
            if (!c.isValid(2)) c = open();
        } catch (SQLException e) {
            pool.offer(c);
            throw DatabaseException.translate(e);
        }
        return c;
    }

    private void release(Connection c) {
        try {
            if (!c.getAutoCommit()) {
                c.rollback();
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Could not reset connection: " + e.getMessage());
        }
        pool.offer(c);
    }

    @Override
    protected Object run(String sql, List<Object> params, Mode mode) {
        Connection c = acquire();
        try {
            return executeOn(c, sql, params, mode);
        } finally {
            release(c);
        }
    }

    /**
     * Usage:   db.inTransaction(tx -> { tx.execute(...); tx.execute(...); return something; });
     * Leaving normally = COMMIT. Any exception inside = ROLLBACK and the exception is re-thrown.
     */
    public <T> T inTransaction(TxWork<T> work) {
        Connection c = acquire();
        Transaction tx = new Transaction(c);
        try {
            c.setAutoCommit(false);
            T result = work.run(tx);
            c.commit();
            return result;
        } catch (SQLException e) {
            rollbackQuietly(c);
            throw DatabaseException.translate(e);
        } catch (RuntimeException e) {
            rollbackQuietly(c);
            throw e;
        } finally {
            tx.close();
            release(c);
        }
    }

    public void runInTransaction(TxBlock block) {
        inTransaction(tx -> { block.run(tx); return null; });
    }

    private static void rollbackQuietly(Connection c) {
        try { c.rollback(); } catch (SQLException e) { LOG.log(Level.SEVERE, "Rollback failed: " + e.getMessage()); }
    }

    /** Used at server start-up to fail early with a clear message. */
    public boolean checkConnection() {
        Row row = fetchOne("SELECT 1 AS ok");
        return row != null && row.getInt("ok") == 1;
    }
}
