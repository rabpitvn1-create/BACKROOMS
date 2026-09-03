const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const engine = require(path.join(__dirname, 'app/src/main/assets/auto-light-flicker.js'));

function image(width, height, rgb = [34, 34, 34]) {
  const data = new Uint8ClampedArray(width * height * 4);
  for (let i = 0; i < width * height; i++) {
    const o = i * 4;
    data[o] = rgb[0];
    data[o + 1] = rgb[1];
    data[o + 2] = rgb[2];
    data[o + 3] = 255;
  }
  return data;
}

function fill(data, width, x0, y0, x1, y1, rgb) {
  for (let y = y0; y <= y1; y++) {
    for (let x = x0; x <= x1; x++) {
      const o = (y * width + x) * 4;
      data[o] = rgb[0];
      data[o + 1] = rgb[1];
      data[o + 2] = rgb[2];
      data[o + 3] = 255;
    }
  }
}

test('dark backgrounds do not invent light sources', () => {
  const width = 48, height = 24;
  const data = image(width, height, [42, 39, 35]);
  assert.deepEqual(engine.detectLightComponents(data, width, height), []);
});

test('small fluorescent ceiling fixture is detected', () => {
  const width = 64, height = 32;
  const data = image(width, height, [76, 70, 40]);
  fill(data, width, 18, 5, 28, 7, [250, 246, 205]);

  const lights = engine.detectLightComponents(data, width, height);
  assert.ok(lights.length >= 1, 'expected at least one detected fixture');
  assert.ok(lights.some(light => light.minX <= 20 && light.maxX >= 26 && light.minY <= 6 && light.maxY >= 6));
});

test('warm yellow lamp is accepted as a light source', () => {
  const width = 48, height = 24;
  const data = image(width, height, [30, 28, 25]);
  fill(data, width, 30, 9, 34, 12, [255, 205, 72]);

  const lights = engine.detectLightComponents(data, width, height);
  assert.ok(lights.length >= 1, 'warm fixtures must not be rejected for saturation alone');
});

test('large uniformly bright wall is rejected instead of flickering', () => {
  const width = 64, height = 32;
  const data = image(width, height, [45, 45, 45]);
  fill(data, width, 4, 4, 58, 27, [242, 242, 238]);

  const lights = engine.detectLightComponents(data, width, height);
  assert.equal(lights.length, 0);
});

test('cover crop matches CSS object-fit cover geometry', () => {
  assert.deepEqual(engine.coverCrop(1920, 1080, 320, 320), {
    sx: 420,
    sy: 0,
    sw: 1080,
    sh: 1080
  });
  assert.deepEqual(engine.coverCrop(1080, 1920, 320, 180), {
    sx: 0,
    sy: 656.25,
    sw: 1080,
    sh: 607.5
  });
});
