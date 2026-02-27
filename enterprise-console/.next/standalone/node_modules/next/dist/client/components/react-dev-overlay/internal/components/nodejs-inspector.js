"use strict";
Object.defineProperty(exports, "__esModule", {
    value: true
});
Object.defineProperty(exports, "NodejsInspectorCopyButton", {
    enumerable: true,
    get: function() {
        return NodejsInspectorCopyButton;
    }
});
const _jsxruntime = require("react/jsx-runtime");
const _copybutton = require("./copy-button");
// Inline this helper to avoid widely used across the codebase,
// as for this feature the Chrome detector doesn't need to be super accurate.
function isChrome() {
    if (typeof window === 'undefined') return false;
    const isChromium = 'chrome' in window && window.chrome;
    const vendorName = window.navigator.vendor;
    return isChromium !== null && isChromium !== undefined && vendorName === 'Google Inc.';
}
const isChromeBrowser = isChrome();
function NodeJsIcon(props) {
    return /*#__PURE__*/ (0, _jsxruntime.jsxs)("svg", {
        width: "44",
        height: "44",
        viewBox: "0 0 44 44",
        fill: "none",
        xmlns: "http://www.w3.org/2000/svg",
        ...props,
        children: [
            /*#__PURE__*/ (0, _jsxruntime.jsxs)("g", {
                clipPath: "url(#clip0_1546_2)",
                children: [
                    /*#__PURE__*/ (0, _jsxruntime.jsx)("path", {
                        d: "M22 0L41.0526 11V33L22 44L2.94744 33V11L22 0Z",
                        fill: "#71BD55"
                    }),
                    /*#__PURE__*/ (0, _jsxruntime.jsx)("path", {
                        d: "M41.0493 11.0001L41.0493 33L22 1.5583e-07L41.0493 11.0001Z",
                        fill: "#A1DF83"
                    })
                ]
            }),
            /*#__PURE__*/ (0, _jsxruntime.jsx)("defs", {
                children: /*#__PURE__*/ (0, _jsxruntime.jsx)("clipPath", {
                    id: "clip0_1546_2",
                    children: /*#__PURE__*/ (0, _jsxruntime.jsx)("rect", {
                        width: "44",
                        height: "44",
                        fill: "white"
                    })
                })
            })
        ]
    });
}
function NodeJsDisabledIcon(props) {
    return /*#__PURE__*/ (0, _jsxruntime.jsxs)("svg", {
        width: "44",
        height: "44",
        viewBox: "0 0 44 44",
        fill: "none",
        xmlns: "http://www.w3.org/2000/svg",
        ...props,
        children: [
            /*#__PURE__*/ (0, _jsxruntime.jsx)("path", {
                d: "M4.44744 11.866L22 1.73205L39.5526 11.866V32.134L22 42.2679L4.44744 32.134V11.866Z",
                stroke: "currentColor",
                fill: "transparent",
                strokeWidth: "3",
                strokeLinejoin: "round"
            }),
            /*#__PURE__*/ (0, _jsxruntime.jsx)("path", {
                d: "M22 2L39 32",
                stroke: "currentColor",
                strokeWidth: "3",
                strokeLinecap: "round"
            })
        ]
    });
}
const label = 'Learn more about enabling Node.js inspector for server code with Chrome DevTools';
function NodejsInspectorCopyButton(param) {
    let { devtoolsFrontendUrl } = param;
    const content = devtoolsFrontendUrl || '';
    const disabled = !content || !isChromeBrowser;
    if (disabled) {
        return /*#__PURE__*/ (0, _jsxruntime.jsx)("a", {
            title: label,
            "aria-label": label,
            className: "nextjs-data-runtime-error-inspect-link",
            href: "https://nextjs.org/docs/app/building-your-application/configuring/debugging#server-side-code",
            target: "_blank",
            rel: "noopener noreferrer",
            children: /*#__PURE__*/ (0, _jsxruntime.jsx)(NodeJsDisabledIcon, {
                width: 16,
                height: 16
            })
        });
    }
    return /*#__PURE__*/ (0, _jsxruntime.jsx)(_copybutton.CopyButton, {
        "data-nextjs-data-runtime-error-copy-devtools-url": true,
        actionLabel: 'Copy Chrome DevTools URL',
        successLabel: "Copied",
        content: content,
        icon: /*#__PURE__*/ (0, _jsxruntime.jsx)(NodeJsIcon, {
            width: 16,
            height: 16
        })
    });
}

if ((typeof exports.default === 'function' || (typeof exports.default === 'object' && exports.default !== null)) && typeof exports.default.__esModule === 'undefined') {
  Object.defineProperty(exports.default, '__esModule', { value: true });
  Object.assign(exports.default, exports);
  module.exports = exports.default;
}

//# sourceMappingURL=nodejs-inspector.js.map