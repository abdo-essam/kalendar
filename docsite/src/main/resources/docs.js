/*
 * Kalendar documentation — theme, navigation, reveal, contents, copy.
 *
 * No dependencies and no network: the page ships with a strict Content-Security-Policy in mind, and
 * a documentation site that cannot render without reaching a CDN is a documentation site that is
 * offline whenever the CDN is.
 */
(function () {
  'use strict';

  var reduced = window.matchMedia('(prefers-reduced-motion: reduce)');
  var STORAGE_KEY = 'kalendar-theme';

  function ready(run) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', run);
    } else {
      run();
    }
  }

  /*
   * The stored theme is applied by an inline script in <head>, before the first paint. All that is
   * left here is the switch itself — reading the preference from this file instead would paint the
   * default and then correct it, which is the flash of wrong colours that makes a docs site feel
   * cheap on every navigation.
   */
  function setUpTheme() {
    var button = document.querySelector('.theme-toggle');
    if (!button) {
      return;
    }
    button.addEventListener('click', function () {
      var next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
      document.documentElement.dataset.theme = next;
      try {
        localStorage.setItem(STORAGE_KEY, next);
      } catch (error) {
        /* Private browsing refuses storage; the toggle still works for this page. */
      }
    });
  }

  /* The sidebar is a drawer below 900px. Escape and the scrim close it, as a dialog would. */
  function setUpSidebar() {
    var toggle = document.querySelector('.menu-toggle');
    var sidebar = document.getElementById('sidebar');
    var scrim = document.querySelector('.scrim');
    if (!toggle || !sidebar) {
      return;
    }

    /*
     * Only classes change. The backdrop stays in the document and is hidden by opacity and
     * pointer-events rather than by the `hidden` attribute: `display: none` cannot be transitioned
     * away from in the same frame it is removed, and pairing it with a requestAnimationFrame is a
     * fade that silently does not happen whenever the frame callback does not run.
     */
    function setOpen(open) {
      sidebar.classList.toggle('is-open', open);
      toggle.setAttribute('aria-expanded', String(open));
      toggle.setAttribute('aria-label', open ? 'Close navigation' : 'Open navigation');
      if (scrim) {
        scrim.classList.toggle('is-open', open);
      }
    }

    toggle.addEventListener('click', function () {
      setOpen(sidebar.classList.contains('is-open') === false);
    });
    if (scrim) {
      scrim.addEventListener('click', function () {
        setOpen(false);
      });
    }
    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && sidebar.classList.contains('is-open')) {
        setOpen(false);
        toggle.focus();
      }
    });
    sidebar.addEventListener('click', function (event) {
      if (event.target.closest('a')) {
        setOpen(false);
      }
    });
  }

  /*
   * Fades sections in as they arrive, staggered a little so a screenful does not all move at once.
   *
   * Everything is visible to begin with when the observer is unavailable or motion is unwanted —
   * content that only appears once a script has run is content that is missing when it has not.
   */
  function setUpReveal() {
    var targets = document.querySelectorAll('[data-reveal]');
    if (!targets.length) {
      return;
    }
    if (reduced.matches || typeof IntersectionObserver === 'undefined') {
      targets.forEach(function (element) {
        element.classList.add('is-visible');
      });
      return;
    }
    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry, index) {
        if (!entry.isIntersecting) {
          return;
        }
        var element = entry.target;
        window.setTimeout(function () {
          element.classList.add('is-visible');
        }, index * 60);
        observer.unobserve(element);
      });
    }, { rootMargin: '0px 0px -8% 0px', threshold: 0.05 });

    targets.forEach(function (element) {
      observer.observe(element);
    });
  }

  /*
   * Marks the contents entry for the section being read.
   *
   * Driven by the headings' positions on every scroll rather than by an IntersectionObserver: the
   * observer fires when a heading crosses the viewport, which is not the same question as "which
   * section am I in" once a section is taller than the screen and its heading has scrolled away.
   */
  function setUpContents() {
    var links = Array.prototype.slice.call(document.querySelectorAll('.toc a'));
    if (!links.length) {
      return;
    }
    var headings = links
      .map(function (link) {
        return document.getElementById(decodeURIComponent(link.hash.slice(1)));
      })
      .filter(Boolean);
    if (!headings.length) {
      return;
    }

    var ticking = false;
    function update() {
      ticking = false;
      var line = window.scrollY + 120;
      var current = 0;
      headings.forEach(function (heading, index) {
        if (heading.offsetTop <= line) {
          current = index;
        }
      });
      if (window.innerHeight + window.scrollY >= document.body.offsetHeight - 8) {
        current = headings.length - 1;
      }
      links.forEach(function (link, index) {
        link.classList.toggle('is-current', index === current);
      });
    }

    window.addEventListener(
      'scroll',
      function () {
        if (!ticking) {
          ticking = true;
          requestAnimationFrame(update);
        }
      },
      { passive: true }
    );
    update();
  }

  /* Copies a snippet, and says so for a moment — a button with no feedback reads as a broken one. */
  function setUpCopy() {
    document.addEventListener('click', function (event) {
      var button = event.target.closest('.copy');
      if (!button) {
        return;
      }
      var block = button.closest('.code-block');
      var code = block && block.querySelector('code');
      if (!code) {
        return;
      }
      var label = button.querySelector('.copy-label') || button;
      write(code.innerText).then(
        function () {
          flash(button, label, 'Copied');
        },
        function () {
          flash(button, label, 'Press ⌘C');
        }
      );
    });
  }

  function write(text) {
    if (navigator.clipboard && window.isSecureContext) {
      return navigator.clipboard.writeText(text);
    }
    return Promise.reject(new Error('Clipboard unavailable'));
  }

  function flash(button, label, message) {
    var original = label.textContent;
    label.textContent = message;
    button.classList.add('is-copied');
    window.setTimeout(function () {
      label.textContent = original;
      button.classList.remove('is-copied');
    }, 1600);
  }

  ready(function () {
    setUpTheme();
    setUpSidebar();
    setUpReveal();
    setUpContents();
    setUpCopy();
  });
})();
