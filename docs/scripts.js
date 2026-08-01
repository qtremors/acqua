const header = document.querySelector("[data-header]");
const navToggle = document.querySelector("[data-nav-toggle]");
const navLinks = document.querySelector("[data-nav-links]");
const latestReleaseApi = "https://api.github.com/repos/qtremors/acqua/releases/latest";
const latestReleaseFallback = "https://github.com/qtremors/acqua/releases/latest";

const releaseDownloadCount = (release) => {
  if (!Array.isArray(release?.assets)) return 0;
  return release.assets.reduce(
    (total, asset) => total + (Number(asset?.download_count) || 0),
    0
  );
};

const universalApkUrl = (release, tag) => {
  if (!Array.isArray(release?.assets)) return null;
  const version = tag.replace(/^v/i, "");
  const expectedName = `Acqua-${version}.apk`;
  const asset = release.assets.find((candidate) => candidate?.name === expectedName);
  return typeof asset?.browser_download_url === "string" && asset.browser_download_url.startsWith("https://")
    ? asset.browser_download_url
    : null;
};

const applyLatestRelease = (release) => {
  const tag = typeof release?.tag_name === "string" ? release.tag_name.trim() : "";
  if (!tag) return;

  const releaseUrl = typeof release.html_url === "string" && release.html_url.startsWith("https://")
    ? release.html_url
    : latestReleaseFallback;
  const downloadUrl = universalApkUrl(release, tag) || releaseUrl;
  const downloads = releaseDownloadCount(release);

  document.querySelectorAll("[data-latest-release]").forEach((element) => {
    element.textContent = downloads > 0
      ? `Latest release · ${tag} · ${downloads.toLocaleString()} downloads`
      : `Latest release · ${tag}`;
  });
  document.querySelectorAll("[data-download-label]").forEach((element) => {
    element.textContent = `Download ${tag}`;
  });
  document.querySelectorAll("[data-latest-release-link]").forEach((element) => {
    element.setAttribute("href", downloadUrl);
    element.setAttribute("aria-label", `Download Acqua ${tag} universal APK`);
  });
};

const loadLatestRelease = async () => {
  try {
    const response = await fetch(latestReleaseApi, {
      headers: { Accept: "application/vnd.github+json" }
    });
    if (!response.ok) return;
    applyLatestRelease(await response.json());
  } catch {
    // Static labels and /releases/latest links remain usable offline or when rate limited.
  }
};

const closeNavigation = () => {
  navToggle?.setAttribute("aria-expanded", "false");
  navToggle?.setAttribute("aria-label", "Open navigation");
  navLinks?.classList.remove("open");
};

const updateHeader = () => header?.classList.toggle("scrolled", window.scrollY > 14);
updateHeader();
window.addEventListener("scroll", updateHeader, { passive: true });

navToggle?.addEventListener("click", () => {
  const open = navToggle.getAttribute("aria-expanded") === "true";
  navToggle.setAttribute("aria-expanded", String(!open));
  navToggle.setAttribute("aria-label", open ? "Open navigation" : "Close navigation");
  navLinks?.classList.toggle("open", !open);
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

loadLatestRelease();

const items = document.querySelectorAll(".reveal");
const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

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
