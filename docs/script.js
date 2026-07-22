const root = document.documentElement;
const themeToggle = document.querySelector('#themeToggle');
const releaseStatus = document.querySelector('#releaseStatus');
const releaseVersion = document.querySelector('#releaseVersion');
const releaseSize = document.querySelector('#releaseSize');
const downloadLinks = document.querySelectorAll('[data-download-link]');
const downloadTexts = document.querySelectorAll('[data-download-text]');
const localApkUrl = './downloads/KardLeaf.apk';
const latestReleaseApiUrl = 'https://api.github.com/repos/waikr/KardLeaf/releases/latest';

const savedTheme = localStorage.getItem('kardleaf-theme');
const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
if (savedTheme === 'dark' || (!savedTheme && prefersDark)) {
  root.dataset.theme = 'dark';
}

themeToggle?.addEventListener('click', () => {
  const nextTheme = root.dataset.theme === 'dark' ? 'light' : 'dark';
  if (nextTheme === 'dark') {
    root.dataset.theme = 'dark';
  } else {
    delete root.dataset.theme;
  }
  localStorage.setItem('kardleaf-theme', nextTheme);
});

const formatBytes = (bytes) => {
  if (!Number.isFinite(bytes) || bytes <= 0) return 'APK';
  const units = ['B', 'KB', 'MB', 'GB'];
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return `${(bytes / (1024 ** index)).toFixed(index > 1 ? 1 : 0)} ${units[index]}`;
};

const formatDate = (value) => {
  if (!value) return '';
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  }).format(new Date(value));
};

async function loadLatestRelease() {
  let localApkAvailable = false;
  let localApkSize = 0;

  try {
    const localResponse = await fetch(localApkUrl, {
      method: 'HEAD',
      cache: 'no-store'
    });
    localApkAvailable = localResponse.ok;
    localApkSize = Number(localResponse.headers.get('content-length')) || 0;
  } catch (error) {
    localApkAvailable = false;
  }

  try {
    const response = await fetch(latestReleaseApiUrl, {
      headers: { Accept: 'application/vnd.github+json' }
    });
    if (!response.ok) throw new Error(`GitHub API ${response.status}`);

    const release = await response.json();
    const apk = release.assets?.find((asset) => asset.name.toLowerCase().endsWith('.apk'));
    const version = release.name || release.tag_name || '最新版本';
    const date = formatDate(release.published_at);

    releaseVersion.textContent = version;
    if (apk) {
      downloadLinks.forEach((link) => { link.href = apk.browser_download_url; });
      downloadTexts.forEach((text) => { text.textContent = `下载 ${version}`; });
      releaseSize.textContent = formatBytes(apk.size);
      releaseStatus.textContent = `${version}${date ? ` · ${date}` : ''} · ${formatBytes(apk.size)} · GitHub Release`;
    } else if (localApkAvailable) {
      downloadLinks.forEach((link) => { link.href = localApkUrl; });
      if (localApkSize > 0) releaseSize.textContent = formatBytes(localApkSize);
      releaseStatus.textContent = `${version}${date ? ` · ${date}` : ''} · Release 未附带 APK，使用本站备用安装包`;
    } else {
      downloadLinks.forEach((link) => { link.href = release.html_url; });
      releaseStatus.textContent = `${version}${date ? ` · ${date}` : ''} · 前往 Releases 查看`;
    }
  } catch (error) {
    if (localApkAvailable) {
      downloadLinks.forEach((link) => { link.href = localApkUrl; });
      if (localApkSize > 0) releaseSize.textContent = formatBytes(localApkSize);
      releaseStatus.textContent = `GitHub 暂时无法访问 · 使用本站备用安装包${localApkSize > 0 ? ` · ${formatBytes(localApkSize)}` : ''}`;
    } else {
      releaseStatus.textContent = '前往 GitHub Releases 获取最新安装包';
    }
  }
}

loadLatestRelease();

document.querySelector('#year').textContent = new Date().getFullYear();

const observer = new IntersectionObserver((entries) => {
  entries.forEach((entry) => {
    if (entry.isIntersecting) {
      entry.target.classList.add('visible');
      observer.unobserve(entry.target);
    }
  });
}, { threshold: 0.12 });

document.querySelectorAll('.reveal').forEach((element) => observer.observe(element));

const screenshotTabs = document.querySelectorAll('.screenshot-tab');
const screenshotCards = document.querySelectorAll('.screenshot-card');

screenshotTabs.forEach((tab) => {
  tab.addEventListener('click', () => {
    screenshotTabs.forEach((item) => item.classList.remove('active'));
    tab.classList.add('active');
    const filter = tab.dataset.filter;
    screenshotCards.forEach((card) => {
      card.classList.toggle('hidden', filter !== 'all' && card.dataset.category !== filter);
    });
  });
});

const lightbox = document.querySelector('#lightbox');
const lightboxImage = lightbox.querySelector('img');
const lightboxTitle = lightbox.querySelector('p');
const lightboxClose = lightbox.querySelector('.lightbox-close');

screenshotCards.forEach((card) => {
  card.addEventListener('click', () => {
    lightboxImage.src = card.dataset.image;
    lightboxImage.alt = card.dataset.title;
    lightboxTitle.textContent = card.dataset.title;
    lightbox.showModal();
  });
});

lightboxClose.addEventListener('click', () => lightbox.close());
lightbox.addEventListener('click', (event) => {
  if (event.target === lightbox) lightbox.close();
});
