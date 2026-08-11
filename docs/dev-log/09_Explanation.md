# Day 9 Architectural Breakdown & Viva Defense Guide

**Author:** Muhammad Arslan (`arslan2147c@gmail.com`)  
**Component:** Phase 4 — Real-Time Visual Telemetry & Security Metrics  
**Commit:** C13  
**Date:** August 02, 2026  

---

## 1. Executive Summary & Why We Built This

On Day 9, we built **`TelemetryViewController.java`**, an aggregation engine and real-time security monitoring controller. It computes system-wide transaction throughput in Transactions Per Second (TPS), composite threat risk percentage scores, and logs security quarantine streams.

### Key Architectural Challenge: Real-Time Metric Aggregation
In high-throughput financial systems processing hundreds of concurrent transfers per second, computing risk scores synchronously inside lock boundaries causes unacceptable latency spikes.

To maintain real-time telemetry with zero performance overhead, `TelemetryViewController` uses non-blocking thread-safe concurrent data structures (`ConcurrentLinkedQueue`, `AtomicInteger`) to log timestamps and calculate metrics asynchronously over sliding time windows.

---

## 2. Mathematical Metric Calculations

### A. Transactions Per Second (TPS)
$$\text{TPS} = \frac{N_{\text{window}} \times 1000}{\text{WindowDuration (ms)}}$$
where $N_{\text{window}}$ represents the number of transactions recorded within the sliding time window (e.g. 1000ms).

### B. Composite Threat Risk Score
$$\text{RiskScore (\%)} = \min\left(100.0, \frac{(1.5 \times N_{\text{flagged}}) + (3.0 \times N_{\text{quarantined}})}{N_{\text{total}}} \times 25.0\right)$$

* **Badge Thresholds:**
  * $< 15.0\%$: `NORMAL / LOW`
  * $15.0\% - 40.0\%$: `ELEVATED THREAT`
  * $> 40.0\%$: `CRITICAL / ATTACK DETECTED`

---

## 3. OOP Concepts & Design Patterns Applied

| OOP Concept / Pattern | Application in Day 9 Code |
| :--- | :--- |
| **Observer Pattern** | `TelemetryViewController` implements `JavaFXEventListener` to observe transactions as they complete. |
| **Encapsulation** | Thread counts and timestamp queues are encapsulated privately behind atomic operations (`AtomicInteger`, `ConcurrentLinkedQueue`). |
| **Immutability & Value Objects** | Consumes immutable `Transaction` records and `FraudResult` evaluation tokens. |

---

## 4. Day 9 Viva Defense Q&A (WhatsApp Study Notes)

### Q1: "Arslan, how does the system calculate TPS (Transactions Per Second) in real time?"
> **Answer:** *"When a transaction completes, its timestamp is pushed onto a thread-safe `ConcurrentLinkedQueue<Long>`. When `calculateCurrentTps(1000)` is invoked, it prunes timestamps older than 1 second and computes the rate directly, guaranteeing $O(1)$ amortized sliding-window throughput calculation without locking."*

### Q2: "How does the Telemetry Controller categorize system threat levels?"
> **Answer:** *"It aggregates the ratio of flagged and quarantined transfers against total volume. If the composite risk score is below 15%, it displays 'NORMAL / LOW'. If velocity spikes or fraudulent transfers are quarantined, the score escalates to 'ELEVATED THREAT' or 'CRITICAL / ATTACK DETECTED'."*
