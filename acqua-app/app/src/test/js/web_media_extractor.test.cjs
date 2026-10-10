const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const extractor = fs.readFileSync(path.join(__dirname, '../../main/assets/web_media_extractor.js'), 'utf8');
function page(scripts = []) {
  let time = 10_000;
  let clicks = 0;
  const rect = { left: 0, right: 400, top: 0, bottom: 400, width: 400, height: 400 };
  const image = { currentSrc: 'https://cdn.test/slide.jpg', naturalWidth: 1080, naturalHeight: 1080,
    getBoundingClientRect: () => rect };
  const button = { disabled: false, getBoundingClientRect: () => ({ left: 350, top: 180, width: 40, height: 40 }),
    matches: () => true, getAttribute: name => name === 'aria-label' ? 'Next' : null,
    click: () => { clicks++; } };
  const article = { querySelector: () => null, getBoundingClientRect: () => rect,
    querySelectorAll: selector => selector === 'img' ? [image] : selector === 'button, [role="button"]' ? [button] : [] };
  const context = vm.createContext({ URL, Date: class extends Date { static now() { return time; } },
    location: { href: 'https://www.instagram.com/p/target/', origin: 'https://www.instagram.com', pathname: '/p/target/', hostname: 'www.instagram.com' },
    window: { innerWidth: 400 }, getComputedStyle: () => ({ visibility: 'visible' }),
    document: { body: article, querySelector: selector => selector === 'main article' ? article : null,
      querySelectorAll: () => scripts.map(textContent => ({ textContent })) } });
  return { extract: () => JSON.parse(vm.runInContext(extractor, context)), tick: value => { time += value; }, clicks: () => clicks };
}

test('finds a complete carousel after more than 48 unrelated JSON scripts', () => {
  const carousel = JSON.stringify({ shortcode: 'target', carousel_media: [
    { display_url: 'https://cdn.test/first.jpg' }, { display_url: 'https://cdn.test/second.jpg' }
  ] });
  const fixture = page([...Array(60).fill('{"unrelated":true}'), carousel]);
  const result = fixture.extract();
  assert.equal(result.structuredCarousel, true);
  assert.deepEqual(result.items.slice(0, 2).map(item => item.url), ['https://cdn.test/first.jpg', 'https://cdn.test/second.jpg']);
  assert.equal(fixture.clicks(), 0);
});

test('retries stalled slide advancement after the delay without clicking on every poll', () => {
  const fixture = page();
  assert.equal(fixture.extract().clickedNext, true);
  fixture.tick(500);
  assert.equal(fixture.extract().clickedNext, false);
  fixture.tick(1_000);
  assert.equal(fixture.extract().clickedNext, true);
  assert.equal(fixture.clicks(), 2);
});
