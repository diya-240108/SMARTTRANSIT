package com.smarttransit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles Administrator authentication, user oversight, reservation charts,
 * train fleet management, and system-wide analytical reports.
 */
public class Admin {
    private int adminId;
    private String username;
    private String fullName;
    private String email;

    public Admin() {}

    public Admin(int adminId, String username, String fullName, String email) {
        this.adminId = adminId;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
    }

    public int getAdminId() { return adminId; }
    public void setAdminId(int adminId) { this.adminId = adminId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    /**
     * Authenticates administrator credentials against the `admins` table.
     */
    public static Admin login(Connection conn, String username, String password) throws SQLException {
        if (username == null || password == null) return null;

        String sql = "SELECT admin_id, username, full_name, email FROM admins WHERE LOWER(username) = LOWER(?) AND password = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username.trim());
            stmt.setString(2, password.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Admin(
                        rs.getInt("admin_id"),
                        rs.getString("username"),
                        rs.getString("full_name"),
                        rs.getString("email")
                    );
                }
            }
        }
        return null;
    }

    /**
     * Lists registered users (passwords omitted for security).
     */
    public static List<User> getRegisteredUsers(Connection conn, String searchKeyword) throws SQLException {
        List<User> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT user_id, username, full_name, email, phone, category, created_at FROM users"
        );
        if (searchKeyword != null && !searchKeyword.trim().isEmpty()) {
            sql.append(" WHERE LOWER(username) LIKE LOWER(?) OR LOWER(full_name) LIKE LOWER(?) OR LOWER(email) LIKE LOWER(?)");
        }
        sql.append(" ORDER BY user_id DESC");

        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            if (searchKeyword != null && !searchKeyword.trim().isEmpty()) {
                String kw = "%" + searchKeyword.trim() + "%";
                stmt.setString(1, kw);
                stmt.setString(2, kw);
                stmt.setString(3, kw);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new User(
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getString("phone"),
                        rs.getString("category"),
                        rs.getString("created_at")
                    ));
                }
            }
        }
        return list;
    }

    public static class TrainBookingReport {
        public String trainNumber;
        public String trainName;
        public String route;
        public int bookingCount;
        public double revenue;
        public int totalCapacity;
        public int bookedSeats;
        public double occupancyPercent;
    }

    public static class MostBookedRoute {
        public String source;
        public String destination;
        public int bookingCount;
        public double totalRevenue;
    }

    public static class ClassAvailabilitySummary {
        public String travelClass;
        public int totalCapacity;
        public int bookedSeats;
        public int availableSeats;
        public double occupancyPercent;
    }

    public static class SystemReport {
        public int totalUsers;
        public int totalTrains;
        public int totalBookings;
        public int confirmedTickets;
        public int cancelledTickets;
        public int waitlistedPassengers;
        public double totalRevenue;
        public int fleetTotalCapacity;
        public int fleetBookedSeats;
        public double overallOccupancyRate;
        public List<TrainBookingReport> trainWiseBookings = new ArrayList<>();
        public List<MostBookedRoute> mostBookedRoutes = new ArrayList<>();
        public List<ClassAvailabilitySummary> classAvailability = new ArrayList<>();
    }

    /**
     * Aggregates real database-backed analytical data across users, trains, bookings,
     * waitlist, train occupancy, most-booked routes, and class-wise availability.
     */
    public static SystemReport generateReport(Connection conn) throws SQLException {
        SystemReport r = new SystemReport();

        // 1. Total Users
        try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM users");
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) r.totalUsers = rs.getInt(1);
        }

        // 2. Total Trains
        try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM trains");
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) r.totalTrains = rs.getInt(1);
        }

        // 3. Tickets breakdown & Revenue
        String ticketQuery = "SELECT status, COUNT(*), COALESCE(SUM(total_fare), 0) FROM tickets GROUP BY status";
        try (PreparedStatement stmt = conn.prepareStatement(ticketQuery);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String st = rs.getString(1);
                int count = rs.getInt(2);
                double sum = rs.getDouble(3);

                r.totalBookings += count;
                if ("CONFIRMED".equalsIgnoreCase(st)) {
                    r.confirmedTickets = count;
                    r.totalRevenue += sum;
                } else if ("CANCELLED".equalsIgnoreCase(st)) {
                    r.cancelledTickets = count;
                    // Retain cancellation fee ₹100 per cancelled ticket in revenue
                    r.totalRevenue += (count * FareCalculator.CANCELLATION_CHARGE);
                } else if ("WAITLIST".equalsIgnoreCase(st)) {
                    r.totalRevenue += sum;
                }
            }
        }

        // 4. Waitlisted Passengers currently waiting
        try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM waitlist WHERE status = 'WAITING'");
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) r.waitlistedPassengers = rs.getInt(1);
        }

        // 5. Train-wise booking metrics & Occupancy
        String trainWiseSql = "SELECT tr.train_id, tr.train_number, tr.train_name, tr.source, tr.destination, " +
                              "(tr.seats_1a + tr.seats_2a + tr.seats_3a + tr.seats_sl) AS total_cap, " +
                              "COUNT(t.ticket_id) AS cnt, " +
                              "COALESCE(SUM(CASE WHEN t.status != 'CANCELLED' THEN t.total_fare ELSE 100.0 END), 0) AS rev, " +
                              "(SELECT COUNT(*) FROM seats s WHERE s.train_id = tr.train_id AND s.is_booked = TRUE) AS booked_count " +
                              "FROM trains tr " +
                              "LEFT JOIN tickets t ON tr.train_id = t.train_id " +
                              "GROUP BY tr.train_id, tr.train_number, tr.train_name, tr.source, tr.destination, total_cap " +
                              "ORDER BY cnt DESC";

        try (PreparedStatement stmt = conn.prepareStatement(trainWiseSql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                TrainBookingReport tbr = new TrainBookingReport();
                tbr.trainNumber = rs.getString("train_number");
                tbr.trainName = rs.getString("train_name");
                tbr.route = rs.getString("source") + " → " + rs.getString("destination");
                tbr.bookingCount = rs.getInt("cnt");
                tbr.revenue = Math.round(rs.getDouble("rev") * 100.0) / 100.0;
                tbr.totalCapacity = rs.getInt("total_cap");
                tbr.bookedSeats = rs.getInt("booked_count");
                tbr.occupancyPercent = (tbr.totalCapacity > 0)
                        ? Math.round(((double) tbr.bookedSeats / tbr.totalCapacity) * 10000.0) / 100.0
                        : 0.0;

                r.fleetTotalCapacity += tbr.totalCapacity;
                r.fleetBookedSeats += tbr.bookedSeats;
                r.trainWiseBookings.add(tbr);
            }
        }

        if (r.fleetTotalCapacity > 0) {
            r.overallOccupancyRate = Math.round(((double) r.fleetBookedSeats / r.fleetTotalCapacity) * 10000.0) / 100.0;
        }

        // 6. Most-Booked Routes
        String routeSql = "SELECT tr.source, tr.destination, COUNT(t.ticket_id) AS route_bookings, " +
                          "COALESCE(SUM(CASE WHEN t.status != 'CANCELLED' THEN t.total_fare ELSE 100.0 END), 0) AS route_rev " +
                          "FROM trains tr " +
                          "JOIN tickets t ON tr.train_id = t.train_id " +
                          "GROUP BY tr.source, tr.destination " +
                          "ORDER BY route_bookings DESC LIMIT 5";
        try (PreparedStatement stmt = conn.prepareStatement(routeSql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                MostBookedRoute mbr = new MostBookedRoute();
                mbr.source = rs.getString("source");
                mbr.destination = rs.getString("destination");
                mbr.bookingCount = rs.getInt("route_bookings");
                mbr.totalRevenue = Math.round(rs.getDouble("route_rev") * 100.0) / 100.0;
                r.mostBookedRoutes.add(mbr);
            }
        }

        // 7. Fleet-wide Class-Wise Availability
        String classSql = "SELECT travel_class, COUNT(*) AS total_s, " +
                          "SUM(CASE WHEN is_booked = TRUE THEN 1 ELSE 0 END) AS booked_s, " +
                          "SUM(CASE WHEN is_booked = FALSE THEN 1 ELSE 0 END) AS avail_s " +
                          "FROM seats " +
                          "GROUP BY travel_class " +
                          "ORDER BY FIELD(travel_class, '1A', '2A', '3A', 'SL')";
        try (PreparedStatement stmt = conn.prepareStatement(classSql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                ClassAvailabilitySummary cas = new ClassAvailabilitySummary();
                cas.travelClass = rs.getString("travel_class");
                cas.totalCapacity = rs.getInt("total_s");
                cas.bookedSeats = rs.getInt("booked_s");
                cas.availableSeats = rs.getInt("avail_s");
                cas.occupancyPercent = (cas.totalCapacity > 0)
                        ? Math.round(((double) cas.bookedSeats / cas.totalCapacity) * 10000.0) / 100.0
                        : 0.0;
                r.classAvailability.add(cas);
            }
        }

        r.totalRevenue = Math.round(r.totalRevenue * 100.0) / 100.0;
        return r;
    }
}
