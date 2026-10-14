const accounts = [
  ['hr.linyue', 'Lin Yue', 'HRBP'], ['hr.chenchen', 'Chen Chen', 'Recruiter'],
  ['dev.zhouhang', 'Zhou Hang', 'Backend Engineer'], ['dev.zhaoning', 'Zhao Ning', 'Platform Engineer'],
  ['sales.wanglei', 'Wang Lei', 'East China Sales'], ['sales.liuting', 'Liu Ting', 'Key Account Sales'],
  ['customer.zhangwei', 'Zhang Wei', 'Individual Customer'], ['customer.lina', 'Li Na', 'Individual Customer'],
];

let csrf;
async function loadCsrf() {
  const response = await fetch('/auth/csrf');
  const body = await response.json();
  csrf = body.data;
}

function renderAccounts() {
  const container = document.getElementById('demoAccounts');
  accounts.forEach(([username, name, role]) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'account';
    const title = document.createElement('strong');
    title.textContent = name + ' · ' + role;
    const detail = document.createElement('span');
    detail.textContent = username;
    button.append(title, detail);
    button.addEventListener('click', () => {
      document.getElementById('username').value = username;
      document.getElementById('password').focus();
    });
    container.appendChild(button);
  });
}

document.getElementById('loginForm').addEventListener('submit', async (event) => {
  event.preventDefault();
  const error = document.getElementById('loginError');
  const button = document.getElementById('loginButton');
  error.textContent = '';
  button.disabled = true;
  try {
    if (!csrf) await loadCsrf();
    const payload = new URLSearchParams();
    payload.set('username', document.getElementById('username').value.trim());
    payload.set('password', document.getElementById('password').value);
    const response = await fetch('/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded', [csrf.headerName]: csrf.token },
      body: payload,
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || 'Login failed');
    window.location.replace(body.redirect || '/');
  } catch (e) {
    error.textContent = e.message;
    await loadCsrf().catch(() => {});
  } finally {
    button.disabled = false;
  }
});

renderAccounts();
loadCsrf().catch(() => {
  document.getElementById('loginError').textContent = 'Authentication service is temporarily unavailable';
});
