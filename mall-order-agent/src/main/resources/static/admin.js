let csrf;
let selectedUser;
let selectedIdentity;
let impersonationEnabled = false;

async function ensureCsrf() {
  if (csrf) return csrf;
  const response = await fetch('/auth/csrf');
  const body = await response.json();
  csrf = body.data;
  return csrf;
}

async function api(path, options = {}) {
  const headers = { Accept: 'application/json', ...(options.headers || {}) };
  if (options.method && options.method !== 'GET') {
    const token = await ensureCsrf();
    headers[token.headerName] = token.token;
    if (options.body) headers['Content-Type'] = 'application/json';
  }
  const response = await fetch(path, { ...options, headers });
  if (response.status === 401) {
    window.location.replace('/login.html');
    throw new Error('Session expired, please log in again');
  }
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.message || body.detail || `Request failed (HTTP ${response.status})`);
  return body.data;
}

const text = (value) => value == null || value === '' ? '—' : String(value);
const date = (value) => value ? new Date(value).toLocaleString('en-US', { hour12: false }) : '—';

function button(label, action, danger = false) {
  const element = document.createElement('button');
  element.type = 'button';
  element.className = danger ? 'link-button danger-link' : 'link-button';
  element.textContent = label;
  element.addEventListener('click', action);
  return element;
}

async function loadUsers() {
  const users = await api('/admin/users');
  const tbody = document.getElementById('userRows');
  tbody.replaceChildren();
  users.forEach((user) => {
    const tr = document.createElement('tr');
    [user.username, `${text(user.displayName)} · ${text(user.actorUserId)}`,
      [...user.roles].join(', '), user.enabled ? (user.lockedUntil ? 'Locked' : 'Enabled') : 'Disabled',
      date(user.lastLoginAt)].forEach((value) => {
      const td = document.createElement('td'); td.textContent = value; tr.appendChild(td);
    });
    const actions = document.createElement('td');
    actions.append(
      button(user.enabled ? 'Disable' : 'Enable', () => setEnabled(user, !user.enabled), user.enabled),
      button('Unlock', () => unlock(user)), button('Reset Password', () => openPasswordReset(user)),
    );
    if (impersonationEnabled && user.actorUserId && user.enabled) {
      actions.append(button('Enter Identity', () => openImpersonation(user)));
    }
    tr.appendChild(actions); tbody.appendChild(tr);
  });
}

async function loadIdentities() {
  const identities = await api('/admin/identities');
  const select = document.getElementById('newIdentity');
  select.replaceChildren();
  identities.forEach((identity) => {
    const option = document.createElement('option');
    option.value = identity.actorUserId;
    option.textContent = `${identity.displayName} · ${identity.category} · ${identity.actorUserId}`;
    select.appendChild(option);
  });
}

async function setEnabled(user, enabled) {
  await api(`/admin/users/${user.id}/enabled`, { method: 'POST', body: JSON.stringify({ enabled }) });
  document.getElementById('userNotice').textContent = enabled ? 'Account has been enabled' : 'Account has been disabled; existing sessions will be invalidated';
  await loadUsers();
}

async function unlock(user) {
  await api(`/admin/users/${user.id}/unlock`, { method: 'POST' });
  document.getElementById('userNotice').textContent = 'Account has been unlocked; existing sessions will be invalidated';
  await loadUsers();
}

function openPasswordReset(user) {
  selectedUser = user;
  document.getElementById('resetTarget').textContent = user.username;
  document.getElementById('resetPassword').value = '';
  document.getElementById('resetNotice').textContent = '';
  document.getElementById('passwordDialog').showModal();
}

function openImpersonation(user) {
  selectedIdentity = user;
  document.getElementById('impersonationTarget').textContent = `${user.displayName} · ${user.actorUserId}`;
  document.getElementById('impersonationPassword').value = '';
  document.getElementById('impersonationReason').value = '';
  document.getElementById('impersonationNotice').textContent = '';
  document.getElementById('impersonationDialog').showModal();
}

async function loadTokens() {
  const tokens = await api('/admin/tokens');
  const tbody = document.getElementById('tokenRows');
  tbody.replaceChildren();
  tokens.forEach((token) => {
    const tr = document.createElement('tr');
    [token.name, token.prefix, token.scopes, date(token.expiresAt), date(token.lastUsedAt)].forEach((value) => {
      const td = document.createElement('td'); td.textContent = value; tr.appendChild(td);
    });
    const actions = document.createElement('td');
    if (token.enabled && !token.revokedAt) actions.append(button('Revoke', () => revokeToken(token), true));
    else actions.textContent = 'Revoked';
    tr.appendChild(actions); tbody.appendChild(tr);
  });
}

async function revokeToken(token) {
  await api(`/admin/tokens/${token.id}/revoke`, { method: 'POST' });
  document.getElementById('tokenNotice').textContent = 'Token has been revoked';
  await loadTokens();
}

async function loadAudit() {
  const events = await api('/admin/audit?limit=100');
  const tbody = document.getElementById('auditRows');
  tbody.replaceChildren();
  events.forEach((event) => {
    const tr = document.createElement('tr');
    [date(event.createdAt), event.eventType, text(event.subjectFingerprint),
      text(event.resourceFingerprint), event.outcome, text(event.sourceIp)].forEach((value) => {
      const td = document.createElement('td'); td.textContent = value; tr.appendChild(td);
    });
    tbody.appendChild(tr);
  });
}

document.querySelectorAll('.tab').forEach((tab) => tab.addEventListener('click', () => {
  document.querySelectorAll('.tab').forEach((item) => item.classList.toggle('active', item === tab));
  ['users', 'tokens', 'audit'].forEach((name) => {
    document.getElementById(`${name}Panel`).classList.toggle('hidden', tab.dataset.tab !== name);
  });
  if (tab.dataset.tab === 'tokens') loadTokens().catch(showError);
  if (tab.dataset.tab === 'audit') loadAudit().catch(showError);
}));

document.getElementById('openCreateUser').addEventListener('click', () => document.getElementById('createUserForm').classList.remove('hidden'));
document.getElementById('cancelCreateUser').addEventListener('click', () => document.getElementById('createUserForm').classList.add('hidden'));
document.getElementById('createUserForm').addEventListener('submit', async (event) => {
  event.preventDefault();
  try {
    await api('/admin/users', { method: 'POST', body: JSON.stringify({
      username: document.getElementById('newUsername').value.trim(),
      actorUserId: document.getElementById('newIdentity').value,
      temporaryPassword: document.getElementById('newPassword').value,
    }) });
    event.target.reset(); event.target.classList.add('hidden');
    document.getElementById('userNotice').textContent = 'Account created; password must be changed on first login';
    await loadUsers();
  } catch (error) { document.getElementById('userNotice').textContent = error.message; }
});

document.getElementById('resetPasswordForm').addEventListener('submit', async (event) => {
  event.preventDefault();
  try {
    await api(`/admin/users/${selectedUser.id}/reset-password`, { method: 'POST', body: JSON.stringify({
      temporaryPassword: document.getElementById('resetPassword').value,
    }) });
    document.getElementById('passwordDialog').close();
    document.getElementById('userNotice').textContent = 'Temporary password has been set; existing sessions will be invalidated';
    await loadUsers();
  } catch (error) { document.getElementById('resetNotice').textContent = error.message; }
});
document.getElementById('cancelReset').addEventListener('click', () => document.getElementById('passwordDialog').close());
document.getElementById('cancelImpersonation').addEventListener('click', () => document.getElementById('impersonationDialog').close());
document.getElementById('impersonationForm').addEventListener('submit', async (event) => {
  event.preventDefault();
  try {
    const result = await api('/admin/impersonation', { method: 'POST', body: JSON.stringify({
      actorUserId: selectedIdentity.actorUserId,
      adminPassword: document.getElementById('impersonationPassword').value,
      reason: document.getElementById('impersonationReason').value.trim(),
    }) });
    window.location.replace(result.redirect || '/');
  } catch (error) { document.getElementById('impersonationNotice').textContent = error.message; }
});

document.getElementById('openCreateToken').addEventListener('click', () => document.getElementById('createTokenForm').classList.remove('hidden'));
document.getElementById('cancelCreateToken').addEventListener('click', () => document.getElementById('createTokenForm').classList.add('hidden'));
document.getElementById('createTokenForm').addEventListener('submit', async (event) => {
  event.preventDefault();
  try {
    const result = await api('/admin/tokens', { method: 'POST', body: JSON.stringify({
      name: document.getElementById('tokenName').value.trim(), scope: 'EVALUATION_ACT_AS',
      validDays: Number(document.getElementById('tokenDays').value),
    }) });
    document.getElementById('rawToken').textContent = result.token;
    document.getElementById('oneTimeToken').classList.remove('hidden');
    event.target.reset(); event.target.classList.add('hidden');
    await loadTokens();
  } catch (error) { document.getElementById('tokenNotice').textContent = error.message; }
});
document.getElementById('copyToken').addEventListener('click', async () => navigator.clipboard.writeText(document.getElementById('rawToken').textContent));
document.getElementById('refreshAudit').addEventListener('click', () => loadAudit().catch(showError));
document.getElementById('logoutButton').addEventListener('click', async () => {
  await api('/auth/logout', { method: 'POST' }); window.location.replace('/login.html');
});

function showError(error) {
  const visible = [...document.querySelectorAll('.admin-section')].find((panel) => !panel.classList.contains('hidden'));
  const notice = visible?.querySelector('.notice');
  if (notice) notice.textContent = error.message;
}

async function init() {
  try {
    await ensureCsrf();
    const me = await api('/auth/me');
    impersonationEnabled = Boolean(me.demoImpersonationEnabled);
    document.getElementById('adminName').textContent = me.displayName || me.username;
    await Promise.all([loadUsers(), loadIdentities()]);
  } catch (error) { showError(error); }
}

init();
