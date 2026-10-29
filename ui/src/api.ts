let session = '';
export async function api(path: string, body: unknown) {
  const response = await fetch(`/local/v1/${path}`, {method: 'POST', headers: {'Content-Type':'application/json', 'X-Local-Session': session}, body: JSON.stringify(body)});
  const data = await response.json().catch(() => ({error:{message:`HTTP ${response.status}`}}));
  if (!response.ok) throw new Error(data.error?.message || `HTTP ${response.status}`);
  return data;
}
export async function connect() {
  const bootstrap = new URLSearchParams(location.hash.slice(1)).get('bootstrap');
  history.replaceState(null, '', location.pathname);
  if (!bootstrap) throw new Error('서버 시작 시 출력된 전체 URL로 접속하세요. 새로고침한 경우 서버를 재시작하세요.');
  session = (await api('session', {bootstrap})).session;
}
export function rules(success: string, failure: string) { return {'2xx':success,'4xx':failure,'5xx':failure}; }
