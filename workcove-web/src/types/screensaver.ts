export type ScreensaverPresetId =
  | 'deep-forest'
  | 'graphite'
  | 'morning-mist'
  | 'deep-ocean'

export type ScreensaverId = ScreensaverPresetId | 'custom'

export interface ScreensaverPreset {
  id: ScreensaverPresetId
  name: string
  description: string
}

export interface ScreensaverSettings {
  selectedId: ScreensaverId
  customImageDataUrl: string
  customImageName: string
}

export const SCREENSAVER_PRESETS: ScreensaverPreset[] = [
  {
    id: 'deep-forest',
    name: '深林',
    description: '沉静深绿，适合专注后的短暂休息',
  },
  {
    id: 'graphite',
    name: '石墨',
    description: '低干扰深灰，保留最少的视觉信息',
  },
  {
    id: 'morning-mist',
    name: '晨雾',
    description: '柔和浅色，适合明亮的办公环境',
  },
  {
    id: 'deep-ocean',
    name: '深海',
    description: '安静蓝黑，减少长时间休息时的刺激',
  },
]
