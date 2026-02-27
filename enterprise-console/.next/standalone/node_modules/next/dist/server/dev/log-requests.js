"use strict";
Object.defineProperty(exports, "__esModule", {
    value: true
});
Object.defineProperty(exports, "logRequests", {
    enumerable: true,
    get: function() {
        return logRequests;
    }
});
const _picocolors = require("../../lib/picocolors");
const _url = require("../../lib/url");
const _requestmeta = require("../request-meta");
function logRequests(options) {
    const { request, response, loggingConfig, requestDurationInMs } = options;
    logIncomingRequest({
        request,
        requestDurationInMs,
        statusCode: response.statusCode
    });
    if (request.fetchMetrics) {
        for (const fetchMetric of request.fetchMetrics){
            logFetchMetric(fetchMetric, loggingConfig);
        }
    }
}
function logIncomingRequest(options) {
    const { request, requestDurationInMs, statusCode } = options;
    const isRSC = (0, _requestmeta.getRequestMeta)(request, 'isRSCRequest');
    const url = isRSC ? (0, _url.stripNextRscUnionQuery)(request.url) : request.url;
    const statusCodeColor = statusCode < 200 ? _picocolors.white : statusCode < 300 ? _picocolors.green : statusCode < 400 ? _picocolors.blue : statusCode < 500 ? _picocolors.yellow : _picocolors.red;
    const coloredStatus = statusCodeColor(statusCode.toString());
    return writeLine(`${request.method} ${url} ${coloredStatus} in ${requestDurationInMs}ms`);
}
function logFetchMetric(fetchMetric, loggingConfig) {
    var _loggingConfig_fetches;
    let { cacheReason, cacheStatus, cacheWarning, end, method, start, status, url } = fetchMetric;
    if (cacheStatus === 'hmr' && !(loggingConfig == null ? void 0 : (_loggingConfig_fetches = loggingConfig.fetches) == null ? void 0 : _loggingConfig_fetches.hmrRefreshes)) {
        // Cache hits during HMR refreshes are intentionally not logged, unless
        // explicitly enabled in the logging config.
        return;
    }
    if (loggingConfig == null ? void 0 : loggingConfig.fetches) {
        if (url.length > 48 && !loggingConfig.fetches.fullUrl) {
            url = truncateUrl(url);
        }
        writeLine((0, _picocolors.white)(`${method} ${url} ${status} in ${Math.round(end - start)}ms ${formatCacheStatus(cacheStatus)}`), 1);
        if (cacheStatus === 'skip' || cacheStatus === 'miss') {
            writeLine((0, _picocolors.gray)(`Cache ${cacheStatus === 'skip' ? 'skipped' : 'missed'} reason: (${(0, _picocolors.white)(cacheReason)})`), 2);
        }
    } else if (cacheWarning) {
        // When logging for fetches is not enabled, we still want to print any
        // associated warnings, so we print the request first to provide context.
        writeLine((0, _picocolors.white)(`${method} ${url}`), 1);
    }
    if (cacheWarning) {
        writeLine(`${(0, _picocolors.yellow)((0, _picocolors.bold)('⚠'))} ${(0, _picocolors.white)(cacheWarning)}`, 2);
    }
}
function writeLine(text, indentationLevel = 0) {
    process.stdout.write(` ${'│ '.repeat(indentationLevel)}${text}\n`);
}
function truncate(text, maxLength) {
    return maxLength !== undefined && text.length > maxLength ? text.substring(0, maxLength) + '..' : text;
}
function truncateUrl(url) {
    const { protocol, host, pathname, search } = new URL(url);
    return protocol + '//' + truncate(host, 16) + truncate(pathname, 24) + truncate(search, 16);
}
function formatCacheStatus(cacheStatus) {
    switch(cacheStatus){
        case 'hmr':
            return (0, _picocolors.green)('(HMR cache)');
        case 'hit':
            return (0, _picocolors.green)('(cache hit)');
        default:
            return (0, _picocolors.yellow)(`(cache ${cacheStatus})`);
    }
}

//# sourceMappingURL=log-requests.js.map