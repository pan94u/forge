"use strict";
Object.defineProperty(exports, "__esModule", {
    value: true
});
0 && (module.exports = {
    pingPrefetchTask: null,
    schedulePrefetchTask: null,
    spawnPrefetchSubtask: null,
    trackPrefetchRequestBandwidth: null
});
function _export(target, all) {
    for(var name in all)Object.defineProperty(target, name, {
        enumerable: true,
        get: all[name]
    });
}
_export(exports, {
    pingPrefetchTask: function() {
        return pingPrefetchTask;
    },
    schedulePrefetchTask: function() {
        return schedulePrefetchTask;
    },
    spawnPrefetchSubtask: function() {
        return spawnPrefetchSubtask;
    },
    trackPrefetchRequestBandwidth: function() {
        return trackPrefetchRequestBandwidth;
    }
});
const _cache = require("./cache");
const scheduleMicrotask = typeof queueMicrotask === 'function' ? queueMicrotask : (fn)=>Promise.resolve().then(fn).catch((error)=>setTimeout(()=>{
            throw error;
        }));
const taskHeap = [];
// This is intentionally low so that when a navigation happens, the browser's
// internal network queue is not already saturated with prefetch requests.
const MAX_CONCURRENT_PREFETCH_REQUESTS = 3;
let inProgressRequests = 0;
let sortIdCounter = 0;
let didScheduleMicrotask = false;
function schedulePrefetchTask(key) {
    // Spawn a new prefetch task
    const task = {
        key,
        sortId: sortIdCounter++,
        isBlocked: false,
        _heapIndex: -1
    };
    heapPush(taskHeap, task);
    // Schedule an async task to process the queue.
    //
    // The main reason we process the queue in an async task is for batching.
    // It's common for a single JS task/event to trigger multiple prefetches.
    // By deferring to a microtask, we only process the queue once per JS task.
    // If they have different priorities, it also ensures they are processed in
    // the optimal order.
    ensureWorkIsScheduled();
}
function ensureWorkIsScheduled() {
    if (didScheduleMicrotask || !hasNetworkBandwidth()) {
        // Either we already scheduled a task to process the queue, or there are
        // too many concurrent requests in progress. In the latter case, the
        // queue will resume processing once more bandwidth is available.
        return;
    }
    didScheduleMicrotask = true;
    scheduleMicrotask(processQueueInMicrotask);
}
/**
 * Checks if we've exceeded the maximum number of concurrent prefetch requests,
 * to avoid saturating the browser's internal network queue. This is a
 * cooperative limit — prefetch tasks should check this before issuing
 * new requests.
 */ function hasNetworkBandwidth() {
    // TODO: Also check if there's an in-progress navigation. We should never
    // add prefetch requests to the network queue if an actual navigation is
    // taking place, to ensure there's sufficient bandwidth for render-blocking
    // data and resources.
    return inProgressRequests < MAX_CONCURRENT_PREFETCH_REQUESTS;
}
function trackPrefetchRequestBandwidth(promiseForServerData) {
    inProgressRequests++;
    promiseForServerData.then(onPrefetchRequestCompletion, onPrefetchRequestCompletion);
}
const noop = ()=>{};
function spawnPrefetchSubtask(promise) {
    // When the scheduler spawns an async task, we don't await its result
    // directly. Instead, the async task writes its result directly into the
    // cache, then pings the scheduler to continue.
    //
    // This function only exists to prevent warnings about unhandled promises.
    promise.then(noop, noop);
}
function onPrefetchRequestCompletion() {
    inProgressRequests--;
    // Notify the scheduler that we have more bandwidth, and can continue
    // processing tasks.
    ensureWorkIsScheduled();
}
function pingPrefetchTask(task) {
    // "Ping" a prefetch that's already in progress to notify it of new data.
    if (!task.isBlocked) {
        // Prefetch is already queued.
        return;
    }
    // Unblock the task and requeue it.
    task.isBlocked = false;
    heapPush(taskHeap, task);
    ensureWorkIsScheduled();
}
function processQueueInMicrotask() {
    didScheduleMicrotask = false;
    // We aim to minimize how often we read the current time. Since nearly all
    // functions in the prefetch scheduler are synchronous, we can read the time
    // once and pass it as an argument wherever it's needed.
    const now = Date.now();
    // Process the task queue until we run out of network bandwidth.
    let task = heapPeek(taskHeap);
    while(task !== null && hasNetworkBandwidth()){
        const route = (0, _cache.requestRouteCacheEntryFromCache)(now, task);
        const exitStatus = pingRouteTree(now, task, route);
        switch(exitStatus){
            case 0:
                // The task yielded because there are too many requests in progress.
                // Stop processing tasks until we have more bandwidth.
                return;
            case 1:
                // The task is blocked. It needs more data before it can proceed.
                // Keep the task out of the queue until the server responds.
                task.isBlocked = true;
                // Continue to the next task
                heapPop(taskHeap);
                task = heapPeek(taskHeap);
                continue;
            case 2:
                // The prefetch is complete. Continue to the next task.
                heapPop(taskHeap);
                task = heapPeek(taskHeap);
                continue;
            default:
                {
                    const _exhaustiveCheck = exitStatus;
                    return;
                }
        }
    }
}
function pingRouteTree(now, task, route) {
    switch(route.status){
        case _cache.EntryStatus.Pending:
            {
                // Still pending. We can't start prefetching the segments until the route
                // tree has loaded.
                const blockedTasks = route.blockedTasks;
                if (blockedTasks === null) {
                    route.blockedTasks = new Set([
                        task
                    ]);
                } else {
                    blockedTasks.add(task);
                }
                return 1;
            }
        case _cache.EntryStatus.Rejected:
            {
                // Route tree failed to load. Treat as a 404.
                return 2;
            }
        case _cache.EntryStatus.Fulfilled:
            {
                // Recursively fill in the segment tree.
                if (!hasNetworkBandwidth()) {
                    // Stop prefetching segments until there's more bandwidth.
                    return 0;
                }
                const tree = route.tree;
                (0, _cache.requestSegmentEntryFromCache)(now, task, route, tree.path, '');
                return pingSegmentTree(now, task, route, tree);
            }
        default:
            {
                const _exhaustiveCheck = route;
                return 2;
            }
    }
}
function pingSegmentTree(now, task, route, tree) {
    if (tree.slots !== null) {
        // Recursively ping the children.
        for(const parallelRouteKey in tree.slots){
            const childTree = tree.slots[parallelRouteKey];
            if (!hasNetworkBandwidth()) {
                // Stop prefetching segments until there's more bandwidth.
                return 0;
            } else {
                const childPath = childTree.path;
                const childToken = childTree.token;
                (0, _cache.requestSegmentEntryFromCache)(now, task, route, childPath, childToken);
            }
            const childExitStatus = pingSegmentTree(now, task, route, childTree);
            if (childExitStatus === 0) {
                // Child yielded without finishing.
                return 0;
            }
        }
    }
    // This segment and all its children have finished prefetching.
    return 2;
}
// -----------------------------------------------------------------------------
// The remainider of the module is a MinHeap implementation. Try not to put any
// logic below here unless it's related to the heap algorithm. We can extract
// this to a separate module if/when we need multiple kinds of heaps.
// -----------------------------------------------------------------------------
function compareQueuePriority(a, b) {
    // Since the queue is a MinHeap, this should return a positive number if b is
    // higher priority than a, and a negative number if a is higher priority
    // than b.
    //
    // sortId is an incrementing counter assigned to prefetches. We want to
    // process the newest prefetches first.
    return b.sortId - a.sortId;
}
function heapPush(heap, node) {
    const index = heap.length;
    heap.push(node);
    node._heapIndex = index;
    heapSiftUp(heap, node, index);
}
function heapPeek(heap) {
    return heap.length === 0 ? null : heap[0];
}
function heapPop(heap) {
    if (heap.length === 0) {
        return null;
    }
    const first = heap[0];
    first._heapIndex = -1;
    const last = heap.pop();
    if (last !== first) {
        heap[0] = last;
        last._heapIndex = 0;
        heapSiftDown(heap, last, 0);
    }
    return first;
}
// Not currently used, but will be once we add the ability to update a
// task's priority.
// function heapSift(heap: Array<PrefetchTask>, node: PrefetchTask) {
//   const index = node._heapIndex
//   if (index !== -1) {
//     const parentIndex = (index - 1) >>> 1
//     const parent = heap[parentIndex]
//     if (compareQueuePriority(parent, node) > 0) {
//       // The parent is larger. Sift up.
//       heapSiftUp(heap, node, index)
//     } else {
//       // The parent is smaller (or equal). Sift down.
//       heapSiftDown(heap, node, index)
//     }
//   }
// }
function heapSiftUp(heap, node, i) {
    let index = i;
    while(index > 0){
        const parentIndex = index - 1 >>> 1;
        const parent = heap[parentIndex];
        if (compareQueuePriority(parent, node) > 0) {
            // The parent is larger. Swap positions.
            heap[parentIndex] = node;
            node._heapIndex = parentIndex;
            heap[index] = parent;
            parent._heapIndex = index;
            index = parentIndex;
        } else {
            // The parent is smaller. Exit.
            return;
        }
    }
}
function heapSiftDown(heap, node, i) {
    let index = i;
    const length = heap.length;
    const halfLength = length >>> 1;
    while(index < halfLength){
        const leftIndex = (index + 1) * 2 - 1;
        const left = heap[leftIndex];
        const rightIndex = leftIndex + 1;
        const right = heap[rightIndex];
        // If the left or right node is smaller, swap with the smaller of those.
        if (compareQueuePriority(left, node) < 0) {
            if (rightIndex < length && compareQueuePriority(right, left) < 0) {
                heap[index] = right;
                right._heapIndex = index;
                heap[rightIndex] = node;
                node._heapIndex = rightIndex;
                index = rightIndex;
            } else {
                heap[index] = left;
                left._heapIndex = index;
                heap[leftIndex] = node;
                node._heapIndex = leftIndex;
                index = leftIndex;
            }
        } else if (rightIndex < length && compareQueuePriority(right, node) < 0) {
            heap[index] = right;
            right._heapIndex = index;
            heap[rightIndex] = node;
            node._heapIndex = rightIndex;
            index = rightIndex;
        } else {
            // Neither child is smaller. Exit.
            return;
        }
    }
}

if ((typeof exports.default === 'function' || (typeof exports.default === 'object' && exports.default !== null)) && typeof exports.default.__esModule === 'undefined') {
  Object.defineProperty(exports.default, '__esModule', { value: true });
  Object.assign(exports.default, exports);
  module.exports = exports.default;
}

//# sourceMappingURL=scheduler.js.map