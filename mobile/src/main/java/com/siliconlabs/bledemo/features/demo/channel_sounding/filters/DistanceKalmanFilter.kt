package com.siliconlabs.bledemo.features.demo.channel_sounding.filters

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * DistanceKalmanFilter (Kotlin) — Minimal, efficient hybrid
 *
 * Final pipeline:
 *   RAW  →  Median(5)  →  Adaptive Outlier Rejection (innovation-sigma)  →  Kalman (random-walk)  →  OUTPUT
 *
 * Notes:
 *  - First-sample initializes to the median value (no ramp from zero).
 *  - BLE near-range sentinel (0.0 m) bypasses median/outlier rejection and updates toward zero.
 *  - processNoise (Q) is scaled with dt for time-consistent behavior.
 *  - Adaptive outlier: if |z-x| > outlierSigma * sqrt(p + R), use R_eff = R * outlierNoiseMultiplier.
 *  - Tuned for aggressive outlier rejection at the cost of increased latency.
 *  - No calibration, rate limiter, trend boosts, spike telemetry, or EMA here—kept intentionally out
 *    for accuracy + speed. If needed, add them upstream/downstream, not inside the estimator core.
 */
class DistanceKalmanFilter(
    // Kalman core
    var processNoise: Double = Defaults.processNoise,          // Q (per second, scaled by dt internally)
    var measurementNoise: Double = Defaults.measurementNoise,  // R
    var initialEstimateError: Double = Defaults.initialEstimateError, // P0

    // Adaptive outlier rejection (innovation-sigma gate)
    var outlierSigma: Double = Defaults.outlierSigma,                  // e.g., 3σ
    var outlierNoiseMultiplier: Double = Defaults.outlierNoiseMultiplier, // inflate R on outliers

    // Median(5)
    var medianWindowSize: Int = Defaults.medianFilterWindowSize        // larger window for outlier rejection
) {

    object Defaults {
        // Tuned for aggressive outlier rejection (accepts more latency)
        const val processNoise: Double = 0.006      // Q (per second) → lower = smoother, more latency
        const val measurementNoise: Double = 0.08   // R → higher = trust measurements less
        const val initialEstimateError: Double = 2.0

        // Stricter outlier gate (2σ rejects more measurements than 3σ)
        const val outlierSigma: Double = 2.0
        const val outlierNoiseMultiplier: Double = 25.0  // Heavy damping on outliers

        // Larger median pre-filter window (more impulse noise rejection)
        const val medianFilterWindowSize: Int = 5

        // Validity (0.0 is the BLE "too close" sentinel — handled separately, not as invalid)
        const val nearRangeSentinel: Double = 0.0
        const val minValidDistance: Double = 0.01
        const val maxValidDistance: Double = 100.0

        const val eps: Double = 1e-12

        // Legacy compatibility constants (used by KalmanFilterConfig)
        const val outlierThresholdPerSecond: Double = 0.5  // m/s rate threshold for config UI
        const val distanceOffset: Double = 0.0             // Calibration offset (not used in this filter)
        const val distanceScale: Double = 1.0              // Calibration scale (not used in this filter)
    }

    // =====================
    // State
    // =====================
    private var x: Double = 0.0                // state (distance)
    private var p: Double = initialEstimateError
    private var initialized = false
    private var lastOutput: Double? = null     // for convenience

    // Median(5) buffer
    private val zWindow = FixedWindow(medianWindowSize)

    var lastDt: Double = 1.0
        private set

    // =====================
    // Public API
    // =====================

    /**
     * Reset the filter to a clean state. Optionally set an initial estimate.
     */
    fun reset(initial: Double? = null) {
        if (initial != null) x = initial
        p = initialEstimateError
        initialized = false
        lastOutput = null
        zWindow.clear()
    }

    /**
     * Update with a new measurement.
     *
     * @param rawMeasurement  Raw distance (meters)
     * @param dtSeconds       Seconds since last update (use your real dt if jittery)
     * @return UpdateResult   Output and internals
     */
    fun update(rawMeasurement: Double, dtSeconds: Double = 1.0): UpdateResult {
        val dt = max(dtSeconds, Defaults.eps)
        lastDt = dt

        // BLE near-range sentinel: 0.0 means "too close" — trust it, don't hold last estimate
        if (rawMeasurement == Defaults.nearRangeSentinel) {
            zWindow.clear()
            return kalmanUpdate(
                z = Defaults.nearRangeSentinel,
                rawMeasurement = rawMeasurement,
                dt = dt,
                skipOutlierRejection = true
            )
        }

        // Validity gate (0.0 handled above; reject NaN/inf, negative, and sub-minimum readings)
        if (!rawMeasurement.isFinite() ||
            rawMeasurement < Defaults.minValidDistance ||
            rawMeasurement > Defaults.maxValidDistance
        ) {
            val safe = lastOutput ?: rawMeasurement.coerceIn(Defaults.minValidDistance, Defaults.maxValidDistance)
            return UpdateResult(
                output = safe,
                zUsed = safe,
                calibrated = rawMeasurement,
                usedQ = processNoise * dt,
                usedR = measurementNoise,
                kalmanGain = 0.0,
                isOutlierSuspected = true,
                isNearOutlier = false,
                isSpike = false,
                limitedByRate = false
            )
        }

        // --- Median(5) pre-filter ---
        if (zWindow.capacity != medianWindowSize) zWindow.resize(medianWindowSize)
        zWindow.add(rawMeasurement)
        val z = median(zWindow.values())

        return kalmanUpdate(z = z, rawMeasurement = rawMeasurement, dt = dt)
    }

    private fun kalmanUpdate(
        z: Double,
        rawMeasurement: Double,
        dt: Double,
        skipOutlierRejection: Boolean = false
    ): UpdateResult {
        // --- First-sample initialization (no ramp from zero) ---
        if (!initialized) {
            x = z
            p = initialEstimateError
            initialized = true
            lastOutput = x
            return UpdateResult(
                output = x,
                zUsed = z,
                calibrated = rawMeasurement,
                usedQ = 0.0,
                usedR = measurementNoise,
                kalmanGain = 0.0,
                isOutlierSuspected = false,
                isNearOutlier = false,
                isSpike = false,
                limitedByRate = false
            )
        }

        // --- Predict ---
        val qEff = processNoise * dt
        val pPred = p + qEff

        // --- Adaptive Outlier Rejection (innovation-sigma gate) ---
        val innovation = abs(z - x)
        val sigma = sqrt(pPred + measurementNoise)
        val isOutlier = !skipOutlierRejection && innovation > outlierSigma * sigma
        val rEff = if (isOutlier) measurementNoise * outlierNoiseMultiplier else measurementNoise

        // --- Update ---
        val k = pPred / (pPred + rEff)
        x += k * (z - x)
        p = (1.0 - k) * pPred

        lastOutput = x

        return UpdateResult(
            output = x,
            zUsed = z,
            calibrated = rawMeasurement,
            usedQ = qEff,
            usedR = rEff,
            kalmanGain = k,
            isOutlierSuspected = isOutlier,
            isNearOutlier = false,
            isSpike = false,
            limitedByRate = false
        )
    }

    /** Latest filtered distance, or null before first update. */
    fun current(): Double? = lastOutput

    /** Time-consistent tuning helper (kept for API parity; no per-second gates here). */
    fun retuneForDt(dtSeconds: Double) {
        // Q is already scaled by dt inside update(); nothing else required here.
        lastDt = max(dtSeconds, Defaults.eps)
    }

    /** Adjust noises at runtime. */
    fun setNoises(q: Double, r: Double) {
        processNoise = q
        measurementNoise = r
    }

    // =====================
    // Internals
    // =====================

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return Double.NaN
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    private class FixedWindow(initialCapacity: Int) {
        private var cap: Int = max(0, initialCapacity)
        private val dq = ArrayDeque<Double>(cap)
        val capacity: Int get() = cap

        fun add(v: Double) {
            if (cap <= 0) return
            if (dq.size == cap) dq.removeFirst()
            dq.addLast(v)
        }
        fun values(): List<Double> = dq.toList()
        fun clear() = dq.clear()
        fun resize(newCapacity: Int) {
            val n = max(0, newCapacity)
            if (n == cap) return
            cap = n
            while (dq.size > cap) dq.removeFirst()
        }
    }

    data class UpdateResult(
        val output: Double,     // final filtered distance
        val zUsed: Double,      // measurement used after Median(5)
        val calibrated: Double, // same as raw (no calibration in this minimal core)
        val usedQ: Double,      // effective Q used (scaled by dt)
        val usedR: Double,      // effective R (possibly inflated)
        val kalmanGain: Double, // K
        val isOutlierSuspected: Boolean,
        // legacy fields retained for API compatibility (always false here)
        val isNearOutlier: Boolean,
        val isSpike: Boolean,
        val limitedByRate: Boolean
    )
}

/** Finite check */
private fun Double.isFinite(): Boolean =
    this != Double.NEGATIVE_INFINITY && this != Double.POSITIVE_INFINITY && !this.isNaN()