// TypeScript trick to simulate opaque types, like in Flow.
"use strict";
Object.defineProperty(exports, "__esModule", {
    value: true
});
Object.defineProperty(exports, "createCacheKey", {
    enumerable: true,
    get: function() {
        return createCacheKey;
    }
});
function createCacheKey(originalHref, nextUrl) {
    const originalUrl = new URL(originalHref);
    // TODO: As of now, we never include search params in the cache key because
    // per-segment prefetch requests are always static, and cannot contain search
    // params. But to support <Link prefetch={true}>, we will sometimes populate
    // the cache with dynamic data, so this will have to change.
    originalUrl.search = '';
    const normalizedHref = originalUrl.href;
    const normalizedNextUrl = nextUrl;
    const cacheKey = {
        href: normalizedHref,
        nextUrl: normalizedNextUrl
    };
    return cacheKey;
}

if ((typeof exports.default === 'function' || (typeof exports.default === 'object' && exports.default !== null)) && typeof exports.default.__esModule === 'undefined') {
  Object.defineProperty(exports.default, '__esModule', { value: true });
  Object.assign(exports.default, exports);
  module.exports = exports.default;
}

//# sourceMappingURL=cache-key.js.map