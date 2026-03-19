import React, { createContext, useContext, useState } from 'react'

interface ConfigCtx {
  baseUrl: string
  setBaseUrl: (url: string) => void
}

const Ctx = createContext<ConfigCtx>({
  baseUrl: 'http://localhost:8099',
  setBaseUrl: () => {},
})

export function ConfigProvider({ children }: { children: React.ReactNode }) {
  const [baseUrl, setBaseUrl] = useState('http://localhost:8099')
  return <Ctx.Provider value={{ baseUrl, setBaseUrl }}>{children}</Ctx.Provider>
}

export const useConfig = () => useContext(Ctx)
