// Follows Joplin packages/renderer/MdToHtml/renderMedia.ts:
// ready resources + MIME dispatch + native HTML media controls; no raw note HTML.
(function () {
    const remoteVideoTypes = { mp4: 'video/mp4', m4v: 'video/mp4', webm: 'video/webm', ogv: 'video/ogg', mov: 'video/quicktime' };
    const decode = value => { try { return decodeURIComponent(value); } catch (_) { return value; } };
    const trace = message => console.log('[KardLeafPreviewMedia] ' + message);
    const traceError = (message, error) => console.error('[KardLeafPreviewMedia] ' + message, error || '');
    const referenceSummary = value => {
        const decoded = decode(String(value || ''));
        const path = decoded.split('#', 1)[0];
        const leaf = path.slice(path.lastIndexOf('/') + 1);
        const extension = leaf.includes('.') ? leaf.split('.').pop().toLowerCase() : '<none>';
        return 'refLen=' + decoded.length + ' ext=' + extension;
    };
    window.pausePreviewMedia = function () {
        document.querySelectorAll('#content video, #content audio').forEach(media => media.pause());
    };
    document.addEventListener('visibilitychange', () => {
        if (document.hidden) window.pausePreviewMedia();
    });
    window.attachPreviewMedia = function () {
        let resources;
        try {
            resources = JSON.parse(window.Android?.getAttachments?.() || '{}') || {};
        } catch (error) {
            traceError('attachments parse failed', error);
            return;
        }
        const links = document.querySelectorAll('#content a[data-preview-attachment]:not(.kardleaf-wikilink)');
        trace('attach start links=' + links.length + ' resources=' + Object.keys(resources).length);
        links.forEach((link, index) => {
            const original = link.dataset.attachmentOriginal || link.getAttribute('href') || '';
            const resolved = resources[decode(original)];
            trace('candidate index=' + index + ' ' + referenceSummary(original) + ' resolved=' + Boolean(resolved));
            let url;
            try { url = new URL(resolved || original); } catch (error) {
                traceError('candidate invalid_url index=' + index + ' ' + referenceSummary(original), error);
                return;
            }
            const directContentMedia = Boolean(resolved) && url.protocol === 'content:';
            if (url.protocol !== 'https:' && url.protocol !== 'http:' && !directContentMedia) {
                trace('candidate skipped index=' + index + ' reason=protocol protocol=' + url.protocol);
                return;
            }
            const mime = resolved
                ? (url.searchParams.get('mime') || '')
                : remoteVideoTypes[url.pathname.split('.').pop().toLowerCase()];
            if (resolved) {
                link.dataset.attachmentOriginal = original;
                link.href = resolved;
            }
            if (!mime || (!mime.startsWith('video/') && !mime.startsWith('audio/'))) {
                trace('candidate skipped index=' + index + ' reason=non_media mime=' + (mime || '<none>'));
                return;
            }
            if (link.dataset.mediaUrl === url.href) {
                trace('candidate skipped index=' + index + ' reason=already_attached mime=' + mime);
                return;
            }
            link.previousElementSibling?.matches('.preview-media') && link.previousElementSibling.remove();
            link.dataset.mediaUrl = url.href;
            // Joplin preserves validated temporal fragments such as #t=10,20.
            const fragment = original.match(/#t=[0-9:,]+$/)?.[0] || '';
            const wrapper = document.createElement('span');
            wrapper.className = 'preview-media';
            const media = document.createElement(mime.startsWith('video/') ? 'video' : 'audio');
            media.controls = true;
            media.preload = 'metadata';
            media.setAttribute('playsinline', '');
            media.setAttribute('aria-label', link.textContent || '媒体附件');
            const source = document.createElement('source');
            source.src = url.href.replace(/#.*$/, '') + fragment;
            source.type = mime === 'audio/x-flac' ? 'audio/flac' : mime;
            const status = document.createElement('span');
            status.className = 'preview-media-status';
            status.setAttribute('role', 'status');
            status.textContent = '正在加载媒体…';
            const failed = () => { status.textContent = '无法播放此媒体，可点击下方附件链接打开'; };
            const failWithTrace = phase => {
                const mediaError = media.error;
                trace('media error index=' + index + ' phase=' + phase + ' ' + referenceSummary(original) +
                    ' code=' + (mediaError?.code ?? 0) + ' network=' + media.networkState + ' ready=' + media.readyState);
                failed();
            };
            source.addEventListener('error', () => failWithTrace('source'));
            media.addEventListener('error', () => failWithTrace('element'));
            media.addEventListener('loadedmetadata', () => {
                status.textContent = '';
                trace('media metadata index=' + index + ' mime=' + mime + ' duration=' + media.duration +
                    ' size=' + media.videoWidth + 'x' + media.videoHeight);
            });
            media.addEventListener('waiting', () => {
                status.textContent = '正在缓冲…';
                trace('media waiting index=' + index + ' ready=' + media.readyState + ' network=' + media.networkState);
            });
            media.addEventListener('playing', () => {
                status.textContent = '';
                trace('media playing index=' + index + ' currentTime=' + media.currentTime);
            });
            media.addEventListener('play', () => trace('media play index=' + index + ' currentTime=' + media.currentTime));
            media.addEventListener('pause', () => trace('media pause index=' + index + ' currentTime=' + media.currentTime));
            media.addEventListener('stalled', () => trace('media stalled index=' + index + ' ready=' + media.readyState + ' network=' + media.networkState));
            media.addEventListener('canplay', () => { status.textContent = ''; });
            for (const event of ['touchstart', 'touchend', 'pointerdown', 'click', 'dblclick']) {
                wrapper.addEventListener(event, e => {
                    e.stopPropagation();
                    window.Android?.onPreviewControlTouched?.();
                }, { passive: true });
            }
            media.appendChild(source);
            wrapper.append(media, status);
            link.before(wrapper);
            trace('player created index=' + index + ' kind=' + media.tagName.toLowerCase() + ' mime=' + mime +
                ' ' + referenceSummary(original));
        });
        trace('attach done');
    };
})();
