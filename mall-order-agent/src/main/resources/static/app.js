const $ = (id) => document.getElementById(id);

const state = {
  currentPersona: null,
  conversationId: createConversationId(),
  awaitingConfirm: false,
  pendingThreadId: null,
  sending: false,
  csrf: null,
};

const CATEGORY_META = {
  HR: { label: 'HR', workspace: 'Employee Knowledge', eyebrow: 'HR Workspace' },
  ENGINEERING: { label: 'Engineering', workspace: 'Technical Knowledge', eyebrow: 'Engineering Workspace' },
  SALES: { label: 'Sales', workspace: 'Customer Orders', eyebrow: 'Sales Workspace' },
  CUSTOMER: { label: 'Customer', workspace: 'My Orders', eyebrow: 'Customer Workspace' },
};

const CAPABILITY_LABELS = {
  KNOWLEDGE_SEARCH: 'Knowledge Search', OWN_ORDER_READ: 'Own Orders',
  ASSIGNED_ORDER_READ: 'Assigned Customer Orders', ORDER_CANCEL: 'Cancel Order',
  AFTER_SALES_CREATE: 'Initiate After-Sales',
};

const STATUS_MAP = {
  0: { text: 'Pending Payment', cls: 'status-pending' }, 1: { text: 'Paid', cls: 'status-paid' },
  2: { text: 'Shipped', cls: 'status-shipped' }, 3: { text: 'Completed', cls: 'status-completed' },
  4: { text: 'Cancelled', cls: 'status-cancelled' },
};

function createConversationId() {
  const suffix = window.crypto?.randomUUID ? window.crypto.randomUUID()
    : Date.now() + '-' + Math.random().toString(16).slice(2);
  return 'web-' + suffix;
}

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined && text !== null) node.textContent = text;
  return node;
}

async function loadCsrf() {
  const response = await fetch('/auth/csrf');
  const body = await response.json();
  state.csrf = body.data;
}

async function api(path, options = {}) {
  const request = { ...options, headers: { ...(options.headers || {}) } };
  const method = (request.method || 'GET').toUpperCase();
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    if (!state.csrf) await loadCsrf();
    request.headers[state.csrf.headerName] = state.csrf.token;
  }
  const response = await fetch(path, request);
  if (response.status === 401) {
    window.location.replace('/login.html');
    throw new Error('Session expired, please log in again');
  }
  if (response.status === 428) {
    window.location.replace('/change-password.html');
    throw new Error('Password change required before continuing');
  }
  let body;
  try { body = await response.json(); } catch { throw new Error('Service returned an unparsable response'); }
  if (!response.ok || body.code !== 200) throw new Error(body.message || ('HTTP ' + response.status));
  return body.data;
}

function appendMessage(role, text) {
  const message = el('div', 'message ' + role, text);
  $('messages').appendChild(message);
  $('messages').scrollTop = $('messages').scrollHeight;
}

function setSending(active) {
  state.sending = active;
  $('sendBtn').disabled = active;
  $('refreshWorkspace').disabled = active;
}

function updateComposer() {
  $('queryInput').placeholder = state.awaitingConfirm ? 'Reply "confirm" or "cancel"' : 'Enter a question';
}

function updateIdentity() {
  const persona = state.currentPersona;
  $('currentAvatar').textContent = persona.displayName.slice(0, 1);
  $('currentPersonaName').textContent = persona.displayName;
  $('currentPersonaMeta').textContent = (CATEGORY_META[persona.category]?.label || persona.category)
    + ' · ' + persona.actorUserId;
  $('workspaceEyebrow').textContent = CATEGORY_META[persona.category]?.eyebrow || 'Workspace';
  $('workspaceTitle').textContent = CATEGORY_META[persona.category]?.workspace || 'Workspace';
  renderSuggestions(persona.suggestions || []);
  renderPersonaSummary(persona);
}

function renderSuggestions(suggestions) {
  const container = $('suggestions');
  container.replaceChildren();
  suggestions.slice(0, 3).forEach((suggestion) => {
    const button = el('button', 'suggestion-button', suggestion);
    button.type = 'button';
    button.addEventListener('click', () => handleUserInput(suggestion));
    container.appendChild(button);
  });
}

function renderPersonaSummary(persona) {
  const summary = $('personaSummary');
  summary.replaceChildren();
  const title = el('div', 'persona-summary-title');
  title.append(el('strong', '', persona.jobTitle), el('span', '', persona.department));
  summary.append(title, el('p', '', persona.description));
  const capabilities = el('div', 'capability-list');
  (persona.capabilities || []).forEach((capability) =>
    capabilities.appendChild(el('span', 'capability', CAPABILITY_LABELS[capability] || capability)));
  summary.appendChild(capabilities);
}

function formatDate(value) {
  if (!value) return '-';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString('en-US');
}

function renderOrder(order) {
  const status = STATUS_MAP[order.orderStatus] || { text: 'Unknown', cls: 'status-completed' };
  const button = el('button', 'order-item');
  button.type = 'button';
  const top = el('span', 'order-top');
  top.append(el('strong', '', order.orderId || '-'), el('span', 'order-status ' + status.cls, status.text));
  const middle = el('span', 'order-middle');
  middle.append(el('strong', '', '¥' + (order.totalAmount ?? '-')), el('span', '', formatDate(order.orderTime)));
  const product = order.orderDetails?.[0]?.productName;
  const details = el('span', 'order-details', product || order.shippingAddress || 'Order Details');
  const contact = el('span', 'order-contact', [order.shippingAddress, order.contactPhone].filter(Boolean).join(' · '));
  button.append(top, middle, details);
  if (contact.textContent) button.appendChild(contact);
  button.addEventListener('click', () => handleUserInput('Query details for order ' + order.orderId));
  return button;
}

function renderCustomerOrders(customer) {
  const section = el('section', 'customer-orders');
  const heading = el('div', 'customer-heading');
  heading.append(el('h3', '', customer.customerName), el('span', '', customer.customerUserId));
  section.appendChild(heading);
  if (!customer.orders?.length) section.appendChild(el('p', 'empty-state compact', 'No orders yet'));
  else customer.orders.forEach((order) => section.appendChild(renderOrder(order)));
  return section;
}

function renderWorkspace(workspace) {
  const content = $('workspaceContent');
  content.replaceChildren();
  if (workspace.customers?.length) {
    workspace.customers.forEach((customer) => content.appendChild(renderCustomerOrders(customer)));
    return;
  }
  const scopeSection = el('section', 'knowledge-section');
  scopeSection.appendChild(el('h3', '', 'Available Knowledge Scopes'));
  const scopes = el('div', 'scope-list');
  (workspace.knowledgeScopes || []).forEach((scope) => scopes.appendChild(el('span', 'scope', scope)));
  scopeSection.appendChild(scopes);
  const promptSection = el('section', 'workspace-prompts');
  promptSection.appendChild(el('h3', '', 'Suggested Questions'));
  (workspace.suggestions || []).forEach((suggestion) => {
    const button = el('button', 'workspace-prompt', suggestion);
    button.type = 'button';
    button.addEventListener('click', () => handleUserInput(suggestion));
    promptSection.appendChild(button);
  });
  content.append(scopeSection, promptSection);
}

async function loadWorkspace() {
  $('workspaceContent').replaceChildren(el('p', 'empty-state', 'Loading...'));
  try { renderWorkspace(await api('/agent/workspace/me')); }
  catch (error) { $('workspaceContent').replaceChildren(el('p', 'empty-state error', error.message)); }
}

function parseConfirmIntent(text) {
  const value = text.trim().toLowerCase().replace(/[?.!,\s]/g, '');
  if (['cancel', 'no', 'stop', 'abort', 'nevermind'].some((word) => value.includes(word))) return 'cancel';
  if (['confirm', 'yes', 'ok', 'agree', 'proceed', 'execute'].some((word) => value.includes(word))) return 'confirm';
  return 'unknown';
}

function removeProgressMessage() {
  const last = $('messages').lastElementChild;
  if (last?.classList.contains('progress')) last.remove();
}

function handleAskResponse(data) {
  state.awaitingConfirm = Boolean(data.awaitingUserConfirm
    || (data.interrupted && data.planStrategy === 'DANGEROUS_ORDER_OP'));
  state.pendingThreadId = state.awaitingConfirm ? data.threadId : null;
  updateComposer();
  appendMessage('assistant', data.answer || 'No answer to display at the moment.');
}

async function ask(query) {
  setSending(true);
  appendMessage('user', query);
  $('queryInput').value = '';
  appendMessage('assistant progress', 'Processing...');
  try {
    const data = await api('/agent/order/ask', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query, conversationId: state.conversationId }),
    });
    removeProgressMessage();
    handleAskResponse(data);
  } catch (error) {
    removeProgressMessage();
    appendMessage('assistant', 'Request failed: ' + error.message);
  } finally { setSending(false); }
}

async function resume(approved) {
  setSending(true);
  appendMessage('assistant progress', 'Processing...');
  try {
    const data = await api('/agent/order/resume', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ threadId: state.pendingThreadId, approved }),
    });
    removeProgressMessage();
    state.awaitingConfirm = false;
    state.pendingThreadId = null;
    updateComposer();
    appendMessage('assistant', data.answer || 'Operation completed.');
    await loadWorkspace();
  } catch (error) {
    removeProgressMessage();
    appendMessage('assistant', 'Operation failed: ' + error.message);
  } finally { setSending(false); }
}

async function handleUserInput(input) {
  const query = input.trim();
  if (!query || state.sending) return;
  if (state.awaitingConfirm) {
    const intent = parseConfirmIntent(query);
    if (intent === 'unknown') { appendMessage('meta', 'This operation is waiting for confirmation or cancellation.'); return; }
    appendMessage('user', query);
    $('queryInput').value = '';
    await resume(intent === 'confirm');
  } else await ask(query);
}

async function logout() {
  try { await api('/auth/logout', { method: 'POST' }); } finally { window.location.replace('/login.html'); }
}

function showImpersonationBanner(me) {
  if (!me.impersonating) return;
  const banner = el('div', 'impersonation-banner');
  const expires = me.impersonationExpiresAt
    ? new Date(me.impersonationExpiresAt * 1000).toLocaleTimeString('en-US', { hour12: false }) : '';
  banner.appendChild(el('strong', '', `Demo identity: ${me.displayName}${expires ? ` · until ${expires}` : ''}`));
  const exit = el('button', 'button button-quiet', 'Exit Demo Identity');
  exit.type = 'button';
  exit.addEventListener('click', async () => {
    const result = await api('/auth/impersonation/exit', { method: 'POST' });
    window.location.replace(result.redirect || '/admin.html');
  });
  banner.appendChild(exit);
  document.body.prepend(banner);
}

async function init() {
  $('chatForm').addEventListener('submit', (event) => {
    event.preventDefault();
    handleUserInput($('queryInput').value);
  });
  $('refreshWorkspace').addEventListener('click', loadWorkspace);
  $('logoutBtn').addEventListener('click', logout);
  updateComposer();
  try {
    await loadCsrf();
    const me = await api('/auth/me');
    if (me.passwordChangeRequired) { window.location.replace('/change-password.html'); return; }
    state.currentPersona = me.persona;
    showImpersonationBanner(me);
    updateIdentity();
    appendMessage('assistant', state.currentPersona.welcomeMessage);
    await loadWorkspace();
    await api('/agent/order/health');
    $('statusBadge').classList.add('online');
    $('statusText').textContent = 'Agent Online';
  } catch (error) {
    $('statusBadge').classList.add('offline');
    $('statusText').textContent = 'Agent Offline';
    appendMessage('assistant', error.message);
  }
}

if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
else init();
