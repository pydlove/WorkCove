export interface TestEnvironmentWebsiteSource {
  name: string
  url: string
}

export function testEnvironmentValuesFromWebsite(
  website: TestEnvironmentWebsiteSource,
) {
  return {
    name: website.name.trim(),
    url: website.url.trim(),
  }
}

export function redactTestEnvironmentCredentials(
  prompt: string,
  username: string,
  password: string,
) {
  return prompt
    .split(`用户名：${username}\n密码：${password}`)
    .join('登录凭据：仅在本次运行内存中传递，不写入会话记录')
}
