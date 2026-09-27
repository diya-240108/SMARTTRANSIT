package com.smarttransit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Handles regular passenger registration, login authentication, input validations,
 * profile retrieval, and dashboard summaries.
 */
public class User {
    private int userId;
    private String username;
    private String password;
    private String fullName;
    private String email;
    private String phone;
    private String category;
    private String createdAt;

    public User() {
        this.category = "NORMAL";
    }

    public User(int userId, String username, String fullName, String email, String phone, String category, String createdAt) {
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.category = category;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    /**
     * Validates input fields and registers a new user in the database.
     */
    public static User register(Connection conn, String username, String password,
                                String fullName, String email, String phone, String category) throws Exception {
        // 1. Validation
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty.");
        }
        if (password == null || password.length() < 4) {
            throw new IllegalArgumentException("Password must be at least 4 characters.");
        }
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException("Full name cannot be empty.");
        }
        if (email == null || !email.contains("@") || !email.contains(".")) {
            throw new IllegalArgumentException("Please provide a valid email address.");
        }
        if (phone == null || phone.replaceAll("\\D", "").length() < 10) {
            throw new IllegalArgumentException("Phone number must contain at least 10 digits.");
        }

        String cat = (category != null && !category.trim().isEmpty()) ? category.toUpperCase().trim() : "NORMAL";
        if (!cat.equals("NORMAL") && !cat.equals("STUDENT") && !cat.equals("SENIOR")) {
            cat = "NORMAL";
        }

        // 2. Check for duplicate username
        String checkSql = "SELECT COUNT(*) FROM users WHERE LOWER(username) = LOWER(?)";
        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setString(1, username.trim());
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    throw new IllegalArgumentException("Username '" + username + "' is already taken. Please choose another.");
                }
            }
        }

        // 3. Insert User
        String insertSql = "INSERT INTO users (username, password, full_name, email, phone, category) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            insertStmt.setString(1, username.trim());
            insertStmt.setString(2, password.trim());
            insertStmt.setString(3, fullName.trim());
            insertStmt.setString(4, email.trim());
            insertStmt.setString(5, phone.trim());
            insertStmt.setString(6, cat);
            insertStmt.executeUpdate();

            try (ResultSet gk = insertStmt.getGeneratedKeys()) {
                if (gk.next()) {
                    int newUserId = gk.getInt(1);

                    // Create welcome notification
                    Notification.create(conn, newUserId, "Welcome to Smart Transit System, " + fullName + "! Your account has been created.");

                    return new User(newUserId, username.trim(), fullName.trim(), email.trim(), phone.trim(), cat, null);
                }
            }
        }

        throw new SQLException("Failed to create user account.");
    }

    /**
     * Authenticates a user by username and password.
     */
    public static User login(Connection conn, String username, String password) throws SQLException {
        if (username == null || password == null) return null;

        String sql = "SELECT user_id, username, full_name, email, phone, category, created_at " +
                     "FROM users WHERE LOWER(username) = LOWER(?) AND password = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username.trim());
            stmt.setString(2, password.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new User(
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getString("phone"),
                        rs.getString("category"),
                        rs.getString("created_at")
                    );
                }
            }
        }
        return null;
    }

    /**
     * Dashboard Summary statistics for a user.
     */
    public static class DashboardStats {
        public int totalBookings;
        public int confirmedBookings;
        public int waitlistBookings;
        public int unreadNotifications;
        public double totalSpent;
    }

    public static DashboardStats getDashboardStats(Connection conn, int userId) throws SQLException {
        DashboardStats stats = new DashboardStats();

        String ticketSql = "SELECT status, total_fare FROM tickets WHERE user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(ticketSql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    stats.totalBookings++;
                    String s = rs.getString("status");
                    if ("CONFIRMED".equalsIgnoreCase(s)) stats.confirmedBookings++;
                    else if ("WAITLIST".equalsIgnoreCase(s)) stats.waitlistBookings++;
                    stats.totalSpent += rs.getDouble("total_fare");
                }
            }
        }

        String notifSql = "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = FALSE";
        try (PreparedStatement stmt = conn.prepareStatement(notifSql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) stats.unreadNotifications = rs.getInt(1);
            }
        }

        return stats;
    }
}
