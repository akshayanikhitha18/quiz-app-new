package com.quizsphere;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** The rest of the application catches THESE, never raw JDBC exceptions. */
public class DatabaseException extends RuntimeException {
    private static final Logger LOG = Logger.getLogger("quizsphere.db");

    public DatabaseException(String message) { super(message); }

    /** MySQL cannot be reached or rejected our credentials. */
    public static class Unavailable extends DatabaseException {
        public Unavailable(String message) { super(message); }
    }

    /** A table constraint rejected the data. kind: duplicate, foreign_key, check, not_null, too_long. */
    public static class ConstraintViolation extends DatabaseException {
        public final String kind;
        public final int errno;

        public ConstraintViolation(String kind, int errno, String message) {
            super(message);
            this.kind = kind;
            this.errno = errno;
        }
    }

    /** Convert a JDBC exception into one of our own; technical details go to the log only. */
    public static DatabaseException translate(SQLException e) {
        int errno = e.getErrorCode();
        String state = e.getSQLState() == null ? "" : e.getSQLState();
        LOG.log(Level.SEVERE, "MySQL error " + errno + " (" + state + "): " + e.getMessage());
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("No suitable driver") || msg.contains("ClassNotFound")) {
            return new Unavailable("MySQL JDBC driver not found: copy mysql-connector-j-*.jar into the lib folder.");
        }
        switch (errno) {
            case 1062: return new ConstraintViolation("duplicate", errno, "That value already exists.");
            case 1451: case 1217: return new ConstraintViolation("foreign_key", errno, "This record is still referenced by other data.");
            case 1452: case 1216: return new ConstraintViolation("foreign_key", errno, "A referenced record does not exist.");
            case 3819: return new ConstraintViolation("check", errno, "The data violates a database rule.");
            case 1048: return new ConstraintViolation("not_null", errno, "A required value is missing.");
            case 1406: return new ConstraintViolation("too_long", errno, "A value is too long.");
            case 1045: return new Unavailable("MySQL rejected the username or password (check DB_USER / DB_PASSWORD).");
            case 1049: return new Unavailable("The database does not exist yet (run database/schema.sql first).");
            case 1213: return new DatabaseException("The database was busy. Please try again.");
            default: break;
        }
        if (state.startsWith("08")) {
            return new Unavailable("Cannot reach MySQL (check DB_HOST / DB_PORT and that MySQL is running).");
        }
        return new DatabaseException("A database error occurred.");
    }
}
