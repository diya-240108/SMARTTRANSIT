package com.smarttransit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 * Encapsulates Ticket booking workflows, transaction control, unique PNR generation,
 * cancellation logic, and reservation charts.
 */
public class Ticket {
    private int ticketId;
    private String pnr;
    private int userId;
    private int trainId;
    private String travelClass;
    private String journeyDate;
    private double totalFare;
    private String status;
    private String bookingTime;

    // Joined Train metadata for E-Ticket rendering
    private String trainNumber;
    private String trainName;
    private String source;
    private String destination;
    private String departureTime;
    private String arrivalTime;
    private String trainStatus;

    private List<Passenger> passengers = new ArrayList<>();

    public Ticket() {}

    // Getters and Setters
    public int getTicketId() { return ticketId; }
    public void setTicketId(int ticketId) { this.ticketId = ticketId; }

    public String getPnr() { return pnr; }
    public void setPnr(String pnr) { this.pnr = pnr; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public int getTrainId() { return trainId; }
    public void setTrainId(int trainId) { this.trainId = trainId; }

    public String getTravelClass() { return travelClass; }
    public void setTravelClass(String travelClass) { this.travelClass = travelClass; }

    public String getJourneyDate() { return journeyDate; }
    public void setJourneyDate(String journeyDate) { this.journeyDate = journeyDate; }

    public double getTotalFare() { return totalFare; }
    public void setTotalFare(double totalFare) { this.totalFare = totalFare; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getBookingTime() { return bookingTime; }
    public void setBookingTime(String bookingTime) { this.bookingTime = bookingTime; }

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

    public String getTrainStatus() { return trainStatus; }
    public void setTrainStatus(String trainStatus) { this.trainStatus = trainStatus; }

    public List<Passenger> getPassengers() { return passengers; }
    public void setPassengers(List<Passenger> passengers) { this.passengers = passengers; }

    /**
     * Generates a unique 10-digit numeric PNR (e.g. 8457219365).
     */
    public static String generateUniquePnr(Connection conn) throws SQLException {
        Random rand = new Random();
        String candidate;
        boolean exists;

        do {
            long num = 1000000000L + (long)(rand.nextDouble() * 8999999999L);
            candidate = String.valueOf(num);

            String checkSql = "SELECT COUNT(*) FROM tickets WHERE pnr = ?";
            try (PreparedStatement stmt = conn.prepareStatement(checkSql)) {
                stmt.setString(1, candidate);
                try (ResultSet rs = stmt.executeQuery()) {
                    exists = (rs.next() && rs.getInt(1) > 0);
                }
            }
        } while (exists);

        return candidate;
    }

    /**
     * CORE BOOKING WORKFLOW (Database Transaction):
     * 1. Generates unique PNR.
     * 2. Calculates fares per passenger (including student/senior concessions).
     * 3. Allocates physical seats with preference, or falls back to Waitlist.
     * 4. Persists Ticket, Passengers, and Waitlist records atomically.
     * 5. Generates User Notification.
     */
    public static Ticket bookTicket(Connection conn, int userId, int trainId,
                                   String travelClass, List<Passenger> passengerList) throws Exception {
        if (passengerList == null || passengerList.isEmpty()) {
            throw new IllegalArgumentException("Booking must contain at least one passenger.");
        }

        // Fetch train details
        Train train = Train.getById(conn, trainId);
        if (train == null) {
            throw new IllegalArgumentException("Train not found for ID: " + trainId);
        }

        double baseFare = train.getFareForClass(travelClass);
        String pnr = generateUniquePnr(conn);

        boolean initialAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false); // Begin Transaction

            double calculatedTotalFare = 0.0;
            boolean hasWaitlistedPassenger = false;

            // Phase 1: Allocate seats and compute fares for each passenger
            for (Passenger p : passengerList) {
                FareCalculator.FareBreakdown fb = FareCalculator.calculateFare(baseFare, p.getAge(), p.getCategory());
                p.setFare(fb.finalFare);
                calculatedTotalFare += fb.finalFare;

                // Try allocating seat with preference
                Seat allocated = Seat.allocateSeatWithPreference(conn, trainId, travelClass, p.getBerthPreference(), pnr);

                if (allocated != null) {
                    p.setCoach(allocated.getCoach());
                    p.setSeatNumber(allocated.getSeatNumber());
                    p.setAllocatedBerth(allocated.getBerthType());
                    p.setStatus("CONFIRMED");
                } else {
                    // Capacity reached -> Mark as WAITLIST
                    p.setCoach("WL");
                    p.setSeatNumber(0);
                    p.setAllocatedBerth("NONE");
                    p.setStatus("WAITLIST");
                    hasWaitlistedPassenger = true;
                }
            }

            String overallTicketStatus = hasWaitlistedPassenger ? "WAITLIST" : "CONFIRMED";

            // Phase 2: Insert Ticket record
            String ticketSql = "INSERT INTO tickets (pnr, user_id, train_id, travel_class, journey_date, total_fare, status) " +
                               "VALUES (?, ?, ?, ?, ?, ?, ?)";
            int newTicketId;
            try (PreparedStatement tStmt = conn.prepareStatement(ticketSql, Statement.RETURN_GENERATED_KEYS)) {
                tStmt.setString(1, pnr);
                tStmt.setInt(2, userId);
                tStmt.setInt(3, trainId);
                tStmt.setString(4, travelClass);
                tStmt.setString(5, train.getJourneyDate());
                tStmt.setDouble(6, Math.round(calculatedTotalFare * 100.0) / 100.0);
                tStmt.setString(7, overallTicketStatus);
                tStmt.executeUpdate();

                try (ResultSet gk = tStmt.getGeneratedKeys()) {
                    if (gk.next()) {
                        newTicketId = gk.getInt(1);
                    } else {
                        throw new SQLException("Failed to retrieve generated ticket ID.");
                    }
                }
            }

            // Phase 3: Insert Passenger records & Enqueue Waitlist if applicable
            String passSql = "INSERT INTO passengers (ticket_id, name, age, gender, category, coach, " +
                             "seat_number, berth_preference, allocated_berth, fare, status) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pStmt = conn.prepareStatement(passSql, Statement.RETURN_GENERATED_KEYS)) {
                for (Passenger p : passengerList) {
                    p.setTicketId(newTicketId);
                    pStmt.setInt(1, newTicketId);
                    pStmt.setString(2, p.getName());
                    pStmt.setInt(3, p.getAge());
                    pStmt.setString(4, p.getGender());
                    pStmt.setString(5, p.getCategory());
                    pStmt.setString(6, p.getCoach());
                    pStmt.setInt(7, p.getSeatNumber());
                    pStmt.setString(8, p.getBerthPreference());
                    pStmt.setString(9, p.getAllocatedBerth());
                    pStmt.setDouble(10, p.getFare());
                    pStmt.setString(11, p.getStatus());
                    pStmt.executeUpdate();

                    try (ResultSet pGk = pStmt.getGeneratedKeys()) {
                        if (pGk.next()) {
                            int passId = pGk.getInt(1);
                            p.setPassengerId(passId);

                            if ("WAITLIST".equalsIgnoreCase(p.getStatus())) {
                                Waitlist.enqueue(conn, newTicketId, trainId, travelClass, userId, passId);
                            }
                        }
                    }
                }
            }

            // Phase 4: Create booking notification
            String notifMsg = String.format("🔔 Booking %s: Your PNR %s has been created for %s on %s. Total Fare: ₹%.2f.",
                                            overallTicketStatus, pnr, train.getTrainName(), train.getJourneyDate(), calculatedTotalFare);
            Notification.create(conn, userId, notifMsg);

            conn.commit(); // Commit Transaction

            Ticket created = getByPnr(conn, pnr);
            return created;

        } catch (Exception e) {
            Database.rollback(conn);
            throw e;
        } finally {
            conn.setAutoCommit(initialAutoCommit);
        }
    }

    public static class CancellationResult {
        public boolean success;
        public String message;
        public double refundAmount;
        public double cancellationCharge;
        public List<Waitlist.PromotionResult> promotions = new ArrayList<>();
    }

    /**
     * CANCELLATION WORKFLOW (Database Transaction):
     * 1. Verifies PNR exists and belongs to user.
     * 2. Checks that ticket is not already cancelled.
     * 3. Releases all confirmed seats back to the inventory.
     * 4. For each released seat, triggers automatic FIFO Waitlist Promotion.
     * 5. Updates ticket and passenger statuses to CANCELLED.
     * 6. Calculates refund amount.
     * 7. Generates cancellation notification.
     */
    public static CancellationResult cancelTicket(Connection conn, int requestingUserId,
                                                  boolean isAdmin, String pnr) throws Exception {
        CancellationResult res = new CancellationResult();
        Ticket ticket = getByPnr(conn, pnr);

        if (ticket == null) {
            res.success = false;
            res.message = "Ticket with PNR " + pnr + " not found.";
            return res;
        }

        if (!isAdmin && ticket.getUserId() != requestingUserId) {
            res.success = false;
            res.message = "Access Denied: Ticket does not belong to logged-in user.";
            return res;
        }

        if ("CANCELLED".equalsIgnoreCase(ticket.getStatus())) {
            res.success = false;
            res.message = "Ticket is already cancelled.";
            return res;
        }

        boolean initialAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false); // Begin Transaction

            // 1. Release seats and trigger waitlist promotion for confirmed passengers
            for (Passenger p : ticket.getPassengers()) {
                if ("CONFIRMED".equalsIgnoreCase(p.getStatus()) && p.getSeatNumber() > 0) {
                    // Release physical seat
                    Seat.releaseSeat(conn, ticket.getTrainId(), ticket.getTravelClass(), p.getCoach(), p.getSeatNumber());

                    // Promote next passenger waiting in FIFO queue
                    Waitlist.PromotionResult promo = Waitlist.promoteFirstInQueue(
                        conn, ticket.getTrainId(), ticket.getTravelClass(),
                        p.getCoach(), p.getSeatNumber(), p.getAllocatedBerth()
                    );

                    if (promo.promoted) {
                        res.promotions.add(promo);
                    }
                }
            }

            // 2. Mark passengers as CANCELLED
            String updatePassSql = "UPDATE passengers SET status = 'CANCELLED' WHERE ticket_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updatePassSql)) {
                stmt.setInt(1, ticket.getTicketId());
                stmt.executeUpdate();
            }

            // 3. Mark waitlist records if any as CANCELLED
            String updateWlSql = "UPDATE waitlist SET status = 'CANCELLED' WHERE ticket_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateWlSql)) {
                stmt.setInt(1, ticket.getTicketId());
                stmt.executeUpdate();
            }

            // 4. Mark ticket as CANCELLED
            String updateTicketSql = "UPDATE tickets SET status = 'CANCELLED' WHERE ticket_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateTicketSql)) {
                stmt.setInt(1, ticket.getTicketId());
                stmt.executeUpdate();
            }

            // 5. Calculate refund
            res.cancellationCharge = FareCalculator.CANCELLATION_CHARGE;
            res.refundAmount = FareCalculator.calculateRefund(ticket.getTotalFare());

            // 6. Send user notification
            String alert = String.format("🔔 Ticket Cancelled: PNR %s has been cancelled. Original: ₹%.2f, Fee: ₹%.2f, Refund: ₹%.2f.",
                                         ticket.getPnr(), ticket.getTotalFare(), res.cancellationCharge, res.refundAmount);
            Notification.create(conn, ticket.getUserId(), alert);

            conn.commit(); // Commit Transaction

            res.success = true;
            res.message = "Ticket cancelled successfully.";
            return res;

        } catch (Exception e) {
            Database.rollback(conn);
            throw e;
        } finally {
            conn.setAutoCommit(initialAutoCommit);
        }
    }

    /**
     * Loads full ticket details by PNR.
     */
    public static Ticket getByPnr(Connection conn, String pnr) throws SQLException {
        String sql = "SELECT t.ticket_id, t.pnr, t.user_id, t.train_id, t.travel_class, t.journey_date, " +
                     "t.total_fare, t.status, t.booking_time, tr.train_number, tr.train_name, " +
                     "tr.source, tr.destination, tr.departure_time, tr.arrival_time, tr.status AS train_status " +
                     "FROM tickets t " +
                     "JOIN trains tr ON t.train_id = tr.train_id " +
                     "WHERE t.pnr = ?";

        Ticket ticket = null;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, pnr.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ticket = new Ticket();
                    ticket.setTicketId(rs.getInt("ticket_id"));
                    ticket.setPnr(rs.getString("pnr"));
                    ticket.setUserId(rs.getInt("user_id"));
                    ticket.setTrainId(rs.getInt("train_id"));
                    ticket.setTravelClass(rs.getString("travel_class"));
                    ticket.setJourneyDate(rs.getString("journey_date"));
                    ticket.setTotalFare(rs.getDouble("total_fare"));
                    ticket.setStatus(rs.getString("status"));
                    ticket.setBookingTime(rs.getString("booking_time"));
                    ticket.setTrainNumber(rs.getString("train_number"));
                    ticket.setTrainName(rs.getString("train_name"));
                    ticket.setSource(rs.getString("source"));
                    ticket.setDestination(rs.getString("destination"));
                    ticket.setDepartureTime(rs.getString("departure_time"));
                    ticket.setArrivalTime(rs.getString("arrival_time"));
                    ticket.setTrainStatus(rs.getString("train_status"));
                }
            }
        }

        if (ticket != null) {
            loadPassengers(conn, ticket);
        }

        return ticket;
    }

    /**
     * Loads list of passengers for a given ticket.
     */
    private static void loadPassengers(Connection conn, Ticket ticket) throws SQLException {
        String passSql = "SELECT * FROM passengers WHERE ticket_id = ? ORDER BY passenger_id ASC";
        try (PreparedStatement stmt = conn.prepareStatement(passSql)) {
            stmt.setInt(1, ticket.getTicketId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Passenger p = new Passenger();
                    p.setPassengerId(rs.getInt("passenger_id"));
                    p.setTicketId(rs.getInt("ticket_id"));
                    p.setName(rs.getString("name"));
                    p.setAge(rs.getInt("age"));
                    p.setGender(rs.getString("gender"));
                    p.setCategory(rs.getString("category"));
                    p.setCoach(rs.getString("coach"));
                    p.setSeatNumber(rs.getInt("seat_number"));
                    p.setBerthPreference(rs.getString("berth_preference"));
                    p.setAllocatedBerth(rs.getString("allocated_berth"));
                    p.setFare(rs.getDouble("fare"));
                    p.setStatus(rs.getString("status"));
                    ticket.getPassengers().add(p);
                }
            }
        }
    }

    /**
     * Retrieves all bookings made by a specific user.
     */
    public static List<Ticket> getBookingsByUser(Connection conn, int userId) throws SQLException {
        List<Ticket> list = new ArrayList<>();
        String sql = "SELECT pnr FROM tickets WHERE user_id = ? ORDER BY booking_time DESC";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Ticket t = getByPnr(conn, rs.getString("pnr"));
                    if (t != null) list.add(t);
                }
            }
        }
        return list;
    }

    /**
     * Retrieves all bookings in the system (for Admin).
     */
    public static List<Ticket> getAllBookings(Connection conn, String statusFilter) throws SQLException {
        List<Ticket> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT pnr FROM tickets");
        if (statusFilter != null && !statusFilter.equalsIgnoreCase("ALL")) {
            sql.append(" WHERE status = ?");
        }
        sql.append(" ORDER BY booking_time DESC");

        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            if (statusFilter != null && !statusFilter.equalsIgnoreCase("ALL")) {
                stmt.setString(1, statusFilter.toUpperCase());
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Ticket t = getByPnr(conn, rs.getString("pnr"));
                    if (t != null) list.add(t);
                }
            }
        }
        return list;
    }

    public static class ChartEntry {
        public String trainNumber;
        public String trainName;
        public String coach;
        public int seatNumber;
        public String passengerName;
        public String pnr;
        public String status;
        public String travelClass;
        public String berthType;
    }

    /**
     * Generates the complete Reservation Chart for a train.
     */
    public static List<ChartEntry> getReservationChart(Connection conn, int trainId) throws SQLException {
        List<ChartEntry> chart = new ArrayList<>();
        String sql = "SELECT tr.train_number, tr.train_name, p.coach, p.seat_number, p.name AS passenger_name, " +
                     "t.pnr, p.status, t.travel_class, p.allocated_berth " +
                     "FROM passengers p " +
                     "JOIN tickets t ON p.ticket_id = t.ticket_id " +
                     "JOIN trains tr ON t.train_id = tr.train_id " +
                     "WHERE t.train_id = ? " +
                     "ORDER BY p.coach ASC, p.seat_number ASC";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, trainId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ChartEntry e = new ChartEntry();
                    e.trainNumber = rs.getString("train_number");
                    e.trainName = rs.getString("train_name");
                    e.coach = rs.getString("coach");
                    e.seatNumber = rs.getInt("seat_number");
                    e.passengerName = rs.getString("passenger_name");
                    e.pnr = rs.getString("pnr");
                    e.status = rs.getString("status");
                    e.travelClass = rs.getString("travel_class");
                    e.berthType = rs.getString("allocated_berth");
                    chart.add(e);
                }
            }
        }
        return chart;
    }

    /**
     * Generates ASCII formatted E-Ticket string as requested in Requirement 11.
     */
    public static String formatETicketAscii(Ticket t) {
        StringBuilder sb = new StringBuilder();
        sb.append("========================================\n");
        sb.append("             E-TICKET                   \n");
        sb.append("========================================\n\n");
        sb.append(String.format("PNR             : %s\n\n", t.getPnr()));
        
        String passName = t.getPassengers().isEmpty() ? "Passenger" : t.getPassengers().get(0).getName();
        sb.append(String.format("Passenger       : %s\n", passName));
        sb.append(String.format("Train           : %s - %s\n\n", t.getTrainNumber(), t.getTrainName()));
        sb.append(String.format("From            : %s\n", t.getSource()));
        sb.append(String.format("To              : %s\n\n", t.getDestination()));
        sb.append(String.format("Journey Date    : %s\n\n", t.getJourneyDate()));

        String coach = t.getPassengers().isEmpty() ? "WL" : t.getPassengers().get(0).getCoach();
        int seat = t.getPassengers().isEmpty() ? 0 : t.getPassengers().get(0).getSeatNumber();
        sb.append(String.format("Coach           : %s\n", coach));
        sb.append(String.format("Seat            : %s\n", seat > 0 ? String.valueOf(seat) : "WL"));
        sb.append(String.format("Class           : %s\n\n", t.getTravelClass()));
        sb.append(String.format("Status          : %s\n\n", t.getStatus()));
        sb.append(String.format("Fare            : ₹%.2f\n\n", t.getTotalFare()));
        sb.append(String.format("Booked On       : %s\n", t.getBookingTime()));
        sb.append("========================================\n");
        return sb.toString();
    }
}
