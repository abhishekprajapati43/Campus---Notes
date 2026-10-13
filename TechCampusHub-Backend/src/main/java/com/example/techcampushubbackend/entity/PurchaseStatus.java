package com.example.techcampushubbackend.entity;

/**
 * Lifecycle of a course purchase request (UPI payment + admin approval).
 */
public enum PurchaseStatus {
    PENDING,
    APPROVED,
    REJECTED
}
