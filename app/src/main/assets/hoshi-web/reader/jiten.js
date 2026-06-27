(function () {
    const excludedTags = new Set(['SCRIPT', 'STYLE', 'NOSCRIPT', 'TEXTAREA', 'RT', 'RP']);
    const japaneseText = /[\u3040-\u30ff\u3400-\u9fff]/;
    const statePriority = ['blacklisted', 'mastered', 'due', 'new', 'young', 'mature', 'redundant'];
    let requestToken = null;
    let deferredBatches = [];
    const pendingBatches = new Map();
    let visibilityGeneration = 0;
    let config = { enabled: false, markerStyle: 'underline', visibleStates: [] };

    function eligible(node) {
        const parent = node.parentElement;
        return parent && node.data.trim() && japaneseText.test(node.data) &&
            !excludedTags.has(parent.tagName) &&
            !parent.closest('rt, rp, #hoshi-reader-popup-layer, .hoshi-jiten-word');
    }

    function configure(next) {
        config = { ...config, ...(next || {}) };
        document.documentElement.dataset.hoshiJitenMarker = config.markerStyle;
        refreshVisibility(++visibilityGeneration);
    }

    function collect() {
        const nodes = [];
        const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
        let node;
        while ((node = walker.nextNode())) {
            if (eligible(node)) nodes.push(node);
        }
        return nodes;
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
                sendDeferredBatch();
                requestAnimationFrame(() => window.dispatchEvent(new Event('resize')));
            }
        };
        schedule(applyChunk);
    }

    function requestParse(token) {
        if (!config.enabled || !window.HoshiJiten) return;
        requestToken = token;
        deferredBatches = [];
        pendingBatches.clear();
        const collected = collect();
        if (collected.length === 0) return;
        partitionByViewport(collected, token, (visible, background) => {
            if (token !== requestToken) return;
            const first = visible.length > 0 ? visible : background.splice(0, 80);
            for (let start = 0; start < background.length; start += 200) {
                deferredBatches.push({
                    token: `${token}:background:${start / 200}`,
                    nodes: background.slice(start, start + 200)
                });
            }
            sendBatch(`${token}:visible`, first);
            if (deferredBatches.length) setTimeout(() => {
                if (requestToken === token) sendDeferredBatch();
            }, 2500);
        });
    }

    function sendBatch(token, batchNodes) {
        if (!batchNodes?.length || !token.startsWith(`${requestToken}:`)) return;
        pendingBatches.set(token, batchNodes);
        window.HoshiJiten.postMessage(JSON.stringify({ token, texts: batchNodes.map(node => node.data) }));
    }

    function sendDeferredBatch() {
        const batch = deferredBatches.shift();
        if (!batch) return;
        sendBatch(batch.token, batch.nodes);
    }

    function partitionByViewport(collected, token, done) {
        const visible = [];
        const background = [];
        const marginX = Math.max(1, window.innerWidth) * 2;
        const marginY = Math.max(1, window.innerHeight) * 2;
        let index = 0;
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
                (near && visible.length < 160 ? visible : background).push(node);
                processed += 1;
            }
            if (index < collected.length) schedule(partitionChunk);
            else done(visible, background);
        };
        schedule(partitionChunk);
    }

    function schedule(callback) {
        if (window.requestIdleCallback) window.requestIdleCallback(callback, { timeout: 32 });
        else setTimeout(() => callback(null), 0);
    }

    function refreshVisibility(generation) {
        const elements = document.querySelectorAll('.hoshi-jiten-word');
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

    window.hoshiJiten = { configure, requestParse, apply, updateCardState };
})();
