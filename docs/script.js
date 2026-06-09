/* ClaudeMC Website – Shared JS */

/* ── Active nav link ───────────────────────────────────────────────── */
(function () {
  const page = location.pathname.split('/').pop() || 'index.html';
  document.querySelectorAll('.nav-links a').forEach(a => {
    const href = a.getAttribute('href').split('/').pop();
    if (href === page || (page === '' && href === 'index.html')) {
      a.classList.add('active');
    }
  });
})();

/* ── Module search + filter ────────────────────────────────────────── */
function initModuleSearch() {
  const search  = document.getElementById('modSearch');
  const filters = document.querySelectorAll('.filter-btn[data-cat]');
  const rows    = document.querySelectorAll('tbody tr[data-cat]');
  const count   = document.getElementById('modCount');

  if (!search) return;

  let activeCat = 'all';

  function apply() {
    const q = search.value.toLowerCase().trim();
    let visible = 0;
    rows.forEach(r => {
      const catMatch  = activeCat === 'all' || r.dataset.cat === activeCat;
      const textMatch = !q || r.textContent.toLowerCase().includes(q);
      const show = catMatch && textMatch;
      r.classList.toggle('hidden', !show);
      if (show) visible++;
    });
    if (count) count.textContent = visible + ' module' + (visible !== 1 ? 's' : '');
  }

  search.addEventListener('input', apply);

  filters.forEach(btn => {
    btn.addEventListener('click', () => {
      filters.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      activeCat = btn.dataset.cat;
      apply();
    });
  });

  apply();
}

/* ── Guide search ──────────────────────────────────────────────────── */
function initGuideSearch() {
  const search  = document.getElementById('guideSearch');
  const items   = document.querySelectorAll('.guide-item');
  if (!search) return;
  search.addEventListener('input', () => {
    const q = search.value.toLowerCase();
    items.forEach(it => {
      it.classList.toggle('hidden', q && !it.textContent.toLowerCase().includes(q));
    });
  });
}

/* ── Troubleshooting search ────────────────────────────────────────── */
function initTSSearch() {
  const search = document.getElementById('tsSearch');
  const items  = document.querySelectorAll('details.ts-item');
  if (!search) return;
  search.addEventListener('input', () => {
    const q = search.value.toLowerCase();
    items.forEach(it => {
      const match = !q || it.textContent.toLowerCase().includes(q);
      it.classList.toggle('hidden', !match);
      if (match && q) it.open = true;
    });
  });
}

/* ── GitHub latest release (auto-updates version + download links) ─── */
async function fetchLatestRelease() {
  // Elements updated by this function use these selectors:
  //   [data-release="version"]  → tag_name (e.g. "v1.20.10")
  //   [data-release="download"] → href of the first .jar asset, or /releases/latest fallback
  //   [data-release="date"]     → human-readable publish date
  const REPO = 'ClaudeMC-Client/claudemc.v1';
  try {
    const res = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`, {
      headers: { Accept: 'application/vnd.github+json' }
    });
    if (!res.ok) return;
    const data = await res.json();

    const tag  = data.tag_name || '';
    const date = data.published_at
      ? new Date(data.published_at).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
      : '';
    const jar  = (data.assets || []).find(a => a.name.endsWith('.jar'));
    const dlUrl = jar ? jar.browser_download_url
                      : `https://github.com/${REPO}/releases/latest`;

    document.querySelectorAll('[data-release="version"]').forEach(el => {
      el.textContent = tag;
    });
    document.querySelectorAll('[data-release="download"]').forEach(el => {
      el.href = dlUrl;
    });
    document.querySelectorAll('[data-release="date"]').forEach(el => {
      el.textContent = date ? `Released ${date}` : '';
    });
  } catch (_) {
    // Network unavailable or rate-limited — static fallback text already in HTML
  }
}

/* ── Table row stagger (modules page) ─────────────────────────────── */
function initTableRowAnims() {
  const rows = document.querySelectorAll('tbody tr[data-cat]');
  if (!rows.length || !('IntersectionObserver' in window)) return;

  const io = new IntersectionObserver((entries) => {
    let batch = 0;
    entries.forEach(e => {
      if (!e.isIntersecting) return;
      const tr = e.target;
      tr.style.animationDelay = `${batch * 28}ms`;
      tr.classList.add('row-anim');
      io.unobserve(tr);
      batch++;
    });
  }, { threshold: 0.05, rootMargin: '0px 0px -30px 0px' });

  rows.forEach(r => io.observe(r));
}

/* ── Scroll-entrance IntersectionObserver ──────────────────────────── */
function initScrollAnims() {
  const els = document.querySelectorAll('[data-anim]');
  if (!els.length || !('IntersectionObserver' in window)) {
    els.forEach(el => el.classList.add('is-visible'));
    return;
  }
  const io = new IntersectionObserver((entries) => {
    entries.forEach((e, i) => {
      if (!e.isIntersecting) return;
      const delay = parseFloat(e.target.dataset.animDelay || 0);
      setTimeout(() => e.target.classList.add('is-visible'), delay * 1000);
      io.unobserve(e.target);
    });
  }, { threshold: 0.12 });
  els.forEach(el => io.observe(el));
}

document.addEventListener('DOMContentLoaded', () => {
  initModuleSearch();
  initGuideSearch();
  initTSSearch();
  fetchLatestRelease();
  initScrollAnims();
  initTableRowAnims();
});
