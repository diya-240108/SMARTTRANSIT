package com.smarttransit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages user notifications for bookings, cancellations, waitlist promotions,
 * and live train schedule changes.
 */
public class Notification {
    private int notificationId;
    private int userId;
    private String message;
    private String createdAt;
    private boolean isRead;

    public Notification() {}

    public Notification(int notificationId, int userId, String message, String createdAt, boolean isRead) {
        this.notificationId = notificationId;
        this.userId = userId;
        this.message = message;
        this.createdAt = createdAt;
        this.isRead = isRead;
    }

    // Getters and Setters
    public int getNotificationId() { return notificationId; }
    public void setNotificationId(int notificationId) { this.notificationId = notificationId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    /**
     * Inserts a notification record for a specific user.
     */
    public static void create(Connection conn, int userId, String message) {
        String sql = "INSERT INTO notifications (user_id, message) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, message);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[WARN] Failed to insert notification: " + e.getMessage());
        }
    }

    /**
     * Retrieves all notifications for a specific user ordered by latest first.
     */
    public static List<Notification> getByUser(Connection conn, int userId) throws SQLException {
        List<Notification> list = new ArrayList<>();
        String sql = "SELECT notification_id, user_id, message, created_at, is_read " +
                     "FROM notifications WHERE user_id = ? ORDER BY created_at DESC";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new Notification(
                        rs.getInt("notification_id"),
                        rs.getInt("user_id"),
                        rs.getString("message"),
                        rs.getString("created_at"),
                        rs.getBoolean("is_read")
                    ));
                }
            }
        }
        return list;
    }

    /**
     * Marks all notifications as read for a user.
     */
    public static void markAllAsRead(Connection conn, int userId) throws SQLException {
        String sql = "UPDATE notifications SET is_read = TRUE WHERE user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        }
    }

    /**
     * Broadcasts train delay or cancellation alerts to all booked passengers on that train.
     */
    public static void notifyPassengersOfTrainStatus(Connection conn, int trainId, String status, String delayInfo) {
        String findUsersSql = "SELECT DISTINCT user_id FROM tickets WHERE train_id = ? AND status IN ('CONFIRMED', 'WAITLIST')";
        Set<Integer> userIds = new HashSet<>();
        try (PreparedStatement stmt = conn.prepareStatement(findUsersSql)) {
            stmt.setInt(1, trainId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    userIds.add(rs.getInt("user_id"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[WARN] Error finding passengers for notification: " + e.getMessage());
        }

        String msg = "🔔 Train Status Alert: Train #" + trainId + " status updated to " + status +
                     (delayInfo != null && !delayInfo.isEmpty() ? " (" + delayInfo + ")" : "");

        for (int uid : userIds) {
            create(conn, uid, msg);
        }
    }
}
