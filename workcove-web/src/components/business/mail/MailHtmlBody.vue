<script setup lang="ts">
import DOMPurify from 'dompurify'
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
} from 'vue'

const props = defineProps<{
  html: string
  title: string
}>()

const themeRevision = ref(0)
let themeObserver: MutationObserver | null = null

const documentHtml = computed(() => {
  themeRevision.value
  const safeHtml = DOMPurify.sanitize(props.html, {
    ALLOWED_TAGS: [
      'a', 'abbr', 'b', 'blockquote', 'body', 'br', 'caption',
      'center', 'code', 'col', 'colgroup', 'dd', 'del', 'div',
      'dl', 'dt', 'em', 'font', 'h1', 'h2', 'h3', 'h4', 'h5',
      'h6', 'head', 'hr', 'html', 'i', 'img', 'ins', 'kbd', 'li',
      'mark', 'ol', 'p', 'pre', 'q', 's', 'small', 'span',
      'strike', 'strong', 'style', 'sub', 'sup', 'table', 'tbody',
      'td', 'tfoot', 'th', 'thead', 'tr', 'u', 'ul',
    ],
    ALLOWED_ATTR: [
      'abbr', 'align', 'alt', 'aria-label', 'background', 'bgcolor',
      'border', 'cellpadding', 'cellspacing', 'class', 'color',
      'colspan', 'dir', 'face', 'height', 'href', 'id', 'lang',
      'name', 'rel', 'role', 'rowspan', 'sizes', 'span', 'src',
      'srcset', 'style', 'summary', 'target', 'title', 'valign',
      'width',
    ],
    ALLOW_DATA_ATTR: false,
    FORBID_TAGS: [
      'audio', 'base', 'button', 'embed', 'form', 'frame',
      'frameset', 'iframe', 'input', 'link', 'meta', 'object',
      'option', 'script', 'select', 'textarea', 'video',
    ],
  })
  const template = document.createElement('template')
  template.innerHTML = safeHtml

  template.content.querySelectorAll('a').forEach((link) => {
    link.target = '_blank'
    link.rel = 'noopener noreferrer nofollow'
  })
  template.content.querySelectorAll('img').forEach((image) => {
    const source = image.getAttribute('src')?.trim() ?? ''
    if (!source || source.toLocaleLowerCase('en-US').startsWith('cid:')) {
      const placeholder = document.createElement('span')
      placeholder.className = 'mail-inline-image-placeholder'
      placeholder.textContent = image.alt
        ? `内嵌图片：${image.alt}`
        : '内嵌图片'
      image.replaceWith(placeholder)
      return
    }
    image.loading = 'lazy'
    image.decoding = 'async'
    image.referrerPolicy = 'no-referrer'
  })

  const canvas = cssValue('--canvas-raised', '#ffffff')
  const text = cssValue('--text', '#1f2329')
  const muted = cssValue('--text-muted', '#626a73')
  const accent = cssValue('--accent', '#246bfd')
  const line = cssValue('--line-soft', '#e5e7eb')
  const fontSans = cssValue(
    '--font-sans',
    '-apple-system, BlinkMacSystemFont, sans-serif',
  )
  const fontMono = cssValue('--font-mono', 'ui-monospace, monospace')

  return `<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="referrer" content="no-referrer">
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data: http: https:; style-src 'unsafe-inline'; font-src data:; script-src 'none'; connect-src 'none'; frame-src 'none'; media-src 'none'">
  <style>
    :root { color-scheme: light; }
    html, body {
      min-height: 100%;
      margin: 0;
      padding: 0;
      color: ${text};
      background: ${canvas};
      font-family: var(--font-sans, ${fontSans});
      font-size: 14px;
      line-height: 1.72;
      overflow-wrap: anywhere;
    }
    body { padding: 4px; }
    a { color: ${accent}; text-decoration: underline; text-underline-offset: 2px; }
    img { max-width: 100% !important; height: auto !important; }
    table { max-width: 100% !important; }
    pre, code { font-family: var(--font-mono, ${fontMono}); white-space: pre-wrap; }
    blockquote { margin: 12px 0; padding-left: 12px; color: ${muted}; border-left: 2px solid ${line}; }
    .mail-inline-image-placeholder {
      display: inline-flex;
      padding: 2px 7px;
      color: ${muted};
      font-size: 12px;
      background: ${line};
      border-radius: 8px;
    }
  </style>
</head>
<body>${template.innerHTML}</body>
</html>`
})

function cssValue(name: string, fallback: string) {
  return getComputedStyle(document.documentElement)
    .getPropertyValue(name)
    .trim() || fallback
}

onMounted(() => {
  themeObserver = new MutationObserver(() => {
    themeRevision.value += 1
  })
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['data-theme'],
  })
})

onBeforeUnmount(() => {
  themeObserver?.disconnect()
  themeObserver = null
})
</script>

<template>
  <iframe
    class="mail-html-frame"
    :title="title || '邮件正文'"
    :srcdoc="documentHtml"
    sandbox="allow-popups allow-popups-to-escape-sandbox"
    referrerpolicy="no-referrer"
  />
</template>

<style scoped>
.mail-html-frame {
  display: block;
  width: 100%;
  min-height: 420px;
  height: 100%;
  background: var(--canvas-raised);
  border: 0;
}
</style>
