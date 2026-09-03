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
  var MAX_COMPONENTS = 10;
  var cache = new Map();

  function clamp(value, min, max) { return Math.max(min, Math.min(max, value)); }
  function luminance(r, g, b) { return 0.2126 * r + 0.7152 * g + 0.0722 * b; }
  function percentile(values, ratio) {
    if (!values.length) return 0;
    var copy = Array.prototype.slice.call(values).sort(function (a, b) { return a - b; });
    return copy[clamp(Math.floor((copy.length - 1) * ratio), 0, copy.length - 1)];
  }
  function neighborhoodAverage(luma, width, height, x, y) {
    var sum = 0, count = 0;
    for (var dy = -3; dy <= 3; dy++) for (var dx = -3; dx <= 3; dx++) {
      if (Math.abs(dx) <= 1 && Math.abs(dy) <= 1) continue;
      var nx = x + dx, ny = y + dy;
      if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
      sum += luma[ny * width + nx]; count++;
    }
    return count ? sum / count : luma[y * width + x];
  }
  function surroundingAverage(luma, width, height, minX, maxX, minY, maxY) {
    var left = Math.max(0, minX - 2), right = Math.min(width - 1, maxX + 2);
    var top = Math.max(0, minY - 2), bottom = Math.min(height - 1, maxY + 2);
    var sum = 0, count = 0;
    for (var y = top; y <= bottom; y++) for (var x = left; x <= right; x++) {
      if (x >= minX && x <= maxX && y >= minY && y <= maxY) continue;
      sum += luma[y * width + x]; count++;
    }
    return count ? sum / count : 0;
  }

  function detectLightComponents(rgba, width, height) {
    if (!rgba || width <= 0 || height <= 0 || rgba.length < width * height * 4) return [];
    var count = width * height, luma = new Float32Array(count), sampled = [];
    for (var i = 0; i < count; i++) {
      var offset = i * 4, y = luminance(rgba[offset], rgba[offset + 1], rgba[offset + 2]);
      luma[i] = y; sampled.push(y);
    }
    var p88 = percentile(sampled, 0.88), p96 = percentile(sampled, 0.96);
    var spread = Math.max(0, p96 - p88);
    var brightCutoff = clamp(Math.max(150, p88 + Math.max(6, Math.min(24, spread * 0.45))), 150, 238);
    var mask = new Uint8Array(count), contrast = new Float32Array(count);
    for (var py = 0; py < height; py++) for (var px = 0; px < width; px++) {
      var index = py * width + px, base = index * 4;
      var r = rgba[base], g = rgba[base + 1], b = rgba[base + 2], lum = luma[index];
      if (lum < brightCutoff) continue;
      var localContrast = lum - neighborhoodAverage(luma, width, height, px, py);
      contrast[index] = localContrast;
      var maxChannel = Math.max(r, g, b), minChannel = Math.min(r, g, b), chroma = maxChannel - minChannel;
      var neutralLight = chroma <= 92;
      var warmLight = r >= 190 && g >= 150 && b >= 38 && r >= g && g >= b;
      var coolLight = b >= 165 && g >= 150 && r >= 125 && b >= r;
      var contrastNeeded = lum >= 242 ? 7 : (lum >= 215 ? 10 : 13);
      if ((neutralLight || warmLight || coolLight) && localContrast >= contrastNeeded) mask[index] = 1;
    }

    var seen = new Uint8Array(count), components = [];
    var neighbors = [[-1,-1],[0,-1],[1,-1],[-1,0],[1,0],[-1,1],[0,1],[1,1]];
    for (var start = 0; start < count; start++) {
      if (!mask[start] || seen[start]) continue;
      var queue = [start], q = 0, pixels = [];
      var minX = width, maxX = 0, minY = height, maxY = 0;
      var sumContrast = 0, sumLuma = 0, sumR = 0, sumG = 0, sumB = 0;
      seen[start] = 1;
      while (q < queue.length) {
        var current = queue[q++]; pixels.push(current);
        var x = current % width, yPos = Math.floor(current / width);
        minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, yPos); maxY = Math.max(maxY, yPos);
        sumContrast += Math.max(0, contrast[current]); sumLuma += luma[current];
        var rgbaOffset = current * 4;
        sumR += rgba[rgbaOffset]; sumG += rgba[rgbaOffset + 1]; sumB += rgba[rgbaOffset + 2];
        for (var n = 0; n < neighbors.length; n++) {
          var nx = x + neighbors[n][0], ny = yPos + neighbors[n][1];
          if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
          var next = ny * width + nx;
          if (!mask[next] || seen[next]) continue;
          seen[next] = 1; queue.push(next);
        }
      }
      var area = pixels.length, boxWidth = maxX - minX + 1, boxHeight = maxY - minY + 1;
      var boxArea = boxWidth * boxHeight, fill = area / Math.max(1, boxArea);
      var areaRatio = area / count, widthRatio = boxWidth / width, heightRatio = boxHeight / height;
      if (area < 2 || areaRatio > 0.055) continue;
      if (boxArea / count > 0.12) continue;
      if (widthRatio > 0.72 && heightRatio > 0.16) continue;
      if (heightRatio > 0.44 || fill < 0.12) continue;
      var avgContrast = sumContrast / area, avgLuma = sumLuma / area;
      var backgroundLuma = surroundingAverage(luma, width, height, minX, maxX, minY, maxY);
      var edgeContrast = avgLuma - backgroundLuma;
      if (edgeContrast < 12 && avgLuma < 248) continue;
      if (avgContrast < 7 && edgeContrast < 24) continue;
      var confidence = clamp((edgeContrast - 8) / 44, 0, 1) * 0.50 +
        clamp((avgLuma - 155) / 95, 0, 1) * 0.30 + clamp(fill / 0.55, 0, 1) * 0.20;
      if (confidence < 0.46) continue;
      components.push({
        minX:minX,maxX:maxX,minY:minY,maxY:maxY,width:boxWidth,height:boxHeight,area:area,fill:fill,
        avgContrast:avgContrast,avgLuma:avgLuma,edgeContrast:edgeContrast,confidence:confidence,pixels:pixels.slice(),
        r:Math.round(sumR/area),g:Math.round(sumG/area),b:Math.round(sumB/area),
        score:confidence*1000+area*4+edgeContrast*2
      });
    }
    components.sort(function (a,b) { return b.score-a.score; });
    return components.slice(0, MAX_COMPONENTS);
  }

  function coverCrop(imageWidth, imageHeight, boxWidth, boxHeight) {
    if (!(imageWidth > 0 && imageHeight > 0 && boxWidth > 0 && boxHeight > 0)) return {sx:0,sy:0,sw:imageWidth||1,sh:imageHeight||1};
    var imageRatio = imageWidth / imageHeight, boxRatio = boxWidth / boxHeight;
    if (imageRatio > boxRatio) { var sw = imageHeight * boxRatio; return {sx:(imageWidth-sw)/2,sy:0,sw:sw,sh:imageHeight}; }
    var sh = imageWidth / boxRatio; return {sx:0,sy:(imageHeight-sh)/2,sw:imageWidth,sh:sh};
  }

  function paintMask(ctx, component, width, color, coreAlpha, haloAlpha) {
    if (!component || !Array.isArray(component.pixels) || !component.pixels.length) return;
    var r=color[0],g=color[1],b=color[2];
    if (haloAlpha > 0) {
      ctx.fillStyle='rgba('+r+','+g+','+b+','+haloAlpha+')';
      component.pixels.forEach(function(index){var x=index%width,y=Math.floor(index/width);ctx.fillRect(x-1,y-1,3,3);});
    }
    ctx.fillStyle='rgba('+r+','+g+','+b+','+coreAlpha+')';
    component.pixels.forEach(function(index){var x=index%width,y=Math.floor(index/width);ctx.fillRect(x,y,1,1);});
  }
  function drawGlow(canvas, components, detectWidth) {
    var ctx=canvas.getContext('2d'); if(!ctx)return; ctx.clearRect(0,0,canvas.width,canvas.height); ctx.globalCompositeOperation='source-over';
    components.forEach(function(component){
      var r=clamp(Math.round(component.r*1.04+10),0,255),g=clamp(Math.round(component.g*1.04+10),0,255),b=clamp(Math.round(component.b*1.04+10),0,255);
      paintMask(ctx,component,detectWidth,[r,g,b],0.94,0.20);
    });
  }
  function drawDimmer(canvas, components) {
    var ctx=canvas.getContext('2d'); if(!ctx)return; ctx.clearRect(0,0,canvas.width,canvas.height); ctx.globalCompositeOperation='source-over';
    components.forEach(function(component){paintMask(ctx,component,canvas.width,[0,0,0],0.86,0.08);});
  }

  function sourceKey(img,width,height){return String(img.currentSrc||img.src||'')+'|'+width+'x'+height;}
  function isPackagedAssetSource(src){return /^file:\/\/\/android_asset\/level_snapshots\//.test(String(src||''));}
  function decodeBase64Rgba(encoded,win){
    if(!encoded)return new Uint8ClampedArray(0); var binary='';
    if(win&&typeof win.atob==='function')binary=win.atob(encoded);
    else if(typeof Buffer!=='undefined')binary=Buffer.from(encoded,'base64').toString('binary');
    else throw new Error('No base64 decoder available');
    var out=new Uint8ClampedArray(binary.length); for(var i=0;i<binary.length;i++)out[i]=binary.charCodeAt(i)&255; return out;
  }
  function analyzePackagedImage(img,box,win){
    if(!win.Android||typeof win.Android.sampleAutoLightPixels!=='function')throw new Error('Native packaged-light sampler unavailable');
    var cssWidth=Math.max(1,Math.round(box.clientWidth||img.clientWidth||320)),cssHeight=Math.max(1,Math.round(box.clientHeight||img.clientHeight||180));
    var raw=win.Android.sampleAutoLightPixels(String(img.currentSrc||img.src||''),cssWidth,cssHeight); if(!raw)throw new Error('Native packaged-light sampler returned no data');
    var sample=JSON.parse(raw),width=Number(sample.width||0),height=Number(sample.height||0),rgba=decodeBase64Rgba(String(sample.rgba||''),win);
    if(width<=0||height<=0||rgba.length<width*height*4)throw new Error('Native packaged-light sampler returned invalid data');
    return detectLightComponents(rgba,width,height);
  }
  function analyzeImage(img,box,win){
    var cssWidth=Math.max(1,Math.round(box.clientWidth||img.clientWidth||320)),cssHeight=Math.max(1,Math.round(box.clientHeight||img.clientHeight||180));
    var detectWidth=DETECT_WIDTH,detectHeight=clamp(Math.round(DETECT_WIDTH*cssHeight/cssWidth),48,96),key=sourceKey(img,detectWidth,detectHeight);
    if(cache.has(key))return cache.get(key); var src=String(img.currentSrc||img.src||'');
    if(isPackagedAssetSource(src)){var packaged=analyzePackagedImage(img,box,win);cache.set(key,packaged);return packaged;}
    var detector=win.document.createElement('canvas');detector.width=detectWidth;detector.height=detectHeight;
    var ctx=detector.getContext('2d',{willReadFrequently:true});if(!ctx)return[];
    var crop=coverCrop(img.naturalWidth,img.naturalHeight,cssWidth,cssHeight);
    ctx.drawImage(img,crop.sx,crop.sy,crop.sw,crop.sh,0,0,detectWidth,detectHeight);
    var result=detectLightComponents(ctx.getImageData(0,0,detectWidth,detectHeight).data,detectWidth,detectHeight);cache.set(key,result);return result;
  }
  function renderForBackground(img,box,win){
    if(!img||!box||!win||!win.document)return;var src=String(img.currentSrc||img.src||'');if(!src)return;
    var token=src+'|'+Date.now();box.__autoLightToken=token;
    try{
      var components=analyzeImage(img,box,win);if(box.__autoLightToken!==token||!img.isConnected)return;
      var oldLayers=box.querySelectorAll('.snapshot-auto-light-layer');for(var oldIndex=0;oldIndex<oldLayers.length;oldIndex++)oldLayers[oldIndex].remove();
      if(!components.length){box.setAttribute('data-auto-light','none');return;}
      var detectHeight=clamp(Math.round(DETECT_WIDTH*Math.max(1,box.clientHeight)/Math.max(1,box.clientWidth)),48,96);
      var glow=win.document.createElement('canvas');glow.className='snapshot-auto-light-layer snapshot-auto-light-glow';glow.setAttribute('aria-hidden','true');glow.width=DETECT_WIDTH;glow.height=detectHeight;drawGlow(glow,components,glow.width);
      var dim=win.document.createElement('canvas');dim.className='snapshot-auto-light-layer snapshot-auto-light-dim';dim.setAttribute('aria-hidden','true');dim.width=DETECT_WIDTH;dim.height=detectHeight;drawDimmer(dim,components);
      var hash=0;for(var i=0;i<src.length;i++)hash=(hash*33+src.charCodeAt(i))>>>0;
      var period=(4.2+(hash%2800)/1000).toFixed(2)+'s',delay='-'+((hash>>>3)%2400)+'ms';
      glow.style.setProperty('--auto-light-period',period);glow.style.setProperty('--auto-light-delay',delay);dim.style.setProperty('--auto-light-period',period);dim.style.setProperty('--auto-light-delay',delay);
      box.appendChild(glow);box.appendChild(dim);box.setAttribute('data-auto-light','active');
      box.setAttribute('data-auto-light-mode',isPackagedAssetSource(src)?'native-mask':'canvas-mask');box.setAttribute('data-auto-light-count',String(components.length));
    }catch(error){box.setAttribute('data-auto-light','unavailable');}
  }
  function prepareBackground(img,box,win){
    if(!img||img.__autoLightPrepared)return;img.__autoLightPrepared=true;
    var run=function(){if(!img.naturalWidth||!img.naturalHeight)return;renderForBackground(img,box,win);};
    img.addEventListener('load',run);if(img.complete)win.setTimeout(run,0);
  }
  function install(doc,win){
    if(!doc||!win||doc.__autoLightFlickerInstalled)return;doc.__autoLightFlickerInstalled=true;
    function attach(){var box=doc.getElementById('snapshot');if(!box)return false;var inspect=function(){var bg=box.querySelector('.snapshot-bg');if(bg)prepareBackground(bg,box,win);};inspect();var observer=new win.MutationObserver(inspect);observer.observe(box,{childList:true,subtree:true,attributes:true,attributeFilter:['src']});doc.__autoLightFlickerObserver=observer;return true;}
    if(!attach()){if(doc.readyState==='loading')doc.addEventListener('DOMContentLoaded',attach,{once:true});else win.setTimeout(attach,0);}
    function syncVisibility(){var rootElement=doc.documentElement;if(!rootElement||!rootElement.classList)return;rootElement.classList.toggle('auto-light-paused',!!doc.hidden);}
    doc.addEventListener('visibilitychange',syncVisibility);syncVisibility();
  }
  return {detectLightComponents:detectLightComponents,coverCrop:coverCrop,isPackagedAssetSource:isPackagedAssetSource,install:install,_clearCache:function(){cache.clear();}};
});
