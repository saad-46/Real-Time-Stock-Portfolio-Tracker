package com.portfolio.service;

import com.portfolio.service.MarketIndexService.IndexDef;
import com.portfolio.service.MarketIndexService.Snapshot;

import java.util.List;

/** What the landing page needs from market data, so it can be driven by the real service or by test fixtures. */
@FunctionalInterface
public interface MarketSource {
    /** Blocking provider access: call off the Swing event thread. {@code force} bypasses the cache (not rate-limit back-off). */
    List<Snapshot> refresh(boolean force);

    /**
     * Daily history for one instrument (blocking: off the event thread), fetched only when it is needed so that a refresh of eight
     * quotes does not also spend sixteen history requests. Returns the updated snapshot, or null if nothing could be added.
     */
    default Snapshot history(String indexId) {
        return null;
    }

    default List<IndexDef> indices() {
        return MarketIndexService.DEFAULT_INDICES;
    }

    /** Names of providers with a key configured (never the keys). */
    default List<String> configuredProviderNames() {
        return List.of();
    }
}
