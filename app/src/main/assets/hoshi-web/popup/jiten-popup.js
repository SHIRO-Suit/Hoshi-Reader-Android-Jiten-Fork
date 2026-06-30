(function () {
    const kanjiPattern = /[\u4E00-\u9FFF\u3400-\u4DBF\uF900-\uFAFF\u3005]/;
    const kanjiSegmentPattern = /[\u4E00-\u9FFF\u3400-\u4DBF\uF900-\uFAFF\u3005]+|[^\u4E00-\u9FFF\u3400-\u4DBF\uF900-\uFAFF\u3005]+/g;
    const popupMode = window.hoshiJitenPopupMode === 'integrated' ? 'integrated' : 'paged';
    let card = null;
    let page = 'dictionary';

    function element(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text != null) node.textContent = text;
        return node;
    }

    function ensureLayout() {
        const entries = document.getElementById('entries-container');
        if (!entries?.parentNode) return;
        const switchers = [...document.querySelectorAll('.hoshi-jiten-popup-switcher')];
        const pages = [...document.querySelectorAll('.hoshi-jiten-popup-page')];
        switchers.slice(1).forEach(node => node.remove());
        pages.slice(1).forEach(node => node.remove());

        let jitenPage = pages[0];
        if (!jitenPage) {
            jitenPage = element('section', 'hoshi-jiten-popup-page');
            jitenPage.id = 'hoshi-jiten-popup-page';
        }

        if (popupMode === 'integrated') {
            switchers.forEach(node => node.remove());
            jitenPage.classList.add('integrated');
            entries.parentNode.insertBefore(jitenPage, entries);
            return;
        }

        jitenPage.classList.remove('integrated');
        entries.parentNode.insertBefore(jitenPage, entries.nextSibling);
        if (switchers[0]) return;
        const switcher = element('div', 'hoshi-jiten-popup-switcher');
        switcher.id = 'hoshi-jiten-popup-switcher';
        ['dictionary', 'jiten'].forEach(name => {
            const indicator = element('button', 'hoshi-jiten-popup-indicator');
            indicator.type = 'button';
            indicator.setAttribute('aria-label', name === 'dictionary' ? 'Dictionary' : 'Jiten');
            indicator.addEventListener('click', () => setPage(name));
            switcher.appendChild(indicator);
        });
        entries.parentNode.insertBefore(switcher, entries);
    }

    function renderCard() {
        ensureLayout();
        const container = document.getElementById('hoshi-jiten-popup-page');
        if (!container) return;
        container.replaceChildren();
        if (!card) return;

        const toolbar = element('div', 'hoshi-jiten-popup-toolbar');
        toolbar.append(element('div', 'hoshi-jiten-popup-section-label', 'Jiten'), actionBar());
        container.append(toolbar);

        const header = element('header', 'hoshi-jiten-popup-header');
        const heading = element('div', 'hoshi-jiten-popup-heading');
        const expression = element('strong', 'hoshi-jiten-popup-expression');
        expression.lang = 'ja';
        expression.append(...readingNodes(card));
        window.hoshiLinkifyKanjiText?.(expression);
        heading.append(expression);
        header.append(heading);
        container.append(header);

        const meta = element('div', 'hoshi-jiten-popup-meta');
        (card.cardState || []).forEach(state => meta.append(element('span', `hoshi-jiten-popup-chip state-${state}`, state)));
        if (card.frequencyRank > 0) meta.append(element('span', 'hoshi-jiten-popup-chip', `#${card.frequencyRank}`));
        if (meta.childNodes.length) container.append(meta);

        const conjugations = conjugationDetails(card);
        if (conjugations.childNodes.length) container.append(conjugations);

        const pitches = pitchDiagrams(card.reading, card.pitchAccents || []);
        if (pitches.childNodes.length) container.append(pitches);

        const meanings = element('div', 'hoshi-jiten-popup-meanings');
        groupMeanings(card.meanings || []).forEach(group => {
            const block = element('section', 'hoshi-jiten-popup-meaning');
            const labels = group.partsOfSpeech.map(partOfSpeechLabel).filter(Boolean);
            if (labels.length) {
                const pos = element('div', 'hoshi-jiten-popup-pos');
                [...new Set(labels)].forEach(label => pos.append(element('span', null, label)));
                block.append(pos);
            }
            const list = element('ol');
            list.start = group.startIndex + 1;
            group.glosses.forEach(glosses => list.append(element('li', null, glosses.join('; '))));
            block.append(list);
            meanings.append(block);
        });
        container.append(meanings);
    }

    function readingNodes(value) {
        const segmented = rubyNodes(value);
        if (segmented.length) return segmented;
        const annotated = value.wordWithReading || value.spelling || '';
        const regex = /([^\u3040-\u30ff]+)\[(.+?)\]/g;
        const nodes = [];
        let hasRuby = false;
        let lastIndex = 0;
        let match;
        while ((match = regex.exec(annotated)) !== null) {
            hasRuby = true;
            if (match.index > lastIndex) nodes.push(document.createTextNode(annotated.slice(lastIndex, match.index)));
            const ruby = document.createElement('ruby');
            ruby.append(document.createTextNode(match[1]), element('rt', null, match[2]));
            nodes.push(ruby);
            lastIndex = regex.lastIndex;
        }
        if (hasRuby) {
            if (lastIndex < annotated.length) nodes.push(document.createTextNode(annotated.slice(lastIndex)));
            return nodes;
        }
        return spellingReadingNodes(value);
    }

    function rubyNodes(value) {
        const baseText = value.matchedText || value.spelling || '';
        const rubies = Array.isArray(value.rubies) ? value.rubies : [];
        if (!baseText || !rubies.length) return [];
        const nodes = [];
        let cursor = 0;
        let hasRuby = false;
        rubies
            .map(ruby => ({
                text: cleanReading(ruby?.text || ''),
                start: Number(ruby?.start),
                end: Number(ruby?.end)
            }))
            .filter(ruby =>
                ruby.text &&
                Number.isFinite(ruby.start) &&
                Number.isFinite(ruby.end) &&
                ruby.start >= 0 &&
                ruby.end > ruby.start &&
                ruby.end <= baseText.length
            )
            .sort((a, b) => a.start - b.start)
            .forEach(rubyInfo => {
                if (rubyInfo.start < cursor) return;
                if (rubyInfo.start > cursor) nodes.push(document.createTextNode(baseText.slice(cursor, rubyInfo.start)));
                const base = baseText.slice(rubyInfo.start, rubyInfo.end);
                hasRuby = appendRubySegment(nodes, base, rubyInfo.text) || hasRuby;
                cursor = rubyInfo.end;
            });
        if (cursor < baseText.length) nodes.push(document.createTextNode(baseText.slice(cursor)));
        return hasRuby ? nodes : [];
    }

    function appendRubySegment(nodes, base, reading) {
        if (!shouldShowRuby(base, reading)) {
            nodes.push(document.createTextNode(base));
            return false;
        }
        const firstKanaIndex = [...base].findIndex(isKana);
        if (firstKanaIndex < 0) {
            nodes.push(rubyElement(base, reading));
            return true;
        }
        let attached = false;
        let buffer = '';
        let bufferIsKana = null;
        const flush = () => {
            if (!buffer) return;
            if (!bufferIsKana && !attached) {
                nodes.push(rubyElement(buffer, reading));
                attached = true;
            } else {
                nodes.push(document.createTextNode(buffer));
            }
            buffer = '';
        };
        [...base].forEach(char => {
            const charIsKana = isKana(char);
            if (buffer && charIsKana !== bufferIsKana) flush();
            buffer += char;
            bufferIsKana = charIsKana;
        });
        flush();
        return attached;
    }

    function rubyElement(base, reading) {
        const ruby = document.createElement('ruby');
        ruby.append(document.createTextNode(base), element('rt', null, reading));
        return ruby;
    }

    function shouldShowRuby(base, reading) {
        if (!base || !reading) return false;
        if ([...base].every(isKana)) return false;
        return true;
    }

    function isKana(char) {
        return /^[\u3040-\u30ffー]$/.test(char);
    }

    function spellingReadingNodes(value) {
        const expression = value.spelling || value.matchedText || '';
        const reading = cleanReading(value.reading || '');
        if (!expression) return [];
        return segmentFurigana(expression, reading).map(([text, furigana]) =>
            furigana ? rubyElement(text, furigana) : document.createTextNode(text)
        );
    }

    function segmentFurigana(expression, reading) {
        if (!reading || reading === expression) return [[expression, '']];
        const groups = (expression.match(kanjiSegmentPattern) || []).map(text => {
            const isKanaGroup = !kanjiPattern.test(text[0]);
            return {
                isKana: isKanaGroup,
                text,
                textNormalized: isKanaGroup ? toHiragana(text) : null
            };
        });
        const segments = segmentizeFurigana(reading, toHiragana(reading), groups, 0);
        return segments ? segments.map(segment => [segment.text, segment.reading]) : [[expression, reading]];
    }

    function segmentizeFurigana(reading, readingNormalized, groups, groupsStart) {
        const groupCount = groups.length - groupsStart;
        if (groupCount <= 0) return reading.length === 0 ? [] : null;

        const group = groups[groupsStart];
        if (group.isKana) {
            const textLength = group.text.length;
            if (group.textNormalized !== null && readingNormalized.startsWith(group.textNormalized)) {
                const segments = segmentizeFurigana(
                    reading.substring(textLength),
                    readingNormalized.substring(textLength),
                    groups,
                    groupsStart + 1
                );
                if (segments !== null) {
                    segments.unshift(...kanaFuriganaSegments(group.text, reading.substring(0, textLength)));
                    return segments;
                }
            }
            return null;
        }

        let result = null;
        for (let i = reading.length; i >= group.text.length; --i) {
            const segments = segmentizeFurigana(
                reading.substring(i),
                readingNormalized.substring(i),
                groups,
                groupsStart + 1
            );
            if (segments !== null) {
                if (result !== null) return null;
                segments.unshift({ text: group.text, reading: reading.substring(0, i) });
                result = segments;
            }
            if (groupCount === 1) break;
        }
        return result;
    }

    function kanaFuriganaSegments(text, reading) {
        if (reading === text) return [{ text, reading: '' }];
        const segments = [];
        let start = 0;
        let state = reading[0] === text[0];
        for (let i = 1; i < text.length; ++i) {
            const newState = reading[i] === text[i];
            if (state === newState) continue;
            segments.push({ text: text.substring(start, i), reading: state ? '' : reading.substring(start, i) });
            state = newState;
            start = i;
        }
        segments.push({ text: text.substring(start), reading: state ? '' : reading.substring(start) });
        return segments;
    }

    function toHiragana(text) {
        return text.replace(/[\u30A1-\u30F6]/g, char => String.fromCharCode(char.charCodeAt(0) - 0x60));
    }

    function conjugationDetails(value) {
        const conjugations = (value.conjugations || []).filter(Boolean);
        const container = element('div', 'hoshi-jiten-popup-conjugations');
        if (!conjugations.length) return container;
        container.append(
            element('span', 'hoshi-jiten-popup-conjugations-label', 'Conjugations: '),
            element('span', 'hoshi-jiten-popup-conjugations-value', conjugations.join(' ; ')),
        );
        return container;
    }

    function groupMeanings(meanings) {
        return meanings.map((meaning, index) => ({
            partsOfSpeech: Array.isArray(meaning.partsOfSpeech)
                ? meaning.partsOfSpeech
                : [meaning.partsOfSpeech].filter(Boolean),
            glosses: [meaning.glosses || []],
            startIndex: index
        }));
    }

    const partOfSpeechLabels = {
        'adj-f': 'noun or verb acting prenominally',
        'adj-i': 'adjective (keiyoushi)',
        'adj-ix': 'adjective (keiyoushi) — yoi/ii class',
        'adj-na': 'adjectival noun (keiyoudoushi)',
        'adj-no': "noun taking the genitive particle 'no'",
        'adj-pn': 'pre-noun adjectival (rentaishi)',
        'adj-t': "'taru' adjective",
        adv: 'adverb (fukushi)',
        'adv-to': "adverb taking the 'to' particle",
        aux: 'auxiliary',
        'aux-adj': 'auxiliary adjective',
        'aux-v': 'auxiliary verb',
        conj: 'conjunction',
        cop: 'copula',
        ctr: 'counter',
        exp: 'expression (phrase, clause, etc.)',
        int: 'interjection (kandoushi)',
        n: 'noun (common) (futsuumeishi)',
        'n-adv': 'adverbial noun (fukushitekimeishi)',
        'n-pr': 'proper noun',
        'n-pref': 'noun used as a prefix',
        'n-suf': 'noun used as a suffix',
        'n-t': 'temporal noun (jisoumeishi)',
        num: 'numeric',
        pn: 'pronoun',
        pref: 'prefix',
        prt: 'particle',
        suf: 'suffix',
        v1: 'Ichidan verb',
        'v1-s': 'Ichidan verb — kureru special class',
        'v-unspec': 'verb (unspecified)',
        vi: 'intransitive verb',
        vk: 'Kuru verb — special class',
        vn: 'irregular nu verb',
        vr: 'irregular ru verb',
        vs: 'noun or participle taking the auxiliary verb suru',
        'vs-c': 'su verb — precursor to modern suru',
        'vs-i': 'suru verb — included',
        'vs-s': 'suru verb — special class',
        vt: 'transitive verb',
        vz: 'Ichidan zuru verb',
        abbr: 'abbreviation',
        arch: 'archaic',
        chn: "children's language",
        col: 'colloquial',
        dated: 'dated term',
        derog: 'derogatory',
        euph: 'euphemistic',
        fam: 'familiar language',
        form: 'formal or literary term',
        hon: 'honorific or respectful (sonkeigo)',
        hum: 'humble (kenjougo)',
        id: 'idiomatic expression',
        joc: 'jocular or humorous',
        obs: 'obsolete term',
        'on-mim': 'onomatopoeic or mimetic',
        poet: 'poetic term',
        pol: 'polite (teineigo)',
        proverb: 'proverb',
        rare: 'rare term',
        sens: 'sensitive',
        sl: 'slang',
        uk: 'usually written using kana',
        vulg: 'vulgar',
        comp: 'computing',
        food: 'food or cooking',
        ling: 'linguistics',
        math: 'mathematics',
        med: 'medicine',
        sports: 'sports'
    };

    function partOfSpeechLabel(code) {
        if (partOfSpeechLabels[code]) return partOfSpeechLabels[code];
        const godan = /^v5([bgkmnrstu])(?:-.+)?$/.exec(code || '');
        if (godan) {
            const endings = { b: 'bu', g: 'gu', k: 'ku', m: 'mu', n: 'nu', r: 'ru', s: 'su', t: 'tsu', u: 'u' };
            return `Godan verb with '${endings[godan[1]]}' ending`;
        }
        if (/^v[24]/.test(code || '')) return 'archaic verb';
        return null;
    }

    function actionBar() {
        const actions = element('div', 'hoshi-jiten-popup-actions');
        actions.append(
            actionButton('Never forget', 'neverForget', 'mastered'),
            actionButton('Blacklist', 'blacklist', 'blacklisted'),
            forgetButton(),
        );
        return actions;
    }

    function textButton(label, className = '') {
        const button = element('button', `hoshi-jiten-popup-action ${className}`.trim(), label);
        button.type = 'button';
        button.title = label;
        button.setAttribute('aria-label', label);
        return button;
    }

    function actionButton(label, list, state) {
        const active = card?.cardState?.includes(state) === true;
        const button = textButton(active ? `✓ ${label}` : label, active ? 'active' : '');
        button.setAttribute('aria-pressed', String(active));
        button.addEventListener('click', () => {
            window.HoshiAndroidPopup.postMessage('jitenAction', {
                wordId: card.wordId,
                readingIndex: card.readingIndex,
                list,
                action: active ? 'remove' : 'add'
            });
        });
        return button;
    }

    function forgetButton() {
        const button = textButton('Forget', 'danger');
        let armed = false;
        let resetTimer = null;
        button.addEventListener('click', () => {
            if (!armed) {
                armed = true;
                button.classList.add('armed');
                button.textContent = 'Confirm forget';
                button.title = 'Confirm Forget';
                button.setAttribute('aria-label', 'Confirm Forget');
                resetTimer = setTimeout(() => renderCard(), 3000);
                return;
            }
            clearTimeout(resetTimer);
            window.HoshiAndroidPopup.postMessage('jitenAction', {
                wordId: card.wordId,
                readingIndex: card.readingIndex,
                list: 'forget',
                action: 'add'
            });
        });
        return button;
    }

    function pitchDiagrams(reading, accents) {
        const container = element('div', 'hoshi-jiten-popup-pitches');
        accents.forEach(accent => {
            const diagram = pitchDiagram(cleanReading(reading), accent);
            if (diagram) container.append(diagram);
        });
        return container;
    }

    function cleanReading(reading) {
        return (reading || '').replace(/[\u3400-\u9fff\uff10-\uff5a\[\]A-Za-z0-9]/g, '');
    }

    function splitMorae(reading) {
        const small = new Set(['ゃ', 'ゅ', 'ょ', 'ャ', 'ュ', 'ョ', 'ぁ', 'ぃ', 'ぅ', 'ぇ', 'ぉ', 'ァ', 'ィ', 'ゥ', 'ェ', 'ォ']);
        const morae = [];
        for (const char of reading) {
            if (morae.length && small.has(char)) morae[morae.length - 1] += char;
            else morae.push(char);
        }
        return morae;
    }

    function pitchDiagram(reading, accent) {
        const morae = splitMorae(reading);
        if (!morae.length) return null;
        const pattern = accent === 0
            ? [0, ...morae.slice(1).map(() => 1), 1]
            : morae.map((_, index) => index === 0 ? (accent === 1 ? 1 : 0) : (index < accent ? 1 : 0)).concat(0);
        const type = accent === 0 ? 'heiban' : accent === 1 ? 'atamadaka' : accent === morae.length ? 'odaka' : 'nakadaka';
        const colors = { heiban: '#d20ca3', atamadaka: '#ea9316', nakadaka: '#27a2ff', odaka: '#0cbf49' };
        const color = colors[type];
        const ns = 'http://www.w3.org/2000/svg';
        const step = 18;
        const width = pattern.length * step;
        const svg = document.createElementNS(ns, 'svg');
        svg.setAttribute('viewBox', `0 0 ${width} 38`);
        svg.setAttribute('width', String(width));
        svg.setAttribute('height', '38');
        svg.setAttribute('role', 'img');
        svg.setAttribute('aria-label', `Pitch accent ${accent}`);
        const points = pattern.map((high, index) => ({ x: 9 + index * step, y: high ? 5 : 17 }));
        const line = document.createElementNS(ns, 'polyline');
        line.setAttribute('points', points.map(point => `${point.x},${point.y}`).join(' '));
        line.setAttribute('fill', 'none');
        line.setAttribute('stroke', color);
        line.setAttribute('stroke-width', '1.5');
        svg.append(line);
        points.forEach((point, index) => {
            const particle = index === points.length - 1;
            const circle = document.createElementNS(ns, 'circle');
            circle.setAttribute('cx', String(point.x));
            circle.setAttribute('cy', String(point.y));
            circle.setAttribute('r', '3');
            circle.setAttribute('fill', particle ? 'var(--hoshi-jiten-pitch-particle, white)' : color);
            circle.setAttribute('stroke', color);
            circle.setAttribute('stroke-width', '1.5');
            svg.append(circle);
            if (!particle && morae[index]) {
                const text = document.createElementNS(ns, 'text');
                text.setAttribute('x', String(point.x));
                text.setAttribute('y', String(point.y + 8));
                text.setAttribute('text-anchor', 'middle');
                text.setAttribute('dominant-baseline', 'hanging');
                text.setAttribute('fill', color);
                text.setAttribute('font-size', '9');
                text.setAttribute('font-weight', 'bold');
                text.textContent = morae[index];
                svg.append(text);
            }
        });
        return svg;
    }

    function setPage(next) {
        const entries = document.getElementById('entries-container');
        const jitenPage = document.getElementById('hoshi-jiten-popup-page');
        if (popupMode === 'integrated') {
            page = 'dictionary';
            if (entries) entries.hidden = false;
            if (jitenPage) jitenPage.hidden = !card;
            return;
        }
        if (!card && next === 'jiten') return;
        page = next;
        const switcher = document.getElementById('hoshi-jiten-popup-switcher');
        if (switcher) switcher.hidden = !card;
        if (entries) entries.hidden = page !== 'dictionary';
        if (jitenPage) jitenPage.hidden = page !== 'jiten';
        document.querySelectorAll('.hoshi-jiten-popup-indicator').forEach((indicator, index) => {
            indicator.classList.toggle('active', index === (page === 'dictionary' ? 0 : 1));
            indicator.disabled = index === 1 && !card;
        });
        document.documentElement.scrollTop = 0;
        document.body.scrollTop = 0;
    }

    function setCard(next) {
        const sameCard = card && next && card.wordId === next.wordId && card.readingIndex === next.readingIndex;
        card = next || null;
        renderCard();
        setPage(sameCard ? page : 'dictionary');
    }

    function navigate(deltaX) {
        if (popupMode !== 'paged' || !card) return false;
        setPage(deltaX < 0 ? 'jiten' : 'dictionary');
        return true;
    }

    window.hoshiJitenPopup = {
        hasCard: () => popupMode === 'paged' && !!card,
        setCard,
        setPage,
        navigate
    };
})();
