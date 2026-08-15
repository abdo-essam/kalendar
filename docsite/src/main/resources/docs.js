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

  /*
   * Search, entirely in the browser.
   *
   * The index is a JSON file generated with the site and fetched from the same origin the page came
   * from — there is no service to call, nothing to keep running, and no query leaves the reader's
   * machine. It is fetched the first time the box is opened rather than on load: most readers never
   * search, and making every page carry the index so that some of them can is the trade that turns a
   * fast documentation site into a slow one.
   */
  var RESULT_LIMIT = 24;
  var SCORE_HEADING = 8;
  var SCORE_HEADING_EXACT = 6;
  var SCORE_TITLE = 3;
  var SCORE_WORDS = 2;

  function setUpSearch() {
    var opener = document.querySelector('.search-open');
    var overlay = document.getElementById('search');
    if (!opener || !overlay) {
      return;
    }
    var input = overlay.querySelector('.search-input');
    var list = overlay.querySelector('.search-results');
    var status = overlay.querySelector('.search-status');
    var closer = overlay.querySelector('.search-close');
    var scrim = overlay.querySelector('.search-scrim');
    var root = opener.getAttribute('data-search-root') || '';
    var source = opener.getAttribute('data-search-index');

    var index = null;
    var pending = false;
    var shown = [];
    var active = -1;
    var restore = null;

    function say(message) {
      status.textContent = message;
    }

    function open() {
      if (!overlay.hidden) {
        return;
      }
      restore = document.activeElement;
      overlay.hidden = false;
      document.documentElement.classList.add('is-searching');
      input.focus();
      input.select();
      load();
    }

    function close() {
      if (overlay.hidden) {
        return;
      }
      overlay.hidden = true;
      document.documentElement.classList.remove('is-searching');
      if (restore && restore.focus) {
        restore.focus();
      }
    }

    /*
     * One fetch, once, and a readable failure if it does not arrive. A search box that silently does
     * nothing is indistinguishable from a search box with no matches, so the difference is spelled
     * out rather than left for the reader to guess at.
     */
    function load() {
      if (index || pending) {
        return;
      }
      pending = true;
      say('Loading the index…');
      fetch(source, { credentials: 'same-origin' })
        .then(function (response) {
          if (!response.ok) {
            throw new Error(String(response.status));
          }
          return response.json();
        })
        .then(function (data) {
          index = prepare(data);
          pending = false;
          run();
        })
        .catch(function () {
          pending = false;
          say('Search is unavailable — the index could not be loaded.');
        });
    }

    /*
     * Lowercased once here rather than on every keystroke of every query.
     *
     * Every searchable string is given a leading space, because `has` finds a word by the space in
     * front of it and the first word of a heading has none. Without this, "drag" misses every one of
     * the eight sections titled "Drag to…" — they still match on their prose, so nothing looks
     * broken; the headings that are exactly what the reader asked for just quietly rank last.
     */
    function prepare(data) {
      var pages = data.p.map(function (page) {
        return { url: page[0], title: page[1], lower: ' ' + page[1].toLowerCase() };
      });
      var sections = data.s.map(function (item) {
        return {
          page: pages[item[0]],
          heading: item[1],
          lower: ' ' + item[1].toLowerCase(),
          anchor: item[2],
          snippet: item[3],
          words: ' ' + item[4]
        };
      });
      return { sections: sections, stop: ' ' + data.x + ' ' };
    }

    /* True when some word in `blob` starts with `term`; the leading space is the word boundary. */
    function has(blob, term) {
      return blob.indexOf(' ' + term) !== -1;
    }

    function terms(query) {
      return query
        .toLowerCase()
        .split(/[^a-z0-9_]+/)
        .filter(function (term) {
          return term && index.stop.indexOf(' ' + term + ' ') === -1;
        });
    }

    /*
     * Every term has to be somewhere in the section, and where it is decides the score: a term in the
     * heading is what the section is about, a term in its prose is only something it mentions.
     */
    function score(section, list) {
      var total = 0;
      for (var i = 0; i < list.length; i++) {
        var term = list[i];
        var points = 0;
        if (has(section.lower, term)) {
          points += SCORE_HEADING;
        }
        if (section.lower === ' ' + term) {
          points += SCORE_HEADING_EXACT;
        }
        if (has(section.page.lower, term)) {
          points += SCORE_TITLE;
        }
        if (has(section.words, term)) {
          points += SCORE_WORDS;
        }
        if (!points) {
          return 0;
        }
        total += points;
      }
      return total;
    }

    function run() {
      var query = input.value.trim();
      list.textContent = '';
      shown = [];
      active = -1;
      if (!index) {
        if (!pending && query) {
          load();
        }
        return;
      }
      if (!query) {
        say('Type to search. ' + index.sections.length + ' sections indexed.');
        return;
      }
      var list_ = terms(query);
      if (!list_.length) {
        say('Nothing to search for yet.');
        return;
      }
      var hits = [];
      index.sections.forEach(function (section, position) {
        var points = score(section, list_);
        if (points) {
          hits.push({ section: section, points: points, position: position });
        }
      });
      hits.sort(function (a, b) {
        return b.points - a.points || a.position - b.position;
      });
      shown = hits.slice(0, RESULT_LIMIT);
      say(
        hits.length
          ? hits.length + (hits.length === 1 ? ' result' : ' results')
          : 'No results for “' + query + '”.'
      );
      shown.forEach(function (hit, position) {
        list.appendChild(result(hit.section, list_, position));
      });
      if (shown.length) {
        select(0);
      }
    }

    function result(section, list_, position) {
      var item = document.createElement('li');
      item.className = 'search-result';
      item.setAttribute('role', 'option');
      item.id = 'search-result-' + position;

      var link = document.createElement('a');
      link.href = root + section.page.url + (section.anchor ? '#' + section.anchor : '');

      var crumb = document.createElement('span');
      crumb.className = 'search-crumb';
      crumb.textContent = section.page.title;

      var heading = document.createElement('span');
      heading.className = 'search-heading';
      heading.appendChild(mark(section.heading, list_));

      link.appendChild(crumb);
      link.appendChild(heading);
      if (section.snippet) {
        var snippet = document.createElement('span');
        snippet.className = 'search-snippet';
        snippet.appendChild(mark(section.snippet, list_));
        link.appendChild(snippet);
      }
      item.appendChild(link);
      return item;
    }

    /*
     * Highlights the matched words. Built out of text nodes rather than a string of HTML: the index
     * is generated from the docs, but a search result is the one place on this site where the page
     * assembles content around something a reader typed.
     */
    function mark(text, list_) {
      var fragment = document.createDocumentFragment();
      var pattern = new RegExp('\\b(' + list_.map(escapeRegExp).join('|') + ')', 'gi');
      var last = 0;
      var found;
      while ((found = pattern.exec(text)) !== null) {
        if (found.index > last) {
          fragment.appendChild(document.createTextNode(text.slice(last, found.index)));
        }
        var hit = document.createElement('mark');
        hit.textContent = found[0];
        fragment.appendChild(hit);
        last = found.index + found[0].length;
        if (!found[0].length) {
          pattern.lastIndex++;
        }
      }
      fragment.appendChild(document.createTextNode(text.slice(last)));
      return fragment;
    }

    function escapeRegExp(value) {
      return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    }

    function select(next) {
      var items = list.children;
      if (!items.length) {
        return;
      }
      active = (next + items.length) % items.length;
      for (var i = 0; i < items.length; i++) {
        var current = i === active;
        items[i].classList.toggle('is-active', current);
        items[i].setAttribute('aria-selected', String(current));
        if (current) {
          input.setAttribute('aria-activedescendant', items[i].id);
          items[i].scrollIntoView({ block: 'nearest' });
        }
      }
    }

    opener.addEventListener('click', open);
    if (closer) {
      closer.addEventListener('click', close);
    }
    if (scrim) {
      scrim.addEventListener('click', close);
    }
    input.addEventListener('input', run);

    input.addEventListener('keydown', function (event) {
      if (event.key === 'ArrowDown') {
        event.preventDefault();
        select(active + 1);
      } else if (event.key === 'ArrowUp') {
        event.preventDefault();
        select(active - 1);
      } else if (event.key === 'Enter') {
        var chosen = list.children[active];
        var link = chosen && chosen.querySelector('a');
        if (link) {
          event.preventDefault();
          link.click();
        }
      }
    });

    /*
     * `/` and ⌘K, the two shortcuts a reader will already have in their fingers from every other
     * documentation site. `/` is skipped while something else has focus and would receive it — a
     * slash typed into a text field is a slash, not a shortcut.
     */
    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && !overlay.hidden) {
        close();
        return;
      }
      if (event.key === 'k' && (event.metaKey || event.ctrlKey)) {
        event.preventDefault();
        open();
        return;
      }
      if (event.key === '/' && !event.metaKey && !event.ctrlKey && !event.altKey) {
        var focused = document.activeElement;
        var typing =
          focused &&
          (focused.tagName === 'INPUT' || focused.tagName === 'TEXTAREA' || focused.isContentEditable);
        if (!typing) {
          event.preventDefault();
          open();
        }
      }
    });
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
    setUpSearch();
    setUpSidebar();
    setUpReveal();
    setUpContents();
    setUpCopy();
  });
})();
