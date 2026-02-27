"use strict";
Object.defineProperty(exports, "__esModule", {
    value: true
});
0 && (module.exports = {
    NavigationResultTag: null,
    navigate: null
});
function _export(target, all) {
    for(var name in all)Object.defineProperty(target, name, {
        enumerable: true,
        get: all[name]
    });
}
_export(exports, {
    NavigationResultTag: function() {
        return NavigationResultTag;
    },
    navigate: function() {
        return navigate;
    }
});
const _fetchserverresponse = require("../router-reducer/fetch-server-response");
const _pprnavigations = require("../router-reducer/ppr-navigations");
const _createhreffromurl = require("../router-reducer/create-href-from-url");
const _cache = require("./cache");
const _cachekey = require("./cache-key");
var NavigationResultTag = /*#__PURE__*/ function(NavigationResultTag) {
    NavigationResultTag[NavigationResultTag["MPA"] = 0] = "MPA";
    NavigationResultTag[NavigationResultTag["Success"] = 1] = "Success";
    NavigationResultTag[NavigationResultTag["NoOp"] = 2] = "NoOp";
    NavigationResultTag[NavigationResultTag["Async"] = 3] = "Async";
    return NavigationResultTag;
}({});
const noOpNavigationResult = {
    tag: 2,
    data: null
};
function navigate(url, currentCacheNode, currentFlightRouterState, nextUrl) {
    const now = Date.now();
    const cacheKey = (0, _cachekey.createCacheKey)(url.href, nextUrl);
    const route = (0, _cache.readRouteCacheEntry)(now, cacheKey);
    if (route !== null && route.status === _cache.EntryStatus.Fulfilled) {
        // We have a matching prefetch.
        const snapshot = readRenderSnapshotFromCache(now, route.tree);
        const prefetchFlightRouterState = snapshot.flightRouterState;
        const prefetchSeedData = snapshot.seedData;
        const prefetchHead = route.head;
        const isPrefetchHeadPartial = route.isHeadPartial;
        const canonicalUrl = route.canonicalUrl;
        return navigateUsingPrefetchedRouteTree(url, nextUrl, currentCacheNode, currentFlightRouterState, prefetchFlightRouterState, prefetchSeedData, prefetchHead, isPrefetchHeadPartial, canonicalUrl);
    }
    // There's no matching prefetch for this route in the cache.
    return {
        tag: 3,
        data: navigateDynamicallyWithNoPrefetch(url, nextUrl, currentCacheNode, currentFlightRouterState)
    };
}
function navigateUsingPrefetchedRouteTree(url, nextUrl, currentCacheNode, currentFlightRouterState, prefetchFlightRouterState, prefetchSeedData, prefetchHead, isPrefetchHeadPartial, canonicalUrl) {
    // Recursively construct a prefetch tree by reading from the Segment Cache. To
    // maintain compatibility, we output the same data structures as the old
    // prefetching implementation: FlightRouterState and CacheNodeSeedData.
    // TODO: Eventually updateCacheNodeOnNavigation (or the equivalent) should
    // read from the Segment Cache directly. It's only structured this way for now
    // so we can share code with the old prefetching implementation.
    const task = (0, _pprnavigations.updateCacheNodeOnNavigation)(currentCacheNode, currentFlightRouterState, prefetchFlightRouterState, prefetchSeedData, prefetchHead, isPrefetchHeadPartial);
    if (task !== null) {
        if (task.needsDynamicRequest) {
            const promiseForDynamicServerResponse = (0, _fetchserverresponse.fetchServerResponse)(url, {
                flightRouterState: currentFlightRouterState,
                nextUrl
            });
            (0, _pprnavigations.listenForDynamicRequest)(task, promiseForDynamicServerResponse);
        } else {
        // The prefetched tree does not contain dynamic holes — it's
        // fully static. We can skip the dynamic request.
        }
        return navigationTaskToResult(task, currentCacheNode, canonicalUrl);
    }
    // The server sent back an empty tree patch. There's nothing to update.
    return noOpNavigationResult;
}
function navigationTaskToResult(task, currentCacheNode, canonicalUrl) {
    const newCacheNode = task.node;
    return {
        tag: 1,
        data: {
            flightRouterState: task.route,
            cacheNode: newCacheNode !== null ? newCacheNode : currentCacheNode,
            canonicalUrl
        }
    };
}
function readRenderSnapshotFromCache(now, tree) {
    let childRouterStates = {};
    let childSeedDatas = {};
    const slots = tree.slots;
    if (slots !== null) {
        for(const parallelRouteKey in slots){
            const childTree = slots[parallelRouteKey];
            const childResult = readRenderSnapshotFromCache(now, childTree);
            childRouterStates[parallelRouteKey] = childResult.flightRouterState;
            childSeedDatas[parallelRouteKey] = childResult.seedData;
        }
    }
    let rsc = null;
    let loading = null;
    let isPartial = true;
    const segmentEntry = (0, _cache.readSegmentCacheEntry)(now, tree.path);
    if (segmentEntry !== null) {
        switch(segmentEntry.status){
            case _cache.EntryStatus.Fulfilled:
                {
                    // Happy path: a cache hit
                    rsc = segmentEntry.rsc;
                    loading = segmentEntry.loading;
                    isPartial = segmentEntry.isPartial;
                    break;
                }
            case _cache.EntryStatus.Pending:
                {
                    // We haven't received data for this segment yet, but there's already
                    // an in-progress request. Since it's extremely likely to arrive
                    // before the dynamic data response, we might as well use it.
                    const promiseForFulfilledEntry = (0, _cache.waitForSegmentCacheEntry)(segmentEntry);
                    rsc = promiseForFulfilledEntry.then((entry)=>entry !== null ? entry.rsc : null);
                    loading = promiseForFulfilledEntry.then((entry)=>entry !== null ? entry.loading : null);
                    // Since we don't know yet whether the segment is partial or fully
                    // static, we must assume it's partial; we can't skip the
                    // dynamic request.
                    isPartial = true;
                    break;
                }
            case _cache.EntryStatus.Rejected:
                break;
            default:
                {
                    const _exhaustiveCheck = segmentEntry;
                    break;
                }
        }
    }
    const extra = tree.extra;
    const flightRouterStateSegment = extra[0];
    const isRootLayout = extra[1];
    return {
        flightRouterState: [
            flightRouterStateSegment,
            childRouterStates,
            null,
            null,
            isRootLayout
        ],
        seedData: [
            flightRouterStateSegment,
            rsc,
            childSeedDatas,
            loading,
            isPartial
        ]
    };
}
async function navigateDynamicallyWithNoPrefetch(url, nextUrl, currentCacheNode, currentFlightRouterState) {
    // Runs when a navigation happens but there's no cached prefetch we can use.
    // Don't bother to wait for a prefetch response; go straight to a full
    // navigation that contains both static and dynamic data in a single stream.
    // (This is unlike the old navigation implementation, which instead blocks
    // the dynamic request until a prefetch request is received.)
    //
    // To avoid duplication of logic, we're going to pretend that the tree
    // returned by the dynamic request is, in fact, a prefetch tree. Then we can
    // use the same server response to write the actual data into the CacheNode
    // tree. So it's the same flow as the "happy path" (prefetch, then
    // navigation), except we use a single server response for both stages.
    const promiseForDynamicServerResponse = (0, _fetchserverresponse.fetchServerResponse)(url, {
        flightRouterState: currentFlightRouterState,
        nextUrl
    });
    const { flightData, canonicalUrl: canonicalUrlOverride } = await promiseForDynamicServerResponse;
    // TODO: Detect if the only thing that changed was the hash, like we do in
    // in navigateReducer
    if (typeof flightData === 'string') {
        // This is an MPA navigation.
        const newUrl = flightData;
        return {
            tag: 0,
            data: newUrl
        };
    }
    // Since the response format of dynamic requests and prefetches is slightly
    // different, we'll need to massage the data a bit. Create FlightRouterState
    // tree that simulates what we'd receive as the result of a prefetch.
    const prefetchFlightRouterState = simulatePrefetchTreeUsingDynamicTreePatch(currentFlightRouterState, flightData);
    // In our simulated prefetch payload, we pretend that there's no seed data
    // nor a prefetch head.
    const prefetchSeedData = null;
    const prefetchHead = null;
    const isPrefetchHeadPartial = true;
    const canonicalUrl = (0, _createhreffromurl.createHrefFromUrl)(canonicalUrlOverride ? canonicalUrlOverride : url);
    // Now we proceed exactly as we would for normal navigation.
    const task = (0, _pprnavigations.updateCacheNodeOnNavigation)(currentCacheNode, currentFlightRouterState, prefetchFlightRouterState, prefetchSeedData, prefetchHead, isPrefetchHeadPartial);
    if (task !== null) {
        if (task.needsDynamicRequest) {
            (0, _pprnavigations.listenForDynamicRequest)(task, promiseForDynamicServerResponse);
        } else {
        // The prefetched tree does not contain dynamic holes — it's
        // fully static. We can skip the dynamic request.
        }
        return navigationTaskToResult(task, currentCacheNode, canonicalUrl);
    }
    // The server sent back an empty tree patch. There's nothing to update.
    return noOpNavigationResult;
}
function simulatePrefetchTreeUsingDynamicTreePatch(currentTree, flightData) {
    // Takes the current FlightRouterState and applies the router state patch
    // received from the server, to create a full FlightRouterState tree that we
    // can pretend was returned by a prefetch.
    //
    // (It sounds similar to what applyRouterStatePatch does, but it doesn't need
    // to handle stuff like interception routes or diffing since that will be
    // handled later.)
    let baseTree = currentTree;
    for (const { segmentPath, tree: treePatch } of flightData){
        // If the server sends us multiple tree patches, we only need to clone the
        // base tree when applying the first patch. After the first patch, we can
        // apply the remaining patches in place without copying.
        const canMutateInPlace = baseTree !== currentTree;
        baseTree = simulatePrefetchTreeUsingDynamicTreePatchImpl(baseTree, treePatch, segmentPath, canMutateInPlace, 0);
    }
    return baseTree;
}
function simulatePrefetchTreeUsingDynamicTreePatchImpl(baseRouterState, patch, segmentPath, canMutateInPlace, index) {
    if (index === segmentPath.length) {
        // We reached the part of the tree that we need to patch.
        return patch;
    }
    // segmentPath represents the parent path of subtree. It's a repeating
    // pattern of parallel route key and segment:
    //
    //   [string, Segment, string, Segment, string, Segment, ...]
    //
    // This path tells us which part of the base tree to apply the tree patch.
    //
    // NOTE: In the case of a fully dynamic request with no prefetch, we receive
    // the FlightRouterState patch in the same request as the dynamic data.
    // Therefore we don't need to worry about diffing the segment values; we can
    // assume the server sent us a correct result.
    const updatedParallelRouteKey = segmentPath[index];
    // const segment: Segment = segmentPath[index + 1] <-- Not used, see note above
    const baseChildren = baseRouterState[1];
    const newChildren = {};
    for(const parallelRouteKey in baseChildren){
        if (parallelRouteKey === updatedParallelRouteKey) {
            const childBaseRouterState = baseChildren[parallelRouteKey];
            newChildren[parallelRouteKey] = simulatePrefetchTreeUsingDynamicTreePatchImpl(childBaseRouterState, patch, segmentPath, canMutateInPlace, // Advance the index by two and keep cloning until we reach
            // the end of the segment path.
            index + 2);
        } else {
            // This child is not being patched. Copy it over as-is.
            newChildren[parallelRouteKey] = baseChildren[parallelRouteKey];
        }
    }
    if (canMutateInPlace) {
        // We can mutate the base tree in place, because the base tree is already
        // a clone.
        baseRouterState[1] = newChildren;
        return baseRouterState;
    }
    // Clone all the fields except the children.
    //
    // Based on equivalent logic in apply-router-state-patch-to-tree, but should
    // confirm whether we need to copy all of these fields. Not sure the server
    // ever sends, e.g. the refetch marker.
    const clone = [
        baseRouterState[0],
        newChildren
    ];
    if (2 in baseRouterState) {
        clone[2] = baseRouterState[2];
    }
    if (3 in baseRouterState) {
        clone[3] = baseRouterState[3];
    }
    if (4 in baseRouterState) {
        clone[4] = baseRouterState[4];
    }
    return clone;
}

if ((typeof exports.default === 'function' || (typeof exports.default === 'object' && exports.default !== null)) && typeof exports.default.__esModule === 'undefined') {
  Object.defineProperty(exports.default, '__esModule', { value: true });
  Object.assign(exports.default, exports);
  module.exports = exports.default;
}

//# sourceMappingURL=navigation.js.map