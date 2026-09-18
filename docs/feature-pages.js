const root = document.documentElement;
const themeToggle = document.querySelector('#themeToggle');
const savedTheme = localStorage.getItem('kardleaf-theme');
const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
if (savedTheme === 'dark' || (!savedTheme && prefersDark)) root.dataset.theme = 'dark';

themeToggle?.addEventListener('click', () => {
  const dark = root.dataset.theme !== 'dark';
  if (dark) root.dataset.theme = 'dark'; else delete root.dataset.theme;
  localStorage.setItem('kardleaf-theme', dark ? 'dark' : 'light');
});

const year = document.querySelector('#year');
if (year) year.textContent = new Date().getFullYear();

document.querySelectorAll('.doc-toc a').forEach((link) => {
  link.addEventListener('click', () => {
    const id = link.getAttribute('href');
    if (!id?.startsWith('#')) return;
    document.querySelector(id)?.scrollIntoView({behavior: 'smooth', block: 'start'});
  });
});
