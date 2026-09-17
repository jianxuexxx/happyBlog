import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getStorage, setStorage } from '../utils/storage'

const THEME_KEY = 'blog-theme'

/** 主题 store：亮/暗切换，localStorage 持久化，:root.dark 类驱动 tokens 变量切换 */
export const useThemeStore = defineStore('theme', () => {
  const isDark = ref<boolean>(getStorage<boolean>(THEME_KEY, false) ?? false)

  function apply() {
    document.documentElement.classList.toggle('dark', isDark.value)
  }

  function init() {
    apply()
  }

  function toggle() {
    isDark.value = !isDark.value
    setStorage(THEME_KEY, isDark.value)
    apply()
  }

  return { isDark, init, toggle }
})