"use strict";
Object.defineProperty(exports, "__esModule", {
    value: true
});
Object.defineProperty(exports, "CopyButton", {
    enumerable: true,
    get: function() {
        return CopyButton;
    }
});
const _interop_require_wildcard = require("@swc/helpers/_/_interop_require_wildcard");
const _jsxruntime = require("react/jsx-runtime");
const _react = /*#__PURE__*/ _interop_require_wildcard._(require("react"));
function useCopyLegacy(content) {
    // This would be simpler with useActionState but we need to support React 18 here.
    // React 18 also doesn't have async transitions.
    const [copyState, dispatch] = _react.useReducer((state, action)=>{
        if (action.type === 'reset') {
            return {
                state: 'initial'
            };
        }
        if (action.type === 'copied') {
            return {
                state: 'success'
            };
        }
        if (action.type === 'copying') {
            return {
                state: 'pending'
            };
        }
        if (action.type === 'error') {
            return {
                state: 'error',
                error: action.error
            };
        }
        return state;
    }, {
        state: 'initial'
    });
    function copy() {
        if (isPending) {
            return;
        }
        if (!navigator.clipboard) {
            dispatch({
                type: 'error',
                error: new Error('Copy to clipboard is not supported in this browser')
            });
        } else {
            dispatch({
                type: 'copying'
            });
            navigator.clipboard.writeText(content).then(()=>{
                dispatch({
                    type: 'copied'
                });
            }, (error)=>{
                dispatch({
                    type: 'error',
                    error
                });
            });
        }
    }
    const reset = _react.useCallback(()=>{
        dispatch({
            type: 'reset'
        });
    }, []);
    const isPending = copyState.state === 'pending';
    return [
        copyState,
        copy,
        reset,
        isPending
    ];
}
function useCopyModern(content) {
    const [copyState, dispatch, isPending] = _react.useActionState((state, action)=>{
        if (action === 'reset') {
            return {
                state: 'initial'
            };
        }
        if (action === 'copy') {
            if (!navigator.clipboard) {
                return {
                    state: 'error',
                    error: new Error('Copy to clipboard is not supported in this browser')
                };
            }
            return navigator.clipboard.writeText(content).then(()=>{
                return {
                    state: 'success'
                };
            }, (error)=>{
                return {
                    state: 'error',
                    error
                };
            });
        }
        return state;
    }, {
        state: 'initial'
    });
    function copy() {
        _react.startTransition(()=>{
            dispatch('copy');
        });
    }
    const reset = _react.useCallback(()=>{
        dispatch('reset');
    }, [
        // TODO: `dispatch` from `useActionState` is not reactive.
        // Remove from dependencies once https://github.com/facebook/react/pull/29665 is released.
        dispatch
    ]);
    return [
        copyState,
        copy,
        reset,
        isPending
    ];
}
const useCopy = typeof _react.useActionState === 'function' ? useCopyModern : useCopyLegacy;
function CopyButton(param) {
    let { actionLabel, successLabel, content, icon, disabled, ...props } = param;
    const [copyState, copy, reset, isPending] = useCopy(content);
    const error = copyState.state === 'error' ? copyState.error : null;
    _react.useEffect(()=>{
        if (error !== null) {
            // Additional console.error to get the stack.
            console.error(error);
        }
    }, [
        error
    ]);
    _react.useEffect(()=>{
        if (copyState.state === 'success') {
            const timeoutId = setTimeout(()=>{
                reset();
            }, 2000);
            return ()=>{
                clearTimeout(timeoutId);
            };
        }
    }, [
        isPending,
        copyState.state,
        reset
    ]);
    const isDisabled = isPending || disabled;
    const label = copyState.state === 'success' ? successLabel : actionLabel;
    // Assign default icon
    const renderedIcon = copyState.state === 'success' ? /*#__PURE__*/ (0, _jsxruntime.jsx)(CopySuccessIcon, {}) : icon || /*#__PURE__*/ (0, _jsxruntime.jsx)(CopyIcon, {});
    return /*#__PURE__*/ (0, _jsxruntime.jsxs)("button", {
        ...props,
        type: "button",
        title: label,
        "aria-label": label,
        "aria-disabled": isDisabled,
        "data-nextjs-data-runtime-error-copy-button": true,
        className: "nextjs-data-runtime-error-copy-button nextjs-data-runtime-error-copy-button--" + copyState.state,
        onClick: ()=>{
            if (!isDisabled) {
                copy();
            }
        },
        children: [
            renderedIcon,
            copyState.state === 'error' ? " " + copyState.error : null
        ]
    });
}
function CopyIcon() {
    return /*#__PURE__*/ (0, _jsxruntime.jsxs)("svg", {
        width: "16",
        height: "16",
        viewBox: "0 0 24 24",
        fill: "transparent",
        stroke: "currentColor",
        strokeWidth: "1.5",
        strokeLinecap: "round",
        strokeLinejoin: "round",
        children: [
            /*#__PURE__*/ (0, _jsxruntime.jsx)("rect", {
                width: "14",
                height: "14",
                x: "8",
                y: "8",
                rx: "2",
                ry: "2"
            }),
            /*#__PURE__*/ (0, _jsxruntime.jsx)("path", {
                d: "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"
            })
        ]
    });
}
function CopySuccessIcon() {
    return /*#__PURE__*/ (0, _jsxruntime.jsx)("svg", {
        height: "16",
        xlinkTitle: "copied",
        viewBox: "0 0 16 16",
        width: "16",
        stroke: "currentColor",
        fill: "currentColor",
        children: /*#__PURE__*/ (0, _jsxruntime.jsx)("path", {
            d: "M13.78 4.22a.75.75 0 0 1 0 1.06l-7.25 7.25a.75.75 0 0 1-1.06 0L2.22 9.28a.751.751 0 0 1 .018-1.042.751.751 0 0 1 1.042-.018L6 10.94l6.72-6.72a.75.75 0 0 1 1.06 0Z"
        })
    });
}

if ((typeof exports.default === 'function' || (typeof exports.default === 'object' && exports.default !== null)) && typeof exports.default.__esModule === 'undefined') {
  Object.defineProperty(exports.default, '__esModule', { value: true });
  Object.assign(exports.default, exports);
  module.exports = exports.default;
}

//# sourceMappingURL=index.js.map