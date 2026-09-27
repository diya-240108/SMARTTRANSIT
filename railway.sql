-- ===================================================================
-- SMART TRANSIT MANAGEMENT SYSTEM / RAILWAY RESERVATION SYSTEM
-- Database Initialization Script: railway.sql
-- Target RDBMS: MySQL 8.0+ / MariaDB 10.4+
-- ===================================================================

DROP DATABASE IF EXISTS railway;
CREATE DATABASE railway CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE railway;

-- -------------------------------------------------------------------
-- 1. Table: admins
-- -------------------------------------------------------------------
CREATE TABLE admins (
    admin_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- -------------------------------------------------------------------
-- 2. Table: users
-- -------------------------------------------------------------------
CREATE TABLE users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL,
    phone VARCHAR(15) NOT NULL,
    category ENUM('NORMAL', 'STUDENT', 'SENIOR') NOT NULL DEFAULT 'NORMAL',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- -------------------------------------------------------------------
-- 3. Table: trains
-- -------------------------------------------------------------------
CREATE TABLE trains (
    train_id INT AUTO_INCREMENT PRIMARY KEY,
    train_number VARCHAR(10) NOT NULL UNIQUE,
    train_name VARCHAR(100) NOT NULL,
    source VARCHAR(60) NOT NULL,
    destination VARCHAR(60) NOT NULL,
    departure_time VARCHAR(15) NOT NULL,
    arrival_time VARCHAR(15) NOT NULL,
    journey_date DATE NOT NULL,
    seats_1a INT NOT NULL DEFAULT 12,
    seats_2a INT NOT NULL DEFAULT 18,
    seats_3a INT NOT NULL DEFAULT 24,
    seats_sl INT NOT NULL DEFAULT 65,
    fare_1a DECIMAL(10, 2) NOT NULL DEFAULT 2200.00,
    fare_2a DECIMAL(10, 2) NOT NULL DEFAULT 1600.00,
    fare_3a DECIMAL(10, 2) NOT NULL DEFAULT 1100.00,
    fare_sl DECIMAL(10, 2) NOT NULL DEFAULT 450.00,
    status ENUM('ON TIME', 'DELAYED', 'CANCELLED') NOT NULL DEFAULT 'ON TIME',
    delay_info VARCHAR(100) DEFAULT 'On schedule'
) ENGINE=InnoDB;

-- -------------------------------------------------------------------
-- 4. Table: seats (Physical Seat Inventory)
-- -------------------------------------------------------------------
CREATE TABLE seats (
    seat_id INT AUTO_INCREMENT PRIMARY KEY,
    train_id INT NOT NULL,
    travel_class ENUM('1A', '2A', '3A', 'SL') NOT NULL,
    coach VARCHAR(10) NOT NULL,
    seat_number INT NOT NULL,
    berth_type ENUM('WINDOW', 'LOWER', 'MIDDLE', 'UPPER', 'SIDE LOWER', 'SIDE UPPER') NOT NULL,
    is_booked BOOLEAN NOT NULL DEFAULT FALSE,
    current_pnr VARCHAR(12) DEFAULT NULL,
    CONSTRAINT fk_seats_train FOREIGN KEY (train_id) REFERENCES trains(train_id) ON DELETE CASCADE,
    UNIQUE KEY uk_train_seat (train_id, travel_class, coach, seat_number)
) ENGINE=InnoDB;

-- -------------------------------------------------------------------
-- 5. Table: tickets
-- -------------------------------------------------------------------
CREATE TABLE tickets (
    ticket_id INT AUTO_INCREMENT PRIMARY KEY,
    pnr VARCHAR(12) NOT NULL UNIQUE,
    user_id INT NOT NULL,
    train_id INT NOT NULL,
    travel_class ENUM('1A', '2A', '3A', 'SL') NOT NULL,
    journey_date DATE NOT NULL,
    total_fare DECIMAL(10, 2) NOT NULL,
    status ENUM('CONFIRMED', 'CANCELLED', 'WAITLIST') NOT NULL DEFAULT 'CONFIRMED',
    booking_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tickets_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_tickets_train FOREIGN KEY (train_id) REFERENCES trains(train_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- -------------------------------------------------------------------
-- 6. Table: passengers
-- -------------------------------------------------------------------
CREATE TABLE passengers (
    passenger_id INT AUTO_INCREMENT PRIMARY KEY,
    ticket_id INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    age INT NOT NULL,
    gender ENUM('Male', 'Female', 'Other') NOT NULL,
    category ENUM('NORMAL', 'STUDENT', 'SENIOR') NOT NULL DEFAULT 'NORMAL',
    coach VARCHAR(10) NOT NULL DEFAULT 'WL',
    seat_number INT NOT NULL DEFAULT 0,
    berth_preference ENUM('WINDOW', 'LOWER', 'ANY') NOT NULL DEFAULT 'ANY',
    allocated_berth VARCHAR(20) DEFAULT 'NONE',
    fare DECIMAL(10, 2) NOT NULL,
    status ENUM('CONFIRMED', 'WAITLIST', 'CANCELLED') NOT NULL DEFAULT 'CONFIRMED',
    CONSTRAINT fk_passengers_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(ticket_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- -------------------------------------------------------------------
-- 7. Table: waitlist (FIFO Queue persistence)
-- -------------------------------------------------------------------
CREATE TABLE waitlist (
    waitlist_id INT AUTO_INCREMENT PRIMARY KEY,
    ticket_id INT NOT NULL,
    train_id INT NOT NULL,
    travel_class ENUM('1A', '2A', '3A', 'SL') NOT NULL,
    user_id INT NOT NULL,
    passenger_id INT NOT NULL,
    waitlist_number INT NOT NULL,
    request_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status ENUM('WAITING', 'PROMOTED', 'CANCELLED') NOT NULL DEFAULT 'WAITING',
    CONSTRAINT fk_waitlist_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(ticket_id) ON DELETE CASCADE,
    CONSTRAINT fk_waitlist_train FOREIGN KEY (train_id) REFERENCES trains(train_id) ON DELETE CASCADE,
    CONSTRAINT fk_waitlist_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_waitlist_passenger FOREIGN KEY (passenger_id) REFERENCES passengers(passenger_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- -------------------------------------------------------------------
-- 8. Table: notifications
-- -------------------------------------------------------------------
CREATE TABLE notifications (
    notification_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ===================================================================
-- SEED DATA
-- ===================================================================

-- 1. Default Admin Credentials
INSERT INTO admins (username, password, full_name, email) VALUES
('admin', 'admin123', 'Chief Reservation Officer', 'admin@smarttransit.edu');

-- 2. Sample Registered Users (With Categories)
INSERT INTO users (username, password, full_name, email, phone, category) VALUES
('rahul', 'rahul123', 'Rahul Sharma', 'rahul@example.com', '9876543210', 'NORMAL'),
('priya', 'priya123', 'Priya Patel', 'priya@example.com', '9822233344', 'STUDENT'),
('amit', 'amit123', 'Amit Verma', 'amit@example.com', '9833344455', 'SENIOR'),
('diya', 'diya123', 'Diya Sundaram', 'diya@example.com', '9844455566', 'NORMAL');

-- 3. Sample Trains
-- Note: Train 12604 (Chennai Express) has small capacities (SL=3) specifically for the Waitlist Demonstration test scenario!
INSERT INTO trains 
(train_number, train_name, source, destination, departure_time, arrival_time, journey_date, seats_1a, seats_2a, seats_3a, seats_sl, fare_1a, fare_2a, fare_3a, fare_sl, status, delay_info)
VALUES
('12604', 'Chennai Express', 'Chennai', 'Coimbatore', '06:00 AM', '01:30 PM', '2026-09-25', 2, 2, 2, 3, 2200.00, 1600.00, 1100.00, 450.00, 'ON TIME', 'On schedule'),
('12608', 'Lalbagh SF Express', 'Chennai', 'Coimbatore', '07:30 AM', '03:00 PM', '2026-09-25', 4, 6, 12, 18, 2100.00, 1550.00, 1050.00, 430.00, 'ON TIME', 'On schedule'),
('12610', 'Intercity SF Express', 'Chennai', 'Coimbatore', '09:15 AM', '04:45 PM', '2026-09-25', 6, 10, 16, 27, 2000.00, 1500.00, 1000.00, 420.00, 'ON TIME', 'On schedule'),
('12301', 'Rajdhani Express', 'New Delhi', 'Kolkata', '04:55 PM', '10:05 AM', '2026-09-26', 12, 18, 24, 40, 2800.00, 2100.00, 1500.00, 650.00, 'ON TIME', 'On schedule'),
('12952', 'Mumbai Rajdhani', 'New Delhi', 'Mumbai', '04:55 PM', '08:35 AM', '2026-09-26', 12, 18, 24, 40, 3000.00, 2200.00, 1600.00, 700.00, 'DELAYED', 'Delayed by 45 mins due to signal testing'),
('20607', 'Vande Bharat Express', 'Chennai', 'Bengaluru', '05:50 AM', '10:20 AM', '2026-09-27', 16, 24, 32, 50, 1900.00, 1400.00, 980.00, 500.00, 'ON TIME', 'On schedule');

-- 4. Pre-allocated Physical Seats for Train 12604 (3 Sleeper seats for Waitlist Test Scenario)
INSERT INTO seats (train_id, travel_class, coach, seat_number, berth_type, is_booked, current_pnr) VALUES
-- Train 1: 1A
(1, '1A', 'H1', 1, 'WINDOW', FALSE, NULL),
(1, '1A', 'H1', 2, 'LOWER', FALSE, NULL),
-- Train 1: 2A
(1, '2A', 'A1', 1, 'LOWER', FALSE, NULL),
(1, '2A', 'A1', 2, 'WINDOW', FALSE, NULL),
-- Train 1: 3A
(1, '3A', 'B1', 1, 'LOWER', FALSE, NULL),
(1, '3A', 'B1', 2, 'WINDOW', FALSE, NULL),
-- Train 1: SL (3 physical seats only!)
(1, 'SL', 'S1', 1, 'LOWER', FALSE, NULL),
(1, 'SL', 'S1', 2, 'WINDOW', FALSE, NULL),
(1, 'SL', 'S1', 3, 'UPPER', FALSE, NULL);

-- 5. Seed Initial Welcome Notifications
INSERT INTO notifications (user_id, message) VALUES
(1, 'Welcome to Smart Transit System, Rahul! Explore trains and book tickets seamlessly.'),
(2, 'Welcome Priya! As a registered student, you are eligible for a 25% discount on ticket fares.'),
(3, 'Welcome Amit! As a registered senior citizen, you are eligible for a 40% discount on ticket fares.');
