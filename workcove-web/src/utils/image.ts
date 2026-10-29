interface SquareImageOptions {
  maxBytes: number
  dimension: number
  sizeLabel: string
}

const ACCEPTED_IMAGE_TYPES = new Set([
  'image/jpeg',
  'image/png',
  'image/webp',
])

export async function fileToSquareWebp(
  file: File,
  options: SquareImageOptions,
) {
  if (!ACCEPTED_IMAGE_TYPES.has(file.type)) {
    throw new Error('请选择 JPG、PNG 或 WebP 图片')
  }
  if (file.size > options.maxBytes) {
    throw new Error(`图片文件不能超过 ${options.sizeLabel}`)
  }

  const bitmap = await window.createImageBitmap(file)
  const side = Math.min(bitmap.width, bitmap.height)
  const sourceX = Math.max(0, (bitmap.width - side) / 2)
  const sourceY = Math.max(0, (bitmap.height - side) / 2)
  const canvas = document.createElement('canvas')
  canvas.width = options.dimension
  canvas.height = options.dimension

  const context = canvas.getContext('2d')
  if (!context) {
    bitmap.close()
    throw new Error('当前环境无法处理图片')
  }

  context.imageSmoothingEnabled = true
  context.imageSmoothingQuality = 'high'
  context.drawImage(
    bitmap,
    sourceX,
    sourceY,
    side,
    side,
    0,
    0,
    options.dimension,
    options.dimension,
  )
  bitmap.close()

  return canvas.toDataURL('image/webp', 0.86)
}

export async function fileToScreensaverWebp(file: File) {
  if (!ACCEPTED_IMAGE_TYPES.has(file.type)) {
    throw new Error('请选择 JPG、PNG 或 WebP 图片')
  }
  if (file.size > 12 * 1024 * 1024) {
    throw new Error('屏保图片文件不能超过 12MB')
  }

  const bitmap = await window.createImageBitmap(file)
  const maximumSide = 2560
  const scale = Math.min(
    1,
    maximumSide / Math.max(bitmap.width, bitmap.height),
  )
  const width = Math.max(1, Math.round(bitmap.width * scale))
  const height = Math.max(1, Math.round(bitmap.height * scale))
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height

  const context = canvas.getContext('2d')
  if (!context) {
    bitmap.close()
    throw new Error('当前环境无法处理屏保图片')
  }

  context.imageSmoothingEnabled = true
  context.imageSmoothingQuality = 'high'
  context.drawImage(bitmap, 0, 0, width, height)
  bitmap.close()

  const maximumDataUrlLength = 5 * 1024 * 1024
  for (const quality of [0.82, 0.72, 0.62, 0.52]) {
    const dataUrl = canvas.toDataURL('image/webp', quality)
    if (dataUrl.length <= maximumDataUrlLength) {
      return dataUrl
    }
  }
  throw new Error('处理后的屏保图片仍然过大，请换一张尺寸更小的图片')
}
