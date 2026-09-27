package com.smarttransit;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Database Utility class for managing JDBC Connections and transactions.
 * Designed for standard MySQL 8+ installations.
 */
public class Database {

    private static String dbHost = "localhost";
    private static String dbPort = "3306";
    private static String dbName = "railway";
    private static String dbUser = "root";
    private static String dbPassword = "root"; // Common default, can be reconfigured

    private static final String DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";

    static {
        try {
            Class.forName(DRIVER_CLASS);
        } catch (ClassNotFoundException e) {
            System.err.println("[ERROR] MySQL JDBC Driver not found in classpath: " + e.getMessage());
        }
    }

    public static void setCredentials(String host, String port, String name, String user, String pass) {
        dbHost = host;
        dbPort = port;
        dbName = name;
        dbUser = user;
        dbPassword = pass;
    }

    public static String getDbUrl() {
        return "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    public static String getDbUser() {
        return dbUser;
    }

    public static String getDbName() {
        return dbName;
    }

    /**
     * Obtains a new JDBC Connection.
     */
    public static Connection getConnection() throws SQLException {
        try {
            return DriverManager.getConnection(getDbUrl(), dbUser, dbPassword);
        } catch (SQLException e) {
            // Try with empty password fallback if password was "root"
            if ("root".equals(dbPassword)) {
                try {
                    Connection c = DriverManager.getConnection(
                            "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName
                            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                            dbUser, "");
                    dbPassword = "";
                    return c;
                } catch (SQLException ignored) {
                    // Fall back to original exception
                }
            }
            throw e;
        }
    }

    /**
     * Closes SQL resources safely.
     */
    public static void close(AutoCloseable... resources) {
        for (AutoCloseable res : resources) {
            if (res != null) {
                try {
                    res.close();
                } catch (Exception e) {
                    System.err.println("[WARN] Error closing resource: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Helper to rollback a transaction safely on failure.
     */
    public static void rollback(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException e) {
                System.err.println("[ERROR] Failed to rollback transaction: " + e.getMessage());
            }
        }
    }

    /**
     * Tests whether database connection is alive.
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}
