package com.smarttransit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages physical seat inventory, allocation based on berth preference,
 * and seat release upon cancellation.
 */
public class Seat {
    private int seatId;
    private int trainId;
    private String travelClass;
    private String coach;
    private int seatNumber;
    private String berthType;
    private boolean isBooked;
    private String currentPnr;

    public Seat() {}

    public Seat(int seatId, int trainId, String travelClass, String coach, int seatNumber,
                String berthType, boolean isBooked, String currentPnr) {
        this.seatId = seatId;
        this.trainId = trainId;
        this.travelClass = travelClass;
        this.coach = coach;
        this.seatNumber = seatNumber;
        this.berthType = berthType;
        this.isBooked = isBooked;
        this.currentPnr = currentPnr;
    }

    // Getters and Setters
    public int getSeatId() { return seatId; }
    public void setSeatId(int seatId) { this.seatId = seatId; }

    public int getTrainId() { return trainId; }
    public void setTrainId(int trainId) { this.trainId = trainId; }

    public String getTravelClass() { return travelClass; }
    public void setTravelClass(String travelClass) { this.travelClass = travelClass; }

    public String getCoach() { return coach; }
    public void setCoach(String coach) { this.coach = coach; }

    public int getSeatNumber() { return seatNumber; }
    public void setSeatNumber(int seatNumber) { this.seatNumber = seatNumber; }

    public String getBerthType() { return berthType; }
    public void setBerthType(String berthType) { this.berthType = berthType; }

    public boolean isBooked() { return isBooked; }
    public void setBooked(boolean booked) { isBooked = booked; }

    public String getCurrentPnr() { return currentPnr; }
    public void setCurrentPnr(String currentPnr) { this.currentPnr = currentPnr; }

    /**
     * Auto-populates seats for a train if not already seeded.
     * Guarantees realistic coach codes and berth types.
     */
    public static void ensureSeatsInitialized(Connection conn, int trainId,
                                            int count1a, int count2a, int count3a, int countSl) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM seats WHERE train_id = ?";
        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setInt(1, trainId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return; // Already initialized
                }
            }
        }

        String insertSql = "INSERT INTO seats (train_id, travel_class, coach, seat_number, berth_type, is_booked) " +
                           "VALUES (?, ?, ?, ?, ?, FALSE)";
        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
            // 1A
            for (int i = 1; i <= count1a; i++) {
                String berth = (i % 2 != 0) ? "WINDOW" : "LOWER";
                insertStmt.setInt(1, trainId);
                insertStmt.setString(2, "1A");
                insertStmt.setString(3, "H1");
                insertStmt.setInt(4, i);
                insertStmt.setString(5, berth);
                insertStmt.addBatch();
            }
            // 2A
            for (int i = 1; i <= count2a; i++) {
                String berth = (i % 2 != 0) ? "LOWER" : "UPPER";
                insertStmt.setInt(1, trainId);
                insertStmt.setString(2, "2A");
                insertStmt.setString(3, "A1");
                insertStmt.setInt(4, i);
                insertStmt.setString(5, berth);
                insertStmt.addBatch();
            }
            // 3A
            String[] b3a = {"LOWER", "MIDDLE", "UPPER", "SIDE LOWER", "SIDE UPPER", "WINDOW"};
            for (int i = 1; i <= count3a; i++) {
                insertStmt.setInt(1, trainId);
                insertStmt.setString(2, "3A");
                insertStmt.setString(3, "B1");
                insertStmt.setInt(4, i);
                insertStmt.setString(5, b3a[(i - 1) % b3a.length]);
                insertStmt.addBatch();
            }
            // SL
            String[] bSl = {"LOWER", "MIDDLE", "UPPER", "SIDE LOWER", "SIDE UPPER", "WINDOW"};
            for (int i = 1; i <= countSl; i++) {
                insertStmt.setInt(1, trainId);
                insertStmt.setString(2, "SL");
                insertStmt.setString(3, "S1");
                insertStmt.setInt(4, i);
                insertStmt.setString(5, bSl[(i - 1) % bSl.length]);
                insertStmt.addBatch();
            }
            insertStmt.executeBatch();
        }
    }

    /**
     * Finds available seats count for a specific train and class.
     */
    public static int getAvailableSeatCount(Connection conn, int trainId, String travelClass) throws SQLException {
        String sql = "SELECT COUNT(*) FROM seats WHERE train_id = ? AND travel_class = ? AND is_booked = FALSE";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, trainId);
            stmt.setString(2, travelClass);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Allocates a seat considering berth preference.
     * If preferred berth is not available, allocates the next available seat.
     */
    public static Seat allocateSeatWithPreference(Connection conn, int trainId, String travelClass,
                                                  String preference, String pnr) throws SQLException {
        // Fetch all available seats for this train and class
        String query = "SELECT seat_id, train_id, travel_class, coach, seat_number, berth_type, is_booked " +
                       "FROM seats WHERE train_id = ? AND travel_class = ? AND is_booked = FALSE " +
                       "ORDER BY seat_number ASC FOR UPDATE";

        List<Seat> availableSeats = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, trainId);
            stmt.setString(2, travelClass);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    availableSeats.add(new Seat(
                        rs.getInt("seat_id"),
                        rs.getInt("train_id"),
                        rs.getString("travel_class"),
                        rs.getString("coach"),
                        rs.getInt("seat_number"),
                        rs.getString("berth_type"),
                        rs.getBoolean("is_booked"),
                        null
                    ));
                }
            }
        }

        if (availableSeats.isEmpty()) {
            return null; // All seats occupied -> Go to waitlist
        }

        Seat selectedSeat = null;
        if (preference != null && !preference.equalsIgnoreCase("ANY")) {
            for (Seat s : availableSeats) {
                if (s.getBerthType().equalsIgnoreCase(preference)) {
                    selectedSeat = s;
                    break;
                }
            }
        }

        // If preferred berth not found, fallback to first available seat
        if (selectedSeat == null) {
            selectedSeat = availableSeats.get(0);
        }

        // Update database to mark seat as booked with PNR
        String updateSql = "UPDATE seats SET is_booked = TRUE, current_pnr = ? WHERE seat_id = ?";
        try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
            updateStmt.setString(1, pnr);
            updateStmt.setInt(2, selectedSeat.getSeatId());
            updateStmt.executeUpdate();
        }

        selectedSeat.setBooked(true);
        selectedSeat.setCurrentPnr(pnr);
        return selectedSeat;
    }

    /**
     * Releases a seat back to the pool upon cancellation.
     */
    public static boolean releaseSeat(Connection conn, int trainId, String travelClass,
                                      String coach, int seatNumber) throws SQLException {
        String sql = "UPDATE seats SET is_booked = FALSE, current_pnr = NULL " +
                     "WHERE train_id = ? AND travel_class = ? AND coach = ? AND seat_number = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, trainId);
            stmt.setString(2, travelClass);
            stmt.setString(3, coach);
            stmt.setInt(4, seatNumber);
            return stmt.executeUpdate() > 0;
        }
    }
}
