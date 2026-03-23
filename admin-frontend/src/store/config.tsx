import React, { createContext, useContext, useState } from 'react'

interface ConfigCtx {
  baseUrl: string       // Python OCR 服务（比较、预览等）
  setBaseUrl: (url: string) => void
  javaBaseUrl: string   // Java Spring Boot 服务（流水线 pipeline API）
  setJavaBaseUrl: (url: string) => void
}

const Ctx = createContext<ConfigCtx>({
  baseUrl: 'http://localhost:8079',
  setBaseUrl: () => {},
  javaBaseUrl: 'http://localhost:8079',
  setJavaBaseUrl: () => {},
})

export function ConfigProvider({ children }: { children: React.ReactNode }) {
  const [baseUrl, setBaseUrl] = useState('http://localhost:8079')
  const [javaBaseUrl, setJavaBaseUrl] = useState('http://localhost:8079')
  return (
    <Ctx.Provider value={{ baseUrl, setBaseUrl, javaBaseUrl, setJavaBaseUrl }}>
      {children}
    </Ctx.Provider>
  )
}

export const useConfig = () => useContext(Ctx)
