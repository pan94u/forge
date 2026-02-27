/**
 * Describes the different fallback modes that a given page can have.
 */ "use strict";
Object.defineProperty(exports, "__esModule", {
    value: true
});
0 && (module.exports = {
    FallbackMode: null,
    fallbackModeToFallbackField: null,
    fallbackModeToStaticPathsResult: null,
    parseFallbackField: null,
    parseStaticPathsResult: null
});
function _export(target, all) {
    for(var name in all)Object.defineProperty(target, name, {
        enumerable: true,
        get: all[name]
    });
}
_export(exports, {
    FallbackMode: function() {
        return FallbackMode;
    },
    fallbackModeToFallbackField: function() {
        return fallbackModeToFallbackField;
    },
    fallbackModeToStaticPathsResult: function() {
        return fallbackModeToStaticPathsResult;
    },
    parseFallbackField: function() {
        return parseFallbackField;
    },
    parseStaticPathsResult: function() {
        return parseStaticPathsResult;
    }
});
var FallbackMode = /*#__PURE__*/ function(FallbackMode) {
    /**
   * A BLOCKING_STATIC_RENDER fallback will block the request until the page is
   * generated. No fallback page will be rendered, and users will have to wait
   * to render the page.
   */ FallbackMode["BLOCKING_STATIC_RENDER"] = "BLOCKING_STATIC_RENDER";
    /**
   * When set to PRERENDER, a fallback page will be sent to users in place of
   * forcing them to wait for the page to be generated. This allows the user to
   * see a rendered page earlier.
   */ FallbackMode["PRERENDER"] = "PRERENDER";
    /**
   * When set to NOT_FOUND, pages that are not already prerendered will result
   * in a not found response.
   */ FallbackMode["NOT_FOUND"] = "NOT_FOUND";
    return FallbackMode;
}({});
function parseFallbackField(fallbackField) {
    if (typeof fallbackField === 'string') {
        return "PRERENDER";
    } else if (fallbackField === null) {
        return "BLOCKING_STATIC_RENDER";
    } else if (fallbackField === false) {
        return "NOT_FOUND";
    } else if (fallbackField === undefined) {
        return undefined;
    } else {
        throw new Error(`Invalid fallback option: ${fallbackField}. Fallback option must be a string, null, undefined, or false.`);
    }
}
function fallbackModeToFallbackField(fallback, page) {
    switch(fallback){
        case "BLOCKING_STATIC_RENDER":
            return null;
        case "NOT_FOUND":
            return false;
        case "PRERENDER":
            if (!page) {
                throw new Error(`Invariant: expected a page to be provided when fallback mode is "${fallback}"`);
            }
            return page;
        default:
            throw new Error(`Invalid fallback mode: ${fallback}`);
    }
}
function parseStaticPathsResult(result) {
    if (result === true) {
        return "PRERENDER";
    } else if (result === 'blocking') {
        return "BLOCKING_STATIC_RENDER";
    } else {
        return "NOT_FOUND";
    }
}
function fallbackModeToStaticPathsResult(fallback) {
    switch(fallback){
        case "PRERENDER":
            return true;
        case "BLOCKING_STATIC_RENDER":
            return 'blocking';
        case "NOT_FOUND":
        default:
            return false;
    }
}

//# sourceMappingURL=fallback.js.map