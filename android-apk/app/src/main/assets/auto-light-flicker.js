(function (root, factory) {
  var api = factory();
  if (typeof module === 'object' && module.exports) {
    module.exports = api;
  } else {
    root.AutoLightFlickerEngine = api;
    if (root.document) api.install(root.document, root);
  }
})(typeof window !== 'undefined' ? window : globalThis, function () {
  'use strict';

  var DETECT_WIDTH = 128;
  var MAX_COMPONENTS = 12;
  var cache = new Map();

  function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
  }

  function luminance(r, g, b) {
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  }

  function percentile(values, ratio) {
    if (!values.length) return 0;
    var copy = Array.prototype.slice.call(values).sort(function (a, b) { return a - b; });
    return copy[clamp(Math.floor((copy.length - 1) * ratio), 0, copy.length - 1)];
  }

  function neighborhoodAverage(luma, width, height, x, y) {
    var sum = 0;
    var count = 0;
    for (var dy = -2; dy <= 2; dy++) {
      for (var dx = -2; dx <= 2; dx++) {
        if (dx === 0 && dy === 0) continue;
        var nx = x + dx;
        var ny = y + dy;
        if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
        sum += luma[ny * width + nx];
        count++;
      }
    }
    return count ? sum / count : luma[y * width + x];
  }

  function detectLightComponents(rgba, width, height) {
    if (!rgba || width <= 0 || height <= 0 || rgba.length < width * height * 4) return [];

    var count = width * height;
    var luma = new Float32Array(count);
    var sampled = [];
    for (var i = 0; i < count; i++) {
      var offset = i * 4;
      var y = luminance(rgba[offset], rgba[offset + 1], rgba[offset + 2]);
      luma[i] = y;
      sampled.push(y);
    }

    var brightCutoff = Math.max(165, percentile(sampled, 0.90) - 5);
    var mask = new Uint8Array(count);
    var contrast = new Float32Array(count);

    for (var py = 0; py < height; py++) {
      for (var px = 0; px < width; px++) {
        var index = py * width + px;
        var base = index * 4;
        var r = rgba[base];
        var g = rgba[base + 1];
        var b = rgba[base + 2];
        var y = luma[index];
        if (y < brightCutoff) continue;

        var localContrast = y - neighborhoodAverage(luma, width, height, px, py);
        contrast[index] = localContrast;
        var maxChannel = Math.max(r, g, b);
        var minChannel = Math.min(r, g, b);
        var chroma = maxChannel - minChannel;
        var neutralLight = chroma <= 125;
        var warmLight = r >= 190 && g >= 155 && b >= 35 && r >= b;
        var coolLight = b >= 165 && g >= 155 && r >= 135;
        var lightLikeColor = neutralLight || warmLight || coolLight;
        var contrastNeeded = y >= 235 ? 4 : 9;

        if (lightLikeColor && localContrast >= contrastNeeded) mask[index] = 1;
      }
    }

    var seen = new Uint8Array(count);
    var components = [];
    var neighbors = [
      [-1, -1], [0, -1], [1, -1],
      [-1, 0],            [1, 0],
      [-1, 1],  [0, 1],  [1, 1]
    ];

    for (var start = 0; start < count; start++) {
      if (!mask[start] || seen[start]) continue;
      var queue = [start];
      seen[start] = 1;
      var q = 0;
      var pixels = [];
      var minX = width;
      var maxX = 0;
      var minY = height;
      var maxY = 0;
      var sumContrast = 0;
      var sumLuma = 0;
      var sumR = 0;
      var sumG = 0;
      var sumB = 0;

      while (q < queue.length) {
        var current = queue[q++];
        pixels.push(current);
        var x = current % width;
        var yPos = Math.floor(current / width);
        minX = Math.min(minX, x);
        maxX = Math.max(maxX, x);
        minY = Math.min(minY, yPos);
        maxY = Math.max(maxY, yPos);
        sumContrast += Math.max(0, contrast[current]);
        sumLuma += luma[current];
        var rgbaOffset = current * 4;
        sumR += rgba[rgbaOffset];
        sumG += rgba[rgbaOffset + 1];
        sumB += rgba[rgbaOffset + 2];

        for (var n = 0; n < neighbors.length; n++) {
          var nx = x + neighbors[n][0];
          var ny = yPos + neighbors[n][1];
          if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
          var next = ny * width + nx;
          if (!mask[next] || seen[next]) continue;
          seen[next] = 1;
          queue.push(next);
        }
      }

      var area = pixels.length;
      var boxWidth = maxX - minX + 1;
      var boxHeight = maxY - minY + 1;
      var boxArea = boxWidth * boxHeight;
      var fill = area / Math.max(1, boxArea);
      var areaRatio = area / count;
      var widthRatio = boxWidth / width;
      var heightRatio = boxHeight / height;

      if (area < 1 || areaRatio > 0.07) continue;
      if (boxArea / count > 0.16) continue;
      if (widthRatio > 0.88 && heightRatio > 0.16) continue;
      if (heightRatio > 0.62) continue;
      if (fill < 0.08) continue;

      var avgContrast = sumContrast / area;
      var avgLuma = sumLuma / area;
      if (avgContrast < 5 && avgLuma < 238) continue;

      components.push({
        minX: minX,
        maxX: maxX,
        minY: minY,
        maxY: maxY,
        width: boxWidth,
        height: boxHeight,
        area: area,
        fill: fill,
        avgContrast: avgContrast,
        avgLuma: avgLuma,
        r: Math.round(sumR / area),
        g: Math.round(sumG / area),
        b: Math.round(sumB / area),
        score: area * (avgContrast + 6) + Math.max(0, avgLuma - brightCutoff) * 0.8
      });
    }

    components.sort(function (a, b) { return b.score - a.score; });
    return components.slice(0, MAX_COMPONENTS);
  }

  function coverCrop(imageWidth, imageHeight, boxWidth, boxHeight) {
    if (!(imageWidth > 0 && imageHeight > 0 && boxWidth > 0 && boxHeight > 0)) {
      return { sx: 0, sy: 0, sw: imageWidth || 1, sh: imageHeight || 1 };
    }
    var imageRatio = imageWidth / imageHeight;
    var boxRatio = boxWidth / boxHeight;
    if (imageRatio > boxRatio) {
      var sw = imageHeight * boxRatio;
      return { sx: (imageWidth - sw) / 2, sy: 0, sw: sw, sh: imageHeight };
    }
    var sh = imageWidth / boxRatio;
    return { sx: 0, sy: (imageHeight - sh) / 2, sw: imageWidth, sh: sh };
  }

  function drawGlow(canvas, components, detectWidth, detectHeight) {
    var ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.globalCompositeOperation = 'source-over';

    components.forEach(function (component) {
      var cx = component.minX + component.width / 2;
      var cy = component.minY + component.height / 2;
      var rx = Math.max(2.5, component.width * 1.65);
      var ry = Math.max(2.5, component.height * 1.9);
      var radius = Math.max(rx, ry);
      var r = clamp(Math.round(component.r * 1.04 + 10), 0, 255);
      var g = clamp(Math.round(component.g * 1.04 + 10), 0, 255);
      var b = clamp(Math.round(component.b * 1.04 + 10), 0, 255);
      var gradient = ctx.createRadialGradient(cx, cy, 0, cx, cy, radius);
      gradient.addColorStop(0, 'rgba(' + r + ',' + g + ',' + b + ',0.92)');
      gradient.addColorStop(0.24, 'rgba(' + r + ',' + g + ',' + b + ',0.54)');
      gradient.addColorStop(1, 'rgba(' + r + ',' + g + ',' + b + ',0)');
      ctx.save();
      ctx.translate(cx, cy);
      ctx.scale(rx / radius, ry / radius);
      ctx.fillStyle = gradient;
      ctx.beginPath();
      ctx.arc(0, 0, radius, 0, Math.PI * 2);
      ctx.fill();
      ctx.restore();
    });
  }

  function drawDimmer(canvas, components) {
    var ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.globalCompositeOperation = 'source-over';

    components.forEach(function (component) {
      var cx = component.minX + component.width / 2;
      var cy = component.minY + component.height / 2;
      var rx = Math.max(1.6, component.width * 0.78);
      var ry = Math.max(1.6, component.height * 0.9);
      var radius = Math.max(rx, ry);
      var gradient = ctx.createRadialGradient(cx, cy, 0, cx, cy, radius);
      gradient.addColorStop(0, 'rgba(0,0,0,0.92)');
      gradient.addColorStop(0.58, 'rgba(0,0,0,0.62)');
      gradient.addColorStop(1, 'rgba(0,0,0,0)');
      ctx.save();
      ctx.translate(cx, cy);
      ctx.scale(rx / radius, ry / radius);
      ctx.fillStyle = gradient;
      ctx.beginPath();
      ctx.arc(0, 0, radius, 0, Math.PI * 2);
      ctx.fill();
      ctx.restore();
    });
  }

  function sourceKey(img, width, height) {
    return String(img.currentSrc || img.src || '') + '|' + width + 'x' + height;
  }

  function isPackagedAssetSource(src) {
    return /^file:\/\/\/android_asset\/level_snapshots\//.test(String(src || ''));
  }

  function decodeBase64Rgba(encoded, win) {
    if (!encoded) return new Uint8ClampedArray(0);
    var binary = '';
    if (win && typeof win.atob === 'function') binary = win.atob(encoded);
    else if (typeof Buffer !== 'undefined') binary = Buffer.from(encoded, 'base64').toString('binary');
    else throw new Error('No base64 decoder available');
    var out = new Uint8ClampedArray(binary.length);
    for (var i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i) & 255;
    return out;
  }

  function analyzePackagedImage(img, box, win) {
    if (!win.Android || typeof win.Android.sampleAutoLightPixels !== 'function') {
      throw new Error('Native packaged-light sampler unavailable');
    }
    var cssWidth = Math.max(1, Math.round(box.clientWidth || img.clientWidth || 320));
    var cssHeight = Math.max(1, Math.round(box.clientHeight || img.clientHeight || 180));
    var raw = win.Android.sampleAutoLightPixels(String(img.currentSrc || img.src || ''), cssWidth, cssHeight);
    if (!raw) throw new Error('Native packaged-light sampler returned no data');
    var sample = JSON.parse(raw);
    var width = Number(sample.width || 0);
    var height = Number(sample.height || 0);
    var rgba = decodeBase64Rgba(String(sample.rgba || ''), win);
    if (width <= 0 || height <= 0 || rgba.length < width * height * 4) {
      throw new Error('Native packaged-light sampler returned invalid data');
    }
    return detectLightComponents(rgba, width, height);
  }

  function analyzeImage(img, box, win) {
    var cssWidth = Math.max(1, Math.round(box.clientWidth || img.clientWidth || 320));
    var cssHeight = Math.max(1, Math.round(box.clientHeight || img.clientHeight || 180));
    var detectWidth = DETECT_WIDTH;
    var detectHeight = clamp(Math.round(DETECT_WIDTH * cssHeight / cssWidth), 48, 96);
    var key = sourceKey(img, detectWidth, detectHeight);
    if (cache.has(key)) return cache.get(key);

    var src = String(img.currentSrc || img.src || '');
    if (isPackagedAssetSource(src)) {
      var packaged = analyzePackagedImage(img, box, win);
      cache.set(key, packaged);
      return packaged;
    }

    var detector = win.document.createElement('canvas');
    detector.width = detectWidth;
    detector.height = detectHeight;
    var ctx = detector.getContext('2d', { willReadFrequently: true });
    if (!ctx) return [];
    var crop = coverCrop(img.naturalWidth, img.naturalHeight, cssWidth, cssHeight);
    ctx.drawImage(img, crop.sx, crop.sy, crop.sw, crop.sh, 0, 0, detectWidth, detectHeight);
    var pixels = ctx.getImageData(0, 0, detectWidth, detectHeight).data;
    var result = detectLightComponents(pixels, detectWidth, detectHeight);
    cache.set(key, result);
    return result;
  }

  function renderForBackground(img, box, win) {
    if (!img || !box || !win || !win.document) return;
    var src = String(img.currentSrc || img.src || '');
    if (!src) return;
    var token = src + '|' + Date.now();
    box.__autoLightToken = token;

    try {
      var components = analyzeImage(img, box, win);
      if (box.__autoLightToken !== token || !img.isConnected) return;
      var oldLayers = box.querySelectorAll('.snapshot-auto-light-layer');
      for (var oldIndex = 0; oldIndex < oldLayers.length; oldIndex++) oldLayers[oldIndex].remove();
      if (!components.length) {
        box.setAttribute('data-auto-light', 'none');
        return;
      }

      var detectHeight = clamp(Math.round(DETECT_WIDTH * Math.max(1, box.clientHeight) / Math.max(1, box.clientWidth)), 48, 96);
      var glow = win.document.createElement('canvas');
      glow.className = 'snapshot-auto-light-layer snapshot-auto-light-glow';
      glow.setAttribute('aria-hidden', 'true');
      glow.width = DETECT_WIDTH;
      glow.height = detectHeight;
      drawGlow(glow, components, glow.width, glow.height);

      var dim = win.document.createElement('canvas');
      dim.className = 'snapshot-auto-light-layer snapshot-auto-light-dim';
      dim.setAttribute('aria-hidden', 'true');
      dim.width = DETECT_WIDTH;
      dim.height = detectHeight;
      drawDimmer(dim, components);

      var hash = 0;
      for (var i = 0; i < src.length; i++) hash = (hash * 33 + src.charCodeAt(i)) >>> 0;
      var period = (4.2 + (hash % 2800) / 1000).toFixed(2) + 's';
      var delay = '-' + ((hash >>> 3) % 2400) + 'ms';
      glow.style.setProperty('--auto-light-period', period);
      glow.style.setProperty('--auto-light-delay', delay);
      dim.style.setProperty('--auto-light-period', period);
      dim.style.setProperty('--auto-light-delay', delay);
      box.appendChild(glow);
      box.appendChild(dim);
      box.setAttribute('data-auto-light', 'active');
      box.setAttribute('data-auto-light-mode', isPackagedAssetSource(src) ? 'native-sample' : 'canvas-sample');
      box.setAttribute('data-auto-light-count', String(components.length));
    } catch (error) {
      box.setAttribute('data-auto-light', 'unavailable');
    }
  }

  function prepareBackground(img, box, win) {
    if (!img || img.__autoLightPrepared) return;
    img.__autoLightPrepared = true;
    var run = function () {
      if (!img.naturalWidth || !img.naturalHeight) return;
      renderForBackground(img, box, win);
    };
    img.addEventListener('load', run);
    if (img.complete) win.setTimeout(run, 0);
  }

  function install(doc, win) {
    if (!doc || !win || doc.__autoLightFlickerInstalled) return;
    doc.__autoLightFlickerInstalled = true;

    function attach() {
      var box = doc.getElementById('snapshot');
      if (!box) return false;
      var inspect = function () {
        var bg = box.querySelector('.snapshot-bg');
        if (bg) prepareBackground(bg, box, win);
      };
      inspect();
      var observer = new win.MutationObserver(inspect);
      observer.observe(box, { childList: true, subtree: true, attributes: true, attributeFilter: ['src'] });
      doc.__autoLightFlickerObserver = observer;
      return true;
    }

    if (!attach()) {
      if (doc.readyState === 'loading') doc.addEventListener('DOMContentLoaded', attach, { once: true });
      else win.setTimeout(attach, 0);
    }

    function syncVisibility() {
      var rootElement = doc.documentElement;
      if (!rootElement || !rootElement.classList) return;
      rootElement.classList.toggle('auto-light-paused', !!doc.hidden);
    }
    doc.addEventListener('visibilitychange', syncVisibility);
    syncVisibility();
  }

  return {
    detectLightComponents: detectLightComponents,
    coverCrop: coverCrop,
    isPackagedAssetSource: isPackagedAssetSource,
    install: install,
    _clearCache: function () { cache.clear(); }
  };
});
