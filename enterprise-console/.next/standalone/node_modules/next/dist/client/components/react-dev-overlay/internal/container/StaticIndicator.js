"use strict";
Object.defineProperty(exports, "__esModule", {
    value: true
});
Object.defineProperty(exports, "StaticIndicator", {
    enumerable: true,
    get: function() {
        return StaticIndicator;
    }
});
const _interop_require_wildcard = require("@swc/helpers/_/_interop_require_wildcard");
const _jsxruntime = require("react/jsx-runtime");
const _react = /*#__PURE__*/ _interop_require_wildcard._(require("react"));
const _Toast = require("../components/Toast");
const _LightningBolt = require("../icons/LightningBolt");
const _CloseIcon = require("../icons/CloseIcon");
function StaticIndicator(param) {
    let { dispatcher } = param;
    return /*#__PURE__*/ (0, _jsxruntime.jsxs)(_Toast.Toast, {
        role: "status",
        className: "nextjs-static-indicator-toast-wrapper",
        children: [
            /*#__PURE__*/ (0, _jsxruntime.jsx)("div", {
                className: "nextjs-static-indicator-toast-icon",
                children: /*#__PURE__*/ (0, _jsxruntime.jsx)(_LightningBolt.LightningBolt, {})
            }),
            /*#__PURE__*/ (0, _jsxruntime.jsxs)("div", {
                className: "nextjs-static-indicator-toast-text",
                children: [
                    "Static route",
                    /*#__PURE__*/ (0, _jsxruntime.jsx)("button", {
                        onClick: ()=>{
                            var _localStorage;
                            // When dismissed, we hide the indicator for 1 hour. Store the
                            // timestamp for when to show it again.
                            const oneHourAway = Date.now() + 1 * 60 * 60 * 1000;
                            (_localStorage = localStorage) == null ? void 0 : _localStorage.setItem('__NEXT_DISMISS_PRERENDER_INDICATOR', oneHourAway.toString());
                            dispatcher == null ? void 0 : dispatcher.onStaticIndicator(false);
                        },
                        className: "nextjs-toast-hide-button",
                        "aria-label": "Hide static indicator",
                        children: /*#__PURE__*/ (0, _jsxruntime.jsx)(_CloseIcon.CloseIcon, {})
                    })
                ]
            })
        ]
    });
}

if ((typeof exports.default === 'function' || (typeof exports.default === 'object' && exports.default !== null)) && typeof exports.default.__esModule === 'undefined') {
  Object.defineProperty(exports.default, '__esModule', { value: true });
  Object.assign(exports.default, exports);
  module.exports = exports.default;
}

//# sourceMappingURL=StaticIndicator.js.map