function autofillError(message, statusCode = 409) {
  const error = new Error(message)
  error.statusCode = statusCode
  return error
}

function encodedText(value) {
  return Buffer.from(value, 'utf8').toString('base64')
}

function buildCredentialFillScript(username, password) {
  return `(() => {
    const decode = (value) => {
      const binary = atob(value)
      const bytes = Uint8Array.from(binary, (character) =>
        character.charCodeAt(0)
      )
      return new TextDecoder().decode(bytes)
    }
    const username = decode(${JSON.stringify(encodedText(username))})
    const password = decode(${JSON.stringify(encodedText(password))})
    const isVisible = (element) => {
      if (!element || element.disabled || element.readOnly) return false
      const style = window.getComputedStyle(element)
      const rect = element.getBoundingClientRect()
      return (
        style.display !== 'none' &&
        style.visibility !== 'hidden' &&
        Number(style.opacity || 1) > 0 &&
        rect.width > 0 &&
        rect.height > 0
      )
    }
    const setValue = (element, value) => {
      element.focus()
      const setter = Object.getOwnPropertyDescriptor(
        HTMLInputElement.prototype,
        'value'
      )?.set
      if (setter) setter.call(element, value)
      else element.value = value
      element.dispatchEvent(
        new InputEvent('input', {
          bubbles: true,
          inputType: 'insertText',
          data: value,
        })
      )
      element.dispatchEvent(new Event('change', { bubbles: true }))
    }
    const documents = [document]
    for (const frame of Array.from(document.querySelectorAll('iframe'))) {
      try {
        if (frame.contentDocument) documents.push(frame.contentDocument)
      } catch {
        // Cross-origin frames cannot be inspected.
      }
    }
    const passwordFields = documents
      .flatMap((root) =>
        Array.from(root.querySelectorAll('input[type="password"]'))
      )
      .filter(isVisible)
    const passwordField = passwordFields[0]
    if (!passwordField) {
      return {
        filledUsername: false,
        filledPassword: false,
        passwordFieldCount: 0,
      }
    }
    const fieldRoot =
      passwordField.form || passwordField.ownerDocument || document
    const usernameCandidates = Array.from(
      fieldRoot.querySelectorAll(
        'input:not([type]), input[type="text"], input[type="email"], input[type="tel"]'
      )
    ).filter(isVisible)
    const score = (element) => {
      const type = String(element.type || '').toLowerCase()
      const labels = Array.from(element.labels || [])
        .map((label) => label.innerText || label.textContent || '')
        .join(' ')
      const copy = [
        element.name,
        element.id,
        element.placeholder,
        element.getAttribute('aria-label'),
        element.autocomplete,
        labels,
      ]
        .filter(Boolean)
        .join(' ')
      let value = 0
      if (/username|user-name/i.test(element.autocomplete || '')) value += 80
      if (/user|account|login|email|phone|mobile|name|账号|用户|邮箱|手机|登录/i.test(copy)) {
        value += 40
      }
      if (type === 'email') value += 20
      if (type === 'tel') value += 10
      return value
    }
    const usernameField = usernameCandidates.sort(
      (left, right) => score(right) - score(left)
    )[0]
    if (usernameField) setValue(usernameField, username)
    setValue(passwordField, password)
    return {
      filledUsername: Boolean(usernameField),
      filledPassword: true,
      passwordFieldCount: passwordFields.length,
    }
  })()`
}

async function fillCredentialsIntoContents(contents, payload) {
  if (!contents || typeof contents.executeJavaScript !== 'function') {
    throw autofillError('内置浏览器尚未打开', 409)
  }
  const username =
    typeof payload?.username === 'string' ? payload.username.trim() : ''
  const password =
    typeof payload?.password === 'string' ? payload.password : ''
  if (!username) throw autofillError('密码库用户名不能为空', 400)
  if (!password) throw autofillError('密码库密码不能为空', 400)
  if (username.length > 256 || password.length > 1024) {
    throw autofillError('密码库登录信息长度无效', 400)
  }

  let current
  try {
    current = new URL(contents.getURL())
  } catch {
    throw autofillError('当前页面地址无效，无法填充登录信息')
  }
  if (current.protocol !== 'http:' && current.protocol !== 'https:') {
    throw autofillError('当前页面不是可填充的网页')
  }
  const expectedOrigin =
    typeof payload?.origin === 'string' ? payload.origin.trim() : ''
  if (expectedOrigin && current.origin !== expectedOrigin) {
    throw autofillError(
      '页面已跳转到其他网站，请重新选择密码库条目',
      409,
    )
  }

  const result = await contents.executeJavaScript(
    buildCredentialFillScript(username, password),
    true,
  )
  if (!result?.filledPassword) {
    throw autofillError('当前页面没有检测到可填写的登录密码框')
  }
  return {
    filledUsername: Boolean(result.filledUsername),
    filledPassword: true,
    url: current.toString(),
  }
}

module.exports = {
  buildCredentialFillScript,
  fillCredentialsIntoContents,
}
