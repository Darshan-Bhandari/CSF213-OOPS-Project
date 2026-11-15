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
