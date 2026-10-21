package vn.esstar.ainovel.feature.browser

/**
 * Story sites often cover the page with an invisible full-screen layer that opens an ad on the first tap.
 * WebView cannot open pop-ups, so the layer swallows every tap forever. This script removes such layers.
 */
internal object BrowserOverlayCleaner {

    val SCRIPT: String = """
        (function () {
          // Pop-under scripts hijack the first click and call window.open(); WebView returns null, so they
          // keep hijacking every click. Hand them a fake window so they believe the ad opened and let go.
          if (!window.__ainovelOpenStubbed) {
            window.__ainovelOpenStubbed = true;
            var fakeWin = { closed: false, focus: function () {}, blur: function () {}, close: function () {},
                            location: { href: '' }, document: { write: function () {}, close: function () {} } };
            try { window.open = function () { return fakeWin; }; } catch (e) {}
          }
          // Invisible click traps are often ::before/::after pseudo-elements (position:fixed; inset:0) on an
          // empty ad container, so element styles look innocent. Detect them by hit-testing a few points:
          // if the top-most element does not actually contain the point, it is a trap.
          function killTraps() {
            var vw = window.innerWidth, vh = window.innerHeight;
            var pts = [[0.15, 0.2], [0.5, 0.3], [0.85, 0.4], [0.3, 0.6], [0.7, 0.75], [0.5, 0.9]];
            for (var iter = 0; iter < 6; iter++) {
              var hits = [];
              for (var i = 0; i < pts.length; i++) {
                var x = vw * pts[i][0], y = vh * pts[i][1];
                var el = document.elementFromPoint(x, y);
                if (!el || el === document.documentElement || el === document.body) continue;
                var r = el.getBoundingClientRect();
                var inside = x >= r.left && x <= r.right && y >= r.top && y <= r.bottom && r.height > 0;
                if (!inside) hits.push(el);
              }
              var trap = null;
              for (var j = 0; j < hits.length && !trap; j++) {
                var same = 0;
                for (var k = 0; k < hits.length; k++) if (hits[k] === hits[j]) same++;
                if (same >= 2) trap = hits[j];
              }
              if (!trap) break;
              trap.style.setProperty('pointer-events', 'none', 'important');
            }
          }
          if (!document.getElementById('__ainovel_css')) {
            var st = document.createElement('style'); st.id = '__ainovel_css';
            st.textContent = '[id*="ads"]::before,[id*="ads"]::after,[class*="ads"]::before,[class*="ads"]::after{display:none!important;pointer-events:none!important}';
            (document.head || document.documentElement).appendChild(st);
          }
          function sweep() {
            killTraps();
            var vw = window.innerWidth, vh = window.innerHeight;
            var all = document.body ? document.body.querySelectorAll('div, span, a, section, aside') : [];
            for (var i = 0; i < all.length; i++) {
              var el = all[i];
              var cs = window.getComputedStyle(el);
              if (cs.position !== 'fixed') continue;
              var r = el.getBoundingClientRect();
              var covers = r.width >= vw * 0.85 && r.height >= vh * 0.6;
              if (!covers) continue;
              var hasText = (el.innerText || '').trim().length > 40;
              if (hasText) continue;
              // Keep it visible (it may be a dimmed backdrop) but let taps go through to the page.
              el.style.setProperty('pointer-events', 'none', 'important');
              if (cs.opacity === '0' || cs.backgroundColor === 'rgba(0, 0, 0, 0)') { el.style.setProperty('display', 'none', 'important'); }
            }
            if (document.body && window.getComputedStyle(document.body).overflow === 'hidden') { document.body.style.overflow = 'auto'; }
          }
          try { sweep(); } catch (e) {}
          setTimeout(function () { try { sweep(); } catch (e) {} }, 1500);
          setTimeout(function () { try { sweep(); } catch (e) {} }, 4000);
          setTimeout(function () { try { sweep(); } catch (e) {} }, 8000);
        })();
    """.trimIndent()

    /** `truyenfull.live` and `www.truyenfull.live` are the same site; `eyep.blog` is not. */
    fun sameRegistrableDomain(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        return registrable(a) == registrable(b)
    }

    private fun registrable(host: String): String {
        val parts = host.lowercase().removePrefix("www.").split('.')
        return if (parts.size <= 2) parts.joinToString(".") else parts.takeLast(2).joinToString(".")
    }
}
