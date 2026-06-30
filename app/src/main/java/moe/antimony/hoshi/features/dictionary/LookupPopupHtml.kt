package moe.antimony.hoshi.features.dictionary

import android.content.Context
import de.manhhao.hoshi.KanjiEntry
import de.manhhao.hoshi.KanjiResult
import de.manhhao.hoshi.KanjiStat
import de.manhhao.hoshi.LookupResult
import de.manhhao.hoshi.TraceCandidate
import de.manhhao.hoshi.TraceSource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import moe.antimony.hoshi.content.ContentLanguageProfile
import moe.antimony.hoshi.features.audio.AudioSettings
import moe.antimony.hoshi.features.anki.AnkiPopupSettings
import moe.antimony.hoshi.features.reader.coerceReaderPopupScale
import java.util.Locale

internal data class LookupPopupAssets(
    val popupJs: String,
    val popupCss: String,
    val languageJapaneseJs: String = "",
    val selectionJapaneseJs: String = "",
    val selectionEnglishJs: String = "",
    val selectionJs: String = "",
    val readerPopupHostJs: String = "",
    val jitenPopupJs: String = "",
    val jitenPopupCss: String = "",
) {
    companion object {
        @Volatile
        private var cached: LookupPopupAssets? = null

        fun load(context: Context): LookupPopupAssets =
            cached ?: synchronized(this) {
                cached ?: read(context.applicationContext).also { cached = it }
            }

        private fun read(context: Context): LookupPopupAssets = LookupPopupAssets(
            popupJs = context.readAsset("hoshi-web/popup/popup.js"),
            popupCss = context.readAsset("hoshi-web/popup/popup.css"),
            languageJapaneseJs = context.readAsset("hoshi-web/shared/language-ja.js"),
            selectionJapaneseJs = context.readAsset("hoshi-web/shared/selection-ja.js"),
            selectionEnglishJs = context.readAsset("hoshi-web/shared/selection-en.js"),
            selectionJs = context.readAsset("hoshi-web/shared/selection.js"),
            readerPopupHostJs = context.readAsset("hoshi-web/popup/reader-popup-host.js"),
            jitenPopupJs = context.readAsset("hoshi-web/popup/jiten-popup.js"),
            jitenPopupCss = context.readAsset("hoshi-web/popup/jiten-popup.css"),
        )

        private fun Context.readAsset(path: String): String =
            assets.open(path)
                .bufferedReader()
                .use { it.readText() }
    }

    fun selectionSupportJs(contentLanguageProfile: ContentLanguageProfile): String =
        when (contentLanguageProfile.dictionaryLanguageId) {
            ContentLanguageProfile.EnglishLanguageId -> "$languageJapaneseJs\n$selectionEnglishJs"
            else -> "$languageJapaneseJs\n$selectionJapaneseJs"
        }
}

internal object LookupPopupHtml {
    fun renderIframeDocument(
        assets: LookupPopupAssets? = null,
        dictionaryStyles: Map<String, String> = emptyMap(),
        settings: DictionarySettings = DictionarySettings(),
        swipeToDismiss: Boolean = false,
        swipeThreshold: Int = 40,
        reducedMotionScrolling: Boolean = false,
        reducedMotionScrollPercent: Int = 100,
        reducedMotionSwipeThreshold: Int = 40,
        darkMode: Boolean = false,
        eInkMode: Boolean = false,
        audioSettings: AudioSettings = AudioSettings(),
        ankiSettings: AnkiPopupSettings = AnkiPopupSettings(),
        fontFaceCss: String = "",
        popupScale: Double = 1.0,
        contentLanguageProfile: ContentLanguageProfile = ContentLanguageProfile.Default,
    ): String {
        val normalizedSettings = settings.normalized()
        val collapsedDictionaries = dictionaryNamesJson(normalizedSettings.collapsedDictionaries)
        val effectiveSwipeThreshold = if (swipeToDismiss) swipeThreshold.coerceAtLeast(0) else 0
        val effectiveReducedMotionScrollScale = reducedMotionScrollPercent.coerceIn(40, 100) / 100.0
        val effectiveReducedMotionSwipeThreshold = reducedMotionSwipeThreshold.coerceAtLeast(0)
        val colorScheme = if (darkMode) "dark" else "light"
        val styles = dictionaryStylesJson(dictionaryStyles)
        val popupCss = assets?.let { """<style>${it.popupCss}</style>""" }
            ?: """<link rel="stylesheet" href="$PopupAssetBaseUrl/popup.css">"""
        val popupTypographyCss = """
            <style>
                ${fontFaceCss.trim()}
                :root { --hoshi-content-font-family: ${contentLanguageProfile.webViewFontFamilyCss}; }
                html { zoom: ${popupCssNumber(popupScale.coerceReaderPopupScale())}; }
            </style>
        """.trimIndent()
        val customCss = customCssStyle(normalizedSettings.customCSS)
        val fontPrewarmScript = """<script>${popupFontPrewarmScript()}</script>"""
        val eInkCss = if (eInkMode) """<style>$eInkPopupCss</style>""" else ""
        val jitenPopupCss = assets?.let { """<style>${it.jitenPopupCss}</style>""" }
            ?: """<link rel="stylesheet" href="$PopupAssetBaseUrl/jiten-popup.css">"""
        val selectionSupportJs = assets
            ?.selectionSupportJs(contentLanguageProfile)
            ?.takeIf(String::isNotBlank)
            ?.let { """<script>$it</script>""" }
            ?: if (assets == null) {
                selectionSupportAssetNames(contentLanguageProfile).joinToString("\n") { name ->
                    """<script src="$PopupAssetBaseUrl/$name"></script>"""
                }
            } else {
                ""
            }
        val selectionJs = assets?.let { """<script>${it.selectionJs}</script>""" }
            ?: """<script src="$PopupAssetBaseUrl/selection.js"></script>"""
        val selectionLanguageId = JsonPrimitive(contentLanguageProfile.dictionaryLanguageId)
        val selectionConfigureJs = """<script>window.hoshiSelection?.configure?.({ language: $selectionLanguageId });</script>"""
        val popupJs = assets?.let { """<script>${it.popupJs}</script>""" }
            ?: """<script src="$PopupAssetBaseUrl/popup.js"></script>"""
        val jitenPopupJs = assets?.let { """<script>${it.jitenPopupJs}</script>""" }
            ?: """<script src="$PopupAssetBaseUrl/jiten-popup.js"></script>"""
        return """
            <!DOCTYPE html>
            <html lang="${contentLanguageProfile.htmlLang}" data-hoshi-color-scheme="$colorScheme" data-hoshi-eink-mode="$eInkMode">
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                $popupCss
                <style>$androidColorSchemeCss</style>
                $popupTypographyCss
                $customCss
                $fontPrewarmScript
                $eInkCss
                $jitenPopupCss
                <style>
                    html,
                    body {
                        overscroll-behavior: none;
                    }
                </style>
                <script>
                    window.HoshiAndroidPopup = window.HoshiAndroidPopup || (function() {
                        var nextMessageId = 1;
                        var pendingMessages = {};
                        function postMessage(name, body, id) {
                            try {
                                window.parent.postMessage({
                                    source: 'hoshi-popup-iframe',
                                    popupId: window.popupId || null,
                                    name: name,
                                    id: id || null,
                                    body: body === undefined ? null : body
                                }, 'https://appassets.androidplatform.net');
                            } catch (e) {
                                console.warn('Hoshi reader popup bridge failed', e);
                            }
                        }
                        return {
                            postMessage: postMessage,
                            requestMessage: function(name, body) {
                                return new Promise(function(resolve) {
                                    var id = String(nextMessageId++);
                                    pendingMessages[id] = resolve;
                                    postMessage(name, body, id);
                                });
                            },
                            resolveMessage: function(id, result) {
                                var resolve = pendingMessages[id];
                                if (!resolve) return;
                                delete pendingMessages[id];
                                resolve(result);
                            }
                        };
                    })();
                    window.webkit = {
                        messageHandlers: {
                            openLink: { postMessage: function(url) { window.HoshiAndroidPopup.postMessage('openLink', url); } },
                            textSelected: { postMessage: function(selection) { window.HoshiAndroidPopup.postMessage('textSelected', selection); } },
                            tapOutside: { postMessage: function() { window.HoshiAndroidPopup.postMessage('tapOutside'); } },
                            swipeDismiss: { postMessage: function() { window.HoshiAndroidPopup.postMessage('swipeDismiss'); } },
                            playWordAudio: { postMessage: function(content) { window.HoshiAndroidPopup.postMessage('playWordAudio', content); } },
                            shellReady: { postMessage: function() { window.HoshiAndroidPopup.postMessage('shellReady'); } },
                            contentReady: { postMessage: function() { window.HoshiAndroidPopup.postMessage('contentReady'); } },
                            popupScrolled: { postMessage: function() { window.HoshiAndroidPopup.postMessage('popupScrolled'); } },
                            mineEntry: { postMessage: function(content) { return window.HoshiAndroidPopup.requestMessage('mineEntry', content); } },
                            duplicateCheck: { postMessage: function(expression) { return window.HoshiAndroidPopup.requestMessage('duplicateCheck', expression); } },
                            getEntry: { postMessage: function(index) { return window.HoshiAndroidPopup.requestMessage('getEntry', index); } },
                            lookupKanji: { postMessage: function(character) { return window.HoshiAndroidPopup.requestMessage('lookupKanji', character); } },
                            lookupRedirect: { postMessage: function(query) { return window.HoshiAndroidPopup.requestMessage('lookupRedirect', query); } }
                        }
                    };
                    window.scanNonJapaneseText = ${normalizedSettings.scanNonJapaneseText};
                    window.scanLength = ${normalizedSettings.scanLength};
                    window.collapseMode = "${normalizedSettings.collapseMode.rawValue}";
                    window.expandFirstDictionary = ${normalizedSettings.expandFirstDictionary};
                    window.collapsedDictionaries = $collapsedDictionaries;
                    window.compactGlossaries = ${normalizedSettings.compactGlossaries};
                    window.showExpressionTags = ${normalizedSettings.showExpressionTags};
                    window.harmonicFrequency = ${normalizedSettings.harmonicFrequency};
                    window.deduplicatePitchAccents = ${normalizedSettings.deduplicatePitchAccents};
                    window.compactPitchAccents = ${normalizedSettings.compactPitchAccents};
                    window.hoshiJitenPopupMode = ${JsonPrimitive(normalizedSettings.jitenPopupMode.rawValue)};
                    window.kanjiCombinedMode = ${normalizedSettings.kanjiCombinedMode};
                    window.audioSources = ${audioSourcesJson(audioSettings)};
                    window.audioRequestEndpoint = "https://appassets.androidplatform.net/audio";
                    window.dictionaryMediaRequestEndpoint = "https://appassets.androidplatform.net/image";
                    window.disablePopupImageViewportMaxHeight = true;
                    window.audioEnableAutoplay = ${audioSettings.enableAutoplay};
                    window.audioPlaybackMode = "${audioSettings.playbackMode.rawValue}";
                    window.needsAudio = ${ankiSettings.needsAudio};
                    window.allowDupes = ${ankiSettings.allowDupes};
                    window.useAnkiConnect = ${ankiSettings.useAnkiConnect};
                    window.embedMedia = ${ankiSettings.embedMedia};
                    window.compactGlossariesAnki = ${ankiSettings.compactGlossaries};
                    window.customCSS = ${JsonPrimitive(normalizedSettings.customCSS)};
                    window.swipeThreshold = $effectiveSwipeThreshold;
                    window.reducedMotionScrolling = $reducedMotionScrolling;
                    window.reducedMotionScrollScale = $effectiveReducedMotionScrollScale;
                    window.reducedMotionSwipeThreshold = $effectiveReducedMotionSwipeThreshold;
                    window.dictionaryStyles = $styles;
                    window.lookupEntries = [];
                    window.entryCount = 0;
                    window.popupId = null;
                    window.hoshiPostPopupScrollState = function() {
                        var scrollRoot = document.scrollingElement || document.documentElement || document.body;
                        var scrollTop = scrollRoot ? (scrollRoot.scrollTop || window.scrollY || 0) : 0;
                        window.HoshiAndroidPopup.postMessage('scrollState', {
                            atTop: scrollTop <= 1,
                            scrollTop: scrollTop
                        });
                    };
                    window.addEventListener('scroll', function() {
                        window.hoshiPostPopupScrollState();
                    }, { passive: true });
                </script>
                $selectionSupportJs
                $selectionJs
                $selectionConfigureJs
                $popupJs
                $jitenPopupJs
            </head>
            <body>
                <script>${popupGestureScript()}</script>
                <div id="entries-container"></div>
                <div class="overlay">
                    <div class="overlay-close" onclick="closeOverlay()">×</div>
                    <div class="overlay-content"></div>
                </div>
                <script>
                    (function() {
                        var container = document.getElementById('entries-container');
                        var posted = false;
                        var observer = null;
                        function postReady() {
                            if (posted) return;
                            posted = true;
                            webkit.messageHandlers.contentReady.postMessage();
                        }
                        function hasRenderableContent() {
                            if (!container || !window.entryCount) {
                                return true;
                            }
                            return !!container.querySelector('.entry .glossary-content');
                        }
                        window.hoshiPopupObserveContentReady = function() {
                            posted = false;
                            if (observer) {
                                observer.disconnect();
                                observer = null;
                            }
                            if (hasRenderableContent()) {
                                postReady();
                                return;
                            }
                            observer = new MutationObserver(function() {
                                if (hasRenderableContent()) {
                                    postReady();
                                    observer.disconnect();
                                    observer = null;
                                }
                            });
                            observer.observe(container, { childList: true, subtree: true });
                            if (hasRenderableContent()) {
                                postReady();
                            }
                        };
                        window.addEventListener('message', function(event) {
                            if (event.origin !== 'https://appassets.androidplatform.net') return;
                            var message = event.data || {};
                            if (message.type === 'reply') {
                                window.HoshiAndroidPopup.resolveMessage(message.id, message.body);
                                return;
                            }
                            if (message.type === 'highlightSelection') {
                                window.hoshiSelection?.highlightSelection(message.count || 0);
                                return;
                            }
                            if (message.type === 'clearSelection') {
                                window.hoshiSelection?.clearSelection();
                                return;
                            }
                            if (message.type === 'resetPopup') {
                                window.popupId = null;
                                document.documentElement.classList.remove('hoshi-popup-full-width');
                                window.hoshiJitenPopup?.setCard(null);
                                closeOverlay();
                                window.hoshiSelection?.clearSelection();
                                window.resetPopupResults?.();
                                requestAnimationFrame(window.hoshiPostPopupScrollState);
                                return;
                            }
                            if (message.type === 'renderPopup') {
                                window.popupId = message.popupId || null;
                                document.documentElement.classList.toggle('hoshi-popup-full-width', !!message.isFullWidth);
                                closeOverlay();
                                window.entryCount = message.entriesCount || 0;
                                window.hoshiJitenPopup?.setCard(message.jitenCard || null);
                                var initialEntries = [];
                                if (message.initialEntryJson) {
                                    try {
                                        initialEntries[0] = JSON.parse(message.initialEntryJson);
                                    } catch (e) {
                                        initialEntries = [];
                                    }
                                }
                                if (window.replacePopupResults) {
                                    window.replacePopupResults(window.entryCount, initialEntries);
                                } else {
                                    window.lookupEntries = initialEntries;
                                    window.hoshiPopupObserveContentReady?.();
                                    window.renderPopup();
                                }
                                requestAnimationFrame(window.hoshiPostPopupScrollState);
                                return;
                            }
                            if (message.type === 'updateJitenCard') {
                                window.hoshiJitenPopup?.setCard(message.jitenCard || null);
                                return;
                            }
                            if (message.type === 'navigateBack') {
                                window.navigateBack?.();
                                return;
                            }
                            if (message.type === 'navigateForward') {
                                window.navigateForward?.();
                            }
                        });
                        webkit.messageHandlers.shellReady.postMessage(null);
                        requestAnimationFrame(window.hoshiPostPopupScrollState);
                    })();
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    internal fun entryJsonString(result: LookupResult): String = result.toEntryJson().toString()

    internal fun kanjiResultJsonString(
        result: KanjiResult,
        settings: DictionarySettings = DictionarySettings(),
    ): String = result.toKanjiJson(settings).toString()

    private fun dictionaryStylesJson(styles: Map<String, String>): JsonObject =
        buildJsonObject {
            styles.forEach { (dictionary, css) ->
                put(dictionary, css)
            }
        }

    private fun dictionaryNamesJson(names: Set<String>): String =
        buildJsonArray {
            names.sorted().forEach { add(JsonPrimitive(it)) }
        }.toString()

    private fun audioSourcesJson(settings: AudioSettings): String =
        buildJsonArray {
            settings.audioSources
                .filter { it.isEnabled }
                .forEach { source ->
                    add(JsonPrimitive(if (source == AudioSettings.LocalAudioSource) AudioSettings.InternalLocalAudioUrl else source.url))
                }
        }.toString()

    private fun popupCssNumber(value: Double): String {
        val formatted = String.format(Locale.US, "%.2f", value).trimEnd('0')
        return if (formatted.endsWith('.')) "${formatted}0" else formatted
    }

    private fun customCssStyle(css: String): String {
        val content = css.trim()
        if (content.isEmpty()) return ""
        return """<style id="popup-custom-css">${content.styleElementContentEscaped()}</style>"""
    }

    private fun String.styleElementContentEscaped(): String =
        replace(Regex("</style", RegexOption.IGNORE_CASE), "<\\/style")

    private fun popupFontPrewarmScript(): String = """
        (function() {
            var prewarmedFaces = typeof WeakSet === 'function' ? new WeakSet() : null;
            function rememberFace(face) {
                if (!face) return false;
                if (prewarmedFaces) {
                    if (prewarmedFaces.has(face)) return false;
                    prewarmedFaces.add(face);
                }
                return true;
            }
            window.hoshiPopupPrewarmFonts = function() {
                if (!document.fonts) return;
                try {
                    document.fonts.forEach(function(face) {
                        if (!rememberFace(face) || face.status !== 'unloaded' || typeof face.load !== 'function') {
                            return;
                        }
                        try {
                            face.load().catch(function() {});
                        } catch (e) {}
                    });
                } catch (e) {}
            };
            window.hoshiPopupPrewarmFonts();
            setTimeout(window.hoshiPopupPrewarmFonts, 0);
            setTimeout(window.hoshiPopupPrewarmFonts, 100);
        })();
    """.trimIndent()

    private fun popupGestureScript(): String = """
        (function() {
            if (window.reducedMotionScrolling) {
                var reducedMotionStartY = 0;
                var root = function() {
                    return document.scrollingElement || document.documentElement || document.body;
                };
                var scrollByPopupHeight = function(direction) {
                    var scrollRoot = root();
                    var popupHeight = document.documentElement.clientHeight || window.innerHeight || scrollRoot.clientHeight;
                    var maxScroll = Math.max(0, scrollRoot.scrollHeight - popupHeight);
                    var current = scrollRoot.scrollTop || window.scrollY || 0;
                    var target = Math.max(0, Math.min(maxScroll, current + popupHeight * window.reducedMotionScrollScale * direction));
                    scrollRoot.scrollTop = target;
                    window.scrollTo(0, target);
                };
                document.addEventListener('touchstart', function(e) {
                    if (e.touches.length === 1) {
                        reducedMotionStartY = e.touches[0].clientY;
                    }
                }, { passive: true });
                document.addEventListener('touchmove', function(e) {
                    if (e.touches.length === 1 && e.cancelable) {
                        e.preventDefault();
                    }
                }, { passive: false });
                document.addEventListener('touchend', function(e) {
                    if (!e.changedTouches.length) return;
                    var delta = reducedMotionStartY - e.changedTouches[0].clientY;
                    var threshold = window.reducedMotionSwipeThreshold;
                    if (delta > threshold) {
                        scrollByPopupHeight(1);
                    } else if (delta < -threshold) {
                        scrollByPopupHeight(-1);
                    }
                }, { passive: true });
                document.addEventListener('wheel', function(e) {
                    if (e.deltaY === 0) return;
                    scrollByPopupHeight(e.deltaY > 0 ? 1 : -1);
                    e.preventDefault();
                }, { passive: false });
            }
            var startX, startY;
            document.addEventListener('touchstart', function(e) {
                startX = e.touches[0].clientX;
                startY = e.touches[0].clientY;
            });
            document.addEventListener('touchend', function(e) {
                var dx = e.changedTouches[0].clientX - startX;
                var dy = e.changedTouches[0].clientY - startY;
                var absDx = Math.abs(dx);
                var absDy = Math.abs(dy);
                var hasJitenPage = window.hoshiJitenPopup?.hasCard?.() === true;
                var threshold = hasJitenPage ? 40 : window.swipeThreshold;
                var isHorizontalDismiss = threshold && absDx > threshold && absDx > absDy * 1.75;
                var hasSelection = window.getSelection().toString();
                if (isHorizontalDismiss && !hasSelection) {
                    if (hasJitenPage) {
                        window.hoshiJitenPopup.navigate(dx);
                    } else {
                        webkit.messageHandlers.swipeDismiss.postMessage(null);
                    }
                }
            });
        })();
    """.trimIndent()

    private fun LookupResult.toEntryJson(): JsonObject = buildJsonObject {
        put("expression", term.expression)
        put("reading", term.reading)
        put("matched", matched)
        putJsonArray("deinflectionTraceRows") {
            traceCandidates.sortedForDisplay().forEach { candidate ->
                add(
                    buildJsonArray {
                        candidate.trace.reversedArray().forEach { transformGroup ->
                            add(
                                buildJsonObject {
                                    put("name", transformGroup.name)
                                    put("description", transformGroup.description)
                                },
                            )
                        }
                    },
                )
            }
        }
        putJsonArray("glossaries") {
            term.glossaries.forEach { glossary ->
                add(
                    buildJsonObject {
                        put("dictionary", glossary.dictName)
                        put("content", glossary.glossary)
                        put("definitionTags", glossary.definitionTags)
                        put("termTags", glossary.termTags)
                    },
                )
            }
        }
        putJsonArray("frequencies") {
            term.frequencies.forEach { frequency ->
                add(
                    buildJsonObject {
                        put("dictionary", frequency.dictName)
                        putJsonArray("frequencies") {
                            frequency.frequencies.forEach { tag ->
                                add(
                                    buildJsonObject {
                                        put("value", tag.value)
                                        put("displayValue", tag.displayValue)
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }
        putJsonArray("pitches") {
            term.pitches.forEach { pitch ->
                add(
                    buildJsonObject {
                        put("dictionary", pitch.dictName)
                        putJsonArray("pitchPositions") {
                            pitch.pitchPositions.distinct().forEach { add(JsonPrimitive(it)) }
                        }
                        putJsonArray("transcriptions") {
                            pitch.transcriptions.distinct().forEach { add(JsonPrimitive(it)) }
                        }
                    },
                )
            }
        }
        putJsonArray("rules") {
            term.rules.splitToSequence(' ')
                .filter { it.isNotBlank() }
                .forEach { add(JsonPrimitive(it)) }
        }
    }

    private fun KanjiResult.toKanjiJson(settings: DictionarySettings): JsonObject = buildJsonObject {
        put("character", character)
        put("combined", toCombinedKanjiEntryJson(settings))
        putJsonArray("entries") {
            entries.forEach { entry -> add(entry.toKanjiEntryJson(settings)) }
        }
    }

    private fun KanjiEntry.toKanjiEntryJson(settings: DictionarySettings = DictionarySettings()): JsonObject = buildJsonObject {
        val normalized = normalizeKanjiEntry()
        put("dictionary", dictName)
        put("onyomi", normalized.onyomi.joinToString(" ").ifBlank { onyomi })
        put("kunyomi", normalized.kunyomi.joinToString(" ").ifBlank { kunyomi })
        put("tags", normalized.tags.joinToString(" ").ifBlank { tags })
        putJsonArray("definitions") {
            normalized.definitions.forEach { add(JsonPrimitive(it)) }
        }
        putJsonArray("stats") {
            stats.forEach { stat ->
                add(
                    buildJsonObject {
                        put("name", stat.name)
                        put("value", stat.value)
                        put("showByDefault", isDefaultKanjiStat(settings, stat.name))
                    },
                )
            }
        }
        putJsonArray("radicals") {
            normalized.radicals.distinct().forEach { add(JsonPrimitive(it)) }
        }
        putJsonArray("radicalMeanings") {
            normalized.radicalMeanings.distinct().forEach { add(JsonPrimitive(it)) }
        }
        putJsonArray("radicalReadings") {
            normalized.radicalReadings.distinct().forEach { add(JsonPrimitive(it)) }
        }
        putJsonArray("radicalExamples") {
            normalized.radicalExamples.distinct().forEach { add(JsonPrimitive(it)) }
        }
        putJsonArray("components") {
            normalized.components.distinct().forEach { add(JsonPrimitive(it)) }
        }
        putJsonArray("readingHints") {
            normalized.readingHints.distinct().forEach { add(JsonPrimitive(it)) }
        }
        putJsonArray("combinations") {
            normalized.combinations.distinct().take(60).forEach { add(JsonPrimitive(it)) }
        }
    }

    private data class NormalizedKanjiFields(
        val onyomi: List<String> = emptyList(),
        val kunyomi: List<String> = emptyList(),
        val tags: List<String> = emptyList(),
        val definitions: List<String> = emptyList(),
        val stats: List<KanjiStat> = emptyList(),
        val radicals: List<String> = emptyList(),
        val radicalMeanings: List<String> = emptyList(),
        val radicalReadings: List<String> = emptyList(),
        val radicalExamples: List<String> = emptyList(),
        val components: List<String> = emptyList(),
        val readingHints: List<String> = emptyList(),
        val combinations: List<String> = emptyList(),
    ) {
        operator fun plus(other: NormalizedKanjiFields): NormalizedKanjiFields = NormalizedKanjiFields(
            onyomi = (onyomi + other.onyomi).distinct(),
            kunyomi = (kunyomi + other.kunyomi).distinct(),
            tags = (tags + other.tags).distinct(),
            definitions = (definitions + other.definitions).distinct(),
            stats = (stats + other.stats).distinctBy { it.name to it.value },
            radicals = radicals + other.radicals,
            radicalMeanings = radicalMeanings + other.radicalMeanings,
            radicalReadings = radicalReadings + other.radicalReadings,
            radicalExamples = radicalExamples + other.radicalExamples,
            components = components + other.components,
            readingHints = readingHints + other.readingHints,
            combinations = combinations + other.combinations,
        )
    }

    private enum class KanjiField {
        Onyomi,
        Kunyomi,
        Tags,
        Definitions,
        Radicals,
        RadicalMeanings,
        RadicalReadings,
        RadicalExamples,
        Components,
        ReadingHints,
        Combinations,
    }

    private data class KanjiMapping(
        val name: String,
        val titleContains: String,
        val rules: List<KanjiMappingRule>,
    ) {
        fun matches(entry: KanjiEntry): Boolean =
            titleContains.isNotBlank() && entry.dictName.contains(titleContains, ignoreCase = true)
    }

    private sealed interface KanjiMappingRule {
        fun apply(entry: KanjiEntry): NormalizedKanjiFields
    }

    private data class DirectTextRule(
        val source: String,
        val target: KanjiField,
    ) : KanjiMappingRule {
        override fun apply(entry: KanjiEntry): NormalizedKanjiFields {
            val values = when (source) {
                "onyomi" -> entry.onyomi.splitKanjiValues()
                "kunyomi" -> entry.kunyomi.splitKanjiValues()
                "tags" -> entry.tags.splitKanjiValues()
                "definitions" -> entry.definitions.toList().filter { it.isNotBlank() }
                else -> emptyList()
            }
            return values.toKanjiFields(target)
        }
    }

    private data class StatRule(
        val names: Set<String>,
    ) : KanjiMappingRule {
        override fun apply(entry: KanjiEntry): NormalizedKanjiFields =
            NormalizedKanjiFields(stats = entry.stats.filter { it.name in names })
    }

    private data class DefinitionPrefixRule(
        val prefix: String,
        val target: KanjiField,
        val splitSeparator: Char? = null,
    ) : KanjiMappingRule {
        override fun apply(entry: KanjiEntry): NormalizedKanjiFields =
            entry.definitions
                .filter { it.trim().startsWith(prefix) }
                .flatMap { it.trim().substringAfter(prefix).splitKanjiValues(splitSeparator) }
                .toKanjiFields(target)
    }

    private data class DefinitionSectionRule(
        val headerContains: String,
        val target: KanjiField,
        val splitValues: Boolean = false,
    ) : KanjiMappingRule {
        override fun apply(entry: KanjiEntry): NormalizedKanjiFields =
            entry.definitions.toList()
                .sectionValuesAfterHeader(headerContains)
                .let { values -> if (splitValues) values.flatMap { it.splitKanjiValues() } else values }
                .toKanjiFields(target)
    }

    private data class DefinitionWithoutSectionRule(
        val headerContains: String,
        val target: KanjiField,
    ) : KanjiMappingRule {
        override fun apply(entry: KanjiEntry): NormalizedKanjiFields =
            entry.definitions.toList()
                .withoutDefinitionSection(headerContains)
                .toKanjiFields(target)
    }

    private fun KanjiEntry.normalizeKanjiEntry(): NormalizedKanjiFields {
        val mapping = KanjiMappings.firstOrNull { it.matches(this) } ?: GenericKanjiMapping
        return mapping.rules.fold(NormalizedKanjiFields()) { acc, rule -> acc + rule.apply(this) }
    }

    private fun List<String>.sectionValuesAfterHeader(headerContains: String): List<String> {
        val values = mutableListOf<String>()
        var collecting = false
        forEach { raw ->
            val line = raw.trim()
            when {
                line.contains(headerContains) -> collecting = true
                line.startsWith(KanjiMapHeaderPrefix) -> collecting = false
                collecting -> values += line
            }
        }
        return values.filter(String::isNotBlank)
    }

    private fun List<String>.withoutDefinitionSection(headerContains: String): List<String> {
        val headerIndex = indexOfFirst { it.trim().contains(headerContains) }
        if (headerIndex < 0) return filter { it.isNotBlank() }
        val start = if (headerIndex > 0 && this[headerIndex - 1].isBlank()) headerIndex - 1 else headerIndex
        val end = indexOfFirstFrom(headerIndex + 1) {
            val line = it.trim()
            line.isBlank() || line.startsWith(KanjiMapHeaderPrefix)
        }.let { if (it < 0) size else it }
        return (take(start) + drop(end)).filter { it.isNotBlank() }
    }

    private inline fun List<String>.indexOfFirstFrom(startIndex: Int, predicate: (String) -> Boolean): Int {
        for (index in startIndex until size) {
            if (predicate(this[index])) return index
        }
        return -1
    }

    private fun List<String>.toKanjiFields(target: KanjiField): NormalizedKanjiFields =
        when (target) {
            KanjiField.Onyomi -> NormalizedKanjiFields(onyomi = this)
            KanjiField.Kunyomi -> NormalizedKanjiFields(kunyomi = this)
            KanjiField.Tags -> NormalizedKanjiFields(tags = this)
            KanjiField.Definitions -> NormalizedKanjiFields(definitions = this)
            KanjiField.Radicals -> NormalizedKanjiFields(radicals = this)
            KanjiField.RadicalMeanings -> NormalizedKanjiFields(radicalMeanings = this)
            KanjiField.RadicalReadings -> NormalizedKanjiFields(radicalReadings = this)
            KanjiField.RadicalExamples -> NormalizedKanjiFields(radicalExamples = this)
            KanjiField.Components -> NormalizedKanjiFields(components = this)
            KanjiField.ReadingHints -> NormalizedKanjiFields(readingHints = this)
            KanjiField.Combinations -> NormalizedKanjiFields(combinations = this)
        }

    private fun String.splitKanjiValues(separator: Char? = null): List<String> =
        replace('\u3000', ' ')
            .let { if (separator == null) it else it.replace(separator, ' ') }
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "n/a" }

    private fun KanjiResult.toCombinedKanjiEntryJson(settings: DictionarySettings): JsonObject {
        val fieldsByDictionary = entries.associate { it.dictName to it.normalizeKanjiEntry() }
        val fallbackDictionaries = entries.map { it.dictName }
        val normalized = NormalizedKanjiFields(
            onyomi = fieldsByDictionary.firstAvailable(settings, KanjiCombinedField.Onyomi, fallbackDictionaries) {
                it.onyomi
            },
            kunyomi = fieldsByDictionary.firstAvailable(settings, KanjiCombinedField.Kunyomi, fallbackDictionaries) {
                it.kunyomi
            },
            tags = fieldsByDictionary.firstAvailable(settings, KanjiCombinedField.Tags, fallbackDictionaries) {
                it.tags
            },
            definitions = fieldsByDictionary.firstAvailable(
                settings,
                KanjiCombinedField.Definitions,
                fallbackDictionaries,
            ) { it.definitions },
            stats = entries.toList().selectedCombinedKanjiStats(settings, fallbackDictionaries),
            radicals = fieldsByDictionary.firstAvailable(settings, KanjiCombinedField.Radicals, fallbackDictionaries) {
                it.radicals
            },
            radicalMeanings = fieldsByDictionary.firstAvailable(
                settings,
                KanjiCombinedField.Radicals,
                fallbackDictionaries,
            ) { it.radicalMeanings },
            radicalReadings = fieldsByDictionary.firstAvailable(
                settings,
                KanjiCombinedField.Radicals,
                fallbackDictionaries,
            ) { it.radicalReadings },
            radicalExamples = fieldsByDictionary.firstAvailable(
                settings,
                KanjiCombinedField.Radicals,
                fallbackDictionaries,
            ) { it.radicalExamples },
            components = fieldsByDictionary.firstAvailable(
                settings,
                KanjiCombinedField.Components,
                fallbackDictionaries,
            ) { it.components },
            readingHints = fieldsByDictionary.firstAvailable(
                settings,
                KanjiCombinedField.Components,
                fallbackDictionaries,
            ) { it.readingHints },
            combinations = fieldsByDictionary.firstAvailable(
                settings,
                KanjiCombinedField.Components,
                fallbackDictionaries,
            ) { it.combinations },
        )

        return buildJsonObject {
            put("dictionary", "Combined")
            put("combined", true)
            put("onyomi", normalized.onyomi.joinToString(" "))
            put("kunyomi", normalized.kunyomi.joinToString(" "))
            put("tags", normalized.tags.joinToString(" "))
            putJsonArray("definitions") {
                normalized.definitions.distinct().forEach { add(JsonPrimitive(it)) }
            }
            putJsonArray("stats") {
                normalized.stats
                    .distinctBy { it.name to it.value }
                    .forEach { stat ->
                        add(
                            buildJsonObject {
                                put("name", stat.name.kanjiStatDisplayName())
                                put("value", stat.value)
                                put(
                                    "showByDefault",
                                    stat.name in PreferredKanjiStatNames ||
                                        settings.kanjiCombinedSettings.dictionaryDisplay.values.any {
                                            stat.name in it.shownStats
                                        },
                                )
                            },
                        )
                    }
            }
            putJsonArray("radicals") {
                normalized.radicals.distinct().forEach { add(JsonPrimitive(it)) }
            }
            putJsonArray("radicalMeanings") {
                normalized.radicalMeanings.distinct().forEach { add(JsonPrimitive(it)) }
            }
            putJsonArray("radicalReadings") {
                normalized.radicalReadings.distinct().forEach { add(JsonPrimitive(it)) }
            }
            putJsonArray("radicalExamples") {
                normalized.radicalExamples.distinct().forEach { add(JsonPrimitive(it)) }
            }
            putJsonArray("components") {
                normalized.components.distinct().forEach { add(JsonPrimitive(it)) }
            }
            putJsonArray("readingHints") {
                normalized.readingHints.distinct().forEach { add(JsonPrimitive(it)) }
            }
            putJsonArray("combinations") {
                normalized.combinations.distinct().take(60).forEach { add(JsonPrimitive(it)) }
            }
        }
    }

    private fun Map<String, NormalizedKanjiFields>.firstAvailable(
        settings: DictionarySettings,
        field: KanjiCombinedField,
        fallbackDictionaries: List<String>,
        values: (NormalizedKanjiFields) -> List<String>,
    ): List<String> =
        settings.kanjiCombinedSettings.priorityFor(field, fallbackDictionaries)
            .asSequence()
            .mapNotNull { this[it] }
            .map(values)
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()

    private fun List<KanjiEntry>.selectedCombinedKanjiStats(
        settings: DictionarySettings,
        fallbackDictionaries: List<String>,
    ): List<KanjiStat> {
        val stats = mutableListOf<KanjiStat>()
        firstAvailableStat(settings, KanjiCombinedField.Frequency, fallbackDictionaries, "freq", "Frequency")
            ?.let(stats::add)
        firstAvailableStat(settings, KanjiCombinedField.Strokes, fallbackDictionaries, "strokes", "Strokes", "\u753b\u6570")
            ?.let(stats::add)
        stats += flatMap { entry -> entry.stats.toList() }
        return stats.distinctBy { it.name to it.value }
    }

    private fun List<KanjiEntry>.firstAvailableStat(
        settings: DictionarySettings,
        field: KanjiCombinedField,
        fallbackDictionaries: List<String>,
        vararg names: String,
    ): KanjiStat? {
        val byDictionary = associateBy { it.dictName }
        return settings.kanjiCombinedSettings.priorityFor(field, fallbackDictionaries)
            .asSequence()
            .mapNotNull { byDictionary[it] }
            .flatMap { entry -> entry.stats.asSequence() }
            .firstOrNull { stat -> names.any { stat.name.equals(it, ignoreCase = true) } }
    }

    private fun KanjiEntry.isDefaultKanjiStat(settings: DictionarySettings, name: String): Boolean =
        name in PreferredKanjiStatNames || name in settings.kanjiCombinedSettings.displayFor(dictName).shownStats

    private fun KanjiEntry.isKanjidicEntry(): Boolean =
        dictName.contains("KANJIDIC", ignoreCase = true)

    private fun KanjiEntry.isKanjiMapEntry(): Boolean =
        dictName.contains("KanjiMap", ignoreCase = true) ||
            definitions.any { it.startsWith("部首：") || it.startsWith("＝＝＝＝") }

    private data class KanjiMapData(
        val radicals: List<String> = emptyList(),
        val radicalMeanings: List<String> = emptyList(),
        val radicalReadings: List<String> = emptyList(),
        val radicalExamples: List<String> = emptyList(),
        val components: List<String> = emptyList(),
        val readingHints: List<String> = emptyList(),
        val combinations: List<String> = emptyList(),
    ) {
        operator fun plus(other: KanjiMapData): KanjiMapData = KanjiMapData(
            radicals = radicals + other.radicals,
            radicalMeanings = radicalMeanings + other.radicalMeanings,
            radicalReadings = radicalReadings + other.radicalReadings,
            radicalExamples = radicalExamples + other.radicalExamples,
            components = components + other.components,
            readingHints = readingHints + other.readingHints,
            combinations = combinations + other.combinations,
        )
    }

    private fun KanjiEntry.toKanjiMapData(): KanjiMapData {
        val sections = mutableMapOf<String, MutableList<String>>()
        var currentSection: String? = null
        val radicals = mutableListOf<String>()
        val radicalMeanings = mutableListOf<String>()
        val radicalReadings = mutableListOf<String>()
        val radicalExamples = mutableListOf<String>()

        definitions.forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("部首：") -> radicals += line.substringAfter('：').splitKanjiMapValues()
                line.startsWith("意味：") -> radicalMeanings += line.substringAfter('：').splitKanjiMapValues(separator = ',')
                line.startsWith("読み：") -> radicalReadings += line.substringAfter('：').splitKanjiMapValues()
                line.startsWith("例え：") -> radicalExamples += line.substringAfter('：').splitKanjiMapValues()
                line.contains("分解") -> currentSection = "components"
                line.contains("読みヒント") -> currentSection = "readingHints"
                line.contains("組み合わせ") -> currentSection = "combinations"
                currentSection != null -> sections.getOrPut(currentSection) { mutableListOf() } += line
            }
        }

        return KanjiMapData(
            radicals = radicals,
            radicalMeanings = radicalMeanings,
            radicalReadings = radicalReadings,
            radicalExamples = radicalExamples,
            components = sections["components"].orEmpty().flatMap { it.splitKanjiMapValues() },
            readingHints = sections["readingHints"].orEmpty(),
            combinations = sections["combinations"].orEmpty(),
        )
    }

    private fun String.splitKanjiMapValues(separator: Char? = null): List<String> =
        replace('　', ' ')
            .let { if (separator == null) it else it.replace(separator, ' ') }
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "n/a" }

    private fun String.kanjiStatDisplayName(): String = when (this) {
        "strokes", "\u753b\u6570" -> "Strokes"
        "freq" -> "Frequency"
        "grade" -> "Grade"
        "jlpt" -> "JLPT"
        "heisig", "heisig6" -> "Heisig"
        "skip" -> "SKIP"
        "sh_desc" -> "Descriptor"
        "four_corner" -> "Four-corner"
        "ucs" -> "Unicode"
        else -> replace('_', ' ').replaceFirstChar { it.titlecase(Locale.US) }
    }

    private val PreferredKanjiStatNames = setOf(
        "strokes",
        "freq",
        "\u753b\u6570",
    )

    private val JpdbKanjiStatNames = setOf(
        "\u6f22\u5b57\u691c\u5b9a",
        "\u65e7\u5b57\u4f53",
        "\u65b0\u5b57\u4f53",
    )

    private val GenericKanjiMapping = KanjiMapping(
        name = "Generic kanji",
        titleContains = "",
        rules = listOf(
            DirectTextRule("onyomi", KanjiField.Onyomi),
            DirectTextRule("kunyomi", KanjiField.Kunyomi),
            DirectTextRule("tags", KanjiField.Tags),
            DirectTextRule("definitions", KanjiField.Definitions),
            StatRule(PreferredKanjiStatNames),
        ),
    )

    private val KanjiMappings = listOf(
        KanjiMapping(
            name = "KANJIDIC",
            titleContains = "KANJIDIC",
            rules = listOf(
                DirectTextRule("onyomi", KanjiField.Onyomi),
                DirectTextRule("kunyomi", KanjiField.Kunyomi),
                DirectTextRule("tags", KanjiField.Tags),
                DirectTextRule("definitions", KanjiField.Definitions),
                StatRule(PreferredKanjiStatNames),
            ),
        ),
        KanjiMapping(
            name = "TheKanjiMap",
            titleContains = "KanjiMap",
            rules = listOf(
                DefinitionPrefixRule(KanjiMapRadicalPrefix, KanjiField.Radicals),
                DefinitionPrefixRule(KanjiMapMeaningPrefix, KanjiField.RadicalMeanings, splitSeparator = ','),
                DefinitionPrefixRule(KanjiMapReadingPrefix, KanjiField.RadicalReadings),
                DefinitionPrefixRule(KanjiMapExamplePrefix, KanjiField.RadicalExamples),
                DefinitionSectionRule(KanjiMapDecompositionHeader, KanjiField.Components, splitValues = true),
                DefinitionSectionRule(KanjiMapReadingHintHeader, KanjiField.ReadingHints),
                DefinitionSectionRule(KanjiMapCombinationHeader, KanjiField.Combinations),
            ),
        ),
        KanjiMapping(
            name = "JPDB Kanji",
            titleContains = "JPDB Kanji",
            rules = listOf(
                DirectTextRule("onyomi", KanjiField.Onyomi),
                DirectTextRule("kunyomi", KanjiField.Kunyomi),
                DirectTextRule("tags", KanjiField.Tags),
                DefinitionWithoutSectionRule(JpdbDecompositionHeader, KanjiField.Definitions),
                DefinitionSectionRule(JpdbDecompositionHeader, KanjiField.Components, splitValues = true),
                StatRule(PreferredKanjiStatNames + JpdbKanjiStatNames),
            ),
        ),
    )

    private const val KanjiMapRadicalPrefix = "\u90e8\u9996\uff1a"
    private const val KanjiMapMeaningPrefix = "\u610f\u5473\uff1a"
    private const val KanjiMapReadingPrefix = "\u8aad\u307f\uff1a"
    private const val KanjiMapExamplePrefix = "\u4f8b\u3048\uff1a"
    private const val KanjiMapHeaderPrefix = "\uff1d\uff1d\uff1d\uff1d"
    private const val KanjiMapDecompositionHeader = "\u5206\u89e3"
    private const val KanjiMapReadingHintHeader = "\u8aad\u307f\u30d2\u30f3\u30c8"
    private const val KanjiMapCombinationHeader = "\u7d44\u307f\u5408\u308f\u305b"
    private const val JpdbDecompositionHeader = "\u6f22\u5b57\u5206\u89e3"

    private fun Array<TraceCandidate>.sortedForDisplay(): List<TraceCandidate> =
        withIndex()
            .filter { it.value.source != TraceSource.DICTIONARY }
            .sortedWith(
                compareBy<IndexedValue<TraceCandidate>> { it.value.source.displayRank() }
                    .thenBy { it.index },
            )
            .map { it.value }

    private fun TraceSource.displayRank(): Int =
        when (this) {
            TraceSource.ALGORITHM -> 0
            TraceSource.BOTH -> 1
            TraceSource.DICTIONARY -> 2
        }

    private const val androidColorSchemeCss = """
        html[data-hoshi-color-scheme="light"],
        html[data-hoshi-color-scheme="light"] body {
            --background-color: #fff;
            --background-color-light: #fff;
            --text-color: #000;
            color-scheme: light;
            background-color: #fff !important;
        }

        html[data-hoshi-color-scheme="light"] .overlay {
            background: #eee;
            color: #000;
        }

        html[data-hoshi-color-scheme="dark"],
        html[data-hoshi-color-scheme="dark"] body {
            --background-color: #000;
            --background-color-light: #000;
            --text-color: #fff;
            --text-color-light1: #aaaaaa;
            --text-color-light2: #999999;
            --text-color-light3: #888888;
            --text-color-light4: #777777;
            --background-color-dark1: #333333;
            color-scheme: dark;
            background-color: #000 !important;
        }

        html[data-hoshi-color-scheme="dark"] .overlay {
            background: #000;
            color: #fff;
        }

        html[data-hoshi-color-scheme="dark"] .glossary-group > div[data-dictionary] {
            color: var(--text-color) !important;
        }
    """

    private const val eInkPopupCss = """
        /*
         * Adapted from E Ink CSS for Yomitan:
         * https://github.com/Mansive/yomitan-eink-css
         */
        html[data-hoshi-eink-mode="true"],
        html[data-hoshi-eink-mode="true"] body {
            --background-color: #fff;
            --background-color-light: #fff;
            --text-color: #000;
            --text-color-light1: #000;
            --text-color-light2: #000;
            --text-color-light3: #000;
            --text-color-light4: #000;
            --background-color-dark1: #fff;
            color-scheme: light;
        }

        html[data-hoshi-eink-mode="true"] *,
        html[data-hoshi-eink-mode="true"] *::before,
        html[data-hoshi-eink-mode="true"] *::after {
            transition: none !important;
            animation-duration: 0s !important;
            box-shadow: none !important;
            text-shadow: none !important;
        }

        html[data-hoshi-eink-mode="true"] ::highlight(hoshi-selection) {
            background-color: transparent !important;
            color: inherit;
            text-decoration-line: underline;
            text-decoration-color: #000;
            text-decoration-thickness: 1.5px;
            text-underline-offset: 2px;
        }

        html[data-hoshi-eink-mode="true"] .deinflection-tag,
        html[data-hoshi-eink-mode="true"] .expr-tag,
        html[data-hoshi-eink-mode="true"] .glossary-tag,
        html[data-hoshi-eink-mode="true"] .pitch-dict-label {
            background-color: #fff !important;
            color: #000 !important;
            border: 1px solid #000 !important;
            border-radius: 0 !important;
        }

        html[data-hoshi-eink-mode="true"] .frequency-group {
            background-color: #fff !important;
            color: #000 !important;
            border: 1px solid #000 !important;
            border-radius: 0 !important;
        }

        html[data-hoshi-eink-mode="true"] .frequency-dict-label {
            background-color: #fff !important;
            color: #000 !important;
            border-right: 1px solid #000 !important;
        }

        html[data-hoshi-eink-mode="true"] .frequency-values {
            background-color: #fff !important;
            color: #000 !important;
        }

        html[data-hoshi-eink-mode="true"] .button-slot {
            border-radius: 0 !important;
            background-color: transparent !important;
            color: #000 !important;
            opacity: 1 !important;
        }

        html[data-hoshi-eink-mode="true"] .button-slot:active {
            background-color: #fff !important;
            outline: 1px solid #000 !important;
            outline-offset: -1px !important;
        }

        html[data-hoshi-eink-mode="true"] .glossary-group > summary::before {
            opacity: 1 !important;
        }

        html[data-hoshi-eink-mode="true"] .dict-label {
            opacity: 1 !important;
        }

        html[data-hoshi-eink-mode="true"] .overlay {
            background: #fff !important;
            color: #000 !important;
            border-top: 1px solid #000 !important;
        }

        html[data-hoshi-color-scheme="dark"][data-hoshi-eink-mode="true"],
        html[data-hoshi-color-scheme="dark"][data-hoshi-eink-mode="true"] body {
            --background-color: #000;
            --background-color-light: #000;
            --text-color: #fff;
            --text-color-light1: #fff;
            --text-color-light2: #fff;
            --text-color-light3: #fff;
            --text-color-light4: #fff;
            --background-color-dark1: #000;
            color-scheme: dark;
        }

        html[data-hoshi-color-scheme="dark"][data-hoshi-eink-mode="true"] .deinflection-tag,
        html[data-hoshi-color-scheme="dark"][data-hoshi-eink-mode="true"] .expr-tag,
        html[data-hoshi-color-scheme="dark"][data-hoshi-eink-mode="true"] .glossary-tag,
        html[data-hoshi-color-scheme="dark"][data-hoshi-eink-mode="true"] .pitch-dict-label,
        html[data-hoshi-color-scheme="dark"][data-hoshi-eink-mode="true"] .frequency-group {
            background-color: #000 !important;
            color: #fff !important;
            border: 1px solid #fff !important;
        }

        html[data-hoshi-color-scheme="dark"][data-hoshi-eink-mode="true"] ::highlight(hoshi-selection) {
            text-decoration-color: #fff;
        }

        html[data-hoshi-color-scheme="dark"][data-hoshi-eink-mode="true"] .frequency-dict-label {
            background-color: #000 !important;
            color: #fff !important;
            border-right: 1px solid #fff !important;
        }

        html[data-hoshi-color-scheme="dark"][data-hoshi-eink-mode="true"] .frequency-values,
        html[data-hoshi-color-scheme="dark"][data-hoshi-eink-mode="true"] .button-slot,
        html[data-hoshi-color-scheme="dark"][data-hoshi-eink-mode="true"] .overlay {
            background-color: #000 !important;
            color: #fff !important;
        }

        html[data-hoshi-color-scheme="dark"][data-hoshi-eink-mode="true"] .button-slot:active {
            outline: 1px solid #fff !important;
        }

        html[data-hoshi-color-scheme="dark"][data-hoshi-eink-mode="true"] .overlay {
            border-top: 1px solid #fff !important;
        }
    """

    private const val PopupAssetBaseUrl = "https://appassets.androidplatform.net/popup"
}

private fun selectionSupportAssetNames(contentLanguageProfile: ContentLanguageProfile): List<String> =
    when (contentLanguageProfile.dictionaryLanguageId) {
        ContentLanguageProfile.EnglishLanguageId -> listOf("language-ja.js", "selection-en.js")
        else -> listOf("language-ja.js", "selection-ja.js")
    }
