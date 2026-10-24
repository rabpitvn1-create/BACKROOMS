/* Local-only credential editor. The native bridge never returns actual key values. */
(function () {
  'use strict';
  const form = document.getElementById('providerCredentialForm');
  const toggle = document.getElementById('providerCredentialToggle');
  const clear = document.getElementById('providerCredentialClear');
  const status = document.getElementById('providerCredentialStatus');
  if (!form || !toggle || !clear || !status) return;

  function bridge() {
    return window.Android && typeof Android.providerCredentialsStatus === 'function'
      && typeof Android.saveProviderCredentials === 'function';
  }
  function describe(result) {
    if (!result || result.error) return 'Không đọc được cấu hình AI trên thiết bị.';
    return 'Gehihi: ' + (result.gehihi ? 'đã cấu hình' : 'chưa cấu hình')
      + ' · Gemini: ' + (Number(result.geminiKeys) || 0) + '/5 khóa.';
  }
  function refresh() {
    try {
      status.textContent = bridge()
        ? describe(JSON.parse(Android.providerCredentialsStatus()))
        : 'Không có kết nối cấu hình Android.';
    } catch (_) {
      status.textContent = 'Không đọc được cấu hình AI trên thiết bị.';
    }
  }
  toggle.addEventListener('click', () => {
    form.hidden = !form.hidden;
    toggle.setAttribute('aria-expanded', String(!form.hidden));
    if (form.hidden) form.reset();
  });
  form.addEventListener('submit', event => {
    event.preventDefault();
    if (!bridge()) { refresh(); return; }
    const values = {};
    form.querySelectorAll('[name]').forEach(input => {
      if (input.value.trim()) values[input.name] = input.value.trim();
    });
    try {
      const result = JSON.parse(Android.saveProviderCredentials(JSON.stringify(values)));
      status.textContent = result.error ? 'Không lưu được cấu hình AI.' : 'Đã lưu trên thiết bị. ' + describe(result);
      if (!result.error) form.reset();
    } catch (_) {
      status.textContent = 'Không lưu được cấu hình AI.';
    }
  });
  clear.addEventListener('click', () => {
    if (!bridge() || !window.confirm('Xóa toàn bộ khóa AI đã lưu trên thiết bị?')) return;
    try {
      const result = JSON.parse(Android.saveProviderCredentials('{"clear":true}'));
      status.textContent = result.error ? 'Không xóa được cấu hình AI.' : 'Đã xóa cấu hình AI.';
      if (!result.error) form.reset();
    } catch (_) {
      status.textContent = 'Không xóa được cấu hình AI.';
    }
  });
  refresh();
})();
