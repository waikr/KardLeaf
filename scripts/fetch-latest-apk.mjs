import { mkdir, writeFile } from 'node:fs/promises';

const api =
  'https://api.github.com/repos/waikr/KardLeaf/releases/latest';

const releaseResponse = await fetch(api, {
  headers: {
    Accept: 'application/vnd.github+json',
    'User-Agent': 'KardLeaf-EdgeOne-Build'
  }
});

if (!releaseResponse.ok) {
  throw new Error(`获取 Release 失败：${releaseResponse.status}`);
}

const release = await releaseResponse.json();

const apk = release.assets?.find(asset =>
  asset.name.toLowerCase().endsWith('.apk')
);

if (!apk) {
  throw new Error('最新 Release 中没有找到 APK');
}

console.log(`最新版本：${release.tag_name}`);
console.log(`APK：${apk.name}`);

const apkResponse = await fetch(apk.browser_download_url, {
  redirect: 'follow',
  headers: {
    'User-Agent': 'KardLeaf-EdgeOne-Build'
  }
});

if (!apkResponse.ok) {
  throw new Error(`下载 APK 失败：${apkResponse.status}`);
}

const data = Buffer.from(await apkResponse.arrayBuffer());

await mkdir('docs/downloads', { recursive: true });
await writeFile('docs/downloads/KardLeaf.apk', data);

console.log(
  `下载完成：docs/downloads/KardLeaf.apk (${data.length} bytes)`
);
