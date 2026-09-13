(function () {
  'use strict';

  const path = location.pathname || '';
  const host = (location.hostname || '').toLowerCase();
  const isInstagram = host === 'instagram.com'
    || host.endsWith('.instagram.com')
    || host === 'instagr.am'
    || host.endsWith('.instagr.am');
  const singleVideoRoute = isInstagram && (path.startsWith('/reel/') || path.startsWith('/tv/'));
  const result = {
    items: [],
    clickedNext: false,
    loginPage: false,
    errorPage: false,
    username: '',
    expectsVideo: singleVideoRoute,
    videoElementCount: 0,
    videoPoster: '',
    videoWidth: 0,
    videoHeight: 0,
    sourceTimestampMillis: 0,
    networkVideoUrls: []
  };

  result.loginPage = isInstagram && path.startsWith('/accounts/login');
  result.errorPage = Boolean(document.querySelector(
    '[data-testid="error-root"], .PolarisErrorRoot, #httpErrorPage, [role="alert"] [data-visualcompletion="ignore-dynamic"]'
  ));

  const article = document.querySelector('main article') || document.querySelector('article');
  const scope = article || document.querySelector('main') || document.body;
  if (!scope) return JSON.stringify(result);

  const profileLink = isInstagram ? scope.querySelector('header a[href^="/"]') : null;
  if (profileLink) {
    const segment = (profileLink.getAttribute('href') || '').split('/').filter(Boolean)[0] || '';
    if (segment && !['p', 'reel', 'tv', 'stories', 'explore'].includes(segment)) {
      result.username = segment;
    }
  }

  const publishedTime = scope.querySelector('time[datetime]');
  if (publishedTime) {
    const parsedTime = Date.parse(publishedTime.getAttribute('datetime') || '');
    if (Number.isFinite(parsedTime) && parsedTime > 0) result.sourceTimestampMillis = parsedTime;
  }

  const seen = new Set();
  const add = (candidate, isVideo, thumbnail, width, height) => {
    let url;
    try {
      url = new URL(candidate, location.href).href;
    } catch {
      return;
    }
    if (!/^https?:\/\//i.test(url) || seen.has(url)) return;
    seen.add(url);
    result.items.push({
      url,
      isVideo: Boolean(isVideo),
      thumbnail: thumbnail || '',
      width: Number(width) || 0,
      height: Number(height) || 0
    });
  };

  const directPath = path.toLowerCase();
  if (/\.(jpe?g|png|gif|webp)$/.test(directPath)) add(location.href, false, '', 0, 0);
  if (/\.(mp4|m4v|webm)$/.test(directPath)) add(location.href, true, '', 0, 0);

  const videos = Array.from(scope.querySelectorAll('video'));
  result.videoElementCount = videos.length;
  const visibleVideo = videos.find((video) => {
    const rect = video.getBoundingClientRect();
    return rect.width > 160 && rect.height > 160;
  }) || videos[0];
  if (visibleVideo) {
    result.videoPoster = visibleVideo.poster || '';
    result.videoWidth = visibleVideo.videoWidth || visibleVideo.clientWidth || 0;
    result.videoHeight = visibleVideo.videoHeight || visibleVideo.clientHeight || 0;
  }

  const hasDirectVideo = videos.some((video) => {
    const source = video.currentSrc || video.src || video.querySelector('source')?.src || '';
    return /^https?:\/\//i.test(source);
  });
  if (videos.length > 0 && !hasDirectVideo && window.performance) {
    result.networkVideoUrls = Array.from(performance.getEntriesByType('resource') || [])
      .map((entry) => entry.name || '')
      .filter((url) => {
        const lower = url.toLowerCase();
        return /^https?:\/\//.test(lower)
          && (lower.includes('.mp4') || lower.includes('mime_type=video') || lower.includes('video%2fmp4'));
      })
      .slice(-12);
  }
  videos.forEach((video) => {
    const source = video.currentSrc || video.src || video.querySelector('source')?.src || '';
    add(
      source,
      true,
      video.poster || '',
      video.videoWidth || video.clientWidth,
      video.videoHeight || video.clientHeight
    );
  });

  const bestImageSource = (image) => {
    let best = image.currentSrc || image.src || '';
    let bestWidth = image.naturalWidth || 0;
    (image.srcset || '').split(',').forEach((candidate) => {
      const parts = candidate.trim().split(/\s+/);
      const width = parts.length > 1 ? Number.parseInt(parts[1], 10) || 0 : 0;
      if (parts[0] && width >= bestWidth) {
        best = parts[0];
        bestWidth = width;
      }
    });
    return best;
  };

  Array.from(scope.querySelectorAll('img')).forEach((image) => {
    const rect = image.getBoundingClientRect();
    if (rect.width < 160 || rect.height < 160 || image.naturalWidth < 300 || image.naturalHeight < 200) return;
    const overlapsVideo = videos.some((video) => {
      const videoRect = video.getBoundingClientRect();
      const overlapWidth = Math.max(0, Math.min(rect.right, videoRect.right) - Math.max(rect.left, videoRect.left));
      const overlapHeight = Math.max(0, Math.min(rect.bottom, videoRect.bottom) - Math.max(rect.top, videoRect.top));
      return overlapWidth * overlapHeight > rect.width * rect.height * 0.5;
    });
    if (!overlapsVideo) add(bestImageSource(image), false, '', image.naturalWidth, image.naturalHeight);
  });

  if (result.items.length === 0) {
    const metaVideo = document.querySelector('meta[property="og:video:secure_url"], meta[property="og:video"]');
    const metaImage = document.querySelector('meta[property="og:image"]');
    if (metaVideo) add(metaVideo.content, true, metaImage?.content || '', 0, 0);
    else if (metaImage) add(metaImage.content, false, '', 0, 0);
  }

  const isVisible = (element) => {
    const rect = element.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0 && getComputedStyle(element).visibility !== 'hidden';
  };
  const hasForwardGlyph = (element) => {
    const svg = element.querySelector('svg');
    if (!svg) return false;
    const box = svg.getAttribute('viewBox') || '';
    const paths = Array.from(svg.querySelectorAll('path, polyline')).map((node) => (
      node.getAttribute('d') || node.getAttribute('points') || ''
    )).join(' ');
    return box.length > 0 && /[lL]\s*[-+]?\d/.test(paths) && !element.closest('header');
  };
  const nextLabels = ['next', 'siguiente', 'suivant', 'weiter', 'avanti', 'próximo', 'proximo', 'volgende', '次へ', '下一步', '다음'];
  const isForwardControl = (button) => {
    if (!isVisible(button) || button.disabled || button.getAttribute('aria-disabled') === 'true') return false;
    const labelled = button.matches('[aria-label]') ? button : button.querySelector('[aria-label]');
    const label = (labelled?.getAttribute('aria-label') || '').trim().toLowerCase();
    if (nextLabels.some((keyword) => label === keyword || label.includes(keyword))) return true;
    if (button.matches('[data-testid*="next" i], [class*="next" i]')) return true;
    return hasForwardGlyph(button) && button.getBoundingClientRect().left > article.getBoundingClientRect().left + article.clientWidth / 2;
  };

  const isStory = isInstagram && path.startsWith('/stories/');
  if (isInstagram && !isStory && !singleVideoRoute && article) {
    const nextButton = Array.from(article.querySelectorAll('button, [role="button"]')).find(isForwardControl);
    if (nextButton) {
      nextButton.click();
      result.clickedNext = true;
    }
  }

  return JSON.stringify(result);
})();
