document.addEventListener('click', function (event) {
  var button = event.target.closest('[data-password-toggle]');
  if (!button) return;

  var input = document.getElementById(button.getAttribute('aria-controls'));
  if (!input) return;

  var reveal = input.type === 'password';
  input.type = reveal ? 'text' : 'password';
  button.setAttribute('aria-label', reveal ? 'Hide password' : 'Show password');
  button.setAttribute('aria-pressed', reveal ? 'true' : 'false');
});
