"use strict";
Object.defineProperty(exports, "__esModule", {
    value: true
});
0 && (module.exports = {
    EntryStatus: null,
    readExactRouteCacheEntry: null,
    readRouteCacheEntry: null,
    readSegmentCacheEntry: null,
    requestRouteCacheEntryFromCache: null,
    requestSegmentEntryFromCache: null,
    waitForSegmentCacheEntry: null
});
function _export(target, all) {
    for(var name in all)Object.defineProperty(target, name, {
        enumerable: true,
        get: all[name]
    });
}
_export(exports, {
    EntryStatus: function() {
        return EntryStatus;
    },
    readExactRouteCacheEntry: function() {
        return readExactRouteCacheEntry;
    },
    readRouteCacheEntry: function() {
        return readRouteCacheEntry;
    },
    readSegmentCacheEntry: function() {
        return readSegmentCacheEntry;
    },
    requestRouteCacheEntryFromCache: function() {
        return requestRouteCacheEntryFromCache;
    },
    requestSegmentEntryFromCache: function() {
        return requestSegmentEntryFromCache;
    },
    waitForSegmentCacheEntry: function() {
        return waitForSegmentCacheEntry;
    }
});
const _approuterheaders = require("../app-router-headers");
const _fetchserverresponse = require("../router-reducer/fetch-server-response");
const _scheduler = require("./scheduler");
const _appbuildid = require("../../app-build-id");
const _createhreffromurl = require("../router-reducer/create-href-from-url");
const _tuplemap = require("./tuple-map");
const _lru = require("./lru");
var EntryStatus = /*#__PURE__*/ function(EntryStatus) {
    EntryStatus[EntryStatus["Pending"] = 0] = "Pending";
    EntryStatus[EntryStatus["Rejected"] = 1] = "Rejected";
    EntryStatus[EntryStatus["Fulfilled"] = 2] = "Fulfilled";
    return EntryStatus;
}({});
const routeCacheMap = (0, _tuplemap.createTupleMap)();
// We use an LRU for memory management. We must update this whenever we add or
// remove a new cache entry, or when an entry changes size.
// TODO: I chose the max size somewhat arbitrarily. Consider setting this based
// on navigator.deviceMemory, or some other heuristic. We should make this
// customizable via the Next.js config, too.
const maxRouteLruSize = 10 * 1024 * 1024 // 10 MB
;
const routeCacheLru = (0, _lru.createLRU)(maxRouteLruSize, onRouteLRUEviction);
// TODO: We may eventually store segment entries in a tuple map, too, to
// account for search params.
const segmentCacheMap = new Map();
// NOTE: Segments and Route entries are managed by separate LRUs. We could
// combine them into a single LRU, but because they are separate types, we'd
// need to wrap each one in an extra LRU node (to maintain monomorphism, at the
// cost of additional memory).
const maxSegmentLruSize = 50 * 1024 * 1024 // 50 MB
;
const segmentCacheLru = (0, _lru.createLRU)(maxSegmentLruSize, onSegmentLRUEviction);
function readExactRouteCacheEntry(now, href, nextUrl) {
    const keypath = nextUrl === null ? [
        href
    ] : [
        href,
        nextUrl
    ];
    const existingEntry = routeCacheMap.get(keypath);
    if (existingEntry !== null) {
        // Check if the entry is stale
        if (existingEntry.staleAt > now) {
            // Reuse the existing entry.
            // Since this is an access, move the entry to the front of the LRU.
            routeCacheLru.put(existingEntry);
            return existingEntry;
        } else {
            // Evict the stale entry from the cache.
            deleteRouteFromCache(existingEntry, keypath);
        }
    }
    return null;
}
function readRouteCacheEntry(now, key) {
    // First check if there's a non-intercepted entry. Most routes cannot be
    // intercepted, so this is the common case.
    const nonInterceptedEntry = readExactRouteCacheEntry(now, key.href, null);
    if (nonInterceptedEntry !== null && !nonInterceptedEntry.couldBeIntercepted) {
        // Found a match, and the route cannot be intercepted. We can reuse it.
        return nonInterceptedEntry;
    }
    // There was no match. Check again but include the Next-Url this time.
    return readExactRouteCacheEntry(now, key.href, key.nextUrl);
}
function readSegmentCacheEntry(now, path) {
    const existingEntry = segmentCacheMap.get(path);
    if (existingEntry !== undefined) {
        // Check if the entry is stale
        if (existingEntry.staleAt > now) {
            // Reuse the existing entry.
            // Since this is an access, move the entry to the front of the LRU.
            segmentCacheLru.put(existingEntry);
            return existingEntry;
        } else {
            // Evict the stale entry from the cache.
            deleteSegmentFromCache(existingEntry, path);
        }
    }
    return null;
}
function waitForSegmentCacheEntry(pendingEntry) {
    // Because the entry is pending, there's already a in-progress request.
    // Attach a promise to the entry that will resolve when the server responds.
    let promiseWithResolvers = pendingEntry.promise;
    if (promiseWithResolvers === null) {
        promiseWithResolvers = pendingEntry.promise = createPromiseWithResolvers();
    } else {
    // There's already a promise we can use
    }
    return promiseWithResolvers.promise;
}
function requestRouteCacheEntryFromCache(now, task) {
    const key = task.key;
    // First check if there's a non-intercepted entry. Most routes cannot be
    // intercepted, so this is the common case.
    const nonInterceptedEntry = readExactRouteCacheEntry(now, key.href, null);
    if (nonInterceptedEntry !== null && !nonInterceptedEntry.couldBeIntercepted) {
        // Found a match, and the route cannot be intercepted. We can reuse it.
        return nonInterceptedEntry;
    }
    // There was no match. Check again but include the Next-Url this time.
    const exactEntry = readExactRouteCacheEntry(now, key.href, key.nextUrl);
    if (exactEntry !== null) {
        return exactEntry;
    }
    // Create a pending entry and spawn a request for its data.
    const pendingEntry = {
        canonicalUrl: null,
        status: 0,
        blockedTasks: null,
        tree: null,
        head: null,
        isHeadPartial: true,
        // If the request takes longer than a minute, a subsequent request should
        // retry instead of waiting for this one.
        //
        // When the response is received, this value will be replaced by a new value
        // based on the stale time sent from the server.
        staleAt: now + 60 * 1000,
        // This is initialized to true because we don't know yet whether the route
        // could be intercepted. It's only set to false once we receive a response
        // from the server.
        couldBeIntercepted: true,
        // LRU-related fields
        keypath: null,
        next: null,
        prev: null,
        size: 0
    };
    (0, _scheduler.spawnPrefetchSubtask)(fetchRouteOnCacheMiss(pendingEntry, task));
    const keypath = key.nextUrl === null ? [
        key.href
    ] : [
        key.href,
        key.nextUrl
    ];
    routeCacheMap.set(keypath, pendingEntry);
    // Stash the keypath on the entry so we know how to remove it from the map
    // if it gets evicted from the LRU.
    pendingEntry.keypath = keypath;
    routeCacheLru.put(pendingEntry);
    return pendingEntry;
}
function requestSegmentEntryFromCache(now, task, route, path, accessToken) {
    const existingEntry = readSegmentCacheEntry(now, path);
    if (existingEntry !== null) {
        return existingEntry;
    }
    // Create a pending entry and spawn a request for its data.
    const pendingEntry = {
        status: 0,
        rsc: null,
        loading: null,
        staleAt: route.staleAt,
        isPartial: true,
        promise: null,
        // LRU-related fields
        key: null,
        next: null,
        prev: null,
        size: 0
    };
    (0, _scheduler.spawnPrefetchSubtask)(fetchSegmentEntryOnCacheMiss(route, pendingEntry, task.key, path, accessToken));
    segmentCacheMap.set(path, pendingEntry);
    // Stash the keypath on the entry so we know how to remove it from the map
    // if it gets evicted from the LRU.
    pendingEntry.key = path;
    segmentCacheLru.put(pendingEntry);
    return pendingEntry;
}
function deleteRouteFromCache(entry, keypath) {
    pingBlockedTasks(entry);
    routeCacheMap.delete(keypath);
    routeCacheLru.delete(entry);
}
function deleteSegmentFromCache(entry, key) {
    cancelEntryListeners(entry);
    segmentCacheMap.delete(key);
    segmentCacheLru.delete(entry);
}
function onRouteLRUEviction(entry) {
    // The LRU evicted this entry. Remove it from the map.
    const keypath = entry.keypath;
    if (keypath !== null) {
        entry.keypath = null;
        pingBlockedTasks(entry);
        routeCacheMap.delete(keypath);
    }
}
function onSegmentLRUEviction(entry) {
    // The LRU evicted this entry. Remove it from the map.
    const key = entry.key;
    if (key !== null) {
        entry.key = null;
        cancelEntryListeners(entry);
        segmentCacheMap.delete(key);
    }
}
function cancelEntryListeners(entry) {
    if (entry.status === 0 && entry.promise !== null) {
        // There were listeners for this entry. Resolve them with `null` to indicate
        // that the prefetch failed. It's up to the listener to decide how to handle
        // this case.
        // NOTE: We don't currently propagate the reason the prefetch was canceled
        // but we could by accepting a `reason` argument.
        entry.promise.resolve(null);
        entry.promise = null;
    }
}
function pingBlockedTasks(entry) {
    const blockedTasks = entry.blockedTasks;
    if (blockedTasks !== null) {
        for (const task of blockedTasks){
            (0, _scheduler.pingPrefetchTask)(task);
        }
        entry.blockedTasks = null;
    }
}
function fulfillRouteCacheEntry(entry, tree, head, isHeadPartial, staleAt, couldBeIntercepted, canonicalUrl) {
    const fulfilledEntry = entry;
    fulfilledEntry.status = 2;
    fulfilledEntry.tree = tree;
    fulfilledEntry.head = head;
    fulfilledEntry.isHeadPartial = isHeadPartial;
    fulfilledEntry.staleAt = staleAt;
    fulfilledEntry.couldBeIntercepted = couldBeIntercepted;
    fulfilledEntry.canonicalUrl = canonicalUrl;
    pingBlockedTasks(entry);
    return fulfilledEntry;
}
function fulfillSegmentCacheEntry(segmentCacheEntry, rsc, loading, staleAt, isPartial) {
    const fulfilledEntry = segmentCacheEntry;
    fulfilledEntry.status = 2;
    fulfilledEntry.rsc = rsc;
    fulfilledEntry.loading = loading;
    fulfilledEntry.staleAt = staleAt;
    fulfilledEntry.isPartial = isPartial;
    // Resolve any listeners that were waiting for this data.
    if (segmentCacheEntry.promise !== null) {
        segmentCacheEntry.promise.resolve(fulfilledEntry);
        // Free the promise for garbage collection.
        fulfilledEntry.promise = null;
    }
}
function rejectRouteCacheEntry(entry, staleAt) {
    const rejectedEntry = entry;
    rejectedEntry.status = 1;
    rejectedEntry.staleAt = staleAt;
    pingBlockedTasks(entry);
}
function rejectSegmentCacheEntry(entry, staleAt) {
    const rejectedEntry = entry;
    rejectedEntry.status = 1;
    rejectedEntry.staleAt = staleAt;
    if (entry.promise !== null) {
        // NOTE: We don't currently propagate the reason the prefetch was canceled
        // but we could by accepting a `reason` argument.
        entry.promise.resolve(null);
        entry.promise = null;
    }
}
async function fetchRouteOnCacheMiss(entry, task) {
    // This function is allowed to use async/await because it contains the actual
    // fetch that gets issued on a cache miss. Notice though that it does not
    // return anything; it writes the result to the cache entry directly, then
    // pings the scheduler to unblock the corresponding prefetch task.
    const key = task.key;
    const href = key.href;
    const nextUrl = key.nextUrl;
    try {
        const response = await fetchSegmentPrefetchResponse(href, '/_tree', nextUrl);
        if (!response || !response.ok || // 204 is a Cache miss. Though theoretically this shouldn't happen when
        // PPR is enabled, because we always respond to route tree requests, even
        // if it needs to be blockingly generated on demand.
        response.status === 204 || !response.body) {
            // Server responded with an error, or with a miss. We should still cache
            // the response, but we can try again after 10 seconds.
            rejectRouteCacheEntry(entry, Date.now() + 10 * 1000);
            return;
        }
        const prefetchStream = createPrefetchResponseStream(response.body, routeCacheLru, entry);
        const serverData = await (0, _fetchserverresponse.createFromNextReadableStream)(prefetchStream);
        if (serverData.buildId !== (0, _appbuildid.getAppBuildId)()) {
            // The server build does not match the client. Treat as a 404. During
            // an actual navigation, the router will trigger an MPA navigation.
            // TODO: Consider moving the build ID to a response header so we can check
            // it before decoding the response, and so there's one way of checking
            // across all response types.
            rejectRouteCacheEntry(entry, Date.now() + 10 * 1000);
            return;
        }
        // This is a bit convoluted but it's taken from router-reducer and
        // fetch-server-response
        const canonicalUrl = response.redirected ? (0, _createhreffromurl.createHrefFromUrl)((0, _fetchserverresponse.urlToUrlWithoutFlightMarker)(response.url)) : href;
        // Check whether the response varies based on the Next-Url header.
        const varyHeader = response.headers.get('vary');
        const couldBeIntercepted = varyHeader !== null && varyHeader.includes(_approuterheaders.NEXT_URL);
        fulfillRouteCacheEntry(entry, serverData.tree, serverData.head, serverData.isHeadPartial, Date.now() + serverData.staleTime, couldBeIntercepted, canonicalUrl);
        if (!couldBeIntercepted && nextUrl !== null) {
            // This route will never be intercepted. So we can use this entry for all
            // requests to this route, regardless of the Next-Url header. This works
            // because when reading the cache we always check for a valid
            // non-intercepted entry first.
            //
            // Re-key the entry. Since we're in an async task, we must first confirm
            // that the entry hasn't been concurrently modified by a different task.
            const currentKeypath = [
                href,
                nextUrl
            ];
            const expectedEntry = routeCacheMap.get(currentKeypath);
            if (expectedEntry === entry) {
                routeCacheMap.delete(currentKeypath);
                const newKeypath = [
                    href
                ];
                routeCacheMap.set(newKeypath, entry);
                // We don't need to update the LRU because the entry is already in it.
                // But since we changed the keypath, we do need to update that, so we
                // know how to remove it from the map if it gets evicted from the LRU.
                entry.keypath = newKeypath;
            } else {
            // Something else modified this entry already. Since the re-keying is
            // just a performance optimization, we can safely skip it.
            }
        }
    } catch (error) {
        // Either the connection itself failed, or something bad happened while
        // decoding the response.
        rejectRouteCacheEntry(entry, Date.now() + 10 * 1000);
    }
}
async function fetchSegmentEntryOnCacheMiss(route, segmentCacheEntry, routeKey, segmentPath, accessToken) {
    // This function is allowed to use async/await because it contains the actual
    // fetch that gets issued on a cache miss. Notice though that it does not
    // return anything; it writes the result to the cache entry directly.
    //
    // Segment fetches are non-blocking so we don't need to ping the scheduler
    // on completion.
    const href = routeKey.href;
    try {
        const response = await fetchSegmentPrefetchResponse(href, accessToken === '' ? segmentPath : segmentPath + "." + accessToken, routeKey.nextUrl);
        if (!response || !response.ok || response.status === 204 || // Cache miss
        !response.body) {
            // Server responded with an error, or with a miss. We should still cache
            // the response, but we can try again after 10 seconds.
            rejectSegmentCacheEntry(segmentCacheEntry, Date.now() + 10 * 1000);
            return;
        }
        // Wrap the original stream in a new stream that never closes. That way the
        // Flight client doesn't error if there's a hanging promise.
        const prefetchStream = createPrefetchResponseStream(response.body, segmentCacheLru, segmentCacheEntry);
        const serverData = await (0, _fetchserverresponse.createFromNextReadableStream)(prefetchStream);
        if (serverData.buildId !== (0, _appbuildid.getAppBuildId)()) {
            // The server build does not match the client. Treat as a 404. During
            // an actual navigation, the router will trigger an MPA navigation.
            // TODO: Consider moving the build ID to a response header so we can check
            // it before decoding the response, and so there's one way of checking
            // across all response types.
            rejectSegmentCacheEntry(segmentCacheEntry, Date.now() + 10 * 1000);
            return;
        }
        fulfillSegmentCacheEntry(segmentCacheEntry, serverData.rsc, serverData.loading, // TODO: The server does not currently provide per-segment stale time.
        // So we use the stale time of the route.
        route.staleAt, serverData.isPartial);
    } catch (error) {
        // Either the connection itself failed, or something bad happened while
        // decoding the response.
        rejectSegmentCacheEntry(segmentCacheEntry, Date.now() + 10 * 1000);
    }
}
async function fetchSegmentPrefetchResponse(href, segmentPath, nextUrl) {
    const headers = {
        [_approuterheaders.RSC_HEADER]: '1',
        [_approuterheaders.NEXT_ROUTER_PREFETCH_HEADER]: '1',
        [_approuterheaders.NEXT_ROUTER_SEGMENT_PREFETCH_HEADER]: segmentPath
    };
    if (nextUrl !== null) {
        headers[_approuterheaders.NEXT_URL] = nextUrl;
    }
    const fetchPriority = 'low';
    const responsePromise = (0, _fetchserverresponse.createFetch)(new URL(href), headers, fetchPriority);
    (0, _scheduler.trackPrefetchRequestBandwidth)(responsePromise);
    const response = await responsePromise;
    const contentType = response.headers.get('content-type');
    const isFlightResponse = contentType && contentType.startsWith(_approuterheaders.RSC_CONTENT_TYPE_HEADER);
    if (!response.ok || !isFlightResponse) {
        return null;
    }
    return response;
}
function createPrefetchResponseStream(originalFlightStream, lru, lruEntry) {
    // When PPR is enabled, prefetch streams may contain references that never
    // resolve, because that's how we encode dynamic data access. In the decoded
    // object returned by the Flight client, these are reified into hanging
    // promises that suspend during render, which is effectively what we want.
    // The UI resolves when it switches to the dynamic data stream
    // (via useDeferredValue(dynamic, static)).
    //
    // However, the Flight implementation currently errors if the server closes
    // the response before all the references are resolved. As a cheat to work
    // around this, we wrap the original stream in a new stream that never closes,
    // and therefore doesn't error.
    //
    // While processing the original stream, we also incrementally update the size
    // of the cache entry in the LRU.
    let totalByteLength = 0;
    const reader = originalFlightStream.getReader();
    return new ReadableStream({
        async pull (controller) {
            while(true){
                const { done, value } = await reader.read();
                if (!done) {
                    // Pass to the target stream and keep consuming the Flight response
                    // from the server.
                    controller.enqueue(value);
                    // Incrementally update the size of the cache entry in the LRU.
                    // NOTE: Since prefetch responses are delivered in a single chunk,
                    // it's not really necessary to do this streamingly, but I'm doing it
                    // anyway in case this changes in the future.
                    totalByteLength += value.byteLength;
                    lru.updateSize(lruEntry, totalByteLength);
                    continue;
                }
                // The server stream has closed. Exit, but intentionally do not close
                // the target stream.
                return;
            }
        }
    });
}
function createPromiseWithResolvers() {
    // Shim of Stage 4 Promise.withResolvers proposal
    let resolve;
    let reject;
    const promise = new Promise((res, rej)=>{
        resolve = res;
        reject = rej;
    });
    return {
        resolve: resolve,
        reject: reject,
        promise
    };
}

if ((typeof exports.default === 'function' || (typeof exports.default === 'object' && exports.default !== null)) && typeof exports.default.__esModule === 'undefined') {
  Object.defineProperty(exports.default, '__esModule', { value: true });
  Object.assign(exports.default, exports);
  module.exports = exports.default;
}

//# sourceMappingURL=cache.js.map