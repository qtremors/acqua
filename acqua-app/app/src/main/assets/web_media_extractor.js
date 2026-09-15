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
    hasNext: false,
    structuredCarousel: false,
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

  const routeMatch = isInstagram ? path.match(/^\/(?:p|reel|tv)\/([^/]+)/i) : null;
  const routeCode = routeMatch?.[1] || '';
  const bestCandidate = (candidates) => {
    if (!Array.isArray(candidates)) return null;
    return candidates.reduce((best, candidate) => {
      if (!candidate || typeof candidate !== 'object' || !candidate.url) return best;
      const area = (Number(candidate.width) || 0) * (Number(candidate.height) || 0);
      const bestArea = best ? (Number(best.width) || 0) * (Number(best.height) || 0) : -1;
      return area >= bestArea ? candidate : best;
    }, null);
  };
  const addStructuredNode = (node, inheritedUsername, inheritedTimestamp) => {
    if (!node || typeof node !== 'object') return 0;
    const username = node.owner?.username || node.user?.username || inheritedUsername || '';
    const rawTimestamp = Number(
      node.taken_at_timestamp || node.taken_at || node.published_time || inheritedTimestamp || 0
    );
    const timestamp = rawTimestamp > 0
      ? (rawTimestamp > 10000000000 ? rawTimestamp : rawTimestamp * 1000)
      : 0;
    const edgeChildren = node.edge_sidecar_to_children?.edges;
    const children = Array.isArray(edgeChildren)
      ? edgeChildren.map((edge) => edge?.node).filter(Boolean)
      : (Array.isArray(node.carousel_media) ? node.carousel_media : []);
    if (children.length > 0) {
      const before = result.items.length;
      children.forEach((child) => addStructuredNode(child, username, timestamp));
      const added = result.items.length - before;
      if (children.length > 1 && added >= children.length) result.structuredCarousel = true;
      if (!result.username && username) result.username = username;
      if (!result.sourceTimestampMillis && timestamp) result.sourceTimestampMillis = timestamp;
      return added;
    }

    const imageCandidate = bestCandidate(node.image_versions2?.candidates);
    const videoCandidate = bestCandidate(node.video_versions);
    const dimensions = node.dimensions || {};
    const videoUrl = videoCandidate?.url || node.video_url || '';
    const imageUrl = imageCandidate?.url || node.display_url || node.thumbnail_src || '';
    const isVideo = Boolean(node.is_video || Number(node.media_type) === 2 || videoUrl);
    const mediaUrl = isVideo ? videoUrl : imageUrl;
    if (!mediaUrl) return 0;
    const width = Number(
      (isVideo ? videoCandidate?.width : imageCandidate?.width) || dimensions.width || node.original_width || 0
    );
    const height = Number(
      (isVideo ? videoCandidate?.height : imageCandidate?.height) || dimensions.height || node.original_height || 0
    );
    const before = result.items.length;
    add(mediaUrl, isVideo, isVideo ? imageUrl : '', width, height);
    if (!result.username && username) result.username = username;
    if (!result.sourceTimestampMillis && timestamp) result.sourceTimestampMillis = timestamp;
    return result.items.length - before;
  };
  const collectStructuredInstagramMedia = () => {
    if (!routeCode) return;
    const scripts = Array.from(document.querySelectorAll('script[type="application/json"]')).slice(0, 48);
    const stateKey = `${location.origin}${path}`;
    const cached = window.__acquaStructuredMediaState;
    if (cached?.key === stateKey && Array.isArray(cached.items) && cached.items.length > 1) {
      cached.items.forEach((item) => add(item.url, item.isVideo, item.thumbnail, item.width, item.height));
      result.username = cached.username || '';
      result.sourceTimestampMillis = cached.sourceTimestampMillis || 0;
      result.structuredCarousel = true;
      return;
    }
    const now = Date.now();
    if (cached?.key === stateKey && cached.scriptCount === scripts.length && now - cached.lastScanAt < 2000) return;
    const candidates = scripts
      .map((script) => script.textContent || '')
      .filter((text) => text.includes(routeCode))
      .sort((left, right) => left.length - right.length);
    let totalVisited = 0;
    for (const text of candidates) {
      let root;
      try {
        root = JSON.parse(text);
      } catch {
        continue;
      }
      const stack = [root];
      let scriptVisited = 0;
      while (stack.length > 0 && scriptVisited < 50000 && totalVisited < 100000) {
        const value = stack.pop();
        scriptVisited += 1;
        totalVisited += 1;
        if (!value || typeof value !== 'object') continue;
        if (!Array.isArray(value) && (value.shortcode === routeCode || value.code === routeCode)) {
          addStructuredNode(value, '', 0);
          if (result.structuredCarousel) {
            window.__acquaStructuredMediaState = {
              key: stateKey,
              items: result.items.map((item) => ({ ...item })),
              username: result.username,
              sourceTimestampMillis: result.sourceTimestampMillis,
              scriptCount: scripts.length,
              lastScanAt: now
            };
            return;
          }
        }
        if (Array.isArray(value)) {
          for (let index = value.length - 1; index >= 0; index -= 1) stack.push(value[index]);
        } else {
          Object.values(value).forEach((child) => {
            if (child && typeof child === 'object') stack.push(child);
          });
        }
      }
    }
    window.__acquaStructuredMediaState = {
      key: stateKey,
      items: [],
      scriptCount: scripts.length,
      lastScanAt: now
    };
  };

  collectStructuredInstagramMedia();

  const directPath = path.toLowerCase();
  if (/\.(jpe?g|png|gif|webp)$/.test(directPath)) add(location.href, false, '', 0, 0);
  if (/\.(mp4|m4v|webm)$/.test(directPath)) add(location.href, true, '', 0, 0);

  const isCurrentInstagramMedia = (element) => {
    if (!isInstagram || !article) return true;
    const rect = element.getBoundingClientRect();
    const articleRect = article.getBoundingClientRect();
    const centerX = rect.left + rect.width / 2;
    return rect.width > 0
      && rect.height > 0
      && centerX >= Math.max(0, articleRect.left)
      && centerX <= Math.min(window.innerWidth, articleRect.right);
  };

  const videos = Array.from(scope.querySelectorAll('video')).filter(isCurrentInstagramMedia);
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

  const images = Array.from(scope.querySelectorAll('img')).filter(isCurrentInstagramMedia);
  images.forEach((image) => {
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
  const nextLabels = ['next', 'siguiente', 'suivant', 'weiter', 'avanti', 'próximo', 'proximo', 'volgende', '次へ', '下一步', '다음'];
  const isForwardControl = (button) => {
    if (!isVisible(button) || button.disabled || button.getAttribute('aria-disabled') === 'true') return false;
    const labelled = button.matches('[aria-label]') ? button : button.querySelector('[aria-label]');
    const label = (labelled?.getAttribute('aria-label') || '').trim().toLowerCase();
    const labelledAsNext = nextLabels.some((keyword) => (
      label === keyword || label.startsWith(`${keyword} `) || label.startsWith(`${keyword},`)
    ));
    if (!labelledAsNext) return false;

    const mediaElement = [...videos, ...images].sort((left, right) => {
      const leftRect = left.getBoundingClientRect();
      const rightRect = right.getBoundingClientRect();
      return rightRect.width * rightRect.height - leftRect.width * leftRect.height;
    })[0];
    if (!mediaElement) return false;

    const controlRect = button.getBoundingClientRect();
    const mediaRect = mediaElement.getBoundingClientRect();
    const centerY = controlRect.top + controlRect.height / 2;
    return controlRect.width <= 96
      && controlRect.height <= 96
      && controlRect.left >= mediaRect.left + mediaRect.width / 2
      && centerY >= mediaRect.top
      && centerY <= mediaRect.bottom;
  };

  const isStory = isInstagram && path.startsWith('/stories/');
  if (isInstagram && !isStory && !singleVideoRoute && article && !result.structuredCarousel) {
    const nextButton = Array.from(article.querySelectorAll('button, [role="button"]')).find(isForwardControl);
    if (nextButton) {
      result.hasNext = true;
      const mediaSignature = [...videos, ...images]
        .map((element) => element.currentSrc || element.src || '')
        .filter(Boolean)
        .join('|') || result.items.map((item) => item.url).join('|') || location.href;
      const stateKey = `${location.origin}${path}`;
      const previousState = window.__acquaCarouselState?.key === stateKey
        ? window.__acquaCarouselState
        : { key: stateKey, lastClickAt: 0, lastClickedMedia: '' };
      const now = Date.now();
      const mediaChanged = !previousState.lastClickedMedia || mediaSignature !== previousState.lastClickedMedia;
      if (mediaChanged && now - previousState.lastClickAt >= 1000) {
        nextButton.click();
        result.clickedNext = true;
        previousState.lastClickAt = now;
        previousState.lastClickedMedia = mediaSignature;
      }
      window.__acquaCarouselState = previousState;
    }
  }

  return JSON.stringify(result);
})();
