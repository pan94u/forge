"use strict";
Object.defineProperty(exports, "__esModule", {
    value: true
});
Object.defineProperty(exports, "attachHydrationErrorState", {
    enumerable: true,
    get: function() {
        return attachHydrationErrorState;
    }
});
const _ishydrationerror = require("../../../is-hydration-error");
const _hydrationerrorinfo = require("./hydration-error-info");
function attachHydrationErrorState(error) {
    if ((0, _ishydrationerror.isHydrationError)(error) && !error.message.includes('https://nextjs.org/docs/messages/react-hydration-error')) {
        const reactHydrationDiffSegments = (0, _hydrationerrorinfo.getReactHydrationDiffSegments)(error.message);
        let parsedHydrationErrorState = {};
        if (reactHydrationDiffSegments) {
            parsedHydrationErrorState = {
                ...error.details,
                ..._hydrationerrorinfo.hydrationErrorState,
                warning: _hydrationerrorinfo.hydrationErrorState.warning || [
                    (0, _ishydrationerror.getDefaultHydrationErrorMessage)()
                ],
                notes: reactHydrationDiffSegments[0],
                reactOutputComponentDiff: reactHydrationDiffSegments[1]
            };
        } else {
            // If there's any extra information in the error message to display,
            // append it to the error message details property
            if (_hydrationerrorinfo.hydrationErrorState.warning) {
                // The patched console.error found hydration errors logged by React
                // Append the logged warning to the error message
                parsedHydrationErrorState = {
                    ...error.details,
                    // It contains the warning, component stack, server and client tag names
                    ..._hydrationerrorinfo.hydrationErrorState
                };
            }
        }
        ;
        error.details = parsedHydrationErrorState;
    }
}

if ((typeof exports.default === 'function' || (typeof exports.default === 'object' && exports.default !== null)) && typeof exports.default.__esModule === 'undefined') {
  Object.defineProperty(exports.default, '__esModule', { value: true });
  Object.assign(exports.default, exports);
  module.exports = exports.default;
}

//# sourceMappingURL=attach-hydration-error-state.js.map