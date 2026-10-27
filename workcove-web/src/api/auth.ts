import {
  get,
  postJson,
  postJsonNoContent,
  postNoContent,
} from '@/api/http'
import type {
  AuthUser,
  LoginInput,
  RegisterInput,
} from '@/types/auth'

export const getCurrentUser = () => get<AuthUser>('/auth/me')

export const sendRegistrationCode = (email: string) =>
  postJsonNoContent('/auth/email-codes', { email })

export const register = (input: RegisterInput) =>
  postJson<AuthUser>('/auth/register', input)

export const login = (input: LoginInput) =>
  postJson<AuthUser>('/auth/login', input)

export const logout = () => postNoContent('/auth/logout')
