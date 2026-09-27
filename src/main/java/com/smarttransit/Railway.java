package com.smarttransit;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main Controller and Embedded HTTP Application Server for the Smart Transit System.
 * Hosts static frontend assets and provides a complete RESTful JSON API.
 */
public class Railway {

    private static final int PORT = 8080;
    private static final Gson gson = new Gson();
    private static String frontendDir = "frontend";

    public static void main(String[] args) {
        printBanner();

        // 1. Verify Database Connectivity
        System.out.println("[DB] Testing connection to MySQL database 'railway'...");
        try (Connection conn = Database.getConnection()) {
            System.out.println("[DB] Successfully connected to MySQL database: " + Database.getDbName());
        } catch (SQLException e) {
            System.err.println("\n[DB ERROR] Could not connect to MySQL: " + e.getMessage());
            System.err.println("Ensure MySQL service is running and 'railway.sql' has been executed.");
            System.err.println("Default credentials tried: user=" + Database.getDbUser() + ", pass=root/empty.");
            System.out.println("[NOTICE] Proceeding with server launch; database endpoints will retry upon request.\n");
        }

        // Determine path to frontend folder (works both in project root and parent)
        File fDir = new File("frontend");
        if (!fDir.exists()) {
            fDir = new File("C:/Users/HP/.gemini/antigravity/scratch/SmartTransit/frontend");
        }
        if (fDir.exists()) {
            frontendDir = fDir.getAbsolutePath();
        }

        // 2. Start Built-in Java HTTP Server
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

            // Register API Handlers
            server.createContext("/api/auth/login", new LoginHandler());
            server.createContext("/api/auth/register", new RegisterHandler());
            server.createContext("/api/user/dashboard", new UserDashboardHandler());
            server.createContext("/api/trains/search", new TrainSearchHandler());
            server.createContext("/api/trains/alternatives", new TrainAlternativesHandler());
            server.createContext("/api/trains/get", new TrainGetHandler());
            server.createContext("/api/trains/enquiry", new TrainEnquiryHandler());
            server.createContext("/api/bookings/book", new BookingHandler());
            server.createContext("/api/bookings/ticket", new TicketGetHandler());
            server.createContext("/api/bookings/user", new UserBookingsHandler());
            server.createContext("/api/bookings/cancel", new CancelBookingHandler());
            server.createContext("/api/notifications", new NotificationsHandler());
            server.createContext("/api/notifications/read-all", new MarkReadNotificationsHandler());

            // Admin API Handlers
            server.createContext("/api/admin/reports", new AdminReportsHandler());
            server.createContext("/api/admin/trains", new AdminTrainsHandler());
            server.createContext("/api/admin/trains/add", new AdminAddTrainHandler());
            server.createContext("/api/admin/trains/update", new AdminUpdateTrainHandler());
            server.createContext("/api/admin/trains/status", new AdminTrainStatusHandler());
            server.createContext("/api/admin/users", new AdminUsersHandler());
            server.createContext("/api/admin/chart", new AdminChartHandler());
            server.createContext("/api/admin/bookings", new AdminBookingsHandler());

            // Static Web Server Handler for Frontend HTML/CSS/JS
            server.createContext("/", new StaticFileHandler(frontendDir));

            server.setExecutor(null); // default multithreaded executor
            server.start();

            System.out.println("==================================================================");
            System.out.println("  Smart Transit Management Server is ACTIVE and RUNNING!");
            System.out.println("  PORT: " + PORT);
            System.out.println("  LOCAL URL: http://localhost:" + PORT + "/index.html");
            System.out.println("  Serving frontend from: " + frontendDir);
            System.out.println("==================================================================");

        } catch (IOException e) {
            System.err.println("[SERVER ERROR] Failed to start HTTP server on port " + PORT + ": " + e.getMessage());
        }
    }

    private static void printBanner() {
        System.out.println("""
        ==================================================================
           SMART TRANSIT MANAGEMENT SYSTEM / RAILWAY RESERVATION PORTAL   
                  Java 21 | Core OOP | JDBC | MySQL | REST Engine         
        ==================================================================
        """);
    }

    // ===================================================================
    // HTTP UTILITY HELPERS
    // ===================================================================

    private static void setCorsHeaders(HttpExchange exchange) {
        Headers h = exchange.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE");
        h.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, Object obj) throws IOException {
        setCorsHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        String json = gson.toJson(obj);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> err = new HashMap<>();
        err.put("success", false);
        err.put("error", message);
        sendJsonResponse(exchange, statusCode, err);
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[1024];
            int read;
            while ((read = reader.read(buf)) != -1) {
                sb.append(buf, 0, read);
            }
            return sb.toString();
        }
    }

    private static Map<String, String> parseQueryParams(URI uri) {
        Map<String, String> params = new HashMap<>();
        String query = uri.getQuery();
        if (query == null || query.isEmpty()) return params;

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0 && idx < pair.length() - 1) {
                try {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    String val = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    params.put(key, val);
                } catch (Exception ignored) {}
            }
        }
        return params;
    }

    // ===================================================================
    // REST API HANDLERS
    // ===================================================================

    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }

            try (Connection conn = Database.getConnection()) {
                String body = readRequestBody(exchange);
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                String username = json.get("username").getAsString();
                String password = json.get("password").getAsString();
                String role = json.has("role") ? json.get("role").getAsString() : "USER";

                Map<String, Object> resp = new HashMap<>();
                if ("ADMIN".equalsIgnoreCase(role)) {
                    Admin admin = Admin.login(conn, username, password);
                    if (admin != null) {
                        resp.put("success", true);
                        resp.put("role", "ADMIN");
                        resp.put("admin", admin);
                        sendJsonResponse(exchange, 200, resp);
                        return;
                    }
                } else {
                    User user = User.login(conn, username, password);
                    if (user != null) {
                        resp.put("success", true);
                        resp.put("role", "USER");
                        resp.put("user", user);
                        sendJsonResponse(exchange, 200, resp);
                        return;
                    }
                }
                sendError(exchange, 401, "Invalid username or password.");
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    static class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }

            try (Connection conn = Database.getConnection()) {
                String body = readRequestBody(exchange);
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                String username = json.get("username").getAsString();
                String password = json.get("password").getAsString();
                String fullName = json.get("fullName").getAsString();
                String email = json.get("email").getAsString();
                String phone = json.get("phone").getAsString();
                String category = json.has("category") ? json.get("category").getAsString() : "NORMAL";

                User user = User.register(conn, username, password, fullName, email, phone, category);
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", true);
                resp.put("user", user);
                sendJsonResponse(exchange, 200, resp);
            } catch (IllegalArgumentException e) {
                sendError(exchange, 400, e.getMessage());
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    static class UserDashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try (Connection conn = Database.getConnection()) {
                Map<String, String> q = parseQueryParams(exchange.getRequestURI());
                int userId = Integer.parseInt(q.getOrDefault("userId", "0"));
                User.DashboardStats stats = User.getDashboardStats(conn, userId);
                List<Ticket> recent = Ticket.getBookingsByUser(conn, userId);
                if (recent.size() > 5) recent = recent.subList(0, 5);

                Map<String, Object> resp = new HashMap<>();
                resp.put("success", true);
                resp.put("stats", stats);
                resp.put("recentBookings", recent);
                sendJsonResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    static class TrainSearchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try (Connection conn = Database.getConnection()) {
                Map<String, String> q = parseQueryParams(exchange.getRequestURI());
                String src = q.getOrDefault("source", "");
                String dst = q.getOrDefault("destination", "");
                String date = q.getOrDefault("date", "");

                List<Train> trains = Train.searchTrains(conn, src, dst, date);
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", true);
                resp.put("trains", trains);
                sendJsonResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    static class TrainAlternativesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try (Connection conn = Database.getConnection()) {
                Map<String, String> q = parseQueryParams(exchange.getRequestURI());
                String src = q.getOrDefault("source", "");
                String dst = q.getOrDefault("destination", "");
                String date = q.getOrDefault("date", "");
                int excludeId = Integer.parseInt(q.getOrDefault("excludeId", "0"));

                List<Train> alternatives = Train.findAlternativeTrains(conn, src, dst, date, excludeId);
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", true);
                resp.put("alternatives", alternatives);
                sendJsonResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    static class TrainGetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try (Connection conn = Database.getConnection()) {
                Map<String, String> q = parseQueryParams(exchange.getRequestURI());
                int trainId = Integer.parseInt(q.getOrDefault("trainId", "0"));
                Train t = Train.getById(conn, trainId);
                if (t != null) {
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("success", true);
                    resp.put("train", t);
                    sendJsonResponse(exchange, 200, resp);
                } else {
                    sendError(exchange, 404, "Train not found");
                }
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    static class TrainEnquiryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try (Connection conn = Database.getConnection()) {
                Map<String, String> q = parseQueryParams(exchange.getRequestURI());
                String num = q.getOrDefault("trainNumber", "");
                Train t = Train.getByNumber(conn, num);
                if (t != null) {
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("success", true);
                    resp.put("train", t);
                    sendJsonResponse(exchange, 200, resp);
                } else {
                    sendError(exchange, 404, "Train #" + num + " not found.");
                }
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    static class BookingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }

            try (Connection conn = Database.getConnection()) {
                String body = readRequestBody(exchange);
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                int userId = json.get("userId").getAsInt();
                int trainId = json.get("trainId").getAsInt();
                String travelClass = json.get("travelClass").getAsString();

                List<Passenger> passengers = new ArrayList<>();
                var pArray = json.getAsJsonArray("passengers");
                for (int i = 0; i < pArray.size(); i++) {
                    JsonObject po = pArray.get(i).getAsJsonObject();
                    String name = po.get("name").getAsString();
                    int age = po.get("age").getAsInt();
                    String gender = po.get("gender").getAsString();
                    String cat = po.has("category") ? po.get("category").getAsString() : "NORMAL";
                    String pref = po.has("berthPreference") ? po.get("berthPreference").getAsString() : "ANY";
                    passengers.add(new Passenger(name, age, gender, cat, pref));
                }

                Ticket ticket = Ticket.bookTicket(conn, userId, trainId, travelClass, passengers);
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", true);
                resp.put("ticket", ticket);
                resp.put("asciiTicket", Ticket.formatETicketAscii(ticket));
                sendJsonResponse(exchange, 200, resp);
            } catch (IllegalArgumentException e) {
                sendError(exchange, 400, e.getMessage());
            } catch (Exception e) {
                sendError(exchange, 500, "Booking failed: " + e.getMessage());
            }
        }
    }

    static class TicketGetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try (Connection conn = Database.getConnection()) {
                Map<String, String> q = parseQueryParams(exchange.getRequestURI());
                String pnr = q.getOrDefault("pnr", "");
                Ticket t = Ticket.getByPnr(conn, pnr);
                if (t != null) {
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("success", true);
                    resp.put("ticket", t);
                    resp.put("asciiTicket", Ticket.formatETicketAscii(t));
                    sendJsonResponse(exchange, 200, resp);
                } else {
                    sendError(exchange, 404, "PNR not found");
                }
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    static class UserBookingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try (Connection conn = Database.getConnection()) {
                Map<String, String> q = parseQueryParams(exchange.getRequestURI());
                int userId = Integer.parseInt(q.getOrDefault("userId", "0"));
                List<Ticket> list = Ticket.getBookingsByUser(conn, userId);
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", true);
                resp.put("bookings", list);
                sendJsonResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    static class CancelBookingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }

            try (Connection conn = Database.getConnection()) {
                String body = readRequestBody(exchange);
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                int userId = json.get("userId").getAsInt();
                String pnr = json.get("pnr").getAsString();
                boolean isAdmin = json.has("isAdmin") && json.get("isAdmin").getAsBoolean();

                Ticket.CancellationResult res = Ticket.cancelTicket(conn, userId, isAdmin, pnr);
                sendJsonResponse(exchange, 200, res);
            } catch (Exception e) {
                sendError(exchange, 500, "Cancellation failed: " + e.getMessage());
            }
        }
    }

    static class NotificationsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try (Connection conn = Database.getConnection()) {
                Map<String, String> q = parseQueryParams(exchange.getRequestURI());
                int userId = Integer.parseInt(q.getOrDefault("userId", "0"));
                List<Notification> list = Notification.getByUser(conn, userId);
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", true);
                resp.put("notifications", list);
                sendJsonResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    static class MarkReadNotificationsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try (Connection conn = Database.getConnection()) {
                Map<String, String> q = parseQueryParams(exchange.getRequestURI());
                int userId = Integer.parseInt(q.getOrDefault("userId", "0"));
                Notification.markAllAsRead(conn, userId);
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", true);
                sendJsonResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    static class AdminReportsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try (Connection conn = Database.getConnection()) {
                Admin.SystemReport r = Admin.generateReport(conn);
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", true);
                resp.put("reports", r);
                sendJsonResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    static class AdminTrainsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try (Connection conn = Database.getConnection()) {
                List<Train> list = Train.getAllTrains(conn);
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", true);
                resp.put("trains", list);
                sendJsonResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    static class AdminAddTrainHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try (Connection conn = Database.getConnection()) {
                String body = readRequestBody(exchange);
                Train t = gson.fromJson(body, Train.class);
                int id = Train.addTrain(conn, t);
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", id > 0);
                resp.put("trainId", id);
                sendJsonResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    static class AdminUpdateTrainHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try (Connection conn = Database.getConnection()) {
                String body = readRequestBody(exchange);
                Train t = gson.fromJson(body, Train.class);
                boolean ok = Train.updateTrain(conn, t);
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", ok);
                sendJsonResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    static class AdminTrainStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try (Connection conn = Database.getConnection()) {
                String body = readRequestBody(exchange);
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                int trainId = json.get("trainId").getAsInt();
                String status = json.get("status").getAsString();
                String delayInfo = json.has("delayInfo") ? json.get("delayInfo").getAsString() : "";

                boolean ok = Train.updateStatus(conn, trainId, status, delayInfo);
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", ok);
                sendJsonResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    static class AdminUsersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try (Connection conn = Database.getConnection()) {
                Map<String, String> q = parseQueryParams(exchange.getRequestURI());
                String keyword = q.getOrDefault("q", "");
                List<User> users = Admin.getRegisteredUsers(conn, keyword);
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", true);
                resp.put("users", users);
                sendJsonResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    static class AdminChartHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try (Connection conn = Database.getConnection()) {
                Map<String, String> q = parseQueryParams(exchange.getRequestURI());
                int trainId = Integer.parseInt(q.getOrDefault("trainId", "0"));
                List<Ticket.ChartEntry> chart = Ticket.getReservationChart(conn, trainId);
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", true);
                resp.put("chart", chart);
                sendJsonResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    static class AdminBookingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try (Connection conn = Database.getConnection()) {
                Map<String, String> q = parseQueryParams(exchange.getRequestURI());
                String status = q.getOrDefault("status", "ALL");
                List<Ticket> list = Ticket.getAllBookings(conn, status);
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", true);
                resp.put("bookings", list);
                sendJsonResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }

    // ===================================================================
    // STATIC FILE SERVER HANDLER (SERVES HTML, CSS, JS)
    // ===================================================================

    static class StaticFileHandler implements HttpHandler {
        private final String rootDir;

        public StaticFileHandler(String rootDir) {
            this.rootDir = rootDir;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                path = "/index.html";
            }

            File file = new File(rootDir, path);
            if (!file.exists() || file.isDirectory()) {
                String notFound = "<h1>404 Not Found</h1><p>The requested static file does not exist: " + path + "</p>";
                exchange.sendResponseHeaders(404, notFound.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(notFound.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            String mime = "text/plain";
            if (path.endsWith(".html")) mime = "text/html; charset=UTF-8";
            else if (path.endsWith(".css")) mime = "text/css; charset=UTF-8";
            else if (path.endsWith(".js")) mime = "application/javascript; charset=UTF-8";
            else if (path.endsWith(".json")) mime = "application/json; charset=UTF-8";
            else if (path.endsWith(".png")) mime = "image/png";
            else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) mime = "image/jpeg";
            else if (path.endsWith(".svg")) mime = "image/svg+xml";

            exchange.getResponseHeaders().set("Content-Type", mime);
            exchange.sendResponseHeaders(200, file.length());

            try (FileInputStream fis = new FileInputStream(file);
                 OutputStream os = exchange.getResponseBody()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, count);
                }
            }
        }
    }
}
