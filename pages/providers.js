/* Provider order: Argon, then six native Gemini keys. */
(() => {
  'use strict';
  function parseAnswer(text) {
    const raw = String(text || '').trim().replace(/^\x60\x60\x60(?:json)?\s*|\s*\x60\x60\x60$/g, '');
    let parsed;
    try { parsed = JSON.parse(raw); }
    catch (_) {
      const first = raw.indexOf('{'), last = raw.lastIndexOf('}');
      if (first < 0 || last <= first) throw new Error('invalid_json');
      parsed = JSON.parse(raw.slice(first, last + 1));
    }
    if (typeof parsed.reply !== 'string' || !parsed.reply.trim()) throw new Error('missing_reply');
    return parsed;
  }
  function create(config, { emit, record }) {
    const cooldowns = new Map();
    return async function generate(prompt) {
      const keys = config.keys || [];
      if (keys.length !== 6 || keys.some(key => !key)) throw new Error('Cấu hình sáu key chưa đầy đủ.');
      const failures = [];
      const argon = config.argon;
      if (argon && (cooldowns.get('argon') || 0) <= Date.now()) {
        emit('backroomProvider', argon.model);
        try {
          const base = argon.baseUrl.replace(/\/+$/, '');
          const endpoint = base.endsWith('/chat/completions') ? base : base + '/chat/completions';
          const response = await fetch(endpoint, {
            method: 'POST', signal: AbortSignal.timeout(60000),
            headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + argon.apiKey },
            body: JSON.stringify({
              model: argon.model,
              messages: [{ role: 'user', content: prompt }],
              temperature: 0.8, max_tokens: 4096, stream: false,
            }),
          });
          record({ kind: 'provider', alias: 'Argon', model: argon.model, status: response.status });
          if (!response.ok) throw new Error('http_error');
          const payload = await response.json();
          const content = payload.choices?.[0]?.message?.content;
          const text = Array.isArray(content) ? content.map(p => p.text || '').join('') : content;
          return parseAnswer(text);
        } catch (_) {
          failures.push('Argon chưa hoàn tất yêu cầu');
          cooldowns.set('argon', Date.now() + 60000);
        }
      }
      const models = config.models || ['gemini-3.6-flash', 'gemini-3.5-flash-lite', 'gemini-3.1-flash-lite', 'gemini-2.5-flash-lite'];
      for (let i = 0; i < keys.length; i++) {
        if ((cooldowns.get('key:' + i) || 0) > Date.now()) continue;
        for (const model of models) {
          const slot = i + ':' + model;
          if ((cooldowns.get(slot) || 0) > Date.now()) continue;
          emit('backroomProvider', 'Gemini K' + (i + 1) + ' · ' + model);
          try {
            const response = await fetch('https://generativelanguage.googleapis.com/v1beta/models/' + model + ':generateContent', {
              method: 'POST', signal: AbortSignal.timeout(60000),
              headers: { 'Content-Type': 'application/json', 'x-goog-api-key': keys[i] },
              body: JSON.stringify({
                contents: [{ role: 'user', parts: [{ text: prompt }] }],
                generationConfig: { responseMimeType: 'application/json', temperature: 0.8, maxOutputTokens: 4096 },
              }),
            });
            record({ kind: 'provider', alias: 'K' + (i + 1), model, status: response.status });
            if (!response.ok) {
              failures.push('K' + (i + 1) + ' HTTP ' + response.status);
              if (response.status === 401) { cooldowns.set('key:' + i, Date.now() + 300000); break; }
              if ([403, 404, 429].includes(response.status)) cooldowns.set(slot, Date.now() + (response.status === 429 ? 60000 : 300000));
              continue;
            }
            const payload = await response.json();
            const text = (payload.candidates?.[0]?.content?.parts || []).filter(p => !p.thought).map(p => p.text || '').join('');
            return parseAnswer(text);
          } catch (error) {
            failures.push('K' + (i + 1) + ': ' + (error.name === 'TimeoutError' ? 'hết thời gian chờ' : 'yêu cầu không hoàn tất'));
          }
        }
      }
      throw new Error('Các API hiện chưa xử lý được lượt: ' + [...new Set(failures)].slice(-7).join('; ') + '. Có thể thử lại.');
    };
  }
  globalThis.BackroomsProviders = { create };
})();
