package com.smarttransit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Manages the Waitlist subsystem using standard Java Collections (Queue and LinkedList)
 * adhering strictly to First-In-First-Out (FIFO) ordering, synchronized with MySQL.
 */
public class Waitlist {

    public static class WaitlistEntry {
        public int waitlistId;
        public int ticketId;
        public int trainId;
        public String travelClass;
        public int userId;
        public int passengerId;
        public String passengerName;
        public String pnr;
        public int waitlistNumber;

        public WaitlistEntry(int waitlistId, int ticketId, int trainId, String travelClass,
                             int userId, int passengerId, String passengerName, String pnr, int waitlistNumber) {
            this.waitlistId = waitlistId;
            this.ticketId = ticketId;
            this.trainId = trainId;
            this.travelClass = travelClass;
            this.userId = userId;
            this.passengerId = passengerId;
            this.passengerName = passengerName;
            this.pnr = pnr;
            this.waitlistNumber = waitlistNumber;
        }
    }

    public static class PromotionResult {
        public boolean promoted;
        public String passengerName;
        public String pnr;
        public String coach;
        public int seatNumber;
        public String travelClass;
        public int waitlistNumber;

        public PromotionResult(boolean promoted, String passengerName, String pnr,
                               String coach, int seatNumber, String travelClass, int waitlistNumber) {
            this.promoted = promoted;
            this.passengerName = passengerName;
            this.pnr = pnr;
            this.coach = coach;
            this.seatNumber = seatNumber;
            this.travelClass = travelClass;
            this.waitlistNumber = waitlistNumber;
        }
    }

    /**
     * Constructs a FIFO Queue of waitlisted passengers for a given train and class from MySQL.
     * Demonstrates use of Java's Queue interface and LinkedList implementation.
     */
    public static Queue<WaitlistEntry> loadWaitlistQueue(Connection conn, int trainId, String travelClass) throws SQLException {
        Queue<WaitlistEntry> queue = new LinkedList<>();

        String sql = "SELECT w.waitlist_id, w.ticket_id, w.train_id, w.travel_class, w.user_id, " +
                     "w.passenger_id, w.waitlist_number, p.name AS passenger_name, t.pnr " +
                     "FROM waitlist w " +
                     "JOIN passengers p ON w.passenger_id = p.passenger_id " +
                     "JOIN tickets t ON w.ticket_id = t.ticket_id " +
                     "WHERE w.train_id = ? AND w.travel_class = ? AND w.status = 'WAITING' " +
                     "ORDER BY w.waitlist_number ASC";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, trainId);
            stmt.setString(2, travelClass);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    queue.offer(new WaitlistEntry(
                        rs.getInt("waitlist_id"),
                        rs.getInt("ticket_id"),
                        rs.getInt("train_id"),
                        rs.getString("travel_class"),
                        rs.getInt("user_id"),
                        rs.getInt("passenger_id"),
                        rs.getString("passenger_name"),
                        rs.getString("pnr"),
                        rs.getInt("waitlist_number")
                    ));
                }
            }
        }
        return queue;
    }

    /**
     * Enqueues a passenger into the waitlist when seats are fully booked.
     */
    public static int enqueue(Connection conn, int ticketId, int trainId, String travelClass,
                               int userId, int passengerId) throws SQLException {
        int nextWlNumber = 1;
        String countSql = "SELECT COALESCE(MAX(waitlist_number), 0) + 1 FROM waitlist " +
                          "WHERE train_id = ? AND travel_class = ? AND status = 'WAITING'";
        try (PreparedStatement stmt = conn.prepareStatement(countSql)) {
            stmt.setInt(1, trainId);
            stmt.setString(2, travelClass);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    nextWlNumber = rs.getInt(1);
                }
            }
        }

        String insertSql = "INSERT INTO waitlist (ticket_id, train_id, travel_class, user_id, passenger_id, waitlist_number, status) " +
                           "VALUES (?, ?, ?, ?, ?, ?, 'WAITING')";
        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            stmt.setInt(1, ticketId);
            stmt.setInt(2, trainId);
            stmt.setString(3, travelClass);
            stmt.setInt(4, userId);
            stmt.setInt(5, passengerId);
            stmt.setInt(6, nextWlNumber);
            stmt.executeUpdate();
        }

        return nextWlNumber;
    }

    /**
     * AUTOMATIC WAITLIST PROMOTION:
     * When a confirmed seat is released, pops the first passenger from the FIFO Queue,
     * promotes them to CONFIRMED, assigns the released seat, updates MySQL, and creates a notification.
     */
    public static PromotionResult promoteFirstInQueue(Connection conn, int trainId, String travelClass,
                                                     String coach, int seatNumber, String berthType) throws SQLException {
        Queue<WaitlistEntry> queue = loadWaitlistQueue(conn, trainId, travelClass);

        if (queue.isEmpty()) {
            // No one waiting in queue
            return new PromotionResult(false, null, null, null, 0, travelClass, 0);
        }

        // FIFO poll - removes the first waiting passenger
        WaitlistEntry candidate = queue.poll();

        // 1. Update passenger record to CONFIRMED with assigned seat
        String updatePassSql = "UPDATE passengers SET coach = ?, seat_number = ?, allocated_berth = ?, status = 'CONFIRMED' " +
                               "WHERE passenger_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(updatePassSql)) {
            stmt.setString(1, coach);
            stmt.setInt(2, seatNumber);
            stmt.setString(3, berthType != null ? berthType : "ANY");
            stmt.setInt(4, candidate.passengerId);
            stmt.executeUpdate();
        }

        // 2. Mark seat as booked by the promoted passenger's PNR
        String updateSeatSql = "UPDATE seats SET is_booked = TRUE, current_pnr = ? " +
                               "WHERE train_id = ? AND travel_class = ? AND coach = ? AND seat_number = ?";
        try (PreparedStatement stmt = conn.prepareStatement(updateSeatSql)) {
            stmt.setString(1, candidate.pnr);
            stmt.setInt(2, trainId);
            stmt.setString(3, travelClass);
            stmt.setString(4, coach);
            stmt.setInt(5, seatNumber);
            stmt.executeUpdate();
        }

        // 3. Mark waitlist record as PROMOTED
        String updateWlSql = "UPDATE waitlist SET status = 'PROMOTED' WHERE waitlist_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(updateWlSql)) {
            stmt.setInt(1, candidate.waitlistId);
            stmt.executeUpdate();
        }

        // 4. Update ticket status if all its passengers are now CONFIRMED
        String checkTicketWl = "SELECT COUNT(*) FROM passengers WHERE ticket_id = ? AND status = 'WAITLIST'";
        try (PreparedStatement stmt = conn.prepareStatement(checkTicketWl)) {
            stmt.setInt(1, candidate.ticketId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    String updateTicketSql = "UPDATE tickets SET status = 'CONFIRMED' WHERE ticket_id = ?";
                    try (PreparedStatement uStmt = conn.prepareStatement(updateTicketSql)) {
                        uStmt.setInt(1, candidate.ticketId);
                        uStmt.executeUpdate();
                    }
                }
            }
        }

        // 5. Create notification for the user
        String alert = String.format("🔔 Seat Confirmed! Waitlisted passenger %s (PNR: %s) has been PROMOTED to Seat %s-%d (%s).",
                                     candidate.passengerName, candidate.pnr, coach, seatNumber, travelClass);
        Notification.create(conn, candidate.userId, alert);

        System.out.println("\n[WAITLIST PROMOTION EXECUTED]");
        System.out.printf("Passenger: %s | PNR: %s | New Seat: %s-%d (%s)\n",
                          candidate.passengerName, candidate.pnr, coach, seatNumber, travelClass);

        return new PromotionResult(true, candidate.passengerName, candidate.pnr,
                                   coach, seatNumber, travelClass, candidate.waitlistNumber);
    }
}
