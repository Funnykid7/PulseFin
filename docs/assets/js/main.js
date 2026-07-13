// Sticky nav shadow + mobile menu toggle + scroll-reveal. No dependencies.

const nav = document.querySelector('.nav');
const onScroll = () => nav.classList.toggle('is-scrolled', window.scrollY > 8);
onScroll();
window.addEventListener('scroll', onScroll, { passive: true });

const navToggle = document.getElementById('navToggle');
const navLinks = document.getElementById('navLinks');

if (navToggle && navLinks) {
  navToggle.addEventListener('click', () => {
    const isOpen = navLinks.classList.toggle('is-open');
    navToggle.setAttribute('aria-expanded', String(isOpen));
  });

  navLinks.querySelectorAll('a').forEach((link) => {
    link.addEventListener('click', () => {
      navLinks.classList.remove('is-open');
      navToggle.setAttribute('aria-expanded', 'false');
    });
  });
}

const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
const revealTargets = document.querySelectorAll('.reveal');

if (prefersReducedMotion || !('IntersectionObserver' in window)) {
  revealTargets.forEach((el) => el.classList.add('is-visible'));
} else {
  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible');
          observer.unobserve(entry.target);
        }
      });
    },
    { threshold: 0.15, rootMargin: '0px 0px -40px 0px' }
  );

  revealTargets.forEach((el) => observer.observe(el));
}

// Chrome loses the Content-Disposition filename across the /releases/latest/download/
// redirect chain (two cross-origin hops) and names the file after the raw blob URL
// instead of "PulseFin.apk". GitHub's own release page links directly to the
// already-resolved asset URL (one hop), which downloads correctly everywhere. Resolve
// that same URL client-side via the (CORS-enabled) GitHub API and repoint the buttons
// at it; if the request fails for any reason, the static /latest/download/ href already
// in the HTML is left in place as a working fallback.
fetch('https://api.github.com/repos/Funnykid7/PulseFin/releases/latest')
  .then((res) => (res.ok ? res.json() : Promise.reject(res.status)))
  .then((release) => {
    const asset = release.assets.find((a) => a.name === 'PulseFin.apk');
    if (!asset) return;
    document.querySelectorAll('.js-apk-link').forEach((link) => {
      link.href = asset.browser_download_url;
    });
  })
  .catch(() => {});
