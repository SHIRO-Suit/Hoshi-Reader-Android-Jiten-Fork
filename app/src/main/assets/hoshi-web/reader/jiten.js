(function () {
    const excludedTags = new Set(['SCRIPT', 'STYLE', 'NOSCRIPT', 'TEXTAREA', 'RT', 'RP']);
    const japaneseText = /[\u3040-\u30ff\u3400-\u9fff]/;
    const statePriority = ['blacklisted', 'mastered', 'due', 'new', 'young', 'mature', 'redundant'];
    let requestToken = null;
    let lastSourceToken = null;
    let parseSequence = 0;
    let deferredBatches = [];
    let viewportTimer = null;
    let viewportBurstTimers = [];
    let viewportWatchInstalled = false;
    let lastViewportParseAt = 0;
    let restoreWaitListener = null;
    const pendingBatches = new Map();
    let visibilityGeneration = 0;
    let config = { enabled: false, markerStyle: 'underline', visibleStates: [] };

    function now() {
        return window.performance?.now ? window.performance.now() : Date.now();
    }

    function duration(start) {
        return `${Math.max(0, Math.round(now() - start))}ms`;
    }

    function debug(message) {
        if (!config.debug) return;
        appendDebugLine(String(message));
    }

    function appendDebugLine(message) {
        const overlay = ensureDebugOverlay();
        if (!overlay) return;
        const line = document.createElement('div');
        line.textContent = `${new Date().toLocaleTimeString()} ${message}`;
        overlay.append(line);
        while (overlay.childNodes.length > 12) overlay.firstChild.remove();
    }

    function ensureDebugOverlay() {
        if (!config.debug || !document.body) return null;
        let overlay = document.getElementById('hoshi-jiten-debug-overlay');
        if (overlay) return overlay;
        overlay = document.createElement('div');
        overlay.id = 'hoshi-jiten-debug-overlay';
        overlay.style.cssText = [
            'position:fixed',
            'left:10px',
            'right:10px',
            'bottom:56px',
            'z-index:2147483647',
            'max-height:46vh',
            'min-height:120px',
            'overflow:auto',
            'padding:10px 12px',
            'border-radius:10px',
            'background:rgba(0,0,0,.84)',
            'color:#fff',
            'font:13px/1.45 monospace',
            'pointer-events:none',
            'white-space:pre-wrap',
            'box-shadow:0 2px 8px rgba(0,0,0,.35)'
        ].join(';');
        document.body.append(overlay);
        return overlay;
    }

    function eligible(node) {
        const parent = node.parentElement;
        return parent && node.data.trim() && japaneseText.test(node.data) &&
            !excludedTags.has(parent.tagName) &&
            !parent.closest('rt, rp, #hoshi-reader-popup-layer, .hoshi-jiten-word');
    }

    function configure(next) {
        config = { ...config, ...(next || {}) };
        document.documentElement.dataset.hoshiJitenMarker = config.markerStyle;
        debug(`config enabled=${config.enabled} marker=${config.markerStyle}`);
        if (config.enabled) installViewportWatcher();
        if (config.enabled && lastSourceToken) scheduleViewportParse('config');
        document.getElementById('hoshi-jiten-debug-overlay')?.toggleAttribute('hidden', !config.debug);
        refreshVisibility(++visibilityGeneration);
    }

    function collect() {
        const start = now();
        const nodes = [];
        const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
        let node;
        while ((node = walker.nextNode())) {
            if (eligible(node)) nodes.push(node);
        }
        debug(`collect ${nodes.length} nodes / ${duration(start)}`);
        return nodes;
    }

    function resetMarkers() {
        const start = now();
        const parents = [];
        document.querySelectorAll('.hoshi-jiten-word').forEach(element => {
            const parent = element.parentNode;
            if (!parent) return;
            while (element.firstChild) parent.insertBefore(element.firstChild, element);
            parent.removeChild(element);
            if (!parents.includes(parent)) parents.push(parent);
        });
        parents.forEach(parent => {
            if (parent.normalize) parent.normalize();
        });
        if (parents.length) debug(`reset markers ${parents.length} parents / ${duration(start)}`);
    }

    function primaryState(states) {
        const visible = new Set(config.visibleStates || []);
        return statePriority.find(state => states.includes(state) && visible.has(state)) || null;
    }

    function updateElement(element, states) {
        statePriority.forEach(state => element.classList.toggle(state, states.includes(state)));
        const state = primaryState(states);
        element.dataset.hoshiJitenVisible = state ? 'true' : 'false';
        if (state) element.dataset.hoshiJitenState = state;
        else delete element.dataset.hoshiJitenState;
    }

    function relativeRubies(token) {
        const tokenStart = Number(token.start);
        const tokenEnd = Number(token.end);
        if (!Array.isArray(token.rubies) || !Number.isFinite(tokenStart) || !Number.isFinite(tokenEnd)) return [];
        return token.rubies
            .map(ruby => ({
                text: String(ruby?.text || ''),
                start: Number(ruby?.start) - tokenStart,
                end: Number(ruby?.end) - tokenStart,
                length: Number(ruby?.length)
            }))
            .filter(ruby =>
                ruby.text &&
                Number.isFinite(ruby.start) &&
                Number.isFinite(ruby.end) &&
                ruby.start >= 0 &&
                ruby.end > ruby.start &&
                ruby.end <= tokenEnd - tokenStart
            );
    }

    function wrapNode(node, tokens) {
        if (!node?.parentNode || !Array.isArray(tokens) || tokens.length === 0) return;
        const text = node.data;
        const sorted = tokens
            .filter(token => token && token.start >= 0 && token.end > token.start && token.end <= text.length)
            .sort((a, b) => a.start - b.start);
        if (sorted.length === 0) return;
        const fragment = document.createDocumentFragment();
        let cursor = 0;
        sorted.forEach(token => {
            if (token.start < cursor) return;
            if (token.start > cursor) fragment.append(document.createTextNode(text.slice(cursor, token.start)));
            const span = document.createElement('span');
            span.className = 'hoshi-jiten-word';
            span.textContent = text.slice(token.start, token.end);
            span.dataset.wordId = String(token.wordId);
            span.dataset.readingIndex = String(token.readingIndex);
            span.dataset.conjugations = JSON.stringify(token.conjugations || []);
            span.dataset.rubies = JSON.stringify(relativeRubies(token));
            updateElement(span, token.card?.cardState || []);
            fragment.append(span);
            cursor = token.end;
        });
        if (cursor < text.length) fragment.append(document.createTextNode(text.slice(cursor)));
        node.parentNode.replaceChild(fragment, node);
    }

    function apply(token, result) {
        if (!token.startsWith(`${requestToken}:`) || !result?.tokens) return;
        const pendingNodes = pendingBatches.get(token);
        if (!pendingNodes) return;
        pendingBatches.delete(token);
        const start = now();
        debug(`receive ${shortToken(token)} ${pendingNodes.length} nodes`);
        let index = 0;
        const applyChunk = deadline => {
            if (!token.startsWith(`${requestToken}:`)) return;
            let processed = 0;
            while (
                index < result.tokens.length &&
                processed < 24 &&
                (processed === 0 || !deadline?.timeRemaining || deadline.timeRemaining() > 1)
            ) {
                wrapNode(pendingNodes[index], result.tokens[index]);
                index += 1;
                processed += 1;
            }
            if (index < result.tokens.length) {
                schedule(applyChunk);
            } else {
                debug(`DOM done ${shortToken(token)} ${index} nodes / ${duration(start)}`);
                afterDomUpdate();
                sendDeferredBatch();
            }
        };
        schedule(applyChunk);
    }

    function afterDomUpdate() {
        requestAnimationFrame(() => {
            const reader = window.hoshiReader;
            if (reader?.rebuildSasayakiCuesAfterDomMutation && readerHasSasayakiCues()) {
                reader.rebuildSasayakiCuesAfterDomMutation();
            } else {
                reader?.buildNodeOffsets?.();
            }
        });
    }

    function requestParse(token) {
        if (!config.enabled || !window.HoshiJiten) {
            debug(`skip parse enabled=${config.enabled} bridge=${!!window.HoshiJiten}`);
            return;
        }
        const start = now();
        lastSourceToken = token;
        requestToken = `${token}:jiten:${++parseSequence}`;
        clearViewportBurstTimers();
        deferredBatches = [];
        pendingBatches.clear();
        debug(`parse start #${parseSequence}`);
        resetMarkers();
        const collected = collect();
        if (collected.length === 0) {
            debug(`parse empty / ${duration(start)}`);
            return;
        }
        partitionByViewport(collected, requestToken, visible => {
            debug(`first batch ready ${visible.length} nodes / ${duration(start)}`);
            sendBatch(`${requestToken}:visible`, visible);
        }, background => {
            if (!requestToken) return;
            if (readerHasSasayakiCues()) {
                debug(`skip background: Sasayaki cues present / ${duration(start)}`);
                return;
            }
            debug(`partition done background=${background.length} / ${duration(start)}`);
            for (let start = 0; start < background.length; start += 200) {
                deferredBatches.push({
                    token: `${requestToken}:background:${start / 200}`,
                    nodes: background.slice(start, start + 200)
                });
            }
            if (deferredBatches.length) setTimeout(() => {
                if (requestToken) sendDeferredBatch();
            }, 2500);
        });
    }

    function requestViewportParse(reason) {
        if (!config.enabled || !window.HoshiJiten || !lastSourceToken) return;
        const timestamp = now();
        if (timestamp - lastViewportParseAt < 350) return;
        const nodes = collectViewportNodes(80);
        if (nodes.length === 0) {
            debug(`viewport ${reason}: no unparsed nodes`);
            return;
        }
        lastViewportParseAt = timestamp;
        requestToken = `${lastSourceToken}:jiten:${++parseSequence}:viewport`;
        deferredBatches = [];
        pendingBatches.clear();
        debug(`viewport parse #${parseSequence} ${reason} ${nodes.length} nodes`);
        sendBatch(`${requestToken}:visible`, nodes);
    }

    function requestParseWhenReaderReady(token) {
        lastSourceToken = token;
        if (restoreWaitListener) {
            window.removeEventListener('hoshi-reader-restore-complete', restoreWaitListener);
            restoreWaitListener = null;
        }
        const parseIfCurrent = reason => {
            if (lastSourceToken !== token) return;
            debug(`reader ${reason}; parse`);
            requestParse(token);
        };
        if (window.hoshiReaderRestoreComplete === true) {
            parseIfCurrent('ready');
            return;
        }
        debug('wait reader restore');
        restoreWaitListener = () => {
            window.removeEventListener('hoshi-reader-restore-complete', restoreWaitListener);
            restoreWaitListener = null;
            parseIfCurrent('restore');
        };
        window.addEventListener('hoshi-reader-restore-complete', restoreWaitListener);
    }

    function reparse() {
        if (lastSourceToken) requestParse(lastSourceToken);
    }

    function scheduleViewportParse(reason) {
        if (!config.enabled || !lastSourceToken) return;
        if (viewportTimer) clearTimeout(viewportTimer);
        viewportTimer = setTimeout(() => {
            viewportTimer = null;
            requestViewportParse(reason);
        }, 180);
    }

    function clearViewportBurstTimers() {
        viewportBurstTimers.forEach(timer => clearTimeout(timer));
        viewportBurstTimers = [];
    }

    function scheduleViewportBurst(reason) {
        if (!config.enabled) return;
        if (!lastSourceToken) {
            debug(`viewport ${reason}: waiting token`);
            return;
        }
        clearViewportBurstTimers();
        [250, 900].forEach(delay => {
            const timer = setTimeout(() => {
                viewportBurstTimers = viewportBurstTimers.filter(item => item !== timer);
                requestViewportParse(`${reason}+${delay}`);
            }, delay);
            viewportBurstTimers.push(timer);
        });
    }

    function viewportChanged(reason) {
        debug(`viewport changed: ${reason || 'unknown'}`);
        scheduleViewportBurst(reason || 'viewport');
    }

    function installViewportWatcher() {
        if (viewportWatchInstalled) return;
        viewportWatchInstalled = true;
        const schedule = event => {
            if (readerHasSasayakiCues()) return;
            scheduleViewportParse(event.type || 'viewport');
        };
        window.addEventListener('scroll', schedule, { passive: true });
        window.addEventListener('resize', schedule, { passive: true });
        document.body?.addEventListener('scroll', schedule, { passive: true });
    }

    function sendBatch(token, batchNodes) {
        if (!batchNodes?.length || !token.startsWith(`${requestToken}:`)) return;
        pendingBatches.set(token, batchNodes);
        debug(`send ${shortToken(token)} ${batchNodes.length} texts / ${batchNodes.reduce((sum, node) => sum + node.data.length, 0)} chars`);
        window.HoshiJiten.postMessage(JSON.stringify({ token, texts: batchNodes.map(node => node.data) }));
    }

    function sendDeferredBatch() {
        const batch = deferredBatches.shift();
        if (!batch) return;
        debug(`deferred next ${shortToken(batch.token)} remaining=${deferredBatches.length}`);
        sendBatch(batch.token, batch.nodes);
    }

    function readerHasSasayakiCues() {
        const reader = window.hoshiReader;
        if (!reader) return false;
        return !!(
            reader.cueSourceRanges?.size ||
            reader.cueWrappers?.size ||
            (Array.isArray(reader.sasayakiCues) && reader.sasayakiCues.length)
        );
    }

    function partitionByViewport(collected, token, onFirst, done) {
        const visible = [];
        const background = [];
        const marginX = Math.max(1, window.innerWidth) * 2;
        const marginY = Math.max(1, window.innerHeight) * 2;
        const firstBatchLimit = 64;
        let firstSent = false;
        let index = 0;
        const sendFirst = () => {
            if (firstSent || visible.length === 0) return;
            firstSent = true;
            onFirst(visible.splice(0, visible.length));
        };
        const partitionChunk = deadline => {
            if (token !== requestToken) return;
            let processed = 0;
            while (
                index < collected.length &&
                processed < 80 &&
                (processed === 0 || !deadline?.timeRemaining || deadline.timeRemaining() > 1)
            ) {
                const node = collected[index++];
                const range = document.createRange();
                range.selectNodeContents(node);
                const rect = range.getBoundingClientRect();
                const near = rect.right >= -marginX && rect.left <= window.innerWidth + marginX &&
                    rect.bottom >= -marginY && rect.top <= window.innerHeight + marginY;
                if (near && !firstSent) visible.push(node);
                else background.push(node);
                processed += 1;
            }
            if (visible.length >= firstBatchLimit) sendFirst();
            if (index < collected.length) schedule(partitionChunk);
            else {
                if (!firstSent) {
                    if (visible.length > 0) sendFirst();
                    else onFirst(background.splice(0, 64));
                } else if (visible.length > 0) {
                    background.unshift(...visible);
                }
                done(background);
            }
        };
        schedule(partitionChunk);
    }

    function collectViewportNodes(limit) {
        const nodes = [];
        const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
        let node;
        while ((node = walker.nextNode())) {
            if (!eligible(node)) continue;
            const range = document.createRange();
            range.selectNodeContents(node);
            const rect = range.getBoundingClientRect();
            if (isNearViewport(rect, 1)) {
                nodes.push(node);
                if (nodes.length >= limit) break;
            }
        }
        return nodes;
    }

    function isNearViewport(rect, screens) {
        const marginX = Math.max(1, window.innerWidth) * screens;
        const marginY = Math.max(1, window.innerHeight) * screens;
        return rect.right >= -marginX && rect.left <= window.innerWidth + marginX &&
            rect.bottom >= -marginY && rect.top <= window.innerHeight + marginY;
    }

    function schedule(callback) {
        if (window.requestIdleCallback) window.requestIdleCallback(callback, { timeout: 32 });
        else setTimeout(() => callback(null), 0);
    }

    function refreshVisibility(generation) {
        const elements = document.querySelectorAll('.hoshi-jiten-word');
        const start = now();
        let index = 0;
        const refreshChunk = () => {
            if (generation !== visibilityGeneration) return;
            const end = Math.min(index + 250, elements.length);
            for (; index < end; index += 1) {
                const element = elements[index];
                const states = statePriority.filter(state => element.classList.contains(state));
                updateElement(element, states);
            }
            if (index < elements.length) requestAnimationFrame(refreshChunk);
            else if (elements.length) debug(`visibility ${elements.length} words / ${duration(start)}`);
        };
        refreshChunk();
    }

    function updateCardState(wordId, readingIndex, states) {
        document.querySelectorAll('.hoshi-jiten-word').forEach(element => {
            if (
                element.dataset.wordId === String(wordId) &&
                element.dataset.readingIndex === String(readingIndex)
            ) updateElement(element, states || []);
        });
    }

    function shortToken(token) {
        if (!token) return '';
        const match = String(token).match(/jiten:(\d+):(.*)$/);
        return match ? `#${match[1]}:${match[2]}` : String(token).slice(-24);
    }

    window.hoshiJiten = {
        configure,
        requestParse,
        requestParseWhenReaderReady,
        reparse,
        viewportChanged,
        debugLog: debug,
        apply,
        updateCardState
    };
})();
