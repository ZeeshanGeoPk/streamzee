package com.streamzee.ui.screens

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.roundToLong

internal const val PLAYBACK_JAVASCRIPT_INTERFACE = "StreamzeePlayback"

private const val LIVE_SAVE_INTERVAL_MS = 3_000L
private const val MAX_PLAYBACK_POSITION_MS = 24L * 60L * 60L * 1_000L

internal class WebViewPlaybackBridge(
    initialPositionMs: Long,
    private val onPositionChanged: (Long) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val latestPosition = AtomicLong(initialPositionMs.coerceAtLeast(0L))
    private val lastDispatchedPosition = AtomicLong(initialPositionMs.coerceAtLeast(0L))
    private val resumePending = AtomicBoolean(initialPositionMs > 1_000L)

    @JavascriptInterface
    fun onPlaybackUpdate(positionSeconds: Double, durationSeconds: Double, forceSave: Boolean) {
        if (!positionSeconds.isFinite() || positionSeconds < 0.0) return
        if (durationSeconds.isFinite() && durationSeconds > 0.0 && positionSeconds > durationSeconds + 30.0) return

        val positionMs = (positionSeconds * 1_000.0)
            .roundToLong()
            .coerceIn(0L, MAX_PLAYBACK_POSITION_MS)
        val initialPositionMs = latestPosition.get()

        if (resumePending.get()) {
            if (positionMs + 5_000L < initialPositionMs) return
            resumePending.set(false)
        }

        latestPosition.set(positionMs)

        val previousPositionMs = lastDispatchedPosition.get()
        if (forceSave || abs(positionMs - previousPositionMs) >= LIVE_SAVE_INTERVAL_MS) {
            lastDispatchedPosition.set(positionMs)
            dispatch(positionMs)
        }
    }

    fun latestPositionMs(): Long = latestPosition.get()

    fun flush() {
        dispatch(latestPosition.get())
    }

    private fun dispatch(positionMs: Long) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onPositionChanged(positionMs)
        } else {
            mainHandler.post { onPositionChanged(positionMs) }
        }
    }
}

internal fun playbackUrlWithResume(
    url: String,
    sourceId: String,
    resumePositionMs: Long,
): String {
    if (sourceId != "videasy" || resumePositionMs <= 0L) return url

    return Uri.parse(url)
        .buildUpon()
        .appendQueryParameter("progress", (resumePositionMs / 1_000L).toString())
        .build()
        .toString()
}

internal fun playbackTrackingScript(resumePositionMs: Long): String {
    val resumeSeconds = resumePositionMs.coerceAtLeast(0L) / 1_000.0

    return """
        (function() {
            if (window.__streamzeePlaybackTrackerInstalled) return;
            window.__streamzeePlaybackTrackerInstalled = true;

            var resumeSeconds = $resumeSeconds;
            var attachedMedia = new WeakSet();

            function numberValue(value) {
                var parsed = Number(value);
                return Number.isFinite(parsed) ? parsed : null;
            }

            function firstNumber() {
                for (var i = 0; i < arguments.length; i++) {
                    var parsed = numberValue(arguments[i]);
                    if (parsed !== null) return parsed;
                }
                return null;
            }

            function report(position, duration, forceSave) {
                var parsedPosition = numberValue(position);
                if (parsedPosition === null || parsedPosition < 0) return;

                var parsedDuration = numberValue(duration);
                if (parsedDuration === null || parsedDuration < 0) parsedDuration = 0;

                try {
                    window.$PLAYBACK_JAVASCRIPT_INTERFACE.onPlaybackUpdate(
                        parsedPosition,
                        parsedDuration,
                        Boolean(forceSave)
                    );
                } catch (ignored) {
                }
            }

            function mediaDuration(media) {
                var duration = numberValue(media.duration);
                if (duration !== null) return duration;

                try {
                    if (media.seekable && media.seekable.length > 0) {
                        return media.seekable.end(media.seekable.length - 1);
                    }
                } catch (ignored) {
                }
                return 0;
            }

            function isLikelyContent(media) {
                var duration = mediaDuration(media);
                return duration === 0 || !Number.isFinite(duration) || duration >= 120;
            }

            function applyResume(media) {
                if (resumeSeconds <= 0 || media.__streamzeeResumeApplied) return;

                var duration = mediaDuration(media);
                var canSeek = media.readyState >= 1 &&
                    (duration === 0 || !Number.isFinite(duration) || duration >= 120) &&
                    (duration === 0 || !Number.isFinite(duration) || resumeSeconds < duration);

                if (!canSeek) return;

                try {
                    var target = Number.isFinite(duration) && duration > 1
                        ? Math.min(resumeSeconds, duration - 1)
                        : resumeSeconds;
                    media.currentTime = Math.max(0, target);
                    media.__streamzeeResumeApplied = true;
                } catch (ignored) {
                }
            }

            function reportMedia(media, forceSave) {
                if (!isLikelyContent(media)) return;
                report(media.currentTime, mediaDuration(media), forceSave);
            }

            function attach(media) {
                if (attachedMedia.has(media)) {
                    applyResume(media);
                    return;
                }

                attachedMedia.add(media);
                applyResume(media);
                media.addEventListener("loadedmetadata", function() { applyResume(media); });
                media.addEventListener("durationchange", function() { applyResume(media); });
                media.addEventListener("playing", function() {
                    applyResume(media);
                    reportMedia(media, false);
                });
                media.addEventListener("timeupdate", function() { reportMedia(media, false); });
                media.addEventListener("seeked", function() { reportMedia(media, true); });
                media.addEventListener("pause", function() { reportMedia(media, true); });
                media.addEventListener("ended", function() { reportMedia(media, true); });
            }

            function scanRoot(root) {
                if (!root || !root.querySelectorAll) return;

                root.querySelectorAll("video, audio").forEach(attach);
                root.querySelectorAll("*").forEach(function(element) {
                    if (element.shadowRoot) scanRoot(element.shadowRoot);
                });
                root.querySelectorAll("iframe").forEach(function(frame) {
                    try {
                        if (frame.contentDocument) scanRoot(frame.contentDocument);
                    } catch (ignored) {
                    }
                });
            }

            function parseProviderMessage(event) {
                var payload = event.data;
                if (typeof payload === "string") {
                    try {
                        payload = JSON.parse(payload);
                    } catch (ignored) {
                        return;
                    }
                }
                if (!payload || typeof payload !== "object") return;

                var nested = payload.data && typeof payload.data === "object"
                    ? payload.data
                    : {};
                var position = firstNumber(
                    payload.timestamp,
                    payload.currentTime,
                    payload.time,
                    payload.position,
                    nested.timestamp,
                    nested.currentTime,
                    nested.time,
                    nested.position
                );
                if (position === null) return;

                var duration = firstNumber(payload.duration, nested.duration) || 0;
                var forceSave = payload.event === "complete" ||
                    payload.type === "complete" ||
                    payload.event === "pause" ||
                    payload.type === "pause";
                report(position, duration, forceSave);
            }

            window.addEventListener("message", parseProviderMessage);
            window.addEventListener("pagehide", function() {
                scanRoot(document);
            });

            scanRoot(document);
            new MutationObserver(function() { scanRoot(document); }).observe(
                document.documentElement,
                { childList: true, subtree: true }
            );
            window.setInterval(function() { scanRoot(document); }, 1_000);
        })();
    """.trimIndent()
}
