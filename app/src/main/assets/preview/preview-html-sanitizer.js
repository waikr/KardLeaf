(function (global) {
    'use strict';

    const SAFE_COLOR = /^#(?:[0-9a-f]{3}|[0-9a-f]{6})$/i;
    const SAFE_FONT_SIZE = /^(?:0\.8em|1.5em)$/i;

    function escapeHtml(value) {
        return String(value || '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function normalizeColor(value) {
        const color = String(value || '').trim().toLowerCase();
        if (!SAFE_COLOR.test(color)) return null;
        return color.length === 4
            ? '#' + color.slice(1).split('').map((part) => part + part).join('')
            : color;
    }

    function normalizeFontSize(value) {
        const size = String(value || '').trim().toLowerCase();
        return SAFE_FONT_SIZE.test(size) ? size : null;
    }

    function parseStyle(styleText) {
        const styles = {};
        for (const declaration of String(styleText || '').split(';')) {
            const value = declaration.trim();
            if (!value) continue;
            const colon = value.indexOf(':');
            if (colon <= 0 || value.indexOf(':', colon + 1) >= 0) return null;
            const name = value.slice(0, colon).trim().toLowerCase();
            const rawValue = value.slice(colon + 1).trim();
            let normalized = null;
            if (name === 'color' || name === 'background-color') normalized = normalizeColor(rawValue);
            else if (name === 'font-size') normalized = normalizeFontSize(rawValue);
            else return null;
            if (!normalized) return null;
            styles[name] = normalized;
        }
        return styles;
    }

    function sanitizeOpeningSpan(rawHtml) {
        const opening = /^<\s*span\b([^>]*)>$/i.exec(String(rawHtml || '').trim());
        if (!opening) return null;
        if (!opening[1].trim()) return '<span>';

        const style = /^\s+style\s*=\s*(["'])([^"'<>]*)\1\s*$/i.exec(opening[1]);
        if (!style) return null;
        const styles = parseStyle(style[2]);
        if (!styles) return null;

        const declarations = [];
        if (styles.color) declarations.push('color:' + styles.color);
        if (styles['background-color']) declarations.push('background-color:' + styles['background-color']);
        if (styles['font-size']) declarations.push('font-size:' + styles['font-size']);
        return declarations.length ? '<span style="' + declarations.join(';') + '">' : '<span>';
    }

    function sanitize(rawHtml) {
        const html = String(rawHtml || '');
        const trimmed = html.trim();
        if (/^<\s*\/\s*span\s*>$/i.test(trimmed)) return '</span>';

        const opening = sanitizeOpeningSpan(trimmed);
        if (opening) return opening;

        const paired = /^\s*(<\s*span\b[^>]*>)([\s\S]*)(<\s*\/\s*span\s*>)\s*$/i.exec(html);
        if (!paired) return escapeHtml(html);
        const safeOpening = sanitizeOpeningSpan(paired[1]);
        if (!safeOpening) return escapeHtml(html);
        return safeOpening + escapeHtml(paired[2]) + '</span>';
    }

    global.KardLeafPreviewHtml = { sanitize };
})(typeof window !== 'undefined' ? window : globalThis);
