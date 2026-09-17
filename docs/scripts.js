(() => {
  "use strict";

  const config = Object.freeze({
    cacheKey: "acqua.github-stats.v2",
    cacheTtlMs: 30 * 60 * 1000,
    requestTimeoutMs: 8_000,
    maxReleasePages: 10,
    repoApi: "https://api.github.com/repos/qtremors/acqua",
    latestReleaseApi: "https://api.github.com/repos/qtremors/acqua/releases/latest",
    releasesApi: "https://api.github.com/repos/qtremors/acqua/releases?per_page=100",
    latestReleaseFallback: "https://github.com/qtremors/acqua/releases/latest"
  });

  const numberFormatter = new Intl.NumberFormat();
  const reduceMotionQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
  const header = document.querySelector("[data-header]");
  const navToggle = document.querySelector("[data-nav-toggle]");
  const navLinks = document.querySelector("[data-nav-links]");
  let navigationOpen = false;

  const navigationItems = () => Array.from(
    navLinks?.querySelectorAll("a, button, [tabindex]:not([tabindex='-1'])") || []
  );

  const closeNavigation = ({ restoreFocus = false } = {}) => {
    const wasOpen = navToggle?.getAttribute("aria-expanded") === "true";
    navToggle?.setAttribute("aria-expanded", "false");
    navToggle?.setAttribute("aria-label", "Open navigation");
    navLinks?.classList.remove("open");
    document.body.style.overflow = "";
    navigationOpen = false;
    if (restoreFocus && wasOpen) navToggle?.focus();
  };

  const openNavigation = () => {
    navToggle?.setAttribute("aria-expanded", "true");
    navToggle?.setAttribute("aria-label", "Close navigation");
    navLinks?.classList.add("open");
    document.body.style.overflow = "hidden";
    navigationOpen = true;
    navigationItems()[0]?.focus();
  };

  const trapNavigationFocus = (event) => {
    if (event.key !== "Tab" || !navigationOpen) return;
    const items = navigationItems();
    if (items.length === 0) return;
    const first = items[0];
    const last = items[items.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  };

  const updateHeader = () => header?.classList.toggle("scrolled", window.scrollY > 14);
  updateHeader();
  window.addEventListener("scroll", updateHeader, { passive: true });
  navToggle?.addEventListener("click", () => {
    if (navigationOpen) closeNavigation({ restoreFocus: true });
    else openNavigation();
  });
  navLinks?.querySelectorAll("a").forEach((link) => {
    link.addEventListener("click", () => closeNavigation());
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && navigationOpen) closeNavigation({ restoreFocus: true });
    trapNavigationFocus(event);
  });
  document.addEventListener("click", (event) => {
    if (navigationOpen && !header?.contains(event.target)) closeNavigation({ restoreFocus: true });
  });

  document.querySelectorAll(".faq-list button").forEach((button) => {
    button.addEventListener("click", () => {
      const answerId = button.getAttribute("aria-controls");
      const answer = answerId ? document.getElementById(answerId) : null;
      const open = button.getAttribute("aria-expanded") !== "true";
      button.setAttribute("aria-expanded", String(open));
      if (answer) answer.hidden = !open;
    });
  });

  const revealItems = document.querySelectorAll(".reveal");
  if (!("IntersectionObserver" in window) || reduceMotionQuery.matches) {
    revealItems.forEach((item) => item.classList.add("visible"));
  } else {
    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        entry.target.classList.add("visible");
        observer.unobserve(entry.target);
      });
    }, { threshold: 0.1, rootMargin: "0px 0px -25px" });
    revealItems.forEach((item) => observer.observe(item));
  }

  const animations = new Map();
  let animationFrame = 0;
  const renderAnimations = (now) => {
    animations.forEach((animation, element) => {
      const progress = Math.min((now - animation.startedAt) / animation.duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      element.textContent = numberFormatter.format(Math.round(animation.target * eased));
      if (progress >= 1) animations.delete(element);
    });
    animationFrame = animations.size > 0 ? requestAnimationFrame(renderAnimations) : 0;
  };

  const statValueElement = (container) => container?.querySelector("[data-stat-value]") || container;
  const animateCounter = (container, target) => {
    const element = statValueElement(container);
    if (!element || !Number.isFinite(target)) return;
    const endValue = Math.max(0, Math.trunc(target));
    if (reduceMotionQuery.matches || endValue === 0) {
      element.textContent = numberFormatter.format(endValue);
      return;
    }
    animations.set(element, { target: endValue, startedAt: performance.now(), duration: 800 });
    if (!animationFrame) animationFrame = requestAnimationFrame(renderAnimations);
  };

  const setUnavailable = (id, cachedValue) => {
    const element = statValueElement(document.getElementById(id));
    if (element) {
      element.textContent = Number.isFinite(cachedValue)
        ? numberFormatter.format(cachedValue)
        : "Unavailable";
    }
  };

  const sumReleaseDownloads = (releases) => {
    if (!Array.isArray(releases)) return 0;
    return releases.reduce((releaseTotal, release) => releaseTotal + (
      Array.isArray(release?.assets)
        ? release.assets.reduce((total, asset) => total + (Number(asset?.download_count) || 0), 0)
        : 0
    ), 0);
  };

  const parseNextLink = (header) => header?.split(",")
    .find((link) => link.includes('rel="next"'))
    ?.match(/<([^>]+)>/)?.[1] || "";

  const safeReleaseUrl = (candidate) => {
    try {
      const url = new URL(candidate);
      return url.protocol === "https:"
        && url.origin === "https://github.com"
        && url.pathname.startsWith("/qtremors/acqua/releases/")
        ? url.href
        : config.latestReleaseFallback;
    } catch {
      return config.latestReleaseFallback;
    }
  };

  const readCache = () => {
    try {
      const value = JSON.parse(localStorage.getItem(config.cacheKey) || "null");
      return value && typeof value === "object" ? value : null;
    } catch {
      return null;
    }
  };

  const writeCache = (value) => {
    try {
      localStorage.setItem(config.cacheKey, JSON.stringify(value));
    } catch {
      // Storage can be unavailable in private or restricted contexts.
    }
  };

  class GitHubRequestError extends Error {
    constructor(message, { status = 0, retryAt = 0 } = {}) {
      super(message);
      this.name = "GitHubRequestError";
      this.status = status;
      this.retryAt = retryAt;
    }
  }

  const fetchJson = async (url, etag = "") => {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), config.requestTimeoutMs);
    try {
      const headers = { Accept: "application/vnd.github+json" };
      if (etag) headers["If-None-Match"] = etag;
      const response = await fetch(url, { headers, signal: controller.signal });
      if (response.status === 304) return { notModified: true, etag };
      if (response.status === 403 || response.status === 429) {
        const resetSeconds = Number(response.headers.get("x-ratelimit-reset"));
        const retrySeconds = Number(response.headers.get("retry-after"));
        const retryAt = Number.isFinite(resetSeconds)
          ? resetSeconds * 1000
          : Date.now() + (Number.isFinite(retrySeconds) ? retrySeconds * 1000 : config.cacheTtlMs);
        throw new GitHubRequestError("GitHub API rate limit reached", {
          status: response.status,
          retryAt
        });
      }
      if (!response.ok) {
        throw new GitHubRequestError(`GitHub request failed (${response.status})`, {
          status: response.status
        });
      }
      return {
        data: await response.json(),
        etag: response.headers.get("etag") || "",
        link: response.headers.get("link") || ""
      };
    } finally {
      clearTimeout(timeout);
    }
  };

  const fetchTotalReleaseDownloads = async () => {
    let nextUrl = config.releasesApi;
    let totalDownloads = 0;
    let page = 0;
    while (nextUrl && page < config.maxReleasePages) {
      const result = await fetchJson(nextUrl);
      totalDownloads += sumReleaseDownloads(result.data);
      nextUrl = result.notModified ? "" : parseNextLink(result.link);
      page += 1;
    }
    return totalDownloads;
  };

  const applyLatestRelease = (release) => {
    const tag = typeof release?.tag_name === "string" ? release.tag_name.trim() : "";
    if (!tag) return;
    const releaseUrl = safeReleaseUrl(release.html_url);
    const latestDownloads = sumReleaseDownloads([release]);
    document.querySelectorAll("[data-latest-release]").forEach((element) => {
      element.textContent = latestDownloads > 0
        ? `Latest release · ${tag} · ${numberFormatter.format(latestDownloads)} downloads`
        : `Latest release · ${tag}`;
    });
    document.querySelectorAll("[data-download-label]").forEach((element) => {
      element.textContent = `Get ${tag}`;
    });
    document.querySelectorAll("[data-latest-release-link]").forEach((element) => {
      element.setAttribute("href", releaseUrl);
      element.setAttribute("aria-label", `View Acqua ${tag} release on GitHub`);
      element.setAttribute("target", "_blank");
      element.setAttribute("rel", "noopener noreferrer");
    });
    animateCounter(document.getElementById("gh-latest-downloads"), latestDownloads);
  };

  const applyStats = (stats) => {
    if (!stats) return;
    animateCounter(document.getElementById("gh-stars"), Number(stats.stars));
    animateCounter(document.getElementById("gh-forks"), Number(stats.forks));
    animateCounter(document.getElementById("gh-total-downloads"), Number(stats.totalDownloads));
    if (stats.latestRelease) applyLatestRelease(stats.latestRelease);
  };

  const fetchGitHubStats = async () => {
    const cache = readCache();
    const now = Date.now();
    if (cache?.data) applyStats(cache.data);
    if (cache?.expiresAt > now || cache?.retryAt > now) return cache.data;

    const [repoResult, releaseResult, totalResult] = await Promise.allSettled([
      fetchJson(config.repoApi, cache?.repoEtag),
      fetchJson(config.latestReleaseApi, cache?.releaseEtag),
      fetchTotalReleaseDownloads()
    ]);
    const repo = repoResult.status === "fulfilled" && !repoResult.value.notModified
      ? repoResult.value.data
      : cache?.repo;
    const latestRelease = releaseResult.status === "fulfilled" && !releaseResult.value.notModified
      ? releaseResult.value.data
      : cache?.data?.latestRelease;
    const data = {
      stars: Number(repo?.stargazers_count ?? cache?.data?.stars),
      forks: Number(repo?.forks_count ?? cache?.data?.forks),
      totalDownloads: totalResult.status === "fulfilled"
        ? totalResult.value
        : Number(cache?.data?.totalDownloads),
      latestRelease
    };
    const failures = [repoResult, releaseResult, totalResult]
      .filter((result) => result.status === "rejected");
    const retryAt = failures.reduce(
      (latest, result) => Math.max(latest, Number(result.reason?.retryAt) || 0),
      0
    );
    if ([data.stars, data.forks, data.totalDownloads].some(Number.isFinite) || latestRelease) {
      applyStats(data);
      writeCache({
        data,
        repo,
        repoEtag: repoResult.status === "fulfilled"
          ? repoResult.value.etag || cache?.repoEtag || ""
          : cache?.repoEtag || "",
        releaseEtag: releaseResult.status === "fulfilled"
          ? releaseResult.value.etag || cache?.releaseEtag || ""
          : cache?.releaseEtag || "",
        expiresAt: now + config.cacheTtlMs,
        retryAt
      });
    } else {
      setUnavailable("gh-stars");
      setUnavailable("gh-forks");
      setUnavailable("gh-total-downloads");
      setUnavailable("gh-latest-downloads");
    }
    return data;
  };

  window.AcquaSite = Object.freeze({
    applyLatestRelease,
    closeNavigation,
    fetchGitHubStats,
    openNavigation,
    safeReleaseUrl,
    sumReleaseDownloads
  });
  fetchGitHubStats().catch(() => {
    const cache = readCache();
    setUnavailable("gh-stars", cache?.data?.stars);
    setUnavailable("gh-forks", cache?.data?.forks);
    setUnavailable("gh-total-downloads", cache?.data?.totalDownloads);
    setUnavailable("gh-latest-downloads");
  });
})();
