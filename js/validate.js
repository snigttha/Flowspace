/* FlowSpace form validation. Usage: <input name="x" data-v="required|email"> + <small class="err" data-for="x"></small> */
const FSV = (() => {
  const today = () => new Date().toLocaleDateString('en-CA'); // local YYYY-MM-DD
  const R = {
    required: v => v.trim() ? '' : 'This field is required.',
    email: v => !v || /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(v.trim()) ? '' : 'Enter a valid email, like name@example.com.',
    min: (v, n) => !v || v.trim().length >= +n ? '' : `Use at least ${n} characters.`,
    max: (v, n) => v.trim().length <= +n ? '' : `Use ${n} characters or fewer.`,
    strong: v => !v || (/[A-Za-z]/.test(v) && /\d/.test(v)) ? '' : 'Include at least one letter and one number.',
    range: (v, a, b) => v === '' || (v.trim() !== '' && +v >= +a && +v <= +b) ? '' : `Enter a number from ${a} to ${b}.`,
    date: v => !v || !isNaN(Date.parse(v)) ? '' : 'Enter a valid date.',
    notfuture: v => !v || v <= today() ? '' : 'The date cannot be in the future.',
    match: (v, id, f) => v === f.elements.namedItem(id).value ? '' : 'The two values do not match.'
  };
  const msgFor = (el, form) => {
    for (const r of (el.dataset.v || '').split('|')) {
      const [k, ...a] = r.split(':');
      const m = R[k] ? R[k](el.value, ...a, form) : '';
      if (m) return m;
    }
    return '';
  };
  const show = (form, name, msg) => {
    const el = form.elements.namedItem(name), s = form.querySelector(`.err[data-for="${name}"]`);
    if (s) s.textContent = msg;
    if (el) { el.classList.toggle('bad', !!msg); el.setAttribute('aria-invalid', String(!!msg)); }
  };
  return {
    today, fail: show,
    check(form) { // true when every [data-v] field passes; focuses the first bad one
      let first = null;
      form.querySelectorAll('[data-v]').forEach(el => { const m = msgFor(el, form); show(form, el.name, m); if (m && !first) first = el; });
      if (first) first.focus();
      return !first;
    },
    clear(form) { form.querySelectorAll('[data-v]').forEach(el => show(form, el.name, '')); },
    live(form) { form.addEventListener('focusout', e => { if (e.target.dataset && e.target.dataset.v) show(form, e.target.name, msgFor(e.target, form)); }); }
  };
})();
