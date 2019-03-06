package com.css.kitchensync.metrics

import com.google.common.collect.Maps
import java.util.concurrent.atomic.AtomicLong
import com.twitter.common.stats.Stats.STATS_PROVIDER as statsProvider

/**
 * Thin wrapper around Stats library so that we don't need to have objects of
 * metrics themselves. We just need to know their names.
 * Especially useful when we need to log a metric at multiple places.
 *
 * For now, it only has counters. We will add metrics and gauges as we see fit
 */
object Stats {
    private val counters = Maps.newConcurrentMap<String, AtomicLong>()

    fun incr(name: String) {
        val ctr = counters.getOrPut(name) { statsProvider.makeCounter(name) }
        ctr.incrementAndGet()
    }

    fun getCounter(name: String): AtomicLong? {
        return counters[name]
    }
}