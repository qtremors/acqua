const header = document.querySelector("[data-header]");
const navToggle = document.querySelector("[data-nav-toggle]");
const navLinks = document.querySelector("[data-nav-links]");
const latestReleaseApi = "https://api.github.com/repos/qtremors/acqua/releases/latest";
const repoApi = "https://api.github.com/repos/qtremors/acqua";
const latestReleaseFallback = "https://github.com/qtremors/acqua/releases/latest";

const numberFormatter = new Intl.NumberFormat();
const reduceMotionQuery = window.matchMedia("(prefers-reduced-motion: reduce)");

const closeNavigation = () => {
  navToggle?.setAttribute("aria-expanded", "false");
  navToggle?.setAttribute("aria-label", "Open navigation");
  navLinks?.classList.remove("open");
  document.body.style.overflow = "";
};

const updateHeader = () => header?.classList.toggle("scrolled", window.scrollY > 14);
updateHeader();
window.addEventListener("scroll", updateHeader, { passive: true });

navToggle?.addEventListener("click", () => {
  const open = navToggle.getAttribute("aria-expanded") === "true";
  navToggle.setAttribute("aria-expanded", String(!open));
  navToggle.setAttribute("aria-label", open ? "Open navigation" : "Close navigation");
  navLinks?.classList.toggle("open", !open);
  document.body.style.overflow = !open ? "hidden" : "";
});

navLinks?.querySelectorAll("a").forEach((link) => link.addEventListener("click", closeNavigation));

document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") closeNavigation();
});

document.addEventListener("click", (event) => {
  if (!header?.contains(event.target)) closeNavigation();
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

const items = document.querySelectorAll(".reveal");
const reducedMotion = reduceMotionQuery.matches;

if (!("IntersectionObserver" in window) || reducedMotion) {
  items.forEach((item) => item.classList.add("visible"));
} else {
  const observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) return;
      entry.target.classList.add("visible");
      observer.unobserve(entry.target);
    });
  }, { threshold: 0.1, rootMargin: "0px 0px -25px" });

  items.forEach((item) => observer.observe(item));
}

const animateCounter = (element, target) => {
  if (!element || !Number.isFinite(target)) return;

  const endValue = Math.max(0, Math.trunc(target));
  if (reducedMotion || endValue === 0) {
    element.innerText = numberFormatter.format(endValue);
    return;
  }

  const duration = 800;
  const startTime = performance.now();

  const updateCounter = (currentTime) => {
    const progress = Math.min((currentTime - startTime) / duration, 1);
    const easedProgress = 1 - Math.pow(1 - progress, 3);
    element.innerText = numberFormatter.format(Math.round(endValue * easedProgress));

    if (progress < 1) {
      requestAnimationFrame(updateCounter);
    }
  };

  requestAnimationFrame(updateCounter);
};

const sumReleaseDownloads = (releases) => {
  if (!Array.isArray(releases)) return 0;
  return releases.reduce((releaseTotal, release) => {
    const assetTotal = Array.isArray(release?.assets)
      ? release.assets.reduce((total, asset) => total + (Number(asset?.download_count) || 0), 0)
      : 0;
    return releaseTotal + assetTotal;
  }, 0);
};

const fetchTotalReleaseDownloads = async () => {
  let nextUrl = "https://api.github.com/repos/qtremors/acqua/releases?per_page=100";
  let totalDownloads = 0;

  while (nextUrl) {
    const response = await fetch(nextUrl, {
      headers: { Accept: "application/vnd.github+json" }
    });
    if (!response.ok) throw new Error(`GitHub releases request failed: ${response.status}`);

    const releases = await response.json();
    totalDownloads += sumReleaseDownloads(releases);

    const nextLink = response.headers.get("link")
      ?.split(",")
      .find((link) => link.includes('rel="next"'));
    nextUrl = nextLink?.match(/<([^>]+)>/)?.[1] || "";
  }

  return totalDownloads;
};

const applyLatestRelease = (release) => {
  const tag = typeof release?.tag_name === "string" ? release.tag_name.trim() : "";
  if (!tag) return;

  const releaseUrl = typeof release.html_url === "string" && release.html_url.startsWith("https://")
    ? release.html_url
    : latestReleaseFallback;
  const latestDownloads = sumReleaseDownloads([release]);

  document.querySelectorAll("[data-latest-release]").forEach((element) => {
    element.textContent = latestDownloads > 0
      ? `Latest release · ${tag} · ${latestDownloads.toLocaleString()} downloads`
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

  const latestDownloadsEl = document.getElementById("gh-latest-downloads");
  if (latestDownloadsEl) {
    animateCounter(latestDownloadsEl, latestDownloads);
  }
};

const fetchGitHubStats = async () => {
  const [repoResult, releaseResult, totalDownloadsResult] = await Promise.allSettled([
    fetch(repoApi, { headers: { Accept: "application/vnd.github+json" } }),
    fetch(latestReleaseApi, { headers: { Accept: "application/vnd.github+json" } }),
    fetchTotalReleaseDownloads()
  ]);

  try {
    const repoRes = repoResult.status === "fulfilled" ? repoResult.value : null;
    if (repoRes?.ok) {
      const data = await repoRes.json();
      if (data.stargazers_count !== undefined) {
        animateCounter(document.getElementById("gh-stars"), data.stargazers_count);
        animateCounter(document.getElementById("gh-forks"), data.forks_count);
      }
    }
  } catch (error) {
    console.error("Error fetching repository stats:", error);
  }

  try {
    const releaseRes = releaseResult.status === "fulfilled" ? releaseResult.value : null;
    if (releaseRes?.ok) {
      const release = await releaseRes.json();
      applyLatestRelease(release);
    }
  } catch (error) {
    console.error("Error fetching latest release:", error);
  }

  if (totalDownloadsResult.status === "fulfilled") {
    const totalDownloads = totalDownloadsResult.value;
    animateCounter(document.getElementById("gh-total-downloads"), totalDownloads);
  } else {
    console.error("Error fetching total downloads:", totalDownloadsResult.reason);
  }
};

fetchGitHubStats();
