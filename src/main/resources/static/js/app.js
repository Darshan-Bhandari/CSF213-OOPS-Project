document.querySelectorAll('[data-page]').forEach(button => button.addEventListener('click', () => {
  const form = document.querySelector('#incident-filters');
  if (form) { form.querySelector('[name="page"]').value = button.dataset.page; form.requestSubmit(); }
}));
document.querySelectorAll('[data-reset-page]').forEach(button => button.addEventListener('click', () => {
  const page = button.form.querySelector('[name="page"]'); if (page) page.value = '0';
}));
document.querySelectorAll('[data-busy]').forEach(button => button.form.addEventListener('submit', event => {
  if (event.submitter === button) { button.textContent = 'Generating…'; button.setAttribute('aria-busy', 'true'); }
}));

const navToggle = document.querySelector('[data-nav-toggle]');
navToggle?.addEventListener('click', () => {
  const mobile = window.matchMedia('(max-width: 900px)').matches;
  if (mobile) document.body.classList.toggle('nav-open');
  else document.body.classList.toggle('nav-collapsed');
  navToggle.setAttribute('aria-expanded', String(mobile ? document.body.classList.contains('nav-open') : !document.body.classList.contains('nav-collapsed')));
});
document.addEventListener('keydown', event => {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    const input = document.querySelector('[data-global-search]');
    if (input && input.getClientRects().length) { event.preventDefault(); input.focus(); }
  }
  if (event.key === 'Escape') {
    document.body.classList.remove('nav-open');
    if (window.matchMedia('(max-width: 900px)').matches) navToggle?.setAttribute('aria-expanded', 'false');
  }
});
document.querySelectorAll('[data-password-toggle]').forEach(button => {
  button.addEventListener('click', () => {
    const input = document.getElementById(button.getAttribute('aria-controls'));
    const visible = input.type === 'password';
    input.type = visible ? 'text' : 'password';
    button.setAttribute('aria-pressed', String(visible));
    button.setAttribute('aria-label', visible ? 'Hide password' : 'Show password');
  });
});

document.querySelectorAll('[data-brand-image]').forEach(image => {
  const fallback = () => { image.hidden = true; image.parentElement.classList.add('brand-image-missing'); };
  image.addEventListener('error', fallback);
  if (image.complete && !image.naturalWidth) fallback();
});
const navigationMedia = window.matchMedia('(max-width: 900px)');
const syncNavigationState = () => {
  const expanded = navigationMedia.matches ? document.body.classList.contains('nav-open') : !document.body.classList.contains('nav-collapsed');
  navToggle?.setAttribute('aria-expanded', String(expanded));
};
navigationMedia.addEventListener('change', () => {
  document.body.classList.remove('nav-collapsed', 'nav-open');
  syncNavigationState();
});
syncNavigationState();

const accountDropdown = document.querySelector('[data-account-dropdown]');
document.addEventListener('click', event => {
  if (accountDropdown?.open && !accountDropdown.contains(event.target)) accountDropdown.open = false;
});
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && accountDropdown?.open) {
    accountDropdown.open = false;
    accountDropdown.querySelector('summary').focus();
  }
});
