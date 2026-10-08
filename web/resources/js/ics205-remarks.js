(() => {
  const rows = document.getElementById('channel-rows');
  function fitRemarks() {
    rows.querySelectorAll('textarea[data-field=remarks]').forEach(textarea => {
      textarea.rows = 1;
      textarea.style.height = 'auto';
      const borderHeight = textarea.offsetHeight - textarea.clientHeight;
      textarea.style.height = (textarea.scrollHeight + borderHeight) + 'px';
    });
  }
  rows.addEventListener('input', event => {
    if (event.target.matches('textarea[data-field=remarks]')) fitRemarks();
  });
  new MutationObserver(fitRemarks).observe(rows, { childList: true });
  new ResizeObserver(fitRemarks).observe(rows.closest('table').parentElement);
  fitRemarks();
})();
