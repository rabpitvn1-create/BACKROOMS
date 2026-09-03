(function (root) {
  'use strict';
  function escapeText(value) {
    return String(value).replace(/[&<>"']/g, function (ch) {
      return {'&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;'}[ch];
    });
  }
  // Only this log entry's resolved evidence is eligible; never inspect hidden
  // Level data, infer clues from keywords, or interpret model-generated HTML.
  root.renderEvidenceText = function (text, entry) {
    text = String(text || '');
    var evidence = entry && entry.role === 'gm' && Array.isArray(entry.evidenceTexts)
      ? entry.evidenceTexts : [];
    var ranges = [];
    evidence.forEach(function (value) {
      if (typeof value !== 'string' || !value.trim()) return;
      var needle = value.trim(), offset = 0, index;
      while ((index = text.indexOf(needle, offset)) !== -1) {
        ranges.push({start:index, end:index + needle.length});
        offset = index + needle.length;
      }
    });
    ranges.sort(function (a, b) { return a.start - b.start || b.end - a.end; });
    var result = '', cursor = 0;
    ranges.forEach(function (range) {
      if (range.start < cursor) return;
      result += escapeText(text.slice(cursor, range.start));
      result += '<strong class="gm-evidence">' + escapeText(text.slice(range.start, range.end)) + '</strong>';
      cursor = range.end;
    });
    return result + escapeText(text.slice(cursor));
  };
})(window);
