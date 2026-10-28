export async function openWebsiteUrl(url: string): Promise<string | null> {
  if (window.workCoveDesktop) {
    return window.workCoveDesktop.openExternal(url)
  }

  const link = document.createElement('a')
  link.href = url
  link.target = '_blank'
  link.rel = 'noopener noreferrer'
  document.body.append(link)
  link.click()
  link.remove()
  return null
}
