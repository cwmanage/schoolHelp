// 前后端报文加密（AES-256-GCM）：与网关 CryptoGlobalFilter 配套
// 密钥经构建期注入（.env 的 VITE_CRYPTO_KEY，64 位 hex）；未配置时加解密自动禁用（明文兼容）
// 边界说明：防 F12/抓包明文，不防专业逆向——安全底线仍是 HTTPS + 后端校验

const KEY_HEX = import.meta.env.VITE_CRYPTO_KEY || ''

export function cryptoEnabled() {
  return KEY_HEX.length === 64
}

function hexToBytes(hex) {
  const out = new Uint8Array(hex.length / 2)
  for (let i = 0; i < hex.length; i += 2) {
    out[i / 2] = parseInt(hex.slice(i, i + 2), 16)
  }
  return out
}

function bytesToB64(bytes) {
  let s = ''
  bytes.forEach((b) => { s += String.fromCharCode(b) })
  return btoa(s)
}

function b64ToBytes(b64) {
  const s = atob(b64)
  const out = new Uint8Array(s.length)
  for (let i = 0; i < s.length; i++) out[i] = s.charCodeAt(i)
  return out
}

async function getKey() {
  return crypto.subtle.importKey('raw', hexToBytes(KEY_HEX), 'AES-GCM', false, ['encrypt', 'decrypt'])
}

/** JSON 对象 → {payload: "iv.cipher"}；未启用返回 null（调用方走明文） */
export async function encryptJson(obj) {
  if (!cryptoEnabled()) return null
  const iv = crypto.getRandomValues(new Uint8Array(12))
  const key = await getKey()
  const cipher = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv },
    key,
    new TextEncoder().encode(JSON.stringify(obj))
  )
  return { payload: `${bytesToB64(iv)}.${bytesToB64(new Uint8Array(cipher))}` }
}

/** {payload: "iv.cipher"} → JSON 对象；非加密结构返回 null（调用方原样透传） */
export async function decryptPayload(payload) {
  if (!cryptoEnabled() || !payload || typeof payload !== 'string') return null
  const dot = payload.indexOf('.')
  if (dot <= 0) return null
  const key = await getKey()
  const plain = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: b64ToBytes(payload.slice(0, dot)) },
    key,
    b64ToBytes(payload.slice(dot + 1))
  )
  return JSON.parse(new TextDecoder().decode(plain))
}
