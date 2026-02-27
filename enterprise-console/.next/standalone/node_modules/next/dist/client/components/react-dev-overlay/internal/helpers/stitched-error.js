"use strict";
Object.defineProperty(exports, "__esModule", {
    value: true
});
Object.defineProperty(exports, "getReactStitchedError", {
    enumerable: true,
    get: function() {
        return getReactStitchedError;
    }
});
const _interop_require_default = require("@swc/helpers/_/_interop_require_default");
const _react = /*#__PURE__*/ _interop_require_default._(require("react"));
const _iserror = /*#__PURE__*/ _interop_require_default._(require("../../../../../lib/is-error"));
const REACT_ERROR_STACK_BOTTOM_FRAME = 'react-stack-bottom-frame';
const REACT_ERROR_STACK_BOTTOM_FRAME_REGEX = new RegExp("(at " + REACT_ERROR_STACK_BOTTOM_FRAME + " )|(" + REACT_ERROR_STACK_BOTTOM_FRAME + "\\@)");
const captureOwnerStack = _react.default.captureOwnerStack ? _react.default.captureOwnerStack : ()=>'';
function getReactStitchedError(err) {
    if (typeof _react.default.captureOwnerStack !== 'function') {
        return err;
    }
    const isErrorInstance = (0, _iserror.default)(err);
    const originStack = isErrorInstance ? err.stack || '' : '';
    const originMessage = isErrorInstance ? err.message : '';
    const stackLines = originStack.split('\n');
    const indexOfSplit = stackLines.findIndex((line)=>REACT_ERROR_STACK_BOTTOM_FRAME_REGEX.test(line));
    const isOriginalReactError = indexOfSplit >= 0 // has the react-stack-bottom-frame
    ;
    let newStack = isOriginalReactError ? stackLines.slice(0, indexOfSplit).join('\n') : originStack;
    const newError = new Error(originMessage);
    // Copy all enumerable properties, e.g. digest
    Object.assign(newError, err);
    newError.stack = newStack;
    // Avoid duplicate overriding stack frames
    appendOwnerStack(newError);
    return newError;
}
function appendOwnerStack(error) {
    let stack = error.stack || '';
    // Avoid duplicate overriding stack frames
    const ownerStack = captureOwnerStack();
    if (ownerStack && stack.endsWith(ownerStack) === false) {
        stack += ownerStack;
        // Override stack
        error.stack = stack;
    }
}

if ((typeof exports.default === 'function' || (typeof exports.default === 'object' && exports.default !== null)) && typeof exports.default.__esModule === 'undefined') {
  Object.defineProperty(exports.default, '__esModule', { value: true });
  Object.assign(exports.default, exports);
  module.exports = exports.default;
}

//# sourceMappingURL=stitched-error.js.map