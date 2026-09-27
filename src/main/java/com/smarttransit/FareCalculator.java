package com.smarttransit;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates travel fares, student discounts, senior citizen concessions,
 * and cancellation refunds.
 */
public class FareCalculator {

    public static final double STUDENT_DISCOUNT_PERCENT = 25.0;
    public static final double SENIOR_DISCOUNT_PERCENT = 40.0;
    public static final double CANCELLATION_CHARGE = 100.0;

    public static class FareBreakdown {
        public double baseFare;
        public String discountType;
        public double discountPercentage;
        public double discountAmount;
        public double finalFare;

        public FareBreakdown(double baseFare, String discountType, double discountPercentage,
                             double discountAmount, double finalFare) {
            this.baseFare = round(baseFare);
            this.discountType = discountType;
            this.discountPercentage = round(discountPercentage);
            this.discountAmount = round(discountAmount);
            this.finalFare = round(finalFare);
        }

        @Override
        public String toString() {
            return String.format(
                "Base Fare: ₹%.2f | Discount (%s): -₹%.2f | Final Fare: ₹%.2f",
                baseFare, discountType, discountAmount, finalFare
            );
        }
    }

    /**
     * Calculates the fare breakdown for an individual passenger.
     *
     * @param trainBaseFare Base fare of the train for the selected class
     * @param age Passenger age
     * @param category User or passenger category ("STUDENT", "SENIOR", "NORMAL")
     * @return FareBreakdown object
     */
    public static FareBreakdown calculateFare(double trainBaseFare, int age, String category) {
        String cat = (category != null) ? category.toUpperCase().trim() : "NORMAL";
        double discountPct = 0.0;
        String discountLabel = "None";

        if ("STUDENT".equals(cat)) {
            discountPct = STUDENT_DISCOUNT_PERCENT;
            discountLabel = "Student (25%)";
        } else if ("SENIOR".equals(cat) || age >= 60) {
            discountPct = SENIOR_DISCOUNT_PERCENT;
            discountLabel = "Senior Citizen (40%)";
        }

        double discountAmt = (trainBaseFare * discountPct) / 100.0;
        double finalAmount = Math.max(0.0, trainBaseFare - discountAmt);

        return new FareBreakdown(trainBaseFare, discountLabel, discountPct, discountAmt, finalAmount);
    }

    /**
     * Calculates refund amount upon ticket cancellation.
     * Refund = Original Fare - ₹100 Cancellation Fee.
     */
    public static double calculateRefund(double originalFare) {
        double refund = originalFare - CANCELLATION_CHARGE;
        return round(Math.max(0.0, refund));
    }

    private static double round(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
