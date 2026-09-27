package com.smarttransit;

/**
 * Represents an individual passenger traveling under a ticket.
 */
public class Passenger {
    private int passengerId;
    private int ticketId;
    private String name;
    private int age;
    private String gender;
    private String category;
    private String coach;
    private int seatNumber;
    private String berthPreference;
    private String allocatedBerth;
    private double fare;
    private String status;

    public Passenger() {
        this.category = "NORMAL";
        this.coach = "WL";
        this.seatNumber = 0;
        this.berthPreference = "ANY";
        this.allocatedBerth = "NONE";
        this.status = "CONFIRMED";
    }

    public Passenger(String name, int age, String gender, String category, String berthPreference) {
        this.name = name;
        this.age = age;
        this.gender = gender;
        this.category = (category != null) ? category : "NORMAL";
        this.berthPreference = (berthPreference != null) ? berthPreference : "ANY";
        this.coach = "WL";
        this.seatNumber = 0;
        this.allocatedBerth = "NONE";
        this.status = "CONFIRMED";
    }

    // Getters and Setters
    public int getPassengerId() { return passengerId; }
    public void setPassengerId(int passengerId) { this.passengerId = passengerId; }

    public int getTicketId() { return ticketId; }
    public void setTicketId(int ticketId) { this.ticketId = ticketId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getCoach() { return coach; }
    public void setCoach(String coach) { this.coach = coach; }

    public int getSeatNumber() { return seatNumber; }
    public void setSeatNumber(int seatNumber) { this.seatNumber = seatNumber; }

    public String getBerthPreference() { return berthPreference; }
    public void setBerthPreference(String berthPreference) { this.berthPreference = berthPreference; }

    public String getAllocatedBerth() { return allocatedBerth; }
    public void setAllocatedBerth(String allocatedBerth) { this.allocatedBerth = allocatedBerth; }

    public double getFare() { return fare; }
    public void setFare(double fare) { this.fare = fare; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSeatFormatted() {
        if ("WAITLIST".equalsIgnoreCase(status) || "WL".equalsIgnoreCase(coach) || seatNumber <= 0) {
            return "WL";
        }
        return coach + "-" + seatNumber + (allocatedBerth != null && !allocatedBerth.equals("NONE") ? " (" + allocatedBerth + ")" : "");
    }
}
