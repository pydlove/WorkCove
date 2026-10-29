import { defineStore } from 'pinia'

import {
  defaultScreensaverSettings,
  loadLocalScreensaver,
  MAX_SCREENSAVER_DATA_URL_LENGTH,
  saveLocalScreensaver,
} from '@/services/localScreensaver'
import type {
  ScreensaverId,
  ScreensaverSettings,
} from '@/types/screensaver'

export const useScreensaverStore = defineStore('screensaver', {
  state: () => ({
    ownerEmail: '',
    ...defaultScreensaverSettings(),
  }),
  getters: {
    resolvedId: (state): ScreensaverId =>
      state.selectedId === 'custom' && !state.customImageDataUrl
        ? 'deep-forest'
        : state.selectedId,
    hasCustomImage: (state) =>
      Boolean(state.customImageDataUrl),
  },
  actions: {
    load(email: string) {
      const normalizedEmail =
        email.trim().toLocaleLowerCase('en-US')
      if (!normalizedEmail) {
        this.ownerEmail = ''
        Object.assign(this, defaultScreensaverSettings())
        return
      }
      if (this.ownerEmail === normalizedEmail) return
      this.ownerEmail = normalizedEmail
      Object.assign(
        this,
        loadLocalScreensaver(normalizedEmail),
      )
    },
    persist() {
      if (!this.ownerEmail) return
      saveLocalScreensaver(this.ownerEmail, {
        selectedId: this.selectedId,
        customImageDataUrl: this.customImageDataUrl,
        customImageName: this.customImageName,
      })
    },
    select(id: ScreensaverId) {
      if (id === 'custom' && !this.customImageDataUrl) return
      this.selectedId = id
      this.persist()
    },
    saveCustom(dataUrl: string, name: string) {
      if (
        dataUrl.length > MAX_SCREENSAVER_DATA_URL_LENGTH ||
        !/^data:image\/(?:jpeg|png|webp);base64,/i.test(dataUrl)
      ) {
        throw new Error('屏保图片数据无效或过大')
      }
      this.customImageDataUrl = dataUrl
      this.customImageName = name.trim().slice(0, 255)
      this.selectedId = 'custom'
      this.persist()
    },
    removeCustom() {
      this.customImageDataUrl = ''
      this.customImageName = ''
      if (this.selectedId === 'custom') {
        this.selectedId = 'deep-forest'
      }
      this.persist()
    },
  },
})
