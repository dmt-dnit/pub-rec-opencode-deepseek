export interface UserInfo {
  email: string;
  name: string;
  role: string;
}

export interface LoginResponse {
  token: string;
  email: string;
  name: string;
  role: string;
}

export interface ArticleEvent {
  id: string;
  title: string;
  author: string;
  publishedAt: string;
}
