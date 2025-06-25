import { getStatus } from '../services/api.js';
import { createElement } from '../utils/dom.js';
export async function renderStatus(root) {
  root.innerHTML='';
  const data = await getStatus();
  const card = createElement('div',{class:'card'},
    createElement('h2',{},'Estado del Coordinador'),
    createElement('p',{}, data._embedded ? data._embedded.message: data.message)
  );
  root.append(card);
}