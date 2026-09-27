package com.smarttransit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents train metadata, seat capacities, fares, search queries,
 * and the Smart Alternative Train Recommendation Engine.
 */
public class Train {
    private int trainId;
    private String trainNumber;
    private String trainName;
    private String source;
    private String destination;
    private String departureTime;
    private String arrivalTime;
    private String journeyDate;
    private int seats1a;
    private int seats2a;
    private int seats3a;
    private int seatsSl;
    private double fare1a;
    private double fare2a;
    private double fare3a;
    private double fareSl;
    private String status;
    private String delayInfo;

    // Real-time class-wise available seats
    private int avail1a;
    private int avail2a;
    private int avail3a;
    private int availSl;

    public Train() {
        this.status = "ON TIME";
        this.delayInfo = "On schedule";
    }

    public Train(int trainId, String trainNumber, String trainName, String source,
                 String destination, String departureTime, String arrivalTime,
                 String journeyDate, int seats1a, int seats2a, int seats3a, int seatsSl,
                 double fare1a, double fare2a, double fare3a, double fareSl,
                 String status, String delayInfo) {
        this.trainId = trainId;
        this.trainNumber = trainNumber;
        this.trainName = trainName;
        this.source = source;
        this.destination = destination;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.journeyDate = journeyDate;
        this.seats1a = seats1a;
        this.seats2a = seats2a;
        this.seats3a = seats3a;
        this.seatsSl = seatsSl;
        this.fare1a = fare1a;
        this.fare2a = fare2a;
        this.fare3a = fare3a;
        this.fareSl = fareSl;
        this.status = (status != null) ? status : "ON TIME";
        this.delayInfo = (delayInfo != null) ? delayInfo : "On schedule";
    }

    // Getters and Setters
    public int getTrainId() { return trainId; }
    public void setTrainId(int trainId) { this.trainId = trainId; }

    public String getTrainNumber() { return trainNumber; }
    public void setTrainNumber(String trainNumber) { this.trainNumber = trainNumber; }

    public String getTrainName() { return trainName; }
    public void setTrainName(String trainName) { this.trainName = trainName; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public String getDepartureTime() { return departureTime; }
    public void setDepartureTime(String departureTime) { this.departureTime = departureTime; }

    public String getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(String arrivalTime) { this.arrivalTime = arrivalTime; }

    public String getJourneyDate() { return journeyDate; }
    public void setJourneyDate(String journeyDate) { this.journeyDate = journeyDate; }

    public int getSeats1a() { return seats1a; }
    public void setSeats1a(int seats1a) { this.seats1a = seats1a; }

    public int getSeats2a() { return seats2a; }
    public void setSeats2a(int seats2a) { this.seats2a = seats2a; }

    public int getSeats3a() { return seats3a; }
    public void setSeats3a(int seats3a) { this.seats3a = seats3a; }

    public int getSeatsSl() { return seatsSl; }
    public void setSeatsSl(int seatsSl) { this.seatsSl = seatsSl; }

    public double getFare1a() { return fare1a; }
    public void setFare1a(double fare1a) { this.fare1a = fare1a; }

    public double getFare2a() { return fare2a; }
    public void setFare2a(double fare2a) { this.fare2a = fare2a; }

    public double getFare3a() { return fare3a; }
    public void setFare3a(double fare3a) { this.fare3a = fare3a; }

    public double getFareSl() { return fareSl; }
    public void setFareSl(double fareSl) { this.fareSl = fareSl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDelayInfo() { return delayInfo; }
    public void setDelayInfo(String delayInfo) { this.delayInfo = delayInfo; }

    public int getAvail1a() { return avail1a; }
    public void setAvail1a(int avail1a) { this.avail1a = avail1a; }

    public int getAvail2a() { return avail2a; }
    public void setAvail2a(int avail2a) { this.avail2a = avail2a; }

    public int getAvail3a() { return avail3a; }
    public void setAvail3a(int avail3a) { this.avail3a = avail3a; }

    public int getAvailSl() { return availSl; }
    public void setAvailSl(int availSl) { this.availSl = availSl; }

    public double getFareForClass(String travelClass) {
        if ("1A".equalsIgnoreCase(travelClass)) return fare1a;
        if ("2A".equalsIgnoreCase(travelClass)) return fare2a;
        if ("3A".equalsIgnoreCase(travelClass)) return fare3a;
        return fareSl;
    }

    public int getAvailabilityForClass(String travelClass) {
        if ("1A".equalsIgnoreCase(travelClass)) return avail1a;
        if ("2A".equalsIgnoreCase(travelClass)) return avail2a;
        if ("3A".equalsIgnoreCase(travelClass)) return avail3a;
        return availSl;
    }

    /**
     * Extracts a Train from current row in ResultSet.
     */
    public static Train fromResultSet(ResultSet rs) throws SQLException {
        Train t = new Train(
            rs.getInt("train_id"),
            rs.getString("train_number"),
            rs.getString("train_name"),
            rs.getString("source"),
            rs.getString("destination"),
            rs.getString("departure_time"),
            rs.getString("arrival_time"),
            rs.getString("journey_date"),
            rs.getInt("seats_1a"),
            rs.getInt("seats_2a"),
            rs.getInt("seats_3a"),
            rs.getInt("seats_sl"),
            rs.getDouble("fare_1a"),
            rs.getDouble("fare_2a"),
            rs.getDouble("fare_3a"),
            rs.getDouble("fare_sl"),
            rs.getString("status"),
            rs.getString("delay_info")
        );
        return t;
    }

    /**
     * Enriches a train with live seat counts from the `seats` table.
     */
    public void loadLiveSeatAvailability(Connection conn) throws SQLException {
        // Ensure physical seats exist in database
        Seat.ensureSeatsInitialized(conn, this.trainId, this.seats1a, this.seats2a, this.seats3a, this.seatsSl);
        this.avail1a = Seat.getAvailableSeatCount(conn, this.trainId, "1A");
        this.avail2a = Seat.getAvailableSeatCount(conn, this.trainId, "2A");
        this.avail3a = Seat.getAvailableSeatCount(conn, this.trainId, "3A");
        this.availSl = Seat.getAvailableSeatCount(conn, this.trainId, "SL");
    }

    /**
     * Searches trains between source and destination on a given date.
     */
    public static List<Train> searchTrains(Connection conn, String source, String dest, String date) throws SQLException {
        List<Train> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM trains WHERE LOWER(source) LIKE LOWER(?) AND LOWER(destination) LIKE LOWER(?)");
        if (date != null && !date.trim().isEmpty()) {
            sql.append(" AND journey_date = ?");
        }
        sql.append(" ORDER BY departure_time ASC");

        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            stmt.setString(1, "%" + source.trim() + "%");
            stmt.setString(2, "%" + dest.trim() + "%");
            if (date != null && !date.trim().isEmpty()) {
                stmt.setString(3, date.trim());
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Train t = fromResultSet(rs);
                    t.loadLiveSeatAvailability(conn);
                    list.add(t);
                }
            }
        }
        return list;
    }

    /**
     * Retrieves a train by its primary key ID.
     */
    public static Train getById(Connection conn, int trainId) throws SQLException {
        String sql = "SELECT * FROM trains WHERE train_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, trainId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Train t = fromResultSet(rs);
                    t.loadLiveSeatAvailability(conn);
                    return t;
                }
            }
        }
        return null;
    }

    /**
     * Retrieves train details by train number (Train Enquiry).
     */
    public static Train getByNumber(Connection conn, String trainNumber) throws SQLException {
        String sql = "SELECT * FROM trains WHERE train_number = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, trainNumber.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Train t = fromResultSet(rs);
                    t.loadLiveSeatAvailability(conn);
                    return t;
                }
            }
        }
        return null;
    }

    /**
     * Retrieves all trains in the system (for Admin dashboard).
     */
    public static List<Train> getAllTrains(Connection conn) throws SQLException {
        List<Train> list = new ArrayList<>();
        String sql = "SELECT * FROM trains ORDER BY train_id ASC";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Train t = fromResultSet(rs);
                t.loadLiveSeatAvailability(conn);
                list.add(t);
            }
        }
        return list;
    }

    /**
     * Adds a new train into the database and generates its seat inventory.
     */
    public static int addTrain(Connection conn, Train t) throws SQLException {
        String sql = "INSERT INTO trains (train_number, train_name, source, destination, departure_time, " +
                     "arrival_time, journey_date, seats_1a, seats_2a, seats_3a, seats_sl, " +
                     "fare_1a, fare_2a, fare_3a, fare_sl, status, delay_info) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, t.getTrainNumber());
            stmt.setString(2, t.getTrainName());
            stmt.setString(3, t.getSource());
            stmt.setString(4, t.getDestination());
            stmt.setString(5, t.getDepartureTime());
            stmt.setString(6, t.getArrivalTime());
            stmt.setString(7, t.getJourneyDate());
            stmt.setInt(8, t.getSeats1a());
            stmt.setInt(9, t.getSeats2a());
            stmt.setInt(10, t.getSeats3a());
            stmt.setInt(11, t.getSeatsSl());
            stmt.setDouble(12, t.getFare1a());
            stmt.setDouble(13, t.getFare2a());
            stmt.setDouble(14, t.getFare3a());
            stmt.setDouble(15, t.getFareSl());
            stmt.setString(16, t.getStatus());
            stmt.setString(17, t.getDelayInfo());

            stmt.executeUpdate();
            try (ResultSet gk = stmt.getGeneratedKeys()) {
                if (gk.next()) {
                    int newId = gk.getInt(1);
                    t.setTrainId(newId);
                    // Initialize seats inventory
                    Seat.ensureSeatsInitialized(conn, newId, t.getSeats1a(), t.getSeats2a(), t.getSeats3a(), t.getSeatsSl());
                    return newId;
                }
            }
        }
        return -1;
    }

    /**
     * Updates train schedule, route, capacity, and fares.
     */
    public static boolean updateTrain(Connection conn, Train t) throws SQLException {
        String sql = "UPDATE trains SET train_name = ?, source = ?, destination = ?, departure_time = ?, " +
                     "arrival_time = ?, journey_date = ?, fare_1a = ?, fare_2a = ?, fare_3a = ?, fare_sl = ? " +
                     "WHERE train_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, t.getTrainName());
            stmt.setString(2, t.getSource());
            stmt.setString(3, t.getDestination());
            stmt.setString(4, t.getDepartureTime());
            stmt.setString(5, t.getArrivalTime());
            stmt.setString(6, t.getJourneyDate());
            stmt.setDouble(7, t.getFare1a());
            stmt.setDouble(8, t.getFare2a());
            stmt.setDouble(9, t.getFare3a());
            stmt.setDouble(10, t.getFareSl());
            stmt.setInt(11, t.getTrainId());
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Updates train status (ON TIME, DELAYED, CANCELLED) and automatically notifies booked passengers.
     */
    public static boolean updateStatus(Connection conn, int trainId, String status, String delayInfo) throws SQLException {
        String sql = "UPDATE trains SET status = ?, delay_info = ? WHERE train_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setString(2, delayInfo);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                // Generate notifications for passengers booked on this train
                Notification.notifyPassengersOfTrainStatus(conn, trainId, status, delayInfo);
                return true;
            }
        }
        return false;
    }

    /**
     * SMART ALTERNATIVE TRAIN RECOMMENDATION ENGINE:
     * When the requested train is full, cancelled, or unavailable:
     * 1. Matches source, destination, and journey date.
     * 2. Filters trains with confirmed seat availability (>0 seats).
     * 3. Ranks candidates using a rule-based Java algorithm (no AI/ML required):
     *    - Primary Rule: Highest seat availability.
     *    - Secondary Rule: Most economical sleeper fare.
     *    - Tertiary Rule: Departure time schedule.
     */
    public static List<Train> findAlternativeTrains(Connection conn, String source, String dest,
                                                    String date, int excludeTrainId) throws SQLException {
        List<Train> alternatives = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT * FROM trains WHERE LOWER(source) LIKE LOWER(?) AND LOWER(destination) LIKE LOWER(?) " +
            "AND train_id != ? AND status != 'CANCELLED'"
        );
        if (date != null && !date.trim().isEmpty()) {
            sql.append(" AND journey_date = ?");
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            stmt.setString(1, "%" + source.trim() + "%");
            stmt.setString(2, "%" + dest.trim() + "%");
            stmt.setInt(3, excludeTrainId);
            if (date != null && !date.trim().isEmpty()) {
                stmt.setString(4, date.trim());
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Train t = fromResultSet(rs);
                    t.loadLiveSeatAvailability(conn);
                    int totalAvail = t.getAvail1a() + t.getAvail2a() + t.getAvail3a() + t.getAvailSl();
                    // Candidate must have available seats in at least one class
                    if (totalAvail > 0) {
                        alternatives.add(t);
                    }
                }
            }
        }

        // Rule-based ranking in Java (no AI/ML required)
        alternatives.sort((t1, t2) -> {
            int avail1 = t1.getAvail1a() + t1.getAvail2a() + t1.getAvail3a() + t1.getAvailSl();
            int avail2 = t2.getAvail1a() + t2.getAvail2a() + t2.getAvail3a() + t2.getAvailSl();
            if (avail1 != avail2) {
                return Integer.compare(avail2, avail1); // Higher availability first
            }
            int fareComp = Double.compare(t1.getFareSl(), t2.getFareSl()); // Economical fare first
            if (fareComp != 0) {
                return fareComp;
            }
            return t1.getDepartureTime().compareToIgnoreCase(t2.getDepartureTime()); // Earlier departure
        });

        if (alternatives.size() > 3) {
            return new ArrayList<>(alternatives.subList(0, 3));
        }
        return alternatives;
    }
}
