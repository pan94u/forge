"use strict";
Object.defineProperty(exports, "__esModule", {
    value: true
});
Object.defineProperty(exports, "enqueueConsecutiveDedupedError", {
    enumerable: true,
    get: function() {
        return enqueueConsecutiveDedupedError;
    }
});
const _ishydrationerror = require("../../../is-hydration-error");
function enqueueConsecutiveDedupedError(queue, error) {
    const isFront = (0, _ishydrationerror.isHydrationError)(error);
    const previousError = isFront ? queue[0] : queue[queue.length - 1];
    // Compare the error stack to dedupe the consecutive errors
    if (previousError && previousError.stack === error.stack) {
        return;
    }
    // TODO: change all to push error into errorQueue,
    // currently there's a async api error is always erroring while hydration error showing up.
    // Move hydration error to the front of the queue to unblock.
    if (isFront) {
        queue.unshift(error);
    } else {
        queue.push(error);
    }
}

if ((typeof exports.default === 'function' || (typeof exports.default === 'object' && exports.default !== null)) && typeof exports.default.__esModule === 'undefined') {
  Object.defineProperty(exports.default, '__esModule', { value: true });
  Object.assign(exports.default, exports);
  module.exports = exports.default;
}

//# sourceMappingURL=enqueue-client-error.js.map