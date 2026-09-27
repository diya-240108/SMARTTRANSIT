# 🚆 Smart Transit Management System / Railway Reservation System

A complete, full-stack college project built for 2nd-Year B.E. Computer Science & Engineering students. Demonstrates **Core Java 21**, **Object-Oriented Programming (OOP)**, **JDBC**, **MySQL**, and a modern, responsive **HTML5/CSS3/JavaScript** frontend without any heavy third-party web frameworks like Spring Boot or React.

---

## 📍 Project Location On Your Computer

The entire source code, database scripts, compiled classes, and frontend files are located at:

```
C:\Users\HP\.gemini\antigravity\scratch\SmartTransit
```

> **Tip**: You can open this directory directly in File Explorer or IntelliJ IDEA.

---

## 🗂️ Complete Directory Structure

```
SmartTransit/
├── README.md                      # Complete Project Guide & Documentation (this file)
├── pom.xml                        # Maven configuration file (Java 21, MySQL Connector/J, Gson)
├── railway.sql                    # Full MySQL database schema DDL & seed data
├── compile.bat                    # Double-click script to compile Java code
├── run.bat                        # Double-click script to launch the application server
│
├── lib/                           # Pre-bundled JAR dependencies
│   ├── mysql-connector-j-8.3.0.jar
│   └── gson-2.13.2.jar
│
├── bin/                           # Compiled Java .class bytecode files
│
├── src/main/java/com/smarttransit/ # Backend Java 21 source code
│   ├── Railway.java               # Main entry point & embedded HTTP REST server (port 8080)
│   ├── Database.java              # JDBC connection manager & transaction rollback helper
│   ├── Admin.java                 # Admin authentication, user management, and system reports
│   ├── User.java                  # Passenger registration, login, and dashboard metrics
│   ├── Train.java                 # Train schedules, search engine & smart alternative engine
│   ├── Ticket.java                # PNR generation, atomic bookings, cancellation & chart
│   ├── Passenger.java             # Passenger model, preferences & seat assignments
│   ├── Seat.java                  # Physical seat inventory table & berth preference allocator
│   ├── Waitlist.java              # FIFO Waitlist queue (LinkedList) & automatic promotion
│   ├── Notification.java          # Real-time alert feed for bookings, delays & promotions
│   └── FareCalculator.java        # Base fares, 25% student / 40% senior discounts & refunds
│
└── frontend/                      # Web frontend (Realistic Indian Railway transit UI)
    ├── index.html                 # Landing page with quick search and live train enquiry
    ├── login.html                 # Login page with Passenger vs Administrator toggle
    ├── register.html              # Registration page with concession category selector
    ├── dashboard.html             # Passenger dashboard with Active Journey Dashboard card
    ├── search.html                # Train search results & class-wise availability chips
    ├── booking.html               # Multi-passenger booking form & live fare breakdown
    ├── ticket.html                # E-Ticket reservation slip & terminal ASCII ticket box
    ├── bookings.html              # My Bookings history table with live cancellation & refund
    ├── admin.html                 # Admin portal: Fleet controls, charts, users & revenue
    ├── notifications.html         # User journey alerts and notifications feed
    ├── css/
    │   └── style.css              # Custom Indian Railway styling (Navy #0f2537 & Orange #e65100)
    └── js/
        └── app.js                 # API communication, session management & UI handlers
```

---

## ⚙️ Prerequisites

Ensure you have the following installed on your computer:
1. **Java Development Kit (JDK) 21** (already installed at `C:\Program Files\Java\jdk-21.0.10`).
2. **MySQL Server 8.0+** (already installed and running on port `3306`).
3. **Web Browser** (Chrome, Edge, Firefox, or Brave).
4. *(Optional)* **IntelliJ IDEA** or **VS Code**.

---

## 🗄️ Database Setup (MySQL)

The database script initializes the `railway` database with 8 tables and sample seed records.

### Method 1: Using Command Prompt / PowerShell
Run this single command in PowerShell:
```powershell
Get-Content "C:\Users\HP\.gemini\antigravity\scratch\SmartTransit\railway.sql" -Raw | & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -proot
```
*(If your MySQL password is not `root`, replace `-proot` with your actual password, e.g., `-pMyPassword`).*

### Method 2: Using MySQL Command Line Client or Workbench
1. Open **MySQL Command Line Client**.
2. Enter your MySQL password.
3. Run:
   ```sql
   source C:/Users/HP/.gemini/antigravity/scratch/SmartTransit/railway.sql;
   ```
4. Verify by checking tables:
   ```sql
   USE railway;
   SHOW TABLES;
   ```
   You should see: `admins`, `notifications`, `passengers`, `seats`, `tickets`, `trains`, `users`, `waitlist`.

### Database Credentials in Code
If your MySQL password is not `root` or empty, open `src/main/java/com/smarttransit/Database.java` and update:
```java
private static String dbUser = "root";
private static String dbPassword = "your_mysql_password";
```

---

## 🚀 How to Run the Application

You can run the project in three different ways:

### Option 1: Double-Click Scripts (Fastest & Easiest)
1. Navigate to `C:\Users\HP\.gemini\antigravity\scratch\SmartTransit` in File Explorer.
2. Double-click **`compile.bat`** $\to$ compiles all Java classes into `bin/`.
3. Double-click **`run.bat`** $\to$ starts the Java backend server.
4. Open your browser and visit:
   ```
   http://localhost:8080/index.html
   ```

### Option 2: Running in IntelliJ IDEA
1. Open **IntelliJ IDEA**.
2. Click **File** $\to$ **Open...** and choose `C:\Users\HP\.gemini\antigravity\scratch\SmartTransit`.
3. Set Project SDK:
   - Go to **File** $\to$ **Project Structure** $\to$ **Project**.
   - Set **SDK** to **21**.
   - Set **Language Level** to **21**.
   - Click **Apply** and **OK**.
4. Navigate to `src/main/java/com/smarttransit/Railway.java`.
5. Right-click inside `Railway.java` and click **Run 'Railway.main()'**.
6. The IntelliJ Run console will display:
   ```
   ==================================================================
     Smart Transit Management Server is ACTIVE and RUNNING!
     PORT: 8080
     LOCAL URL: http://localhost:8080/index.html
   ==================================================================
   ```
7. Click the URL `http://localhost:8080/index.html` or type it into your browser.

### Option 3: Running from PowerShell / Terminal
```powershell
cd "C:\Users\HP\.gemini\antigravity\scratch\SmartTransit"
& "C:\Program Files\Java\jdk-21.0.10\bin\java.exe" -cp "bin;lib/*" com.smarttransit.Railway
```

---

## 🔑 Sample Login Credentials

| Role | Username | Password | Full Name | Category / Special Features |
| :--- | :--- | :--- | :--- | :--- |
| **Admin** | `admin` | `admin123` | Chief Reservation Officer | Full access: reports, fleet, chart, delay alerts |
| **User (General)** | `rahul` | `rahul123` | Rahul Sharma | General fare passenger |
| **User (Student)** | `priya` | `priya123` | Priya Patel | **25% Student Discount** automatically calculated |
| **User (Senior)** | `amit` | `amit123` | Amit Verma | **40% Senior Citizen Discount** automatically calculated |
| **User (General)** | `diya` | `diya123` | Diya Sundaram | General fare passenger |

---

## 🎯 Step-by-Step Viva Demonstration Walkthrough

When presenting your project to college professors, follow this exact sequence:

### 1. Show the Landing Page & Enquiry (`index.html`)
- Open `http://localhost:8080/index.html`.
- Highlight the realistic Indian Railway design system (Navy `#0f2537`, IRCTC Orange `#e65100`, no generic AI glassmorphism).
- Scroll to **Train Enquiry**: Enter `12604` and click **Enquire** to showcase live route details and schedule.

### 2. Login as a Student User (`login.html`)
- Click **Login**, choose **Passenger Login**, enter `priya` / `priya123`.
- In the top navigation bar, point out: `Priya Patel (STUDENT)`.

### 3. Search Trains & Class Availability (`search.html`)
- Search From: `Chennai`, To: `Coimbatore`, Date: `2026-09-25`.
- Observe **Train 12604 - Chennai Express**:
  - Point out that **Sleeper (SL)** has **3 seats available** (configured specifically for the waitlist demonstration!).

### 4. Demonstrate Booking, Seat Preference & 25% Discount (`booking.html`)
- Click **Book** on Sleeper class.
- Add 3 passengers:
  - Passenger 1: *Priya Patel* (Age: 21, `STUDENT`, Preference: `WINDOW`).
  - Passenger 2: *Amit Verma* (Age: 65, `SENIOR`, Preference: `LOWER`).
  - Passenger 3: *Rohan Gupta* (Age: 25, `NORMAL`, Preference: `ANY`).
- Show the **Live Fare Breakdown** on the right side:
  - Priya automatically receives a **25% Student Discount**.
  - Amit automatically receives a **40% Senior Discount**.
- Click **Confirm & Reserve Ticket**.
- You are redirected to **`ticket.html`**:
  - Unique 10-digit PNR generated (e.g. `8457219365`).
  - Seats allocated: `S1-1` (Window), `S1-2` (Lower), `S1-3` (Upper).
  - Show the prof the **ASCII Ticket Box** matching the requirement.
  - All 3 available Sleeper seats are now fully booked!

### 5. Demonstrate the Waitlist System (`search.html` & `booking.html`)
- Click **Logout**.
- Login as `diya` / `diya123`.
- Search `Chennai` to `Coimbatore` on `2026-09-25`.
- Observe that Sleeper class on Train 12604 now shows: **WAITLIST Available** (because 3/3 seats are taken).
- Click **Book** for passenger *Diya Sundaram*.
- Complete booking $\to$ The ticket status is assigned **`WAITLIST`**, Coach: `WL`, Seat: `0`.
- Diya is now position **#1 in the FIFO Queue**.

### 6. Demonstrate Automatic Waitlist Promotion ⭐ (`bookings.html`)
- Click **Logout**.
- Login back as `priya` / `priya123`.
- Go to **My Bookings** (`bookings.html`).
- Click **Cancel** on Priya's ticket.
- A confirmation dialog appears showing:
  - Original Fare Paid.
  - Cancellation Fee: ₹100.00.
  - Net Refund Amount.
- Click **OK**:
  - The seat (`S1-1`) is immediately released in MySQL.
  - The Java FIFO queue pops the head (`queue.poll()`).
  - **Diya is automatically PROMOTED to `CONFIRMED` on Seat `S1-1`!**
  - An alert message confirms: `Waitlist #1 PROMOTED: Passenger Diya Sundaram to Seat S1-1`.

### 7. Verify the Promoted Ticket & Notification (`ticket.html` & `notifications.html`)
- Click **Logout**.
- Login as `diya` / `diya123`.
- Click **Alerts**:
  - See notification: `🔔 Seat Confirmed! Waitlisted passenger Diya Sundaram has been PROMOTED to Seat S1-1 (SL).`
- Go to **My Bookings**:
  - Status has automatically changed from `WAITLIST` to `CONFIRMED` with Seat `S1-1`.

### 8. Show Smart Alternative Train Recommendations
- Search trains when Train 12604 has 0 seats or is cancelled.
- Point out the **Smart Alternative Train Recommendations** box that automatically suggests Train `12608` (07:30 AM) and Train `12610` (09:15 AM) on the same route with available seats.

### 9. Demonstrate Admin Portal & Reports (`admin.html`)
- Click **Logout**.
- Login as `admin` / `admin123`.
- Show the 5 Admin tabs:
  1. **Analytics & Reports**: Total Users, Total Trains, Total Bookings, Confirmed, Cancelled, Waitlisted, Total Revenue, and train-wise revenue performance.
  2. **Train Fleet**: Update train status to `DELAYED` with delay info `Delayed by 35 mins` $\to$ Automatically generates notifications for all booked passengers!
  3. **Reservation Chart**: Select Train `12604` to view the full chart: Train, Coach, Seat, Passenger, PNR, and Status.
  4. **Registered Users**: Search and inspect registered passengers (passwords safely omitted).
  5. **All Bookings**: Filter across all system bookings.

---

## 💡 Quick Viva Questions & Ready Answers

### Q: Why didn't you use Spring Boot?
**A:** "For a 2nd-year academic project, Spring Boot introduces too many layers of abstraction (annotations, auto-configuration, IoC container). We used Java’s standard built-in `com.sun.net.httpserver.HttpServer` and native JDBC to clearly demonstrate core OOP, manual transaction control, SQL statements, and the Java Collections Framework."

### Q: How is FIFO ordering guaranteed in the waitlist?
**A:** "We use Java's `Queue<WaitlistEntry>` interface implemented with `LinkedList<WaitlistEntry>`. When seats are full, passengers are added to the waitlist queue with an incremental `waitlist_number`. On cancellation, `queue.poll()` extracts the head of the queue, assigning the released seat to the passenger who waited the longest."

### Q: How do you prevent inconsistent database states if an error occurs?
**A:** "We use JDBC Transactions. We set `conn.setAutoCommit(false)` before executing related SQL operations. If seat release, waitlist promotion, ticket updates, and refund calculations succeed, we call `conn.commit()`. If an exception occurs, the `catch` block calls `conn.rollback()` to undo partial updates."

### Q: How do you prevent SQL injection?
**A:** "We exclusively use `PreparedStatement` with parameterized placeholders (`?`) for all queries containing user input. User values are never concatenated directly into SQL strings."

---

## 🛠️ Troubleshooting

- **Error: `Address already in use: bind`**:
  Another application (or previous run) is using port 8080. Open PowerShell and run:
  ```powershell
  Get-Process -Id (Get-NetTCPConnection -LocalPort 8080).OwningProcess | Stop-Process -Force
  ```
- **Error: `Table 'railway.seats' doesn't exist`**:
  Run the SQL initialization command in the **Database Setup** section above.
- **Error: `Access denied for user 'root'@'localhost'`**:
  Open `src/main/java/com/smarttransit/Database.java` and set `dbPassword` to match your local MySQL root password.
