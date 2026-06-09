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

document.addEventListener('DOMContentLoaded', () => {
  initModuleSearch();
  initGuideSearch();
  initTSSearch();
});
