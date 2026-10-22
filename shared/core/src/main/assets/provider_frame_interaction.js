(function () {
  'use strict';
  if (window.__vueoFramePlayInstalled) return;
  window.__vueoFramePlayInstalled = true;
  var playSelectors = __VUEO_FRAME_PLAY_SELECTORS__;
  var playerSelectors = __VUEO_PLAYER_FRAME_SELECTORS__;
  var id = String(Date.now()) + '-' + String(Math.random()).slice(2, 12);
  var stopped = false, readyReported = false, playerReported = false;
  var attempts = new WeakMap(), observer, interval, stopTimer;
  function report(event) {
    try { window.vueoCapture.capture('VUEO_FRAME_PLAY|' + JSON.stringify({id: id, event: event})); } catch (_) {}
  }
  function visible(el) {
    if (!el || el.disabled || el.getAttribute('aria-disabled') === 'true') return false;
    var style = window.getComputedStyle(el), rect = el.getBoundingClientRect();
    return style.display !== 'none' && style.visibility !== 'hidden' && Number(style.opacity || '1') !== 0 && rect.width > 0 && rect.height > 0;
  }
  function tick() {
    if (stopped || document.readyState === 'loading') return;
    if (!readyReported) { readyReported = true; report('ready'); }
    if (!playerReported) playerSelectors.some(function (selector) {
      var frame;
      try { frame = document.querySelector(selector); } catch (_) { return false; }
      if (frame && frame.tagName === 'IFRAME' && frame.getAttribute('src')) {
        playerReported = true; report('player-frame'); return true;
      }
      return false;
    });
    if (playerReported) { stop(); return; }
    playSelectors.forEach(function (selector) {
      var el;
      try { el = document.querySelector(selector); } catch (_) { return; }
      // Only explicitly requested media Play buttons. Never generic controls,
      // links, forms or verification widgets, and no coordinate clicking.
      if (!el || el.tagName !== 'BUTTON' || !visible(el)) return;
      var old = attempts.get(el) || {count: 0, at: 0};
      if (old.count >= 2 || Date.now() - old.at < 750) return;
      attempts.set(el, {count: old.count + 1, at: Date.now()});
      try { el.click(); report('play-click'); } catch (_) {}
    });
  }
  function stop() {
    if (stopped) return;
    stopped = true;
    if (observer) observer.disconnect();
    clearInterval(interval); clearTimeout(stopTimer);
    document.removeEventListener('DOMContentLoaded', tick);
  }
  document.addEventListener('DOMContentLoaded', tick);
  observer = new MutationObserver(tick);
  observer.observe(document, {childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'style', 'src', 'disabled']});
  interval = setInterval(tick, 250);
  stopTimer = setTimeout(stop, 12000);
  window.addEventListener('pagehide', stop, {once: true});
  tick();
})();
